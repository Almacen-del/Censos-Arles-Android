package com.example.conteoinventarioplantacionarlessas
import com.example.conteoinventarioplantacionarlessas.data.SupabaseIdentity
import org.junit.Assert.*
import org.junit.Test
class SupabaseIdentityTest {
 @Test fun existingWorkersReopenTheirOriginalDatabase(){
  assertEquals("vSesIBX5JqNtg1UJjngErzXZsxq1",SupabaseIdentity.localScope(" AUXILIAR1@CENSOSARLES.SAS ","new-id"))
  assertEquals("zqZAVgUwxcYOvCrLHoHdPCQhEbe2",SupabaseIdentity.localScope("auxiliar2@censosarles.sas","new-id"))
  assertEquals("new-id",SupabaseIdentity.localScope("different@example.com","new-id"))
 }
}
