---
schemaVersion: 1
pattern: state-view
swimlane: Shed Manager → Neighbors
status: draft
version: 1
---
# Slice: Flagged Neighbors

![Diagram](./flagged-neighbors.svg)

## Intent
Shows the shed manager every neighbor the system has flagged for repeated late returns, on the
Flagged Neighbors screen they suspend neighbors from. The system flags; the shed manager decides.

## Invariants / Business Rules
- **INV-FLN-1:** A neighbor appears on Flagged Neighbors when Neighbor Flagged is recorded for
  them.
  - A neighbor is flagged by the system after 3 late returns within 90 days. Being on this list
    never suspends anyone by itself.
- **INV-FLN-2:** A flagged neighbor stays on the list until the shed manager suspends them.
  - There is no dismiss action. A flag the shed manager decides not to act on stays on the list.
- **INV-FLN-3:** When the shed manager suspends a flagged neighbor, the neighbor leaves the list.
  - Suspending is the only action this screen offers.
- **INV-FLN-4:** Only the shed manager sees Flagged Neighbors.
- **INV-FLN-5:** Only Neighbor Flagged and Neighbor Suspended change Flagged Neighbors.

## Scenarios (Given / When / Then)
- **Neighbor flagged**
  - **Given:** Flagged Neighbors is empty
  - **When:** Neighbor Flagged is recorded for Ana on 5 October
  - **Then:** Flagged Neighbors lists Ana, flagged 5 October, with a Suspend action.
- **Flagged neighbor suspended**
  - **Given:** Flagged Neighbors lists Ana and Ben
  - **When:** Neighbor Suspended is recorded for Ana
  - **Then:** Ana leaves Flagged Neighbors. Ben is still listed.
- **Flag left alone**
  - **Given:** Flagged Neighbors has listed Ana since 5 October, and the shed manager has not
    suspended her
  - **When:** a month passes with no Neighbor Suspended recorded for Ana
  - **Then:** Ana is still listed.

## Open Questions
- [ ] What happens to a flagged neighbor who closes their account before the shed manager acts?
  - As modeled, Flagged Neighbors doesn't hear Neighbor Account Closed. The neighbor would stay
    on the list forever, with a Suspend action for an account that is closed and final.
  - Recommendation: they drop off the list when the account is closed. Flagged Neighbors would
    then also hear Neighbor Account Closed, which is a model change.
- [ ] What does the shed manager see for each flagged neighbor?
  - Recommendation: the neighbor's name and email, the date they were flagged, and the late
    returns that caused the flag (tool, due date, date returned), so the shed manager can judge
    whether to suspend. The details would need to reach this view, either carried on Neighbor
    Flagged or from Neighbor Registered and the loan events.
- [ ] If a neighbor already on the list is flagged again, does the list show one entry or two?
  - This depends on whether a second flag is recorded while one is still waiting, which is open
    on Flag Neighbor.
  - Recommendation: one entry per neighbor, showing how many times they have been flagged and
    the latest flag date.
