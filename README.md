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

## Storylines
<!-- Generated — `em slice index toolshed.em` writes one row per storyline between the
     markers (count, implemented, diagram); the canvas is `em render toolshed.em --map`. -->
<!-- GENERATED:storylines:start -->
| Storyline | Slices | Implemented | Diagram |
|-----------|--------|-------------|---------|
| Stock the shed | 3 | 1 | [storylines/stock-the-shed.svg](storylines/stock-the-shed.svg) |
| Join the shed | 6 | 3 | [storylines/join-the-shed.svg](storylines/join-the-shed.svg) |
| Borrow and return a tool | 12 | 1 | [storylines/borrow-and-return-a-tool.svg](storylines/borrow-and-return-a-tool.svg) |
| Chase an overdue loan | 6 | 0 | [storylines/chase-an-overdue-loan.svg](storylines/chase-an-overdue-loan.svg) |
| Hold a tool for pickup | 29 | 1 | [storylines/hold-a-tool-for-pickup.svg](storylines/hold-a-tool-for-pickup.svg) |
| Keep late returners in check | 9 | 0 | [storylines/keep-late-returners-in-check.svg](storylines/keep-late-returners-in-check.svg) |
| Take a tool or a neighbor out of the shed | 15 | 0 | [storylines/take-a-tool-or-a-neighbor-out-of-the-shed.svg](storylines/take-a-tool-or-a-neighbor-out-of-the-shed.svg) |
| Keep tools in working order | 5 | 0 | [storylines/keep-tools-in-working-order.svg](storylines/keep-tools-in-working-order.svg) |

Canvas: [storylines/map.html](storylines/map.html) (`em render --map`)
<!-- GENERATED:storylines:end -->

## Slices
<!-- The canonical slice index — the ONE place slices are enumerated (the state file
     points here rather than keeping its own copy). Generated — run
     `em slice index toolshed.em` to (re)write the table below from the model and its
     slice docs; never hand-edit between the markers. -->
<!-- GENERATED:slices:start -->
### Stock the shed
_3 slices · [diagram](storylines/stock-the-shed.svg)_

| # | Slice | Pattern | Status | Design doc |
|---|-------|---------|--------|------------|
| 1 | Add Tool | State Change | implemented | [stock-the-shed/slices/add-tool.md](stock-the-shed/slices/add-tool.md) |
| 2 | Tool Inventory | State View | draft | [stock-the-shed/slices/tool-inventory.md](stock-the-shed/slices/tool-inventory.md) |
| 3 | Tool Catalog | State View | reviewed | [stock-the-shed/slices/tool-catalog.md](stock-the-shed/slices/tool-catalog.md) |

### Join the shed
_6 slices · [diagram](storylines/join-the-shed.svg)_

| # | Slice | Pattern | Status | Design doc |
|---|-------|---------|--------|------------|
| 1 | Register Neighbor | State Change | implemented | [join-the-shed/slices/register-neighbor.md](join-the-shed/slices/register-neighbor.md) |
| 2 | Neighbors | State View | draft | [join-the-shed/slices/neighbors.md](join-the-shed/slices/neighbors.md) |
| 3 | My Account | State View | implemented | [join-the-shed/slices/my-account.md](join-the-shed/slices/my-account.md) |
| 4 | Change Email | State Change | implemented | [join-the-shed/slices/change-email.md](join-the-shed/slices/change-email.md) |
| 5 | My Account — email changed | State View | continuation of `my-account` | [join-the-shed/slices/my-account.md](join-the-shed/slices/my-account.md) |
| 6 | Neighbors — email changed | State View | continuation of `neighbors` | [join-the-shed/slices/neighbors.md](join-the-shed/slices/neighbors.md) |

### Borrow and return a tool
_12 slices · [diagram](storylines/borrow-and-return-a-tool.svg)_

