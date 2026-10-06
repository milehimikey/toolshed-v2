package com.toolshed.axon

import java.util.UUID

@JvmInline
value class ToolId(
    val value: UUID,
)

@JvmInline
value class NeighborId(
    val value: UUID,
)

@JvmInline
value class LoanId(
    val value: UUID,
)

@JvmInline
value class HoldId(
    val value: UUID,
)
