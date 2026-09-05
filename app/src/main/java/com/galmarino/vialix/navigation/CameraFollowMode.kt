package com.galmarino.vialix.navigation

/**
 * What the "my location" button does with the camera while browsing the map. Tapping the button
 * walks the cycle FREE -> FOLLOW -> HEADING -> FREE; any manual pan drops back to [FREE].
 *
 * Pure so the cycle can be unit-tested; the mapping onto Ferrostar's camera modes lives in the UI
 * layer (`ui/MapControls.kt`).
 */
enum class CameraFollowMode {
    /** The camera stays where the user left it. */
    FREE,

    /** The camera keeps the user centred, north up. */
    FOLLOW,

    /** The camera keeps the user centred and rotates with the direction of travel. */
    HEADING;

    /** The mode a tap on the button switches to. */
    fun next(): CameraFollowMode =
        when (this) {
            FREE -> FOLLOW
            FOLLOW -> HEADING
            HEADING -> FREE
        }

    val isFollowing: Boolean
        get() = this != FREE
}
