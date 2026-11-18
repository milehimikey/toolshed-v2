package com.toolshed.toolinventory

import com.toolshed.web.Caller
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** GET /api/tools/inventory: the shed manager's Tool Inventory screen alone (INV-TINV-11, INV-TINV-12). */
@RestController
class ToolInventoryController(
    private val queryGateway: QueryGateway,
) {
    @GetMapping("/api/tools/inventory")
    fun list(caller: Caller): ToolInventory {
        caller.requireToolInventoryReader()
        return queryGateway.query(ListToolInventory, ToolInventory::class.java).join()
    }
}
