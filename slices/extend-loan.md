---
schemaVersion: 1
pattern: state-change
swimlane: Neighbor → Loans
status: draft
version: 1
---
# Slice: Extend Loan

![Diagram](./extend-loan.svg)

## Intent
Let a borrower keep a tool longer by extending their loan from My Loans. An extension moves the
due date later by one of the tool's default checkout periods.

## Invariants / Business Rules
- **INV-EXT-1:** Only the borrower can extend a loan, and they do it themselves from My Loans.
- **INV-EXT-2:** Only a loan that is still out can be extended.
  - Once the tool is returned, the loan is over.
- **INV-EXT-3:** An extension adds one of the tool's default checkout periods to the loan's
  current due date.
  - It adds the tool's default period even if the borrower chose a shorter period at checkout.
- **INV-EXT-4:** An extension is refused while any hold is waiting on the tool.
- **INV-EXT-5:** An extension is refused once the loan is overdue.
  - Overdue means today is past the due date. On the due date itself, the loan can still be
    extended.
  - Only the overdue loan itself is refused. The same borrower can still extend their other
    loans that are not overdue.
- **INV-EXT-6:** There is no limit on how many times a loan can be extended, as long as each
  extension meets the other rules here.
- **INV-EXT-7:** A suspended neighbor can't extend a loan.
  - Suspension takes away borrowing, and keeping a tool longer is borrowing.
- **INV-EXT-8:** A loan whose tool has been retired can't be extended.

## Scenarios (Given / When / Then)
- **Happy path**
  - **Given:** Ana has a cordless drill on loan, with a 7-day default checkout period, due
    12 October. No one has a hold on the drill, and it is 10 October.
  - **When:** Ana extends the loan from My Loans
  - **Then:** Loan Extended is recorded for the loan, with the new due date 19 October. Ana's
    My Loans and the Tool Catalog show the drill due 19 October.
- **Extending on the due date**
  - **Given:** Ana's drill loan is due 12 October, no hold is waiting, and it is 12 October
  - **When:** Ana extends the loan
  - **Then:** Loan Extended is recorded, with the new due date 19 October.
- **Extending again**
  - **Given:** Ana's drill loan has already been extended once and is due 19 October. No hold is
    waiting, and it is 18 October.
  - **When:** Ana extends the loan again
  - **Then:** Loan Extended is recorded, with the new due date 26 October.
- **Extending a loan checked out for a shorter period**
  - **Given:** Ana checked out the drill (7-day default) for 3 days, due 8 October. No hold is
    waiting, and it is 7 October.
  - **When:** Ana extends the loan
  - **Then:** Loan Extended is recorded, with the new due date 15 October.
- **Another loan is overdue**
  - **Given:** Ana has a ladder on loan that was due 4 October, and a cordless drill (7-day
    default) due 12 October with no hold waiting. It is 10 October.
  - **When:** Ana extends the drill loan
  - **Then:** Loan Extended is recorded for the drill, with the new due date 19 October. The
    ladder loan is still overdue.

## Open Questions
None.