| # | Slice | Pattern | Status | Design doc |
|---|-------|---------|--------|------------|
| 1 | Check Out Tool | State Change | reviewed | [borrow-and-return-a-tool/slices/check-out-tool.md](borrow-and-return-a-tool/slices/check-out-tool.md) |
| 2 | Tool Inventory — tool checked out | State View | continuation of `tool-inventory` | [borrow-and-return-a-tool/slices/tool-inventory.md](borrow-and-return-a-tool/slices/tool-inventory.md) |
| 3 | Tool Catalog — checked out | State View | continuation of `tool-catalog` | [borrow-and-return-a-tool/slices/tool-catalog.md](borrow-and-return-a-tool/slices/tool-catalog.md) |
| 4 | My Loans | State View | draft | [borrow-and-return-a-tool/slices/my-loans.md](borrow-and-return-a-tool/slices/my-loans.md) |
| 5 | Extend Loan | State Change | implemented | [borrow-and-return-a-tool/slices/extend-loan.md](borrow-and-return-a-tool/slices/extend-loan.md) |
| 6 | Tool Inventory — loan extended | State View | continuation of `tool-inventory` | [borrow-and-return-a-tool/slices/tool-inventory.md](borrow-and-return-a-tool/slices/tool-inventory.md) |
| 7 | My Loans — loan extended | State View | continuation of `my-loans` | [borrow-and-return-a-tool/slices/my-loans.md](borrow-and-return-a-tool/slices/my-loans.md) |
| 8 | Tool Catalog — loan extended | State View | continuation of `tool-catalog` | [borrow-and-return-a-tool/slices/tool-catalog.md](borrow-and-return-a-tool/slices/tool-catalog.md) |
| 9 | Return Tool | State Change | draft | [borrow-and-return-a-tool/slices/return-tool.md](borrow-and-return-a-tool/slices/return-tool.md) |
| 10 | Tool Inventory — tool returned | State View | continuation of `tool-inventory` | [borrow-and-return-a-tool/slices/tool-inventory.md](borrow-and-return-a-tool/slices/tool-inventory.md) |
| 11 | Tool Catalog — tool returned | State View | continuation of `tool-catalog` | [borrow-and-return-a-tool/slices/tool-catalog.md](borrow-and-return-a-tool/slices/tool-catalog.md) |
| 12 | My Loans — tool returned | State View | continuation of `my-loans` | [borrow-and-return-a-tool/slices/my-loans.md](borrow-and-return-a-tool/slices/my-loans.md) |

### Chase an overdue loan
_6 slices · [diagram](storylines/chase-an-overdue-loan.svg)_

| # | Slice | Pattern | Status | Design doc |
|---|-------|---------|--------|------------|
| 1 | Overdue Loans To Notify | State View | draft | [chase-an-overdue-loan/slices/overdue-loans-to-notify.md](chase-an-overdue-loan/slices/overdue-loans-to-notify.md) |
| 2 | Send Overdue Notice | Translation | draft | [chase-an-overdue-loan/slices/send-overdue-notice.md](chase-an-overdue-loan/slices/send-overdue-notice.md) |
| 3 | My Loans — overdue notice sent | State View | continuation of `my-loans` | [chase-an-overdue-loan/slices/my-loans.md](chase-an-overdue-loan/slices/my-loans.md) |
| 4 | Record Overdue Notice Failure | Translation | draft | [chase-an-overdue-loan/slices/record-overdue-notice-failure.md](chase-an-overdue-loan/slices/record-overdue-notice-failure.md) |
| 5 | My Loans — overdue notice failed | State View | continuation of `my-loans` | [chase-an-overdue-loan/slices/my-loans.md](chase-an-overdue-loan/slices/my-loans.md) |
| 6 | Undelivered Notices | State View | draft | [chase-an-overdue-loan/slices/undelivered-notices.md](chase-an-overdue-loan/slices/undelivered-notices.md) |

### Hold a tool for pickup
_29 slices · [diagram](storylines/hold-a-tool-for-pickup.svg)_

