---
schemaVersion: 1
pattern: state-view
swimlane: Shed Manager → Neighbors
status: draft
implementedIn: https://github.com/milehimikey/toolshed-v2/commit/dab5cb19e98edd34173c2419f853fb3f5e2c61e0
conformedVersion: 1
conformedAt: 49d67795aabbe79704390bd08ef9925876074139
conformedOn: 2026-11-01
shippedVersion: 1
shippedOn: 2026-10-31
version: 2
merged-from: flagged-neighbors@v1, suspended-neighbors@v1
---
# Slice: Neighbors

![Diagram](./neighbors.svg)

## Intent
Shows the shed manager every neighbor account that has signed up, with each account's current
status (active, suspended or closed), on the Neighbors screen. It is the shed manager's one
screen for neighbors: it shows who the system has flagged for repeated late returns and the late
returns behind each flag, who is suspended and since when, and account closures, and it is the
screen the shed manager suspends and reinstates neighbors from. The system flags; the shed
manager decides.

Version 2 folds the former Flagged Neighbors and Suspended Neighbors views into this one: both
were filters of the same accounts, and each offered one action this screen now offers.

## Trigger & Actor
- **Actor:** the shed manager, opening the Neighbors screen.
- **What it shows:** one entry per account ever registered, built from the events listed under
  Read Model / View. Nothing on this screen records anything by itself; its actions issue
  commands owned by other slices:
  - **Suspend** (Suspend Neighbor slice) — offered on every active account, flagged or not
    (INV-NBRS-6).
  - **Reinstate** (Reinstate Neighbor slice) — offered on every suspended account (INV-NBRS-13).
- Flagging is done by the system (Flag Neighbor). A neighbor is not told they were flagged, and a
  suspended neighbor is never flagged (Neighbors To Flag, INV-NTF-7). No email goes to the
  neighbor on suspension or reinstatement, and no reason or note is recorded with a suspension.
- Showing only the flagged accounts, or only the suspended ones, is the screen's own filter over
  this one list; there is no separate read model for either.

## Read Model / View
- **View:** `Neighbors` built from events: "Neighbor Registered", "Neighbor Email Changed",
  "Neighbor Flagged", "Neighbor Suspended", "Neighbor Reinstated", "Neighbor Account Closed",
  and, to name the late returns behind a flag, "Tool Returned"
- **Consumed by:** the Neighbors screen (Shed Manager)
- **Freshness / consistency expectation:** eventual. An entry reflects an event shortly after it
  is recorded (seconds, not minutes). Suspend and Reinstate check their own rules when they run,
  so a stale entry can't suspend someone who is no longer active or reinstate someone who is no
  longer suspended.

One row per account. NeighborId, LoanId and ToolId are UUIDs. A Date is a calendar date in the
shed's local time zone; an Instant is a UTC instant set when the event was recorded.

| Field | Type | Source / Notes |
|-------|------|-----------------|
| neighborId | NeighborId | Neighbor Registered.neighborId. |
| name | Text | Neighbor Registered.name. Never changes. Need not be unique. |
| email | Text | Neighbor Registered.email, replaced by each Neighbor Email Changed.email for this account. Always the account's current email. |
| registeredAt | Instant | Neighbor Registered.registeredAt. Shown as the sign-up date: its calendar date in the shed's local time zone (INV-NBRS-5). |
| status | Text | One of `active`, `suspended`, `closed`. `Derived:` see the status rules below. |
| suspendedAt | Instant | `Derived:` suspendedAt of the Neighbor Suspended behind a `suspended` status; empty otherwise (INV-NBRS-12). Shown as the suspended date: its calendar date in the shed's local time zone. |
| flagCount | Int | `Derived:` the number of open flags on the account: Neighbor Flagged recorded since the account was last suspended, or ever if never suspended (INV-NBRS-10, INV-NBRS-11). 0 when the account is not flagged. |
| latestFlaggedAt | Instant | `Derived:` flaggedAt of the latest open flag; empty when flagCount is 0. Shown as a date in the shed's local time zone. |
| lateReturns | LateReturn[] | `Derived:` one item for each loan named by the account's open flags, as below; empty when flagCount is 0. |
| canSuspend | Bool | `Derived:` yes while status is `active` (INV-NBRS-6). |
| canReinstate | Bool | `Derived:` yes while status is `suspended` (INV-NBRS-13). |

