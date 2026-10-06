package com.toolshed.sendpickupnotice

import com.toolshed.axon.HoldId
import org.axonframework.eventsourcing.annotation.EventTag
import java.time.Instant
import java.time.LocalDate

data class PickupNoticeSent(
    @field:EventTag(key = "holdId")
    val holdId: HoldId,
    val sentTo: String,
    val holderName: String,
    val toolName: String,
    val collectByDate: LocalDate,
    val sentAt: Instant,
)
