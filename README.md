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
| 1 | Add Tool | State Change | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/add-tool.md](slices/add-tool.md) |
| 2 | Tool Inventory | State View | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 3 | Tool Catalog | State View | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 4 | Register Neighbor | State Change | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/register-neighbor.md](slices/register-neighbor.md) |
| 5 | Tool Inventory — neighbor registered | State View | continuation of `tool-inventory` | Mike Key | Mike Key | — | — | — | — | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 6 | Neighbors | State View | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/neighbors.md](slices/neighbors.md) |
| 7 | My Account | State View | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/my-account.md](slices/my-account.md) |
| 8 | Check Out Tool | State Change | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/check-out-tool.md](slices/check-out-tool.md) |
| 9 | Tool Inventory — tool checked out | State View | continuation of `tool-inventory` | Mike Key | Mike Key | — | — | — | — | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 10 | Tool Catalog — checked out | State View | continuation of `tool-catalog` | Mike Key | Mike Key | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 11 | My Loans | State View | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 12 | Extend Loan | State Change | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/extend-loan.md](slices/extend-loan.md) |
| 13 | Tool Inventory — loan extended | State View | continuation of `tool-inventory` | Mike Key | Mike Key | — | — | — | — | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 14 | My Loans — loan extended | State View | continuation of `my-loans` | Mike Key | Mike Key | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 15 | Tool Catalog — loan extended | State View | continuation of `tool-catalog` | Mike Key | Mike Key | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 16 | Place Hold | State Change | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/place-hold.md](slices/place-hold.md) |
| 17 | My Holds | State View | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 18 | Tool Catalog — hold placed | State View | continuation of `tool-catalog` | Mike Key | Mike Key | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 19 | My Loans — hold placed | State View | continuation of `my-loans` | Mike Key | Mike Key | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 20 | Cancel Hold | State Change | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/cancel-hold.md](slices/cancel-hold.md) |
| 21 | My Holds — hold cancelled | State View | continuation of `my-holds` | Mike Key | Mike Key | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 22 | Tool Catalog — hold cancelled | State View | continuation of `tool-catalog` | Mike Key | Mike Key | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 23 | My Loans — hold cancelled | State View | continuation of `my-loans` | Mike Key | Mike Key | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 24 | Overdue Loans To Notify | State View | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/overdue-loans-to-notify.md](slices/overdue-loans-to-notify.md) |
| 25 | Send Overdue Notice | Translation | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/send-overdue-notice.md](slices/send-overdue-notice.md) |
| 26 | My Loans — overdue notice sent | State View | continuation of `my-loans` | Mike Key | Mike Key | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 27 | Record Overdue Notice Failure | Translation | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/record-overdue-notice-failure.md](slices/record-overdue-notice-failure.md) |
| 28 | My Loans — overdue notice failed | State View | continuation of `my-loans` | Mike Key | Mike Key | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 29 | Undelivered Notices | State View | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 30 | Change Email | State Change | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/change-email.md](slices/change-email.md) |
| 31 | My Account — email changed | State View | continuation of `my-account` | Mike Key | Mike Key | — | — | — | — | [slices/my-account.md](slices/my-account.md) |
| 32 | Neighbors — email changed | State View | continuation of `neighbors` | Mike Key | Mike Key | — | — | — | — | [slices/neighbors.md](slices/neighbors.md) |
| 33 | Undelivered Notices — email changed | State View | continuation of `undelivered-notices` | Mike Key | Mike Key | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 34 | Return Tool | State Change | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/return-tool.md](slices/return-tool.md) |
| 35 | Undelivered Notices — tool returned | State View | continuation of `undelivered-notices` | Mike Key | Mike Key | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 36 | Tool Inventory — tool returned | State View | continuation of `tool-inventory` | Mike Key | Mike Key | — | — | — | — | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 37 | Tool Catalog — tool returned | State View | continuation of `tool-catalog` | Mike Key | Mike Key | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 38 | My Loans — tool returned | State View | continuation of `my-loans` | Mike Key | Mike Key | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 39 | Holds To Fulfill | State View | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/holds-to-fulfill.md](slices/holds-to-fulfill.md) |
| 40 | Fulfill Hold | Automation | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/fulfill-hold.md](slices/fulfill-hold.md) |
| 41 | Undelivered Notices — hold fulfilled | State View | continuation of `undelivered-notices` | Mike Key | Mike Key | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 42 | Tool Catalog — hold fulfilled | State View | continuation of `tool-catalog` | Mike Key | Mike Key | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 43 | My Holds — hold fulfilled | State View | continuation of `my-holds` | Mike Key | Mike Key | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 44 | Pickup Notices To Send | State View | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/pickup-notices-to-send.md](slices/pickup-notices-to-send.md) |
| 45 | Send Pickup Notice | Translation | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/send-pickup-notice.md](slices/send-pickup-notice.md) |
| 46 | My Holds — pickup notice sent | State View | continuation of `my-holds` | Mike Key | Mike Key | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 47 | Record Pickup Notice Failure | Translation | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/record-pickup-notice-failure.md](slices/record-pickup-notice-failure.md) |
| 48 | My Holds — pickup notice failed | State View | continuation of `my-holds` | Mike Key | Mike Key | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 49 | Undelivered Notices — pickup notice failed | State View | continuation of `undelivered-notices` | Mike Key | Mike Key | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 50 | Pickups To Expire | State View | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/pickups-to-expire.md](slices/pickups-to-expire.md) |
| 51 | Expire Hold | Automation | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/expire-hold.md](slices/expire-hold.md) |
| 52 | My Loans — hold expired | State View | continuation of `my-loans` | Mike Key | Mike Key | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 53 | My Holds — hold expired | State View | continuation of `my-holds` | Mike Key | Mike Key | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 54 | Tool Catalog — hold expired | State View | continuation of `tool-catalog` | Mike Key | Mike Key | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 55 | Undelivered Notices — hold expired | State View | continuation of `undelivered-notices` | Mike Key | Mike Key | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 56 | Neighbors To Flag | State View | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/neighbors-to-flag.md](slices/neighbors-to-flag.md) |
| 57 | Flag Neighbor | Automation | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/flag-neighbor.md](slices/flag-neighbor.md) |
| 58 | Flagged Neighbors | State View | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/flagged-neighbors.md](slices/flagged-neighbors.md) |
| 59 | Suspend Neighbor | State Change | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/suspend-neighbor.md](slices/suspend-neighbor.md) |
| 60 | Flagged Neighbors — neighbor suspended | State View | continuation of `flagged-neighbors` | Mike Key | Mike Key | — | — | — | — | [slices/flagged-neighbors.md](slices/flagged-neighbors.md) |
| 61 | Suspended Neighbors | State View | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/suspended-neighbors.md](slices/suspended-neighbors.md) |
| 62 | Neighbors — neighbor suspended | State View | continuation of `neighbors` | Mike Key | Mike Key | — | — | — | — | [slices/neighbors.md](slices/neighbors.md) |
| 63 | My Account — neighbor suspended | State View | continuation of `my-account` | Mike Key | Mike Key | — | — | — | — | [slices/my-account.md](slices/my-account.md) |
| 64 | Reinstate Neighbor | State Change | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/reinstate-neighbor.md](slices/reinstate-neighbor.md) |
| 65 | Suspended Neighbors — neighbor reinstated | State View | continuation of `suspended-neighbors` | Mike Key | Mike Key | — | — | — | — | [slices/suspended-neighbors.md](slices/suspended-neighbors.md) |
| 66 | Neighbors — neighbor reinstated | State View | continuation of `neighbors` | Mike Key | Mike Key | — | — | — | — | [slices/neighbors.md](slices/neighbors.md) |
| 67 | My Account — neighbor reinstated | State View | continuation of `my-account` | Mike Key | Mike Key | — | — | — | — | [slices/my-account.md](slices/my-account.md) |
| 68 | Retire Tool | State Change | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/retire-tool.md](slices/retire-tool.md) |
| 69 | Tool Inventory — tool retired | State View | continuation of `tool-inventory` | Mike Key | Mike Key | — | — | — | — | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 70 | Tool Catalog — tool retired | State View | continuation of `tool-catalog` | Mike Key | Mike Key | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 71 | My Holds — tool retired | State View | continuation of `my-holds` | Mike Key | Mike Key | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 72 | My Loans — tool retired | State View | continuation of `my-loans` | Mike Key | Mike Key | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 73 | Undelivered Notices — tool retired | State View | continuation of `undelivered-notices` | Mike Key | Mike Key | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 74 | Record Tool Lost | State Change | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/record-tool-lost.md](slices/record-tool-lost.md) |
| 75 | Tool Inventory — tool lost | State View | continuation of `tool-inventory` | Mike Key | Mike Key | — | — | — | — | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 76 | Tool Catalog — tool lost | State View | continuation of `tool-catalog` | Mike Key | Mike Key | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 77 | My Loans — tool lost | State View | continuation of `my-loans` | Mike Key | Mike Key | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 78 | My Holds — tool lost | State View | continuation of `my-holds` | Mike Key | Mike Key | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 79 | Undelivered Notices — tool lost | State View | continuation of `undelivered-notices` | Mike Key | Mike Key | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 80 | Close Account | State Change | ready-to-implement | Mike Key | Mike Key | — | — | — | — | [slices/close-account.md](slices/close-account.md) |
| 81 | Flagged Neighbors — account closed | State View | continuation of `flagged-neighbors` | Mike Key | Mike Key | — | — | — | — | [slices/flagged-neighbors.md](slices/flagged-neighbors.md) |
| 82 | Suspended Neighbors — account closed | State View | continuation of `suspended-neighbors` | Mike Key | Mike Key | — | — | — | — | [slices/suspended-neighbors.md](slices/suspended-neighbors.md) |
| 83 | My Account — account closed | State View | continuation of `my-account` | Mike Key | Mike Key | — | — | — | — | [slices/my-account.md](slices/my-account.md) |
| 84 | Neighbors — account closed | State View | continuation of `neighbors` | Mike Key | Mike Key | — | — | — | — | [slices/neighbors.md](slices/neighbors.md) |
| 85 | My Holds — account closed | State View | continuation of `my-holds` | Mike Key | Mike Key | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 86 | Tool Catalog — account closed | State View | continuation of `tool-catalog` | Mike Key | Mike Key | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 87 | My Loans — account closed | State View | continuation of `my-loans` | Mike Key | Mike Key | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 88 | Undelivered Notices — account closed | State View | continuation of `undelivered-notices` | Mike Key | Mike Key | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
<!-- GENERATED:slices:end -->

## Status
See [`.event-modeling.md`](.event-modeling.md) for current phase, decisions, and open questions.
