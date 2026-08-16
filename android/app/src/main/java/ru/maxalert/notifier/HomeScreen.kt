package ru.maxalert.notifier

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.maxalert.notifier.max.MaxSession
import ru.maxalert.notifier.ui.LocalAlertPalette
import ru.maxalert.notifier.ui.Spacing

internal val TABS = listOf("Главное", "Сигнал", "Журнал")

/**
 * Three screens instead of one long scroll: how it sounds, what it watches, what it saw.
 * The state of the alert and the traffic light stay pinned above the tabs, because those
 * two answers are the reason to open the app at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen() {
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

@Composable
internal fun StatusStrip(settings: AlertSettings, onClick: () -> Unit) {
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
        awaitingPassword = session.passwordTrackId != null,
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
internal fun AlertStateCard() {
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
internal fun UpdateCard() {
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
