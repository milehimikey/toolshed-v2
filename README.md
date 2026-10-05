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
| 1 | Add Tool | State Change | no doc yet | — | — | — | — | — | — | [slices/add-tool.md](slices/add-tool.md) |
| 2 | Tool Inventory — tool added | State View | no doc yet | — | — | — | — | — | — | [slices/tool-inventory-tool-added.md](slices/tool-inventory-tool-added.md) |
| 3 | Tool Catalog — tool added | State View | no doc yet | — | — | — | — | — | — | [slices/tool-catalog-tool-added.md](slices/tool-catalog-tool-added.md) |
| 4 | Register Neighbor | State Change | no doc yet | — | — | — | — | — | — | [slices/register-neighbor.md](slices/register-neighbor.md) |
| 5 | Neighbors — neighbor registered | State View | no doc yet | — | — | — | — | — | — | [slices/neighbors-neighbor-registered.md](slices/neighbors-neighbor-registered.md) |
| 6 | My Account — neighbor registered | State View | no doc yet | — | — | — | — | — | — | [slices/my-account-neighbor-registered.md](slices/my-account-neighbor-registered.md) |
| 7 | Check Out Tool | State Change | no doc yet | — | — | — | — | — | — | [slices/check-out-tool.md](slices/check-out-tool.md) |
| 8 | Tool Catalog — checked out | State View | continuation of `tool-catalog-tool-added` | — | — | — | — | — | — | [slices/tool-catalog-tool-added.md](slices/tool-catalog-tool-added.md) |
| 9 | My Loans — checked out | State View | no doc yet | — | — | — | — | — | — | [slices/my-loans-checked-out.md](slices/my-loans-checked-out.md) |
| 10 | My Holds — checked out by holder | State View | no doc yet | — | — | — | — | — | — | [slices/my-holds-checked-out-by-holder.md](slices/my-holds-checked-out-by-holder.md) |
| 11 | Extend Loan | State Change | no doc yet | — | — | — | — | — | — | [slices/extend-loan.md](slices/extend-loan.md) |
| 12 | My Loans — loan extended | State View | continuation of `my-loans-checked-out` | — | — | — | — | — | — | [slices/my-loans-checked-out.md](slices/my-loans-checked-out.md) |
| 13 | Place Hold | State Change | no doc yet | — | — | — | — | — | — | [slices/place-hold.md](slices/place-hold.md) |
| 14 | Tool Catalog — hold placed | State View | continuation of `tool-catalog-tool-added` | — | — | — | — | — | — | [slices/tool-catalog-tool-added.md](slices/tool-catalog-tool-added.md) |
| 15 | My Holds — hold placed | State View | continuation of `my-holds-checked-out-by-holder` | — | — | — | — | — | — | [slices/my-holds-checked-out-by-holder.md](slices/my-holds-checked-out-by-holder.md) |
| 16 | Overdue Loans To Notify | State View | no doc yet | — | — | — | — | — | — | [slices/overdue-loans-to-notify.md](slices/overdue-loans-to-notify.md) |
| 17 | Send Overdue Notice | Automation | no doc yet | — | — | — | — | — | — | [slices/send-overdue-notice.md](slices/send-overdue-notice.md) |
| 18 | My Loans — overdue notice sent | State View | continuation of `my-loans-checked-out` | — | — | — | — | — | — | [slices/my-loans-checked-out.md](slices/my-loans-checked-out.md) |
| 19 | Return Tool | State Change | no doc yet | — | — | — | — | — | — | [slices/return-tool.md](slices/return-tool.md) |
| 20 | Tool Catalog — tool returned | State View | continuation of `tool-catalog-tool-added` | — | — | — | — | — | — | [slices/tool-catalog-tool-added.md](slices/tool-catalog-tool-added.md) |
| 21 | My Loans — tool returned | State View | continuation of `my-loans-checked-out` | — | — | — | — | — | — | [slices/my-loans-checked-out.md](slices/my-loans-checked-out.md) |
| 22 | Neighbors To Flag | State View | no doc yet | — | — | — | — | — | — | [slices/neighbors-to-flag.md](slices/neighbors-to-flag.md) |
| 23 | Holds To Fulfill | State View | no doc yet | — | — | — | — | — | — | [slices/holds-to-fulfill.md](slices/holds-to-fulfill.md) |
| 24 | Fulfill Hold | Automation | no doc yet | — | — | — | — | — | — | [slices/fulfill-hold.md](slices/fulfill-hold.md) |
| 25 | Tool Catalog — hold fulfilled | State View | continuation of `tool-catalog-tool-added` | — | — | — | — | — | — | [slices/tool-catalog-tool-added.md](slices/tool-catalog-tool-added.md) |
| 26 | My Holds — hold fulfilled | State View | continuation of `my-holds-checked-out-by-holder` | — | — | — | — | — | — | [slices/my-holds-checked-out-by-holder.md](slices/my-holds-checked-out-by-holder.md) |
| 27 | Flag Neighbor | Automation | no doc yet | — | — | — | — | — | — | [slices/flag-neighbor.md](slices/flag-neighbor.md) |
| 28 | Flagged Neighbors — neighbor flagged | State View | no doc yet | — | — | — | — | — | — | [slices/flagged-neighbors-neighbor-flagged.md](slices/flagged-neighbors-neighbor-flagged.md) |
| 29 | Suspend Neighbor | State Change | no doc yet | — | — | — | — | — | — | [slices/suspend-neighbor.md](slices/suspend-neighbor.md) |
| 30 | Flagged Neighbors — neighbor suspended | State View | continuation of `flagged-neighbors-neighbor-flagged` | — | — | — | — | — | — | [slices/flagged-neighbors-neighbor-flagged.md](slices/flagged-neighbors-neighbor-flagged.md) |
| 31 | Suspended Neighbors — neighbor suspended | State View | no doc yet | — | — | — | — | — | — | [slices/suspended-neighbors-neighbor-suspended.md](slices/suspended-neighbors-neighbor-suspended.md) |
| 32 | Neighbors — neighbor suspended | State View | continuation of `neighbors-neighbor-registered` | — | — | — | — | — | — | [slices/neighbors-neighbor-registered.md](slices/neighbors-neighbor-registered.md) |
| 33 | Reinstate Neighbor | State Change | no doc yet | — | — | — | — | — | — | [slices/reinstate-neighbor.md](slices/reinstate-neighbor.md) |
| 34 | Suspended Neighbors — neighbor reinstated | State View | continuation of `suspended-neighbors-neighbor-suspended` | — | — | — | — | — | — | [slices/suspended-neighbors-neighbor-suspended.md](slices/suspended-neighbors-neighbor-suspended.md) |
| 35 | Neighbors — neighbor reinstated | State View | continuation of `neighbors-neighbor-registered` | — | — | — | — | — | — | [slices/neighbors-neighbor-registered.md](slices/neighbors-neighbor-registered.md) |
| 36 | Retire Tool | State Change | no doc yet | — | — | — | — | — | — | [slices/retire-tool.md](slices/retire-tool.md) |
| 37 | Tool Inventory — tool retired | State View | continuation of `tool-inventory-tool-added` | — | — | — | — | — | — | [slices/tool-inventory-tool-added.md](slices/tool-inventory-tool-added.md) |
| 38 | Tool Catalog — tool retired | State View | continuation of `tool-catalog-tool-added` | — | — | — | — | — | — | [slices/tool-catalog-tool-added.md](slices/tool-catalog-tool-added.md) |
| 39 | Close Account | State Change | no doc yet | — | — | — | — | — | — | [slices/close-account.md](slices/close-account.md) |
| 40 | My Account — account closed | State View | continuation of `my-account-neighbor-registered` | — | — | — | — | — | — | [slices/my-account-neighbor-registered.md](slices/my-account-neighbor-registered.md) |
| 41 | Neighbors — account closed | State View | continuation of `neighbors-neighbor-registered` | — | — | — | — | — | — | [slices/neighbors-neighbor-registered.md](slices/neighbors-neighbor-registered.md) |
<!-- GENERATED:slices:end -->

## Status
See [`.event-modeling.md`](.event-modeling.md) for current phase, decisions, and open questions.