| # | Slice | Pattern | Status | Design doc |
|---|-------|---------|--------|------------|
| 1 | Place Hold | State Change | reviewed | [hold-a-tool-for-pickup/slices/place-hold.md](hold-a-tool-for-pickup/slices/place-hold.md) |
| 2 | My Holds | State View | draft | [hold-a-tool-for-pickup/slices/my-holds.md](hold-a-tool-for-pickup/slices/my-holds.md) |
| 3 | Tool Catalog — hold placed | State View | continuation of `tool-catalog` | [hold-a-tool-for-pickup/slices/tool-catalog.md](hold-a-tool-for-pickup/slices/tool-catalog.md) |
| 4 | My Loans — hold placed | State View | continuation of `my-loans` | [hold-a-tool-for-pickup/slices/my-loans.md](hold-a-tool-for-pickup/slices/my-loans.md) |
| 5 | Holds To Collect | State View | draft | [hold-a-tool-for-pickup/slices/holds-to-collect.md](hold-a-tool-for-pickup/slices/holds-to-collect.md) |
| 6 | Collect Hold | Automation | draft | [hold-a-tool-for-pickup/slices/collect-hold.md](hold-a-tool-for-pickup/slices/collect-hold.md) |
| 7 | My Holds — hold collected | State View | continuation of `my-holds` | [hold-a-tool-for-pickup/slices/my-holds.md](hold-a-tool-for-pickup/slices/my-holds.md) |
| 8 | Undelivered Notices — hold collected | State View | continuation of `undelivered-notices` | [hold-a-tool-for-pickup/slices/undelivered-notices.md](hold-a-tool-for-pickup/slices/undelivered-notices.md) |
| 9 | Cancel Hold | State Change | draft | [hold-a-tool-for-pickup/slices/cancel-hold.md](hold-a-tool-for-pickup/slices/cancel-hold.md) |
| 10 | My Holds — hold cancelled | State View | continuation of `my-holds` | [hold-a-tool-for-pickup/slices/my-holds.md](hold-a-tool-for-pickup/slices/my-holds.md) |
| 11 | Tool Catalog — hold cancelled | State View | continuation of `tool-catalog` | [hold-a-tool-for-pickup/slices/tool-catalog.md](hold-a-tool-for-pickup/slices/tool-catalog.md) |
| 12 | My Loans — hold cancelled | State View | continuation of `my-loans` | [hold-a-tool-for-pickup/slices/my-loans.md](hold-a-tool-for-pickup/slices/my-loans.md) |
| 13 | Undelivered Notices — hold cancelled | State View | continuation of `undelivered-notices` | [hold-a-tool-for-pickup/slices/undelivered-notices.md](hold-a-tool-for-pickup/slices/undelivered-notices.md) |
| 14 | Holds To Fulfill | State View | draft | [hold-a-tool-for-pickup/slices/holds-to-fulfill.md](hold-a-tool-for-pickup/slices/holds-to-fulfill.md) |
| 15 | Fulfill Hold | Automation | draft | [hold-a-tool-for-pickup/slices/fulfill-hold.md](hold-a-tool-for-pickup/slices/fulfill-hold.md) |
| 16 | Tool Catalog — hold fulfilled | State View | continuation of `tool-catalog` | [hold-a-tool-for-pickup/slices/tool-catalog.md](hold-a-tool-for-pickup/slices/tool-catalog.md) |
| 17 | My Holds — hold fulfilled | State View | continuation of `my-holds` | [hold-a-tool-for-pickup/slices/my-holds.md](hold-a-tool-for-pickup/slices/my-holds.md) |
| 18 | Pickup Notices To Send | State View | draft | [hold-a-tool-for-pickup/slices/pickup-notices-to-send.md](hold-a-tool-for-pickup/slices/pickup-notices-to-send.md) |
| 19 | Send Pickup Notice | Translation | draft | [hold-a-tool-for-pickup/slices/send-pickup-notice.md](hold-a-tool-for-pickup/slices/send-pickup-notice.md) |
| 20 | My Holds — pickup notice sent | State View | continuation of `my-holds` | [hold-a-tool-for-pickup/slices/my-holds.md](hold-a-tool-for-pickup/slices/my-holds.md) |
| 21 | Record Pickup Notice Failure | Translation | draft | [hold-a-tool-for-pickup/slices/record-pickup-notice-failure.md](hold-a-tool-for-pickup/slices/record-pickup-notice-failure.md) |
| 22 | My Holds — pickup notice failed | State View | continuation of `my-holds` | [hold-a-tool-for-pickup/slices/my-holds.md](hold-a-tool-for-pickup/slices/my-holds.md) |
| 23 | Undelivered Notices — pickup notice failed | State View | continuation of `undelivered-notices` | [hold-a-tool-for-pickup/slices/undelivered-notices.md](hold-a-tool-for-pickup/slices/undelivered-notices.md) |
| 24 | Pickups To Expire | State View | draft | [hold-a-tool-for-pickup/slices/pickups-to-expire.md](hold-a-tool-for-pickup/slices/pickups-to-expire.md) |
| 25 | Expire Hold | Automation | implemented | [hold-a-tool-for-pickup/slices/expire-hold.md](hold-a-tool-for-pickup/slices/expire-hold.md) |
| 26 | My Loans — hold expired | State View | continuation of `my-loans` | [hold-a-tool-for-pickup/slices/my-loans.md](hold-a-tool-for-pickup/slices/my-loans.md) |
| 27 | My Holds — hold expired | State View | continuation of `my-holds` | [hold-a-tool-for-pickup/slices/my-holds.md](hold-a-tool-for-pickup/slices/my-holds.md) |
| 28 | Tool Catalog — hold expired | State View | continuation of `tool-catalog` | [hold-a-tool-for-pickup/slices/tool-catalog.md](hold-a-tool-for-pickup/slices/tool-catalog.md) |
| 29 | Undelivered Notices — hold expired | State View | continuation of `undelivered-notices` | [hold-a-tool-for-pickup/slices/undelivered-notices.md](hold-a-tool-for-pickup/slices/undelivered-notices.md) |