Each LateReturn item:

| Field | Type | Source / Notes |
|-------|------|-----------------|
| loanId | LoanId | One of Neighbor Flagged.loanIds. |
| toolName | Text | Tool Returned.toolName for the loan. |
| dueDate | Date | Tool Returned.dueDate for the loan: the due date in force when it was returned. |
| returnedOn | Date | Tool Returned.returnedOn for the loan. |
| flaggedAt | Instant | flaggedAt of the Neighbor Flagged that named this loan. |

To fill the late returns when a flag lands, the view also keeps, beyond its rows, each returned
loan's tool name, due date and return date as Tool Returned carried them. These are bookkeeping,
not rows, and are never returned (INV-NBRS-14).

**Status rules.** An account's status is a test of its current facts, taken in this order. The
first that holds wins:
1. **closed** — Neighbor Account Closed has been recorded for the account.
2. **suspended** — otherwise, Neighbor Suspended has been recorded and no Neighbor Reinstated
   since.
3. **active** — otherwise.

**Flag rules.** A flag is open from its Neighbor Flagged until the account is next suspended or
closed; suspension and closure clear the account's open flags (INV-NBRS-11). A Neighbor Flagged
recorded after a reinstatement starts the account's flags afresh.

**Queries**
- **List the neighbors.** Takes nothing. Returns every account, each with every field above,
  including its late returns. There is no paging: the query always returns every account, ordered
  by name, then by registeredAt (INV-NBRS-8). The screen filters to flagged (flagCount above 0)
  or suspended accounts itself.

## Invariants / Business Rules
- **INV-NBRS-1:** Every account appears on the Neighbors screen exactly once.
  - One entry per Neighbor Registered; nothing else creates an entry. A neighbor who signs up
    again after closing their account has a new account, so they appear twice: the closed
    entry and the new one.
- **INV-NBRS-2:** Each account's status is active, suspended or closed, worked out by the status
  rules above.
  - Neighbor Registered → active. Neighbor Suspended → suspended. Neighbor Reinstated →
    active. Neighbor Account Closed → closed.
- **INV-NBRS-3:** An account that is closed stays listed, shown as closed.
  - This screen is where the shed manager sees account closures. It is the record of every
    account; a closed account keeps its row, with no action offered and no open flags.
- **INV-NBRS-4:** Closed is final.
  - A suspended neighbor who closes their account shows as closed, not suspended. No later event
    changes a closed account's status.
- **INV-NBRS-5:** Each entry shows the account's name, its current email, its sign-up date, and
  its status.
  - The email is set by Neighbor Registered and replaced by each Neighbor Email Changed. The name
    never changes.
- **INV-NBRS-6:** The screen offers Suspend on every active account, whether or not the neighbor
  has been flagged, and on no other.
  - Suspend Neighbor checks its own rules when it runs.
- **INV-NBRS-8:** Accounts are listed by name, ignoring case, then by when they signed up.
  - So a neighbor who signed up again after closing appears twice, the older account first.
- **INV-NBRS-7:** Applying the same event twice leaves the Neighbors screen as if it had been
  applied once.
- **INV-NBRS-9:** Only the shed manager can read the Neighbors screen.
  - A signed-in caller without the shed-manager role is refused with `not-shed-manager`, and a
    caller with no sign-in with `not-signed-in`. Nothing is returned to either.
- **INV-NBRS-10:** A flagged account shows how many open flags it has, the date of the latest,
  and the late returns behind every open flag, each with the tool's name, its due date, the date
  it was returned, and the date of the flag that named it.
  - Neighbor Flagged names the late returns it uses up, as the loans they belong to. It carries
    no copies of names or dates. The view takes those from each loan's Tool Returned, which
    carries the tool's name, the due date in force at the return, and the return date.
  - Being flagged never suspends anyone by itself, and there is no dismiss action: a flag the
    shed manager decides not to act on stays open.
