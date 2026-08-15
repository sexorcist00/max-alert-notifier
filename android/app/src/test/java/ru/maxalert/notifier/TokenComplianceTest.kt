package ru.maxalert.notifier

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The token system's own guard rail: components must not write raw colours.
 *
 * This is the Kotlin equivalent of the design-system skill's token validator. Colour
 * literals belong in the primitive layer; anywhere else they are a value that light and
 * dark themes cannot both satisfy and that nothing can re-theme later.
 */
private val COLOUR_LITERAL = Regex("""Color\(\s*0x[0-9A-Fa-f]{8}""")

private val ALLOWED = setOf(
    "ui/Tokens.kt",   // the primitive layer -- raw values live here by definition
    "Theme.kt",       // the semantic layer maps primitives onto Material roles
    "AlertLevel.kt",  // level colours are domain tokens, referenced by name elsewhere
)

private fun sourceFiles(): List<File> {
    val root = File("src/main/java/ru/maxalert/notifier")
    return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
}

class TokenComplianceNegativeCasesTest {

    @Test
    fun `no screen writes a raw colour literal`() {
        val offenders = sourceFiles()
            .filter { file -> ALLOWED.none { allowed -> file.path.endsWith(allowed) } }
            .filter { file -> COLOUR_LITERAL.containsMatchIn(file.readText()) }
            .map { file -> file.path }

        assertTrue(
            "Сырой цвет вне слоя токенов: $offenders — используйте LocalAlertPalette или тему",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the check is actually looking at the sources`() {
        // A guard that would otherwise pass silently if the path ever moved.
        assertTrue("Исходники не найдены — проверка ничего не проверяет", sourceFiles().size > 5)
    }
}

class TokenCompliancePositiveCasesTest {

    @Test
    fun `the primitive layer is where the raw values are`() {
        val tokens = sourceFiles().first { it.path.endsWith("ui/Tokens.kt") }.readText()
        assertTrue(COLOUR_LITERAL.containsMatchIn(tokens))
    }
}
