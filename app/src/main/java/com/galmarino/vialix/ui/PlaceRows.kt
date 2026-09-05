package com.galmarino.vialix.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.galmarino.vialix.R
import com.galmarino.vialix.navigation.Destination
import com.galmarino.vialix.places.FavoriteKind

/**
 * List rows and dialogs for saved places, used by the search screen's empty state. Tap navigates;
 * long-press (when [onLongClick] is given) manages the entry.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecentRow(destination: Destination, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    ListItem(
        headlineContent = {
            Text(destination.name ?: stringResource(R.string.dropped_pin_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(destination.address ?: formatCoordinates(destination.coordinate), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_history),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = { Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = withLongPressHaptic(onLongClick)),
    )
}

/**
 * "Home · 1 Some Street" row, or "Home · Set location" while unset. Tap navigates (or sets it up
 * when unset); long-press (when [onLongClick] is given) manages the shortcut.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FavoriteRow(kind: FavoriteKind, destination: Destination?, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(stringResource(kind.labelRes())) },
        supportingContent = {
            Text(
                text =
                    if (destination == null) {
                        stringResource(R.string.favorite_unset)
                    } else {
                        destination.name ?: destination.address ?: formatCoordinates(destination.coordinate)
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Icon(painter = painterResource(kind.iconRes()), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = { Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = withLongPressHaptic(onLongClick)),
    )
}

/** Wraps a long-press action so it gives the same haptic tick as the other long-presses in the app. */
@Composable
private fun withLongPressHaptic(action: (() -> Unit)?): (() -> Unit)? {
    val haptics = LocalHapticFeedback.current
    return action?.let {
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            it()
        }
    }
}

/** Options for a Home/Work shortcut: pick an address, use the current fix, or (when set) clear it. */
@Composable
internal fun FavoriteOptionsDialog(
    kind: FavoriteKind,
    isSet: Boolean,
    onSearchAddress: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.favorite_options, stringResource(kind.labelRes()))) },
        text = {
            Column {
                DialogAction(R.string.favorite_search_address, R.drawable.ic_search, onSearchAddress)
                DialogAction(R.string.favorite_use_current_location, R.drawable.ic_my_location, onUseCurrentLocation)
                if (isSet) DialogAction(R.string.favorite_clear, R.drawable.ic_clear, onClear)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** One option of a Material "simple dialog": a list item on the dialog's own container. */
@Composable
private fun DialogAction(labelRes: Int, iconRes: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(labelRes)) },
        leadingContent = { Icon(painter = painterResource(iconRes), contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

/** A text button for a destructive action: the whole button (label, ripple, disabled state) in the error colour. */
@Composable
private fun DestructiveTextButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
        Text(text)
    }
}

/** Long-press menu for a recent: the one thing it offers is forgetting the place. */
@Composable
internal fun RecentOptionsDialog(destination: Destination, onRemove: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(destination.name ?: stringResource(R.string.dropped_pin_title)) },
        text = { Text(destination.address ?: formatCoordinates(destination.coordinate)) },
        confirmButton = { DestructiveTextButton(stringResource(R.string.recent_remove), onRemove) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Confirmation behind the "Clear" action in the recents header. */
@Composable
internal fun ClearRecentsDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recent_clear_confirm_title)) },
        text = { Text(stringResource(R.string.recent_clear_confirm_text)) },
        confirmButton = { DestructiveTextButton(stringResource(R.string.recent_clear), onConfirm) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Section header with an optional trailing action, as used above the recents list. */
@Composable
internal fun SectionHeaderRow(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action?.invoke()
    }
}

fun FavoriteKind.labelRes(): Int =
    when (this) {
        FavoriteKind.HOME -> R.string.favorite_home
        FavoriteKind.WORK -> R.string.favorite_work
    }

internal fun FavoriteKind.iconRes(): Int =
    when (this) {
        FavoriteKind.HOME -> R.drawable.ic_home
        FavoriteKind.WORK -> R.drawable.ic_work
    }
