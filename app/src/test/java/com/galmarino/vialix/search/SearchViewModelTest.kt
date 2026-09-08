// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.search

import com.galmarino.vialix.RequestFailure
import com.galmarino.vialix.settings.Settings
import com.stadiamaps.ferrostar.core.InvalidStatusCodeException
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.UserLocation

/**
 * Exercises the typeahead pipeline with a scripted [Geocoder]: debounce, forced submit, the
 * "don't search the same thing twice" rule, cancellation of superseded requests and error mapping.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val geocoder = FakeGeocoder()
    private val settings = MutableStateFlow(Settings(languageTag = "de-DE"))
    private val location = MutableStateFlow<UserLocation?>(null)

    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = SearchViewModel(geocoder, settings, location)
        // In the app viewModelScope is Main.immediate, so the pipeline is collecting before any
        // input can arrive; the test dispatcher needs a nudge to get to the same point.
        dispatcher.scheduler.runCurrent()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `typing searches once after the debounce`() = runTest(dispatcher) {
        viewModel.onQueryChanged("Ber")
        viewModel.onQueryChanged("Berl")
        viewModel.onQueryChanged("Berlin")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS - 1)
        assertEquals(emptyList<String>(), geocoder.queries)

        advanceTimeBy(2)
        geocoder.complete(listOf(place("Berlin")))
        advanceUntilIdle()

        assertEquals(listOf("Berlin"), geocoder.queries)
        assertEquals("Berlin", viewModel.state.value.searchedQuery)
        assertEquals(listOf("Berlin"), viewModel.state.value.results.map { it.name })
        assertFalse(viewModel.state.value.isSearching)
    }

    @Test
    fun `the language bias follows the settings and the location`() = runTest(dispatcher) {
        location.value = UserLocation(GeographicCoordinate(lat = 52.5, lng = 13.4), 5.0, null, java.time.Instant.EPOCH, null)
        viewModel.onQueryChanged("Berlin")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)

        assertEquals("de-DE", geocoder.languageTags.single())
        assertEquals(52.5, geocoder.biases.single()?.lat)
    }

    @Test
    fun `short queries are not searched and clear old results`() = runTest(dispatcher) {
        viewModel.onQueryChanged("Berlin")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        geocoder.complete(listOf(place("Berlin")))
        advanceUntilIdle()

        viewModel.onQueryChanged("Be")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)

        assertEquals(1, geocoder.queries.size)
        assertEquals(emptyList<Place>(), viewModel.state.value.results)
        assertNull(viewModel.state.value.searchedQuery)
    }

    @Test
    fun `submit skips the debounce and the echo of the debounce is not searched again`() = runTest(dispatcher) {
        viewModel.onQueryChanged("Berlin")
        viewModel.submit()
        advanceTimeBy(1)
        assertEquals(listOf("Berlin"), geocoder.queries)
        geocoder.complete(listOf(place("Berlin")))

        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        assertEquals(listOf("Berlin"), geocoder.queries)
    }

    @Test
    fun `debounce does not restart a submitted request that is still running`() = runTest(dispatcher) {
        viewModel.onQueryChanged("Berlin")
        viewModel.submit()
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)

        assertEquals(listOf("Berlin"), geocoder.queries)
        assertEquals(emptyList<String>(), geocoder.cancelled)
        assertTrue(viewModel.state.value.isSearching)

        geocoder.complete(listOf(place("Berlin")))
        advanceUntilIdle()
        assertEquals("Berlin", viewModel.state.value.searchedQuery)
    }

    @Test
    fun `submit retries an unchanged query after an error`() = runTest(dispatcher) {
        viewModel.onQueryChanged("Berlin")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        geocoder.fail(IOException("offline"))
        advanceUntilIdle()
        assertEquals(SearchError.RequestFailed(RequestFailure.Offline), viewModel.state.value.error)

        viewModel.submit()
        advanceTimeBy(1)
        assertEquals(listOf("Berlin", "Berlin"), geocoder.queries)
        assertNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.isSearching)
    }

    @Test
    fun `a newer query cancels the running request without leaving a phantom error`() = runTest(dispatcher) {
        viewModel.onQueryChanged("Berlin")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        assertEquals(listOf("Berlin"), geocoder.queries)

        viewModel.onQueryChanged("Hamburg")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        assertEquals(listOf("Berlin", "Hamburg"), geocoder.queries)
        assertTrue(geocoder.cancelled.contains("Berlin"))

        geocoder.complete(listOf(place("Hamburg")))
        advanceUntilIdle()
        assertNull(viewModel.state.value.error)
        assertEquals("Hamburg", viewModel.state.value.searchedQuery)
        assertEquals(listOf("Hamburg"), viewModel.state.value.results.map { it.name })
    }

    @Test
    fun `server errors keep their status code`() = runTest(dispatcher) {
        viewModel.onQueryChanged("Berlin")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        geocoder.fail(InvalidStatusCodeException(503))
        advanceUntilIdle()

        assertEquals(SearchError.RequestFailed(RequestFailure.ServerError(503)), viewModel.state.value.error)
        assertEquals(emptyList<Place>(), viewModel.state.value.results)
    }

    @Test
    fun `reset clears everything`() = runTest(dispatcher) {
        viewModel.onQueryChanged("Berlin")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        geocoder.complete(listOf(place("Berlin")))
        advanceUntilIdle()

        viewModel.reset()
        assertEquals(SearchState(), viewModel.state.value)
    }

    private fun place(name: String) = Place(name, null, GeographicCoordinate(lat = 0.0, lng = 0.0))

    @Test
    fun `clearing the field cancels the request in flight and nothing repopulates the results`() = runTest(dispatcher) {
        viewModel.onQueryChanged("Berlin")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        assertEquals(listOf("Berlin"), geocoder.queries)

        viewModel.onQueryChanged("")
        advanceUntilIdle()

        assertEquals(listOf("Berlin"), geocoder.cancelled)
        assertFalse(viewModel.state.value.isSearching)
        assertEquals(emptyList<Place>(), viewModel.state.value.results)
        assertNull(viewModel.state.value.searchedQuery)
    }

    @Test
    fun `a query is searched trimmed`() = runTest(dispatcher) {
        viewModel.onQueryChanged("  Berlin ")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        geocoder.complete(listOf(place("Berlin")))
        advanceUntilIdle()

        assertEquals(listOf("Berlin"), geocoder.queries)
        assertEquals("Berlin", viewModel.state.value.searchedQuery)
        assertEquals("  Berlin ", viewModel.state.value.query)
    }

    /** Every call suspends until the test completes or fails it; cancellation is recorded. */
    private class FakeGeocoder : Geocoder {
        val queries = mutableListOf<String>()
        val biases = mutableListOf<GeographicCoordinate?>()
        val languageTags = mutableListOf<String?>()
        val cancelled = mutableListOf<String>()
        private var pending: CompletableDeferred<List<Place>>? = null

        override suspend fun search(query: String, bias: GeographicCoordinate?, languageTag: String?): List<Place> {
            queries += query
            biases += bias
            languageTags += languageTag
            val deferred = CompletableDeferred<List<Place>>().also { pending = it }
            try {
                return deferred.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelled += query
                throw e
            }
        }

        override suspend fun reverse(coordinate: GeographicCoordinate, languageTag: String?): Place? = null

        fun complete(places: List<Place>) = checkNotNull(pending).complete(places)

        fun fail(e: Exception) = checkNotNull(pending).completeExceptionally(e)
    }
}
