package ar.jornadas.data

import androidx.room.*
import retrofit2.http.GET
import kotlinx.coroutines.flow.Flow

data class FichaRemota(val id: String, val title: String, val track: String, val day: Int,
    val time: String, val room: String, val speaker: String, val summary: String)

interface ProgramaApi {
    @GET("programa") suspend fun consultar(): List<FichaRemota>
}

@Entity(tableName = "charlas")
data class Charla(@PrimaryKey val id: String, val titulo: String, val tema: String, val dia: Int,
    val hora: String, val sala: String, val ponente: String, val resumen: String,
    val favorita: Boolean = false)

// Una sola fila: existe si alguna vez se guardó una edición completa (aunque venga vacía).
@Entity(tableName = "edicion")
data class Edicion(@PrimaryKey val id: Int = 1, val descargadaEn: Long)

@Dao
interface CarteleraDao {
    @Query("SELECT EXISTS(SELECT 1 FROM edicion)")
    fun observarEdicion(): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM edicion)")
    suspend fun hayEdicion(): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarEdicion(edicion: Edicion)

    @Query("SELECT * FROM charlas ORDER BY dia, hora, sala")
    fun observar(): Flow<List<Charla>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(fichas: List<Charla>)

    @Query("UPDATE charlas SET favorita = NOT favorita WHERE id = :id")
    suspend fun alternar(id: String)

    @Query("SELECT id FROM charlas WHERE favorita = 1")
    suspend fun favoritas(): List<String>

    @Query("DELETE FROM charlas")
    suspend fun borrarTodo()

    // Instantánea completa: las retiradas se borran y las vigentes conservan la marca por id.
    // Las favoritas se leen al escribir (no al iniciar la descarga), así se respetan
    // las marcas hechas durante la petición.
    @Transaction
    suspend fun reemplazarPrograma(nuevas: List<Charla>) {
        val marcadas = favoritas().toSet()
        borrarTodo()
        guardar(nuevas.map { it.copy(favorita = it.id in marcadas) })
        guardarEdicion(Edicion(descargadaEn = System.currentTimeMillis()))
    }
}

@Database(entities = [Charla::class, Edicion::class], version = 2, exportSchema = false)
abstract class Archivo : RoomDatabase() {
    abstract fun cartelera(): CarteleraDao
}

// v1 -> v2: crea la tabla edicion sin tocar charlas ni favoritas.
// Si el usuario ya tenía charlas guardadas, cuenta como edición previa.
val MIGRACION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `edicion` (`id` INTEGER NOT NULL, `descargadaEn` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO edicion (id, descargadaEn) SELECT 1, 0 WHERE EXISTS (SELECT 1 FROM charlas)")
    }
}
