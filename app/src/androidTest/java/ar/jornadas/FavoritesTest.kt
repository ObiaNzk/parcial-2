package ar.jornadas

import android.content.Context
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
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

// Request 42: personal agenda across program editions.
@RunWith(AndroidJUnit4::class)
class FavoritesTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val talk11v1 = "Compose en producción"
    private val talk11v2 = "Compose en producción avanzada"

    @Before fun startWithoutData() {
        context.deleteDatabase("cartelera.db")
        setService(revision = 1)
    }

    @Test fun markSurvivesChangeAndRetiredTalkReturnsUnmarked() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText(talk11v1)
            toggleFavorite(talk11v1)

            // v2: same id, new title and room, still marked.
            refreshTo(revision = 2)
            waitForText(talk11v2)
            compose.onNodeWithText(talk11v2).performClick()
            compose.onNodeWithText("Día 1 · 09:00 · Sala Puerto").assertIsDisplayed()
            compose.onNodeWithText("Quitar de mi agenda").assertIsDisplayed()
            compose.onNodeWithText("Volver").performClick()

            // v3: talk 11 is retired from the program and from Mi agenda.
            refreshTo(revision = 3)
            waitForTextGone(talk11v2)
            compose.onNodeWithText("Mi agenda").performClick()
            compose.onAllNodesWithText(talk11v2).assertCountEquals(0)
            compose.onNodeWithText("Programa").performClick()
            // Returning to the catalog starts its own download (request 44): let it finish.
            compose.waitUntil(3_000) {
                compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
                    .fetchSemanticsNodes().isNotEmpty()
            }

            // v1 again: talk 11 comes back unmarked.
            refreshTo(revision = 1)
            waitForText(talk11v1)
            compose.onNodeWithText(talk11v1).performClick()
            compose.onNodeWithText("Agregar a mi agenda").assertIsDisplayed()
        }
    }

    @Test fun markMadeDuringDownloadIsKept() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText(talk11v1)
            waitForDownloadsToFinish()

            setService(revision = 2, slow = true)
            compose.onNodeWithText("Actualizar").performClick()
            toggleFavorite(talk11v1) // while the 8 s request is still running

            waitForText(talk11v2, timeoutMs = 20_000)
            waitForDownloadsToFinish()
            compose.onNodeWithText(talk11v2).performClick()
            compose.onNodeWithText("Quitar de mi agenda").assertIsDisplayed()
        }
    }

    private fun toggleFavorite(title: String) {
        compose.onNodeWithText(title).performClick()
        compose.onNodeWithText("Agregar a mi agenda").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Volver").performClick()
    }

    // Waits first: returning to the catalog triggers its own download (request 44),
    // and two parallel downloads could finish in either order.
    private fun refreshTo(revision: Int) {
        waitForDownloadsToFinish()
        setService(revision)
        compose.onNodeWithText("Actualizar").performClick()
    }

    // Same preferences file used by the Mesa screen.
    private fun setService(revision: Int, slow: Boolean = false) {
        context.getSharedPreferences("mesa", Context.MODE_PRIVATE).edit()
            .putBoolean("disponible", true)
            .putInt("revision", revision)
            .putBoolean("demoraLarga", slow)
            .commit()
    }

    private fun waitForText(text: String, timeoutMs: Long = 5_000) = compose.waitUntil(timeoutMs) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForTextGone(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
    }

    // The catalog shows an indeterminate progress bar while a request is running.
    private fun waitForDownloadsToFinish() = compose.waitUntil(20_000) {
        compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .fetchSemanticsNodes().isEmpty()
    }
}
