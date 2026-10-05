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
| 8 | Tool Catalog — checked out | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 9 | My Loans | State View | draft | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 10 | My Holds | State View | draft | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 11 | Extend Loan | State Change | draft | — | — | — | — | — | — | [slices/extend-loan.md](slices/extend-loan.md) |
| 12 | My Loans — loan extended | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 13 | Tool Catalog — loan extended | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 14 | Place Hold | State Change | draft | — | — | — | — | — | — | [slices/place-hold.md](slices/place-hold.md) |
| 15 | Tool Catalog — hold placed | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 16 | My Holds — hold placed | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 17 | My Loans — hold placed | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 18 | Cancel Hold | State Change | draft | — | — | — | — | — | — | [slices/cancel-hold.md](slices/cancel-hold.md) |
| 19 | My Holds — hold cancelled | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 20 | Tool Catalog — hold cancelled | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 21 | My Loans — hold cancelled | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 22 | Overdue Loans To Notify | State View | draft | — | — | — | — | — | — | [slices/overdue-loans-to-notify.md](slices/overdue-loans-to-notify.md) |
| 23 | Send Overdue Notice | Translation | draft | — | — | — | — | — | — | [slices/send-overdue-notice.md](slices/send-overdue-notice.md) |
| 24 | My Loans — overdue notice sent | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 25 | Record Overdue Notice Failure | Translation | draft | — | — | — | — | — | — | [slices/record-overdue-notice-failure.md](slices/record-overdue-notice-failure.md) |
| 26 | Undelivered Notices | State View | draft | — | — | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 27 | Return Tool | State Change | draft | — | — | — | — | — | — | [slices/return-tool.md](slices/return-tool.md) |
| 28 | Tool Catalog — tool returned | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 29 | My Loans — tool returned | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 30 | Neighbors To Flag | State View | draft | — | — | — | — | — | — | [slices/neighbors-to-flag.md](slices/neighbors-to-flag.md) |
| 31 | Holds To Fulfill | State View | draft | — | — | — | — | — | — | [slices/holds-to-fulfill.md](slices/holds-to-fulfill.md) |
| 32 | Fulfill Hold | Automation | draft | — | — | — | — | — | — | [slices/fulfill-hold.md](slices/fulfill-hold.md) |
| 33 | Tool Catalog — hold fulfilled | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 34 | My Holds — hold fulfilled | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 35 | Pickup Notices To Send | State View | draft | — | — | — | — | — | — | [slices/pickup-notices-to-send.md](slices/pickup-notices-to-send.md) |
| 36 | Send Pickup Notice | Translation | draft | — | — | — | — | — | — | [slices/send-pickup-notice.md](slices/send-pickup-notice.md) |
| 37 | My Holds — pickup notice sent | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 38 | Pickups To Expire | State View | draft | — | — | — | — | — | — | [slices/pickups-to-expire.md](slices/pickups-to-expire.md) |
| 39 | Expire Hold | Automation | draft | — | — | — | — | — | — | [slices/expire-hold.md](slices/expire-hold.md) |
| 40 | My Holds — hold expired | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 41 | Tool Catalog — hold expired | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 42 | Flag Neighbor | Automation | draft | — | — | — | — | — | — | [slices/flag-neighbor.md](slices/flag-neighbor.md) |
| 43 | Flagged Neighbors | State View | draft | — | — | — | — | — | — | [slices/flagged-neighbors.md](slices/flagged-neighbors.md) |
| 44 | Suspend Neighbor | State Change | draft | — | — | — | — | — | — | [slices/suspend-neighbor.md](slices/suspend-neighbor.md) |
| 45 | Flagged Neighbors — neighbor suspended | State View | continuation of `flagged-neighbors` | — | — | — | — | — | — | [slices/flagged-neighbors.md](slices/flagged-neighbors.md) |
| 46 | Suspended Neighbors | State View | draft | — | — | — | — | — | — | [slices/suspended-neighbors.md](slices/suspended-neighbors.md) |
| 47 | Neighbors — neighbor suspended | State View | continuation of `neighbors` | — | — | — | — | — | — | [slices/neighbors.md](slices/neighbors.md) |
| 48 | My Account — neighbor suspended | State View | continuation of `my-account` | — | — | — | — | — | — | [slices/my-account.md](slices/my-account.md) |
| 49 | Reinstate Neighbor | State Change | draft | — | — | — | — | — | — | [slices/reinstate-neighbor.md](slices/reinstate-neighbor.md) |
| 50 | Suspended Neighbors — neighbor reinstated | State View | continuation of `suspended-neighbors` | — | — | — | — | — | — | [slices/suspended-neighbors.md](slices/suspended-neighbors.md) |
| 51 | Neighbors — neighbor reinstated | State View | continuation of `neighbors` | — | — | — | — | — | — | [slices/neighbors.md](slices/neighbors.md) |
| 52 | My Account — neighbor reinstated | State View | continuation of `my-account` | — | — | — | — | — | — | [slices/my-account.md](slices/my-account.md) |
| 53 | Retire Tool | State Change | draft | — | — | — | — | — | — | [slices/retire-tool.md](slices/retire-tool.md) |
| 54 | Tool Inventory — tool retired | State View | continuation of `tool-inventory` | — | — | — | — | — | — | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 55 | Tool Catalog — tool retired | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 56 | My Holds — tool retired | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 57 | My Loans — tool retired | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 58 | Close Account | State Change | draft | — | — | — | — | — | — | [slices/close-account.md](slices/close-account.md) |
| 59 | My Account — account closed | State View | continuation of `my-account` | — | — | — | — | — | — | [slices/my-account.md](slices/my-account.md) |
| 60 | Neighbors — account closed | State View | continuation of `neighbors` | — | — | — | — | — | — | [slices/neighbors.md](slices/neighbors.md) |
| 61 | My Holds — account closed | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 62 | Tool Catalog — account closed | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 63 | My Loans — account closed | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
<!-- GENERATED:slices:end -->

## Status
See [`.event-modeling.md`](.event-modeling.md) for current phase, decisions, and open questions.
