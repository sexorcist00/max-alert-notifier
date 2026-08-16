package ru.maxalert.notifier

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
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
import ru.maxalert.notifier.ui.Spacing

@Composable
internal fun LogTab(settings: AlertSettings, update: ((AlertSettings) -> AlertSettings) -> Unit) {
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
