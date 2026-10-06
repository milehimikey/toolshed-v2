package com.toolshed.checkouttool

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import org.axonframework.modelling.annotation.TargetEntityId
import java.math.BigDecimal

/**
 * Check Out Tool. [loanPeriodDays] is optional; absent means the tool's default checkout period.
 * It is a [BigDecimal] so a fractional value reaches the decider and is refused there, never rounded.
 */
data class CheckOutTool(
    val loanId: LoanId,
    val toolId: ToolId,
    val neighborId: NeighborId,
    val loanPeriodDays: BigDecimal? = null,
) {
    @TargetEntityId
    private fun scope() = CheckoutScope(loanId, toolId, neighborId)
}

/** The consistency scope of one checkout: the loan, the tool and the borrower it touches. */
data class CheckoutScope(
    val loanId: LoanId,
    val toolId: ToolId,
    val neighborId: NeighborId,
)
