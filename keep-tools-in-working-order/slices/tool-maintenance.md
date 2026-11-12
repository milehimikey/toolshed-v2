---
schemaVersion: 1
pattern: state-view
swimlane: Shed Manager → Tools
status: draft
version: 1
---
# Slice: Tool Maintenance

## Intent
Shows the shed manager every tool that is currently in maintenance and what work each one needs
(sharpening, cleaning, tuning, and so on), on the Tool Maintenance screen. It is the shed
manager's list of tools that need attention. From it the shed manager completes the maintenance
(Complete Tool Maintenance slice), which puts the tool back on the Tool Catalog.

## Trigger & Actor
- **Actor:** the shed manager, opening the Tool Maintenance screen.
- **What it shows:** one entry per tool in maintenance, built from the events listed under Read
  Model / View. A tool is in maintenance from Tool Maintenance Scheduled until Tool Maintenance
  Completed or Tool Retired. Nothing on this screen records anything by itself; its action
  issues a command owned by another slice:
  - **Complete Maintenance** (Complete Tool Maintenance slice) — offered on every entry.

## Read Model / View
- **View:** `Tool Maintenance` built from events: "Tool Maintenance Scheduled" (creates the
  entry), "Tool Maintenance Completed" (removes it), "Tool Retired" (removes it)
- **Consumed by:** the Tool Maintenance screen (Shed Manager)
- **Freshness / consistency expectation:** eventual. An entry appears or goes away shortly after
  the event is recorded (seconds, not minutes). Nothing on this screen depends on it being
  instant, because Complete Tool Maintenance checks its own rules when it runs.

One row per tool in maintenance. ToolId is a UUID. An Instant is a UTC instant set when the
event was recorded. No field is optional.

| Field | Type | Source / Notes |
|-------|------|-----------------|
| toolId | ToolId | Tool Maintenance Scheduled.toolId. |
| toolName | Text | Tool Maintenance Scheduled.toolName: the tool's name as it was when maintenance was scheduled. Need not be unique. |
| tasks | Text[] | Tool Maintenance Scheduled.tasks: the work the tool needs, in the order recorded. One to 10 entries. |
| scheduledAt | Instant | Tool Maintenance Scheduled.scheduledAt. |

There is no status or flag on a row: a tool is in this view or it is not. When maintenance
ends, its row is removed, not kept with an ended marker. The view keeps no history; a tool sent
for maintenance a second time gets a fresh row.

**Queries**
- **List tools in maintenance.** Takes nothing. Returns every tool in maintenance, each with
  toolId, toolName, tasks and scheduledAt, ordered by toolName ignoring case, then by
  scheduledAt, then by toolId (INV-TMNT-8). There is no paging. With no tool in maintenance it
  returns an empty list, not a refusal.

## Invariants / Business Rules
- **INV-TMNT-1:** A tool sent for maintenance appears in the view exactly once, with the work it
  needs.
  - One entry per tool in maintenance, created by Tool Maintenance Scheduled. Nothing else
    creates an entry.
  - The entry shows the tool's name, its tasks in the order recorded, and when maintenance was
    scheduled.
  - Tools may share a name; each is its own entry.
- **INV-TMNT-2:** Completing maintenance removes the tool from the view.
  - Tool Maintenance Completed removes that tool's entry. Other entries are unchanged.
- **INV-TMNT-3:** Retiring a tool in maintenance removes it from the view.
  - Tool Retired removes that tool's entry, so a tool retired beyond repair no longer shows as
    needing attention.
  - A Tool Retired for a tool that has no entry changes nothing.
- **INV-TMNT-4:** A tool sent for maintenance again, after its earlier maintenance ended, gets a
  new entry from the new event.
  - The new entry carries the new tasks and the new scheduledAt. Nothing from the earlier
    maintenance is kept.
- **INV-TMNT-5:** Only Tool Maintenance Scheduled, Tool Maintenance Completed and Tool Retired
  change the view.
  - Tool Added, loans, holds, Tool Lost and every other event leave it alone. A tool in
    maintenance is never on loan and has no holds waiting, so no loan or hold event, and no Tool
    Lost, can arise for it while it has an entry.
