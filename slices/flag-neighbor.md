---
schemaVersion: 1
pattern: automation
swimlane: System → Neighbors
status: ready-to-implement
ratifiedBy: Mike Key
ratifiedOn: 2026-10-05
reviewedBy: Mike Key
reviewedOn: 2026-10-05
version: 1
---
# Slice: Flag Neighbor

![Diagram](./flag-neighbor.svg)

## Intent
When a neighbor's late returns reach the late-return threshold, record that they crossed it,
naming the late returns behind the flag, so the shed manager sees them on Flagged Neighbors and
decides whether to suspend them. The system flags; a person decides.

## Trigger & Actor
Internally triggered. The Late Return Flagger, a system processor in this slice, acts whenever a
neighbor appears on Neighbors To Flag, and issues Flag Neighbor for them with the loans that list
gives. It is driven by events, not a schedule: a neighbor joins that list when a late return is
recorded. No person flags a neighbor, and there is no screen. The neighbor
isn't told; a flag is a prompt for the shed manager, not a penalty.

## Command / Input
**Command:** `Flag Neighbor`

A NeighborId or LoanId is a UUID. One clock reading is taken when the command is handled. "Today"
is that moment's calendar date in the shed's local time zone, and the event's flaggedAt is that
same moment.

| Field | Type | Required | Rules / Validation |
|-------|------|----------|--------------------|
| neighborId | NeighborId | yes | The neighbor to flag. Must be a registered account (INV-FLG-10), not closed and not suspended (INV-FLG-5). |
| loanIds | LoanId[] | yes | The late returns behind the flag, by loan, as Neighbors To Flag gave them. Must be exactly the neighbor's counting late returns today (INV-FLG-4), and at least 3 of them (INV-FLG-2). Order doesn't matter. |

## Trigger
**Triggered by:** processor `Late Return Flagger`, also in this slice, reading Neighbors To Flag.

## Event(s) Emitted
**Event:** `Neighbor Flagged` → context `Neighbors`
**Read by:** Flagged Neighbors (the neighbor's entry, flag count and latest flag date, and the late
returns behind the flag) and Neighbors To Flag (the named late returns are used up).

| Field | Type | Immutable Fact? | Source / Notes |
|-------|------|-----------------|----------------|
| neighborId | NeighborId | yes | From the command. |
| loanIds | LoanId[] | yes | From the command: the late returns this flag uses up. At least 3. |
| flaggedAt | Instant | yes | The UTC moment the event is recorded, set by the system. Shown as the flag date: its calendar date in the shed's local time zone. |

The event never changes. It carries the loans, not copies of names or dates; readers find each
loan's tool and return date from its own events. A later flag records a new Neighbor Flagged.

## Read Model / View
This slice produces no view of its own. Its processor reads Neighbors To Flag
(slices/neighbors-to-flag.md). Neighbor Flagged feeds Flagged Neighbors
(slices/flagged-neighbors.md) and takes the named late returns off Neighbors To Flag. Both are
eventually consistent.

## Invariants / Business Rules
A **counting late return**, for a neighbor today, is a loan of theirs whose Tool Returned has late
yes, and which:
- counts on its return day and the next 89 days: returnedOn is today or one of the 89 days
  before, in the shed's local time;
- isn't named by an earlier Neighbor Flagged for them;
- was returned after their latest Neighbor Reinstated, if any;
- was not returned while they were suspended.

A tool recorded lost has no Tool Returned, so it never counts. Once flagged, the neighbor appears
on Flagged Neighbors and stays there until the shed manager acts; there is no dismiss action
(Flagged Neighbors owns how it shows). Neighbors are flagged by the system only, through the
processor above.

- **INV-FLG-2:** A neighbor is flagged when they have at least 3 counting late returns.
  - "At least 3" is tested on the neighbor's own counting set, worked out from their events when
    the command is handled, not on the loans the command names. Loans that don't count are never
    made up for by naming them.
- **INV-FLG-3:** Flagging never suspends anyone.
  - Flag Neighbor records only Neighbor Flagged. The shed manager decides whether to suspend a
    flagged neighbor (Suspend Neighbor).
- **INV-FLG-4:** A flag uses up the late returns that caused it.
  - Neighbor Flagged names, by loan, every counting late return the neighbor has when it is
    recorded, even if there are more than 3. Those loans never count again.
  - The neighbor is flagged again only after 3 new counting late returns.
- **INV-FLG-5:** A neighbor is flagged only while they are still due a flag.
  - A neighbor whose account is closed is never flagged, and nor is a suspended neighbor. A
    neighbor whose count was reset by reinstatement after they joined Neighbors To Flag is not
    flagged for those late returns.
