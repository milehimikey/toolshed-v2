---
schemaVersion: 1
pattern: state-view
swimlane: Shed Manager → Loans
status: draft
version: 1
---
# Slice: Undelivered Notices

![Diagram](./undelivered-notices.svg)

## Intent
Shows the shed manager every overdue notice that could not be delivered, so they can follow up
with the borrower in person. A borrower who wasn't told their loan is overdue doesn't stay
unnoticed.

## Invariants / Business Rules
- **INV-UDN-1:** Every failed overdue notice appears on Undelivered Notices exactly once.
  - One entry per Overdue Notice Failed; nothing else creates an entry.
  - A failure is recorded only after the system's retries have run out, so the shed manager
    never sees a notice that is still being retried.
- **INV-UDN-2:** Each entry identifies the borrower and the loan, so the shed manager can follow
  up: the borrower's name and email, the tool's name, and the due date.
  - Anything more is an open question below.
- **INV-UDN-3:** Only the shed manager sees Undelivered Notices. Neighbors never do.

## Scenarios (Given / When / Then)
- **Notice fails**
  - **Given:** Undelivered Notices is empty
  - **When:** Overdue Notice Failed is recorded for Ana Ruiz's cordless drill loan, due
    12 October
  - **Then:** Undelivered Notices lists Ana Ruiz with ana@example.org, the cordless drill, and
    the due date 12 October.
- **Another notice fails**
  - **Given:** Undelivered Notices lists Ana's drill loan
  - **When:** Overdue Notice Failed is recorded for Ben's ladder loan
  - **Then:** Undelivered Notices lists both, and Ana's entry is unchanged.

## Open Questions
- [ ] What takes an entry off Undelivered Notices?
  - As modeled, nothing does. Entries pile up forever, including for loans long since returned.
  - Options: (a) an entry drops off when the tool is returned, since the reason to follow up is
    gone (this list would hear Tool Returned, a model change); (b) the shed manager marks an
    entry as followed up (a new command, event and slice); (c) entries stay for good, as a
    record.
  - Recommendation: (a). It needs no new action. A manager-side dismiss action was turned down
    for flags, and the same reasoning fits here.
- [ ] Besides name, email, tool and due date, what else does each entry show?
  - Recommendation: the date the failure was recorded, and whether the provider refused the
    notice or it bounced. A bounce usually means a wrong email, and the shed manager can tell
    the neighbor.
- [ ] Should undelivered pickup notices appear here too?
  - Pickup notices go through the same email provider. As modeled, a pickup notice that fails
    goes unrecorded. The holder may never learn the tool is set aside, and their 3-day window
    lapses.
  - Options: (a) handle a failed pickup notice the same way as an overdue one: retries, then a
    recorded failure shown here (a new event and slice, a model change); (b) leave pickup
    failures unrecorded.
  - Recommendation: (a). The same provider fails the same way, and the shed manager is the one
    who can reach the neighbor.
