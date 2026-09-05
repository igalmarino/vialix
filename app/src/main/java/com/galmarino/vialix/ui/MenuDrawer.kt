package com.galmarino.vialix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.galmarino.vialix.BuildConfig
import com.galmarino.vialix.R

/**
 * The navigation drawer behind the menu icon in the search pill (Material 3's standard pairing
 * for a hamburger icon): an app header, the way into Settings, plus the version and the data
 * credits. [routingCredit] and [searchCredit] name the configured providers (see `Attribution`).
 * The caller owns the `ModalNavigationDrawer` and its state; this is only the drawer sheet.
 */
@Composable
fun MenuDrawerContent(routingCredit: String, searchCredit: String, onOpenSettings: () -> Unit) {
    ModalDrawerSheet {
        Column(modifier = Modifier.fillMaxHeight().padding(vertical = 12.dp)) {
            DrawerHeader()
            HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.settings)) },
                icon = { Icon(painter = painterResource(R.drawable.ic_settings), contentDescription = null) },
                selected = false,
                onClick = onOpenSettings,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
            // The version and data credits are pinned to the bottom of the drawer, away from
            // the actions; the divider separates the two groups.
            Spacer(Modifier.weight(1f))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
            Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                modifier = Modifier.padding(horizontal = 28.dp),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = stringResource(R.string.about_credits, routingCredit, searchCredit),
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** The launcher glyph on a rounded tile, the app name and a one-line tagline. */
@Composable
private fun DrawerHeader() {
    Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp)) {
        Box(
            modifier = Modifier
                .size(LOGO_TILE_SIZE)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            // The monochrome launcher vector is a 108 dp adaptive icon whose artwork sits in the
            // 66 dp safe zone; drawing it larger than the clipped tile makes the glyph fill it.
            Icon(
                painter = painterResource(R.drawable.ic_launcher_monochrome),
                contentDescription = null,
                modifier = Modifier.requiredSize(LOGO_TILE_SIZE * 108 / 66),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.menu_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val LOGO_TILE_SIZE = 56.dp
