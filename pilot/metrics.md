# Pilot metrics — toolshed-v2 (em-factory rig)

Recording template carried from toolshed v1 (`playbook/metrics.md`), plus the rig-specific
tables below. `oversight.steward` keeps this current; honesty label per [NOTES.md](NOTES.md).

## Conform cycles

| Date | Target rev | Real drift | Model gap | Internal inconsistency | Uncertainty | False positives |
|---|---|---|---|---|---|---|
|      |             |            |           |                         |             |                  |

## Ratification turnaround

| Slice key | Created/reratified (commit, date) | Ratified (commit, date) | Turnaround |
|---|---|---|---|
|           |                                    |                          |            |

## Status-vs-reality disagreement (weekly sample)

| Week of | Slice key | driftSignal bucket | Actual state | Caught by |
|---|---|---|---|---|
|         |           |                     |               |           |

## Gate effect on what gets built (weekly)

| Week of | Proposed | Ratified | Refused by gate | Why (one line each refusal) |
|---|---|---|---|---|
|         |          |          |                  |                              |

## Rig: modeling → delivery flow

| Slice key | Ratified (date) | Picked up by delivery (date) | Merged (date) | Wait (ratified → picked up) |
|---|---|---|---|---|
|           |                 |                              |               |                             |

## Rig: human interrupts

| Date | From seat | Kind (domain question / ratification / permission / stuck) | Could it have been answered from the Decisions log? |
|---|---|---|---|
|      |           |                                                             |                                                     |

## Rig: gaps

| Date | Slice key | Gap | Surfaced by (seat) | Surfaced before code (Y) / found at review (N) | Resolution |
|---|---|---|---|---|---|
|      |           |     |                    |                                                 |            |

## Rig: seat continuity

| Date | Seat | Event (compaction / restore / relaunch / handover) | Recovered cleanly? | Notes |
|---|---|---|---|---|
|      |      |                                                     |                    |       |

## Comparison with v1 (fill at retro)

| Measure | toolshed v1 (single operator) | toolshed-v2 (em-factory rig) |
|---|---|---|
| Slices | 40 | |
| Elapsed days, discover → last merge | | |
| Conform findings at first run | see v1 `conformance/2026-09-07-report.md` | |
| Human interrupts | not recorded | |
