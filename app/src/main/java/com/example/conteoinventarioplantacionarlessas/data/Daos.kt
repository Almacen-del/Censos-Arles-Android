package com.example.conteoinventarioplantacionarlessas.data

import androidx.room.Dao

import androidx.room.Insert

import androidx.room.OnConflictStrategy

import androidx.room.Query

import androidx.room.Update

import androidx.room.Transaction

import kotlinx.coroutines.flow.Flow

@Dao

interface InventarioDao {
    @Transaction
    suspend fun createOrGetCenso(tipo:String):Censo {
        getUltimoCenso(tipo)?.let {return it}
        val censo=Censo(tipo=tipo)
        return censo.copy(id=insertCenso(censo).toInt())
    }


    @Query("SELECT * FROM lineas WHERE loteId = :loteId ORDER BY CAST(SUBSTR(numero, 2) AS INTEGER), numero, id")
    suspend fun listarLineasDelLote(loteId: String): List<Linea>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertarLineaNueva(linea: Linea)

    @Transaction
    suspend fun agregarLinea(loteId: String, numero: String, cantidad: Int): Linea {
        require(getLoteById(loteId) != null) { "No se encontró el lote seleccionado." }
        val codigo = LineCreation.code(numero)
        LineCreation.validateCount(cantidad)
        require(listarLineasDelLote(loteId).none { LineCreation.number(it.numero) == LineCreation.number(codigo) }) {
            "La línea $codigo ya existe en este lote. Usa otro número."
        }
        val nueva = Linea("${loteId}_$codigo", loteId, codigo, cantidad)
        require(getLineaById(nueva.id) == null && getPlantas(nueva.id).isEmpty()) {
            "Esta línea ya tiene datos. Usa otro número."
        }
        insertarLineaNueva(nueva)
        // Persist the catalogue atomically; opening a census creates its blank forms.
        for (inicio in 1..cantidad step 400) {
            insertPlantas((inicio..minOf(inicio + 399, cantidad)).map { posicion ->
                Planta(nueva.id, "P" + posicion.toString().padStart(3, '0'), posicion, siembra = null)
            })
        }
        return nueva
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)

    suspend fun insertCenso(censo: Censo): Long

    @Query("SELECT * FROM censos WHERE tipo = :tipo ORDER BY fecha DESC")

    fun getCensosPorTipo(tipo: String): Flow<List<Censo>>

    @Query("SELECT * FROM censos WHERE id = :id")

    suspend fun getCensoById(id: Int): Censo?

    @Query("SELECT * FROM lotes WHERE id NOT IN ('L001','L002') ORDER BY id")

    fun getLotes(): Flow<List<Lote>>

    @Query("SELECT * FROM lineas WHERE loteId = :loteId ORDER BY CAST(SUBSTR(numero, 2) AS INTEGER), numero, id")

    fun getLineasPorLote(loteId: String): Flow<List<Linea>>

    @Query("SELECT * FROM lineas WHERE id = :id")

    suspend fun getLineaById(id: String): Linea?

    // FRUTOS

    @Insert(onConflict = OnConflictStrategy.REPLACE)

    suspend fun insertRegistroFrutos(registro: RegistroFrutos): Long

    @Query("SELECT * FROM registro_frutos WHERE censoId = :censoId AND lineaId = :lineaId ORDER BY posicion ASC")

    fun getRegistrosFrutos(censoId: Int, lineaId: String): Flow<List<RegistroFrutos>>

    @Query("SELECT * FROM registro_frutos WHERE censoId = :censoId AND lineaId = :lineaId AND posicion = :posicion")

    suspend fun getRegistroFrutos(censoId: Int, lineaId: String, posicion: Int): RegistroFrutos?

    // PRODUCCION

    @Insert(onConflict = OnConflictStrategy.REPLACE)

    suspend fun insertRegistroProduccion(registro: RegistroProduccion): Long

    @Query("SELECT * FROM registro_produccion WHERE censoId = :censoId AND loteId = :loteId ORDER BY id ASC")

    fun getRegistrosProduccion(censoId: Int, loteId: String): Flow<List<RegistroProduccion>>

    @Query("SELECT * FROM registro_produccion WHERE censoId = :censoId AND lineaId = :lineaId")

    suspend fun getRegistroProduccion(censoId: Int, lineaId: String): RegistroProduccion?

    // FLORACION

    @Insert(onConflict = OnConflictStrategy.REPLACE)

