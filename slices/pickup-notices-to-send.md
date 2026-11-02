---
schemaVersion: 1
pattern: state-view
swimlane: System → Holds
status: draft
implementedIn: https://github.com/milehimikey/toolshed-v2/commit/e627d2500f6dd65a69323f893f4bfcce125921a9
conformedVersion: 1
conformedAt: 49d67795aabbe79704390bd08ef9925876074139
conformedOn: 2026-11-01
shippedVersion: 1
shippedOn: 2026-10-31
version: 2
---
# Slice: Pickup Notices To Send

![Diagram](./pickup-notices-to-send.svg)

## Intent
The pickup notice gateway's to-do list: every tool set aside for a holder whose pickup email
hasn't gone out yet, with everything the email needs. The gateway works through it, so each
holder is emailed once, as soon as a tool is set aside for them.

## Trigger & Actor
- **Actor:** the Pickup Notice Gateway, a system reaction in the Send Pickup Notice slice. No
  person reads this list, and it has no screen.
- **What the gateway does with it:** for each hold on the list, it records Pickup Notice Sent,
  and the email is then handed to the provider keyed by the hold's ID (Send Pickup Notice slice).

## Read Model / View
- **View:** `Pickup Notices To Send` built from events: "Hold Fulfilled",
  "Hold Cancelled", "Hold Collected", "Neighbor Email Changed", "Pickup Notice
  Sent", "Hold Expired"
- **Consumed by:** the Pickup Notice Gateway (Send Pickup Notice). Internal: no ui.
- **Freshness / consistency expectation:** eventual. A hold joins the list shortly after Hold
  Fulfilled is recorded (seconds, not minutes), and leaves shortly after the event that ends it.

One row per hold on the list. HoldId, ToolId and NeighborId are UUIDs. A Date is a calendar date
in the shed's local time zone.

| Field | Type | Source / Notes |
|-------|------|-----------------|
| holdId | HoldId | Hold Fulfilled.holdId. |
| toolId | ToolId | Hold Fulfilled.toolId. |
| neighborId | NeighborId | Hold Fulfilled.neighborId: the holder. |
| collectByDate | Date | Hold Fulfilled.collectByDate, read as recorded. |
| holderName | Text | Hold Fulfilled.neighborName. |
| holderEmail | Text | `Derived:` Hold Fulfilled.neighborEmail, replaced by each later Neighbor Email Changed.email for that neighbor. Always the current one. |
| toolName | Text | Hold Fulfilled.toolName. |

A row carries everything it needs from its own Hold Fulfilled; the view keeps no bookkeeping
beyond its rows. Neighbor Email Changed updates the address on that neighbor's rows only.

**When a row leaves the view.** A hold's row is removed when, for that hold:
- Pickup Notice Sent is recorded (INV-PNS-3);
- Hold Cancelled or Hold Expired is recorded (retiring the tool or closing the holder's account
  records Hold Cancelled for it);
- Hold Collected is recorded for the hold (INV-PNS-4).

Nothing puts it back.

**Queries**
- **List pickups to notify.** Takes nothing. Returns every row, with every field above. There is
  no paging. The order doesn't matter to the gateway, which sends one email per hold.

## Invariants / Business Rules
The list has no screen; only the pickup notice gateway reads it. When the email goes out, with no
quiet hours, what it says, and that none goes out once the collect-by date has passed, are Send
Pickup Notice's rules.

- **INV-PNS-1:** A hold is on the list from the moment Hold Fulfilled is recorded for it until its
  pickup email is sent, or its set-aside ends first.
- **INV-PNS-2:** Each item carries what the email needs: the holder's name and email, the tool's
  name, and the collect-by date.
  - The holder's name and email, the tool's name and the collect-by date are the ones Hold
    Fulfilled carries, read as recorded and never worked out here; a later Neighbor Email Changed
    replaces the email.
- **INV-PNS-3:** Once Pickup Notice Sent is recorded for a hold, it leaves the list and never
  comes back.
- **INV-PNS-4:** A hold leaves the list, unsent, when its set-aside ends first: the holder
  collects the tool, or the hold is cancelled or expires. Retiring the tool or closing the
  holder's account cancels the hold (Retire Tool, Close Account record Hold Cancelled).
  - A tool set aside for a holder is never on loan, so it is never recorded lost while on the
    list.
- **INV-PNS-7:** Applying the same event twice leaves the list as if it had been applied once.