- **INV-FLG-7:** A neighbor flagged again while an earlier flag is still waiting on Flagged
  Neighbors gets a second Neighbor Flagged.
  - An earlier flag never stops a new one. Flagged Neighbors keeps one entry for the neighbor,
    with the count and the latest date.
- **INV-FLG-9:** Flagging a neighbor again for exactly the loans an earlier Neighbor Flagged
  already names succeeds and records nothing.
- **INV-FLG-10:** The neighbor must be a registered account.

## Scenarios (Given / When / Then)
- **Happy path (INV-FLG-2, INV-FLG-3, INV-FLG-4)**
  - **Given:** Ana (N1) is active, with late returns of loans L1 on 1 September, L2 on 20
    September and L3 on 5 October, none named by an earlier flag. It is 5 October.
  - **When:** the Late Return Flagger issues Flag Neighbor for N1 with L1, L2 and L3
  - **Then:** Neighbor Flagged is recorded for N1, naming L1, L2 and L3, with the time it was
    recorded. Nothing else is recorded.
- **Four counting late returns (INV-FLG-4)**
  - **Given:** Ana has counting late returns L1, L2, L3 and L4, none flagged
  - **When:** Flag Neighbor is issued for N1 with L1, L2, L3 and L4
  - **Then:** Neighbor Flagged is recorded naming all four.
- **A loan left out (INV-FLG-4)**
  - **Given:** Ana has counting late returns L1, L2, L3 and L4
  - **When:** Flag Neighbor is issued for N1 with only L1, L2 and L3
  - **Then:** refused with `late-returns-changed`: "The late returns behind this flag have
    changed." No event is recorded.
- **Below the threshold (INV-FLG-2)**
  - **Given:** Ana has counting late returns L1 and L2 only
  - **When:** Flag Neighbor is issued for N1 with L1 and L2
  - **Then:** refused with `below-threshold`: "This neighbor has fewer than 3 late returns that
    count." No event is recorded.
- **A late return drops out of the window (INV-FLG-2)**
  - **Given:** Ana's late returns were on 1 June, 20 September and 5 October, none flagged
  - **When:** Flag Neighbor is issued for N1 on 5 October with all three loans
  - **Then:** refused with `below-threshold`: "This neighbor has fewer than 3 late returns that
    count." The 1 June return is more than 90 days back. No event is recorded.
- **Window boundary (INV-FLG-2)**
  - **Given:** Ana's late returns were on 8 July, 20 September and 5 October, none flagged
  - **When:** Flag Neighbor is issued for N1 on 5 October with all three loans
  - **Then:** Neighbor Flagged is recorded: 8 July is the 89th day before 5 October, so it still
    counts.
- **Flagged again on new late returns (INV-FLG-4, INV-FLG-7)**
  - **Given:** Ana was flagged on 5 October for L1, L2 and L3, and the shed manager hasn't acted.
    Since then she returned L5, L6 and L7 late, on 20 October, 1 November and 15 November.
  - **When:** Flag Neighbor is issued for N1 with L5, L6 and L7 on 15 November
  - **Then:** Neighbor Flagged is recorded again, naming L5, L6 and L7.
- **Used-up returns don't count again (INV-FLG-2, INV-FLG-4)**
  - **Given:** Ana was flagged for L1, L2 and L3, and returned L5 late since
  - **When:** Flag Neighbor is issued for N1 with L1, L2, L3 and L5
  - **Then:** refused with `below-threshold`: "This neighbor has fewer than 3 late returns that
    count." Only L5 counts. No event is recorded.
- **An old loan named alongside new ones (INV-FLG-4)**
  - **Given:** Ana was flagged for L1, L2 and L3, and has since returned L5, L6 and L7 late
  - **When:** Flag Neighbor is issued for N1 with L1, L5, L6 and L7
  - **Then:** refused with `late-returns-changed`: "The late returns behind this flag have
    changed." L1 was used up by the earlier flag. No event is recorded.
- **The same flag arrives twice (INV-FLG-9)**
  - **Given:** Neighbor Flagged was recorded for N1, naming L1, L2 and L3
  - **When:** Flag Neighbor for N1 with L1, L2 and L3 is issued again
  - **Then:** it succeeds and nothing new is recorded.
- **Suspended neighbor (INV-FLG-5)**
  - **Given:** Ana is suspended, with 3 late returns from before her suspension that were never
    flagged
  - **When:** Flag Neighbor is issued for N1
  - **Then:** refused with `neighbor-suspended`: "This neighbor is suspended." No event is recorded.
