package com.example.conteoinventarioplantacionarlessas.data

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Room remains the source of truth. Acknowledgments are persisted only after server acceptance.
 * Stable source + table + local id make every retry an idempotent overwrite. */
object CloudSync {
    internal val serial = Mutex()
    fun canDeleteLocal(context: Context, uid: String): Boolean {
        val session = SupabaseSession.current(context)
        return session?.scope == uid && session.email.equals("almacen@arlessas.com", true) &&
            !context.getSharedPreferences("session", Context.MODE_PRIVATE).getBoolean("guest", false)
    }
    data class LocalRecord(val scope: String, val row: Row)
    fun localRecords(context: Context, uid: String): List<LocalRecord> {
        check(canDeleteLocal(context, uid))
        return listOf(uid, GUEST).flatMap { scope ->
            rows(context, scope).filter { scope != GUEST || owner(context, it).let { owner -> owner == null || owner == uid } }
                .map { LocalRecord(scope, it) }
        }
    }
    suspend fun deleteLocal(context: Context, uid: String, selected: LocalRecord) = serial.withLock {
        withContext(Dispatchers.IO) {
            check(canDeleteLocal(context, uid))
            val current = localRecords(context, uid).singleOrNull { it.scope == selected.scope && it.row.id == selected.row.id }
            check(current?.row?.json == selected.row.json) { "El registro cambió. Vuelve a seleccionarlo." }
            val type = selected.row.data["tipo"] as String
            val data = selected.row.data["datos"] as Map<*, *>
            val db = database(context, selected.scope)
            db.beginTransaction()
            try {
                // Keep the inventory slot and tree numbering; clear only captured values.
                val fields = when(type) {
                    "FLORACION" -> "sexo=NULL, inflorescencias=NULL, botones=NULL, muerto=0, capturado=0, verificadoEn=0, observacion='', estado='BORRADOR'"
                    "FRUTOS" -> "sexo=NULL, frutos=NULL, muerto=0, capturado=0, verificadoEn=0, estado='BORRADOR'"
                    else -> "vivos=NULL, muertos=NULL, resiembras=NULL, observacion=NULL, estado='BORRADOR'"
                }
                db.execSQL("UPDATE ${tables.getValue(type)} SET $fields WHERE id=?", arrayOf(data["id"]))
                db.execSQL("DELETE FROM borradores_ficha WHERE tipo=? AND censoId=? AND lineaId=? AND posicion=?",
                    arrayOf(type, data["censoId"], data["lineaId"], data["posicion"] ?: 0))
                db.execSQL("DELETE FROM cloud_ack_supabase WHERE id=?", arrayOf(selected.row.id))
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            AppDatabase.getDatabase(context, selected.scope).invalidationTracker.refreshVersionsAsync()
            setStatus(context, uid, "Registro eliminado del celular. El árbol sigue disponible para contar.")
        }
    }
    const val GUEST = "local-guest"
    private val initialized = mutableSetOf<String>()
    private val tables = mapOf("FRUTOS" to "registro_frutos", "PRODUCCION" to "registro_produccion", "FLORACION" to "registro_floracion")
    fun preferences(context: Context) = context.getSharedPreferences("cloud_sync_supabase", Context.MODE_PRIVATE)
    fun status(context: Context, uid: String) = preferences(context).getString("status:$uid", "Los registros se guardan primero en este celular.")!!
    fun setStatus(context: Context, uid: String, text: String) { preferences(context).edit().putString("status:$uid", text).apply() }
    fun schedule(context: Context, uid: String, periodic: Boolean = false) {
        if (uid == GUEST) return
        val manager = WorkManager.getInstance(context)
        val data = workDataOf("uid" to uid)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val work = OneTimeWorkRequestBuilder<CensusSyncWorker>().setInputData(data).setConstraints(constraints).setInitialDelay(3, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        // Coalesce rapid field saves. Periodic work and session entry recover any save
        // made in the narrow interval between the last snapshot and worker completion.
        manager.enqueueUniqueWork("sync:$uid", ExistingWorkPolicy.KEEP, work)
        if (periodic) manager.enqueueUniquePeriodicWork("periodic:$uid", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CensusSyncWorker>(15, TimeUnit.MINUTES).setInputData(data).setConstraints(constraints).build())
    }
    fun cancel(context: Context, uid: String?) {
        if (uid == null || uid == GUEST) return
        WorkManager.getInstance(context).cancelUniqueWork("sync:$uid")
        WorkManager.getInstance(context).cancelUniqueWork("periodic:$uid")
    }
    data class Row(val id: String, val json: String, val data: Map<String, Any?>)
    // Auxiliary tables do not change the Room inventory schema or existing records.
    internal fun database(context: Context, scope: String): androidx.sqlite.db.SupportSQLiteDatabase {
        val db = AppDatabase.getDatabase(context, scope).openHelper.writableDatabase
        synchronized(initialized) {
        if (scope in initialized) return db
        db.execSQL("CREATE TABLE IF NOT EXISTS cloud_source (id INTEGER PRIMARY KEY, source TEXT NOT NULL)")
        db.execSQL("INSERT OR IGNORE INTO cloud_source(id,source) VALUES(1,?)", arrayOf(UUID.randomUUID().toString()))
        db.execSQL("CREATE TABLE IF NOT EXISTS cloud_ack_supabase (id TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS cloud_owner (id TEXT PRIMARY KEY NOT NULL, uid TEXT NOT NULL)")
        initialized.add(scope)
        }
        return db
    }
    internal fun source(context: Context, scope: String): String = database(context,scope).query("SELECT source FROM cloud_source WHERE id=1").use { it.moveToFirst(); it.getString(0) }
    fun rows(context: Context, scope: String): List<Row> {
        val db = database(context, scope)
        val source = db.query("SELECT source FROM cloud_source WHERE id=1").use { it.moveToFirst(); it.getString(0) }
        val result = mutableListOf<Row>()
        val signatures=mutableMapOf<String,String>()
        db.beginTransaction()
        try {
        tables.forEach { (type, table) ->
            db.query("SELECT r.*, c.fecha AS fechaCenso, l.nombre AS loteNombre, ln.numero AS lineaNumero FROM $table r JOIN censos c ON c.id=r.censoId JOIN lotes l ON l.id=r.loteId JOIN lineas ln ON ln.id=r.lineaId WHERE l.id NOT IN ('L001','L002') AND l.nombre NOT LIKE 'Demostración%'").use { cursor ->
                while (cursor.moveToNext()) {
                    val record = linkedMapOf<String, Any?>()
                    cursor.columnNames.forEachIndexed { i, name -> record[name] = when(cursor.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_NULL -> null
                        android.database.Cursor.FIELD_TYPE_INTEGER -> if(name in listOf("muerto", "capturado")) cursor.getInt(i) != 0 else cursor.getLong(i)
                        else -> cursor.getString(i)
                    } }
                    if (record["compartidoPor"] != null) continue
                    record.remove("compartidoPor")
                    if (record.containsKey("capturado") && record["capturado"] != true) continue
                    val measurement = when(type) { "FRUTOS" -> listOf("sexo", "frutos"); "PRODUCCION" -> listOf("vivos", "muertos", "resiembras", "observacion"); else -> listOf("sexo", "inflorescencias", "botones") }
                    if (!record.containsKey("capturado") && measurement.all { record[it] == null || record[it] == "" }) continue
                    if(type=="FLORACION") {
                        val line=record["lineaId"].toString()
                        record["catalogSignature"]=signatures.getOrPut(line) { SharedSync.signature(requireNotNull(SharedSync.snapshot(db,line))) }
                    }
                    val id = "${source}_${type}_${record["id"]}"
                    val payload = linkedMapOf<String, Any?>("schemaVersion" to 1L, "sourceId" to source, "tipo" to type,
                        "censoId" to "${source}_${record["censoId"]}", "origen" to if(scope == GUEST) "INVITADO" else "TRABAJADOR", "datos" to record)
                    result.add(Row(id, JSONObject(payload).toString(), payload))
                }
            }
        }
        db.setTransactionSuccessful()
        } finally {db.endTransaction()}
        return result
    }
    fun claimGuest(context: Context, uid: String): Int {
        require(uid != GUEST)
        val rows = rows(context, GUEST)
        val db = database(context, GUEST)
        var count = 0
        db.beginTransaction()
        try {
            rows.forEach { row ->
                db.execSQL("INSERT OR IGNORE INTO cloud_owner(id,uid) VALUES(?,?)", arrayOf(row.id, uid))
                db.query("SELECT changes()").use { it.moveToFirst(); count += it.getInt(0) }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return count
    }
    fun owner(context: Context, row: Row): String? = database(context, GUEST).query("SELECT uid FROM cloud_owner WHERE id=?", arrayOf(row.id)).use { if(it.moveToFirst()) it.getString(0) else null }
    fun acknowledged(context: Context, scope: String, row: Row): Boolean = database(context, scope).query("SELECT payload FROM cloud_ack_supabase WHERE id=?", arrayOf(row.id)).use { it.moveToFirst() && it.getString(0) == row.json }
    fun acknowledge(context: Context, scope: String, row: Row) = database(context, scope).execSQL("INSERT OR REPLACE INTO cloud_ack_supabase(id,payload) VALUES(?,?)", arrayOf(row.id, row.json))
}

class CensusSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = CloudSync.serial.withLock { withContext(Dispatchers.IO) {
        val uid = inputData.getString("uid") ?: return@withContext Result.failure()

        fun allowed() = SupabaseSession.current(applicationContext)?.scope == uid && !applicationContext.getSharedPreferences("session", Context.MODE_PRIVATE).getBoolean("guest", false)
        if (!allowed()) return@withContext Result.success()
        try {
            CloudSync.setStatus(applicationContext, uid, "Sincronizando con Supabase…")
            var total = 0
            for(scope in listOf(uid, CloudSync.GUEST)) {
                val rows=CloudSync.rows(applicationContext,scope).filter {scope!=CloudSync.GUEST || CloudSync.owner(applicationContext,it)==uid}
                total+=rows.size
                for(batch in rows.filterNot {CloudSync.acknowledged(applicationContext,scope,it)}.chunked(50)) {
                    if(isStopped || !allowed()) return@withContext Result.success()
                    val session=SupabaseSession.token(applicationContext,uid)
                    val payload=org.json.JSONArray(batch.map {SupabaseProtocol.payload(it.id,session.uid,it.data)})
                    val response=org.json.JSONArray(SupabaseSession.request("/rest/v1/campo_records?on_conflict=id&select=id,worker_uid","POST",payload.toString(),session.access,"resolution=merge-duplicates,return=representation"))
                    val accepted=(0 until response.length()).map {response.getJSONObject(it)}
                    check(accepted.size==batch.size && accepted.all {it.getString("worker_uid")==session.uid} && accepted.map {it.getString("id")}.toSet()==batch.map {it.id}.toSet()) {"El servidor no confirmó todos los registros."}
                    if(!allowed()) return@withContext Result.success()
                    batch.forEach {CloudSync.acknowledge(applicationContext,scope,it)}
                }
            }
            val received=SharedSync.exchange(applicationContext,uid) { !isStopped && allowed() }
            val conflicts=SharedSync.issues(applicationContext,uid).size
            CloudSync.setStatus(applicationContext, uid, "$total registros propios revisados · $received recibidos de otros celulares." + if(conflicts>0) " $conflicts diferencias por revisar." else " Sincronización completa.")
            Result.success()
        } catch(e: Exception) {
            val denied = e is SupabaseFailure && e.status in listOf(400,401,403)
            CloudSync.setStatus(applicationContext, uid, if(denied) "Guardado local. Supabase rechazó el acceso; revisa permisos o inicia sesión otra vez." else "Guardado local. Se reintentará al recuperar conexión.")
            if(denied) Result.failure() else Result.retry()
        }
    } }
}
