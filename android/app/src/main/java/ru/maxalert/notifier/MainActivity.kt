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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import ru.maxalert.notifier.ui.LocalAlertPalette
import ru.maxalert.notifier.ui.Spacing
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

private val TABS = listOf("Главное", "Сигнал", "Журнал")

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
    // A scroll position per tab: a shared one drops you into the middle of a short tab
    // after scrolling a long one.
    val scrollStates = List(TABS.size) { rememberScrollState() }

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
                TopAppBar(
                    title = {
                        Text(
                            "Оповещение о тревоге",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        // The master switch belongs where it is always reachable: whether the
                        // phone is on watch is the one thing worth knowing from any tab.
                        Text("Дежурство", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(Spacing.sm))
                        Switch(
                            checked = settings.enabled,
                            onCheckedChange = { value ->
                                update { it.copy(enabled = value) }
                                if (value) MaxWatchService.start(context) else MaxWatchService.stop(context)
                            },
                        )
                        Spacer(Modifier.width(Spacing.sm))
                    },
                )
                StatusStrip(settings) { tab = 0 }
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
                .verticalScroll(scrollStates[tab])
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            AlertStateCard()
            UpdateCard()

            when (tab) {
                0 -> WatchTab(settings, ::update)
                1 -> AlarmTab(settings, ::update)
                else -> LogTab(settings, ::update)
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

    val palette = LocalAlertPalette.current
    val color = when (status.level) {
        SessionStatus.Level.OK -> palette.statusOk
        SessionStatus.Level.WARN -> palette.statusWarn
        SessionStatus.Level.FAIL -> palette.statusFail
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .selectable(selected = false, onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(Spacing.statusDot)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                status.title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                status.detail,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
            )
            ConnectionProbe.ageText(now)?.let { age ->
                Text(age, style = MaterialTheme.typography.labelSmall)
            }
        }
        IconButton(
            enabled = !ConnectionProbe.busy,
            onClick = {
                AppRefresh.bump()
                if (session.loggedIn) scope.launch { ConnectionProbe.run(context) }
            },
        ) {
            // While the probe is out, the button has to look busy: a refresh icon that does
            // nothing visible for two seconds reads as a button that did not work.
            if (ConnectionProbe.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Spacing.inlineIcon),
                    strokeWidth = Spacing.hairline,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Обновить состояние и проверить сессию",
                )
            }
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
        colors = CardDefaults.cardColors(containerColor = Color(state.level.colorArgb)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                state.level.title,
                color = Color(state.level.onColorArgb),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text("${state.chat}: ${state.text}", color = Color(state.level.onColorArgb))
            Text(
                "Объявлен ${format.format(Date(state.since))}" +
                    if (state.silenced) " · звук выключен, состояние держится" else "",
                color = Color(state.level.onColorArgb),
                style = MaterialTheme.typography.bodySmall,
            )
            val onCard = ButtonDefaults.buttonColors(
                containerColor = Color(state.level.onColorArgb),
                contentColor = Color(state.level.colorArgb),
            )
            // While it is still sounding, silencing and lifting are two different decisions,
            // and one button cannot mean both: whoever reaches for quiet at three in the
            // morning must not lose the standing alert by pressing it.
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (state.level.rings && !state.silenced) {
                    Button(
                        onClick = { AlarmController.stop(context) },
                        colors = onCard,
                        modifier = Modifier.weight(1f),
                    ) { Text("Заглушить", maxLines = 1) }
                }
                Button(
                    onClick = {
                        AlarmController.stop(context)
                        AlertState.clear(context)
                    },
                    colors = onCard,
                    modifier = Modifier.weight(1f),
                ) { Text("Снять", maxLines = 1) }
            }
            if (state.level.rings && !state.silenced) {
                Text(
                    "«Заглушить» гасит звук, состояние остаётся. «Снять» отменяет тревогу целиком.",
                    color = Color(state.level.onColorArgb),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
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
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Есть версия ${release.version}", fontWeight = FontWeight.Bold)
            Text(
                "Установлена ${BuildConfig.VERSION_NAME}. Обновление ставится системным " +
                    "установщиком — потребуется разрешение на установку из этого приложения.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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

    var patternsOpen by remember { mutableStateOf(false) }
    var soundsOpen by remember { mutableStateOf(false) }

    // The reason to open this tab is to find out how it will sound, so the button that shows
    // it comes first. It used to sit at the bottom of "Продолжительность", where nothing about
    // the heading suggested a full rehearsal was hiding under it.
    SectionCard("Проверка") {
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
        Text(
            "Зазвенит по-настоящему — со звуком, вибрацией и экраном, но не дольше 30 секунд.",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    // Long radio lists pushed everything else off the screen; the choice already made is
    // shown as one line, and the list opens only when there is a choice to change.
    SectionCard("Ритм") {
        CurrentChoiceRow(
            value = settings.pattern.label,
            note = settings.pattern.standard,
            open = patternsOpen,
            onToggle = { patternsOpen = !patternsOpen },
        )
        AnimatedVisibility(visible = patternsOpen) {
          Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                "Один ритм задаёт звук, вибрацию и фонарик. Шаблоны взяты из стандартов: кто " +
                    "слышал пожарный извещатель, узнаёт сигнал без объяснений.",
                style = MaterialTheme.typography.bodySmall,
            )
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

    SectionCard("Звук") {
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
            Text(
                "Звонок будильника из настроек телефона не используется: тревога должна " +
                    "звучать одинаково всегда.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
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
        Text(
            "Пауза не даёт одному и тому же сообщению поднять тревогу дважды подряд.",
            style = MaterialTheme.typography.bodySmall,
        )
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

    // Pressing a step must land on the field it is about. Focus does the scrolling for us,
    // opens the keyboard, and leaves the cursor where the answer goes.
    val chatFocus = remember { FocusRequester() }
    val keywordsFocus = remember { FocusRequester() }
    val allClearFocus = remember { FocusRequester() }

    val tick = AppRefresh.tick
    val notificationAccess = remember(tick) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    val batteryFree = remember(tick) {
        val power = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        power?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
    val steps = SetupChecklist.steps(settings, notificationAccess, batteryFree)

    SetupCard(steps) { action ->
        when (action) {
            SetupAction.ENABLE_DUTY -> {
                update { it.copy(enabled = true) }
                MaxWatchService.start(context)
            }
            SetupAction.NOTIFICATION_ACCESS -> context.openNotificationAccess()
            SetupAction.BATTERY -> context.requestBatteryFreedom()
            SetupAction.CHAT -> chatFocus.requestFocus()
            SetupAction.KEYWORDS -> keywordsFocus.requestFocus()
            SetupAction.ALL_CLEAR -> allClearFocus.requestFocus()
        }
    }

    // Open when there is nothing left to fix -- then it is the answer to "what will it do".
    // While steps remain, it is reference material and should not stand in front of them.
    CollapsibleCard("Что сейчас настроено", startOpen = steps.isEmpty()) {
        Text(
            ConfigSummary.describe(
                settings,
                AlarmSounds.CATALOGUE.firstOrNull { it.uri == settings.soundUri }?.label
                    ?: "свой файл",
            ),
            style = MaterialTheme.typography.bodyMedium,
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
            modifier = Modifier.fillMaxWidth().focusRequester(chatFocus),
        )
        OutlinedTextField(
            value = keywordsText,
            onValueChange = { value ->
                keywordsText = value
                update { it.copy(keywords = Matcher.parseKeywords(value)) }
            },
            label = { Text("Слова КОДА КРАСНОГО") },
            supportingText = { Text("Единственный уровень, который звенит. Пусто и жёлтые тоже пусты — красный на любое сообщение.") },
            modifier = Modifier.fillMaxWidth().focusRequester(keywordsFocus),
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
        HorizontalDivider()
        var probe by remember { mutableStateOf("") }
        OutlinedTextField(
            value = probe,
            onValueChange = { probe = it },
            label = { Text("Проверить фразу") },
            supportingText = {
                Text(
                    if (probe.isBlank()) "Вставьте сюда текст сообщения — покажу, сработает ли и почему."
                    else Matcher.explain(
                        Matcher.evaluate(
                            IncomingNotification(
                                packageName = settings.sourcePackage,
                                chat = settings.chatFilter.ifBlank { "любой чат" },
                                text = probe,
                            ),
                            settings,
                        )
                    )
                )
            },
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
            modifier = Modifier.fillMaxWidth().focusRequester(allClearFocus),
        )
    }

    CollapsibleCard("Версия и обновления", BuildConfig.VERSION_NAME) {
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

    CollapsibleCard("Чтобы не убивала система", "Huawei закрывает фон") {
        Text(
            "Huawei закрывает фоновые приложения. Разрешите работу в фоне и снимите ограничения " +
                "батареи, иначе тревога однажды не прозвенит.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(
            onClick = { context.requestBatteryFreedom() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Снять ограничения батареи") }
        OutlinedButton(
            onClick = { context.openBatterySettings() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Запуск приложений (Huawei)") }
        OutlinedButton(
            onClick = { context.openDndAccess() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Доступ к «Не беспокоить»") }
    }
}

/**
 * What is still missing before this phone can be trusted to wake someone.
 *
 * Every line does the thing it names. A checklist that only states the problem sends the user
 * hunting for the screen that fixes it -- and the two that matter most on this phone (the
 * notification access and the battery page) are buried deep in EMUI's own settings.
 */
@Composable
private fun SetupCard(steps: List<SetupStep>, onAction: (SetupAction) -> Unit) {
    val palette = LocalAlertPalette.current

    SectionCard(if (steps.isEmpty()) "Настроено" else "Осталось настроить") {
        if (steps.isEmpty()) {
            Text(
                "Всё на месте: чат выбран, слова заданы, доступы выданы. Проверьте тревогу на " +
                    "вкладке «Сигнал» и можно дежурить.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            steps.forEach { step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(Spacing.bulletDot)
                            .clip(CircleShape)
                            // Red only where nothing will ring at all; the rest merely make
                            // the watch coarse, and painting them the same hides which is which.
                            .background(if (step.blocking) palette.statusFail else palette.statusWarn)
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        step.title,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    TextButton(onClick = { onAction(step.action) }) {
                        Text(step.button, maxLines = 1)
                    }
                }
            }
        }
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
                onClick = { context.openNotificationAccess() },
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
    var confirmLogout by remember { mutableStateOf(false) }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Выйти из аккаунта MAX?") },
            text = {
                Text(
                    "Второй источник перестанет работать: тревога останется только на " +
                        "уведомлениях МАКСа. Обратный вход — снова по SMS и только без VPN."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        session.clear()
                        ConnectionProbe.reset()
                        MaxWatchService.stop(context)
                        AppRefresh.bump()
                        confirmLogout = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Выйти") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Отмена") }
            },
        )
    }

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
            HorizontalDivider()
            TextButton(
                onClick = { confirmLogout = true },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
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
private fun LogTab(settings: AlertSettings, update: ((AlertSettings) -> AlertSettings) -> Unit) {
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
                val watched = entry.chat.isNotBlank() &&
                    settings.chatFilter.isNotBlank() &&
                    entry.chat.contains(settings.chatFilter, ignoreCase = true)
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (entry.fired) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = "Сработала тревога",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(Spacing.inlineIcon),
                            )
                            Spacer(Modifier.width(Spacing.xs))
                        }
                        Text(
                            "${format.format(Date(entry.time))}  ${entry.chat}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(entry.text, style = MaterialTheme.typography.bodySmall)
                    Text(entry.reason, style = MaterialTheme.typography.labelSmall)
                    if (entry.chat.isNotBlank() && !watched) {
                        TextButton(onClick = { update { it.copy(chatFilter = entry.chat) } }) {
                            Text("Следить за этим чатом")
                        }
                    }
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
private fun CollapsibleCard(
    title: String,
    subtitle: String? = null,
    startOpen: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Keyed on startOpen: when the reason to keep it shut goes away, the card opens itself.
    var open by remember(startOpen) { mutableStateOf(startOpen) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = open, onClick = { open = !open }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                }
                Text(if (open) "Свернуть" else "Открыть", style = MaterialTheme.typography.labelLarge)
            }
            AnimatedVisibility(visible = open) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) { content() }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** The setting as it stands now, with one tap to open the list of alternatives. */
@Composable
private fun CurrentChoiceRow(
    value: String,
    note: String?,
    open: Boolean,
    onToggle: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(value, fontWeight = FontWeight.Bold)
            if (open) note?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
        trailing?.invoke()
        // Two targets side by side need a gap, or the play button and "Изменить" get
        // hit interchangeably.
        Spacer(Modifier.width(Spacing.sm))
        TextButton(onClick = onToggle) { Text(if (open) "Свернуть" else "Изменить") }
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

/** A duration as chips: no keyboard, no unit to guess, no way to type 3 instead of 300. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationChoice(
    label: String,
    options: List<Int>,
    current: Int,
    onPick: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            options.forEach { value ->
                FilterChip(
                    selected = value == current,
                    onClick = { onPick(value) },
                    label = { Text(Durations.label(value), maxLines = 1) },
                )
            }
        }
    }
}

/**
 * Opens EMUI's own "Запуск приложений" screen when it exists.
 *
 * On Huawei that screen -- not the standard battery page -- is the one that decides whether
 * the watcher survives the night, and it cannot be reached from the generic settings intent.
 * Falls back to the app's details page everywhere else.
 */
private fun Context.openBatterySettings() {
    val huawei = Intent().setClassName(
        "com.huawei.systemmanager",
        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    if (runCatching { startActivity(huawei) }.isFailure) {
        runCatching { startActivity(fallback) }
    }
}

/** Asks Android to stop restricting the app in the background; a no-op if already allowed. */
private fun Context.requestBatteryFreedom() {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { startActivity(intent) }.isFailure) openBatterySettings()
}

private fun Context.openNotificationAccess() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
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

/** Play or stop a preview. An icon button keeps the 48dp target Android asks for. */
@Composable
private fun PreviewButton(context: Context, soundUri: String?) {
    val playing = AlarmPreview.playing == soundUri
    IconButton(onClick = { AlarmPreview.toggleSound(context, soundUri) }) {
        Icon(
            imageVector = if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = if (playing) "Остановить прослушивание" else "Прослушать сигнал",
        )
    }
}
