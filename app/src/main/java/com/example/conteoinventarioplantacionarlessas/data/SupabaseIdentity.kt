package com.example.conteoinventarioplantacionarlessas.data

/** Verified original account ids. Used only for the on-device Room database and guest ownership. */
object SupabaseIdentity {
    fun localScope(email: String, uid: String): String = when(email.trim().lowercase(java.util.Locale.ROOT)) {
        "auxiliar1@censosarles.sas" -> "vSesIBX5JqNtg1UJjngErzXZsxq1"
        "auxiliar2@censosarles.sas" -> "zqZAVgUwxcYOvCrLHoHdPCQhEbe2"
        else -> uid
    }
}
