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
- **INV-MYL-4:** A loan shows as overdue from the start of the day after its due date, in the
  shed's local time, and not before.
  - Overdue is worked out by comparing the due date with today. No event marks a loan overdue,
    so the screen shows it overdue on the day after the due date with nothing recorded.
  - An overdue loan can't be extended, so once a loan is overdue it stays overdue until it is
    returned.
- **INV-MYL-5:** Once an overdue notice is sent for a loan, My Loans shows that the borrower was
  notified.
  - If the notice later fails to deliver, My Loans shows instead that the overdue notice couldn't
    be delivered.
- **INV-MYL-6:** A returned loan drops off My Loans, and so does a loan ended by the shed manager
  recording the tool lost.
  - My Loans shows only what the neighbor has out now. It keeps no borrowing history.
- **INV-MYL-7:** Each loan shows the tool, the checkout date, the due date, whether it is
  overdue, and whether an overdue notice has been sent or couldn't be delivered.
  - There is no "due soon" state. Overdue is the only state a loan shows besides on loan.
- **INV-MYL-8:** A loan's entry changes only through its own checkout, extension, overdue notice,
  return or loss, and through holds starting or ending on its tool.
  - That is: Tool Checked Out, Loan Extended, Overdue Notice Sent, Overdue Notice Failed, Tool
    Returned, Tool Lost, Hold Placed, Hold Cancelled, Hold Expired, Tool Retired, and Neighbor
    Account Closed of a holder.
  - The tool's name comes from Tool Added.
- **INV-MYL-9:** While any hold is waiting on a loan's tool, My Loans shows that the loan can't
  be extended because someone is waiting.
  - A hold is waiting from Hold Placed until it ends. A hold ends when it is cancelled or
    expires, when the holder checks the tool out, when the tool is retired or recorded lost, or
    when the holder closes their account.
  - This includes holds that were already waiting when the loan began. When no hold is waiting
    any more, the loan can be extended again.
- **INV-MYL-10:** An overdue loan, and a loan whose tool has been retired, are marked as not
  extendable.
  - A tool can be retired while it is on loan. The loan stays on My Loans until it is returned,
    like any other loan.
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
- **Overdue notice couldn't be delivered**
  - **Given:** Ana's My Loans shows the cordless drill as overdue, with a note that she has been
    notified
  - **When:** Overdue Notice Failed is recorded for that notice
  - **Then:** Ana's My Loans shows the cordless drill as overdue, with a note that the overdue
    notice couldn't be delivered.
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
- **Tool recorded lost**
  - **Given:** Ana's My Loans lists the ladder, overdue
  - **When:** Tool Lost is recorded for the ladder
  - **Then:** the ladder drops off Ana's My Loans.
- **Loan starts with a hold already waiting**
  - **Given:** the drill was returned with Ben's hold at position 1 and Cara's at position 2.
    It was set aside for Ben.
  - **When:** Tool Checked Out is recorded for Ben collecting the drill
  - **Then:** Ben's hold ends with his checkout. Ben's My Loans lists the drill and shows it
    can't be extended, because Cara's hold is still waiting.
- **Next loan after an expired hold**
  - **Given:** Ben placed a hold on the drill while Ana had it. Ana returned it, it was set aside
    for Ben, and Hold Expired was recorded for Ben's hold. No other hold is waiting.
  - **When:** Tool Checked Out is recorded for Dan borrowing the drill
  - **Then:** Dan's My Loans lists the drill with no hold waiting, and the loan can be extended.
- **Other neighbors' loans**
  - **Given:** Ana has the cordless drill on loan
  - **When:** Tool Checked Out is recorded for Ben borrowing a ladder
  - **Then:** Ben's My Loans lists the ladder, and Ana's My Loans is unchanged.

## Open Questions
None.
