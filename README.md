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
| # | Slice | Storyline | Pattern | Status | Reviewed by | Ratified by | Owner | Tracking | Implemented in | Conformed | Design doc |
|---|-------|-----------|---------|--------|-------------|-------------|-------|----------|----------------|-----------|------------|
| 1 | Add Tool | Stock the shed | State Change | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/pull/20 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/add-tool.md](slices/add-tool.md) |
| 2 | Tool Inventory | Stock the shed | State View | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/8c79bc377dd5fb3c1f345ae36773509b3ac82d37 | v1 @ 49d67795aabbe79704390bd08ef9925876074139 | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 3 | Tool Catalog | Stock the shed | State View | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/01689d1fc90e13b2356e10def6605b8c08aff62a | v1 @ 49d67795aabbe79704390bd08ef9925876074139 | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 4 | Register Neighbor | Join the shed | State Change | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/pull/5 | v1 @ d78f8326a04b4eda4da5c755223272103eed1491 | [slices/register-neighbor.md](slices/register-neighbor.md) |
| 5 | Tool Inventory — neighbor registered | Join the shed | State View | continuation of `tool-inventory` | — | — | — | — | — | — | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 6 | Neighbors | Join the shed | State View | draft | — | — | — | — | https://github.com/milehimikey/toolshed-v2/commit/dab5cb19e98edd34173c2419f853fb3f5e2c61e0 | v1 @ 49d67795aabbe79704390bd08ef9925876074139 | [slices/neighbors.md](slices/neighbors.md) |
| 7 | My Account | Join the shed | State View | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/bdeaac10b78e42ab294c71f441d152974b09da35 | v1 @ 49d67795aabbe79704390bd08ef9925876074139 | [slices/my-account.md](slices/my-account.md) |
| 8 | Change Email | Join the shed | State Change | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/pull/12 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/change-email.md](slices/change-email.md) |
| 9 | My Account — email changed | Join the shed | State View | continuation of `my-account` | — | — | — | — | — | — | [slices/my-account.md](slices/my-account.md) |
| 10 | Neighbors — email changed | Join the shed | State View | continuation of `neighbors` | — | — | — | — | — | — | [slices/neighbors.md](slices/neighbors.md) |
| 11 | Check Out Tool | Borrow and return a tool | State Change | draft | — | — | — | — | https://github.com/milehimikey/toolshed-v2/pull/8 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/check-out-tool.md](slices/check-out-tool.md) |
| 12 | Tool Inventory — tool checked out | Borrow and return a tool | State View | continuation of `tool-inventory` | — | — | — | — | — | — | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 13 | Tool Catalog — checked out | Borrow and return a tool | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 14 | My Loans | Borrow and return a tool | State View | draft | — | — | — | — | https://github.com/milehimikey/toolshed-v2/commit/b1485c8a98f0f2a4b4f48fce0be87ad4cfb6b284 | v1 @ 49d67795aabbe79704390bd08ef9925876074139 | [slices/my-loans.md](slices/my-loans.md) |
| 15 | Extend Loan | Borrow and return a tool | State Change | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/pull/6 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/extend-loan.md](slices/extend-loan.md) |
| 16 | Tool Inventory — loan extended | Borrow and return a tool | State View | continuation of `tool-inventory` | — | — | — | — | — | — | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 17 | My Loans — loan extended | Borrow and return a tool | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 18 | Tool Catalog — loan extended | Borrow and return a tool | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 19 | Return Tool | Borrow and return a tool | State Change | draft | — | — | — | — | https://github.com/milehimikey/toolshed-v2/pull/15 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/return-tool.md](slices/return-tool.md) |
| 20 | Tool Inventory — tool returned | Borrow and return a tool | State View | continuation of `tool-inventory` | — | — | — | — | — | — | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 21 | Tool Catalog — tool returned | Borrow and return a tool | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 22 | My Loans — tool returned | Borrow and return a tool | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 23 | Overdue Loans To Notify | Chase an overdue loan | State View | draft | — | — | — | — | https://github.com/milehimikey/toolshed-v2/commit/6e4e0f39ac24e1842a1103d83dbc9153221d4683 | v1 @ 49d67795aabbe79704390bd08ef9925876074139 | [slices/overdue-loans-to-notify.md](slices/overdue-loans-to-notify.md) |
| 24 | Send Overdue Notice | Chase an overdue loan | Translation | draft | — | — | — | — | https://github.com/milehimikey/toolshed-v2/commit/4560d39537428be5c70e45b83caed07789ea18cb | v1 @ 49d67795aabbe79704390bd08ef9925876074139 | [slices/send-overdue-notice.md](slices/send-overdue-notice.md) |
| 25 | My Loans — overdue notice sent | Chase an overdue loan | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 26 | Record Overdue Notice Failure | Chase an overdue loan | Translation | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/pull/11 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/record-overdue-notice-failure.md](slices/record-overdue-notice-failure.md) |
| 27 | My Loans — overdue notice failed | Chase an overdue loan | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 28 | Undelivered Notices | Chase an overdue loan | State View | draft | — | — | — | — | https://github.com/milehimikey/toolshed-v2/commit/4831929b38a3b68b09d2ecbbff122e795f05b971 | v1 @ 49d67795aabbe79704390bd08ef9925876074139 | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 29 | Place Hold | Hold a tool for pickup | State Change | draft | — | — | — | — | https://github.com/milehimikey/toolshed-v2/pull/9 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/place-hold.md](slices/place-hold.md) |
| 30 | My Holds | Hold a tool for pickup | State View | draft | — | — | — | — | https://github.com/milehimikey/toolshed-v2/commit/d9518711311de805e50704c325a661b527b2845b | v1 @ 49d67795aabbe79704390bd08ef9925876074139 | [slices/my-holds.md](slices/my-holds.md) |
| 31 | Tool Catalog — hold placed | Hold a tool for pickup | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 32 | My Loans — hold placed | Hold a tool for pickup | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 33 | Cancel Hold | Hold a tool for pickup | State Change | draft | — | — | — | — | https://github.com/milehimikey/toolshed-v2/pull/10 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/cancel-hold.md](slices/cancel-hold.md) |
| 34 | My Holds — hold cancelled | Hold a tool for pickup | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 35 | Tool Catalog — hold cancelled | Hold a tool for pickup | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 36 | My Loans — hold cancelled | Hold a tool for pickup | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 37 | Undelivered Notices — hold cancelled | Hold a tool for pickup | State View | continuation of `undelivered-notices` | — | — | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 38 | Holds To Fulfill | Hold a tool for pickup | State View | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/0ca7c86d5efde09122a8e2179472932795bd5764 | v1 @ 49d67795aabbe79704390bd08ef9925876074139 | [slices/holds-to-fulfill.md](slices/holds-to-fulfill.md) |
| 39 | Fulfill Hold | Hold a tool for pickup | Automation | draft | — | — | — | — | https://github.com/milehimikey/toolshed-v2/commit/03d92a92ecdff305310b869c9b4a173bdcd09fbd | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/fulfill-hold.md](slices/fulfill-hold.md) |
| 40 | Undelivered Notices — hold fulfilled | Hold a tool for pickup | State View | continuation of `undelivered-notices` | — | — | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 41 | Tool Catalog — hold fulfilled | Hold a tool for pickup | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 42 | My Holds — hold fulfilled | Hold a tool for pickup | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 43 | Pickup Notices To Send | Hold a tool for pickup | State View | draft | — | — | — | — | https://github.com/milehimikey/toolshed-v2/commit/e627d2500f6dd65a69323f893f4bfcce125921a9 | v1 @ 49d67795aabbe79704390bd08ef9925876074139 | [slices/pickup-notices-to-send.md](slices/pickup-notices-to-send.md) |
| 44 | Send Pickup Notice | Hold a tool for pickup | Translation | draft | — | — | — | — | https://github.com/milehimikey/toolshed-v2/commit/c44110594b70cc09e0ad028737b02133cf938b78 | v1 @ 49d67795aabbe79704390bd08ef9925876074139 | [slices/send-pickup-notice.md](slices/send-pickup-notice.md) |
| 45 | My Holds — pickup notice sent | Hold a tool for pickup | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 46 | Record Pickup Notice Failure | Hold a tool for pickup | Translation | draft | — | — | — | — | https://github.com/milehimikey/toolshed-v2/pull/19 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/record-pickup-notice-failure.md](slices/record-pickup-notice-failure.md) |
| 47 | My Holds — pickup notice failed | Hold a tool for pickup | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 48 | Undelivered Notices — pickup notice failed | Hold a tool for pickup | State View | continuation of `undelivered-notices` | — | — | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 49 | Pickups To Expire | Hold a tool for pickup | State View | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/58ab5d4257fbc545159e6b4e8398ed1938d5d54d | v1 @ 49d67795aabbe79704390bd08ef9925876074139 | [slices/pickups-to-expire.md](slices/pickups-to-expire.md) |
| 50 | Expire Hold | Hold a tool for pickup | Automation | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/33b880061c6bb7c2805599a53207dfe58c69bffe | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/expire-hold.md](slices/expire-hold.md) |
| 51 | My Loans — hold expired | Hold a tool for pickup | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 52 | My Holds — hold expired | Hold a tool for pickup | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 53 | Tool Catalog — hold expired | Hold a tool for pickup | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 54 | Undelivered Notices — hold expired | Hold a tool for pickup | State View | continuation of `undelivered-notices` | — | — | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 55 | Neighbors To Flag | Keep late returners in check | State View | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/5ceee111012aafc9e36695a74977c1a6a16958bb | v1 @ 49d67795aabbe79704390bd08ef9925876074139 | [slices/neighbors-to-flag.md](slices/neighbors-to-flag.md) |
| 56 | Flag Neighbor | Keep late returners in check | Automation | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/49e73dd7051c66f844a967c12b5c732ff88da063 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/flag-neighbor.md](slices/flag-neighbor.md) |
| 57 | Neighbors — neighbor flagged | Keep late returners in check | State View | continuation of `neighbors` | — | — | — | — | — | — | [slices/neighbors.md](slices/neighbors.md) |
| 58 | Suspend Neighbor | Keep late returners in check | State Change | draft | — | — | — | — | https://github.com/milehimikey/toolshed-v2/pull/18 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/suspend-neighbor.md](slices/suspend-neighbor.md) |
| 59 | Neighbors — neighbor suspended | Keep late returners in check | State View | continuation of `neighbors` | — | — | — | — | — | — | [slices/neighbors.md](slices/neighbors.md) |
| 60 | My Account — neighbor suspended | Keep late returners in check | State View | continuation of `my-account` | — | — | — | — | — | — | [slices/my-account.md](slices/my-account.md) |
| 61 | Reinstate Neighbor | Keep late returners in check | State Change | draft | — | — | — | — | https://github.com/milehimikey/toolshed-v2/pull/25 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/reinstate-neighbor.md](slices/reinstate-neighbor.md) |
| 62 | Neighbors — neighbor reinstated | Keep late returners in check | State View | continuation of `neighbors` | — | — | — | — | — | — | [slices/neighbors.md](slices/neighbors.md) |
| 63 | My Account — neighbor reinstated | Keep late returners in check | State View | continuation of `my-account` | — | — | — | — | — | — | [slices/my-account.md](slices/my-account.md) |
| 64 | Retire Tool | Take a tool or a neighbor out of the shed | State Change | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/pull/26 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/retire-tool.md](slices/retire-tool.md) |
| 65 | Tool Inventory — tool retired | Take a tool or a neighbor out of the shed | State View | continuation of `tool-inventory` | — | — | — | — | — | — | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 66 | Tool Catalog — tool retired | Take a tool or a neighbor out of the shed | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 67 | My Holds — tool retired | Take a tool or a neighbor out of the shed | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 68 | My Loans — tool retired | Take a tool or a neighbor out of the shed | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 69 | Undelivered Notices — tool retired | Take a tool or a neighbor out of the shed | State View | continuation of `undelivered-notices` | — | — | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 70 | Record Tool Lost | Take a tool or a neighbor out of the shed | State Change | draft | — | — | — | — | https://github.com/milehimikey/toolshed-v2/pull/24 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/record-tool-lost.md](slices/record-tool-lost.md) |
| 71 | Tool Inventory — tool lost | Take a tool or a neighbor out of the shed | State View | continuation of `tool-inventory` | — | — | — | — | — | — | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 72 | Tool Catalog — tool lost | Take a tool or a neighbor out of the shed | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 73 | My Loans — tool lost | Take a tool or a neighbor out of the shed | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 74 | My Holds — tool lost | Take a tool or a neighbor out of the shed | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 75 | Undelivered Notices — tool lost | Take a tool or a neighbor out of the shed | State View | continuation of `undelivered-notices` | — | — | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 76 | Close Account | Take a tool or a neighbor out of the shed | State Change | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/pull/27 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/close-account.md](slices/close-account.md) |
| 77 | My Account — account closed | Take a tool or a neighbor out of the shed | State View | continuation of `my-account` | — | — | — | — | — | — | [slices/my-account.md](slices/my-account.md) |
| 78 | Neighbors — account closed | Take a tool or a neighbor out of the shed | State View | continuation of `neighbors` | — | — | — | — | — | — | [slices/neighbors.md](slices/neighbors.md) |
| 79 | My Holds — account closed | Take a tool or a neighbor out of the shed | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 80 | Tool Catalog — account closed | Take a tool or a neighbor out of the shed | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 81 | My Loans — account closed | Take a tool or a neighbor out of the shed | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 82 | Undelivered Notices — account closed | Take a tool or a neighbor out of the shed | State View | continuation of `undelivered-notices` | — | — | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
<!-- GENERATED:slices:end -->

## Status
See [`.event-modeling.md`](.event-modeling.md) for current phase, decisions, and open questions.
