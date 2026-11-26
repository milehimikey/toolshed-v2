package com.toolshed.toolmaintenance

import com.toolshed.web.Caller
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** GET /api/tools/maintenance: the shed manager's Tool Maintenance screen alone (INV-TMNT-9, INV-TMNT-10). */
@RestController
class ToolMaintenanceController(
    private val queryGateway: QueryGateway,
) {
    @GetMapping("/api/tools/maintenance")
    fun list(caller: Caller): ToolMaintenance {
        caller.requireToolMaintenanceReader()
        return queryGateway.query(ListToolsInMaintenance, ToolMaintenance::class.java).join()
    }
}
