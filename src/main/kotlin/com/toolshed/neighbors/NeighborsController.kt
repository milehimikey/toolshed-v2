package com.toolshed.neighbors

import com.toolshed.web.Caller
import com.toolshed.web.Refusal
import com.toolshed.web.requireShedManager
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** GET /api/neighbors: the Neighbors screen. Only the shed manager may read it (INV-NBRS-9). */
@RestController
class NeighborsController(
    private val view: NeighborsView,
) {
    @GetMapping("/api/neighbors")
    fun list(caller: Caller): List<NeighborRow> {
        caller.requireShedManager(
            notSignedIn = Refusal("INV-NBRS-9", "Please sign in."),
            notShedManager = Refusal("INV-NBRS-9", "Only the shed manager can see this screen."),
        )
        return view.list()
    }
}
