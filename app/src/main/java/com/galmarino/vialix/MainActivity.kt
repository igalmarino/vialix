package com.galmarino.vialix

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.galmarino.vialix.navigation.NavigationViewModel
import com.galmarino.vialix.search.SearchViewModel
import com.galmarino.vialix.settings.systemManagesAppLocale
import com.galmarino.vialix.settings.withAppLocale
import com.galmarino.vialix.ui.NavigationScreen
import com.galmarino.vialix.ui.rememberDistanceFormatter
import com.galmarino.vialix.ui.search.SearchScreen
import com.galmarino.vialix.ui.settings.SettingsScreen
import com.galmarino.vialix.ui.theme.VialixTheme

class MainActivity : ComponentActivity() {

    private val graph: AppGraph
        get() = (application as NavApplication).graph

    private val viewModel: NavigationViewModel by viewModels { NavigationViewModel.Factory(graph) }
    private val searchViewModel: SearchViewModel by viewModels { SearchViewModel.Factory(graph, viewModel.location) }

    /**
     * API 29–32: apply the App language setting ourselves (on 33+ the framework does it from the
     * per-app locale). `application` is still null here; the application context is not.
     */
    override fun attachBaseContext(newBase: Context) {
        if (systemManagesAppLocale) return super.attachBaseContext(newBase)
        val tag = (newBase.applicationContext as NavApplication).graph.settings.state.value.uiLanguageTag
        super.attachBaseContext(newBase.withAppLocale(tag))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The OS per-app language screen may have changed it while the process was alive.
        graph.settings.syncUiLanguageFromSystem()
        // Edge to edge. `auto` picks the glyph colour from the configuration's night flag for the
        // frame before Compose draws; SystemBarGlyphs below then follows the resolved theme, which
        // is what everything under the bars (map tiles, chrome, sheets, pages) follows as well.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        window.isNavigationBarContrastEnforced = false

        setContent {
            // One answer to "is the app dark?" (the Appearance setting resolved against the
            // system), shared by the Material theme, the chrome over the map, the system-bar
            // glyphs and — through the ViewModel — the basemap style.
            val currentSettings by graph.settings.state.collectAsStateWithLifecycle()
            val isDark = currentSettings.resolvesToDark(isSystemInDarkTheme())
            LaunchedEffect(isDark) { viewModel.onDarkThemeChanged(isDark) }

            VialixTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var showSettings by rememberSaveable { mutableStateOf(false) }
                    var showSearch by rememberSaveable { mutableStateOf(false) }
                    SystemBarGlyphs(darkTheme = isDark)

                    // The settings and search pages are layered over the map rather than replacing
                    // it, so the MapLibre view (and its camera) survives a round trip through them.
                    Box {
                        NavigationScreen(
                            viewModel = viewModel,
                            config = graph.config,
                            settings = graph.settings,
                            onOpenSettings = { showSettings = true },
                            onOpenSearch = { showSearch = true },
                            onVoiceQuery = { text ->
                                searchViewModel.onQueryChanged(text)
                                searchViewModel.submit()
                                showSearch = true
                            },
                        )
                        OverlayScreen(visible = showSearch) {
                            val screenState by viewModel.screenState.collectAsStateWithLifecycle()
                            val savedPlaces by viewModel.savedPlaces.collectAsStateWithLifecycle()
                            val location by viewModel.location.collectAsStateWithLifecycle()
                            SearchScreen(
                                viewModel = searchViewModel,
                                savedPlaces = savedPlaces,
                                assigningFavorite = screenState.favoriteToAssign,
                                userLocation = location,
                                distanceFormatter = rememberDistanceFormatter(currentSettings),
                                onBack = {
                                    showSearch = false
                                    viewModel.cancelFavoriteAssignment()
                                },
                                onDestinationPicked = { destination ->
                                    showSearch = false
                                    searchViewModel.reset()
                                    // Either stored as Home/Work (if that is what the search was
                                    // for) or routed to like a long-press: pin, sheet, preview.
                                    viewModel.onPlacePicked(destination)
                                },
                                onAssignFavoriteFromSearch = viewModel::beginFavoriteAssignment,
                                onSetFavoriteToCurrentLocation = viewModel::setFavoriteToCurrentLocation,
                                onClearFavorite = viewModel::clearFavorite,
                                onRemoveRecent = viewModel::removeRecent,
                                onClearRecents = viewModel::clearRecents,
                            )
                        }
                        OverlayScreen(visible = showSettings) {
                            SettingsScreen(
                                settings = graph.settings,
                                showDeveloperOptions = BuildConfig.DEBUG,
                                onBack = { showSettings = false },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Light glyphs on a dark app, dark glyphs on a light one. Every surface that can end up under a
 * system bar — map tiles, the scrim and pill, the bottom sheet, the menu drawer, the Search and
 * Settings pages — follows the same resolved theme, so one choice is right everywhere.
 */
@Composable
private fun SystemBarGlyphs(darkTheme: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? ComponentActivity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

/** A full-screen page over the map that fades and slides in from below rather than popping. */
@Composable
private fun OverlayScreen(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 8 },
        exit = fadeOut() + slideOutVertically { it / 8 },
    ) {
        content()
    }
}
