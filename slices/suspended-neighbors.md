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
  for them, whether they were suspended from Flagged Neighbors or from the Neighbors screen.
- **INV-SPN-2:** Each entry shows the neighbor's name and email and the date they were
  suspended.
  - The name comes from Neighbor Registered. The email is the neighbor's current one: Neighbor
    Registered sets it, Neighbor Email Changed replaces it. Suspended Neighbors hears both for
    that purpose.
  - A suspended neighbor can't change their email. The email shown is the one they had when
    they were suspended, and it changes only after they are reinstated, by which time they have
    left the list.
  - No reason or note is recorded with a suspension, so none is shown.
- **INV-SPN-3:** A suspended neighbor stays on the list until they are reinstated or close their
  account.
  - A suspension never lifts by itself after a set time, and it is never permanent.
- **INV-SPN-4:** When the shed manager reinstates a neighbor, the neighbor leaves the list.
  - Reinstating is the only action this screen offers.
- **INV-SPN-5:** When a suspended neighbor closes their account, they leave the list.
  - Closed is final, so the shed manager is never offered Reinstate on a closed account.
- **INV-SPN-6:** A neighbor appears on the list at most once.
  - A neighbor who is reinstated and later suspended again appears again.
- **INV-SPN-7:** Only the shed manager sees Suspended Neighbors.
- **INV-SPN-8:** Only Neighbor Suspended, Neighbor Reinstated and Neighbor Account Closed change
  Suspended Neighbors. Neighbor Registered and Neighbor Email Changed supply names and emails.

## Scenarios (Given / When / Then)
- **Neighbor suspended**
  - **Given:** Suspended Neighbors is empty
  - **When:** Neighbor Suspended is recorded for Ana Ruiz (ana@example.org) on 7 October
  - **Then:** Suspended Neighbors lists Ana Ruiz, ana@example.org, suspended 7 October, with a
    Reinstate action.
- **Neighbor reinstated**
  - **Given:** Suspended Neighbors lists Ana and Ben
  - **When:** Neighbor Reinstated is recorded for Ana
  - **Then:** Ana leaves Suspended Neighbors. Ben is still listed.
- **Suspended neighbor closes their account**
  - **Given:** Suspended Neighbors lists Ana
  - **When:** Neighbor Account Closed is recorded for Ana
  - **Then:** Ana leaves Suspended Neighbors.
- **Suspended again after reinstatement**
  - **Given:** Ana was suspended on 7 October and reinstated on 1 November
  - **When:** Neighbor Suspended is recorded for Ana again on 3 February
  - **Then:** Suspended Neighbors lists Ana once, suspended 3 February.

## Open Questions
None.
