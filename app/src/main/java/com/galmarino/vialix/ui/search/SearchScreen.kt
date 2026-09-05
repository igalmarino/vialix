package com.galmarino.vialix.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galmarino.vialix.R
import com.galmarino.vialix.navigation.Destination
import com.galmarino.vialix.navigation.distanceMeters
import com.galmarino.vialix.places.FavoriteKind
import com.galmarino.vialix.places.SavedPlaces
import com.galmarino.vialix.search.Place
import com.galmarino.vialix.search.SearchError
import com.galmarino.vialix.search.SearchViewModel
import com.galmarino.vialix.ui.ClearRecentsDialog
import com.galmarino.vialix.ui.FavoriteOptionsDialog
import com.galmarino.vialix.ui.FavoriteRow
import com.galmarino.vialix.ui.RecentOptionsDialog
import com.galmarino.vialix.ui.RecentRow
import com.galmarino.vialix.ui.SectionHeaderRow
import com.galmarino.vialix.ui.failureText
import com.galmarino.vialix.ui.labelRes
import com.stadiamaps.ferrostar.ui.formatters.DistanceFormatter
import kotlinx.coroutines.launch
import uniffi.ferrostar.UserLocation

/**
 * Full-screen destination search, layered over the map like the settings page. The text field
 * lives in the app bar. Before the user has typed enough to search, the body offers what they
 * already know: Home/Work and the recent destinations; afterwards it shows results (with their
 * distance from the user), or an empty / error line.
 *
 * This is also where saved places are managed: an unset Home/Work row (or a long-press on a set
 * one) opens [FavoriteOptionsDialog], a long-press on a recent removes it and the recents header
 * has a Clear action.
 *
 * @param assigningFavorite Set when the pick will be stored as Home/Work rather than routed to;
 *   the screen says so in a banner and hides the shortcuts themselves.
 * @param onAssignFavoriteFromSearch The user wants to search for the address to store as Home/Work;
 *   the caller sets [assigningFavorite] and the screen stays open.
 * @param onSetFavoriteToCurrentLocation Returns `false` when there is no fix yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    savedPlaces: SavedPlaces,
    assigningFavorite: FavoriteKind?,
    userLocation: UserLocation?,
    distanceFormatter: DistanceFormatter,
    onBack: () -> Unit,
    onDestinationPicked: (Destination) -> Unit,
    onAssignFavoriteFromSearch: (FavoriteKind) -> Unit,
    onSetFavoriteToCurrentLocation: (FavoriteKind) -> Boolean,
    onClearFavorite: (FavoriteKind) -> Unit,
    onRemoveRecent: (Destination) -> Unit,
    onClearRecents: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val state by viewModel.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // The field value (text + cursor) stays local; only the text goes to the ViewModel. Round-
    // tripping a TextFieldValue through a StateFlow makes the cursor jump under fast typing. It is
    // seeded from the ViewModel so reopening the screen shows the previous query.
    var fieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(state.query, selection = TextRange(state.query.length)))
    }
    // Typing updates both synchronously, so this only fires for changes made elsewhere: a voice
    // query arriving while the screen is (re)opened, or a reset() after a pick.
    LaunchedEffect(state.query) {
        if (state.query != fieldValue.text) fieldValue = TextFieldValue(state.query, selection = TextRange(state.query.length))
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun pick(destination: Destination) {
        keyboard?.hide()
        onDestinationPicked(destination)
    }

    var recentToManage by remember { mutableStateOf<Destination?>(null) }
    var favoriteToManage by rememberSaveable { mutableStateOf<FavoriteKind?>(null) }
    var confirmClearRecents by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noFixYet = stringResource(R.string.error_no_fix_yet)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = fieldValue,
                        onValueChange = {
                            fieldValue = it
                            viewModel.onQueryChanged(it.text)
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
                        // The title slot provides titleLarge; a search field is bodyLarge.
                        textStyle = MaterialTheme.typography.bodyLarge,
                        trailingIcon = {
                            if (fieldValue.text.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        fieldValue = TextFieldValue("")
                                        viewModel.onQueryChanged("")
                                    },
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_clear),
                                        contentDescription = stringResource(R.string.search_clear),
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.submit() }),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.isSearching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            val error = state.error
            when {
                state.query.trim().length < SearchViewModel.MIN_QUERY_LENGTH ->
                    SavedPlacesList(
                        places = savedPlaces,
                        assigningFavorite = assigningFavorite,
                        onPick = ::pick,
                        onFavoriteClick = { kind ->
                            val favorite = savedPlaces.favorite(kind)
                            if (favorite != null) pick(favorite) else favoriteToManage = kind
                        },
                        onFavoriteLongClick = { favoriteToManage = it },
                        onRecentLongClick = { recentToManage = it },
                        onClearRecents = { confirmClearRecents = true },
                    )
                error != null -> StatusLine(errorText(error), retry = viewModel::submit)
                state.searchedQuery != null && state.results.isEmpty() && !state.isSearching ->
                    StatusLine(stringResource(R.string.search_no_results, state.searchedQuery.orEmpty()))
                else ->
                    // Previous results stay visible while a new request is in flight, so the list
                    // does not flicker on every keystroke.
                    LazyColumn(modifier = Modifier.fillMaxSize().imePadding()) {
                        items(state.results) { place ->
                            ResultRow(
                                place = place,
                                distance = userLocation?.let { distanceFormatter.format(distanceMeters(it.coordinates, place.coordinate)) },
                                onClick = { pick(Destination.of(place)) },
                            )
                        }
                    }
            }
        }
    }

    recentToManage?.let { destination ->
        RecentOptionsDialog(
            destination = destination,
            onRemove = {
                recentToManage = null
                onRemoveRecent(destination)
            },
            onDismiss = { recentToManage = null },
        )
    }
    favoriteToManage?.let { kind ->
        FavoriteOptionsDialog(
            kind = kind,
            isSet = savedPlaces.favorite(kind) != null,
            onSearchAddress = {
                favoriteToManage = null
                onAssignFavoriteFromSearch(kind)
            },
            onUseCurrentLocation = {
                favoriteToManage = null
                if (!onSetFavoriteToCurrentLocation(kind)) scope.launch { snackbarHostState.showSnackbar(noFixYet) }
            },
            onClear = {
                favoriteToManage = null
                onClearFavorite(kind)
            },
            onDismiss = { favoriteToManage = null },
        )
    }
    if (confirmClearRecents) {
        ClearRecentsDialog(
            onConfirm = {
                confirmClearRecents = false
                onClearRecents()
            },
            onDismiss = { confirmClearRecents = false },
        )
    }
}

/** The empty-query body: the assignment banner or the Home/Work rows, then the recents with their Clear action. */
@Composable
private fun SavedPlacesList(
    places: SavedPlaces,
    assigningFavorite: FavoriteKind?,
    onPick: (Destination) -> Unit,
    onFavoriteClick: (FavoriteKind) -> Unit,
    onFavoriteLongClick: (FavoriteKind) -> Unit,
    onRecentLongClick: (Destination) -> Unit,
    onClearRecents: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().imePadding()) {
        if (assigningFavorite != null) {
            item(key = "assign") {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.search_assign_header, stringResource(assigningFavorite.labelRes())),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        } else {
            items(FavoriteKind.entries, key = { "favorite-$it" }) { kind ->
                FavoriteRow(
                    kind = kind,
                    destination = places.favorite(kind),
                    onClick = { onFavoriteClick(kind) },
                    onLongClick = { onFavoriteLongClick(kind) },
                )
            }
        }
        if (places.recents.isNotEmpty()) {
            item(key = "recent-header") {
                SectionHeaderRow(
                    title = stringResource(R.string.recent_header),
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    action = { TextButton(onClick = onClearRecents) { Text(stringResource(R.string.recent_clear)) } },
                )
            }
            items(places.recents, key = { "recent-${it.coordinate.lat},${it.coordinate.lng}" }) { destination ->
                RecentRow(destination = destination, onClick = { onPick(destination) }, onLongClick = { onRecentLongClick(destination) })
            }
        }
    }
}

@Composable
private fun ResultRow(place: Place, distance: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(place.name) },
        supportingContent = place.address?.let { { Text(it) } },
        leadingContent = { Icon(painter = painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingContent =
            distance?.let {
                {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun StatusLine(text: String, retry: (() -> Unit)? = null) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (retry != null) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = retry) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}

@Composable
private fun errorText(error: SearchError): String =
    when (error) {
        is SearchError.RequestFailed -> failureText(error.failure, R.string.search_server_error, R.string.search_error)
    }
