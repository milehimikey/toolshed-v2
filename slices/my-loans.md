---
schemaVersion: 1
pattern: state-view
swimlane: Neighbor → Loans
status: draft
version: 1
---
# Slice: My Loans

![Diagram](./my-loans.svg)

## Intent
Shows a neighbor the tools they have borrowed and when each is due back, on the screen they
extend and return loans from.

## Invariants / Business Rules
- **INV-MYL-1:** A neighbor's My Loans shows only that neighbor's own loans.
- **INV-MYL-2:** Every checkout appears on the borrower's My Loans as one loan, with the tool
  and its due date.
  - The due date is the one set at checkout: the tool's default checkout period, or a shorter
    period the borrower chose.
  - A holder collecting a tool set aside for them is an ordinary checkout and appears the same
    way.
- **INV-MYL-3:** When a loan is extended, its due date on My Loans becomes the extended due date.
  - An extension adds one of the tool's default checkout periods to the current due date.
- **INV-MYL-4:** A loan shows as overdue once today is past its due date, and not before.
  - Overdue is worked out by comparing the due date with today. No event marks a loan overdue,
    so the screen shows it overdue on the day after the due date with nothing recorded.
  - An overdue loan can't be extended, so once a loan is overdue it stays overdue until it is
    returned.
- **INV-MYL-5:** Once an overdue notice is sent for a loan, My Loans shows that the borrower was
  notified.
- **INV-MYL-6:** A returned loan drops off My Loans.
  - My Loans shows only what the neighbor has out now. It keeps no borrowing history.
- **INV-MYL-7:** Each loan shows the tool, the checkout date, the due date, whether it is
  overdue, and whether an overdue notice has been sent.
  - There is no "due soon" state. Overdue is the only state a loan shows besides on loan.
- **INV-MYL-8:** A loan's entry changes only through its own checkout, extension, overdue notice
  and return, and through holds placed on or ended for its tool.
  - That is: Tool Checked Out, Loan Extended, Overdue Notice Sent, Tool Returned, Hold Placed,
    Hold Cancelled, Tool Retired, and Neighbor Account Closed of a holder.
- **INV-MYL-9:** While any hold is waiting on a loan's tool, My Loans shows that the loan can't
  be extended because someone is waiting.
  - A hold on a tool out on loan is waiting from Hold Placed until it is cancelled, or ended
    because the tool was retired or the holder closed their account. When no hold is waiting
    any more, the loan can be extended again.
- **INV-MYL-10:** An overdue loan, and a loan whose tool has been retired, are marked as not
  extendable.
  - A loan on a retired tool stays on My Loans until it is returned, like any other loan.
  - Suspension is not shown on My Loans. A suspended neighbor sees it on My Account, and an
    extension they try is refused with that reason.

## Scenarios (Given / When / Then)
- **Tool checked out**
  - **Given:** Ana has no loans
  - **When:** Tool Checked Out is recorded on 5 October for Ana borrowing a cordless drill (default
    checkout period 7 days), due 12 October
  - **Then:** Ana's My Loans lists the cordless drill, checked out 5 October, due 12 October, not
    overdue.
- **Loan extended**
  - **Given:** Ana's My Loans lists the cordless drill, due 12 October
  - **When:** Loan Extended is recorded for that loan, adding the drill's 7-day default period
  - **Then:** Ana's My Loans lists the cordless drill, due 19 October.
- **Loan becomes overdue**
  - **Given:** Ana's My Loans lists the cordless drill, due 12 October
  - **When:** it is 13 October, and the drill has not been returned
  - **Then:** Ana's My Loans shows the cordless drill as overdue and not extendable. Nothing is
    recorded.
- **Overdue notice sent**
  - **Given:** Ana's My Loans shows the cordless drill as overdue
  - **When:** Overdue Notice Sent is recorded for that loan
  - **Then:** Ana's My Loans shows the cordless drill as overdue, with a note that she has been
    notified.
- **Tool returned**
  - **Given:** Ana's My Loans lists the cordless drill and a ladder
  - **When:** Tool Returned is recorded for the cordless drill
  - **Then:** the cordless drill drops off Ana's My Loans, and the ladder is unchanged.
- **Someone places a hold on the borrowed tool**
  - **Given:** Ana's My Loans lists the cordless drill, due 12 October, with no hold waiting
  - **When:** Hold Placed is recorded for Ben on the drill
  - **Then:** Ana's My Loans shows the drill, due 12 October, and that it can't be extended
    because someone is waiting.
- **The waiting hold is cancelled**
  - **Given:** Ana's My Loans shows the drill can't be extended because Ben's hold is waiting,
    and no other hold is waiting
  - **When:** Hold Cancelled is recorded for Ben's hold
  - **Then:** Ana's My Loans no longer shows that someone is waiting, and the loan can be
    extended.
- **The waiting holder closes their account**
  - **Given:** Ana's My Loans shows the drill can't be extended because Ben's hold is waiting,
    and no other hold is waiting
  - **When:** Neighbor Account Closed is recorded for Ben
  - **Then:** Ana's My Loans no longer shows that someone is waiting.
- **Tool retired while on loan**
  - **Given:** Ana's My Loans lists the cordless drill, due 12 October
  - **When:** Tool Retired is recorded for the drill
  - **Then:** Ana's My Loans still lists the drill, due 12 October, marked as not extendable.
- **Other neighbors' loans**
  - **Given:** Ana has the cordless drill on loan
  - **When:** Tool Checked Out is recorded for Ben borrowing a ladder
  - **Then:** Ben's My Loans lists the ladder, and Ana's My Loans is unchanged.

## Open Questions
None.
