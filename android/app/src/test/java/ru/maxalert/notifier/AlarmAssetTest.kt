package ru.maxalert.notifier

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The catalogue, the shipped files and their provenance must agree.
 *
 * Two things already went wrong here. The default sound was once set to a tone that was not in
 * the catalogue, so the settings screen said "свой файл" about the app's own choice. And when
 * the synthesised sirens were replaced by recordings, a stale name would have resolved to no
 * resource at all -- an alarm that plays nothing, discovered on the night it matters.
 */
private val RAW = File("src/main/res/raw")
// Tests run with app/ as the working directory: app -> android -> repository root.
private val SOUND_DOC = File("../../docs/sounds.md")

private fun shippedNames(): List<String> =
    RAW.listFiles().orEmpty().filter { it.isFile }.map { it.nameWithoutExtension }.sorted()

private fun catalogueNames(): List<String> = AlarmSounds.CATALOGUE
    .map { choice -> choice.uri.removePrefix(AlarmController.BUNDLED_PREFIX) }
    .sorted()

class AlarmAssetNegativeCasesTest {

    @Test
    fun `no catalogue entry points at a file that is not shipped`() {
        val missing = catalogueNames() - shippedNames().toSet()

        assertTrue("В каталоге есть звук без файла: $missing", missing.isEmpty())
    }

    @Test
    fun `no shipped file is missing from the catalogue`() {
        val orphans = shippedNames() - catalogueNames().toSet()

        assertTrue("Файл есть, а в каталоге нет: $orphans", orphans.isEmpty())
    }

    @Test
    fun `no shipped file is missing from the provenance document`() {
        val doc = SOUND_DOC.readText()
        val undocumented = shippedNames().filterNot { name -> doc.contains(name) }

        assertTrue("Нет строки в docs/sounds.md: $undocumented", undocumented.isEmpty())
    }

    @Test
    fun `no leftover WAV survives next to an OGG of the same name`() {
        // Two files with one resource name is a build error on Android, and the first symptom
        // is a red build with no obvious cause.
        val duplicates = RAW.listFiles().orEmpty()
            .groupBy { it.nameWithoutExtension }
            .filter { (_, files) -> files.size > 1 }
            .keys

        assertTrue("Дубликаты ресурсов: $duplicates", duplicates.isEmpty())
    }
}

class AlarmAssetPositiveCasesTest {

    @Test
    fun `the whole set ships and none of it is an empty file`() {
        val files = RAW.listFiles().orEmpty().filter { it.isFile }

        assertTrue("Звуков не найдено вовсе — проверка ничего не проверяет", files.size >= 8)
        val tiny = files.filter { it.length() < 4_096 }.map { it.name }
        assertTrue("Подозрительно маленький файл: $tiny", tiny.isEmpty())
    }

    @Test
    fun `every sound is ogg, so nothing is a multi-megabyte wav by accident`() {
        val wrong = RAW.listFiles().orEmpty().filterNot { it.extension == "ogg" }.map { it.name }

        assertTrue("Не ogg: $wrong", wrong.isEmpty())
    }

    @Test
    fun `the sirens are recordings and say so in the note`() {
        val recordings = listOf("alarm_missile", "alarm_air_raid", "alarm_siren", "alarm_klaxon")
        val notes = AlarmSounds.CATALOGUE
            .filter { choice ->
                recordings.any { name -> choice.uri.endsWith(name) }
            }
        assertTrue("Не все сирены найдены в каталоге", notes.size == recordings.size)
        assertTrue(
            "Запись должна быть названа записью: ${notes.map { it.note }}",
            notes.all { choice -> choice.note?.contains("запись") == true },
        )
    }
}
