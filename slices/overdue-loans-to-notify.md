---
schemaVersion: 1
pattern: state-view
swimlane: System → Loans
status: draft
version: 1
---
# Slice: Overdue Loans To Notify

![Diagram](./overdue-loans-to-notify.svg)

## Intent
The list of overdue loans whose borrowers have not yet been sent an overdue notice. The overdue
notice gateway works through it, so each borrower with an overdue loan is emailed once.

## Invariants / Business Rules
- **INV-OLN-1:** A loan is on the list from the start of the day after its due date, in the
  shed's local time, while the tool is still out and no overdue notice has been sent for it.
  - Overdue is worked out by comparing the due date with today. No event marks a loan overdue;
    the loan joins the list because the date has passed.
- **INV-OLN-2:** A loan's due date is the one set at checkout, moved by each extension.
  - A loan extended before it became overdue joins the list only once its new due date has
    passed.
- **INV-OLN-3:** Once an overdue notice is sent for a loan, the loan leaves the list and never
  comes back.
  - One notice per loan. An overdue loan can't be extended, so it stays overdue until it is
    returned, and no second notice goes out.
- **INV-OLN-4:** When the tool is returned, its loan leaves the list.
  - A loan returned late, before its notice went out, never gets a notice.
- **INV-OLN-5:** The list has no screen. Only the overdue notice gateway reads it.
- **INV-OLN-6:** Each loan on the list carries everything the notice needs: the borrower's name
  and email, the tool's name, and the due date.
  - The borrower's name and email come from Neighbor Registered, and the tool's name from Tool
    Added. The gateway looks nothing up elsewhere.
- **INV-OLN-7:** A loan is ready for its notice as soon as it joins the list. There are no quiet
  hours.
- **INV-OLN-8:** A notice that later fails to deliver doesn't put the loan back on the list.
  - The failure is recorded as Overdue Notice Failed and shown to the shed manager on
    Undelivered Notices. No second notice goes out for that loan.

## Scenarios (Given / When / Then)
- **Loan not yet overdue**
  - **Given:** Tool Checked Out was recorded for Ana borrowing a cordless drill, due 12 October
  - **When:** it is 12 October
  - **Then:** the drill loan is not on the list.
- **Loan becomes overdue**
  - **Given:** Ana's drill loan is due 12 October and the drill has not been returned
  - **When:** it is the start of 13 October in the shed's local time
  - **Then:** the drill loan is on the list, ready for its notice, carrying Ana's name and
    email, "cordless drill", and the due date 12 October.
- **Overdue notice sent**
  - **Given:** Ana's overdue drill loan is on the list
  - **When:** Overdue Notice Sent is recorded for that loan
  - **Then:** the drill loan leaves the list. It stays off the list for as long as the drill is
    still out.
- **Extended before it became overdue**
  - **Given:** Ana's drill loan was due 12 October
  - **When:** Loan Extended is recorded on 10 October, moving the due date to 19 October
  - **Then:** the drill loan is not on the list on 13 October, and joins it on 20 October if
    the drill is still out.
- **Returned before the notice**
  - **Given:** Ana's overdue drill loan is on the list
  - **When:** Tool Returned is recorded for the drill before any notice is sent
  - **Then:** the drill loan leaves the list, and no notice is sent for it.

- **Notice fails to deliver**
  - **Given:** Overdue Notice Sent has been recorded for Ana's drill loan, so it is off the list
  - **When:** Overdue Notice Failed is recorded for that notice
  - **Then:** the drill loan stays off the list.

## Open Questions
- [ ] If a borrower closes their account while a loan is overdue and no notice has been sent, is
  the notice still sent?
  - This depends on whether Close Account allows closing with tools still out.
  - Recommendation: if closing with tools out is allowed, still send the notice, because the
    tool is still owed.