    suspend fun insertRegistroFloracion(registro: RegistroFloracion): Long

    @Query("SELECT * FROM registro_floracion WHERE censoId = :censoId AND lineaId = :lineaId ORDER BY posicion ASC")

    fun getRegistrosFloracion(censoId: Int, lineaId: String): Flow<List<RegistroFloracion>>

    @Query("SELECT * FROM registro_floracion WHERE censoId = :censoId AND lineaId = :lineaId AND posicion = :posicion")

    suspend fun getRegistroFloracion(censoId: Int, lineaId: String, posicion: Int): RegistroFloracion?

    // LINEAS OMITIDAS

    @Insert(onConflict = OnConflictStrategy.REPLACE)

    suspend fun insertLineaOmitida(linea: LineaOmitida): Long

    @Query("DELETE FROM lineas_omitidas WHERE censoId = :censoId AND lineaId = :lineaId")

    suspend fun deleteLineaOmitida(censoId: Int, lineaId: String)

    @Query("SELECT * FROM lineas_omitidas WHERE censoId = :censoId")

    fun getLineasOmitidas(censoId: Int): Flow<List<LineaOmitida>>



    // MOCK DATA

    @Insert(onConflict = OnConflictStrategy.REPLACE)

    suspend fun insertLotes(lotes: List<Lote>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)

    suspend fun insertLineas(lineas: List<Linea>)

    @Query("SELECT * FROM lotes WHERE id = :id")

    suspend fun getLoteById(id: String): Lote?

    @Query("SELECT * FROM censos WHERE tipo = :tipo ORDER BY fecha DESC, id DESC LIMIT 1")

    suspend fun getUltimoCenso(tipo: String): Censo?

    @Query("SELECT * FROM plantas WHERE lineaId = :lineaId ORDER BY posicion, codigo")

    suspend fun getPlantas(lineaId: String): List<Planta>

    @Insert(onConflict = OnConflictStrategy.IGNORE)

    suspend fun insertPlantas(plantas: List<Planta>)

    @Query("SELECT * FROM registro_floracion WHERE censoId = :censoId AND lineaId = :lineaId ORDER BY posicion, id")

    suspend fun listarFloracion(censoId: Int, lineaId: String): List<RegistroFloracion>

    @Query("SELECT * FROM avance_floracion WHERE censoId = :censoId AND lineaId = :lineaId")

    suspend fun getAvanceFloracion(censoId: Int, lineaId: String): AvanceFloracion?

    @Insert(onConflict = OnConflictStrategy.REPLACE)

    suspend fun guardarAvanceFloracion(avance: AvanceFloracion)

    @Transaction

    suspend fun prepararFloracion(censoId: Int, lineaId: String): List<RegistroFloracion> {

        val existentes = listarFloracion(censoId, lineaId)

        // Los registros históricos se conservan, incluidos sus códigos originales.

        if (existentes.isNotEmpty()) return existentes

        val linea = requireNotNull(getLineaById(lineaId)) { "No se encontró la línea." }

        val plantas = getPlantas(lineaId)

        if (plantas.isEmpty() && linea.cantidadArboles == 0) return emptyList()
        require(plantas.isNotEmpty()) { "Falta cargar el catálogo de plantas de esta línea." }

        require(linea.cantidadArboles == plantas.size) { "Revisa la cantidad de árboles y el catálogo de esta línea." }

        require(plantas.map { it.posicion }.distinct().size == plantas.size) { "El catálogo tiene posiciones repetidas." }

        plantas.forEach { planta ->

            insertRegistroFloracion(RegistroFloracion(

                censoId = censoId, loteId = linea.loteId, lineaId = lineaId,

                siembra = planta.siembra, arbolCodigo = planta.codigo,

                posicion = planta.posicion, sexo = planta.sexo, muerto = planta.muerto, inflorescencias = null, botones = null

            ))

        }

        return listarFloracion(censoId, lineaId)

    }

    @Transaction

    suspend fun guardarFloracionConAvance(registro: RegistroFloracion, posicion: Int) {
        require(getRegistroFloracion(registro.censoId,registro.lineaId,registro.posicion)?.compartidoPor == null) { "Este conteo pertenece a otro celular." }
        insertRegistroFloracion(registro)
        actualizarPlanta(registro.lineaId, registro.arbolCodigo, registro.sexo, registro.muerto)

        guardarAvanceFloracion(AvanceFloracion(registro.censoId, registro.lineaId, posicion))

    }


