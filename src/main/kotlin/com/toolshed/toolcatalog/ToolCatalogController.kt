package com.toolshed.toolcatalog

import com.toolshed.web.Caller
import com.toolshed.web.Refusal
import com.toolshed.web.requireSignedIn
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * GET /api/tools/catalog: the neighbors' Tool Catalog screen. Any signed-in neighbor may read it, and it is the
 * same for all of them (INV-TCAT-5, INV-TCAT-14) — the shed manager's own tool list is the separate Tool Inventory
 * screen.
 */
@RestController
class ToolCatalogController(
    private val queryGateway: QueryGateway,
) {
    @GetMapping("/api/tools/catalog")
    fun list(caller: Caller): ToolCatalog {
        caller.requireSignedIn(notSignedIn = Refusal("INV-TCAT-14", "Please sign in."))
        return queryGateway.query(ListToolCatalog, ToolCatalog::class.java).join()
    }
}
