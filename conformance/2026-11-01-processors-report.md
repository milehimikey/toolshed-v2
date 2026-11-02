# Conformance Report — Toolshed — 2026-11-01 (processors)

- **Model:** `toolshed.em`
- **Target repo:** /Users/mikey/projects/toolshed-v2 (this repository) @ `49d67795aabbe79704390bd08ef9925876074139`
- **Previous conformance revision:** `d78f8326a04b4eda4da5c755223272103eed1491` (2026-11-01, `conformance/2026-11-01-followup-report.md`)
- **Scope:** diff-scoped since d78f832 — PR #44 (follow-up conformance records, outbox comment tidy) and PR #45 (every projection on its fixture-named dedicated processor in the running app)
- **Slices checked:** tool-catalog, tool-inventory, neighbors, my-account, my-loans, my-holds, overdue-loans-to-notify, undelivered-notices, holds-to-fulfill, pickup-notices-to-send, pickups-to-expire, neighbors-to-flag, flagged-neighbors, suspended-neighbors, send-overdue-notice, send-pickup-notice
- **Slices skipped (not yet implemented):** none

### Run metadata

- `em conform-scope` mapped no changed path mechanically (every `implementedIn:` is a URL), so the slices were placed by hand from the changed code packages: each `<view>/<View>Configuration.kt` → its view slice (14), `sendoverduenotice/` and `sendpickupnotice/` → the two translation slices. `axon/DedicatedProcessor.kt` and the two `axon/*OverAxonServerTest.kt` files are foundation code shared by all of them. The remaining changed paths are the previous sweep's own records and frontmatter.
- The changes are wiring and comments only: each slice's `configure` builds the same `EventProcessorModule` under a now-public constant, and a new `@Configuration` pins the running app's processor to that constant. No command, event, view field, reaction or endpoint changed, so the scratch model is byte-identical to the canonical model and `em diff --json` reports no changes.
- `em coverage --tests src/test/kotlin`: 299/299 invariants cited.

## Summary

| Surface | Real drift | Model gap | Internal inconsistency | Accepted divergence | Unpropagated delta | Uncertainty |
|---|---|---|---|---|---|---|
| Structural | 0 | 0 | — | 0 | 0 | 0 |
| Spec | 0 | 0 | — | 0 | 0 | 0 |
| Internal | — | — | 0 | 0 | — | 0 |

Clean. PR #45 closed the observation carried from the first two sweeps: the running app no longer runs any handler on a package-named processor, and fixture and app agree on every processor name. No slice doc says anything about processor names, so there is no spec claim to check beyond the structural one above.

## Findings

none

## Scenario / invariant coverage

Only what the PRs changed is re-walked; the first sweep's per-invariant tables for these slices still stand.

| Claim | Type | Enforcement site | Test site | Status |
|---|---|---|---|---|
| Each of the 14 projections runs alone on a pooled-streaming processor named as its fixture names it, in the running app | wiring | `src/main/kotlin/com/toolshed/axon/DedicatedProcessor.kt` (helper); `<View>ProjectionProcessorConfiguration` at the foot of each of the 14 `<View>Configuration.kt` files, each with a public `PROCESSOR` constant used by both `configure` and the `@Bean` | `src/test/kotlin/com/toolshed/axon/DedicatedProcessorsOverAxonServerTest.kt` — parameterized over all 14 projections; plus the two outbox cases (INV-OVN-4, INV-PKN-4) and a check that no package-named processor exists and exactly 16 processors run | covered |
| Five fixture processor names aligned to `Projection_<View>_Processor` (MyAccount, MyLoans, NeighborsToFlag, ToolCatalog, ToolInventory) | naming | the same files | nothing referenced the old strings (grep); full suite 117 suites / 1127 tests green on PR #45 | covered |
| Outbox KDoc states the at-most-one-per-key guarantee once | doc comment | `sendoverduenotice/OverdueNoticeOutbox.kt:23`, `sendpickupnotice/PickupNoticeOutbox.kt:23` | — | covered |

## Uncertainties

none new. Still open by design from the first sweep: the Record Tool Lost red note and the six doc repairs deferred to v2.

## Applied this run

none — no findings to rule. Certification: `em slice conform` for the sixteen in-scope slices at `49d67795aabbe79704390bd08ef9925876074139`; `em state set-conformance` at the same revision.
