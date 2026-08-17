package ru.maxalert.notifier

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ru.maxalert.notifier.ui.LocalAlertPalette
import ru.maxalert.notifier.ui.Spacing
import ru.maxalert.notifier.ui.TextAlpha

@Composable
internal fun LogTab(settings: AlertSettings, update: ((AlertSettings) -> AlertSettings) -> Unit) {
    val context = LocalContext.current
    val entries = EventLog.entries
    val palette = LocalAlertPalette.current
    val format = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    SectionCard("Последние сообщения из МАКСа", icon = Icons.Filled.Forum) {
        if (entries.isEmpty()) {
            // An empty state that only says "empty" leaves the user with nothing to do.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Inbox,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = TextAlpha.SECONDARY),
                    modifier = Modifier.size(Spacing.xxl),
                )
                Spacer(Modifier.width(Spacing.md))
                Hint(
                    "Пока пусто. Напишите что-нибудь в нужный чат — здесь появится точное " +
                        "название чата, которое надо вписать в фильтр."
                )
            }
        } else {
            entries.forEach { entry ->
                val watched = entry.chat.isNotBlank() &&
                    settings.chatFilter.isNotBlank() &&
                    entry.chat.contains(settings.chatFilter, ignoreCase = true)
                Row(Modifier.fillMaxWidth()) {
                    // One glyph per outcome: what rang, what was watched and ignored, what
                    // came from a chat nobody is watching. The journal is read to find out
                    // which of those happened, so it should not have to be read to find out.
                    Icon(
                        imageVector = when {
                            entry.fired -> Icons.Filled.NotificationsActive
                            watched -> Icons.Filled.Visibility
                            else -> Icons.AutoMirrored.Filled.Chat
                        },
                        contentDescription = when {
                            entry.fired -> "Сработала тревога"
                            watched -> "Из наблюдаемого чата"
                            else -> "Из другого чата"
                        },
                        tint = when {
                            entry.fired -> MaterialTheme.colorScheme.error
                            watched -> palette.statusOk
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = TextAlpha.SECONDARY)
                        },
                        modifier = Modifier.size(Spacing.inlineIcon),
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${format.format(Date(entry.time))}  ${entry.chat}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            entry.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = TextAlpha.BODY),
                        )
                        Text(
                            entry.reason,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = TextAlpha.SECONDARY),
                        )
                        if (entry.chat.isNotBlank() && !watched) {
                            TextButton(onClick = { update { it.copy(chatFilter = entry.chat) } }) {
                                Text("Следить за этим чатом")
                            }
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
