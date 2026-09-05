package com.galmarino.vialix.places

import com.galmarino.vialix.navigation.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uniffi.ferrostar.GeographicCoordinate

class SavedPlacesRepositoryTest {

    private class InMemoryStore(var value: String? = null) : KeyValueStore {
        var writes = 0

        override fun read(): String? = value

        override fun write(value: String) {
            this.value = value
            writes++
        }
    }

    private fun place(n: Int, name: String? = "Place $n", address: String? = "Street $n") =
        Destination(GeographicCoordinate(lat = 10.0 + n, lng = 20.0 + n), name, address)

    @Test
    fun `starts empty when nothing is stored`() {
        val repository = SavedPlacesRepository(InMemoryStore())
        assertEquals(SavedPlaces(), repository.state.value)
    }

    @Test
    fun `newest recent comes first`() {
        val repository = SavedPlacesRepository(InMemoryStore())
        repository.addRecent(place(1))
        repository.addRecent(place(2))
        assertEquals(listOf(place(2), place(1)), repository.state.value.recents)
    }

    @Test
    fun `navigating to a recent again moves it to the front instead of duplicating it`() {
        val repository = SavedPlacesRepository(InMemoryStore())
        repository.addRecent(place(1))
        repository.addRecent(place(2))
        repository.addRecent(place(1, name = "Renamed"))
        assertEquals(listOf(place(1, name = "Renamed"), place(2)), repository.state.value.recents)
    }

    @Test
    fun `recents are capped at twenty`() {
        val repository = SavedPlacesRepository(InMemoryStore())
        repeat(25) { repository.addRecent(place(it)) }
        val recents = repository.state.value.recents
        assertEquals(SavedPlaces.MAX_RECENTS, recents.size)
        assertEquals(place(24), recents.first())
        assertEquals(place(5), recents.last())
    }

    @Test
    fun `a recent can be removed by coordinate and the rest keep their order`() {
        val repository = SavedPlacesRepository(InMemoryStore())
        repository.addRecent(place(1))
        repository.addRecent(place(2))
        repository.addRecent(place(3))

        // Same spot, different label: still the same recent.
        repository.removeRecent(place(2, name = null, address = null))
        assertEquals(listOf(place(3), place(1)), repository.state.value.recents)
    }

    @Test
    fun `removing an unknown recent changes nothing`() {
        val repository = SavedPlacesRepository(InMemoryStore())
        repository.addRecent(place(1))
        repository.removeRecent(place(9))
        assertEquals(listOf(place(1)), repository.state.value.recents)
    }

    @Test
    fun `clearing recents leaves the shortcuts alone`() {
        val repository = SavedPlacesRepository(InMemoryStore())
        repository.setFavorite(FavoriteKind.HOME, place(1))
        repository.addRecent(place(2))
        repository.addRecent(place(3))

        repository.clearRecents()
        assertEquals(emptyList<Destination>(), repository.state.value.recents)
        assertEquals(place(1), repository.state.value.home)
    }

    @Test
    fun `home and work can be set and cleared independently`() {
        val repository = SavedPlacesRepository(InMemoryStore())
        repository.setFavorite(FavoriteKind.HOME, place(1))
        repository.setFavorite(FavoriteKind.WORK, place(2))
        assertEquals(place(1), repository.state.value.home)
        assertEquals(place(2), repository.state.value.work)

        repository.clearFavorite(FavoriteKind.HOME)
        assertNull(repository.state.value.home)
        assertEquals(place(2), repository.state.value.work)
    }

    @Test
    fun `every change is written through and survives a restart`() {
        val store = InMemoryStore()
        val repository = SavedPlacesRepository(store)
        repository.setFavorite(FavoriteKind.HOME, place(1))
        repository.addRecent(place(2, name = null, address = null))
        repository.addRecent(place(3))
        assertEquals(3, store.writes)

        val reloaded = SavedPlacesRepository(InMemoryStore(store.value))
        assertEquals(repository.state.value, reloaded.state.value)
        assertNull(reloaded.state.value.recents[1].name)
    }

    @Test
    fun `a corrupt store yields an empty state rather than a crash`() {
        assertEquals(SavedPlaces(), SavedPlacesRepository(InMemoryStore("{not json")).state.value)
        assertEquals(SavedPlaces(), SavedPlacesRepository(InMemoryStore("")).state.value)
    }

    @Test
    fun `entries without a coordinate are skipped when decoding`() {
        val json = """{"version":1,"home":{"name":"nowhere"},"recents":[{"lat":1.5,"lng":2.5},{"lat":"x","lng":1}]}"""
        val places = SavedPlacesCodec.decode(json)
        assertNull(places.home)
        assertEquals(listOf(Destination(GeographicCoordinate(lat = 1.5, lng = 2.5))), places.recents)
    }
}
