# toolshed

<!-- One-paragraph description of the business process(es) this event model covers. -->

## Live view
While modeling, run the live view so the team can watch the diagram update:

```bash
em watch toolshed.em -o toolshed.svg --serve   # re-render + instant push-reload
# then open the URL it prints (http://localhost:5173/?svg=toolshed.svg) and share the screen
```

Pan/zoom to navigate the diagram (drag, scroll; **Fit** resets), and click **Review mode** in
the header for a slice-by-slice walkthrough. If a save fails to render, the viewer keeps the
last good diagram and shows an error banner until the next successful load.

Static render: `em render toolshed.em -o toolshed.svg`

## Patterns legend
- **State Change** — UI → Command → Event
- **State View** — Event(s) → Read Model → UI
- **Automation** — Read Model (slice before) → Processor + Command → Event, together
- **Translation** — External input (or Read Model, slice before) → Translation + Command → Event, together

Between them these are the only legal connections: `ui → command`, `command → event`,
`event → read model`, `read model → ui`, `read model → reaction`, `reaction → command`. A command
never reaches a read model directly — the event goes between them. Every slice is joined up at
both ends: something triggers each command (the screen it's issued from, or the reaction that
triggers it, also in this slice), and every event a command records is read by some read model — so each State
Change slice is paired with the State View slice that projects its event. A read model repeated
along the timeline (`view X again`) shows the same projection at a later point; the instances are
never connected to one another.

## Slices
<!-- The canonical slice index — the ONE place slices are enumerated (the state file
     points here rather than keeping its own copy). Generated — run
     `em slice index toolshed.em` to (re)write the table below from the model and its
     slice docs; never hand-edit between the markers. -->