- **INV-TMNT-6:** Applying the same event twice leaves the view as if it had been applied once.
  - A repeated Tool Maintenance Scheduled for a tool that already has an entry leaves that
    entry as it was.
  - A repeated Tool Maintenance Completed or Tool Retired for a tool with no entry changes
    nothing.
- **INV-TMNT-7:** Each entry shows the tasks as the command recorded them, with nothing added or
  dropped by the view.
  - Trimming, blank removal and repeat removal happen in Schedule Tool Maintenance, before the
    event is recorded; the view shows the event's tasks as they are.
- **INV-TMNT-8:** Entries are listed by tool name, ignoring case, then by when maintenance was
  scheduled, then by tool ID.
  - The same name-first order as every list of tools in the shed. The tool ID tie-break makes the
    order the same on every read.
- **INV-TMNT-9:** A request with no sign-in is refused before anything else is checked.
- **INV-TMNT-10:** Only the shed manager can read the Tool Maintenance view: a caller signed in
  without the shed-manager role is refused.
  - Neighbors see tools on the Tool Catalog, where a tool in maintenance does not appear.

## Scenarios (Given / When / Then)
- **Tool sent for maintenance (INV-TMNT-1)**
  - **Given:** the view lists no tools
  - **When:** Tool Maintenance Scheduled is recorded for the hedge trimmer, with the tasks
    "sharpen blades" and "oil hinges", at 09:00 on 3 November
  - **Then:** the view lists the hedge trimmer with those two tasks, in that order, scheduled at
    09:00 on 3 November.
- **Two tools in maintenance (INV-TMNT-1)**
  - **Given:** the view lists the hedge trimmer
  - **When:** Tool Maintenance Scheduled is recorded for the ladder, with the task "replace
    rung"
  - **Then:** the view lists both tools, each with its own tasks.
- **Two tools with the same name (INV-TMNT-1)**
  - **Given:** the view lists one tool named "Cordless drill", scheduled at 09:00
  - **When:** Tool Maintenance Scheduled is recorded for a second tool named "Cordless drill",
    with its own tool ID
  - **Then:** the view lists both drills as separate entries.
- **Maintenance completed (INV-TMNT-2)**
  - **Given:** the view lists the hedge trimmer and the ladder
  - **When:** Tool Maintenance Completed is recorded for the hedge trimmer
  - **Then:** the hedge trimmer drops off the view. The ladder is unchanged.
- **Last tool completed (INV-TMNT-2)**
  - **Given:** the view lists only the ladder
  - **When:** Tool Maintenance Completed is recorded for the ladder
  - **Then:** the view lists no tools.
- **Tool in maintenance retired (INV-TMNT-3)**
  - **Given:** the view lists the hedge trimmer and the ladder
  - **When:** Tool Retired is recorded for the hedge trimmer
  - **Then:** the hedge trimmer drops off the view. The ladder is unchanged.
- **Retirement of a tool not in maintenance (INV-TMNT-3, INV-TMNT-5)**
  - **Given:** the view lists the ladder
  - **When:** Tool Retired is recorded for the cordless drill, which is not in maintenance
  - **Then:** the view still lists only the ladder.
- **Sent for maintenance a second time (INV-TMNT-4)**
  - **Given:** the hedge trimmer was sent for "sharpen blades", completed, and has dropped off
    the view
  - **When:** Tool Maintenance Scheduled is recorded for the hedge trimmer again, with the task
    "replace cord", at a later time
  - **Then:** the view lists the hedge trimmer once, with only "replace cord" and the later
    scheduledAt.
- **Events the view doesn't hear (INV-TMNT-5)**
  - **Given:** the view lists the hedge trimmer
  - **When:** Tool Added is recorded for a new cordless drill, and a Hold Placed for the ladder
  - **Then:** the view still lists only the hedge trimmer.
- **Same event applied twice (INV-TMNT-6)**
  - **Given:** the view lists the hedge trimmer once
  - **When:** the same Tool Maintenance Scheduled for the hedge trimmer is applied again
  - **Then:** the view still lists the hedge trimmer once, with the same tasks and scheduledAt.
- **Completion applied twice (INV-TMNT-6)**
  - **Given:** the hedge trimmer has dropped off the view after Tool Maintenance Completed
  - **When:** the same Tool Maintenance Completed is applied again
  - **Then:** the view is unchanged.
