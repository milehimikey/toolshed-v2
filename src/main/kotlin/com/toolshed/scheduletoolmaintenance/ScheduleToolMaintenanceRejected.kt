package com.toolshed.scheduletoolmaintenance

import com.toolshed.axon.CommandRejected

/** A refused Schedule Tool Maintenance. [invariant] is the doc's INV id, [reason] the stable name, the message the doc sentence. */
class ScheduleToolMaintenanceRejected(
    invariant: String,
    reason: String,
    sentence: String,
) : CommandRejected(invariant, reason, sentence)