## Scenarios (Given / When / Then)
- **Set-aside joins the list (INV-PNS-1, INV-PNS-2)**
  - **Given:** Ben Ito's (N2, ben@example.org) hold H1 is at the front of the queue for T1, the
    "Cordless drill"
  - **When:** Hold Fulfilled is recorded for H1, tool T1 "Cordless drill", holder N2 "Ben Ito",
    ben@example.org, collect by 17 October
  - **Then:** H1 is on the list with holderName "Ben Ito", holderEmail ben@example.org, toolName
    "Cordless drill" and collectByDate 17 October.
- **Pickup email sent (INV-PNS-3)**
  - **Given:** H1 is on the list
  - **When:** Pickup Notice Sent is recorded for H1
  - **Then:** H1 leaves the list, and never comes back.
- **Hold cancelled before the email (INV-PNS-4)**
  - **Given:** H1 is on the list
  - **When:** Hold Cancelled is recorded for H1
  - **Then:** H1 leaves the list, unsent.
- **Hold expired before the email (INV-PNS-4)**
  - **Given:** H1 is on the list
  - **When:** Hold Expired is recorded for H1
  - **Then:** H1 leaves the list, unsent.
- **Holder collects before the email (INV-PNS-4)**
  - **Given:** H1, Ben's hold on T1, is on the list
  - **When:** Hold Collected is recorded for H1
  - **Then:** H1 leaves the list, unsent.
- **Tool retired before the email (INV-PNS-4)**
  - **Given:** H1, on T1, is on the list
  - **When:** Retire Tool records Hold Cancelled for H1, with Tool Retired for T1
  - **Then:** H1 leaves the list, unsent.
- **Holder closes their account before the email (INV-PNS-4)**
  - **Given:** H1, Ben's hold, is on the list
  - **When:** Close Account records Hold Cancelled for H1, with Neighbor Account Closed for Ben
  - **Then:** H1 leaves the list, unsent.
- **Holder changes their email before the email goes out (INV-PNS-2)**
  - **Given:** H1 is on the list with ben@example.org
  - **When:** Neighbor Email Changed is recorded for Ben, to ben.ito@example.net
  - **Then:** H1 stays on the list, now with ben.ito@example.net, and the pickup email goes to that
    address.
- **No set-aside, nothing to send (INV-PNS-1)**
  - **Given:** the list is empty
  - **When:** Hold Placed is recorded for Cleo's hold on a ladder
  - **Then:** the list is still empty.
- **Same event applied twice (INV-PNS-7)**
  - **Given:** H1 is on the list once
  - **When:** the same Hold Fulfilled for H1 is applied again
  - **Then:** H1 is still on the list once.

## Alternate & Error Flows
- **Repeated events:** every event is applied idempotently (INV-PNS-7). An event applied a second
  time leaves the list as it was after the first.
- **A failed pickup email:** doesn't change the list. Every Pickup Notice Failed follows a Pickup
  Notice Sent, which already took the hold off the list, and this view doesn't hear Pickup Notice
  Failed.
- **Events for a hold that has left the list:** a later event for it (a replay) changes nothing.
- **The gateway reads a hold twice before Pickup Notice Sent lands:** the list may return the same
  hold on two reads in a row. Sending one email per hold is Send Pickup Notice's rule, enforced
  when it records Pickup Notice Sent; this list raises no errors of its own.

## Non-Functional Requirements
- **Security / authz:** read only by the Pickup Notice Gateway. No person or screen reads it.
- **PII & compliance:** holds the name and current email of every registered neighbor, and hands
  them to the gateway only for holders with a tool set aside. It shows them on no screen.
- **Performance / SLA:** none beyond the freshness above. How soon after a set-aside the email
  goes out is Send Pickup Notice's concern.

## Dependencies & Read Models Affected
- **Upstream events this slice relies on**, and the fields it reads from each:
  - Hold Fulfilled (Fulfill Hold): holdId, toolId, toolName, neighborId, neighborName, neighborEmail, collectByDate.
  - Neighbor Email Changed (Change Email): neighborId, email.
  - Pickup Notice Sent (Send Pickup Notice): holdId.
  - Hold Cancelled (Cancel Hold): holdId.
  - Hold Expired (Expire Hold): holdId.
  - Hold Collected (Collect Hold): holdId.
  - Hold Cancelled is also recorded by Retire Tool and Close Account, one per hold they end.
  - These match slices/add-tool.md, slices/register-neighbor.md, slices/change-email.md,
    slices/check-out-tool.md, and Hold Fulfilled as recorded in the conventions. The others are
    specified in slices not yet written in depth; each is checked against these keys when it is.
- **Downstream read models / slices affected:** Send Pickup Notice, whose gateway works from this
  list and records Pickup Notice Sent.

## Open Questions
None.
