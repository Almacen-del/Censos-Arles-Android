package com.example.conteoinventarioplantacionarlessas.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.conteoinventarioplantacionarlessas.BuildConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SupabaseSession {
    data class Session(val uid: String, val email: String, val scope: String, val access: String, val refresh: String, val expires: Long)
    private val refreshLock = Mutex()
    private const val ALIAS = "arles-supabase-session-v1"
    private fun prefs(context: Context) = context.getSharedPreferences("supabase_session", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun current(context: Context): Session? = runCatching {
        val raw = prefs(context).getString("session", null) ?: return null
        val envelope = JSONObject(raw)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)))
        val json = JSONObject(String(cipher.doFinal(Base64.decode(envelope.getString("data"), Base64.NO_WRAP)), Charsets.UTF_8))
        Session(json.getString("uid"),json.getString("email"),json.getString("scope"),json.getString("access"),json.getString("refresh"),json.getLong("expires"))
    }.getOrNull()
    @Synchronized fun save(context: Context, session: Session) {
        val json = JSONObject().put("uid",session.uid).put("email",session.email).put("scope",session.scope)
            .put("access",session.access).put("refresh",session.refresh).put("expires",session.expires)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        check(prefs(context).edit().putString("session",JSONObject().put("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP))
            .put("data",Base64.encodeToString(encrypted,Base64.NO_WRAP)).toString()).commit()) { "No se pudo guardar la sesión." }
    }
    @Synchronized fun clear(context: Context) { prefs(context).edit().remove("session").commit() }
    fun request(path: String, method: String = "GET", body: String? = null, token: String? = null, prefer: String? = null) =
        SupabaseProtocol.request(BuildConfig.SUPABASE_URL,BuildConfig.SUPABASE_KEY,path,method,body,token,prefer)
    private fun parse(raw: String, scope: String? = null): Session {
        val json = JSONObject(raw); val user = json.getJSONObject("user"); val uid = user.getString("id")
        return Session(uid,user.getString("email"),scope ?: uid,json.getString("access_token"),json.getString("refresh_token"),
            json.optLong("expires_at",System.currentTimeMillis()/1000 + json.getLong("expires_in")))
    }
    fun signIn(email: String, password: String): Session {
        val result = parse(request("/auth/v1/token?grant_type=password","POST",JSONObject().put("email",email).put("password",password).toString()))
        val member = JSONArray(request("/rest/v1/campo_members?select=active,legacy_uid&user_id=eq.${result.uid}",token=result.access))
        if(member.length()!=1 || !member.getJSONObject(0).getBoolean("active"))throw SupabaseFailure(403,"Tu cuenta no está habilitada para censos.")
        val legacy=member.getJSONObject(0).optString("legacy_uid", "").takeUnless { it.isBlank() || it=="null" }
        return result.copy(scope=legacy ?: SupabaseIdentity.localScope(result.email,result.uid))
    }
    suspend fun token(context: Context, expectedScope: String): Session = refreshLock.withLock {
        val before=current(context) ?: throw SupabaseFailure(401,"Inicia sesión para sincronizar.")
        if(before.scope!=expectedScope)throw SupabaseFailure(401,"La cuenta cambió. No se enviaron registros.")
        if(before.expires>System.currentTimeMillis()/1000+60)return@withLock before
        val next=parse(request("/auth/v1/token?grant_type=refresh_token","POST",JSONObject().put("refresh_token",before.refresh).toString()),before.scope)
        synchronized(this) {
            if(current(context)?.refresh!=before.refresh)throw SupabaseFailure(401,"La sesión cambió.")
            save(context,next)
        }
        next
    }
}
