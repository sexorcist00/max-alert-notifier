package ru.maxalert.notifier

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import ru.maxalert.notifier.ui.Strokes
import ru.maxalert.notifier.ui.TextAlpha

/** A tab is a place, and an icon says which place faster than the word does. */
internal data class TabSpec(val title: String, val icon: ImageVector)

internal val TABS = listOf(
    TabSpec("Главное", Icons.Filled.Shield),
    TabSpec("Сигнал", Icons.AutoMirrored.Filled.VolumeUp),
    TabSpec("Журнал", Icons.Filled.History),
)

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
                // Title only. The duty switch used to live here with its label, and between
                // the two the app's own name rendered as "Оповещение о т…". The switch moved
                // into the status banner, which is where it belongs anyway: right next to the
                // line that says what duty is currently doing.
                TopAppBar(
                    title = {
                        Text(
                            "Оповещение о тревоге",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
                StatusStrip(
                    settings = settings,
                    onDuty = { value ->
                        update { it.copy(enabled = value) }
                        if (value) MaxWatchService.start(context) else MaxWatchService.stop(context)
                    },
                    onClick = { tab = 0 },
                )
                ThreatScale()
                TabRow(selectedTabIndex = tab) {
                    TABS.forEachIndexed { index, spec ->
                        Tab(
                            selected = tab == index,
                            onClick = { tab = index },
                            text = { Text(spec.title, maxLines = 1) },
                            icon = {
                                Icon(
                                    imageVector = spec.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(Spacing.inlineIcon),
                                )
                            },
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
            ClearedCard()
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
internal fun StatusStrip(
    settings: AlertSettings,
    onDuty: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
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
            // A tint of the status colour rather than a neutral strip: this line is the first
            // thing looked at, and on a threat screen its colour is half the message.
            .background(color.copy(alpha = 0.12f))
            .selectable(selected = false, onClick = onClick)
            .padding(end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The accent bar carries the colour at full strength, where a 12% tint cannot.
        Box(
            Modifier
                .width(Spacing.xs)
                .height(Spacing.xxl)
                .background(color)
        )
        Spacer(Modifier.width(Spacing.md))
        Icon(
            imageVector = when (status.level) {
                SessionStatus.Level.OK -> Icons.Filled.Shield
                SessionStatus.Level.WARN -> Icons.Filled.Warning
                SessionStatus.Level.FAIL -> Icons.Filled.Error
            },
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(Spacing.inlineIcon),
        )
        Spacer(Modifier.width(Spacing.md))
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = Spacing.md)
        ) {
            Text(
                status.title,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                status.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = TextAlpha.BODY),
                maxLines = 2,
            )
            ConnectionProbe.ageText(now)?.let { age ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = TextAlpha.SECONDARY),
                        modifier = Modifier.size(Spacing.bulletDot * 1.5f),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        age,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = TextAlpha.SECONDARY),
                    )
                }
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
                    strokeWidth = Strokes.hairline,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Обновить состояние и проверить сессию",
                )
            }
        }
        Switch(
            checked = settings.enabled,
            onCheckedChange = onDuty,
            modifier = Modifier.semantics { contentDescription = "Дежурство" },
        )
        Spacer(Modifier.width(Spacing.sm))
    }
}

/**
 * Where the situation stands, on the scale it stands on.
 *
 * Four segments, the current one filled and named. A single coloured card answers "is there an
 * alert"; it does not answer "how bad, out of what" -- which is the question a threat display
 * exists for, and the reason every real one shows the whole ladder rather than today's rung.
 */
@Composable
internal fun ThreatScale() {
    val current = AlertState.state.level
    val palette = LocalAlertPalette.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        AlertLevel.entries.forEach { level ->
            val active = level == current
            val tone = if (level == AlertLevel.NONE) palette.statusOk else Color(level.colorArgb)
            Column(
                modifier = Modifier.weight(if (active) 1.6f else 1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(Spacing.xs)
                        .clip(CircleShape)
                        .background(if (active) tone else tone.copy(alpha = 0.22f))
                )
                if (active) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        level.shortTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = tone,
                        maxLines = 1,
                    )
                }
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
            // The level, with the sign of what it is: at a glance from across the room the
            // icon and the colour carry the message, and the words confirm it.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (state.level.rings) Icons.Filled.Campaign else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Color(state.level.onColorArgb),
                    modifier = Modifier.size(Spacing.xxl)
                )
                Spacer(Modifier.width(Spacing.md))
                Text(
                    state.level.title,
                    color = Color(state.level.onColorArgb),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Filled.Forum,
                    contentDescription = null,
                    tint = Color(state.level.onColorArgb),
                    modifier = Modifier.size(Spacing.inlineIcon),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    "${state.chat}: ${state.text}",
                    color = Color(state.level.onColorArgb),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (state.silenced) Icons.AutoMirrored.Filled.VolumeOff else Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = Color(state.level.onColorArgb),
                    modifier = Modifier.size(Spacing.inlineIcon),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    "Объявлен ${format.format(Date(state.since))}" +
                        if (state.silenced) " · звук выключен, состояние держится" else "",
                    color = Color(state.level.onColorArgb),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
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
            Hint("Установлена ${BuildConfig.VERSION_NAME}. Обновление ставится системным " +
                    "установщиком — потребуется разрешение на установку из этого приложения.")
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

/**
 * The end of an alert, stated once.
 *
 * The peak of using this app is the alarm itself; the end used to be a card that vanished and
 * a screen that looked as if nothing had happened. This is the closing line -- how long it
 * stood, between which times -- in the same place the alert card occupied, so the eye finds
 * the answer where it last saw the question. Green, because the situation is over; factual,
 * because a warning is not an achievement to celebrate.
 */
@Composable
internal fun ClearedCard() {
    val cleared = AlertState.lastCleared ?: return
    if (AlertState.state.active) return

    val palette = LocalAlertPalette.current
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Card(
        colors = CardDefaults.cardColors(containerColor = palette.statusOk),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = palette.onStatus,
                modifier = Modifier.size(Spacing.inlineIcon),
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                AlertSummary.describe(
                    level = cleared.level,
                    durationMs = cleared.until - cleared.since,
                    from = clock.format(Date(cleared.since)),
                    to = clock.format(Date(cleared.until)),
                    chat = cleared.chat,
                ),
                color = palette.onStatus,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.sm))
            TextButton(
                onClick = { AlertState.dismissCleared() },
                colors = ButtonDefaults.textButtonColors(contentColor = palette.onStatus),
            ) { Text("Скрыть", maxLines = 1) }
        }
    }
}
