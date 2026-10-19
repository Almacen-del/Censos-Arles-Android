package com.example.conteoinventarioplantacionarlessas.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SupabaseFailure(val status: Int, message: String) : Exception(message)

/** No retries here: WorkManager owns retries and stable record ids make them idempotent. */
object SupabaseProtocol {
    fun payload(id: String, worker: String, source: Map<String, Any?>): JSONObject = JSONObject()
        .put("id", id).put("worker_uid", worker).put("tipo", source["tipo"])
        .put("censo_id", source["censoId"]).put("source_id", source["sourceId"])
        .put("origen", source["origen"]).put("schema_version", source["schemaVersion"])
        .put("datos", JSONObject(source["datos"] as Map<*, *>))

    fun accepted(response: String, id: String, worker: String): Boolean {
        val result = JSONArray(response)
        return result.length() == 1 && result.getJSONObject(0).getString("id") == id &&
            result.getJSONObject(0).getString("worker_uid") == worker
    }

    fun request(base: String, key: String, path: String, method: String = "GET", body: String? = null,
                token: String? = null, prefer: String? = null): String {
        val connection = URL(base + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("apikey", key)
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            prefer?.let { connection.setRequestProperty("Prefer", it) }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw SupabaseFailure(status, when(status) {
                400, 401 -> "Sesión o credenciales no válidas. Inicia sesión con tu cuenta de Supabase."
                403 -> "Tu cuenta no tiene permiso. Los registros siguen guardados en el celular."
                429 -> "Demasiados intentos. Espera un momento."
                else -> "No se pudo completar la conexión con Supabase (HTTP $status)."
            })
            return response
        } finally { connection.disconnect() }
    }
}
