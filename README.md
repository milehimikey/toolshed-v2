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
| 13 | Place Hold | State Change | draft | — | — | — | — | — | — | [slices/place-hold.md](slices/place-hold.md) |
| 14 | Tool Catalog — hold placed | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 15 | My Holds — hold placed | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 16 | Overdue Loans To Notify | State View | draft | — | — | — | — | — | — | [slices/overdue-loans-to-notify.md](slices/overdue-loans-to-notify.md) |
| 17 | Send Overdue Notice | Translation | draft | — | — | — | — | — | — | [slices/send-overdue-notice.md](slices/send-overdue-notice.md) |
| 18 | My Loans — overdue notice sent | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 19 | Return Tool | State Change | draft | — | — | — | — | — | — | [slices/return-tool.md](slices/return-tool.md) |
| 20 | Tool Catalog — tool returned | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 21 | My Loans — tool returned | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 22 | Neighbors To Flag | State View | draft | — | — | — | — | — | — | [slices/neighbors-to-flag.md](slices/neighbors-to-flag.md) |
| 23 | Holds To Fulfill | State View | draft | — | — | — | — | — | — | [slices/holds-to-fulfill.md](slices/holds-to-fulfill.md) |
| 24 | Fulfill Hold | Automation | draft | — | — | — | — | — | — | [slices/fulfill-hold.md](slices/fulfill-hold.md) |
| 25 | Tool Catalog — hold fulfilled | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 26 | My Holds — hold fulfilled | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 27 | Flag Neighbor | Automation | draft | — | — | — | — | — | — | [slices/flag-neighbor.md](slices/flag-neighbor.md) |
| 28 | Flagged Neighbors | State View | draft | — | — | — | — | — | — | [slices/flagged-neighbors.md](slices/flagged-neighbors.md) |
| 29 | Suspend Neighbor | State Change | draft | — | — | — | — | — | — | [slices/suspend-neighbor.md](slices/suspend-neighbor.md) |
| 30 | Flagged Neighbors — neighbor suspended | State View | continuation of `flagged-neighbors` | — | — | — | — | — | — | [slices/flagged-neighbors.md](slices/flagged-neighbors.md) |
| 31 | Suspended Neighbors | State View | draft | — | — | — | — | — | — | [slices/suspended-neighbors.md](slices/suspended-neighbors.md) |
| 32 | Neighbors — neighbor suspended | State View | continuation of `neighbors` | — | — | — | — | — | — | [slices/neighbors.md](slices/neighbors.md) |
| 33 | Reinstate Neighbor | State Change | draft | — | — | — | — | — | — | [slices/reinstate-neighbor.md](slices/reinstate-neighbor.md) |
| 34 | Suspended Neighbors — neighbor reinstated | State View | continuation of `suspended-neighbors` | — | — | — | — | — | — | [slices/suspended-neighbors.md](slices/suspended-neighbors.md) |
| 35 | Neighbors — neighbor reinstated | State View | continuation of `neighbors` | — | — | — | — | — | — | [slices/neighbors.md](slices/neighbors.md) |
| 36 | Retire Tool | State Change | draft | — | — | — | — | — | — | [slices/retire-tool.md](slices/retire-tool.md) |
| 37 | Tool Inventory — tool retired | State View | continuation of `tool-inventory` | — | — | — | — | — | — | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 38 | Tool Catalog — tool retired | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 39 | Close Account | State Change | draft | — | — | — | — | — | — | [slices/close-account.md](slices/close-account.md) |
| 40 | My Account — account closed | State View | continuation of `my-account` | — | — | — | — | — | — | [slices/my-account.md](slices/my-account.md) |
| 41 | Neighbors — account closed | State View | continuation of `neighbors` | — | — | — | — | — | — | [slices/neighbors.md](slices/neighbors.md) |
<!-- GENERATED:slices:end -->

## Status
See [`.event-modeling.md`](.event-modeling.md) for current phase, decisions, and open questions.
