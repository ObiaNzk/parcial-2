package ar.jornadas

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Request 41: program without network.
@RunWith(AndroidJUnit4::class)
class OfflineProgramTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun startWithoutData() {
        context.deleteDatabase("cartelera.db")
        setService(available = true)
    }

    @Test fun failureWithSavedDataKeepsList() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("Compose en producción")

            setService(available = false)
            compose.onNodeWithText("Actualizar").performClick()

            waitForText("Sin conexión: usando datos guardados")
            compose.onNodeWithText("Compose en producción").assertIsDisplayed()
        }
    }

    @Test fun failureWithoutDataOffersRetry() {
        setService(available = false)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("No se pudo descargar el programa")
            compose.onNodeWithText("Reintentar").assertIsDisplayed()
            compose.onAllNodesWithText("Compose en producción").assertCountEquals(0)
        }
    }

    // Same preferences file used by the Mesa screen.
    private fun setService(available: Boolean) {
        context.getSharedPreferences("mesa", Context.MODE_PRIVATE).edit()
            .putBoolean("disponible", available)
            .putInt("revision", 1)
            .putBoolean("demoraLarga", false)
            .commit()
    }

    private fun waitForText(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}
