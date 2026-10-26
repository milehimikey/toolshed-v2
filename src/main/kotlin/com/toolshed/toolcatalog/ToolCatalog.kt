package com.toolshed.toolcatalog

import com.toolshed.axon.ToolId
import java.time.Instant
import java.time.LocalDate

/** The catalog's status values (field table: `status`). */
object ToolCatalogStatus {
    const val AVAILABLE = "available"
    const val CHECKED_OUT = "checked-out"
    const val RESERVED = "reserved"
    const val SET_ASIDE = "set-aside"
}

/** One row of the Tool Catalog view: the same for every neighbor (INV-TCAT-5), no borrower or holder identity. */
data class ToolCatalogRow(
    val toolId: ToolId,
    val name: String,
    val description: String?,
    val defaultCheckoutPeriodDays: Int,
    val addedAt: Instant,
    val status: String,
    val dueDate: LocalDate?,
    val holdsWaiting: Int,
    val canCheckOut: Boolean,
    val canPlaceHold: Boolean,
)

/** The query's answer: every listed tool, ordered for the screen (INV-TCAT-13). */
data class ToolCatalog(
    val tools: List<ToolCatalogRow>,
)

/** The query the Tool Catalog screen asks. Takes nothing; the same for every neighbor (INV-TCAT-5). */
object ListToolCatalog