- **INV-NBRS-11:** Suspending or closing the account clears its open flags; a flag recorded after
  a reinstatement starts the count afresh.
  - The flags were what the shed manager acted on, or can no longer act on. A suspended neighbor
    is never flagged (Neighbors To Flag), so no flag lands while suspended.
- **INV-NBRS-12:** A suspended account shows when it was suspended; the date is cleared on
  reinstatement.
  - A suspension never lifts by itself after a set time, and it is never permanent. A neighbor
    suspended again after a reinstatement shows the new date.
- **INV-NBRS-13:** The screen offers Reinstate on every suspended account, and on no other.
  - Reinstate Neighbor checks its own rules when it runs.
- **INV-NBRS-14:** To name late returns, the view keeps every returned loan's tool name, due date
  and return date, whether or not the borrower is flagged; none of it is returned on its own.

## Scenarios (Given / When / Then)
Unless a scenario says otherwise: Ana Ruiz registered with ana@example.org and Ben Ito with
ben@example.org. Ana returned the ladder (L1, due 31 August) on 1 September, the drill (L2, due
15 September) on 20 September and the hedge trimmer (L3, due 1 October) on 5 October, all late.

- **Neighbor registered (INV-NBRS-1, INV-NBRS-2, INV-NBRS-5, INV-NBRS-6)**
  - **Given:** no neighbors have signed up
  - **When:** Neighbor Registered is recorded for Ana Ruiz, ana@example.org
  - **Then:** the Neighbors screen lists Ana Ruiz, ana@example.org, signed up on today's date, as
    active, not flagged, with Suspend offered and Reinstate not offered.
- **Two neighbors with the same name (INV-NBRS-1)**
  - **Given:** the Neighbors screen lists Ana Ruiz, ana@example.org
  - **When:** Neighbor Registered is recorded for a different account, also named Ana Ruiz,
    with ana.r@example.net
  - **Then:** the Neighbors screen lists both, as separate entries.
- **Same event applied twice (INV-NBRS-7)**
  - **Given:** the Neighbors screen lists Ana once, as active
  - **When:** the same Neighbor Registered for Ana is applied again
  - **Then:** the Neighbors screen still lists Ana once.
- **Email changed (INV-NBRS-5)**
  - **Given:** the Neighbors screen lists Ana Ruiz with ana@example.org, as active
  - **When:** Neighbor Email Changed is recorded for Ana, to ana.ruiz@example.net
  - **Then:** the Neighbors screen lists Ana Ruiz with ana.ruiz@example.net, still active.
- **Neighbor flagged (INV-NBRS-10)**
  - **Given:** the Neighbors screen lists Ana as active, not flagged
  - **When:** Neighbor Flagged is recorded for Ana on 5 October, naming L1, L2 and L3
  - **Then:** the Neighbors screen shows Ana as active, flagged once, latest on 5 October, with
    three late returns: the ladder (due 31 August, returned 1 September), the drill (due 15
    September, returned 20 September) and the hedge trimmer (due 1 October, returned 5 October).
    Suspend is offered, as before.
- **Due date after an extension (INV-NBRS-10)**
  - **Given:** L2 was extended from 15 September to 18 September before it was returned on 20
    September
  - **When:** Neighbor Flagged is recorded for Ana, naming L1, L2 and L3
  - **Then:** the drill's late return shows due 18 September, returned 20 September.
- **Flagged again (INV-NBRS-10)**
  - **Given:** the Neighbors screen shows Ana flagged once on 5 October, and she has since
    returned three more tools late (L4, L5, L6)
  - **When:** Neighbor Flagged is recorded for Ana on 15 November, naming L4, L5 and L6
  - **Then:** the Neighbors screen shows Ana flagged twice, latest on 15 November, with all six
    late returns: L1, L2 and L3 flagged 5 October, and L4, L5 and L6 flagged 15 November.