<!-- GENERATED:slices:start -->
| # | Slice | Pattern | Status | Reviewed by | Ratified by | Owner | Tracking | Implemented in | Conformed | Design doc |
|---|-------|---------|--------|-------------|-------------|-------|----------|----------------|-----------|------------|
| 1 | Add Tool | State Change | draft | — | — | — | — | — | — | [slices/add-tool.md](slices/add-tool.md) |
| 2 | Tool Inventory | State View | draft | — | — | — | — | — | — | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 3 | Tool Catalog | State View | draft | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 4 | Register Neighbor | State Change | draft | — | — | — | — | — | — | [slices/register-neighbor.md](slices/register-neighbor.md) |
| 5 | Neighbors | State View | draft | — | — | — | — | — | — | [slices/neighbors.md](slices/neighbors.md) |
| 6 | My Account | State View | draft | — | — | — | — | — | — | [slices/my-account.md](slices/my-account.md) |
| 7 | Check Out Tool | State Change | draft | — | — | — | — | — | — | [slices/check-out-tool.md](slices/check-out-tool.md) |
| 8 | Tool Inventory — tool checked out | State View | continuation of `tool-inventory` | — | — | — | — | — | — | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 9 | Tool Catalog — checked out | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 10 | My Loans | State View | draft | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 11 | My Holds | State View | draft | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 12 | Extend Loan | State Change | draft | — | — | — | — | — | — | [slices/extend-loan.md](slices/extend-loan.md) |
| 13 | My Loans — loan extended | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 14 | Tool Catalog — loan extended | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 15 | Place Hold | State Change | draft | — | — | — | — | — | — | [slices/place-hold.md](slices/place-hold.md) |
| 16 | Tool Catalog — hold placed | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 17 | My Holds — hold placed | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 18 | My Loans — hold placed | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 19 | Cancel Hold | State Change | draft | — | — | — | — | — | — | [slices/cancel-hold.md](slices/cancel-hold.md) |
| 20 | My Holds — hold cancelled | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 21 | Tool Catalog — hold cancelled | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 22 | My Loans — hold cancelled | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 23 | Overdue Loans To Notify | State View | draft | — | — | — | — | — | — | [slices/overdue-loans-to-notify.md](slices/overdue-loans-to-notify.md) |
| 24 | Send Overdue Notice | Translation | draft | — | — | — | — | — | — | [slices/send-overdue-notice.md](slices/send-overdue-notice.md) |
| 25 | My Loans — overdue notice sent | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 26 | Record Overdue Notice Failure | Translation | draft | — | — | — | — | — | — | [slices/record-overdue-notice-failure.md](slices/record-overdue-notice-failure.md) |
| 27 | Undelivered Notices | State View | draft | — | — | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 28 | Return Tool | State Change | draft | — | — | — | — | — | — | [slices/return-tool.md](slices/return-tool.md) |
| 29 | Undelivered Notices — tool returned | State View | continuation of `undelivered-notices` | — | — | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 30 | Tool Inventory — tool returned | State View | continuation of `tool-inventory` | — | — | — | — | — | — | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 31 | Tool Catalog — tool returned | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 32 | My Loans — tool returned | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 33 | Neighbors To Flag | State View | draft | — | — | — | — | — | — | [slices/neighbors-to-flag.md](slices/neighbors-to-flag.md) |
| 34 | Holds To Fulfill | State View | draft | — | — | — | — | — | — | [slices/holds-to-fulfill.md](slices/holds-to-fulfill.md) |
| 35 | Fulfill Hold | Automation | draft | — | — | — | — | — | — | [slices/fulfill-hold.md](slices/fulfill-hold.md) |
| 36 | Tool Catalog — hold fulfilled | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 37 | My Holds — hold fulfilled | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 38 | Pickup Notices To Send | State View | draft | — | — | — | — | — | — | [slices/pickup-notices-to-send.md](slices/pickup-notices-to-send.md) |
| 39 | Send Pickup Notice | Translation | draft | — | — | — | — | — | — | [slices/send-pickup-notice.md](slices/send-pickup-notice.md) |
| 40 | My Holds — pickup notice sent | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 41 | Record Pickup Notice Failure | Translation | draft | — | — | — | — | — | — | [slices/record-pickup-notice-failure.md](slices/record-pickup-notice-failure.md) |
| 42 | Undelivered Notices — pickup notice failed | State View | continuation of `undelivered-notices` | — | — | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 43 | Pickups To Expire | State View | draft | — | — | — | — | — | — | [slices/pickups-to-expire.md](slices/pickups-to-expire.md) |
| 44 | Expire Hold | Automation | draft | — | — | — | — | — | — | [slices/expire-hold.md](slices/expire-hold.md) |
| 45 | My Holds — hold expired | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 46 | Tool Catalog — hold expired | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 47 | Undelivered Notices — hold expired | State View | continuation of `undelivered-notices` | — | — | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 48 | Flag Neighbor | Automation | draft | — | — | — | — | — | — | [slices/flag-neighbor.md](slices/flag-neighbor.md) |
| 49 | Flagged Neighbors | State View | draft | — | — | — | — | — | — | [slices/flagged-neighbors.md](slices/flagged-neighbors.md) |
| 50 | Suspend Neighbor | State Change | draft | — | — | — | — | — | — | [slices/suspend-neighbor.md](slices/suspend-neighbor.md) |
| 51 | Flagged Neighbors — neighbor suspended | State View | continuation of `flagged-neighbors` | — | — | — | — | — | — | [slices/flagged-neighbors.md](slices/flagged-neighbors.md) |
| 52 | Suspended Neighbors | State View | draft | — | — | — | — | — | — | [slices/suspended-neighbors.md](slices/suspended-neighbors.md) |
| 53 | Neighbors — neighbor suspended | State View | continuation of `neighbors` | — | — | — | — | — | — | [slices/neighbors.md](slices/neighbors.md) |
| 54 | My Account — neighbor suspended | State View | continuation of `my-account` | — | — | — | — | — | — | [slices/my-account.md](slices/my-account.md) |
| 55 | Reinstate Neighbor | State Change | draft | — | — | — | — | — | — | [slices/reinstate-neighbor.md](slices/reinstate-neighbor.md) |
| 56 | Suspended Neighbors — neighbor reinstated | State View | continuation of `suspended-neighbors` | — | — | — | — | — | — | [slices/suspended-neighbors.md](slices/suspended-neighbors.md) |
| 57 | Neighbors — neighbor reinstated | State View | continuation of `neighbors` | — | — | — | — | — | — | [slices/neighbors.md](slices/neighbors.md) |
| 58 | My Account — neighbor reinstated | State View | continuation of `my-account` | — | — | — | — | — | — | [slices/my-account.md](slices/my-account.md) |
| 59 | Retire Tool | State Change | draft | — | — | — | — | — | — | [slices/retire-tool.md](slices/retire-tool.md) |
| 60 | Tool Inventory — tool retired | State View | continuation of `tool-inventory` | — | — | — | — | — | — | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 61 | Tool Catalog — tool retired | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 62 | My Holds — tool retired | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 63 | My Loans — tool retired | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 64 | Undelivered Notices — tool retired | State View | continuation of `undelivered-notices` | — | — | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 65 | Record Tool Lost | State Change | draft | — | — | — | — | — | — | [slices/record-tool-lost.md](slices/record-tool-lost.md) |
| 66 | Tool Inventory — tool lost | State View | continuation of `tool-inventory` | — | — | — | — | — | — | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 67 | Tool Catalog — tool lost | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 68 | My Loans — tool lost | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 69 | My Holds — tool lost | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 70 | Undelivered Notices — tool lost | State View | continuation of `undelivered-notices` | — | — | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 71 | Close Account | State Change | draft | — | — | — | — | — | — | [slices/close-account.md](slices/close-account.md) |
| 72 | Flagged Neighbors — account closed | State View | continuation of `flagged-neighbors` | — | — | — | — | — | — | [slices/flagged-neighbors.md](slices/flagged-neighbors.md) |
| 73 | Suspended Neighbors — account closed | State View | continuation of `suspended-neighbors` | — | — | — | — | — | — | [slices/suspended-neighbors.md](slices/suspended-neighbors.md) |
| 74 | My Account — account closed | State View | continuation of `my-account` | — | — | — | — | — | — | [slices/my-account.md](slices/my-account.md) |
| 75 | Neighbors — account closed | State View | continuation of `neighbors` | — | — | — | — | — | — | [slices/neighbors.md](slices/neighbors.md) |
| 76 | My Holds — account closed | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 77 | Tool Catalog — account closed | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 78 | My Loans — account closed | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 79 | Undelivered Notices — account closed | State View | continuation of `undelivered-notices` | — | — | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
<!-- GENERATED:slices:end -->

## Status
See [`.event-modeling.md`](.event-modeling.md) for current phase, decisions, and open questions.
