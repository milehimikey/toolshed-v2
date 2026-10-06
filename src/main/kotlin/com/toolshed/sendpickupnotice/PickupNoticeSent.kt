package com.toolshed.sendpickupnotice

import com.toolshed.axon.HoldId
import java.time.Instant
import java.time.LocalDate

data class PickupNoticeSent(
    val holdId: HoldId,
    val sentTo: String,
    val holderName: String,
    val toolName: String,
    val collectByDate: LocalDate,
    val sentAt: Instant,
)
