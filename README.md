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
| 1 | Add Tool | State Change | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/pull/20 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/add-tool.md](slices/add-tool.md) |
| 2 | Tool Inventory | State View | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/8c79bc377dd5fb3c1f345ae36773509b3ac82d37 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 3 | Tool Catalog | State View | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/01689d1fc90e13b2356e10def6605b8c08aff62a | v1 @ d78f8326a04b4eda4da5c755223272103eed1491 | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 4 | Register Neighbor | State Change | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/pull/5 | v1 @ d78f8326a04b4eda4da5c755223272103eed1491 | [slices/register-neighbor.md](slices/register-neighbor.md) |
| 5 | Tool Inventory — neighbor registered | State View | continuation of `tool-inventory` | — | — | — | — | — | — | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 6 | Neighbors | State View | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/dab5cb19e98edd34173c2419f853fb3f5e2c61e0 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/neighbors.md](slices/neighbors.md) |
| 7 | My Account | State View | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/bdeaac10b78e42ab294c71f441d152974b09da35 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/my-account.md](slices/my-account.md) |
| 8 | Check Out Tool | State Change | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/pull/8 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/check-out-tool.md](slices/check-out-tool.md) |
| 9 | Tool Inventory — tool checked out | State View | continuation of `tool-inventory` | — | — | — | — | — | — | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 10 | Tool Catalog — checked out | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 11 | My Loans | State View | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/b1485c8a98f0f2a4b4f48fce0be87ad4cfb6b284 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/my-loans.md](slices/my-loans.md) |
| 12 | Extend Loan | State Change | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/pull/6 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/extend-loan.md](slices/extend-loan.md) |
| 13 | Tool Inventory — loan extended | State View | continuation of `tool-inventory` | — | — | — | — | — | — | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 14 | My Loans — loan extended | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 15 | Tool Catalog — loan extended | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 16 | Place Hold | State Change | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/pull/9 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/place-hold.md](slices/place-hold.md) |
| 17 | My Holds | State View | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/d9518711311de805e50704c325a661b527b2845b | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/my-holds.md](slices/my-holds.md) |
| 18 | Tool Catalog — hold placed | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 19 | My Loans — hold placed | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 20 | Cancel Hold | State Change | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/pull/10 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/cancel-hold.md](slices/cancel-hold.md) |
| 21 | My Holds — hold cancelled | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 22 | Tool Catalog — hold cancelled | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 23 | My Loans — hold cancelled | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 24 | Overdue Loans To Notify | State View | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/6e4e0f39ac24e1842a1103d83dbc9153221d4683 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/overdue-loans-to-notify.md](slices/overdue-loans-to-notify.md) |
| 25 | Send Overdue Notice | Translation | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/4560d39537428be5c70e45b83caed07789ea18cb | v1 @ d78f8326a04b4eda4da5c755223272103eed1491 | [slices/send-overdue-notice.md](slices/send-overdue-notice.md) |
| 26 | My Loans — overdue notice sent | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 27 | Record Overdue Notice Failure | Translation | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/pull/11 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/record-overdue-notice-failure.md](slices/record-overdue-notice-failure.md) |
| 28 | My Loans — overdue notice failed | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 29 | Undelivered Notices | State View | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/4831929b38a3b68b09d2ecbbff122e795f05b971 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 30 | Change Email | State Change | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/pull/12 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/change-email.md](slices/change-email.md) |
| 31 | My Account — email changed | State View | continuation of `my-account` | — | — | — | — | — | — | [slices/my-account.md](slices/my-account.md) |
| 32 | Neighbors — email changed | State View | continuation of `neighbors` | — | — | — | — | — | — | [slices/neighbors.md](slices/neighbors.md) |
| 33 | Undelivered Notices — email changed | State View | continuation of `undelivered-notices` | — | — | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 34 | Return Tool | State Change | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/pull/15 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/return-tool.md](slices/return-tool.md) |
| 35 | Undelivered Notices — tool returned | State View | continuation of `undelivered-notices` | — | — | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 36 | Tool Inventory — tool returned | State View | continuation of `tool-inventory` | — | — | — | — | — | — | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 37 | Tool Catalog — tool returned | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 38 | My Loans — tool returned | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 39 | Holds To Fulfill | State View | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/0ca7c86d5efde09122a8e2179472932795bd5764 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/holds-to-fulfill.md](slices/holds-to-fulfill.md) |
| 40 | Fulfill Hold | Automation | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/03d92a92ecdff305310b869c9b4a173bdcd09fbd | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/fulfill-hold.md](slices/fulfill-hold.md) |
| 41 | Undelivered Notices — hold fulfilled | State View | continuation of `undelivered-notices` | — | — | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 42 | Tool Catalog — hold fulfilled | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 43 | My Holds — hold fulfilled | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 44 | Pickup Notices To Send | State View | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/e627d2500f6dd65a69323f893f4bfcce125921a9 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/pickup-notices-to-send.md](slices/pickup-notices-to-send.md) |
| 45 | Send Pickup Notice | Translation | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/c44110594b70cc09e0ad028737b02133cf938b78 | v1 @ d78f8326a04b4eda4da5c755223272103eed1491 | [slices/send-pickup-notice.md](slices/send-pickup-notice.md) |
| 46 | My Holds — pickup notice sent | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 47 | Record Pickup Notice Failure | Translation | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/pull/19 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/record-pickup-notice-failure.md](slices/record-pickup-notice-failure.md) |
| 48 | My Holds — pickup notice failed | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 49 | Undelivered Notices — pickup notice failed | State View | continuation of `undelivered-notices` | — | — | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 50 | Pickups To Expire | State View | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/58ab5d4257fbc545159e6b4e8398ed1938d5d54d | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/pickups-to-expire.md](slices/pickups-to-expire.md) |
| 51 | Expire Hold | Automation | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/33b880061c6bb7c2805599a53207dfe58c69bffe | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/expire-hold.md](slices/expire-hold.md) |
| 52 | My Loans — hold expired | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 53 | My Holds — hold expired | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 54 | Tool Catalog — hold expired | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 55 | Undelivered Notices — hold expired | State View | continuation of `undelivered-notices` | — | — | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 56 | Neighbors To Flag | State View | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/5ceee111012aafc9e36695a74977c1a6a16958bb | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/neighbors-to-flag.md](slices/neighbors-to-flag.md) |
| 57 | Flag Neighbor | Automation | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/49e73dd7051c66f844a967c12b5c732ff88da063 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/flag-neighbor.md](slices/flag-neighbor.md) |
| 58 | Flagged Neighbors | State View | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/e06c9c00d6904dd6201f8b7f4ea5bfe009fecc14 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/flagged-neighbors.md](slices/flagged-neighbors.md) |
| 59 | Suspend Neighbor | State Change | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/pull/18 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/suspend-neighbor.md](slices/suspend-neighbor.md) |
| 60 | Flagged Neighbors — neighbor suspended | State View | continuation of `flagged-neighbors` | — | — | — | — | — | — | [slices/flagged-neighbors.md](slices/flagged-neighbors.md) |
| 61 | Suspended Neighbors | State View | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/commit/030182873ff44081469907042fd8424d6aaf2364 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/suspended-neighbors.md](slices/suspended-neighbors.md) |
| 62 | Neighbors — neighbor suspended | State View | continuation of `neighbors` | — | — | — | — | — | — | [slices/neighbors.md](slices/neighbors.md) |
| 63 | My Account — neighbor suspended | State View | continuation of `my-account` | — | — | — | — | — | — | [slices/my-account.md](slices/my-account.md) |
| 64 | Reinstate Neighbor | State Change | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/pull/25 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/reinstate-neighbor.md](slices/reinstate-neighbor.md) |
| 65 | Suspended Neighbors — neighbor reinstated | State View | continuation of `suspended-neighbors` | — | — | — | — | — | — | [slices/suspended-neighbors.md](slices/suspended-neighbors.md) |
| 66 | Neighbors — neighbor reinstated | State View | continuation of `neighbors` | — | — | — | — | — | — | [slices/neighbors.md](slices/neighbors.md) |
| 67 | My Account — neighbor reinstated | State View | continuation of `my-account` | — | — | — | — | — | — | [slices/my-account.md](slices/my-account.md) |
| 68 | Retire Tool | State Change | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/pull/26 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/retire-tool.md](slices/retire-tool.md) |
| 69 | Tool Inventory — tool retired | State View | continuation of `tool-inventory` | — | — | — | — | — | — | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 70 | Tool Catalog — tool retired | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 71 | My Holds — tool retired | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 72 | My Loans — tool retired | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 73 | Undelivered Notices — tool retired | State View | continuation of `undelivered-notices` | — | — | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 74 | Record Tool Lost | State Change | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/pull/24 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/record-tool-lost.md](slices/record-tool-lost.md) |
| 75 | Tool Inventory — tool lost | State View | continuation of `tool-inventory` | — | — | — | — | — | — | [slices/tool-inventory.md](slices/tool-inventory.md) |
| 76 | Tool Catalog — tool lost | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 77 | My Loans — tool lost | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 78 | My Holds — tool lost | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 79 | Undelivered Notices — tool lost | State View | continuation of `undelivered-notices` | — | — | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
| 80 | Close Account | State Change | implemented | Mike Key | Mike Key | — | — | https://github.com/milehimikey/toolshed-v2/pull/27 | v1 @ 54b94c75b4528330dea08fa62ca7fe63b7f9cc09 | [slices/close-account.md](slices/close-account.md) |
| 81 | Flagged Neighbors — account closed | State View | continuation of `flagged-neighbors` | — | — | — | — | — | — | [slices/flagged-neighbors.md](slices/flagged-neighbors.md) |
| 82 | Suspended Neighbors — account closed | State View | continuation of `suspended-neighbors` | — | — | — | — | — | — | [slices/suspended-neighbors.md](slices/suspended-neighbors.md) |
| 83 | My Account — account closed | State View | continuation of `my-account` | — | — | — | — | — | — | [slices/my-account.md](slices/my-account.md) |
| 84 | Neighbors — account closed | State View | continuation of `neighbors` | — | — | — | — | — | — | [slices/neighbors.md](slices/neighbors.md) |
| 85 | My Holds — account closed | State View | continuation of `my-holds` | — | — | — | — | — | — | [slices/my-holds.md](slices/my-holds.md) |
| 86 | Tool Catalog — account closed | State View | continuation of `tool-catalog` | — | — | — | — | — | — | [slices/tool-catalog.md](slices/tool-catalog.md) |
| 87 | My Loans — account closed | State View | continuation of `my-loans` | — | — | — | — | — | — | [slices/my-loans.md](slices/my-loans.md) |
| 88 | Undelivered Notices — account closed | State View | continuation of `undelivered-notices` | — | — | — | — | — | — | [slices/undelivered-notices.md](slices/undelivered-notices.md) |
<!-- GENERATED:slices:end -->

## Status
See [`.event-modeling.md`](.event-modeling.md) for current phase, decisions, and open questions.
