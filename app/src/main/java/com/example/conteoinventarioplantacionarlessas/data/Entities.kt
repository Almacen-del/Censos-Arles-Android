package com.example.conteoinventarioplantacionarlessas.data

import androidx.room.Entity

import androidx.room.PrimaryKey

import java.util.UUID

@Entity(tableName = "censos")

data class Censo(

    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    val tipo: String, // "FRUTOS", "PRODUCCION", "FLORACION"

    val fecha: Long = System.currentTimeMillis(),

    val estado: String = "EN_PROCESO"

)

@Entity(tableName = "lotes")

data class Lote(

    @PrimaryKey val id: String,

    val nombre: String

)

@Entity(tableName = "lineas")

data class Linea(

    @PrimaryKey val id: String,

    val loteId: String,

    val numero: String,

    val cantidadArboles: Int?

)

@Entity(tableName = "registro_frutos", indices = [androidx.room.Index(value = ["censoId", "lineaId"])])

data class RegistroFrutos(

    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    val censoId: Int,

    val loteId: String,

    val lineaId: String,

    val arbolCodigo: String,

    val posicion: Int,

    val sexo: String?, // "Macho", "Hembra", null

    val frutos: Int?, // null means not entered. 0 is valid.

    val estado: String = "BORRADOR",
    @androidx.room.ColumnInfo(defaultValue = "0") val muerto: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "0") val capturado: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "0") val verificadoEn: Long = 0

)

@Entity(tableName = "registro_produccion", indices = [androidx.room.Index(value = ["censoId", "lineaId"])])

data class RegistroProduccion(

    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    val censoId: Int,

    val loteId: String,

    val lineaId: String,

    val vivos: Int?,

    val muertos: Int?,

    val resiembras: Int?,

    val observacion: String?,

    val estado: String = "BORRADOR"

)

@Entity(tableName = "registro_floracion", indices = [androidx.room.Index(value = ["censoId", "lineaId"])])

data class RegistroFloracion(

    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    val censoId: Int,

    val loteId: String,

    val siembra: String?,

    val lineaId: String,

    val arbolCodigo: String,

    val posicion: Int,

    val sexo: String?, // "Macho", "Hembra", null

    val inflorescencias: Int?,

    val botones: Int?,

    val estado: String = "BORRADOR",
    @androidx.room.ColumnInfo(defaultValue = "0") val muerto: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "0") val capturado: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "0") val verificadoEn: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "''") val observacion: String = "",
    @androidx.room.ColumnInfo(defaultValue = "NULL") val compartidoPor: String? = null

)

@Entity(tableName = "lineas_omitidas", indices = [androidx.room.Index(value = ["censoId", "lineaId"])])

data class LineaOmitida(

    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    val censoId: Int,

    val lineaId: String,
    val responsable: String? = null

)

// Catálogo por planta: el código y la siembra no se deducen del número de árboles.

@Entity(tableName = "plantas", primaryKeys = ["lineaId", "codigo"])

data class Planta(

    val lineaId: String,

    val codigo: String,

    val posicion: Int,

    val siembra: String?,
    val sexo: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "0") val muerto: Boolean = false,
    val alerta: String? = null,
    val filasOrigen: String? = null

)

@Entity(tableName = "avance_floracion", primaryKeys = ["censoId", "lineaId"])

data class AvanceFloracion(val censoId: Int, val lineaId: String, val posicion: Int)


@Entity(tableName = "catalogo_importado")
data class CatalogoImportado(@PrimaryKey val version: String)

data class AvanceLinea(val lineaId: String, val total: Int, val completos: Int) {
    val terminada: Boolean get() = total > 0 && completos >= total
}

@Entity(tableName="borradores_ficha", primaryKeys=["tipo","censoId","lineaId","posicion"])
data class BorradorFicha(val tipo:String,val censoId:Int,val lineaId:String,val posicion:Int,
    val sexo:String?,val muerto:Boolean,val a:String,val b:String,val c:String,val observacion:String,val actualizadoEn:Long)
