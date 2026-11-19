package ar.jornadas.ui

import android.app.Application
import androidx.lifecycle.*
import ar.jornadas.Servicios
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException

class ProgramaModel(app: Application, private val estado: SavedStateHandle) : AndroidViewModel(app) {
    private val servicios = Servicios(app)
    val mesa = servicios.mesa
    private val repo = servicios.agenda
    val charlas = repo.programa.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Filtros en el SavedStateHandle: sobreviven a la rotación y a la muerte del proceso.
    val dia = estado.getStateFlow("dia", 0)
    val tema = estado.getStateFlow("tema", "Todos")
    fun elegirDia(indice: Int) { estado["dia"] = indice }
    fun elegirTema(nombre: String) { estado["tema"] = nombre }

    val aviso = MutableStateFlow("")
    val cargando = MutableStateFlow(false)

    fun recargar() {
        viewModelScope.launch {
            cargando.value = true
            aviso.value = ""
            try {
                repo.sincronizar()
            } catch (e: IOException) {
                aviso.value = if (charlas.value.isNotEmpty()) {
                    "Sin conexión: usando datos guardados"
                } else {
                    "No se pudo descargar el programa"
                }
            } finally {
                cargando.value = false
            }
        }
    }

    fun marcar(id: String) { viewModelScope.launch { repo.cambiarFavorita(id) } }
}
