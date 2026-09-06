// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix

import android.app.Application
import uniffi.ferrostar.createFerrostarLogger

class NavApplication : Application() {

    /** Application-scoped object graph. Created once; survives activity recreation. */
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        // Routes Rust-side log output from the navigation core to logcat.
        createFerrostarLogger()
        graph = AppGraph(this)
    }
}
