package com.toolshed.toolinventory

import com.toolshed.web.Caller
import com.toolshed.web.Refusal
import com.toolshed.web.requireShedManager
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The shed manager's Tool Inventory. Only the shed manager can read it (INV-TINV-11, INV-TINV-12), so the guard runs
 * before the query is sent.
 */
@RestController
@RequestMapping("/api/tools")
class ToolInventoryController(
    private val queryGateway: QueryGateway,
) {
    @GetMapping
    fun list(caller: Caller): ToolInventory {
        caller.requireShedManager(
            notSignedIn = Refusal("INV-TINV-11", "Please sign in."),
            notShedManager = Refusal("INV-TINV-12", "Only the shed manager can see the tool inventory."),
        )
        return queryGateway.query(ListToolInventory, ToolInventory::class.java).join()
    }
}
