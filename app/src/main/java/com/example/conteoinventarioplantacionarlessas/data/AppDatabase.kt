package com.example.conteoinventarioplantacionarlessas.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Censo::class,
        Lote::class,
        Linea::class,
        RegistroFrutos::class,
        RegistroProduccion::class,
        RegistroFloracion::class,
        LineaOmitida::class,
        Planta::class,
        AvanceFloracion::class,
        CatalogoImportado::class,
        BorradorFicha::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun inventarioDao(): InventarioDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS plantas (lineaId TEXT NOT NULL, codigo TEXT NOT NULL, posicion INTEGER NOT NULL, siembra TEXT, PRIMARY KEY(lineaId, codigo))")
                db.execSQL("CREATE TABLE IF NOT EXISTS avance_floracion (censoId INTEGER NOT NULL, lineaId TEXT NOT NULL, posicion INTEGER NOT NULL, PRIMARY KEY(censoId, lineaId))")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plantas ADD COLUMN sexo TEXT")
                db.execSQL("ALTER TABLE plantas ADD COLUMN muerto INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE plantas ADD COLUMN alerta TEXT")
                db.execSQL("ALTER TABLE plantas ADD COLUMN filasOrigen TEXT")
                for (table in listOf("registro_frutos", "registro_floracion")) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN muerto INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE $table ADD COLUMN capturado INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE $table ADD COLUMN verificadoEn INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("UPDATE $table SET capturado=1 WHERE sexo IS NOT NULL OR estado='COMPLETO'")
                }
                db.execSQL("UPDATE registro_frutos SET capturado=1 WHERE frutos IS NOT NULL")
                db.execSQL("UPDATE registro_floracion SET capturado=1 WHERE inflorescencias IS NOT NULL OR botones IS NOT NULL")
                db.execSQL("CREATE TABLE IF NOT EXISTS catalogo_importado (version TEXT NOT NULL PRIMARY KEY)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3,4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lineas_omitidas ADD COLUMN responsable TEXT")
                for (table in listOf("registro_frutos", "registro_floracion", "registro_produccion", "lineas_omitidas")) {
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_${table}_censoId_lineaId ON $table(censoId,lineaId)")
                }
            }
        }

        val MIGRATION_4_5 = object : Migration(4,5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS borradores_ficha (tipo TEXT NOT NULL,censoId INTEGER NOT NULL,lineaId TEXT NOT NULL,posicion INTEGER NOT NULL,sexo TEXT,muerto INTEGER NOT NULL,a TEXT NOT NULL,b TEXT NOT NULL,c TEXT NOT NULL,observacion TEXT NOT NULL,actualizadoEn INTEGER NOT NULL,PRIMARY KEY(tipo,censoId,lineaId,posicion))")
            }
        }

        val MIGRATION_5_6 = object : Migration(5,6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE registro_floracion ADD COLUMN observacion TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_6_7 = object : Migration(6,7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE registro_floracion ADD COLUMN compartidoPor TEXT DEFAULT NULL")
            }
        }

        private val instances = mutableMapOf<String, AppDatabase>()

        fun getDatabase(context: Context, workerUid: String? = null): AppDatabase {
            // Preserve the previous local database; never assign its records to an unknown worker.
            val name = if (workerUid == null) "inventario_arles_db" else {
                val hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(workerUid.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                "inventario_arles_worker_$hash.db"
            }
            return synchronized(instances) {
                instances.getOrPut(name) {
                    Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build()
                }
            }
        }
    }
}
