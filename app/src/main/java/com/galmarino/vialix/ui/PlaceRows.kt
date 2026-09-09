// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.galmarino.vialix.R
import com.galmarino.vialix.navigation.Destination
import com.galmarino.vialix.places.FavoriteKind

/**
 * List rows and dialogs for saved places, used by the search screen's empty state. Tap navigates;
 * long-press (when [onLongClick] is given) manages the entry. The same action sits behind a
 * visible "more" button at the end of the row and is offered to TalkBack as a custom action, so
 * long-press is a shortcut rather than the only way in.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecentRow(destination: Destination, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val manageLabel = stringResource(R.string.recent_remove)
    ListItem(
        headlineContent = {
            Text(destination.name ?: stringResource(R.string.dropped_pin_title), style = MaterialTheme.typography.titleMedium)
        },
        supportingContent = {
            Text(destination.address ?: formatCoordinates(destination.coordinate), maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            PlaceLeadingIcon(R.drawable.ic_history)
        },
        trailingContent = { ManageButton(onLongClick, manageLabel) },
        modifier =
            Modifier.combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = withLongPressHaptic(onLongClick),
                onLongClickLabel = onLongClick?.let { manageLabel },
            ),
    )
}

/**
 * "Home · 1 Some Street" row, or "Home · Set location" while unset. Tap navigates (or sets it up
 * when unset); long-press (when [onLongClick] is given) manages the shortcut.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FavoriteRow(kind: FavoriteKind, destination: Destination?, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val manageLabel = stringResource(R.string.favorite_options, stringResource(kind.labelRes()))
    ListItem(
        headlineContent = { Text(stringResource(kind.labelRes()), style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Text(
                text =
                    if (destination == null) {
                        stringResource(R.string.favorite_unset)
                    } else {
                        destination.name ?: destination.address ?: formatCoordinates(destination.coordinate)
                    },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            PlaceLeadingIcon(kind.iconRes(), favourite = true)
        },
        trailingContent = { ManageButton(onLongClick, manageLabel) },
        modifier =
            Modifier.combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = withLongPressHaptic(onLongClick),
                onLongClickLabel = onLongClick?.let { manageLabel },
            ),
    )
}

/** Shared icon footprint keeps saved places, recents and search results aligned. */
@Composable
internal fun PlaceLeadingIcon(@DrawableRes iconRes: Int, favourite: Boolean = false) {
    Surface(
        shape = CircleShape,
        color = if (favourite) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (favourite) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}

/** The row's trailing "more" button when it can be managed, else the plain chevron of a row that only navigates. */
@Composable
private fun ManageButton(onManage: (() -> Unit)?, label: String) {
    if (onManage == null) {
        Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = null)
    } else {
        IconButton(onClick = onManage) {
            Icon(painter = painterResource(R.drawable.ic_more_vert), contentDescription = label)
        }
    }
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
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action?.invoke()
    }
}

fun FavoriteKind.labelRes(): Int = when (this) {
    FavoriteKind.HOME -> R.string.favorite_home
    FavoriteKind.WORK -> R.string.favorite_work
}

internal fun FavoriteKind.iconRes(): Int = when (this) {
    FavoriteKind.HOME -> R.drawable.ic_home
    FavoriteKind.WORK -> R.drawable.ic_work
}