- **Flag left alone (INV-NBRS-10)**
  - **Given:** the Neighbors screen has shown Ana flagged since 5 October, and the shed manager
    has not suspended her
  - **When:** a month passes with no Neighbor Suspended recorded for Ana
  - **Then:** Ana is still shown flagged once, with the same late returns.
- **Flagged neighbor changes their email (INV-NBRS-5, INV-NBRS-10)**
  - **Given:** the Neighbors screen shows Ana Ruiz, ana@example.org, flagged once
  - **When:** Neighbor Email Changed is recorded for Ana, to ana.ruiz@example.net
  - **Then:** the Neighbors screen shows Ana Ruiz, ana.ruiz@example.net. Her flags are unchanged.
- **Neighbor suspended (INV-NBRS-2, INV-NBRS-6, INV-NBRS-11, INV-NBRS-12, INV-NBRS-13)**
  - **Given:** the Neighbors screen shows Ana as active, flagged once, and Ben as active
  - **When:** Neighbor Suspended is recorded for Ana on 7 October
  - **Then:** the Neighbors screen shows Ana as suspended since 7 October, with no open flags,
    Suspend not offered and Reinstate offered. Ben is unchanged.
- **Suspended without a flag (INV-NBRS-6, INV-NBRS-12)**
  - **Given:** the Neighbors screen shows Ben as active, not flagged
  - **When:** Neighbor Suspended is recorded for Ben on 9 October
  - **Then:** the Neighbors screen shows Ben as suspended since 9 October, with Reinstate offered.
- **Neighbor reinstated (INV-NBRS-2, INV-NBRS-6, INV-NBRS-12, INV-NBRS-13)**
  - **Given:** the Neighbors screen shows Ana as suspended since 7 October
  - **When:** Neighbor Reinstated is recorded for Ana
  - **Then:** the Neighbors screen shows Ana as active again, with no suspended date, not
    flagged, with Suspend offered and Reinstate not offered.
- **Flagged again after reinstatement (INV-NBRS-11)**
  - **Given:** Ana was flagged, then suspended, which cleared her flags, and later reinstated
  - **When:** Neighbor Flagged is recorded for Ana on 20 January, naming L7, L8 and L9
  - **Then:** the Neighbors screen shows Ana flagged once, latest on 20 January, with only L7, L8
    and L9.
- **Suspended again after reinstatement (INV-NBRS-12)**
  - **Given:** Ana was suspended on 7 October and reinstated on 1 November
  - **When:** Neighbor Suspended is recorded for Ana again on 3 February
  - **Then:** the Neighbors screen shows Ana as suspended since 3 February.
- **Account closed (INV-NBRS-3, INV-NBRS-6, INV-NBRS-11)**
  - **Given:** the Neighbors screen lists Ana as active, flagged once
  - **When:** Neighbor Account Closed is recorded for Ana
  - **Then:** the Neighbors screen still lists Ana, now shown as closed, with no open flags and
    neither Suspend nor Reinstate offered.
- **Suspended neighbor closes their account (INV-NBRS-4, INV-NBRS-13)**
  - **Given:** the Neighbors screen shows Ana as suspended
  - **When:** Neighbor Account Closed is recorded for Ana
  - **Then:** the Neighbors screen shows Ana as closed, with Reinstate not offered.
- **Closed stays closed (INV-NBRS-4)**
  - **Given:** the Neighbors screen shows Ana's account as closed
  - **When:** an earlier Neighbor Suspended, Neighbor Reinstated or Neighbor Flagged for that
    account is applied again
  - **Then:** the account is still shown as closed, with no open flags.
- **Signing up again after closing (INV-NBRS-1, INV-NBRS-3)**
  - **Given:** the Neighbors screen shows Ana Ruiz's account, ana@example.org, as closed
  - **When:** Neighbor Registered is recorded for a new account for Ana Ruiz, with
    ana@example.org
  - **Then:** the Neighbors screen lists two entries for Ana Ruiz with ana@example.org: the old
    account, still closed, and the new one, active, with Suspend offered.