### Keep late returners in check
_9 slices · [diagram](storylines/keep-late-returners-in-check.svg)_

| # | Slice | Pattern | Status | Design doc |
|---|-------|---------|--------|------------|
| 1 | Neighbors To Flag | State View | draft | [keep-late-returners-in-check/slices/neighbors-to-flag.md](keep-late-returners-in-check/slices/neighbors-to-flag.md) |
| 2 | Flag Neighbor | Automation | draft | [keep-late-returners-in-check/slices/flag-neighbor.md](keep-late-returners-in-check/slices/flag-neighbor.md) |
| 3 | Neighbors — neighbor flagged | State View | continuation of `neighbors` | [keep-late-returners-in-check/slices/neighbors.md](keep-late-returners-in-check/slices/neighbors.md) |
| 4 | Suspend Neighbor | State Change | draft | [keep-late-returners-in-check/slices/suspend-neighbor.md](keep-late-returners-in-check/slices/suspend-neighbor.md) |
| 5 | Neighbors — neighbor suspended | State View | continuation of `neighbors` | [keep-late-returners-in-check/slices/neighbors.md](keep-late-returners-in-check/slices/neighbors.md) |
| 6 | My Account — neighbor suspended | State View | continuation of `my-account` | [keep-late-returners-in-check/slices/my-account.md](keep-late-returners-in-check/slices/my-account.md) |
| 7 | Reinstate Neighbor | State Change | draft | [keep-late-returners-in-check/slices/reinstate-neighbor.md](keep-late-returners-in-check/slices/reinstate-neighbor.md) |
| 8 | Neighbors — neighbor reinstated | State View | continuation of `neighbors` | [keep-late-returners-in-check/slices/neighbors.md](keep-late-returners-in-check/slices/neighbors.md) |
| 9 | My Account — neighbor reinstated | State View | continuation of `my-account` | [keep-late-returners-in-check/slices/my-account.md](keep-late-returners-in-check/slices/my-account.md) |

### Take a tool or a neighbor out of the shed
_15 slices · [diagram](storylines/take-a-tool-or-a-neighbor-out-of-the-shed.svg)_

