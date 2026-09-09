// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.galmarino.vialix.AppGraph
import com.galmarino.vialix.RequestFailure
import com.galmarino.vialix.navigation.distanceMeters
import com.galmarino.vialix.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uniffi.ferrostar.UserLocation

/** Search failures, rendered inline in the results area. Mapped to strings in the UI layer. */
sealed interface SearchError {
    data class RequestFailed(val failure: RequestFailure) : SearchError
}

data class SearchState(
    /** What the user has typed. */
    val query: String = "",
    /** The trimmed query [results] belong to; null until the first search completes. */
    val searchedQuery: String? = null,
    val results: List<Place> = emptyList(),
    val isSearching: Boolean = false,
    val error: SearchError? = null,
)

/**
 * Typeahead search over a [Geocoder]. Requests fire after a typing pause and a keyboard "search"
 * fires one immediately; a newer request cancels the one in flight, so results never go stale.
 * Activity-scoped, so the query and results survive rotation and closing the search screen.
 *
 * Takes the settings as a plain [StateFlow] rather than the Android-bound store so the pipeline
 * can be unit-tested with a fake [Geocoder].
 */
@OptIn(FlowPreview::class)
class SearchViewModel(
    private val geocoder: Geocoder,
    private val settings: StateFlow<Settings>,
    private val location: StateFlow<UserLocation?>,
) : ViewModel() {

    private data class Request(val query: String, val forced: Boolean)

    private val query = MutableStateFlow("")
    private val submits = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** A submitted query whose delayed debounce echo must not restart the request in flight. */
    private var submittedQuery: String? = null

    /** The query dropped below the minimum length: cancel whatever is in flight, now, not after the debounce. */
    private val clears = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            merge(
                query.debounce(DEBOUNCE_MS).map { Request(it, forced = false) },
                submits.map { Request(query.value, forced = true) },
                clears.map { Request("", forced = false) },
            )
                .filter { request -> request.forced || request.query.trim() != submittedQuery }
                .collectLatest { runSearch(it) }
        }
    }

    fun onQueryChanged(text: String) {
        query.value = text
        if (text.trim() != submittedQuery) submittedQuery = null
        if (text.trim().length < MIN_QUERY_LENGTH) {
            // Too short to search: show the hint right away rather than after the debounce, and
            // make sure a request still running for the previous text cannot repopulate the results.
            _state.update { it.copy(query = text, results = emptyList(), searchedQuery = null, error = null, isSearching = false) }
            clears.tryEmit(Unit)
        } else {
            _state.update { it.copy(query = text) }
        }
    }

    /** Keyboard "search" action: skip the debounce, and retry even if the text has not changed. */
    fun submit() {
        submittedQuery = query.value.trim()
        submits.tryEmit(Unit)
    }

    /** Called once a place has been picked, so the next search starts clean. */
    fun reset() {
        submittedQuery = null
        query.value = ""
        _state.value = SearchState()
        clears.tryEmit(Unit)
    }

    private suspend fun runSearch(request: Request) {
        val q = request.query.trim()
        if (q.length < MIN_QUERY_LENGTH) return
        // The debounce echoes the query the user just submitted with Enter; don't search it twice.
        if (!request.forced && q == _state.value.searchedQuery && _state.value.error == null) return

        _state.update { it.copy(isSearching = true, error = null) }
        try {
            val bias = location.value?.coordinates
            val places = geocoder.search(q, bias, settings.value.resolvedLanguageTag())
            val origin = location.value?.coordinates
            val results = if (origin != null) places.sortedBy { distanceMeters(origin, it.coordinate) } else places
            _state.update { it.copy(results = results, searchedQuery = q, isSearching = false) }
        } catch (e: CancellationException) {
            // A newer query superseded this one; collectLatest cancelled us. Not an error.
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Search failed for '$q'", e)
            _state.update {
                it.copy(
                    results = emptyList(),
                    searchedQuery = q,
                    isSearching = false,
                    error = SearchError.RequestFailed(RequestFailure.of(e)),
                )
            }
        }
    }

    class Factory(private val graph: AppGraph, private val location: StateFlow<UserLocation?>) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(SearchViewModel::class.java)) {
                "Unknown ViewModel class ${modelClass.name}"
            }
            return SearchViewModel(graph.geocoder, graph.settings.state, location) as T
        }
    }

    companion object {
        const val DEBOUNCE_MS = 400L
        const val MIN_QUERY_LENGTH = 3
        private const val TAG = "SearchViewModel"
    }
}
