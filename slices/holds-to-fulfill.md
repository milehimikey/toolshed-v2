---
schemaVersion: 1
pattern: state-view
swimlane: System → Holds
status: draft
implementedIn: https://github.com/milehimikey/toolshed-v2/commit/0ca7c86d5efde09122a8e2179472932795bd5764
conformedVersion: 1
conformedAt: 49d67795aabbe79704390bd08ef9925876074139
conformedOn: 2026-11-01
shippedVersion: 1
shippedOn: 2026-10-31
version: 2
---
# Slice: Holds To Fulfill

![Diagram](./holds-to-fulfill.svg)

## Intent
The hold fulfiller's to-do list: every tool that is back in the shed, not set aside for anyone,
with a hold waiting, and which hold is next in line for it. The hold fulfiller works through it,
so a returned tool is set aside for the neighbor at the front of its queue.

## Trigger & Actor
- **Actor:** the Hold Fulfiller, a system reaction in the Fulfill Hold slice. No person reads this
  list, and it has no screen.
- **What the fulfiller does with it:** for each tool on the list, it issues Fulfill Hold for the
  front hold, which records Hold Fulfilled (Fulfill Hold slice).

## Read Model / View
- **View:** `Holds To Fulfill` built from events: "Hold Placed", "Hold Collected", "Tool Returned",
  "Hold Cancelled", "Tool Checked Out", "Hold Fulfilled", "Hold Expired"
- **Consumed by:** the Hold Fulfiller (Fulfill Hold). Internal: no ui.
- **Freshness / consistency expectation:** eventual. A tool joins or leaves the list shortly after
  the event that causes it is recorded (seconds, not minutes). Fulfill Hold checks its own rules
  when it runs, so acting on a row a moment out of date sets nothing aside wrongly.

One row per tool on the list. ToolId, HoldId and NeighborId are UUIDs.

| Field | Type | Source / Notes |
|-------|------|-----------------|
| toolId | ToolId | Hold Placed.toolId. |
| holdId | HoldId | `Derived:` the front hold: the first hold on toolId, in the order Hold Placed was recorded, that is still waiting (INV-HTF-2). |
| neighborId | NeighborId | `Derived:` the front hold's holder, from its Hold Placed. |

**Bookkeeping.** For every tool that has holds, the view keeps: whether a loan of it is open (from
Tool Checked Out until Tool Returned or Tool Lost for that loan); whether a hold on it is set
aside (from Hold Fulfilled until that hold ends); and its waiting holds,
with holder, in the order Hold Placed was recorded. None of this is returned except the row.

**Waiting and ending.** A hold is waiting from Hold Placed until it is fulfilled or ends. A hold
ends when Hold Cancelled, Hold Expired or Hold Collected is recorded for it. Retiring the tool, recording it lost and closing the holder's account each end holds by recording Hold Cancelled for them (Retire Tool, Record Tool Lost, Close Account). This list hears only the Holds events for holds.

**Queries**
- **List tools to fulfill.** Takes nothing. Returns every row: toolId, and the front hold's holdId
  and neighborId. There is no paging. The order doesn't matter to the fulfiller, which acts on
  each tool separately.

## Invariants / Business Rules
The list has no screen; only the hold fulfiller reads it. The front holder is never passed over,
even when they are suspended or have an overdue loan and can't collect: the tool is set aside for
them, and if they don't collect it by the end of the collect-by date the hold expires (Fulfill
Hold, INV-FUL-8; Expire Hold). This view doesn't hear suspension or loans of other tools, so it
never skips anyone.

- **INV-HTF-1:** A tool is on the list exactly when all of these hold, tested against its current
  facts: no loan of it is open, no hold on it is set aside, and at least one hold on it is
  waiting.
  - A loan is open from Tool Checked Out until Tool Returned or Tool Lost for that loan. A hold is
    set aside from Hold Fulfilled until it ends; a set-aside whose collect-by date has passed still
    counts until Hold Expired is recorded for it.
  - The test reads current facts, not the order events arrived in. So a tool on loan is never on
    the list, however many holds wait on it, and whatever ended its last set-aside.
- **INV-HTF-2:** Each item names the hold at the front of the tool's queue: the earliest-placed
  hold still waiting.
  - "Earliest-placed" is the order Hold Placed was recorded for the tool, not placedAt, since
    two holds can share a timestamp.