    @Query("SELECT * FROM plantas WHERE lineaId=:lineaId AND codigo=:codigo")
    suspend fun getPlanta(lineaId: String, codigo: String): Planta?

    @Query("UPDATE plantas SET sexo=:sexo, muerto=:muerto WHERE lineaId=:lineaId AND codigo=:codigo")
    suspend fun actualizarPlanta(lineaId: String, codigo: String, sexo: String?, muerto: Boolean)

    @Query("SELECT * FROM catalogo_importado WHERE version=:version")
    suspend fun catalogoImportado(version: String): CatalogoImportado?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun marcarCatalogo(catalogo: CatalogoImportado)

    @Query("SELECT * FROM registro_frutos WHERE censoId=:censoId AND lineaId=:lineaId ORDER BY posicion,id")
    suspend fun listarFrutos(censoId: Int, lineaId: String): List<RegistroFrutos>

    @Transaction
    suspend fun prepararFrutos(censoId: Int, lineaId: String): List<RegistroFrutos> {
        val existentes = listarFrutos(censoId, lineaId)
        if (existentes.isNotEmpty()) return existentes
        val linea = requireNotNull(getLineaById(lineaId))
        val plantas = getPlantas(lineaId)
        require(plantas.isNotEmpty() && linea.cantidadArboles == plantas.size) { "Falta el catálogo de esta línea." }
        plantas.forEach { p -> insertRegistroFrutos(RegistroFrutos(censoId=censoId, loteId=linea.loteId,
            lineaId=lineaId, arbolCodigo=p.codigo, posicion=p.posicion, sexo=p.sexo, frutos=null, muerto=p.muerto)) }
        return listarFrutos(censoId,lineaId)
    }

    @Transaction
    suspend fun guardarFrutosYPlanta(registro: RegistroFrutos) {
        insertRegistroFrutos(registro)
        actualizarPlanta(registro.lineaId,registro.arbolCodigo,registro.sexo,registro.muerto)
    }

    @Query("SELECT * FROM lineas_omitidas WHERE censoId=:censoId AND lineaId=:lineaId LIMIT 1")
    suspend fun bloqueo(censoId: Int, lineaId: String): LineaOmitida?

    @Transaction
    suspend fun bloquearLineas(censoId: Int, loteId: String, ids: List<String>, responsable: String?) {
        require(getCensoById(censoId) != null) { "No se encontró el censo." }
        require(ids.isNotEmpty()) { "Selecciona un rango de líneas." }
        ids.distinct().forEach { id ->
            require(getLineaById(id)?.loteId == loteId) { "Todas las líneas deben pertenecer al lote seleccionado." }
        }
        ids.distinct().forEach { id ->
            val old = bloqueo(censoId,id)
            insertLineaOmitida(LineaOmitida(id=old?.id ?: 0,censoId=censoId,lineaId=id,
                responsable=responsable?.trim()?.takeIf { it.isNotEmpty() } ?: old?.responsable))
        }
    }

    @Query("""
        SELECT l.id AS lineaId,
          CASE WHEN :tipo='PRODUCCION' THEN 1 ELSE COALESCE(l.cantidadArboles,0) END AS total,
          CASE :tipo
            WHEN 'FRUTOS' THEN (SELECT COUNT(DISTINCT r.arbolCodigo) FROM registro_frutos r
              JOIN plantas p ON p.lineaId=r.lineaId AND p.codigo=r.arbolCodigo
              WHERE r.censoId=:censoId AND r.lineaId=l.id AND r.estado='COMPLETO' AND r.capturado=1
              AND NOT EXISTS (SELECT 1 FROM borradores_ficha d WHERE d.tipo=:tipo AND d.censoId=r.censoId AND d.lineaId=r.lineaId AND d.posicion=r.posicion AND d.actualizadoEn>r.verificadoEn)
              AND ((r.muerto=1 AND r.frutos IS NULL) OR (r.muerto=0 AND
                ((r.sexo='Macho' AND r.frutos IS NULL) OR (r.sexo='Hembra' AND r.frutos>=0)))))
            WHEN 'FLORACION' THEN (SELECT COUNT(DISTINCT r.arbolCodigo) FROM registro_floracion r
              JOIN plantas p ON p.lineaId=r.lineaId AND p.codigo=r.arbolCodigo
              WHERE r.censoId=:censoId AND r.lineaId=l.id AND r.estado='COMPLETO' AND r.capturado=1
              AND NOT EXISTS (SELECT 1 FROM borradores_ficha d WHERE d.tipo=:tipo AND d.censoId=r.censoId AND d.lineaId=r.lineaId AND d.posicion=r.posicion AND d.actualizadoEn>r.verificadoEn)
              AND ((r.muerto=1 AND r.inflorescencias IS NULL AND r.botones IS NULL) OR
                (r.muerto=0 AND r.sexo IN ('Macho','Hembra') AND
                  ((r.inflorescencias=0 AND r.botones IS NULL) OR (r.inflorescencias>0 AND r.botones>=0)))))
            WHEN 'PRODUCCION' THEN (SELECT CASE WHEN COUNT(*)>0 THEN 1 ELSE 0 END FROM registro_produccion r
              WHERE r.censoId=:censoId AND r.lineaId=l.id AND r.estado='COMPLETO'
                AND r.vivos>=0 AND r.muertos>=0 AND r.resiembras>=0)
            ELSE 0 END AS completos
        FROM lineas l WHERE l.loteId=:loteId ORDER BY CAST(SUBSTR(l.numero, 2) AS INTEGER), l.numero, l.id
    """)
    fun avanceLineas(censoId: Int, loteId: String, tipo: String): Flow<List<AvanceLinea>>

