---
schemaVersion: 1
pattern: state-view
swimlane: Shed Manager → Neighbors
status: draft
version: 1
---
# Slice: Suspended Neighbors

![Diagram](./suspended-neighbors.svg)

## Intent
Shows the shed manager every neighbor who is currently suspended, on the Suspended Neighbors
screen they reinstate neighbors from.

## Invariants / Business Rules
- **INV-SPN-1:** A neighbor appears on Suspended Neighbors when Neighbor Suspended is recorded
  for them.
- **INV-SPN-2:** A suspended neighbor stays on the list until the shed manager reinstates them.
  - A suspension never lifts by itself after a set time, and it is never permanent.
- **INV-SPN-3:** When the shed manager reinstates a neighbor, the neighbor leaves the list.
  - Reinstating is the only action this screen offers.
- **INV-SPN-4:** A neighbor appears on the list at most once.
  - A neighbor who is reinstated and later suspended again appears again.
- **INV-SPN-5:** Only the shed manager sees Suspended Neighbors.
- **INV-SPN-6:** Only Neighbor Suspended and Neighbor Reinstated change Suspended Neighbors.

## Scenarios (Given / When / Then)
- **Neighbor suspended**
  - **Given:** Suspended Neighbors is empty
  - **When:** Neighbor Suspended is recorded for Ana on 7 October
  - **Then:** Suspended Neighbors lists Ana, suspended 7 October, with a Reinstate action.
- **Neighbor reinstated**
  - **Given:** Suspended Neighbors lists Ana and Ben
  - **When:** Neighbor Reinstated is recorded for Ana
  - **Then:** Ana leaves Suspended Neighbors. Ben is still listed.
- **Suspended again after reinstatement**
  - **Given:** Ana was suspended on 7 October and reinstated on 1 November
  - **When:** Neighbor Suspended is recorded for Ana again on 3 February
  - **Then:** Suspended Neighbors lists Ana once, suspended 3 February.

## Open Questions
- [ ] What happens to a suspended neighbor who closes their account?
  - A suspended neighbor may close their account, and closed is final. As modeled, Suspended
    Neighbors doesn't hear Neighbor Account Closed, so the neighbor would stay listed forever,
    with a Reinstate action for an account that is closed.
  - Recommendation: they drop off the list when the account is closed, the same as a closed
    account drops off Flagged Neighbors. Suspended Neighbors would then also hear Neighbor
    Account Closed, which is a model change.
- [ ] What does the shed manager see for each suspended neighbor?
  - Recommendation: the neighbor's name and email and the date they were suspended. Whether to
    also show the flag and late returns that led to it follows the same question on Flagged
    Neighbors.
