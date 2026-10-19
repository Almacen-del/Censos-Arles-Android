package com.example.conteoinventarioplantacionarlessas.data

import kotlinx.coroutines.flow.Flow

class InventarioRepository(private val dao: InventarioDao, private val onSaved: () -> Unit = {}) {

    suspend fun agregarLinea(loteId: String, numero: String, cantidad: Int): Linea =
        dao.agregarLinea(loteId, numero, cantidad).also { onSaved() }

    val draftQueue = DraftQueue(dao,onSaved)
    fun borradores(tipo:String,censo:Int,linea:String) = dao.borradores(tipo,censo,linea)
    suspend fun leerBorrador(tipo:String,censo:Int,linea:String,posicion:Int) = dao.leerBorrador(tipo,censo,linea,posicion)

    suspend fun agregarArbolFloracion(censo:Int,linea:String,alPrincipio:Boolean):Int {
        check(draftQueue.flush()) { "No se pudieron guardar los borradores. Inténtalo de nuevo." }
        val position=dao.agregarArbolFloracion(censo,linea,alPrincipio)
        onSaved()
        return position
    }

    suspend fun eliminarArbolCreado(censo:Int,linea:String,posicion:Int) {
        check(draftQueue.flush()) { "No se pudieron guardar los borradores. Inténtalo de nuevo." }
        dao.eliminarArbolCreado(censo,linea,posicion)
        onSaved()
    }

    fun getLotes(): Flow<List<Lote>> = dao.getLotes()

    fun getLineas(loteId: String): Flow<List<Linea>> = dao.getLineasPorLote(loteId)

    suspend fun getLineaById(id: String): Linea? = dao.getLineaById(id)

    suspend fun createOrGetCenso(tipo: String): Censo = dao.createOrGetCenso(tipo)

    suspend fun nuevoCenso(tipo: String): Censo {

        val nuevoCenso = Censo(tipo = tipo)

        val id = dao.insertCenso(nuevoCenso)

        return nuevoCenso.copy(id = id.toInt())

    }

    fun getCensos(tipo: String): Flow<List<Censo>> = dao.getCensosPorTipo(tipo)

    suspend fun getCensoById(id: Int): Censo? = dao.getCensoById(id)

    // Frutos

    fun getRegistrosFrutos(censoId: Int, lineaId: String): Flow<List<RegistroFrutos>> = dao.getRegistrosFrutos(censoId, lineaId)

    suspend fun getRegistroFrutos(censoId: Int, lineaId: String, posicion: Int): RegistroFrutos? = dao.getRegistroFrutos(censoId, lineaId, posicion)

    suspend fun saveRegistroFrutos(registro: RegistroFrutos) {

        dao.guardarFrutosYPlanta(registro)
        onSaved()

    }

    // Produccion

    fun getRegistrosProduccion(censoId: Int, loteId: String): Flow<List<RegistroProduccion>> = dao.getRegistrosProduccion(censoId, loteId)

    suspend fun getRegistroProduccion(censoId: Int, lineaId: String): RegistroProduccion? = dao.getRegistroProduccion(censoId, lineaId)

    suspend fun saveRegistroProduccion(registro: RegistroProduccion) {

        dao.insertRegistroProduccion(registro)
        onSaved()

    }

    suspend fun prepararFloracion(censoId: Int, lineaId: String) = dao.prepararFloracion(censoId, lineaId)

    suspend fun getLoteById(id: String) = dao.getLoteById(id)

    suspend fun getAvanceFloracion(censoId: Int, lineaId: String) = dao.getAvanceFloracion(censoId, lineaId)

    suspend fun guardarAvanceFloracion(avance: AvanceFloracion) = dao.guardarAvanceFloracion(avance)

    suspend fun guardarFloracionConAvance(registro: RegistroFloracion, posicion: Int) {
        dao.guardarFloracionConAvance(registro, posicion)
        onSaved()
    }

    // Floracion

    fun getRegistrosFloracion(censoId: Int, lineaId: String): Flow<List<RegistroFloracion>> = dao.getRegistrosFloracion(censoId, lineaId)

    suspend fun getRegistroFloracion(censoId: Int, lineaId: String, posicion: Int): RegistroFloracion? = dao.getRegistroFloracion(censoId, lineaId, posicion)

    suspend fun saveRegistroFloracion(registro: RegistroFloracion) {

        dao.insertRegistroFloracion(registro)
        onSaved()

    }

    fun getAvanceLineas(censoId: Int, loteId: String, tipo: String) = dao.avanceLineas(censoId,loteId,tipo)
    suspend fun bloquearLineas(censoId: Int, loteId: String, ids: List<String>, responsable: String?) = dao.bloquearLineas(censoId,loteId,ids,responsable)

    // Lineas omitidas

    fun getLineasOmitidas(censoId: Int): Flow<List<LineaOmitida>> = dao.getLineasOmitidas(censoId)

    suspend fun omitirLinea(censoId: Int, lineaId: String) {

        dao.bloquearLineas(censoId, requireNotNull(dao.getLineaById(lineaId)).loteId, listOf(lineaId), null)

    }

    suspend fun deshacerOmitirLinea(censoId: Int, lineaId: String) {

        dao.deleteLineaOmitida(censoId, lineaId)

    }

    suspend fun prepararFrutos(censoId: Int, lineaId: String) = dao.prepararFrutos(censoId, lineaId)
    suspend fun getPlantas(lineaId: String) = dao.getPlantas(lineaId)
    suspend fun getPlanta(lineaId: String, codigo: String) = dao.getPlanta(lineaId, codigo)

    // Mock Data

    suspend fun loadMockData() {

        if (!com.example.conteoinventarioplantacionarlessas.BuildConfig.DEBUG) return

        val lotes = listOf(

            Lote("L001", "Demostración · Norte"),

            Lote("L002", "Demostración · Sur")

        )

        val lineas = listOf(

            Linea("L001-01", "L001", "01", 10),

            Linea("L001-02", "L001", "02", 5),

            Linea("L002-01", "L002", "01", 15),

            Linea("L002-02", "L002", "02", 8)

        )

        dao.insertLotes(lotes)

        dao.insertLineas(lineas)

        // Solo catálogo ficticio de debug; no procede del Excel ni se sincroniza.

        dao.insertPlantas(lineas.flatMap { linea ->

            (1..requireNotNull(linea.cantidadArboles)).map { posicion ->

                Planta(linea.id, "P" + posicion.toString().padStart(3, '0'), posicion, "2020")

            }

        })

    }

}