- **Count reset by reinstatement (INV-FLG-5)**
  - **Given:** Ana returned L1, L2 and L3 late, then was suspended and reinstated
  - **When:** Flag Neighbor is issued for N1 with L1, L2 and L3
  - **Then:** refused with `below-threshold`: "This neighbor has fewer than 3 late returns that
    count." Returns from before her reinstatement don't count. No event is recorded.
- **Closed account (INV-FLG-5)**
  - **Given:** Ana's account was closed after she reached 3 counting late returns
  - **When:** Flag Neighbor is issued for N1
  - **Then:** refused with `account-closed`: "This account is closed." No event is recorded.
- **Unknown neighbor (INV-FLG-10)**
  - **Given:** no account has neighbor ID N9
  - **When:** Flag Neighbor is issued for N9
  - **Then:** refused with `neighbor-not-found`: "There is no such neighbor." No event is
    recorded.

## Alternate & Error Flows
A refusal records nothing; the Late Return Flagger logs it with the neighbor ID and moves on. No
person sees these reasons. The name is the contract; the sentence is the log text.

The checks run in this order, and the first that fails is the reason returned:
1. `neighbor-not-found` (INV-FLG-10).
2. If a Neighbor Flagged for this neighbor already names exactly these loans, the command
   succeeds and records nothing (INV-FLG-9).
3. `account-closed`, then `neighbor-suspended` (INV-FLG-5).
4. `below-threshold` (INV-FLG-2).
5. `late-returns-changed`, if the loans given aren't exactly the neighbor's counting late returns
   (INV-FLG-4).

| Reason | Sentence | Rule |
|--------|----------|------|
| `neighbor-not-found` | There is no such neighbor. | INV-FLG-10 |
| `account-closed` | This account is closed. | INV-FLG-5 |
| `neighbor-suspended` | This neighbor is suspended. | INV-FLG-5 |
| `below-threshold` | This neighbor has fewer than 3 late returns that count. | INV-FLG-2 |
| `late-returns-changed` | The late returns behind this flag have changed. | INV-FLG-4 |

`neighbor-not-found`, `account-closed` and `neighbor-suspended` mean the same conditions as in the
neighbor commands; the sentences here fit a system log. A missing or malformed ID, or an empty
loan list, is bad input, rejected before any rule is checked.

Idempotency: a repeated Flag Neighbor with the same loans succeeds and records nothing
(INV-FLG-9). The flagger may act on the same neighbor twice before Neighbor Flagged lands; the
second command is then a repeat.

`late-returns-changed` means the list moved on between the flagger's read and the command, for
example a fourth late return landed. The flagger acts again on the list's updated entry, which
gives the current set.

Requests at the same moment: Flag Neighbor is decided against the neighbor's events as already
recorded, one at a time per neighbor. A suspension, reinstatement or closure landing at the same
moment is decided in the same order: whichever is recorded first stands.

There are no external calls, retries or compensations in this slice. Nothing is emailed when a
neighbor is flagged.

## Non-Functional Requirements
- **Security / authz:** only the Late Return Flagger issues Flag Neighbor. No person or screen
  does. It is an internal automation command, not on the REST API, so it has no sign-in guard.
- **PII & compliance:** Neighbor Flagged links a neighbor to loans they returned late. It holds no
  name or email.
- **Performance / SLA:** none beyond the list's freshness. The flagger acts as soon as the
  neighbor appears on Neighbors To Flag, shortly after the late return that crosses the threshold
  is recorded.

## Dependencies & Read Models Affected
- **Upstream events this slice relies on**, to apply its rules, and the fields it reads from each:
  - Neighbor Registered (Register Neighbor): neighborId.
  - Tool Checked Out (Check Out Tool): loanId, neighborId.
  - Tool Returned (Return Tool): loanId, returnedOn, late.
  - Neighbor Flagged (this slice): neighborId, loanIds.
  - Neighbor Suspended (Suspend Neighbor): neighborId.
  - Neighbor Reinstated (Reinstate Neighbor): neighborId.
  - Neighbor Account Closed (Close Account): neighborId.
  - These match slices/register-neighbor.md, slices/check-out-tool.md and slices/return-tool.md.
    The others are specified in slices not yet written in depth; each is checked against these
    keys when it is.
- **Read model this slice's processor reads:** Neighbors To Flag (slices/neighbors-to-flag.md):
  each neighbor due a flag, with the loans of all their counting late returns.
- **Downstream read models / slices affected:** Flagged Neighbors and Neighbors To Flag. Suspend
  Neighbor is offered on Flagged Neighbors.

## Open Questions
None.
