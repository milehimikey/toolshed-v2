---
schemaVersion: 1
pattern: state-change
swimlane: Shed Manager → Tools
status: draft
version: 1
---
# Slice: Schedule Tool Maintenance

## Intent
Send a tool that is in the shed for maintenance: sharpening, cleaning, tuning, or whatever work
it needs. The shed manager names the work, with any notes, from the Tool
Inventory screen; the system records who they are from sign-in. From that moment the
tool is off the Tool Catalog, so neighbors cannot borrow or hold it, and it shows on the Tool
Maintenance screen with the work it needs. Completing the work (Complete Tool Maintenance) puts
it back. A tool that is already in maintenance is not refused: running the command again edits
its scheduled work. The task list and notes sent replace the current ones, so the shed manager
can add, remove or reword a task, and Tool Maintenance Tasks Changed is recorded. If the list and
notes sent are the same as now, nothing is recorded.

Only a tool that is in the shed and free can be sent: not on loan, not set aside for a holder,
with no holds waiting and not retired. (A tool already in maintenance is edited, not sent.) A tool that is out or
promised to a neighbor is not taken away from them; the shed manager schedules the work after
the loan or the holds have run their course.

## Trigger & Actor
The shed manager, from the Tool Inventory screen. Sign-in, handled outside the shed's events,
supplies the shed-manager role and the shed manager's identity (their name), which the
handler records as scheduledBy, or as changedBy when the command edits a tool already in
maintenance; the shed manager does not type either. The screen that sends an edit pre-fills the
current tasks and notes from Tool Maintenance. Tool Inventory itself does not show whether a tool is in
maintenance and does not hide the action (it is only the trigger screen), so the command checks
every rule itself and a repeat is harmless.

## Command / Input
**Command:** `Schedule Tool Maintenance`

A ToolId is a UUID.

| Field | Type | Required | Rules / Validation |
|-------|------|----------|--------------------|
| toolId | ToolId | yes | The tool to send for maintenance. Must be a tool that has been added (INV-STM-3). A missing or malformed ID is bad input, rejected before any rule is checked. |
| tasks | Text[] | yes | The work the tool needs, in the order given. When the tool is already in maintenance this is the whole new list, replacing the current one. Each task has leading and trailing whitespace (any Unicode whitespace) removed; a task that is empty after that is dropped; a task that repeats an earlier one (compared ignoring case) is dropped. What is left must have 1 to 10 tasks (INV-STM-2), each at most 1000 characters (INV-STM-2). Free text: there is no fixed list of kinds. |
| notes | Text | no | Free-text notes about the work, such as what was noticed or what to watch for. When the tool is already in maintenance this replaces the current notes, so leaving it out clears them. Trimmed of any Unicode whitespace; empty after that means absent. At most 5000 characters when present (INV-STM-11). |

The command carries no scheduledBy and no changedBy. The handler assigns them (INV-STM-10); a
scheduledBy or changedBy sent with the request is ignored.

## Trigger
**Triggered by:** screen `Tool Inventory` @Shed Manager

## Event(s) Emitted
The command has two outcomes, one of which is recorded depending on whether the tool is already
in maintenance (INV-STM-8). Where the same list and notes are sent again, neither is recorded.

**Event:** `Tool Maintenance Scheduled` → context `Tools` (tool not in maintenance)
**Read by:** Tool Maintenance (a row for the tool is added) and Tool Catalog (the tool leaves the
catalog).

| Field | Type | Immutable Fact? | Source / Notes |
|-------|------|-----------------|----------------|
| toolId | ToolId | yes | From the command. |
| toolName | Text | yes | Set by the system: the name on the tool's Tool Added, so Tool Maintenance can show it without looking the tool up. |
| tasks | Text[] | yes | From the command, after trimming, dropping blanks and dropping repeats. Order kept. |
| notes | Text | yes | From the command, trimmed. Absent when none were given. |
| scheduledBy | Text | yes | Set by the system: the signed-in shed manager's name, as sign-in supplies it (INV-STM-10). Not from the command. |
| scheduledAt | Instant | yes | Set by the system: the UTC moment the event is recorded. |

