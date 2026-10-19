package com.example.conteoinventarioplantacionarlessas
import com.example.conteoinventarioplantacionarlessas.data.SupabaseProtocol
import com.example.conteoinventarioplantacionarlessas.data.SupabaseFailure
import java.net.ServerSocket
import java.net.InetSocketAddress
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SupabaseProtocolTest {
 @Test fun payloadKeepsIdentityNullZeroDeathAndObservation() {
  val source=mapOf<String,Any?>("tipo" to "FLORACION","censoId" to "source_2","sourceId" to "source","schemaVersion" to 1L,"origen" to "INVITADO","datos" to mapOf("inflorescencias" to 0,"botones" to null,"muerto" to true,"observacion" to "Daño en rama"))
  val first=SupabaseProtocol.payload("source_FLORACION_7","worker",source)
  val second=SupabaseProtocol.payload("source_FLORACION_7","worker",source)
  assertEquals(first.toString(),second.toString())
  assertEquals(0,first.getJSONObject("datos").getInt("inflorescencias"))
  assertTrue(first.getJSONObject("datos").isNull("botones"))
  assertTrue(first.getJSONObject("datos").getBoolean("muerto"))
  assertEquals("Daño en rama",first.getJSONObject("datos").getString("observacion"))
 }
 @Test fun noAcknowledgmentWithoutExactServerIdentity() {
  assertTrue(SupabaseProtocol.accepted("[{\"id\":\"r\",\"worker_uid\":\"u\"}]","r","u"))
  assertFalse(SupabaseProtocol.accepted("[]","r","u"))
  assertFalse(SupabaseProtocol.accepted("[{\"id\":\"other\",\"worker_uid\":\"u\"}]","r","u"))
  assertFalse(SupabaseProtocol.accepted("[{\"id\":\"r\",\"worker_uid\":\"other\"}]","r","u"))
 }
 @Test fun transportSendsUpsertHeadersAndRejectsDeniedWrites() {
  val server=ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"))
  var auth="";var prefer="";var posted=""
  val thread=Thread {
   server.accept().use { socket ->
    socket.soTimeout=5000
    val reader=socket.getInputStream().bufferedReader()
    reader.readLine()
    val headers=mutableMapOf<String,String>()
    while(true){val line=reader.readLine()?:break;if(line.isEmpty())break;headers[line.substringBefore(':').lowercase()]=line.substringAfter(':').trim()}
    auth=headers["authorization"].orEmpty();prefer=headers["prefer"].orEmpty()
    val body=CharArray(headers["content-length"]?.toInt()?:0)
    var read=0;while(read<body.size){val n=reader.read(body,read,body.size-read);if(n<0)break;read+=n}
    posted=String(body)
    socket.getOutputStream().write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
   }
  }.apply{isDaemon=true;start()}
  try {
   try { SupabaseProtocol.request("http://127.0.0.1:${server.localPort}","public-key","/records","POST","{\"id\":\"stable\"}","test-token","resolution=merge-duplicates,return=representation");fail("Denied write must throw") }
   catch(e: SupabaseFailure){assertEquals(403,e.status)}
   thread.join(5000)
   assertEquals("Bearer test-token",auth)
   assertEquals("resolution=merge-duplicates,return=representation",prefer)
   assertEquals("stable",JSONObject(posted).getString("id"))
  } finally {server.close();thread.join(5000)}
 }
}
