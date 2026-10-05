---
schemaVersion: 1
pattern: state-change
swimlane: Shed Manager → Tools
status: draft
version: 1
---
# Slice: Record Tool Lost

![Diagram](./record-tool-lost.svg)

## Intent
Let the shed manager record that a tool out on loan is lost or broken and won't come back. Doing
so ends the loan, so the borrower isn't left blocked by a tool they can't return, and takes the
tool out of the shed for good.

## Invariants / Business Rules
- **INV-LOST-1:** Only the shed manager records a tool lost, from Tool Inventory.
  - The borrower doesn't record it, and the shed manager doesn't record a return on the
    borrower's behalf instead.
- **INV-LOST-2:** A tool can be recorded lost only while it is out on loan, and the loss is
  recorded against that loan.
- **INV-LOST-3:** Recording a tool lost ends its loan.
  - The loan drops off the borrower's My Loans and leaves Overdue Loans To Notify, so no overdue
    notice goes out for it afterwards. An overdue loan ended this way no longer blocks the
    borrower's checkouts and pickups.
  - Any undelivered overdue notice for the loan drops off Undelivered Notices.
- **INV-LOST-4:** A lost tool never counts as a late return, even if its loan was overdue.
- **INV-LOST-5:** Recording a tool lost retires it.
  - It drops off Tool Inventory and the Tool Catalog, and can't be borrowed or held again.
- **INV-LOST-6:** Recording a tool lost ends every hold on it.
  - The holds drop off their holders' My Holds. As for any retired tool, the holders aren't
    told.

## Scenarios (Given / When / Then)
- **Happy path**
  - **Given:** Ana has the ladder on loan, due 12 October, and it is overdue. Ben has a hold
    waiting on the ladder.
  - **When:** on 20 October the shed manager records the ladder lost from Tool Inventory
  - **Then:** Tool Lost is recorded against Ana's ladder loan. The ladder drops off Tool
    Inventory and the Tool Catalog, and off Ana's My Loans. Ana's late-return count is
    unchanged, and her overdue ladder no longer blocks her from borrowing. Ben's hold ends and
    drops off his My Holds.
- **Lost before the due date**
  - **Given:** Ana has the hedge trimmer on loan, due 25 October, and tells the shed manager it
    broke beyond repair
  - **When:** the shed manager records the hedge trimmer lost from Tool Inventory
  - **Then:** Tool Lost is recorded against Ana's loan, which ends. The hedge trimmer drops off
    Tool Inventory and the Tool Catalog.

## Open Questions
- [ ] Can a tool be recorded lost before its loan is overdue?
  - The second scenario assumes yes. A borrower who breaks a tool would then not have to wait
    for the due date.
  - Recommendation: yes, any time while it is out.
- [ ] Is the borrower told, by email or on screen, that their loan was ended because the tool
  was recorded lost?
  - Recommendation: no email. The loan simply drops off My Loans, and the shed manager will
    usually have talked to them already.
- [ ] Is anything recorded about cost or replacement, or a note on what happened?
  - Recommendation: no. Nothing on record asks for charges or notes. A replacement is added as
    a new tool.
- [ ] If a tool recorded lost turns up later, how does it come back?
  - Recommendation: the shed manager adds it again as a new tool, the same way a mistake in a
    tool's details is corrected.
