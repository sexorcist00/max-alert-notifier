package ru.maxalert.notifier

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import ru.maxalert.notifier.ui.Spacing

@Composable
internal fun AlarmTab(settings: AlertSettings, update: ((AlertSettings) -> AlertSettings) -> Unit) {
    val context = LocalContext.current

    val soundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            uri?.let { picked -> update { it.copy(soundUri = picked.toString()) } }
        }
    }

    var patternsOpen by remember { mutableStateOf(false) }
    var soundsOpen by remember { mutableStateOf(false) }

    // The reason to open this tab is to find out how it will sound, so the button that shows
    // it comes first. It used to sit at the bottom of "Продолжительность", where nothing about
    // the heading suggested a full rehearsal was hiding under it.
    SectionCard("Проверка", icon = Icons.Filled.PlayCircle) {
        Button(
            onClick = {
                AlarmPreview.stop(context)
                AlarmController.trigger(
                    context,
                    settings.copy(loopSeconds = minOf(settings.loopSeconds, 30)),
                    "Проверка",
                    "Так будет выглядеть и звучать тревога",
                )
                context.startActivity(
                    Intent(context, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Проверить тревогу целиком") }
        Hint("Зазвенит по-настоящему — со звуком, вибрацией и экраном, но не дольше 30 секунд.")
    }

    // Long radio lists pushed everything else off the screen; the choice already made is
    // shown as one line, and the list opens only when there is a choice to change.
    SectionCard("Ритм", icon = Icons.Filled.GraphicEq) {
        CurrentChoiceRow(
            value = settings.pattern.label,
            note = settings.pattern.standard,
            open = patternsOpen,
            onToggle = { patternsOpen = !patternsOpen },
        )
        AnimatedVisibility(visible = patternsOpen) {
          Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Hint("Один ритм задаёт звук, вибрацию и фонарик. Шаблоны взяты из стандартов: кто " +
                    "слышал пожарный извещатель, узнаёт сигнал без объяснений.")
            AlarmPattern.entries.forEach { pattern ->
                ChoiceRow(
                    selected = settings.pattern == pattern,
                    onSelect = {
                        update { it.copy(patternId = pattern.id) }
                        AlarmPreview.vibrate(context, settings.copy(patternId = pattern.id))
                    },
                    title = pattern.label,
                    subtitle = pattern.standard,
                )
            }
          }
        }
        if (patternsOpen) {
            OutlinedButton(
                onClick = { AlarmPreview.vibrate(context, settings) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Почувствовать ритм") }
        }
    }

    SectionCard("Звук", icon = Icons.AutoMirrored.Filled.VolumeUp) {
        CurrentChoiceRow(
            value = AlarmSounds.CATALOGUE.firstOrNull { it.uri == settings.soundUri }?.label
                ?: "Свой файл",
            note = AlarmSounds.CATALOGUE.firstOrNull { it.uri == settings.soundUri }?.note,
            open = soundsOpen,
            onToggle = { soundsOpen = !soundsOpen },
            trailing = { PreviewButton(context, settings.soundUri) },
        )
        AnimatedVisibility(visible = soundsOpen) {
          Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            AlarmSounds.CATALOGUE.forEach { choice ->
            ChoiceRow(
                selected = settings.soundUri == choice.uri,
                onSelect = { update { it.copy(soundUri = choice.uri) } },
                title = choice.label,
                subtitle = choice.note,
                trailing = { PreviewButton(context, choice.uri) },
            )
            }
          }
        }
        if (soundsOpen) OutlinedButton(
            onClick = {
                soundPicker.launch(
                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Звук тревоги")
                    }
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Выбрать свой файл") }
        if (soundsOpen) {
            Hint("Звонок будильника из настроек телефона не используется: тревога должна " +
                    "звучать одинаково всегда.")
        }
    }

    SectionCard("Вибрация и свет", icon = Icons.Filled.Vibration) {
        SwitchRow("Вибрация", settings.vibrate) { value -> update { it.copy(vibrate = value) } }
        if (settings.vibrate) {
            Text("Сила: ${settings.vibrationStrength}%", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = settings.vibrationStrength.toFloat(),
                onValueChange = { value ->
                    update { it.copy(vibrationStrength = value.toInt().coerceIn(10, 100)) }
                },
                onValueChangeFinished = { AlarmPreview.vibrate(context, settings) },
                valueRange = 10f..100f,
                steps = 8,
            )
        }
        SwitchRow("Мигать фонариком", settings.flashlight) { value ->
            update { it.copy(flashlight = value) }
            if (value) AlarmPreview.flash(context, settings)
        }
        if (settings.flashlight) {
            Hint("Не быстрее трёх вспышек в секунду — порог мерцания WCAG 2.3.1, выше опасно при " +
                    "фоточувствительной эпилепсии.")
        }
        SwitchRow("Выкручивать громкость", settings.forceMaxVolume) { value ->
            update { it.copy(forceMaxVolume = value) }
        }
        Hint("Звук идёт по тревожному аудиоканалу — его не глушит «Не беспокоить». Прежняя " +
                "громкость возвращается сразу после отбоя.")
    }

    SectionCard("Продолжительность", icon = Icons.Filled.Timer) {
        DurationChoice(
            label = "Звонить не дольше",
            options = Durations.options(Durations.ALARM_LIMIT, settings.loopSeconds),
            current = settings.loopSeconds,
        ) { value -> update { it.copy(loopSeconds = value) } }
        DurationChoice(
            label = "Пауза после срабатывания",
            options = Durations.options(Durations.COOLDOWN, settings.cooldownSeconds),
            current = settings.cooldownSeconds,
        ) { value -> update { it.copy(cooldownSeconds = value) } }
        Hint("Пауза не даёт одному и тому же сообщению поднять тревогу дважды подряд.")
    }
}
