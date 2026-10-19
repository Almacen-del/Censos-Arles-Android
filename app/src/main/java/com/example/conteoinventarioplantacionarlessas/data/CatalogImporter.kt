package com.example.conteoinventarioplantacionarlessas.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object CatalogImporter {
    suspend fun ensureLoaded(context: Context, database: AppDatabase) = withContext(Dispatchers.IO) {
        val version = context.assets.open("catalogo-version.txt").bufferedReader().use { it.readText().trim() }
        val dao = database.inventarioDao()
        if (dao.catalogoImportado(version) != null) return@withContext
        val data = context.assets.open("catalogo.json").bufferedReader().use { JSONObject(it.readText()) }
        require(data.getString("version") == version) { "Versión de catálogo incorrecta" }
        fun JSONObject.optional(key: String): String? = if(isNull(key)) null else getString(key)
        database.withTransaction {
            if (dao.catalogoImportado(version) != null) return@withTransaction
            val lots = data.getJSONArray("lotes")
            dao.insertLotes((0 until lots.length()).map { val p=lots.getJSONObject(it); Lote(p.getString("id"),p.getString("nombre")) })
            val lines = data.getJSONArray("lineas")
            dao.insertLineas((0 until lines.length()).map { val p=lines.getJSONObject(it); Linea(p.getString("id"),p.getString("loteId"),p.getString("numero"),p.getInt("cantidadArboles")) })
            val plants = data.getJSONArray("plantas")
            // Bounded insert batches; IGNORE preserves field corrections on future imports.
            for (start in 0 until plants.length() step 400) {
                dao.insertPlantas((start until minOf(start+400, plants.length())).map {
                    val p=plants.getJSONObject(it)
                    Planta(p.getString("lineaId"),p.getString("codigo"),p.getInt("posicion"),p.optional("siembra"),
                        sexo=p.optional("sexo"), muerto=false, alerta=p.optional("alerta"), filasOrigen=p.optional("filasOrigen"))
                })
            }
            dao.marcarCatalogo(CatalogoImportado(version))
        }
    }
}
