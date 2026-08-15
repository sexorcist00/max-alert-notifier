package ru.maxalert.notifier

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import ru.maxalert.notifier.max.MaxLogin
import ru.maxalert.notifier.max.MaxSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventLog.load(this)
        AlertState.load(this)
        requestNotificationPermission()
        // Duty mode belongs in the shade whenever the watcher is on.
        if (SettingsStore(this).load().enabled) MaxWatchService.start(this)
        setContent {
            MaxAlertTheme {
                SettingsScreen()
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
            .launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    var settings by remember { mutableStateOf(store.load()) }
    var keywordsText by remember { mutableStateOf(settings.keywords.joinToString(", ")) }
    var deactivationText by remember { mutableStateOf(settings.deactivationKeywords.joinToString(", ")) }
    var loopText by remember { mutableStateOf(settings.loopSeconds.toString()) }
    var cooldownText by remember { mutableStateOf(settings.cooldownSeconds.toString()) }

    fun update(transform: (AlertSettings) -> AlertSettings) {
        settings = transform(settings)
        store.save(settings)
    }

    val soundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            update { it.copy(soundUri = uri?.toString()) }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Код красный") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AlertStateCard()

            AccessCard(context)

            SectionCard("Дежурный режим") {
                SwitchRow("Слежу за чатом", settings.enabled) { value ->
                    update { it.copy(enabled = value) }
                    if (value) MaxWatchService.start(context) else MaxWatchService.stop(context)
                }
                Text(
                    "Пока дежурство включено, в шторке висит закреплённое уведомление — " +
                        "оттуда же можно выключить одним касанием.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Приложение-источник: МАКС (${settings.sourcePackage})",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            DirectConnectionCard(
                enabled = settings.useDirectConnection,
                onEnabledChange = { value ->
                    update { it.copy(useDirectConnection = value) }
                    if (value) MaxWatchService.start(context) else MaxWatchService.stop(context)
                },
            )

            SectionCard("Что ловим") {
                OutlinedTextField(
                    value = settings.chatFilter,
                    onValueChange = { value -> update { it.copy(chatFilter = value) } },
                    label = { Text("Название чата содержит") },
                    supportingText = { Text("Пусто — любой чат. Точное название видно в журнале ниже.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = keywordsText,
                    onValueChange = { value ->
                        keywordsText = value
                        update { it.copy(keywords = Matcher.parseKeywords(value)) }
                    },
                    label = { Text("Ключевые слова через запятую") },
                    supportingText = { Text("Пусто — тревога на любое сообщение в этом чате. Регистр не важен.") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = deactivationText,
                    onValueChange = { value ->
                        deactivationText = value
                        update { it.copy(deactivationKeywords = Matcher.parseKeywords(value)) }
                    },
                    label = { Text("Слова отбоя через запятую") },
                    supportingText = { Text("Такое сообщение в том же чате снимает «Код красный» — в том числе задним числом, когда телефон был без связи.") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard("Тревога") {
                Text("Звук тревоги", style = MaterialTheme.typography.bodyMedium)
                SOUND_CHOICES.forEach { (value, label) ->
                    SoundRow(
                        label = label,
                        selected = settings.soundUri == value,
                        onSelect = { update { it.copy(soundUri = value) } },
                    )
                }
                SoundRow(
                    label = "Свой файл",
                    selected = settings.soundUri != null && SOUND_CHOICES.none { it.first == settings.soundUri },
                    onSelect = {},
                )
                OutlinedButton(
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Звук тревоги")
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                settings.soundUri?.let(Uri::parse),
                            )
                        }
                        soundPicker.launch(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Выбрать свой файл")
                }
                SwitchRow("Вибрация", settings.vibrate) { value ->
                    update { it.copy(vibrate = value) }
                }
                SwitchRow("Выкручивать громкость будильника", settings.forceMaxVolume) { value ->
                    update { it.copy(forceMaxVolume = value) }
                }
                NumberField("Звонить не дольше, сек", loopText) { value ->
                    loopText = value
                    value.toIntOrNull()?.takeIf { it > 0 }?.let { seconds ->
                        update { it.copy(loopSeconds = seconds) }
                    }
                }
                NumberField("Пауза после срабатывания, сек", cooldownText) { value ->
                    cooldownText = value
                    value.toIntOrNull()?.takeIf { it >= 0 }?.let { seconds ->
                        update { it.copy(cooldownSeconds = seconds) }
                    }
                }
                Button(
                    onClick = {
                        AlarmController.trigger(
                            context,
                            settings.copy(loopSeconds = minOf(settings.loopSeconds, 30)),
                            "Проверка",
                            "Так будет звучать тревога",
                        )
                        context.startActivity(
                            Intent(context, AlarmActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Проверить тревогу")
                }
            }

            SectionCard("Чтобы не убивала система") {
                Text(
                    "Huawei закрывает фоновые приложения. Разрешите работу в фоне и снимите " +
                        "ограничения батареи, иначе тревога однажды не прозвенит.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = { context.openBatterySettings() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Настройки батареи")
                }
                OutlinedButton(
                    onClick = { context.openDndAccess() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Доступ к «Не беспокоить»")
                }
            }

            LogCard(context)
        }
    }
}

@Composable
private fun DirectConnectionCard(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { MaxSession(context) }

    var loggedIn by remember { mutableStateOf(session.loggedIn) }
    var phone by remember { mutableStateOf(session.phone ?: "+7") }
    var code by remember { mutableStateOf("") }
    var codeRequested by remember { mutableStateOf(session.authToken != null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    SectionCard("Своё подключение к MAX") {
        Text(
            "Приложение само держит связь с MAX и видит сообщения, даже когда МАКС не " +
                "показывает уведомление. Нужен вход в ваш аккаунт по SMS.",
            style = MaterialTheme.typography.bodySmall,
        )
        SwitchRow("Держать своё подключение", enabled, onEnabledChange)
        Text("Состояние: ${MaxWatchService.status}", style = MaterialTheme.typography.bodyMedium)

        if (loggedIn) {
            Text("Вход выполнен: ${session.phone ?: "аккаунт MAX"}", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = {
                    session.clear()
                    loggedIn = false
                    codeRequested = false
                    MaxWatchService.stop(context)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Выйти из аккаунта")
            }
        } else {
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Телефон аккаунта MAX") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        runCatching { MaxLogin.requestCode(context, phone.trim()) }
                            .onSuccess { codeRequested = true }
                            .onFailure { error = it.message ?: "не удалось запросить код" }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (codeRequested) "Выслать код ещё раз" else "Выслать код по SMS")
            }

            if (codeRequested) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Код из SMS") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    enabled = !busy && code.isNotBlank(),
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            runCatching { MaxLogin.submitCode(context, code.trim()) }
                                .onSuccess {
                                    loggedIn = true
                                    error = null
                                    if (enabled) MaxWatchService.start(context)
                                }
                                .onFailure { error = it.message ?: "код не принят" }
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Войти")
                }
            }
        }

        error?.let { text ->
            Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AccessCard(context: Context) {
    val granted = NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (granted) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (granted) "Доступ к уведомлениям выдан" else "Нет доступа к уведомлениям",
                fontWeight = FontWeight.Bold,
            )
            if (!granted) {
                Text(
                    "Без него приложение не видит сообщения МАКСа. Найдите в списке «Тревога из MAX» " +
                        "и включите переключатель.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Выдать доступ")
                }
            }
        }
    }
}

@Composable
private fun LogCard(context: Context) {
    val entries = EventLog.entries
    val format = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    SectionCard("Журнал (последние уведомления МАКСа)") {
        if (entries.isEmpty()) {
            Text(
                "Пока пусто. Напишите что-нибудь в нужный чат — здесь появится точное название " +
                    "чата, которое надо вписать в фильтр.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            entries.forEach { entry ->
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "${format.format(Date(entry.time))}  ${if (entry.fired) "🔔" else "—"}  ${entry.chat}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(entry.text, style = MaterialTheme.typography.bodySmall)
                    Text(entry.reason, style = MaterialTheme.typography.labelSmall)
                }
                HorizontalDivider()
            }
            OutlinedButton(
                onClick = { EventLog.clear(context) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Очистить журнал")
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun Context.openBatterySettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

private fun Context.openDndAccess() {
    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

/** Bundled tones plus the device's own alarm sound; anything else is a picked file. */
private val SOUND_CHOICES = listOf(
    null to "Системный будильник",
    "${AlarmController.BUNDLED_PREFIX}alarm_two_tone" to "Двухтональный сигнал",
    "${AlarmController.BUNDLED_PREFIX}alarm_siren" to "Сирена",
    "${AlarmController.BUNDLED_PREFIX}alarm_pulse" to "Резкие писки",
    "${AlarmController.BUNDLED_PREFIX}alarm_klaxon" to "Низкий клаксон",
)

@Composable
private fun AlertStateCard() {
    val context = LocalContext.current
    val state = AlertState.state
    if (!state.active) return

    val format = remember { SimpleDateFormat("d MMMM, HH:mm", Locale.getDefault()) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "КОД КРАСНЫЙ",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text("${state.chat}: ${state.text}", color = Color.White)
            Text(
                "Объявлен ${format.format(Date(state.since))}" +
                    if (state.silenced) " · звук выключен, состояние держится" else "",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = {
                    AlarmController.stop(context)
                    AlertState.clear(context)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Снять вручную")
            }
        }
    }
}

@Composable
private fun SoundRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label)
    }
}
