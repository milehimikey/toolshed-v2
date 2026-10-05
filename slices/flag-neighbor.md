---
schemaVersion: 1
pattern: automation
swimlane: System → Neighbors
status: draft
version: 1
---
# Slice: Flag Neighbor

![Diagram](./flag-neighbor.svg)

## Intent
When a neighbor's late returns reach the late-return threshold, record that they crossed it, so
the shed manager sees them on Flagged Neighbors and decides whether to suspend them. The system
flags; a person decides.

## Invariants / Business Rules
- **INV-FLG-1:** Neighbors are flagged by the system, never by a person.
  - The late-return flagger acts on each neighbor on Neighbors To Flag.
- **INV-FLG-2:** A neighbor is flagged when they have 3 late returns within the last 90 days
  that have not yet counted toward a flag.
  - A return is late when it happens after the loan's due date in force. The 90 days are a
    rolling window.
  - A tool recorded lost never counts as a late return.
- **INV-FLG-3:** Flagging never suspends anyone.
  - The shed manager decides whether to suspend a flagged neighbor. Reaching the threshold never
    suspends a neighbor by itself.
- **INV-FLG-4:** A flag uses up the late returns that caused it.
  - Neighbor Flagged names the late returns, by loan, that it uses up: every late return that
    counted toward the threshold when the flag was recorded. It carries the loans, not copies of
    names or dates.
  - The neighbor is flagged again only after 3 new late returns within 90 days.
- **INV-FLG-5:** A neighbor is flagged only while they are still due a flag.
  - A neighbor whose account is closed is never flagged, and nor is a suspended neighbor. A
    neighbor whose count was reset by reinstatement after they joined Neighbors To Flag is not
    flagged for those late returns.
- **INV-FLG-6:** Once flagged, the neighbor appears on Flagged Neighbors and stays there until the
  shed manager acts.
  - There is no dismiss action. A flag the shed manager doesn't act on stays on Flagged
    Neighbors.
- **INV-FLG-7:** A neighbor flagged again while an earlier flag is still waiting on Flagged
  Neighbors gets a second Neighbor Flagged.
  - Flagged Neighbors keeps one entry for the neighbor, showing how many times they have been
    flagged and the date of the latest flag.
- **INV-FLG-8:** The neighbor isn't told they have been flagged.
  - A flag is a prompt for the shed manager, not a penalty.

## Scenarios (Given / When / Then)
- **Happy path**
  - **Given:** Ana returned tools late on 1 September, 20 September and 5 October, none of which
    have counted toward a flag, so she is on Neighbors To Flag
  - **When:** the late-return flagger acts on Ana
  - **Then:** Neighbor Flagged is recorded for Ana, naming the three loans returned late, which
    are used up. Ana
    leaves Neighbors To Flag and appears on the shed manager's Flagged Neighbors.
- **Flagged again on new late returns**
  - **Given:** Ana was flagged on 5 October, and the shed manager hasn't acted on it. Since then
    she has returned tools late on 20 October, 1 November and 15 November, so she is on
    Neighbors To Flag again.
  - **When:** the late-return flagger acts on Ana
  - **Then:** Neighbor Flagged is recorded for Ana again, using up the three new late returns.
    Flagged Neighbors still shows one entry for Ana: flagged twice, latest on 15 November.

## Open Questions
None.