| # | Slice | Pattern | Status | Design doc |
|---|-------|---------|--------|------------|
| 1 | Retire Tool | State Change | reviewed | [take-a-tool-or-a-neighbor-out-of-the-shed/slices/retire-tool.md](take-a-tool-or-a-neighbor-out-of-the-shed/slices/retire-tool.md) |
| 2 | My Holds — tool retired | State View | continuation of `my-holds` | [take-a-tool-or-a-neighbor-out-of-the-shed/slices/my-holds.md](take-a-tool-or-a-neighbor-out-of-the-shed/slices/my-holds.md) |
| 3 | Tool Inventory — tool retired | State View | continuation of `tool-inventory` | [take-a-tool-or-a-neighbor-out-of-the-shed/slices/tool-inventory.md](take-a-tool-or-a-neighbor-out-of-the-shed/slices/tool-inventory.md) |
| 4 | Tool Catalog — tool retired | State View | continuation of `tool-catalog` | [take-a-tool-or-a-neighbor-out-of-the-shed/slices/tool-catalog.md](take-a-tool-or-a-neighbor-out-of-the-shed/slices/tool-catalog.md) |
| 5 | My Loans — tool retired | State View | continuation of `my-loans` | [take-a-tool-or-a-neighbor-out-of-the-shed/slices/my-loans.md](take-a-tool-or-a-neighbor-out-of-the-shed/slices/my-loans.md) |
| 6 | Record Tool Lost | State Change | draft | [take-a-tool-or-a-neighbor-out-of-the-shed/slices/record-tool-lost.md](take-a-tool-or-a-neighbor-out-of-the-shed/slices/record-tool-lost.md) |
| 7 | My Holds — tool lost | State View | continuation of `my-holds` | [take-a-tool-or-a-neighbor-out-of-the-shed/slices/my-holds.md](take-a-tool-or-a-neighbor-out-of-the-shed/slices/my-holds.md) |
| 8 | Tool Inventory — tool lost | State View | continuation of `tool-inventory` | [take-a-tool-or-a-neighbor-out-of-the-shed/slices/tool-inventory.md](take-a-tool-or-a-neighbor-out-of-the-shed/slices/tool-inventory.md) |
| 9 | Tool Catalog — tool lost | State View | continuation of `tool-catalog` | [take-a-tool-or-a-neighbor-out-of-the-shed/slices/tool-catalog.md](take-a-tool-or-a-neighbor-out-of-the-shed/slices/tool-catalog.md) |
| 10 | My Loans — tool lost | State View | continuation of `my-loans` | [take-a-tool-or-a-neighbor-out-of-the-shed/slices/my-loans.md](take-a-tool-or-a-neighbor-out-of-the-shed/slices/my-loans.md) |
| 11 | Undelivered Notices — tool lost | State View | continuation of `undelivered-notices` | [take-a-tool-or-a-neighbor-out-of-the-shed/slices/undelivered-notices.md](take-a-tool-or-a-neighbor-out-of-the-shed/slices/undelivered-notices.md) |
| 12 | Close Account | State Change | draft | [take-a-tool-or-a-neighbor-out-of-the-shed/slices/close-account.md](take-a-tool-or-a-neighbor-out-of-the-shed/slices/close-account.md) |
| 13 | My Holds — account closed | State View | continuation of `my-holds` | [take-a-tool-or-a-neighbor-out-of-the-shed/slices/my-holds.md](take-a-tool-or-a-neighbor-out-of-the-shed/slices/my-holds.md) |
| 14 | My Account — account closed | State View | continuation of `my-account` | [take-a-tool-or-a-neighbor-out-of-the-shed/slices/my-account.md](take-a-tool-or-a-neighbor-out-of-the-shed/slices/my-account.md) |
| 15 | Neighbors — account closed | State View | continuation of `neighbors` | [take-a-tool-or-a-neighbor-out-of-the-shed/slices/neighbors.md](take-a-tool-or-a-neighbor-out-of-the-shed/slices/neighbors.md) |

### Keep tools in working order
_5 slices · [diagram](storylines/keep-tools-in-working-order.svg)_

| # | Slice | Pattern | Status | Design doc |
|---|-------|---------|--------|------------|
| 1 | Schedule Tool Maintenance | State Change | reviewed | [keep-tools-in-working-order/slices/schedule-tool-maintenance.md](keep-tools-in-working-order/slices/schedule-tool-maintenance.md) |
| 2 | Tool Maintenance | State View | reviewed | [keep-tools-in-working-order/slices/tool-maintenance.md](keep-tools-in-working-order/slices/tool-maintenance.md) |
| 3 | Tool Catalog — maintenance scheduled | State View | continuation of `tool-catalog` | [keep-tools-in-working-order/slices/tool-catalog.md](keep-tools-in-working-order/slices/tool-catalog.md) |
| 4 | Complete Tool Maintenance | State Change | reviewed | [keep-tools-in-working-order/slices/complete-tool-maintenance.md](keep-tools-in-working-order/slices/complete-tool-maintenance.md) |
| 5 | Tool Catalog — maintenance completed | State View | continuation of `tool-catalog` | [keep-tools-in-working-order/slices/tool-catalog.md](keep-tools-in-working-order/slices/tool-catalog.md) |
<!-- GENERATED:slices:end -->

## Status
See [`.event-modeling.md`](.event-modeling.md) for current phase, decisions, and open questions.
