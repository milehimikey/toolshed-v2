---
schemaVersion: 1
pattern: state-view
swimlane: Shed Manager → Loans
status: draft
implementedIn: https://github.com/milehimikey/toolshed-v2/commit/4831929b38a3b68b09d2ecbbff122e795f05b971
conformedVersion: 1
conformedAt: 49d67795aabbe79704390bd08ef9925876074139
conformedOn: 2026-11-01
shippedVersion: 1
shippedOn: 2026-10-31
version: 2
---
# Slice: Undelivered Notices

![Diagram](./undelivered-notices.svg)

## Intent
Shows the shed manager every email the shed couldn't deliver — overdue notices and pickup
notices — so they can reach the neighbor another way. A neighbor who wasn't told their loan is
overdue, or that a tool is waiting for them, doesn't stay unnoticed. An entry stays only while
there is something to follow up.

## Trigger & Actor
- **Actor:** the shed manager, opening the Undelivered Notices screen.
- **What it shows:** one entry per failed overdue notice whose loan is still open, and one per
  failed pickup notice whose set-aside hasn't ended, built from the events listed under Read
  Model / View. The screen offers no actions. Following up is the shed manager's job, in person;
  no email is tried again after its failure is recorded (Send Overdue Notice, INV-OVN-9).
- Which failure reports become events is decided by the two failure slices: a report that
  matches no notice the shed sent, or that arrives after the loan or set-aside has ended, is
  ignored there and never reaches this view.

## Read Model / View
- **View:** `Undelivered Notices` built from events: "Overdue Notice Failed", "Pickup Notice
  Failed", "Neighbor Email Changed", "Loan Extended", "Tool Returned", "Tool Lost", "Hold
  Collected", "Hold Cancelled", "Hold Expired"
- **Consumed by:** the Undelivered Notices screen (Shed Manager)
- **Freshness / consistency expectation:** eventual. An entry appears, changes or drops off
  shortly after its event is recorded (seconds, not minutes).

One row per entry. LoanId, HoldId, ToolId and NeighborId are UUIDs. A Date is a calendar date in
the shed's local time zone; an Instant is a UTC instant set when the event was recorded. A field
marked optional can be empty.

| Field | Type | Source / Notes |
|-------|------|-----------------|
| kind | Text | `overdue-notice` or `pickup-notice`. `Derived:` from which failure event created the entry. |
| loanId | LoanId | Optional; overdue-notice entries only. Overdue Notice Failed.loanId. |
| holdId | HoldId | Optional; pickup-notice entries only. `Derived:` Pickup Notice Failed.holdId. |
| neighborId | NeighborId | The failure event's neighborId: the borrower or the holder. |
| neighborName | Text | The failure event's neighborName: the name the notice was addressed to. |
| neighborEmail | Text | `Derived:` the failure event's neighborEmail (the address that failed), replaced by each later Neighbor Email Changed.email for that neighbor. Always the current one. |
| toolId | ToolId | Optional; pickup-notice entries only. `Derived:` Pickup Notice Failed.toolId. |
| toolName | Text | The failure event's toolName. |
| dueDate | Date | Optional; overdue-notice entries only. Overdue Notice Failed.dueDate, replaced by each later Loan Extended.dueDate for the loan. |
| collectByDate | Date | Optional; pickup-notice entries only. `Derived:` Pickup Notice Failed.collectByDate. |
| reason | Text | `refused` or `bounced`, from the failure event's reason. |
| failedAt | Instant | From the failure event's failedAt. Shown as the failure date: its calendar date in the shed's local time zone. |

To fill an entry the moment its failure lands, the view also keeps, for every open loan, its
tool, borrower and current due date, and for every live set-aside, its tool, holder and
collect-by date. These are bookkeeping, not rows, and are never returned.

**When an entry drops off.**
- An overdue-notice entry drops off when its loan ends: Tool Returned or Tool Lost for that
  loan. Retiring the tool while it is out doesn't end the loan, so the entry stays.
- A pickup-notice entry drops off when its set-aside ends: Hold Collected, Hold Cancelled or
  Hold Expired for the hold. Retiring the tool or closing the holder's account ends it by
  recording Hold Cancelled (Retire Tool, Close Account).

Nothing brings an entry back.

**Queries**
- **List undelivered notices.** Takes nothing. Returns every entry with all the fields above.
  There is no paging. Entries are listed by neighbor name, then by failedAt, the time the entry
  was created (INV-UDN-9).

## Invariants / Business Rules
- **INV-UDN-1:** Every Overdue Notice Failed and every Pickup Notice Failed creates exactly one
  entry.
  - Nothing else creates an entry. A failure is recorded only after the email provider has made
    its 3 delivery attempts over about an hour and reported a final failure, so the shed manager
    never sees a notice that is still being tried.
- **INV-UDN-2:** Each entry shows the failure date, and whether the provider refused the email or
  it bounced.
  - A bounce usually means a wrong email address. The shed manager can ask the neighbor to
    change it from My Account.
