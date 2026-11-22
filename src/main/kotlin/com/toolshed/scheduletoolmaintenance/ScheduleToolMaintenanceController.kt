package com.toolshed.scheduletoolmaintenance

import com.toolshed.axon.ToolId
import com.toolshed.web.Caller
import com.toolshed.web.CommandDispatcher
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/** Body for PUT /api/tools/{toolId}/maintenance. A scheduledBy or changedBy sent here is not a field, so it is ignored. */
data class ScheduleToolMaintenanceRequest(
    val tasks: List<String>?,
    val notes: String?,
)

/**
 * Maintenance is a singleton state of the tool (Amendment 10), so it is a PUT on the tool's maintenance; a repeat of
 * the same work answers 204 and records nothing. The shed manager's name comes from the sign-in header, never the body.
 * Checks run: not signed in, then a malformed id (bad input, 400), then the shed-manager role (precedent api-shape/1).
 */
@RestController
@RequestMapping("/api/tools/{toolId}/maintenance")
class ScheduleToolMaintenanceController(
    private val dispatcher: CommandDispatcher,
) {
    @PutMapping
    fun schedule(
        caller: Caller,
        @PathVariable toolId: String,
        @RequestHeader(MANAGER_NAME_HEADER, required = false) managerName: String?,
        @RequestBody body: ScheduleToolMaintenanceRequest,
    ): ResponseEntity<Void> {
        val name = managerName?.trim()?.takeIf { it.isNotEmpty() }
        val id = parseToolId(caller, name, toolId)
        caller.requireMaintenanceScheduler(name)
        dispatcher.send(ScheduleToolMaintenance(id, body.tasks, body.notes, name), Any::class.java)
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build()
    }

    /** Not-signed-in outranks bad input, so an unsigned caller is told to sign in even with a malformed id. */
    private fun parseToolId(
        caller: Caller,
        name: String?,
        raw: String,
    ): ToolId {
        if (!caller.isSignedIn || (caller.isShedManager && name == null)) caller.requireMaintenanceScheduler(name)
        val uuid =
            try {
                UUID.fromString(raw.also { require(it.length == 36) })
            } catch (e: IllegalArgumentException) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "toolId must be a UUID")
            }
        return ToolId(uuid)
    }

    companion object {
        const val MANAGER_NAME_HEADER = "X-Toolshed-Manager-Name"
    }
}
