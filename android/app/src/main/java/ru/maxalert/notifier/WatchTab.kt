package ru.maxalert.notifier

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.launch
import ru.maxalert.notifier.max.AuthOutcome
import ru.maxalert.notifier.max.MaxLogin
import ru.maxalert.notifier.max.explain
import ru.maxalert.notifier.max.MaxSession
import ru.maxalert.notifier.ui.LocalAlertPalette
import ru.maxalert.notifier.ui.Spacing

@Composable
internal fun WatchTab(settings: AlertSettings, update: ((AlertSettings) -> AlertSettings) -> Unit) {
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
        HorizontalDivider()
        // The four word lists are one group and read as a ladder, each labelled with the
        // colour it declares -- four identical grey fields gave no clue which was which.
        KeywordField(
            level = AlertLevel.RED,
            label = "Слова КОДА КРАСНОГО",
            value = keywordsText,
            support = "Единственный уровень, который звенит. Пусто и жёлтые тоже пусты — " +
                "красный на любое сообщение.",
            focusRequester = keywordsFocus,
        ) { value ->
            keywordsText = value
            update { it.copy(keywords = Matcher.parseKeywords(value)) }
        }
        KeywordField(
            level = AlertLevel.YELLOW_HIGH,
            label = "Слова кода жёлтого повышенного",
            value = yellowHighText,
        ) { value ->
            yellowHighText = value
            update { it.copy(yellowHighKeywords = Matcher.parseKeywords(value)) }
        }
        KeywordField(
            level = AlertLevel.YELLOW,
            label = "Слова кода жёлтого",
            value = yellowText,
            support = "Жёлтые уровни не звучат: меняют состояние и строку в шторке.",
        ) { value ->
            yellowText = value
            update { it.copy(yellowKeywords = Matcher.parseKeywords(value)) }
        }
        KeywordField(
            level = AlertLevel.NONE,
            label = "Слова отбоя",
            value = deactivationText,
            support = "Снимают «Код красный», в том числе задним числом — после возврата связи.",
            focusRequester = allClearFocus,
        ) { value ->
            deactivationText = value
            update { it.copy(deactivationKeywords = Matcher.parseKeywords(value)) }
        }

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
    }

    CollapsibleCard("Версия и обновления", BuildConfig.VERSION_NAME) {
        Text("Установлено: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
        Hint(if (BuildConfig.VERSION_NAME == "0.0.0") {
                "Сборка не из релиза — обновление будет предлагаться всегда."
            } else {
                "Обновления берутся из Releases репозитория и предлагаются при запуске."
            })
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
        Hint("Huawei закрывает фоновые приложения. Разрешите работу в фоне и снимите ограничения " +
                "батареи, иначе тревога однажды не прозвенит.")
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
internal fun SetupCard(steps: List<SetupStep>, onAction: (SetupAction) -> Unit) {
    val palette = LocalAlertPalette.current

    SectionCard(if (steps.isEmpty()) "Настроено" else "Осталось настроить") {
        if (steps.isEmpty()) {
            Hint("Всё на месте: чат выбран, слова заданы, доступы выданы. Проверьте тревогу на " +
                    "вкладке «Сигнал» и можно дежурить.")
        } else {
            steps.forEach { step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // An icon says what the step is about before the sentence is read, and the
                    // tint says how bad it is: red where nothing will ring at all, amber where
                    // it only makes the watch coarse. A row of identical dots said neither.
                    Icon(
                        imageVector = step.action.icon(),
                        contentDescription = null,
                        tint = if (step.blocking) palette.statusFail else palette.statusWarn,
                        modifier = Modifier.size(Spacing.inlineIcon),
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
internal fun AccessCard(context: Context) {
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
internal fun DirectConnectionCard(
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

    val vpnActive = remember(tick) { NetworkRoute.vpnActive(context) }

    val passwordNeeded = remember(tick) { session.passwordTrackId != null }
    val passwordHint = remember(tick) { session.passwordHint }

    var phone by remember { mutableStateOf(session.phone ?: "+7") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
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
                        MaxLogin.cancel()
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
        Hint("Видит сообщения, даже когда МАКС не показывает уведомление. Нужен вход по SMS.")
        VpnRow(settings, vpnActive, update)
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
                                .onSuccess { outcome ->
                                    error = outcome.explain()
                                    if (outcome is AuthOutcome.LoggedIn &&
                                        settings.useDirectConnection
                                    ) {
                                        MaxWatchService.start(context)
                                    }
                                }
                                .onFailure { error = it.message ?: "код не принят" }
                            AppRefresh.bump()
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Войти") }
            }

            // MAX now asks some accounts for a login password after the code. The step only
            // appears once the server has actually asked for it: the track id it sends is what
            // the answer is tied to, and it is stored, so closing the app mid-login is safe.
            if (passwordNeeded) {
                HorizontalDivider()
                Text(
                    "MAX просит пароль от аккаунта — второй шаг после кода.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                passwordHint?.let { hint ->
                    Text("Подсказка: $hint", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль аккаунта MAX") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "Скрыть" else "Показать", maxLines = 1)
                        }
                    },
                    supportingText = {
                        Text("Тот самый пароль, который МАКС попросил придумать при входе.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    enabled = !busy && password.isNotBlank(),
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            runCatching { MaxLogin.submitPassword(context, password.trim()) }
                                .onSuccess { outcome ->
                                    error = outcome.explain()
                                    if (outcome is AuthOutcome.LoggedIn) {
                                        password = ""
                                        if (settings.useDirectConnection) {
                                            MaxWatchService.start(context)
                                        }
                                    }
                                }
                                .onFailure { error = it.message ?: "пароль не принят" }
                            AppRefresh.bump()
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Подтвердить пароль") }
                TextButton(
                    enabled = !busy,
                    onClick = {
                        MaxLogin.cancel()
                        session.passwordTrackId = null
                        session.passwordHint = null
                        password = ""
                        error = null
                        AppRefresh.bump()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Начать вход заново") }
            }
            Text(
                when {
                    vpnActive && settings.bypassVpn ->
                        "VPN включён, но обход включён тоже: код запрашивается мимо VPN. Если " +
                            "MAX всё равно откажет — значит VPN блокирует соединения без себя, " +
                            "выключите его на время входа."
                    vpnActive ->
                        "VPN включён, а обход выключен — MAX не выдаст код на зарубежный адрес. " +
                            "Включите обход выше или выключите VPN на время входа."
                    else ->
                        "Через VPN вход обычно не проходит — MAX не выдаёт код на зарубежный " +
                            "адрес. Уведомления МАКСа от этого не зависят."
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (vpnActive) FontWeight.Bold else FontWeight.Normal,
            )
        }

        error?.let { text ->
            Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * The VPN switch, with the truth about what it can and cannot do.
 *
 * Android lets an app pin its own socket to the physical network, so this connection can leave
 * around a VPN while the rest of the phone keeps it. What it cannot beat is a VPN in lockdown
 * mode ("block connections without VPN"), and a switch that stayed silent about that would look
 * broken when the login still failed -- so the card says which route the last attempt took.
 */
@Composable
private fun VpnRow(
    settings: AlertSettings,
    vpnActive: Boolean,
    update: ((AlertSettings) -> AlertSettings) -> Unit,
) {
    val palette = LocalAlertPalette.current

    SwitchRow("Обходить VPN", settings.bypassVpn) { value ->
        update { it.copy(bypassVpn = value) }
    }
    Hint(if (settings.bypassVpn) {
            "Только это подключение уходит мимо VPN — остальной трафик телефона остаётся в нём. " +
                "Не поможет, если в VPN включено «блокировать соединения без VPN»: тогда " +
                "добавьте приложение в исключения самого VPN или выключите его на время входа."
        } else {
            "Подключение идёт как весь трафик — через VPN, если он включён."
        })
    if (vpnActive) {
        Text(
            if (settings.bypassVpn) "Сейчас VPN включён, обход применяется"
            else "Сейчас VPN включён, обход выключен",
            style = MaterialTheme.typography.bodySmall,
            color = if (settings.bypassVpn) palette.statusOk else palette.statusWarn,
            fontWeight = FontWeight.Bold,
        )
    }
    NetworkRoute.lastRoute?.let { route ->
        Text("Последнее подключение: $route", style = MaterialTheme.typography.labelSmall)
    }
}

/** Which icon stands for a setup step. Presentation, so it lives here and not in the pure list. */
private fun SetupAction.icon(): ImageVector = when (this) {
    SetupAction.ENABLE_DUTY -> Icons.Filled.PowerSettingsNew
    SetupAction.NOTIFICATION_ACCESS -> Icons.Filled.NotificationsActive
    SetupAction.BATTERY -> Icons.Filled.BatteryAlert
    SetupAction.CHAT -> Icons.Filled.Forum
    SetupAction.KEYWORDS -> Icons.Filled.Tag
    SetupAction.ALL_CLEAR -> Icons.Filled.CheckCircle
}
