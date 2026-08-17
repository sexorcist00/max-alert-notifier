package ru.maxalert.notifier

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import ru.maxalert.notifier.ui.Spacing
import ru.maxalert.notifier.ui.TextAlpha

@Composable
internal fun CollapsibleCard(
    title: String,
    subtitle: String? = null,
    startOpen: Boolean = false,
    icon: ImageVector? = null,
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
                icon?.let { vector ->
                    Icon(
                        imageVector = vector,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = TextAlpha.SECONDARY),
                        modifier = Modifier.size(Spacing.inlineIcon),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    subtitle?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = TextAlpha.SECONDARY),
                        )
                    }
                }
                Text(if (open) "Свернуть" else "Открыть", style = MaterialTheme.typography.labelLarge)
            }
            AnimatedVisibility(visible = open) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) { content() }
            }
        }
    }
}

/**
 * A card with a heading, and optionally the icon of what the heading is about.
 *
 * The icon is not decoration: on a screen of eight stacked cards it is what lets someone find
 * the sound settings without reading four headings on the way there.
 */
@Composable
internal fun SectionCard(
    title: String,
    icon: ImageVector? = null,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.let { vector ->
                    Icon(
                        imageVector = vector,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = TextAlpha.SECONDARY),
                        modifier = Modifier.size(Spacing.inlineIcon),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

/** The setting as it stands now, with one tap to open the list of alternatives. */
@Composable
internal fun CurrentChoiceRow(
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
internal fun ChoiceRow(
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
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * One level's word list, labelled with the colour that level raises.
 *
 * The dot is in the label rather than beside the field so it moves with the label when the
 * field is focused, and so nothing has to be aligned by hand at a large font scale.
 */
@Composable
internal fun KeywordField(
    level: AlertLevel,
    label: String,
    value: String,
    support: String? = null,
    focusRequester: FocusRequester? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(Spacing.bulletDot)
                        .clip(CircleShape)
                        .background(Color(level.colorArgb))
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(label)
            }
        },
        supportingText = support?.let { text -> { Text(text) } },
        modifier = Modifier
            .fillMaxWidth()
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
    )
}

/** A duration as chips: no keyboard, no unit to guess, no way to type 3 instead of 300. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DurationChoice(
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

/** Play or stop a preview. An icon button keeps the 48dp target Android asks for. */
@Composable
internal fun PreviewButton(context: Context, soundUri: String?) {
    val playing = AlarmPreview.playing == soundUri
    IconButton(onClick = { AlarmPreview.toggleSound(context, soundUri) }) {
        Icon(
            imageVector = if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = if (playing) "Остановить прослушивание" else "Прослушать сигнал",
        )
    }
}

/**
 * Supporting text: one step down in size and in opacity.
 *
 * Explanations used to be full-strength text one size smaller, which put them at the same
 * visual weight as the setting they explain. Hierarchy here comes from the opacity ladder in
 * [TextAlpha] rather than from a grey of its own -- one that would have to be picked twice,
 * once per theme, and would drift.
 */
@Composable
internal fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = TextAlpha.SECONDARY),
    )
}
