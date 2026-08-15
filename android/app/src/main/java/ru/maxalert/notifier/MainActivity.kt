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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
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
        if (SettingsStore(this).load().enabled) MaxWatchService.start(this)
        setContent { MaxAlertTheme { HomeScreen() } }
    }

    override fun onPause() {
        super.onPause()
        AlarmPreview.stop(this)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
            .launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private val TABS = listOf("Тревога", "Наблюдение", "Журнал")

/**
 * Three screens instead of one long scroll: how it sounds, what it watches, what it saw.
 * The state of the alert and the traffic light stay pinned above the tabs, because those
 * two answers are the reason to open the app at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    var settings by remember { mutableStateOf(store.load()) }
    var tab by remember { mutableIntStateOf(0) }

    fun update(transform: (AlertSettings) -> AlertSettings) {
        settings = transform(settings)
        store.save(settings)
    }

    // Nothing on this screen is allowed to age silently: everything the connection touches
    // is re-read on a timer, and the duty notification can switch dury off behind our back.
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            AppRefresh.bump()
        }
    }
    LaunchedEffect(AppRefresh.tick) {
        settings = store.load()
    }
    // An update is offered on opening the app, never installed behind the user's back.
    LaunchedEffect(Unit) {
        UpdateChecker.check(context, quiet = true)
    }

    DisposableEffect(Unit) {
        onDispose { AlarmPreview.stop(context) }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("Оповещение о тревоге") })
                StatusStrip(settings) { tab = 1 }
                TabRow(selectedTabIndex = tab) {
                    TABS.forEachIndexed { index, title ->
                        Tab(
                            selected = tab == index,
                            onClick = { tab = index },
                            text = { Text(title) },
                        )
                    }
                }
            }
        },
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
            UpdateCard()

            when (tab) {
                0 -> AlarmTab(settings, ::update)
                1 -> WatchTab(settings, ::update)
                else -> LogTab()
            }
        }
    }
}

/* ---------------------------------------------------------------- status */

@Composable
private fun StatusStrip(settings: AlertSettings, onClick: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { MaxSession(context) }

    // Subscribing to the tick is what keeps every line below from going stale.
    val tick = AppRefresh.tick
    val now = remember(tick) { System.currentTimeMillis() }

    val status = SessionStatus.evaluate(
        watching = settings.enabled,
        notificationAccess = NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName),
        directEnabled = settings.useDirectConnection,
        loggedIn = session.loggedIn,
        online = MaxWatchService.online,
        lastOnlineAt = session.lastOnlineAt,
        now = now,
    )

    val color = when (status.level) {
        SessionStatus.Level.OK -> Color(0xFF2E7D32)
        SessionStatus.Level.WARN -> Color(0xFFF9A825)
        SessionStatus.Level.FAIL -> MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .selectable(selected = false, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(status.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(status.detail, style = MaterialTheme.typography.bodySmall)
            ConnectionProbe.ageText(now)?.let { age ->
                Text(age, style = MaterialTheme.typography.labelSmall)
            }
        }
        TextButton(
            enabled = !ConnectionProbe.busy,
            onClick = {
                AppRefresh.bump()
                if (session.loggedIn) scope.launch { ConnectionProbe.run(context) }
            },
        ) {
            Text(if (ConnectionProbe.busy) "…" else "↻")
        }
    }
}

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
            ) { Text("Снять вручную") }
        }
    }
}