- **Tasks shown as recorded (INV-TMNT-7)**
  - **Given:** the view lists no tools
  - **When:** Tool Maintenance Scheduled is recorded with the tasks "clean", "tune", "clean
    filter" in that order
  - **Then:** the entry shows exactly those three tasks in that order.
- **Listing order (INV-TMNT-8)**
  - **Given:** the view lists a ladder scheduled at 08:00, a cordless drill scheduled at 10:00,
    and a second cordless drill scheduled at 09:00
  - **When:** the shed manager lists the tools in maintenance
  - **Then:** the tools come back as: the drill scheduled at 09:00, the drill scheduled at
    10:00, then the ladder.
- **Listing with nothing in maintenance (INV-TMNT-8)**
  - **Given:** the view lists no tools
  - **When:** the shed manager lists the tools in maintenance
  - **Then:** an empty list comes back. It is not a refusal.
- **Not signed in (INV-TMNT-9)**
  - **Given:** the view lists the hedge trimmer
  - **When:** a caller with no sign-in asks for the tools in maintenance
  - **Then:** refused with `not-signed-in`: "Please sign in." Nothing is returned.
- **Asked by a neighbor (INV-TMNT-10)**
  - **Given:** a neighbor is signed in, without the shed-manager role
  - **When:** the tools in maintenance are asked for
  - **Then:** refused with `not-shed-manager`: "Only the shed manager can see the tools in
    maintenance." Nothing is returned.

## Alternate & Error Flows
Reading this view is guarded like a command. A refused read returns nothing and one reason: the
stable name, and the sentence shown on the screen. The checks run in this order:
1. `not-signed-in` (INV-TMNT-9).
2. `not-shed-manager` (INV-TMNT-10).

| Reason | Sentence | Rule |
|--------|----------|------|
| `not-signed-in` | Please sign in. | INV-TMNT-9 |
| `not-shed-manager` | Only the shed manager can see the tools in maintenance. | INV-TMNT-10 |

- **Repeated events:** every event is applied idempotently (INV-TMNT-6). An event applied a
  second time leaves the entry as it was after the first.
- **Event for a tool with no entry:** a Tool Maintenance Completed or Tool Retired for a tool
  that is not in the view can only be a replay, or a retirement of a tool that was never in
  maintenance. It changes nothing.
- **Stale actions:** an action offered on the screen can be out of date by the time it is used
  (for example, Complete Maintenance on a tool that has just been retired). The command it
  issues checks its own rules and refuses with its own reason; this view raises no errors of its
  own.

## Non-Functional Requirements
- **Security / authz:** the Tool Maintenance screen is the shed manager's; neighbors see tools on
  the Tool Catalog instead. Sign-in, handled outside the model, supplies the shed-manager role; a
  caller without it is refused `not-shed-manager` (INV-TMNT-10), and one with no sign-in
  `not-signed-in` (INV-TMNT-9).
- **PII & compliance:** none. The view shows tool names and maintenance tasks only, never a
  neighbor's name or email.
- **Performance / SLA:** none beyond the freshness above.

## Dependencies & Read Models Affected
- **Upstream events this slice relies on**, and the fields it reads from each:
  - Tool Maintenance Scheduled (Schedule Tool Maintenance): toolId, toolName, tasks, scheduledAt.
  - Tool Maintenance Completed (Complete Tool Maintenance): toolId.
  - Tool Retired (Retire Tool): toolId.
  - The two maintenance events are specified in slices shaped alongside this one
    (slices/schedule-tool-maintenance.md, slices/complete-tool-maintenance.md); the keys above
    are this view's reading of the model, and each of those slices is checked against them.
- **Downstream read models / slices affected:** none. The screen it feeds issues Complete Tool
  Maintenance.
- **Related rules owned elsewhere:** the Tool Catalog hides a tool in maintenance and shows it
  again on completion (the Tool Catalog continuation positions "maintenance scheduled" and
  "maintenance completed"). Check Out Tool and Place Hold must refuse a tool in maintenance;
  that rule belongs to their own docs and is revised there. Retire Tool allows retiring a tool
  in maintenance, which ends the maintenance (INV-TMNT-3).

## Open Questions
None.
