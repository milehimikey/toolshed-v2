package com.toolshed.toolinventory

import com.toolshed.web.Caller
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** GET /api/tools/inventory: the shed manager's Tool Inventory screen alone (INV-TINV-11, INV-TINV-12). */
@RestController
class ToolInventoryController(
    private val queries: QueryGateway,
) {
    @GetMapping("/api/tools/inventory")
    fun list(caller: Caller): List<ToolInventoryResponse> {
        caller.requireToolInventoryReader()
        return queries
            .query(ListToolInventory, ToolInventory::class.java)
            .join()
            .tools
            .map { it.toResponse() }
    }
}

data class ToolInventoryResponse(
    val toolId: UUID,
    val name: String,
    val description: String?,
    val defaultCheckoutPeriodDays: Int,
    val addedAt: Instant,
    val onLoan: Boolean,
    val loanId: UUID?,
    val borrowerId: UUID?,
    val borrowerName: String?,
    val dueDate: LocalDate?,
    val retired: Boolean,
    val canRetire: Boolean,
    val overdue: Boolean,
    val canMarkLost: Boolean,
)

private fun ToolInventoryRow.toResponse() =
    ToolInventoryResponse(
        toolId = toolId.value,
        name = name,
        description = description,
        defaultCheckoutPeriodDays = defaultCheckoutPeriodDays,
        addedAt = addedAt,
        onLoan = onLoan,
        loanId = loanId?.value,
        borrowerId = borrowerId?.value,
        borrowerName = borrowerName,
        dueDate = dueDate,
        retired = retired,
        canRetire = canRetire,
        overdue = overdue,
        canMarkLost = canMarkLost,
    )
