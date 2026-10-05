---
schemaVersion: 1
pattern: state-view
swimlane: Shed Manager → Neighbors
status: draft
version: 1
---
# Slice: Neighbors

![Diagram](./neighbors.svg)

## Intent
Shows the shed manager every neighbor who has signed up, with each neighbor's current status:
active, suspended, or closed.

## Invariants / Business Rules
- **INV-NBRS-1:** Every account appears on the Neighbors screen exactly once.
  - One entry per Neighbor Registered; nothing else creates an entry. A neighbor who signs up
    again after closing their account has a new account, so they appear twice: the closed
    entry and the new one.
- **INV-NBRS-2:** Each neighbor's status is active, suspended, or closed, set by the latest of
  these events for that account.
  - Neighbor Registered → active. Neighbor Suspended → suspended. Neighbor Reinstated →
    active. Neighbor Account Closed → closed.
- **INV-NBRS-3:** A neighbor whose account is closed stays listed, shown as closed.
  - This screen is where the shed manager sees account closures.
- **INV-NBRS-4:** Closed is final.
  - A suspended neighbor who closes their account shows as closed, not suspended.
- **INV-NBRS-5:** Each entry shows the neighbor's name, email, date of sign-up, and status.

## Scenarios (Given / When / Then)
- **Neighbor registered**
  - **Given:** no neighbors have signed up
  - **When:** Neighbor Registered is recorded for Ana, ana@example.com, on 5 October 2026
  - **Then:** the Neighbors screen lists Ana with her email, the 5 October 2026 sign-up date,
    and the status active.
- **Neighbor suspended**
  - **Given:** the Neighbors screen lists Ana as active
  - **When:** Neighbor Suspended is recorded for Ana
  - **Then:** the Neighbors screen shows Ana as suspended. Other neighbors are unchanged.
- **Neighbor reinstated**
  - **Given:** the Neighbors screen shows Ana as suspended
  - **When:** Neighbor Reinstated is recorded for Ana
  - **Then:** the Neighbors screen shows Ana as active again.
- **Account closed**
  - **Given:** the Neighbors screen lists Ana as active
  - **When:** Neighbor Account Closed is recorded for Ana
  - **Then:** the Neighbors screen still lists Ana, now shown as closed.
- **Suspended neighbor closes their account**
  - **Given:** the Neighbors screen shows Ana as suspended
  - **When:** Neighbor Account Closed is recorded for Ana
  - **Then:** the Neighbors screen shows Ana as closed.
- **Signing up again after closing**
  - **Given:** the Neighbors screen shows Ana's account as closed
  - **When:** Neighbor Registered is recorded for a new account of Ana's
  - **Then:** the Neighbors screen lists two entries for Ana. The old account is still shown as
    closed, and the new one as active.

## Open Questions
None.