- **INV-HTF-3:** When one of its holds is fulfilled, the tool leaves the list: the hold is now set
  aside (INV-HTF-1).
- **INV-HTF-4:** When a set-aside ends without the holder collecting it (the hold expires or is
  cancelled, including by the holder closing their account), the tool joins the list if INV-HTF-1 then
  holds: no loan open and another hold waiting.
  - With no hold waiting, the tool becomes available and stays off the list. If someone checked
    it out after the collect-by date and before Hold Expired, it stays off the list until that
    loan ends.
- **INV-HTF-5:** When the holder at the front of the queue checks the tool out before their hold
  is fulfilled, the tool leaves the list: a loan is now open, and that checkout ended their hold.
  - The next hold waits for the tool's return.
- **INV-HTF-6:** When a waiting hold ends, the next waiting hold becomes the front. If no hold is
  left waiting, the tool leaves the list (INV-HTF-1).
- **INV-HTF-7:** Retiring a tool, or recording it lost, ends all its holds: Retire Tool and Record
  Tool Lost record Hold Cancelled for each. With no hold left waiting, the tool leaves the list,
  or never joins it.
  - A tool is recorded lost only while it is on loan, so it is never on the list at that moment.
    Its holds are cancelled, and it never comes onto the list.
  - A tool retired while on loan never joins the list when it is returned: its holds were
    cancelled with it.
- **INV-HTF-9:** Applying the same event twice leaves the list as if it had been applied once.

## Scenarios (Given / When / Then)
- **Returned with a hold waiting (INV-HTF-1, INV-HTF-2)**
  - **Given:** Ana has the cordless drill (T1) on loan L1, and Ben's hold H1 on it was placed
    before Cleo's hold H2
  - **When:** Tool Returned is recorded for L1, tool T1
  - **Then:** T1 is on the list, with front hold H1, Ben.
- **Returned with no hold waiting (INV-HTF-1)**
  - **Given:** Ana has T1 on loan L1, with no holds on it
  - **When:** Tool Returned is recorded for L1
  - **Then:** T1 is not on the list.
- **Hold placed on a tool on loan (INV-HTF-1)**
  - **Given:** Ana has T1 on loan
  - **When:** Hold Placed is recorded for Ben's hold H1 on T1
  - **Then:** T1 is not on the list.
- **Holds placed in the same instant (INV-HTF-2)**
  - **Given:** Ana has T1 on loan
  - **When:** Hold Placed is recorded for H1 (Ben) and then H2 (Cleo), both with the same
    placedAt, and Tool Returned is then recorded for Ana's loan
  - **Then:** T1 is on the list with front hold H1: the order recorded decides.
- **Hold fulfilled (INV-HTF-3)**
  - **Given:** T1 is on the list, with front hold H1 and H2 behind it
  - **When:** Hold Fulfilled is recorded for H1
  - **Then:** T1 leaves the list.
- **Set-aside expires, another hold waiting (INV-HTF-4)**
  - **Given:** T1 is set aside for H1, and H2 is waiting
  - **When:** Hold Expired is recorded for H1
  - **Then:** T1 is back on the list, with front hold H2.
- **Set-aside expires, no hold waiting (INV-HTF-4)**
  - **Given:** T1 is set aside for H1, and no other hold is waiting
  - **When:** Hold Expired is recorded for H1
  - **Then:** T1 is not on the list.
- **Set-aside past its collect-by date, not yet expired (INV-HTF-1)**
  - **Given:** T1 is set aside for H1, collect by 17 October, and H2 is waiting
  - **When:** the list is read on 18 October, before Hold Expired is recorded for H1
  - **Then:** T1 is not on the list. It joins, with front hold H2, once Hold Expired lands.
- **Lapsed set-aside, next holder collects before expiry (INV-HTF-1, INV-HTF-4)**
  - **Given:** T1 was set aside for Ben's H1, his collect-by date has passed, Cleo's H2 and Dan's
    H3 are waiting, and Cleo checked T1 out (ending H2) before Hold Expired was recorded for H1
  - **When:** Hold Expired is recorded for H1
  - **Then:** T1 is not on the list: Cleo's loan is open. When Tool Returned is recorded for
    Cleo's loan, T1 joins the list with front hold H3.
- **Set-aside cancelled, another hold waiting (INV-HTF-4)**
  - **Given:** T1 is set aside for H1, and H2 is waiting
  - **When:** Hold Cancelled is recorded for H1
  - **Then:** T1 is back on the list, with front hold H2.