Nothing else is recorded: not a reason, not a date for the work, not a cost. The event does not carry a "from" state; the tool was free, and that is all it says.

**Event:** `Tool Maintenance Tasks Changed` → context `Tools` (tool already in maintenance)
**Read by:** Tool Maintenance (the row's tasks and notes are replaced, and who and when are shown).

| Field | Type | Immutable Fact? | Source / Notes |
|-------|------|-----------------|----------------|
| toolId | ToolId | yes | From the command. |
| tasks | Text[] | yes | From the command, after trimming, dropping blanks and dropping repeats. Order kept. The whole new list, not a difference. |
| notes | Text | yes | From the command, trimmed. Absent when none were given, which means the notes are now cleared. |
| changedBy | Text | yes | Set by the system: the signed-in shed manager's name, as sign-in supplies it (INV-STM-10). Not from the command. |
| changedAt | Instant | yes | Set by the system: the UTC moment the event is recorded. |

The event carries no toolName and no earlier list; the row already has the name.

## Read Model / View
This slice produces no view of its own. Tool Maintenance Scheduled adds a row to Tool
Maintenance and removes the tool from Tool Catalog. Tool Maintenance Tasks Changed replaces the
tasks and notes on the tool's Tool Maintenance row and sets its last changed by and at; Tool
Catalog does not hear it. Each view is specified in its own doc. Both
are eventually consistent.

## Invariants / Business Rules
- **INV-STM-1:** Only the shed manager can schedule maintenance.
  - Neighbors do not. Sign-in, handled outside the shed's events, supplies the shed-manager
    role and identity; a caller without the role is refused with `not-shed-manager`.
  - A caller with no sign-in is refused with `not-signed-in`, before anything else is checked.
- **INV-STM-2:** A maintenance request names the work: at least one task, at most 10, each at
  most 1000 characters.
  - Tasks are trimmed, blank tasks are dropped and repeated tasks are dropped first; the limits
    apply to what is left. Nothing is cut short or rounded: too many or too long is refused.
  - Nothing left is refused with `tasks-missing`; more than 10 with `too-many-tasks`; a task
    over 1000 characters with `task-too-long`.
- **INV-STM-3:** The tool must exist.
  - A tool ID that was never added is refused with `tool-not-found`.
- **INV-STM-4:** A retired tool cannot be scheduled.
  - A tool is retired once Tool Retired has been recorded for it, or Tool Lost (which retires
    it). A retired tool that was in maintenance is retired, not in maintenance. Refused with
    `tool-retired`.
- **INV-STM-5:** A tool on loan cannot be scheduled.
  - A tool is on loan from Tool Checked Out until Tool Returned or Tool Lost for that loan.
    Refused with `tool-checked-out`.
- **INV-STM-6:** A tool set aside for a holder cannot be scheduled.
  - A tool is set aside from Hold Fulfilled until Hold Collected, Hold Cancelled or Hold
    Expired for that hold. Refused with `tool-set-aside`.
- **INV-STM-7:** A tool with holds waiting cannot be scheduled.
  - A hold is waiting from Hold Placed until it is fulfilled or ends (Hold Cancelled, Hold
    Expired, Hold Collected). This includes a tool just returned whose Hold Fulfilled is not
    yet recorded. Refused with `tool-has-holds`.
- **INV-STM-8:** A tool that is already in maintenance has its scheduled work edited, not
  scheduled again.
  - It is not refused. The tasks and notes in the request replace the current ones, after the
    same tidying and limits as a first schedule (INV-STM-2, INV-STM-11), and Tool Maintenance
    Tasks Changed is recorded, so tasks named are tracked and not dropped.
  - A tool is in maintenance from Tool Maintenance Scheduled until Tool Maintenance Completed.
    A second Tool Maintenance Scheduled is never recorded for a tool in maintenance.
  - The loan, set-aside and holds checks (INV-STM-5, 6, 7) are not applied to an edit; the
    tool's retirement is (INV-STM-4).
  - If the tidied tasks equal the current tasks (same tasks in the same order, compared exactly
    after trimming) and the trimmed notes equal the current notes (both absent counts as equal),
    the command succeeds and records nothing (INV-STM-12).
- **INV-STM-9:** A tool whose maintenance has been completed can be scheduled again.
  - Each Scheduled–Completed pair is a separate round. There is no limit on how many.
- **INV-STM-10:** The event records who sent the tool or changed its work, from sign-in.
  - `scheduledBy` and `changedBy` are not input and have no input rules. The handler assigns
    them from the signed-in shed manager's identity (the name sign-in supplies); a value sent
    with the request is ignored.
  - A request that is signed in as the shed manager but carries no identity name is refused
    with `not-signed-in`, since there is no one to record.
- **INV-STM-11:** Notes are optional and long.
  - Notes are trimmed; empty after that means none. At most 5000 characters; over that is
    refused with `notes-too-long`. Nothing is cut short.
- **INV-STM-12:** Sending the same tasks and notes again changes nothing.
  - For a tool already in maintenance whose current tasks and notes equal those sent (INV-STM-8),
    the command succeeds and records nothing: no Tool Maintenance Tasks Changed, so the last
    changed by and at do not move.

## Scenarios (Given / When / Then)
### Scenario: Happy path (INV-STM-5, INV-STM-6, INV-STM-7)
- **Given:**
  - the cordless drill is in the shed, with no loan open and no holds on it
- **When:**
  - the shed manager, signed in as Dana, runs Schedule Tool Maintenance for the drill with tasks
    "Sharpen bits" and "Clean chuck" and the notes "Chuck sticks when cold"
- **Then:**
  - Tool Maintenance Scheduled is recorded for the drill with its name "Cordless drill", the
    two tasks in that order, the notes, scheduledBy "Dana", and the time it was recorded
  - the drill appears on Tool Maintenance and leaves Tool Catalog

### Scenario: Tasks are tidied (INV-STM-2)
- **Given:**
  - the ladder is in the shed and free
- **When:**
  - the shed manager runs Schedule Tool Maintenance for the ladder with tasks "  Oil hinges ",
    "", "oil hinges" and "Check rungs"
- **Then:**
  - Tool Maintenance Scheduled is recorded with tasks "Oil hinges" and "Check rungs"

### Scenario: No task named (INV-STM-2)
- **Given:**
  - the ladder is in the shed and free
- **When:**
  - the shed manager runs Schedule Tool Maintenance for the ladder with tasks "   "
- **Then:**
  - refused with `tasks-missing`: "Say what work the tool needs." No event is recorded.

### Scenario: Notes left out (INV-STM-11)
- **Given:**
  - the ladder is in the shed and free
- **When:**
  - the shed manager runs Schedule Tool Maintenance for the ladder with the task "Oil hinges" and
    notes "   "
- **Then:**
  - Tool Maintenance Scheduled is recorded with no notes.

### Scenario: Notes too long (INV-STM-11)
- **Given:**
  - the ladder is in the shed and free
- **When:**
  - the shed manager runs Schedule Tool Maintenance for the ladder with the task "Oil hinges" and
    notes of 5001 characters
- **Then:**
  - refused with `notes-too-long`: "Notes can be at most 5000 characters." No event is recorded.

### Scenario: Long notes accepted (INV-STM-11)
- **Given:**
  - the ladder is in the shed and free
- **When:**
  - the shed manager runs Schedule Tool Maintenance for the ladder with the task "Oil hinges" and
    notes of exactly 5000 characters
- **Then:**
  - Tool Maintenance Scheduled is recorded with all the notes.

### Scenario: Who scheduled it comes from sign-in (INV-STM-10)
- **Given:**
  - the ladder is in the shed and free, and the shed manager is signed in as Dana
- **When:**
  - the shed manager runs Schedule Tool Maintenance for the ladder with the task "Oil hinges"
    and a request that also names a scheduledBy of "Someone Else"
- **Then:**
  - Tool Maintenance Scheduled is recorded with scheduledBy "Dana"; the value sent is ignored

### Scenario: Signed in without a name (INV-STM-10)
- **Given:**
  - the ladder is in the shed and free
- **When:**
  - a caller with the shed-manager role but no identity name runs Schedule Tool Maintenance for
    the ladder with the task "Oil hinges"
- **Then:**
  - refused with `not-signed-in`: "Please sign in." No event is recorded.

### Scenario: Eleven tasks (INV-STM-2)
- **Given:**
  - the ladder is in the shed and free
- **When:**
  - the shed manager runs Schedule Tool Maintenance for the ladder with 11 different tasks
- **Then:**
  - refused with `too-many-tasks`: "A tool can have at most 10 tasks." No event is recorded.

### Scenario: Ten tasks of 1000 characters (INV-STM-2)
- **Given:**
  - the ladder is in the shed and free
- **When:**
  - the shed manager runs Schedule Tool Maintenance for the ladder with 10 different tasks, each
    exactly 1000 characters
- **Then:**
  - Tool Maintenance Scheduled is recorded with all 10 tasks.

### Scenario: A task over 1000 characters (INV-STM-2)
- **Given:**
  - the ladder is in the shed and free
- **When:**
  - the shed manager runs Schedule Tool Maintenance for the ladder with one task of 1001
    characters
- **Then:**
  - refused with `task-too-long`: "A task can be at most 1000 characters." No event is recorded.

### Scenario: Unknown tool (INV-STM-3)
- **Given:**
  - no tool T9 has been added
- **When:**
  - the shed manager runs Schedule Tool Maintenance for T9 with the task "Sharpen"
- **Then:**
  - refused with `tool-not-found`: "There is no such tool." No event is recorded.

### Scenario: Retired tool (INV-STM-4)
- **Given:**
  - Tool Retired has been recorded for the hedge trimmer
- **When:**
  - the shed manager runs Schedule Tool Maintenance for the hedge trimmer with the task "Sharpen"
- **Then:**
  - refused with `tool-retired`: "That tool has been retired." No event is recorded.

### Scenario: Tool retired while in maintenance (INV-STM-4)
- **Given:**
  - Tool Maintenance Scheduled and then Tool Retired have been recorded for the hedge trimmer
- **When:**
  - the shed manager runs Schedule Tool Maintenance for the hedge trimmer with the task "Sharpen"
- **Then:**
  - refused with `tool-retired`: "That tool has been retired." No event is recorded.

### Scenario: Tool on loan (INV-STM-5)
- **Given:**
  - Ana has the hedge trimmer on loan, due 20 October
- **When:**
  - the shed manager runs Schedule Tool Maintenance for the hedge trimmer with the task "Sharpen"
- **Then:**
  - refused with `tool-checked-out`: "That tool is on loan." No event is recorded.

### Scenario: Tool returned, loan over (INV-STM-5)
- **Given:**
  - Ana's loan of the hedge trimmer ended with Tool Returned, and no hold is on it
- **When:**
  - the shed manager runs Schedule Tool Maintenance for the hedge trimmer with the task "Sharpen"
- **Then:**
  - Tool Maintenance Scheduled is recorded for the hedge trimmer.

### Scenario: Tool set aside (INV-STM-6)
- **Given:**
  - the cordless drill is set aside for Ben (Hold Fulfilled, not yet collected or ended)
- **When:**
  - the shed manager runs Schedule Tool Maintenance for the drill with the task "Clean chuck"
- **Then:**
  - refused with `tool-set-aside`: "That tool is set aside for a neighbor." No event is
    recorded.

### Scenario: Set-aside has ended (INV-STM-6)
- **Given:**
  - Ben's hold on the cordless drill was fulfilled and then Hold Expired was recorded, and no
    other hold is on it
- **When:**
  - the shed manager runs Schedule Tool Maintenance for the drill with the task "Clean chuck"
- **Then:**
  - Tool Maintenance Scheduled is recorded for the drill.

### Scenario: Hold waiting (INV-STM-7)
- **Given:**
  - the drill is in the shed and Cara's hold on it is waiting
- **When:**
  - the shed manager runs Schedule Tool Maintenance for the drill with the task "Clean chuck"
- **Then:**
  - refused with `tool-has-holds`: "That tool has holds waiting." No event is recorded.

### Scenario: Waiting hold cancelled (INV-STM-7)
- **Given:**
  - Cara's hold on the drill was placed and then Hold Cancelled was recorded, and no other hold
    is on it
- **When:**
  - the shed manager runs Schedule Tool Maintenance for the drill with the task "Clean chuck"
- **Then:**
  - Tool Maintenance Scheduled is recorded for the drill.

### Scenario: Scheduling again adds a task (INV-STM-8, INV-STM-10)
- **Given:**
  - Tool Maintenance Scheduled has been recorded for the ladder with the task "Oil hinges"
- **When:**
  - the shed manager, signed in as Dana, runs Schedule Tool Maintenance for the ladder with tasks
    "Oil hinges" and "Check rungs"
- **Then:**
  - Tool Maintenance Tasks Changed is recorded for the ladder with the two tasks in that order,
    changedBy "Dana" and the time it was recorded; no Tool Maintenance Scheduled is recorded
  - Tool Maintenance shows both tasks for the ladder

### Scenario: A task removed and another reworded (INV-STM-8)
- **Given:**
  - the ladder is in maintenance with tasks "Oil hinges", "Check rungs" and "Paint"
- **When:**
  - the shed manager runs Schedule Tool Maintenance for the ladder with tasks "Oil hinges" and
    "Check all rungs"
- **Then:**
  - Tool Maintenance Tasks Changed is recorded with exactly those two tasks

### Scenario: Notes changed and cleared (INV-STM-8, INV-STM-11)
- **Given:**
  - the ladder is in maintenance with the task "Oil hinges" and the notes "Squeaks"
- **When:**
  - the shed manager runs Schedule Tool Maintenance for the ladder with the task "Oil hinges" and
    no notes
- **Then:**
  - Tool Maintenance Tasks Changed is recorded with the task "Oil hinges" and no notes

### Scenario: Same tasks and notes again (INV-STM-12)
- **Given:**
  - the ladder is in maintenance with the task "Oil hinges" and the notes "Squeaks"
- **When:**
  - the shed manager runs Schedule Tool Maintenance for the ladder with the task " Oil hinges "
    and the notes "Squeaks"
- **Then:**
  - the command succeeds and no event is recorded

### Scenario: Same tasks in a different order (INV-STM-8)
- **Given:**
  - the ladder is in maintenance with tasks "Oil hinges" and "Check rungs"
- **When:**
  - the shed manager runs Schedule Tool Maintenance for the ladder with tasks "Check rungs" and
    "Oil hinges"
- **Then:**
  - Tool Maintenance Tasks Changed is recorded with the tasks in the new order

### Scenario: Editing with no task left (INV-STM-2, INV-STM-8)
- **Given:**
  - the ladder is in maintenance with the task "Oil hinges"
- **When:**
  - the shed manager runs Schedule Tool Maintenance for the ladder with tasks "  " and ""
- **Then:**
  - refused with `tasks-missing`: "Say what work the tool needs." No event is recorded, and the
    ladder keeps its tasks.

### Scenario: Editing a tool retired in maintenance (INV-STM-4, INV-STM-8)
- **Given:**
  - the hedge trimmer was in maintenance and Tool Retired has been recorded for it
- **When:**
  - the shed manager runs Schedule Tool Maintenance for the hedge trimmer with the task "Sharpen"
- **Then:**
  - refused with `tool-retired`: "That tool has been retired." No event is recorded.

### Scenario: Editing after completion starts a new round (INV-STM-8, INV-STM-9)
- **Given:**
  - Tool Maintenance Scheduled, Tool Maintenance Tasks Changed and then Tool Maintenance
    Completed have been recorded for the ladder
- **When:**
  - the shed manager runs Schedule Tool Maintenance for the ladder with the task "Check rungs"
- **Then:**
  - Tool Maintenance Scheduled is recorded, not Tool Maintenance Tasks Changed.

### Scenario: Scheduled again after completion (INV-STM-9)
- **Given:**
  - Tool Maintenance Scheduled and then Tool Maintenance Completed have been recorded for the
    ladder
- **When:**
  - the shed manager runs Schedule Tool Maintenance for the ladder with the task "Check rungs"
- **Then:**
  - Tool Maintenance Scheduled is recorded for the ladder with the task "Check rungs".

### Scenario: Someone other than the shed manager (INV-STM-1)
- **Given:**
  - the ladder is in the shed and free
- **When:**
  - a caller signed in without the shed-manager role runs Schedule Tool Maintenance for the
    ladder
- **Then:**
  - refused with `not-shed-manager`: "Only the shed manager can schedule tool maintenance." No
    event is recorded.

### Scenario: No sign-in (INV-STM-1)
- **Given:**
  - a caller with no sign-in
- **When:**
  - they run Schedule Tool Maintenance for the ladder
- **Then:**
  - refused with `not-signed-in`: "Please sign in." No event is recorded.

## Alternate & Error Flows
Every refusal records nothing and returns one reason: the stable name, and the sentence shown to
the shed manager on the Tool Inventory screen. The name is the contract; the sentence is the
required wording of the message.

The checks run in this order, and the first that fails is the reason returned:
1. `not-signed-in`, then `not-shed-manager` (INV-STM-1).
2. The tasks: `tasks-missing`, then `too-many-tasks`, then `task-too-long` (INV-STM-2).
3. `notes-too-long` (INV-STM-11).
4. `tool-not-found` (INV-STM-3).
5. `tool-retired` (INV-STM-4).
6. A tool already in maintenance is edited (INV-STM-8) and goes no further: unchanged records
   nothing (INV-STM-12), otherwise Tool Maintenance Tasks Changed is recorded.
7. For a tool not in maintenance: `tool-checked-out` (INV-STM-5), then `tool-set-aside`
   (INV-STM-6), then `tool-has-holds` (INV-STM-7); if none fails, Tool Maintenance Scheduled is
   recorded.

| Reason | Sentence | Rule |
|--------|----------|------|
| `not-signed-in` | Please sign in. | INV-STM-1, INV-STM-10 |
| `not-shed-manager` | Only the shed manager can schedule tool maintenance. | INV-STM-1 |
| `tasks-missing` | Say what work the tool needs. | INV-STM-2 |
| `too-many-tasks` | A tool can have at most 10 tasks. | INV-STM-2 |
| `task-too-long` | A task can be at most 1000 characters. | INV-STM-2 |
| `notes-too-long` | Notes can be at most 5000 characters. | INV-STM-11 |
| `tool-not-found` | There is no such tool. | INV-STM-3 |
| `tool-retired` | That tool has been retired. | INV-STM-4 |
| `tool-checked-out` | That tool is on loan. | INV-STM-5 |
| `tool-set-aside` | That tool is set aside for a neighbor. | INV-STM-6 |
| `tool-has-holds` | That tool has holds waiting. | INV-STM-7 |

This slice never refuses with `tool-in-maintenance`; other slices do (see Dependencies).
`not-signed-in`, `not-shed-manager`, `tool-not-found`, `tool-retired`, `tool-checked-out` and
`tool-set-aside` mean the same conditions as in the other slices that use them. A missing or
malformed tool ID (not a UUID) is bad input, rejected before any rule is checked.

Repeats: scheduling a tool that is already in maintenance edits it (INV-STM-8). A double submit
of the same request succeeds and records nothing more, since the second finds the same tasks and
notes (INV-STM-12). Tool Maintenance Scheduled is recorded once per round.

Requests at the same moment: Schedule Tool Maintenance is decided against the tool's events as
already recorded, one at a time per tool. If a checkout, a hold or a fulfilment for the tool is
decided just before, it stands and the schedule is refused. If an earlier edit or schedule is
decided first, this request is compared with the list and notes it left. If it is decided just after, it is
refused by its own slice because the tool is in maintenance. If Retire Tool is decided first,
this request is refused with `tool-retired`; if this request is decided first, the retirement
then ends the maintenance (Tool Maintenance drops the row on Tool Retired).

There are no external calls in this slice, and no email is sent.

## Non-Functional Requirements
- **Security / authz:** only the shed manager may schedule maintenance (INV-STM-1). Sign-in,
  handled outside the shed's events, supplies the shed-manager role and the identity the handler
  records as scheduledBy or changedBy (INV-STM-10). The constitution (Amendments 6 and 9) names only the role
  and the neighbor id today; for this to hold it must also say that sign-in supplies the shed
  manager's name with the role.
- **PII & compliance:** Tool Maintenance Scheduled holds the tool, its name, free-text tasks and
  notes, the name of the shed manager who scheduled it (taken from sign-in), and a time; Tool
  Maintenance Tasks Changed holds the tool, the replacing tasks and notes, the name of the shed
  manager who changed them (from sign-in) and a time. Those names are personal data, kept with
  the event history and not erased, like the neighbor names in the loan history (constitution
  Amendment 6). Tasks and notes describe work on a tool, not people.
- **Performance / SLA:** none.

## Dependencies & Read Models Affected
- **Upstream events this slice relies on**, to apply its rules, and the fields it reads from
  each:
  - Tool Added (Add Tool): toolId, name.
  - Tool Retired (Retire Tool): toolId.
  - Tool Lost (Record Tool Lost): loanId, toolId (a lost tool is retired by that).
  - Tool Checked Out (Check Out Tool): loanId, toolId. Tool Returned (Return Tool): loanId.
  - Hold Placed (Place Hold): holdId, toolId. Hold Fulfilled (Fulfill Hold): holdId, toolId.
    Hold Cancelled, Hold Expired and Hold Collected: holdId (Hold Collected also carries
    toolId). Hold events other than Hold Placed and Hold Fulfilled carry no tool, so the
    decider follows each hold to its tool through those two.
  - Tool Maintenance Scheduled (this slice): toolId, tasks, notes. Tool Maintenance Tasks
    Changed (this slice): toolId, tasks, notes. The decider keeps the latest tasks and notes
    to tell an unchanged request from an edit. Tool Maintenance Completed (Complete Tool
    Maintenance): toolId.
- **Downstream read models / slices affected:** Tool Maintenance and Tool Catalog (Tool
  Maintenance Scheduled); Tool Maintenance (Tool Maintenance Tasks Changed). Tool Inventory is
  not affected. The screen that sends an edit reads the current tasks and notes from Tool
  Maintenance to pre-fill them.
- **Rules other slices must take on, outside this doc:** Check Out Tool and Place Hold must
  refuse a tool that is in maintenance, since the catalog hides it but the commands can still
  be called. Retire Tool allows a tool in maintenance and thereby ends the maintenance. Their
  docs are revised separately.

## Open Questions
None.