- **INV-UDN-3:** An overdue-notice entry shows the borrower's name and current email, the tool's
  name, and the loan's current due date.
  - The name, the email, the tool's name and the due date come with Overdue Notice Failed, the
    email as replaced by any later Neighbor Email Changed and the due date as moved by any later
    Loan Extended.
- **INV-UDN-3a:** A pickup-notice entry shows the holder's name and current email, the tool's
  name, and the collect-by date.
  - Name, email, tool name and collect-by date come with Pickup Notice Failed; the email is
    replaced by any later Neighbor Email Changed.
- **INV-UDN-4:** An overdue-notice entry drops off when its loan ends: the tool is returned, or
  the shed manager records it lost.
  - Either way the loan has ended, and there is nothing left to follow up. A tool retired while
    out keeps its entry until one of these happens.
- **INV-UDN-5:** A pickup-notice entry drops off when its set-aside ends.
  - A set-aside ends when the holder collects the tool, or the hold is cancelled or expires;
    retiring the tool or closing the holder's account records Hold Cancelled for it.
- **INV-UDN-9:** Entries are listed by neighbor name, ignoring case, then by when the failure was
  recorded.
  - The same order as every list in the shed.
- **INV-UDN-8:** Applying the same event twice leaves Undelivered Notices as if it had been
  applied once.
- **INV-UDN-10:** Only the shed manager can read Undelivered Notices.
  - A signed-in caller without the shed-manager role is refused with `not-shed-manager`, and a
    caller with no sign-in with `not-signed-in`. Nothing is returned to either.

## Scenarios (Given / When / Then)
Unless a scenario says otherwise: Ana Ruiz registered with ana@example.org and has the cordless
drill on loan L1, due 12 October. Ben Ito registered with ben@example.org, and Hold Fulfilled was
recorded for his hold H1 on the hedge trimmer with a collect-by date of 16 October.

- **Overdue notice fails (INV-UDN-1, INV-UDN-2, INV-UDN-3)**
  - **Given:** Undelivered Notices is empty
  - **When:** Overdue Notice Failed is recorded for L1 on 13 October, as bounced
  - **Then:** Undelivered Notices lists an overdue-notice entry: Ana Ruiz, ana@example.org, the
    cordless drill, due 12 October, failed 13 October, bounced.
- **Due date shown after an extension (INV-UDN-3)**
  - **Given:** Loan Extended moved L1's due date to 19 October, and the loan is now overdue
  - **When:** Overdue Notice Failed is recorded for L1 on 20 October, as refused
  - **Then:** the entry shows the due date 19 October, failed 20 October, refused.
- **Pickup notice fails (INV-UDN-1, INV-UDN-2, INV-UDN-3a)**
  - **Given:** Undelivered Notices lists Ana's overdue-notice entry
  - **When:** Pickup Notice Failed is recorded for H1 on 13 October, as refused
  - **Then:** Undelivered Notices also lists a pickup-notice entry: Ben Ito, ben@example.org, the
    hedge trimmer, collect by 16 October, failed 13 October, refused. Ana's entry is unchanged.
- **Same event applied twice (INV-UDN-8)**
  - **Given:** Undelivered Notices lists Ana's overdue-notice entry once
  - **When:** the same Overdue Notice Failed for L1 is applied again
  - **Then:** the entry is still listed once.
- **Tool returned (INV-UDN-4)**
  - **Given:** Undelivered Notices lists Ana's overdue-notice entry for L1
  - **When:** Tool Returned is recorded for L1
  - **Then:** Ana's entry drops off.
- **Tool recorded lost (INV-UDN-4)**
  - **Given:** Undelivered Notices lists Ana's overdue-notice entry for L1
  - **When:** Tool Lost is recorded for L1
  - **Then:** Ana's entry drops off.
- **Tool retired while out (INV-UDN-4)**
  - **Given:** Undelivered Notices lists Ana's overdue-notice entry for L1
  - **When:** Tool Retired is recorded for the cordless drill
  - **Then:** Ana's entry stays. It drops off once L1 is returned or recorded lost.
- **Holder collects the tool (INV-UDN-5)**
  - **Given:** Undelivered Notices lists Ben's pickup-notice entry for H1
  - **When:** Hold Collected is recorded for H1
  - **Then:** Ben's entry drops off.
- **Someone else checks out the tool (INV-UDN-5)**
  - **Given:** Undelivered Notices lists Ben's pickup-notice entry for H1, and his collect-by date
    has passed, but Hold Expired hasn't been recorded
  - **When:** Cara checks the hedge trimmer out, so Hold Collected is recorded for her hold, not H1
  - **Then:** Ben's entry stays until Hold Expired is recorded for H1, and then drops off.
- **Holder cancels the hold (INV-UDN-5)**
  - **Given:** Undelivered Notices lists Ben's pickup-notice entry for H1
  - **When:** Hold Cancelled is recorded for H1
  - **Then:** Ben's entry drops off.
