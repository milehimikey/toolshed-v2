package com.toolshed.scheduletoolmaintenance

import java.time.Instant

/** The pure decision for Schedule Tool Maintenance: the command, the folded state, and the moment to record. */
object ScheduleToolMaintenanceDecider {
    private const val MAX_TASKS = 10
    private const val MAX_TASK_LENGTH = 1000
    private const val MAX_NOTES_LENGTH = 5000

    /**
     * Pure decision. Identity (role) is checked before the command is sent; here the checks run in the doc's order and
     * the first that fails is the reason returned. A tool in maintenance is edited (INV-STM-8), never refused for loan,
     * set-aside or holds, and an unchanged request records nothing (INV-STM-12).
     */
    fun decide(
        command: ScheduleToolMaintenance,
        state: MaintenanceState,
        at: Instant,
    ): List<Any> {
        val managerName = command.managerName?.trim()?.takeIf { it.isNotEmpty() }
        // INV-STM-10: a shed manager with no name has no one to record.
        if (managerName == null) refuse("INV-STM-10", "not-signed-in", "Please sign in.")

        val tasks = tidy(command.tasks.orEmpty())
        if (tasks.isEmpty()) refuse("INV-STM-2", "tasks-missing", "Say what work the tool needs.")
        if (tasks.size > MAX_TASKS) refuse("INV-STM-2", "too-many-tasks", "A tool can have at most 10 tasks.")
        if (tasks.any { it.codePointCount(0, it.length) > MAX_TASK_LENGTH }) {
            refuse("INV-STM-2", "task-too-long", "A task can be at most 1000 characters.")
        }
        val notes = command.notes?.trim()?.takeIf { it.isNotEmpty() }
        if (notes != null && notes.codePointCount(0, notes.length) > MAX_NOTES_LENGTH) {
            refuse("INV-STM-11", "notes-too-long", "Notes can be at most 5000 characters.")
        }

        val toolId = command.toolId
        if (!state.hasTool(toolId)) refuse("INV-STM-3", "tool-not-found", "There is no such tool.")
        if (state.isRetired(toolId)) refuse("INV-STM-4", "tool-retired", "That tool has been retired.")

        val current = state.currentWork(toolId)
        if (current != null) {
            // INV-STM-8 / INV-STM-12: an edit replaces the work; the same work records nothing.
            if (current.tasks == tasks && current.notes == notes) return emptyList()
            return listOf(ToolMaintenanceTasksChanged(toolId, tasks, notes, managerName, at))
        }

        if (state.isOnLoan(toolId)) refuse("INV-STM-5", "tool-checked-out", "That tool is on loan.")
        if (state.isSetAside(toolId)) refuse("INV-STM-6", "tool-set-aside", "That tool is set aside for a neighbor.")
        if (state.hasWaitingHolds(toolId)) refuse("INV-STM-7", "tool-has-holds", "That tool has holds waiting.")

        val toolName = requireNotNull(state.toolName(toolId)) { "a tool that exists has a name" }
        return listOf(ToolMaintenanceScheduled(toolId, toolName, tasks, notes, managerName, at))
    }

    /** Trim each task, drop the blank ones, drop repeats of an earlier one ignoring case; order kept (INV-STM-2). */
    private fun tidy(tasks: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        return tasks.map { it.trim() }.filter { it.isNotEmpty() && seen.add(it.lowercase()) }
    }

    private fun refuse(
        invariant: String,
        reason: String,
        sentence: String,
    ): Nothing = throw ScheduleToolMaintenanceRejected(invariant, reason, sentence)
}
