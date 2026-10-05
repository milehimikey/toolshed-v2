---
schemaVersion: 1
pattern: state-change
swimlane: Neighbor → Loans
status: draft
version: 1
---
# Slice: Return Tool

![Diagram](./return-tool.svg)

## Intent
Let a borrower bring a tool back and end their loan themselves, from My Loans. The tool is then
back in the shed, for the next holder or for anyone to borrow.

## Invariants / Business Rules
- **INV-RET-1:** The borrower returns their own loan themselves, from My Loans.
  - The shed manager does not record returns.
- **INV-RET-2:** Only a loan that is still out can be returned, and only once.
- **INV-RET-3:** Returning a tool records only Tool Returned.
  - Setting the tool aside for the first waiting hold is a separate system reaction to the
    return, recorded as Hold Fulfilled.
- **INV-RET-4:** A return is late when it happens after the loan's due date.
  - A return on the due date itself is on time. The due date is the one in force at the time,
    after any extensions.
  - Late returns count toward a neighbor being flagged.
- **INV-RET-5:** An overdue loan can be returned.
  - Once it is returned, no overdue notice goes out for it.
- **INV-RET-6:** A returned tool with no holds waiting is available to borrow straight away.
  - A returned tool with holds waiting is never shown as available. Until Hold Fulfilled is
    recorded, only the neighbor whose hold is at the front of the queue may check it out.
- **INV-RET-7:** A suspended neighbor can return a tool.
- **INV-RET-8:** Nothing about the tool's condition is recorded on return.
  - A damaged tool is the shed manager's to retire.

## Scenarios (Given / When / Then)
- **Happy path: on-time return, no holds**
  - **Given:** Ana has the cordless drill on loan, due 12 October, and no holds are waiting on it
  - **When:** on 11 October, Ana returns the drill from My Loans
  - **Then:** Tool Returned is recorded for the loan, on time. The drill drops off Ana's My
    Loans, and the Tool Catalog shows it available.
- **Return on the due date**
  - **Given:** Ana's drill loan is due 12 October
  - **When:** Ana returns the drill on 12 October
  - **Then:** Tool Returned is recorded, on time.
- **Return with a hold waiting**
  - **Given:** Ana has the drill on loan, and Ben's hold on it is at the front of the queue
  - **When:** Ana returns the drill
  - **Then:** Tool Returned is recorded. The drill drops off Ana's My Loans, and the Tool Catalog
    does not show it as available. The hold reaction then sets the drill aside for Ben.
- **Late return**
  - **Given:** Ana's drill loan was due 12 October, and Overdue Notice Sent has been recorded for
    it
  - **When:** Ana returns the drill on 15 October
  - **Then:** Tool Returned is recorded, late. The drill drops off Ana's My Loans, and the late
    return counts toward Ana being flagged.
- **Late return before the notice went out**
  - **Given:** Ana's drill loan was due 12 October, and no overdue notice has been sent
  - **When:** Ana returns the drill on 13 October
  - **Then:** Tool Returned is recorded, late, and no overdue notice is sent for the loan.
- **Suspended neighbor returns a tool**
  - **Given:** Ana is suspended and has the drill on loan
  - **When:** Ana returns the drill from My Loans
  - **Then:** Tool Returned is recorded, and the drill drops off Ana's My Loans.

## Open Questions
- [ ] Can a neighbor whose account is closed still return a tool they have out?
  - This depends on whether Close Account allows closing with tools still out. After closing,
    the neighbor can only view My Account.
  - Recommendation: if closing with tools out is allowed, the neighbor can still return them.
    Otherwise the question doesn't arise.
- [ ] If the tool was retired while it was on loan, what happens when it comes back?
  - This depends on whether a tool on loan can be retired at all, which Retire Tool decides.
  - Recommendation: the return is recorded as usual and the tool doesn't come back into the
    catalog. Retiring it already ended its holds, so nobody is waiting.