- **Set-aside expires (INV-UDN-5)**
  - **Given:** Undelivered Notices lists Ben's pickup-notice entry for H1
  - **When:** Hold Expired is recorded for H1
  - **Then:** Ben's entry drops off.
- **Tool retired while set aside (INV-UDN-5)**
  - **Given:** Undelivered Notices lists Ben's pickup-notice entry for H1
  - **When:** Retire Tool records Hold Cancelled for H1, with Tool Retired for the hedge trimmer
  - **Then:** Ben's entry drops off.
- **Holder closes their account (INV-UDN-5)**
  - **Given:** Undelivered Notices lists Ben's pickup-notice entry for H1
  - **When:** Close Account records Hold Cancelled for H1, with Neighbor Account Closed for Ben
  - **Then:** Ben's entry drops off.
- **Neighbor corrects their email (INV-UDN-3)**
  - **Given:** Undelivered Notices lists Ana's overdue-notice entry, showing ana@example.org, the
    address that bounced
  - **When:** Neighbor Email Changed is recorded for Ana, to ana.ruiz@example.net
  - **Then:** Ana's entry shows ana.ruiz@example.net. The entry stays until L1 is returned or
    recorded lost.
- **Listing order (INV-UDN-9)**
  - **Given:** Undelivered Notices lists Ben's pickup-notice entry (failed 13 October), Ana's
    overdue-notice entry (failed 14 October) and Ana's pickup-notice entry (failed 12 October)
  - **When:** the shed manager lists undelivered notices
  - **Then:** the entries come back as: Ana's pickup-notice entry, Ana's overdue-notice entry,
    then Ben's.
- **Listing undelivered notices**
  - **Given:** Undelivered Notices lists Ana's overdue-notice entry and Ben's pickup-notice entry
  - **When:** the shed manager's screen lists undelivered notices
  - **Then:** both entries are returned with all their fields, with no paging.
- **A neighbor tries to read the list (INV-UDN-10)**
  - **Given:** Undelivered Notices has entries
  - **When:** a neighbor, signed in without the shed-manager role, asks for the undelivered notices
  - **Then:** refused with `not-shed-manager`: "Only the shed manager can see this screen."
    Nothing is returned.
- **No sign-in (INV-UDN-10)**
  - **Given:** Undelivered Notices has entries
  - **When:** a caller with no sign-in asks for the undelivered notices
  - **Then:** refused with `not-signed-in`: "Please sign in." Nothing is returned.

## Alternate & Error Flows
- **Read refusals:** the list query refuses a caller without the shed-manager role with
  `not-shed-manager` ("Only the shed manager can see this screen."), and a caller with no
  sign-in with `not-signed-in` ("Please sign in."). Nothing is returned on a refusal. These
  mean the same conditions as in the shed manager's commands.
- **Repeated events:** every event is applied idempotently (INV-UDN-8). An event applied a
  second time leaves the entries as they were after the first.
- **Ended loans and set-asides:** a failure report for a loan or set-aside that has already
  ended is ignored by Record Overdue Notice Failure and Record Pickup Notice Failure, so no entry
  is ever created for one. An entry, once it drops off, never comes back.
- **No actions:** the screen issues no commands. Its only use is to prompt the shed manager to
  follow up in person.

## Non-Functional Requirements
- **Security / authz:** the Undelivered Notices screen is the shed manager's alone; neighbors
  never see it. Sign-in, handled outside the shed's events, supplies the shed-manager role; only
  a caller with it may read this view.
- **PII & compliance:** shows neighbors' names, current emails and the tools they have or are
  waiting for, to the shed manager only. An entry is kept only while its loan or set-aside is
  live. Names and emails themselves are kept with the shed's loan and hold history and never
  erased.
- **Performance / SLA:** none beyond the freshness above.

## Dependencies & Read Models Affected
- **Upstream events this slice relies on**, and the fields it reads from each:
  - Overdue Notice Failed (Record Overdue Notice Failure): loanId, neighborId, neighborName, neighborEmail, toolName, dueDate, reason, failedAt.
  - Pickup Notice Failed (Record Pickup Notice Failure): holdId, toolId, neighborId, neighborName, neighborEmail, toolName, collectByDate, reason, failedAt.
  - Neighbor Email Changed (Change Email): neighborId, email.
  - Loan Extended (Extend Loan): loanId, dueDate.
  - Tool Returned (Return Tool), Tool Lost (Record Tool Lost): loanId.
  - Hold Collected (Check Out Tool): holdId (the holder collecting ends the set-aside).
  - Hold Cancelled (Cancel Hold), Hold Expired (Expire Hold): holdId.
  - Hold Cancelled is also recorded by Retire Tool and Close Account, one per hold they end.
  - These keys match the deep-passed docs; the two failure events carrying the notice's details
    is agreed with slices/record-overdue-notice-failure.md and record-pickup-notice-failure.md.
- **Downstream read models / slices affected:** none. The screen issues no commands.

## Open Questions
None.
