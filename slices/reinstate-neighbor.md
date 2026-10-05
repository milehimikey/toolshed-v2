---
schemaVersion: 1
pattern: state-change
swimlane: Shed Manager → Neighbors
status: draft
version: 1
---
# Slice: Reinstate Neighbor

![Diagram](./reinstate-neighbor.svg)

## Intent
Let the shed manager lift a neighbor's suspension, so the neighbor can borrow again with a clean
late-return record.

## Invariants / Business Rules
- **INV-REI-1:** Only the shed manager reinstates a neighbor, from Suspended Neighbors.
  - A suspension never lifts by itself after a set time.
- **INV-REI-2:** Only a suspended neighbor can be reinstated.
  - An active neighbor has nothing to lift. A neighbor whose account is closed can't be
    reinstated, because closed is final, even if they were suspended when they closed it.
- **INV-REI-3:** A reinstated neighbor is active again.
  - They can check out tools, extend loans, place holds and collect set-aside tools, subject to
    the usual rules.
  - A neighbor can be reinstated while they still have an overdue loan. The overdue loan goes on
    blocking checkouts and pickups until the tool is returned.
  - Holds they kept while suspended carry on from their place in the queue.
- **INV-REI-4:** Reinstatement starts the neighbor's late-return count again from zero.
  - Late returns from before the reinstatement never count toward a later flag.
- **INV-REI-5:** Once reinstated, the neighbor leaves Suspended Neighbors. The Neighbors screen
  and the neighbor's own My Account show them as active.
- **INV-REI-6:** The neighbor isn't emailed about the reinstatement, and no reason or note is
  recorded with it.
  - My Account shows them as active again.

## Scenarios (Given / When / Then)
- **Happy path**
  - **Given:** Ana is suspended and listed on Suspended Neighbors. She has two late returns within
    the last 90 days, and a hold waiting at position 2 on the cordless drill.
  - **When:** the shed manager reinstates Ana from Suspended Neighbors
  - **Then:** Neighbor Reinstated is recorded for Ana. She leaves Suspended Neighbors, and the
    Neighbors screen and her My Account show her as active. Her late-return count starts again
    from zero, and her hold on the drill is still at position 2.
- **Borrowing again after reinstatement**
  - **Given:** Ana was reinstated and has no overdue loans
  - **When:** Ana checks out the hedge trimmer from the Tool Catalog
  - **Then:** Tool Checked Out is recorded for Ana.

## Open Questions
None.