    @Insert(onConflict=OnConflictStrategy.REPLACE)
    suspend fun guardarBorrador(borrador:BorradorFicha)
    @Query("SELECT * FROM borradores_ficha WHERE tipo=:tipo AND censoId=:censo AND lineaId=:linea AND posicion=:posicion")
    suspend fun leerBorrador(tipo:String,censo:Int,linea:String,posicion:Int):BorradorFicha?
    @Query("SELECT * FROM borradores_ficha WHERE tipo=:tipo AND censoId=:censo AND lineaId=:linea")
    fun borradores(tipo:String,censo:Int,linea:String):Flow<List<BorradorFicha>>

    @Query("UPDATE plantas SET codigo=:nuevo, posicion=:posicion WHERE lineaId=:linea AND codigo=:anterior")
    suspend fun moverPlanta(linea:String, anterior:String, nuevo:String, posicion:Int)
    @Query("UPDATE borradores_ficha SET posicion=:nueva WHERE tipo='FLORACION' AND censoId=:censo AND lineaId=:linea AND posicion=:anterior")
    suspend fun moverBorradorFloracion(censo:Int,linea:String,anterior:Int,nueva:Int)
    @Query("UPDATE lineas SET cantidadArboles=:cantidad WHERE id=:linea")
    suspend fun actualizarCantidadArboles(linea:String,cantidad:Int)

    /** One transaction preserves record IDs (including cloud IDs), measurements and draft ownership. */
    @Transaction
    suspend fun agregarArbolFloracion(censoId:Int,lineaId:String,alPrincipio:Boolean):Int {
        require(getCensoById(censoId)?.tipo=="FLORACION") { "No se encontró el censo de floración." }
        val linea=requireNotNull(getLineaById(lineaId))
        val rows=prepararFloracion(censoId,lineaId)
        val plantas=getPlantas(lineaId)
        require(rows.size==plantas.size && rows.all {r->plantas.any {it.codigo==r.arbolCodigo && it.posicion==r.posicion}}) {
            "El catálogo de este censo cambió. Revisa la línea antes de agregar árboles."
        }
        require(!alPrincipio || rows.none { it.compartidoPor != null }) { "Esta línea tiene conteos de otro celular. Solo puedes agregar al final para conservar su numeración." }
        val plan=TreeInsertion.plan(plantas,alPrincipio)
        if(alPrincipio) {
            // Move primary keys from the end to avoid collisions without replacing rows.
            for(move in plan.moves.sortedByDescending {TreeInsertion.number(it.old.codigo)}) {
                moverPlanta(lineaId,move.old.codigo,move.newCode,move.newPosition)
            }
            for(row in rows) {
                val move=plan.moves.single {it.old.codigo==row.arbolCodigo}
                insertRegistroFloracion(row.copy(arbolCodigo=move.newCode,posicion=move.newPosition))
            }
            for(move in plan.moves.sortedByDescending {it.old.posicion}) {
                moverBorradorFloracion(censoId,lineaId,move.old.posicion,move.newPosition)
            }
            getAvanceFloracion(censoId,lineaId)?.let { guardarAvanceFloracion(it.copy(posicion=it.posicion+1)) }
        }
        insertPlantas(listOf(Planta(lineaId,plan.code,plan.position,null)))
        insertRegistroFloracion(RegistroFloracion(censoId=censoId,loteId=linea.loteId,siembra=null,lineaId=lineaId,
            arbolCodigo=plan.code,posicion=plan.position,sexo=null,inflorescencias=null,botones=null))
        actualizarCantidadArboles(lineaId,plantas.size+1)
        guardarAvanceFloracion(AvanceFloracion(censoId,lineaId,plan.position))
        return plan.position
    }
    @Query("DELETE FROM plantas WHERE lineaId=:linea AND codigo=:codigo")
    suspend fun quitarPlanta(linea:String,codigo:String)
    @Query("DELETE FROM registro_floracion WHERE id=:id")
    suspend fun quitarFloracion(id:Int)
    @Query("DELETE FROM borradores_ficha WHERE tipo='FLORACION' AND censoId=:censo AND lineaId=:linea AND posicion=:posicion")
    suspend fun quitarBorradorFloracion(censo:Int,linea:String,posicion:Int)

