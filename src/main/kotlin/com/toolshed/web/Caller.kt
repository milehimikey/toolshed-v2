package com.toolshed.web

import com.toolshed.axon.NeighborId

/**
 * Who the sign-in layer says is calling. The app does not authenticate; it trusts the headers
 * read by [CallerArgumentResolver]. A caller with neither a neighbor nor the role is not signed in.
 */
data class Caller(
    val neighborId: NeighborId?,
    val isShedManager: Boolean,
) {
    val isSignedIn: Boolean
        get() = neighborId != null || isShedManager

    companion object {
        val ANONYMOUS = Caller(neighborId = null, isShedManager = false)
    }
}
