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
- **INV-FLG-3:** Flagging never suspends anyone.
  - The shed manager decides whether to suspend a flagged neighbor. Automatic suspension at the
    threshold was turned down.
- **INV-FLG-4:** A flag uses up the late returns that caused it.
  - The neighbor is flagged again only after 3 new late returns within 90 days.
- **INV-FLG-5:** A neighbor is flagged only while they are still due a flag.
  - A neighbor whose account is closed is never flagged. A neighbor whose count was reset by
    reinstatement after they joined Neighbors To Flag is not flagged for those late returns.
- **INV-FLG-6:** Once flagged, the neighbor appears on Flagged Neighbors and stays there until the
  shed manager acts.
  - There is no dismiss action. A flag the shed manager doesn't act on stays on Flagged
    Neighbors.

## Scenarios (Given / When / Then)
- **Happy path**
  - **Given:** Ana returned tools late on 1 September, 20 September and 5 October, none of which
    have counted toward a flag, so she is on Neighbors To Flag
  - **When:** the late-return flagger acts on Ana
  - **Then:** Neighbor Flagged is recorded for Ana, and the three late returns are used up. Ana
    leaves Neighbors To Flag and appears on the shed manager's Flagged Neighbors.
- **Flagged again on new late returns**
  - **Given:** Ana was flagged on 5 October, and since then has returned tools late on
    20 October, 1 November and 15 November, so she is on Neighbors To Flag again
  - **When:** the late-return flagger acts on Ana
  - **Then:** Neighbor Flagged is recorded for Ana again, using up the three new late returns.

## Open Questions
- [ ] Can a suspended neighbor be flagged?
  - A suspended neighbor can still return tools late. Their only possible flag would put them on
    Flagged Neighbors, whose only action is to suspend them, and reinstatement resets their count
    anyway. The same question is open on Neighbors To Flag.
  - Recommendation: never flag a suspended neighbor.
- [ ] If a neighbor is flagged again while an earlier flag is still waiting on Flagged Neighbors,
  what does the shed manager see?
  - Options: (a) a second flag is recorded, and Flagged Neighbors keeps one entry for the
    neighbor showing they were flagged again; (b) no new flag while one is still waiting, and
    their late returns keep counting.
  - Recommendation: (a). Repeated late returns are the signal the shed manager needs, and
    recording each flag keeps that history.
- [ ] Is the neighbor told they have been flagged?
  - Recommendation: no. A flag is a prompt for the shed manager, not a penalty. The neighbor
    hears from the shed only if they are suspended.