@Composable
private fun UpdateCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val release = UpdateChecker.available ?: return
    var downloading by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Есть версия ${release.version}", fontWeight = FontWeight.Bold)
            Text(
                "Установлена ${BuildConfig.VERSION_NAME}. Обновление ставится системным " +
                    "установщиком — потребуется разрешение на установку из этого приложения.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !downloading,
                    onClick = {
                        downloading = true
                        scope.launch {
                            val apk = UpdateChecker.download(context, release)
                            downloading = false
                            if (apk != null) UpdateChecker.install(context, apk)
                        }
                    },
                ) { Text(if (downloading) "Скачиваю…" else "Обновить") }
                OutlinedButton(onClick = { UpdateChecker.dismiss() }) { Text("Позже") }
            }
            UpdateChecker.message?.let { text ->
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/* ------------------------------------------------------------ alarm tab */

@Composable
private fun AlarmTab(settings: AlertSettings, update: ((AlertSettings) -> AlertSettings) -> Unit) {
    val context = LocalContext.current

    val soundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            uri?.let { picked -> update { it.copy(soundUri = picked.toString()) } }
        }
    }

    SectionCard("Ритм") {
        Text(
            "Один ритм задаёт звук, вибрацию и фонарик. Шаблоны взяты из стандартов: кто слышал " +
                "пожарный извещатель, узнаёт сигнал без объяснений.",
            style = MaterialTheme.typography.bodySmall,
        )
        AlarmPattern.entries.forEach { pattern ->
            ChoiceRow(
                selected = settings.pattern == pattern,
                onSelect = { update { it.copy(patternId = pattern.id) } },
                title = pattern.label,
                subtitle = pattern.standard,
            )
        }
        OutlinedButton(
            onClick = { AlarmPreview.vibrate(context, settings) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Почувствовать ритм") }
    }

    SectionCard("Звук") {
        AlarmSounds.CATALOGUE.forEach { choice ->
            ChoiceRow(
                selected = settings.soundUri == choice.uri,
                onSelect = { update { it.copy(soundUri = choice.uri) } },
                title = choice.label,
                subtitle = choice.note,
                trailing = {
                    TextButton(onClick = { AlarmPreview.toggleSound(context, choice.uri) }) {
                        Text(if (AlarmPreview.playing == choice.uri) "стоп" else "▶")
                    }
                },
            )
        }
        val custom = settings.soundUri != null && !AlarmSounds.contains(settings.soundUri)
        ChoiceRow(
            selected = custom,
            onSelect = {},
            title = if (custom) "Свой файл (выбран)" else "Свой файл",
            trailing = {
                TextButton(onClick = { AlarmPreview.toggleSound(context, settings.soundUri) }) {
                    Text(if (AlarmPreview.playing == settings.soundUri && custom) "стоп" else "▶")
                }
            },
        )
        OutlinedButton(
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
        Text(
            "Звонок будильника из настроек телефона не используется: тревога должна звучать " +
                "одинаково всегда.",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    SectionCard("Вибрация и свет") {
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
            Text(
                "Не быстрее трёх вспышек в секунду — порог мерцания WCAG 2.3.1, выше опасно при " +
                    "фоточувствительной эпилепсии.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        SwitchRow("Выкручивать громкость", settings.forceMaxVolume) { value ->
            update { it.copy(forceMaxVolume = value) }
        }
        Text(
            "Звук идёт по тревожному аудиоканалу — его не глушит «Не беспокоить». Прежняя " +
                "громкость возвращается сразу после отбоя.",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    SectionCard("Продолжительность") {
        NumberField("Звонить не дольше, сек", settings.loopSeconds) { value ->
            update { it.copy(loopSeconds = value.coerceAtLeast(5)) }
        }
        NumberField("Пауза после срабатывания, сек", settings.cooldownSeconds) { value ->
            update { it.copy(cooldownSeconds = value.coerceAtLeast(0)) }
        }
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
        ) { Text("Полная проверка тревоги") }
    }
}

/* ------------------------------------------------------------ watch tab */

@Composable
private fun WatchTab(settings: AlertSettings, update: ((AlertSettings) -> AlertSettings) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var keywordsText by remember { mutableStateOf(settings.keywords.joinToString(", ")) }
    var yellowHighText by remember { mutableStateOf(settings.yellowHighKeywords.joinToString(", ")) }
    var yellowText by remember { mutableStateOf(settings.yellowKeywords.joinToString(", ")) }
    var deactivationText by remember { mutableStateOf(settings.deactivationKeywords.joinToString(", ")) }

    SectionCard("Дежурство") {
        SwitchRow("Слежу за чатом", settings.enabled) { value ->
            update { it.copy(enabled = value) }
            if (value) MaxWatchService.start(context) else MaxWatchService.stop(context)
        }
        Text(
            "Пока дежурство включено, в шторке висит закреплённое уведомление — оттуда же " +
                "выключается одним касанием.",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    AccessCard(context)
    DirectConnectionCard(settings, update)

    SectionCard("Что ловим") {
        OutlinedTextField(
            value = settings.chatFilter,
            onValueChange = { value -> update { it.copy(chatFilter = value) } },
            label = { Text("Название чата содержит") },
            supportingText = { Text("Пусто — любой чат. Точное название видно во вкладке «Журнал».") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = keywordsText,
            onValueChange = { value ->
                keywordsText = value
                update { it.copy(keywords = Matcher.parseKeywords(value)) }
            },
            label = { Text("Слова КОДА КРАСНОГО") },
            supportingText = { Text("Единственный уровень, который звенит. Пусто и жёлтые тоже пусты — красный на любое сообщение.") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = yellowHighText,
            onValueChange = { value ->
                yellowHighText = value
                update { it.copy(yellowHighKeywords = Matcher.parseKeywords(value)) }
            },
            label = { Text("Слова кода жёлтого повышенного") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = yellowText,
            onValueChange = { value ->
                yellowText = value
                update { it.copy(yellowKeywords = Matcher.parseKeywords(value)) }
            },
            label = { Text("Слова кода жёлтого") },
            supportingText = { Text("Жёлтые уровни не звучат: меняют состояние и строку в шторке.") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = deactivationText,
            onValueChange = { value ->
                deactivationText = value
                update { it.copy(deactivationKeywords = Matcher.parseKeywords(value)) }
            },
            label = { Text("Слова отбоя") },
            supportingText = { Text("Снимают «Код красный», в том числе задним числом — после возврата связи.") },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    SectionCard("Версия") {
        Text("Установлено: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
        Text(
            if (BuildConfig.VERSION_NAME == "0.0.0") {
                "Сборка не из релиза — обновление будет предлагаться всегда."
            } else {
                "Обновления берутся из Releases репозитория и предлагаются при запуске."
            },
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(
            onClick = { scope.launch { UpdateChecker.check(context, quiet = false) } },
            enabled = !UpdateChecker.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (UpdateChecker.busy) "Проверяю…" else "Проверить обновления") }
        UpdateChecker.message?.let { text ->
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }

    SectionCard("Чтобы не убивала система") {
        Text(
            "Huawei закрывает фоновые приложения. Разрешите работу в фоне и снимите ограничения " +
                "батареи, иначе тревога однажды не прозвенит.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(
            onClick = { context.openBatterySettings() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Настройки батареи") }
        OutlinedButton(
            onClick = { context.openDndAccess() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Доступ к «Не беспокоить»") }
    }
}

@Composable
private fun AccessCard(context: Context) {
    val granted = NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)

    SectionCard("Источник 1 · уведомления МАКСа") {
        Text(
            if (granted) "Доступ выдан — ловлю уведомления мгновенно"
            else "Нет доступа: без него уведомления МАКСа не видны",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (granted) FontWeight.Normal else FontWeight.Bold,
        )
        if (!granted) {
            Button(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Выдать доступ") }
        }
    }
}

@Composable
private fun DirectConnectionCard(
    settings: AlertSettings,
    update: ((AlertSettings) -> AlertSettings) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { MaxSession(context) }

    // Read live, never cached: the session can be dropped by the service or by MAX itself,
    // and a button that still says "вход выполнен" would be lying.
    val tick = AppRefresh.tick
    val loggedIn = remember(tick) { session.loggedIn }
    val codeRequested = remember(tick) { session.authToken != null }

    var phone by remember { mutableStateOf(session.phone ?: "+7") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    SectionCard("Источник 2 · своё подключение к MAX") {
        Text(
            "Видит сообщения, даже когда МАКС не показывает уведомление. Нужен вход по SMS.",
            style = MaterialTheme.typography.bodySmall,
        )
        SwitchRow("Держать подключение", settings.useDirectConnection) { value ->
            update { it.copy(useDirectConnection = value) }
            if (value) MaxWatchService.start(context) else MaxWatchService.stop(context)
        }
        Text("Состояние: ${MaxWatchService.status}", style = MaterialTheme.typography.bodySmall)

        if (loggedIn) {
            Text("Вход выполнен: ${session.phone ?: "аккаунт MAX"}")
            Button(
                enabled = !ConnectionProbe.busy,
                onClick = { scope.launch { ConnectionProbe.run(context) } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (ConnectionProbe.busy) "Проверяю…" else "Проверить сессию сейчас") }
            ConnectionProbe.message?.let { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ConnectionProbe.ok == false) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
            OutlinedButton(
                onClick = {
                    session.clear()
                    ConnectionProbe.reset()
                    MaxWatchService.stop(context)
                    AppRefresh.bump()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Выйти из аккаунта") }
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
                            .onFailure { error = it.message ?: "не удалось запросить код" }
                        // The stored token is the truth about "code requested", not a flag here.
                        AppRefresh.bump()
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (codeRequested) "Выслать код ещё раз" else "Выслать код по SMS") }

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
                                    if (settings.useDirectConnection) MaxWatchService.start(context)
                                }
                                .onFailure { error = it.message ?: "код не принят" }
                            AppRefresh.bump()
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Войти") }
            }
            Text(
                "Через VPN вход обычно не проходит — MAX не выдаёт код на зарубежный адрес. " +
                    "Войдите без VPN; уведомления МАКСа от этого не зависят.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        error?.let { text ->
            Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/* -------------------------------------------------------------- log tab */

@Composable
private fun LogTab() {
    val context = LocalContext.current
    val entries = EventLog.entries
    val format = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    SectionCard("Последние сообщения из МАКСа") {
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
            ) { Text("Очистить журнал") }
        }
    }
}

/* --------------------------------------------------------------- pieces */

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
private fun ChoiceRow(
    selected: Boolean,
    onSelect: () -> Unit,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.weight(1f)) {
            Text(title)
            subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
        trailing?.invoke()
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NumberField(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { entered ->
            text = entered
            entered.toIntOrNull()?.let(onChange)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun Context.openBatterySettings() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun Context.openDndAccess() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
