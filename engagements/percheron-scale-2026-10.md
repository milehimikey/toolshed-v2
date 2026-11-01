---
engagementSchemaVersion: "1.1"
slug: percheron-scale-2026-10
model: "../toolshed.em"
created: 2026-10-31
createdBy: "percheron"
tracking: "percheron-scale"
parallel: 3
status: closed
slices:
  - {key: send-overdue-notice, state: merged, branch: "impl/send-overdue-notice", base: "main", pr: null}
  - {key: undelivered-notices, state: merged, branch: "impl/undelivered-notices", base: "main", pr: null}
  - {key: holds-to-fulfill, state: merged, branch: "impl/holds-to-fulfill", base: "main", pr: null}
  - {key: fulfill-hold, state: merged, branch: "impl/fulfill-hold", base: "main", pr: null}
  - {key: pickup-notices-to-send, state: merged, branch: "impl/pickup-notices-to-send", base: "main", pr: null}
  - {key: send-pickup-notice, state: merged, branch: "impl/send-pickup-notice", base: "main", pr: null}
  - {key: pickups-to-expire, state: merged, branch: "impl/pickups-to-expire", base: "main", pr: null}
  - {key: expire-hold, state: merged, branch: "impl/expire-hold", base: "main", pr: null}
  - {key: neighbors-to-flag, state: merged, branch: "impl/neighbors-to-flag", base: "main", pr: null}
  - {key: flag-neighbor, state: merged, branch: "impl/flag-neighbor", base: "main", pr: null}
  - {key: flagged-neighbors, state: merged, branch: "impl/flagged-neighbors", base: "main", pr: null}
---
# Engagement: percheron-scale-2026-10

Written by `em engagement new` and updated only by `em engagement set` / `em engagement close` —
never hand-edit the frontmatter `slices:` entries or the Ledger table (see docs/engagement-schema.md).

## Ledger

<!-- GENERATED:em-engagement-ledger:start -->
| Slice | Pattern | Doc status | State | Branch | Base | PR |
|---|---|---|---|---|---|---|
| `send-overdue-notice` | translation | ready-to-implement | merged | impl/send-overdue-notice | main | — |
| `undelivered-notices` | state-view | implemented | merged | impl/undelivered-notices | main | — |
| `holds-to-fulfill` | state-view | implemented | merged | impl/holds-to-fulfill | main | — |
| `fulfill-hold` | automation | implemented | merged | impl/fulfill-hold | main | — |
| `pickup-notices-to-send` | state-view | implemented | merged | impl/pickup-notices-to-send | main | — |
| `send-pickup-notice` | translation | implemented | merged | impl/send-pickup-notice | main | — |
| `pickups-to-expire` | state-view | implemented | merged | impl/pickups-to-expire | main | — |
| `expire-hold` | automation | implemented | merged | impl/expire-hold | main | — |
| `neighbors-to-flag` | state-view | implemented | merged | impl/neighbors-to-flag | main | — |
| `flag-neighbor` | automation | implemented | merged | impl/flag-neighbor | main | — |
| `flagged-neighbors` | state-view | implemented | merged | impl/flagged-neighbors | main | — |
<!-- GENERATED:em-engagement-ledger:end -->
