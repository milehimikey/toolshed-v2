package com.toolshed.myloans

import com.toolshed.axon.NeighborId
import com.toolshed.web.Caller
import com.toolshed.web.Refusal
import com.toolshed.web.requireNeighbor
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** GET /api/neighbors/{neighborId}/loans: the signed-in neighbor's own My Loans. Another neighbor's id is refused (INV-MYL-15). */
@RestController
class MyLoansController(
    private val queries: QueryGateway,
) {
    @GetMapping("/api/neighbors/{neighborId}/loans")
    fun list(
        caller: Caller,
        @PathVariable neighborId: UUID,
    ): MyLoans {
        val neighbor = NeighborId(neighborId)
        caller.requireNeighbor(
            neighborId = neighbor,
            notSignedIn = Refusal("INV-MYL-14", "Please sign in."),
            mismatch = Refusal("INV-MYL-15", "You can only see your own loans."),
        )
        return queries.query(GetMyLoans(neighbor), MyLoans::class.java).join()
    }
}