- **Set-aside holder closes their account (INV-HTF-4)**
  - **Given:** T1 is set aside for Ben's H1, and Cleo's H2 is waiting
  - **When:** Close Account records Hold Cancelled for H1, because Ben closed his account
  - **Then:** T1 is back on the list, with front hold H2.
- **Front hold cancelled before it is fulfilled (INV-HTF-6)**
  - **Given:** T1 is on the list, with front hold H1 and H2 behind it
  - **When:** Hold Cancelled is recorded for H1
  - **Then:** T1 stays on the list, with front hold H2.
- **Last waiting hold cancelled (INV-HTF-6)**
  - **Given:** T1 is on the list, with front hold H1 and no other hold
  - **When:** Hold Cancelled is recorded for H1
  - **Then:** T1 leaves the list.
- **Front holder collects before the hold is fulfilled (INV-HTF-5)**
  - **Given:** T1 is on the list, with front hold H1 (Ben) and H2 behind it
  - **When:** Tool Checked Out is recorded for T1 and Ben, with Hold Collected for H1
  - **Then:** T1 leaves the list (the loan is open, and H1 is gone from the queue). H2 waits for
    T1's next return.
- **Tool retired (INV-HTF-7)**
  - **Given:** T1 is on the list, with front hold H1
  - **When:** Retire Tool records Hold Cancelled for H1, with Tool Retired for T1
  - **Then:** T1 leaves the list.
- **Retired while on loan, then returned (INV-HTF-7)**
  - **Given:** Ana has T1 on loan with H1 waiting, and the shed manager retired T1, which
    recorded Hold Cancelled for H1
  - **When:** Tool Returned is recorded for Ana's loan
  - **Then:** T1 is not on the list.
- **Tool recorded lost while holds wait (INV-HTF-7)**
  - **Given:** Ana has T1 on loan L1, and H1 is waiting on it
  - **When:** Record Tool Lost records Hold Cancelled for H1, with Tool Lost for L1
  - **Then:** H1 is gone, and T1 never joins the list.
- **Same event applied twice (INV-HTF-9)**
  - **Given:** T1 is on the list once, with front hold H1
  - **When:** the same Tool Returned is applied again
  - **Then:** T1 is still on the list once, with front hold H1.

## Alternate & Error Flows
- **Repeated events:** every event is applied idempotently (INV-HTF-9). An event applied a second
  time leaves the list as it was after the first.
- **Events for a hold that has ended:** a later Hold Cancelled, Hold Expired or Hold Fulfilled for
  a hold that has already ended (a replay) changes nothing.
- **The fulfiller reads a tool twice before Hold Fulfilled lands:** the list may return the same
  tool on two reads in a row. Fulfilling a hold only once, and only while the tool is still free,
  is Fulfill Hold's rule (INV-FUL-3), enforced when it records Hold Fulfilled; this list raises no
  errors of its own.

## Non-Functional Requirements
- **Security / authz:** read only by the Hold Fulfiller. No person or screen reads it.
- **PII & compliance:** holds neighbor IDs only, no names or emails.
- **Performance / SLA:** none beyond the freshness above. How soon after a return the tool is set
  aside is Fulfill Hold's concern.

## Dependencies & Read Models Affected
- **Upstream events this slice relies on**, and the fields it reads from each:
  - Hold Placed (Place Hold): holdId, toolId, neighborId.
  - Hold Cancelled (Cancel Hold): holdId.
  - Hold Fulfilled (Fulfill Hold): holdId, toolId.
  - Hold Expired (Expire Hold): holdId.
  - Tool Checked Out (Check Out Tool): loanId, toolId (a loan is open).
  - Hold Collected (Check Out Tool): holdId (the hold leaves the queue).
  - Tool Returned (Return Tool): loanId, toolId.
  - Hold Cancelled is also recorded by Retire Tool, Record Tool Lost and Close Account, one per
    hold they end.
  - These match slices/place-hold.md, slices/check-out-tool.md and slices/return-tool.md, and
    Hold Fulfilled as Fulfill Hold is being written. The others are specified in slices not yet
    written in depth; each is checked against these keys when it is.
- **Downstream read models / slices affected:** Fulfill Hold, whose fulfiller works from this list
  and records Hold Fulfilled.

## Open Questions
None.