- **Listing order (INV-NBRS-8)**
  - **Given:** Ben Ito signed up on 2 October; Ana Ruiz signed up on 1 October, closed her
    account, and signed up again on 5 October
  - **When:** the shed manager lists the neighbors
  - **Then:** the accounts come back as: Ana Ruiz (signed up 1 October, closed), Ana Ruiz
    (signed up 5 October, active), then Ben Ito.
- **Listing the neighbors**
  - **Given:** the Neighbors screen lists Ana (active, flagged once), Ben (suspended) and Cara
    (closed)
  - **When:** the shed manager's screen lists the neighbors
  - **Then:** all three are returned with all their fields, Ana's with her late returns, with no
    paging. Only Ana's entry offers Suspend; only Ben's offers Reinstate.
- **A neighbor tries to read the list (INV-NBRS-9)**
  - **Given:** the Neighbors screen has entries
  - **When:** a neighbor, signed in without the shed-manager role, asks for the neighbors
  - **Then:** refused with `not-shed-manager`: "Only the shed manager can see this screen."
    Nothing is returned.
- **No sign-in (INV-NBRS-9)**
  - **Given:** the Neighbors screen has entries
  - **When:** a caller with no sign-in asks for the neighbors
  - **Then:** refused with `not-signed-in`: "Please sign in." Nothing is returned.

## Alternate & Error Flows
- **Read refusals:** the list query refuses a caller without the shed-manager role with
  `not-shed-manager` ("Only the shed manager can see this screen."), and a caller with no
  sign-in with `not-signed-in` ("Please sign in."). Nothing is returned on a refusal. These
  mean the same conditions as in the shed manager's commands.
- **Repeated events:** every event is applied idempotently (INV-NBRS-7). An event applied a
  second time leaves the entry as it was after the first.
- **Event for an account the view hasn't seen:** events are applied in the order they were
  recorded, and every account starts with Neighbor Registered, so this can't happen.
- **Events on a closed account:** a Neighbor Flagged, Neighbor Suspended or Neighbor Reinstated
  applied to a closed account changes nothing (INV-NBRS-4).
- **Stale actions:** Suspend or Reinstate offered on the screen can be out of date by the time
  it is used (for example, on a neighbor who has just closed their account). Suspend Neighbor and
  Reinstate Neighbor check their own rules and refuse with their own reasons; this view raises no
  errors of its own.

## Non-Functional Requirements
- **Security / authz:** the Neighbors screen is the shed manager's alone; neighbors see only
  their own account, on My Account. Sign-in, handled outside the shed's events, supplies the
  shed-manager role; only a caller with it may read this view.
- **PII & compliance:** shows every account's name, current email, sign-up date, status,
  suspension date and late-return history to the shed manager, including closed accounts, which
  stay listed. A replaced email is not kept in the view. To name late returns, the view keeps
  every returned loan's tool name and dates, whether or not the borrower is flagged.
  Names and emails are kept with the shed's loan and hold history and are never erased.
- **Performance / SLA:** none beyond the freshness above.

## Dependencies & Read Models Affected
- **Upstream events this slice relies on**, and the fields it reads from each:
  - Neighbor Registered (Register Neighbor): neighborId, name, email, registeredAt.
  - Neighbor Email Changed (Change Email): neighborId, email.
  - Neighbor Flagged (Flag Neighbor): neighborId, loanIds (LoanId[]), flaggedAt.
  - Neighbor Suspended (Suspend Neighbor): neighborId, suspendedAt.
  - Neighbor Reinstated (Reinstate Neighbor): neighborId.
  - Neighbor Account Closed (Close Account): neighborId.
  - Tool Returned (Return Tool): loanId, toolName, dueDate, returnedOn.
  - These keys match slices/register-neighbor.md, flag-neighbor.md, suspend-neighbor.md and the
    loan slices' docs.
- **Downstream read models / slices affected:** none. The screen it feeds issues Suspend
  Neighbor and Reinstate Neighbor.
- **Lineage:** v2 merges Flagged Neighbors v1 and Suspended Neighbors v1 into this view (model
  reshape, 2026-11-01). Their docs stay in slices/ marked superseded by neighbors@v2.

## Open Questions
None.