    @Transaction
    suspend fun eliminarArbolCreado(censo:Int,linea:String,posicion:Int) {
        require(getCensoById(censo)?.tipo=="FLORACION")
        val registro=requireNotNull(getRegistroFloracion(censo,linea,posicion))
        val planta=requireNotNull(getPlanta(linea,registro.arbolCodigo))
        require(planta.filasOrigen.isNullOrBlank() && planta.siembra==null && !registro.capturado) {
            "Solo se pueden eliminar árboles agregados sin conteo guardado."
        }
        val registros=listarFloracion(censo,linea)
        val plantas=getPlantas(linea)
        require(registros.size==plantas.size && registros.all {r->plantas.any {it.codigo==r.arbolCodigo && it.posicion==r.posicion}} && planta.posicion==posicion) {
            "El catálogo cambió. Vuelve a abrir la línea."
        }
        require(registros.none { it.posicion >= posicion && it.compartidoPor != null }) { "No se puede cambiar la numeración de conteos recibidos de otro celular." }
        val numero=TreeInsertion.number(planta.codigo)
        val posteriores=plantas.filter {it.posicion>posicion}
        require(posteriores.all {TreeInsertion.number(it.codigo)>numero}) { "La numeración no coincide con el orden del surco." }
        quitarBorradorFloracion(censo,linea,posicion)
        quitarFloracion(registro.id)
        quitarPlanta(linea,planta.codigo)
        // Reverse the insertion while preserving stable record IDs and draft ownership.
        for(p in posteriores.sortedBy {TreeInsertion.number(it.codigo)}) {
            moverPlanta(linea,p.codigo,TreeInsertion.code(TreeInsertion.number(p.codigo)-1),p.posicion-1)
        }
        for(r in registros.filter {it.posicion>posicion}) {
            insertRegistroFloracion(r.copy(arbolCodigo=TreeInsertion.code(TreeInsertion.number(r.arbolCodigo)-1),posicion=r.posicion-1))
        }
        for(p in posteriores.sortedBy {it.posicion}) moverBorradorFloracion(censo,linea,p.posicion,p.posicion-1)
        val restantes=getPlantas(linea)
        actualizarCantidadArboles(linea,restantes.size)
        guardarAvanceFloracion(AvanceFloracion(censo,linea,restantes.firstOrNull()?.posicion ?: 0))
    }
    /** Notes are saved independently from census completion and never complete a pending count. */
    @Query("UPDATE registro_floracion SET observacion=:nota, capturado=1 WHERE censoId=:censo AND lineaId=:linea AND posicion=:posicion AND observacion<>:nota")
    suspend fun actualizarObservacion(censo:Int,linea:String,posicion:Int,nota:String):Int

    @Transaction
    suspend fun guardarBorradorConObservacion(borrador:BorradorFicha):Boolean {
        if (borrador.tipo == "FLORACION") require(getRegistroFloracion(borrador.censoId,borrador.lineaId,borrador.posicion)?.compartidoPor == null) { "Este conteo pertenece a otro celular." }
        guardarBorrador(borrador)
        return borrador.tipo=="FLORACION" && actualizarObservacion(borrador.censoId,borrador.lineaId,borrador.posicion,borrador.observacion)>0
    }
}
