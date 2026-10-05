---
schemaVersion: 1
pattern: state-change
swimlane: Shed Manager → Tools
status: draft
version: 1
---
# Slice: Retire Tool

![Diagram](./retire-tool.svg)

## Intent
Take a tool out of the shed for good: a damaged tool, one the shed no longer keeps, or one added
with a mistake. The shed manager retires it from Tool Inventory, and neighbors can no longer
borrow or hold it.

## Invariants / Business Rules
- **INV-RTR-1:** Only the shed manager can retire a tool, from the Tool Inventory screen.
- **INV-RTR-2:** A tool that is already retired can't be retired again.
- **INV-RTR-3:** Retiring is final. A retired tool is never lent or held again.
  - It drops off Tool Inventory and Tool Catalog. A tool retired to correct a mistake in its
    details is added again as a new tool.
- **INV-RTR-4:** Retiring a tool ends every hold on it, waiting or set aside.
  - The ended holds drop off their holders' My Holds. No hold is passed on, and nothing is left
    for the hold fulfiller or the pickup notice gateway to act on.
- **INV-RTR-5:** Nothing is recorded about the tool's condition or the reason it was retired.
  - A damaged tool is the shed manager's call to retire.

## Scenarios (Given / When / Then)
- **Happy path**
  - **Given:** the ladder is in the shed, available, with no holds on it
  - **When:** the shed manager retires the ladder from Tool Inventory
  - **Then:** Tool Retired is recorded for the ladder. It drops off Tool Inventory and Tool
    Catalog.
- **Set-aside tool with another hold waiting**
  - **Given:** the cordless drill is set aside for Ben, and Cara's hold on it is waiting
  - **When:** the shed manager retires the drill
  - **Then:** Tool Retired is recorded for the drill. Both Ben's and Cara's holds end and drop off
    their My Holds. The drill is not passed to Cara, and drops off Tool Inventory and Tool
    Catalog.

## Open Questions
- [ ] Can a tool be retired while it is on loan?
  - (a) Yes. The loan carries on: the borrower can't extend it, and returns the tool as usual,
    after which it stays out of the catalog. Retiring ends the holds on it straight away.
    (b) No. Retire is refused until the tool has been returned.
  - Recommendation: (a). The shed manager may learn a tool is broken or lost while it is out, and
    holders shouldn't keep waiting for it. Extend Loan and My Loans are already written for (a).
    Under (b), they lose their retired-tool rules and Return Tool's question falls away.
- [ ] If a borrower loses or breaks a tool and can't return it, how does their loan end?
  - Nothing in the model ends a loan except Tool Returned. A loan for a tool that never comes
    back stays out and, once past its due date, overdue for good. That blocks the borrower from
    every checkout and pickup, permanently.
  - Options: (a) the shed manager records the tool as lost against the loan, which ends the loan
    and retires the tool (a new event and slice); (b) the shed manager records a return on the
    borrower's behalf, then retires the tool, though returns were ruled to be the neighbor's own
    action; (c) the borrower stays blocked until the shed manager deals with it outside the
    system.
  - Recommendation: (a). It records what actually happened, and frees the borrower, without
    pretending the tool came back.
- [ ] Are holders told when a tool they hold is retired?
  - Their hold simply drops off My Holds.
  - Recommendation: no message, the same as an expired hold. If the shed wants holders told, that
    is a new email and event.
