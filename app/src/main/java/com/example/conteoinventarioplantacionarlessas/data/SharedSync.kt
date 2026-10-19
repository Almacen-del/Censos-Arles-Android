package com.example.conteoinventarioplantacionarlessas.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/** Shared copies never become uploads. Local captures and drafts always win over incoming copies. */
object SharedSync {
    private val editing = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    fun beginEditing(line: String) { editing.add(line) }
    fun endEditing(line: String) { editing.remove(line) }
    private fun objects(a: JSONArray) = (0 until a.length()).map { a.getJSONObject(it) }
    internal fun query(db: SupportSQLiteDatabase, sql: String, vararg args: Any?): List<JSONObject> =
        db.query(sql, args).use { c -> buildList {
            while(c.moveToNext()) add(JSONObject().also { o -> c.columnNames.forEachIndexed { i,k ->
                o.put(k,when(c.getType(i)) { 0 -> JSONObject.NULL; 1 -> c.getLong(i); 2 -> c.getDouble(i); else -> c.getString(i) })
            } })
        } }
    internal fun setup(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS shared_campaign (campaign TEXT PRIMARY KEY NOT NULL,censo INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS shared_catalog_base (line TEXT PRIMARY KEY NOT NULL,payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS shared_catalog_sent (campaign TEXT NOT NULL,source TEXT NOT NULL,line TEXT NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(campaign,source,line))")
        db.execSQL("CREATE TABLE IF NOT EXISTS shared_issues (label TEXT PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS shared_remote_records (id TEXT PRIMARY KEY NOT NULL,payload TEXT NOT NULL)")
    }
    internal fun canonical(value: Any?): String = when(value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",","{","}") { JSONObject.quote(it)+":"+canonical(value.get(it)) }
        is JSONArray -> (0 until value.length()).joinToString(",","[","]") { canonical(value.get(it)) }
        is Number, is Boolean -> value.toString()
        else -> JSONObject.quote(value.toString())
    }
    private fun issue(db: SupportSQLiteDatabase, text: String) { db.execSQL("INSERT OR IGNORE INTO shared_issues(label) VALUES(?)",arrayOf(text)) }
    fun issues(context: Context, scope: String): List<String> {
        val db=CloudSync.database(context,scope); setup(db)
        return query(db,"SELECT label FROM shared_issues ORDER BY label").map { it.getString("label") }
    }
    internal fun snapshot(db: SupportSQLiteDatabase, line: String): JSONObject? {
        val l=query(db,"SELECT l.id,l.loteId,l.numero,o.nombre AS loteNombre FROM lineas l JOIN lotes o ON o.id=l.loteId WHERE l.id=?",line).singleOrNull() ?: return null
        l.put("plantas",JSONArray(query(db,"SELECT codigo,posicion,siembra,filasOrigen FROM plantas WHERE lineaId=? ORDER BY posicion,codigo",line)))
        return l
    }
    private fun baseline(context: Context): Map<String,String> {
        val json=context.assets.open("catalogo.json").bufferedReader().use { JSONObject(it.readText()) }
        val lots=objects(json.getJSONArray("lotes")).associate { it.getString("id") to it.getString("nombre") }
        val plants=objects(json.getJSONArray("plantas")).groupBy { it.getString("lineaId") }
        return objects(json.getJSONArray("lineas")).associate { l ->
            val id=l.getString("id")
            val s=JSONObject().put("id",id).put("loteId",l.getString("loteId")).put("numero",l.getString("numero")).put("loteNombre",lots[l.getString("loteId")])
            s.put("plantas",JSONArray(plants[id].orEmpty().sortedBy { it.getInt("posicion") }.map { p ->
                JSONObject().also { o -> listOf("codigo","posicion","siembra","filasOrigen").forEach { o.put(it,p.opt(it) ?: JSONObject.NULL) } }
            }))
            id to canonical(s)
        }
    }
    internal fun signature(snapshot:JSONObject):String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(canonical(snapshot).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun savedBase(db: SupportSQLiteDatabase,line: String): String? = query(db,"SELECT payload FROM shared_catalog_base WHERE line=?",line).firstOrNull()?.getString("payload")
    private fun saveBase(db: SupportSQLiteDatabase,line:String,json:String) { db.execSQL("INSERT OR REPLACE INTO shared_catalog_base VALUES(?,?)",arrayOf(line,json)) }
    internal fun bind(db: SupportSQLiteDatabase,campaign:String): Int {
        setup(db)
        db.beginTransaction()
        try {
            val bound=query(db,"SELECT censo FROM shared_campaign WHERE campaign=?",campaign).firstOrNull()?.getInt("censo")
            if(bound!=null) {db.setTransactionSuccessful();return bound}
            val existing=query(db,"SELECT id FROM censos WHERE tipo='FLORACION' ORDER BY fecha DESC LIMIT 1").firstOrNull()
            val id=existing?.getInt("id") ?: db.compileStatement("INSERT INTO censos(tipo,fecha,estado) VALUES('FLORACION',?,'EN_PROCESO')").use {
                it.bindLong(1,System.currentTimeMillis());it.executeInsert().toInt()
            }
            db.execSQL("INSERT INTO shared_campaign VALUES(?,?)",arrayOf<Any?>(campaign,id))
            db.setTransactionSuccessful();return id
        } finally {db.endTransaction()}
    }
    private fun pages(path: String,token:String): List<JSONObject> {
        val all=mutableListOf<JSONObject>(); var offset=0
        while(true) {
            val page=objects(JSONArray(SupabaseSession.request("$path&limit=500&offset=$offset",token=token)))
            all.addAll(page); if(page.size<500) return all; offset+=page.size
        }
    }
    suspend fun exchange(context:Context,scope:String,allowed:()->Boolean): Int {
        val session=SupabaseSession.token(context,scope)
        val campaigns=objects(JSONArray(SupabaseSession.request("/rest/v1/campo_sync_campaigns?active=eq.true&select=id",token=session.access)))
        if(campaigns.isEmpty()) return 0
        check(campaigns.size==1)
        val campaign=campaigns.single().getString("id")
        val db=CloudSync.database(context,scope); setup(db)
        val censo=bind(db,campaign)
        val base=baseline(context)
        val ownIds=mutableSetOf<String>()
        val ownedLines=mutableSetOf<String>()
        val source=CloudSync.source(context,scope)
        db.execSQL("DELETE FROM shared_issues")
        // Register only this campaign's bound census; a newly created census is never silently merged.
        for(localScope in listOf(scope,CloudSync.GUEST)) {
            val rows=CloudSync.rows(context,localScope).filter { localScope!=CloudSync.GUEST || CloudSync.owner(context,it)==scope }
            if(localScope==scope) {
                ownIds.addAll(rows.map { it.id })
                ownedLines.addAll(rows.filter {it.data["tipo"]=="FLORACION" && (it.data["datos"] as Map<*,*>)["censoId"]==censo.toLong()}.map {(it.data["datos"] as Map<*,*>)["lineaId"].toString()})
            }
            val guestCenso=if(localScope==CloudSync.GUEST && rows.isNotEmpty()) bind(CloudSync.database(context,localScope),campaign) else null
            val censusIds=if(localScope==scope) listOf("${source}_$censo") else rows.filter { it.data["tipo"]=="FLORACION" && (it.data["datos"] as Map<*,*>)["censoId"] == guestCenso?.toLong() }.map { it.data["censoId"].toString() }.distinct()
            for(id in censusIds) {
                check(allowed())
                val body=JSONObject().put("censo_id",id).put("campaign_id",campaign).put("worker_uid",session.uid)
                SupabaseSession.request("/rest/v1/campo_sync_censuses?on_conflict=censo_id","POST",body.toString(),session.access,"resolution=ignore-duplicates")
            }
            if(localScope==CloudSync.GUEST && guestCenso!=null) {
                val guestDb=CloudSync.database(context,localScope)
                val guestSource=CloudSync.source(context,localScope)
                val guestLines=rows.filter { (it.data["datos"] as Map<*,*>)["censoId"]==guestCenso.toLong() }
                    .map { (it.data["datos"] as Map<*,*>)["lineaId"].toString() }.distinct()
                for(line in guestLines) {
                    val current=snapshot(guestDb,line) ?: continue
                    val json=canonical(current)
                    val sent=query(guestDb,"SELECT payload FROM shared_catalog_sent WHERE campaign=? AND source=? AND line=?",campaign,guestSource,line).firstOrNull()?.getString("payload")
                    if(json==sent) continue
                    check(allowed())
                    val body=JSONObject().put("campaign_id",campaign).put("source_id",guestSource).put("line_id",line).put("worker_uid",session.uid).put("payload",current)
                    SupabaseSession.request("/rest/v1/campo_sync_catalogs?on_conflict=campaign_id,source_id,line_id","POST",body.toString(),session.access,"resolution=merge-duplicates")
                    saveBase(guestDb,line,json)
                    guestDb.execSQL("INSERT OR REPLACE INTO shared_catalog_sent VALUES(?,?,?,?)",arrayOf(campaign,guestSource,line,json))
                }
            }
        }
        val lines=query(db,"SELECT id FROM lineas WHERE loteId NOT IN ('L001','L002')")
        for(line in lines) {
            val id=line.getString("id"); val current=snapshot(db,id) ?: continue
            val json=canonical(current); val previous=savedBase(db,id) ?: base[id]
            val sent=query(db,"SELECT payload FROM shared_catalog_sent WHERE campaign=? AND source=? AND line=?",campaign,source,id).firstOrNull()?.getString("payload")
            if(json==previous && (id !in ownedLines || json==sent)) continue
            check(allowed())
            val body=JSONObject().put("campaign_id",campaign).put("source_id",source).put("line_id",id).put("worker_uid",session.uid).put("payload",current)
            SupabaseSession.request("/rest/v1/campo_sync_catalogs?on_conflict=campaign_id,source_id,line_id","POST",body.toString(),session.access,"resolution=merge-duplicates")
            // Save exactly the transmitted snapshot: concurrent local changes remain dirty.
            saveBase(db,id,json)
            db.execSQL("INSERT OR REPLACE INTO shared_catalog_sent VALUES(?,?,?,?)",arrayOf(campaign,source,id,json))
        }
        val catalogs=pages("/rest/v1/campo_sync_catalogs?campaign_id=eq.$campaign&select=source_id,line_id,payload&order=source_id,line_id",session.access)
        val blocked=mutableSetOf<String>()
        for((id,versions) in catalogs.groupBy { it.getString("line_id") }) {
            val variants=versions.map { it.getJSONObject("payload") }.distinctBy { canonical(it) }
            if(variants.size>1) { issue(db,"$id: los celulares tienen catálogos distintos; se conservaron ambos."); blocked.add(id); continue }
            check(allowed())
            if(!applyCatalog(db,variants.single(),base[id])) blocked.add(id)
        }
        val maps=pages("/rest/v1/campo_sync_censuses?campaign_id=eq.$campaign&select=censo_id&order=censo_id",session.access).map { it.getString("censo_id") }.toSet()
        val remote=pages("/rest/v1/campo_records?tipo=eq.FLORACION&select=id,worker_uid,censo_id,source_id,datos&order=id",session.access).filter { it.getString("censo_id") in maps }
        check(allowed())
        val knownCatalogs=catalogs.associate { (it.getString("source_id")+"/"+it.getString("line_id")) to signature(it.getJSONObject("payload")) }
        val eligible=eligibleRecords(remote,ownIds,knownCatalogs) { issue(db,it) }
        val received=applyRecords(db,censo,eligible,ownIds,blocked)
        AppDatabase.getDatabase(context,scope).invalidationTracker.refreshVersionsAsync()
        return received
    }
    internal fun eligibleRecords(records:List<JSONObject>, ownIds:Set<String>, catalogs:Map<String,String>, report:(String)->Unit):List<JSONObject> {
        fun populated(row:JSONObject):Boolean {
            val d=row.getJSONObject("datos")
            return d.optBoolean("muerto",false) || listOf("sexo","inflorescencias","botones","observacion").any {
                !d.isNull(it) && d.optString(it,"").isNotBlank()
            }
        }
        // Empty legacy slots are still retained on the server; they are not a second field reading.
        val captures=records.filter(::populated)
        return captures.groupBy { val d=it.getJSONObject("datos"); d.getString("lineaId")+"/"+d.getString("arbolCodigo") }.flatMap { (tree,versions) ->
            val missing=versions.any { row ->
                if(row.getString("id") in ownIds) false else {
                    val d=row.getJSONObject("datos")
                    val expected=catalogs[row.optString("source_id")+"/"+d.getString("lineaId")]
                    expected==null || d.optString("catalogSignature","")!=expected
                }
            }
            if(missing) {
                report("$tree: falta actualizar el catálogo del celular de origen; los otros árboles sí pueden recibirse.")
                emptyList()
            } else versions
        }
    }
    internal fun applyCatalog(db:SupportSQLiteDatabase, incoming:JSONObject, assetBase:String?):Boolean {
        val line=incoming.getString("id"); val json=canonical(incoming)
        if(line in editing) {issue(db,"$line: cierra la ficha y sincroniza de nuevo para recibir cambios.");return false}
        val plants=objects(incoming.getJSONArray("plantas"))
        require(plants.size<=9999 && plants.map {it.getString("codigo")}.distinct().size==plants.size && plants.map {it.getInt("posicion")}.distinct().size==plants.size)
        require(plants.all {it.getInt("posicion")>0})
        db.beginTransaction()
        try {
            val current=snapshot(db,line); val currentJson=current?.let {canonical(it)}
            if(currentJson==json) {saveBase(db,line,json);db.setTransactionSuccessful();return true}
            val clean=currentJson==null || currentJson==(savedBase(db,line) ?: assetBase)
            val protected=query(db,"SELECT id FROM registro_floracion WHERE lineaId=? AND capturado=1 AND compartidoPor IS NULL LIMIT 1",line).isNotEmpty() ||
                query(db,"SELECT posicion FROM borradores_ficha WHERE lineaId=? LIMIT 1",line).isNotEmpty() ||
                query(db,"SELECT id FROM registro_frutos WHERE lineaId=? AND capturado=1 LIMIT 1",line).isNotEmpty()
            if(!clean || protected) {
                issue(db,"$line: cambio de árboles pendiente; se conservó el catálogo y el trabajo local.")
                db.setTransactionSuccessful();return false
            }
            db.execSQL("INSERT OR IGNORE INTO lotes(id,nombre) VALUES(?,?)",arrayOf(incoming.getString("loteId"),incoming.getString("loteNombre")))
            db.execSQL("INSERT OR REPLACE INTO lineas(id,loteId,numero,cantidadArboles) VALUES(?,?,?,?)",arrayOf(line,incoming.getString("loteId"),incoming.getString("numero"),plants.size))
            db.execSQL("DELETE FROM plantas WHERE lineaId=?",arrayOf(line))
            db.execSQL("DELETE FROM registro_floracion WHERE lineaId=? AND (capturado=0 OR compartidoPor IS NOT NULL)",arrayOf(line))
            db.execSQL("DELETE FROM avance_floracion WHERE lineaId=?",arrayOf(line))
            for(p in plants) db.execSQL("INSERT INTO plantas(lineaId,codigo,posicion,siembra,sexo,muerto,alerta,filasOrigen) VALUES(?,?,?,?,NULL,0,NULL,?)",
                arrayOf<Any?>(line,p.getString("codigo"),p.getInt("posicion"),p.nullable("siembra"),p.nullable("filasOrigen")))
            saveBase(db,line,json);db.setTransactionSuccessful();return true
        } finally {db.endTransaction()}
    }
    private fun JSONObject.nullable(key:String):String? = if(isNull(key)) null else getString(key)
    private val values=listOf("sexo","siembra","inflorescencias","botones","estado","muerto","observacion")
    internal fun measurement(j:JSONObject):String = canonical(JSONObject().also { out -> values.forEach { key ->
        out.put(key,if(key=="muerto") (j.opt(key)==true || j.optInt(key,0)==1) else j.opt(key) ?: JSONObject.NULL)
    } })
    internal fun applyRecords(db:SupportSQLiteDatabase,censo:Int,records:List<JSONObject>,ownIds:Set<String>,blocked:Set<String> = emptySet()):Int {
        setup(db);var count=0
        db.beginTransaction()
        try {
            // Fill every slot, not only received trees, so opening the line keeps all pending trees.
            for(line in records.map { it.getJSONObject("datos").getString("lineaId") }.distinct().filterNot {it in blocked}) {
                db.execSQL("INSERT INTO registro_floracion(censoId,loteId,siembra,lineaId,arbolCodigo,posicion,sexo,inflorescencias,botones,estado,muerto,capturado,verificadoEn,observacion,compartidoPor) SELECT ?,l.loteId,p.siembra,p.lineaId,p.codigo,p.posicion,p.sexo,NULL,NULL,'BORRADOR',p.muerto,0,0,'',NULL FROM plantas p JOIN lineas l ON l.id=p.lineaId WHERE p.lineaId=? AND NOT EXISTS(SELECT 1 FROM registro_floracion r WHERE r.censoId=? AND r.lineaId=p.lineaId AND r.arbolCodigo=p.codigo)",arrayOf(censo,line,censo))
            }
            for((key,versions) in records.groupBy { val d=it.getJSONObject("datos"); d.getString("lineaId")+"/"+d.getString("arbolCodigo") }) {
                versions.forEach { db.execSQL("INSERT OR REPLACE INTO shared_remote_records VALUES(?,?)",arrayOf(it.getString("id"),it.toString())) }
                val row=versions.first();val data=row.getJSONObject("datos");val line=data.getString("lineaId");val code=data.getString("arbolCodigo")
                if(line in blocked) continue
                if(line in editing) {issue(db,"$line: cierra la ficha y sincroniza de nuevo para recibir cambios.");continue}
                if(versions.map {measurement(it.getJSONObject("datos"))}.distinct().size>1) {issue(db,"$key: hay conteos diferentes; se conservaron las versiones de ambos celulares.");continue}
                if(versions.any {it.getString("id") in ownIds}) continue
                val plant=query(db,"SELECT posicion FROM plantas WHERE lineaId=? AND codigo=?",line,code).singleOrNull()
                if(plant==null || plant.getInt("posicion")!=data.getInt("posicion")) {issue(db,"$key: falta recibir el catálogo correcto de esta línea.");continue}
                val position=plant.getInt("posicion")
                val existing=query(db,"SELECT * FROM registro_floracion WHERE censoId=? AND lineaId=? AND arbolCodigo=?",censo,line,code)
                if(existing.size>1) {issue(db,"$key: varias fichas locales; no se reemplazaron.");continue}
                val old=existing.singleOrNull()
                val draft=query(db,"SELECT posicion FROM borradores_ficha WHERE tipo='FLORACION' AND censoId=? AND lineaId=? AND posicion=?",censo,line,position).isNotEmpty()
                if(draft || (old!=null && old.isNull("compartidoPor") && old.getInt("capturado")==1)) {
                    if(draft || measurement(old!!)!=measurement(data)) issue(db,"$key: tienes un conteo o borrador local diferente; no se reemplazó.")
                    continue
                }
                val remoteId=row.getString("id")
                val id=old?.getLong("id")
                val fields=arrayOf<Any?>(id,censo,data.getString("loteId"),data.nullable("siembra"),line,code,position,data.nullable("sexo"),
                    if(data.isNull("inflorescencias")) null else data.getInt("inflorescencias"),if(data.isNull("botones")) null else data.getInt("botones"),
                    data.getString("estado"),if(data.optBoolean("muerto",false)) 1 else 0,1,data.optLong("verificadoEn",0),data.optString("observacion",""),remoteId)
                db.execSQL("INSERT OR REPLACE INTO registro_floracion(id,censoId,loteId,siembra,lineaId,arbolCodigo,posicion,sexo,inflorescencias,botones,estado,muerto,capturado,verificadoEn,observacion,compartidoPor) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",fields)
                count++
            }
            db.setTransactionSuccessful()
        } finally {db.endTransaction()}
        return count
    }
}
