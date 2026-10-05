---
schemaVersion: 1
pattern: state-view
swimlane: System → Neighbors
status: draft
version: 1
---
# Slice: Neighbors To Flag

![Diagram](./neighbors-to-flag.svg)

## Intent
The list of neighbors whose late returns have reached the late-return threshold and who have not
yet been flagged for it. The late-return flagger works through it, so the shed manager sees each
of these neighbors on Flagged Neighbors and decides whether to suspend them.

## Invariants / Business Rules
- **INV-NTF-1:** Only late returns count toward the threshold.
  - A return is late when it happens after the loan's due date in force, after any extensions.
    A return on the due date is on time.
  - A loan that is still out doesn't count, even if it is overdue, until it is returned late.
  - A tool recorded lost never counts as a late return, even if the loan was overdue.
- **INV-NTF-2:** A neighbor joins the list when they have 3 late returns within 90 days that
  have not yet counted toward a flag.
  - The 90 days are a rolling window, counted back from today by the date of each late return.
    A late return older than 90 days no longer counts.
- **INV-NTF-3:** Once Neighbor Flagged is recorded for a neighbor, they leave the list. The late
  returns that caused the flag are used up.
  - The neighbor is flagged again only after 3 new late returns within 90 days.
  - A flag the shed manager doesn't act on stays on Flagged Neighbors. It doesn't put the
    neighbor back on this list.
- **INV-NTF-4:** The list has no screen. Only the late-return flagger reads it.
  - The shed manager sees flagged neighbors on Flagged Neighbors, not here.
- **INV-NTF-5:** Reinstatement starts a neighbor's late-return count again from zero.
  - Late returns from before Neighbor Reinstated never count toward a later flag.
- **INV-NTF-6:** A neighbor whose account is closed is never flagged.
  - Once Neighbor Account Closed is recorded, the neighbor leaves the list if they are on it,
    and later late returns don't put them back.
- **INV-NTF-7:** A suspended neighbor is never flagged.
  - Once Neighbor Suspended is recorded, the neighbor leaves the list if they are on it, and late
    returns while they are suspended don't put them on it. Reinstatement then starts their count
    again from zero.

## Scenarios (Given / When / Then)
- **On-time return doesn't count**
  - **Given:** Ana has no late returns, and her drill loan is due 12 October
  - **When:** Tool Returned is recorded for the drill on 12 October
  - **Then:** Ana is not on the list, and her late-return count is unchanged.
- **Late return below the threshold**
  - **Given:** Ana has one late return, on 1 September, that has not counted toward a flag
  - **When:** Tool Returned is recorded on 20 September for a loan of Ana's that was due
    15 September
  - **Then:** Ana has two late returns within 90 days, and she is not on the list.
- **Third late return within 90 days**
  - **Given:** Ana has late returns on 1 September and 20 September, neither counted toward a
    flag
  - **When:** Tool Returned is recorded on 5 October for a loan of Ana's that was due
    1 October
  - **Then:** Ana is on the list, waiting to be flagged.
- **Old late return has dropped out of the window**
  - **Given:** Ana has late returns on 1 June and 20 September, neither counted toward a flag
  - **When:** Tool Returned is recorded on 5 October for a loan of Ana's after its due date
  - **Then:** only two of Ana's late returns are within 90 days, and she is not on the list.
- **Neighbor flagged**
  - **Given:** Ana is on the list
  - **When:** Neighbor Flagged is recorded for Ana
  - **Then:** Ana leaves the list. Her three late returns are used up.
- **Flagged again only on new late returns**
  - **Given:** Ana was flagged on 5 October and has no late returns since
  - **When:** Tool Returned is recorded on 12 October for a loan of Ana's after its due date
  - **Then:** Ana has one late return counting toward a new flag, and she is not on the list.
- **Reinstatement resets the count**
  - **Given:** Ana has two late returns within 90 days that have not counted toward a flag
  - **When:** Neighbor Reinstated is recorded for Ana
  - **Then:** Ana has no late returns counting toward a flag.
- **Closed account**
  - **Given:** Ana has two late returns within 90 days
  - **When:** Neighbor Account Closed is recorded for Ana, and a late Tool Returned is then
    recorded for her
  - **Then:** Ana is not on the list.
- **Suspended neighbor**
  - **Given:** Ana is suspended
  - **When:** a late Tool Returned is recorded for Ana
  - **Then:** Ana is not on the list.

## Open Questions
None.
