# Conformance Report — Toolshed — 2026-11-01 (follow-up)

- **Model:** `toolshed.em`
- **Target repo:** /Users/mikey/projects/toolshed-v2 (this repository) @ `d78f8326a04b4eda4da5c755223272103eed1491`
- **Previous conformance revision:** `54b94c75b4528330dea08fa62ca7fe63b7f9cc09` (2026-11-01, `conformance/2026-11-01-report.md`)
- **Scope:** diff-scoped since 54b94c7 — the three squash-merged PRs that applied that sweep's code rulings (#41 Tool Catalog dropped set, #42 concurrent sign-up test, #43 outboxes on dedicated processors)
- **Slices checked:** tool-catalog, send-overdue-notice, send-pickup-notice, register-neighbor
- **Slices skipped (not yet implemented):** none

### Run metadata

- `em conform-scope` mapped no changed path mechanically (every `implementedIn:` is a PR or commit URL, which matches nothing by design), so the four slices were placed by hand from the changed code paths: `toolcatalog/` → tool-catalog; `sendoverduenotice/` → send-overdue-notice; `sendpickupnotice/` → send-pickup-notice; `registerneighbor/` (test only) → register-neighbor. The other changed paths are this sweep's own artefacts (state file, constitution note, conformance records, `model-versions/v1.json`, per-slice `conformed*` frontmatter and deferred questions, the re-rendered diagram) and map to no slice's code.
- The repository squash-merges, so the first sweep's four local commits reached `main` folded into PR #41's squash commit; nothing was lost (`git diff` between the two heads showed only the three PRs' code). Future `em slice conform --at` values are merge commits on `main`, which stay reachable.
- Scratch model `toolshed-asis.em` seeded from the canonical model; the four in-scope slices are byte-identical to it because none of the three PRs changed a command, event, view field, reaction or endpoint. `em diff --json` reports no changes.
- `em coverage --tests src/test/kotlin`: 299/299 invariants cited; the four in-scope slices are 14/14, 9/9, 10/10 and 9/9.

## Summary

| Surface | Real drift | Model gap | Internal inconsistency | Accepted divergence | Unpropagated delta | Uncertainty |
|---|---|---|---|---|---|---|
| Structural | 0 | 0 | — | 0 | 0 | 0 |
| Spec | 0 | 0 | — | 0 | 0 | 0 |
| Internal | — | — | 0 | 0 | — | 0 |

Clean. The three PRs did exactly what the first sweep's rulings asked and nothing else: finding 4 is fixed in code and tested, and two of that sweep's uncertainties (production processor assignment for the outboxes; concurrent same-email sign-ups) now have evidence and tests. No new behaviour was introduced.

## Findings

none

## Scenario / invariant coverage

Only what the PRs changed is re-walked here; the first sweep's per-invariant tables for these slices still stand for everything else.

### tool-catalog (state-view)

| Claim | Type | Enforcement site | Test site | Status |
|---|---|---|---|---|
| INV-TCAT-12 (apply twice = apply once), redelivered Tool Added after retire/lost | invariant | `src/main/kotlin/com/toolshed/toolcatalog/ToolCatalogRepository.kt:46` (`dropped` set), `:58` (add is a no-op for a dropped tool), `:128-131` (`drop()` on retire and lost) | `src/test/kotlin/com/toolshed/toolcatalog/ToolCatalogProjectionTest.kt:319,341` | covered — first-sweep finding 4 resolved |

### send-overdue-notice (translation)

| Claim | Type | Enforcement site | Test site | Status |
|---|---|---|---|---|
| INV-OVN-4 — a refused hand-off stalls only the outbox: the outbox alone rides a dedicated pooled-streaming processor in the running app | invariant | `src/main/kotlin/com/toolshed/sendoverduenotice/SendOverdueNoticeConfiguration.kt:59-66` (`OverdueNoticeOutboxProcessorConfiguration`, `EventProcessorDefinition.pooledStreaming(OUTBOX_PROCESSOR).assigningHandlers { OverdueNoticeOutbox }`) | `src/test/kotlin/com/toolshed/axon/NoticeOutboxProcessorsOverAxonServerTest.kt:88` | covered — first-sweep uncertainty "processor assignment in the running app" resolved |
| Outbox KDoc describes restart behaviour accurately | doc comment | `src/main/kotlin/com/toolshed/sendoverduenotice/OverdueNoticeOutbox.kt:23` | — | covered — first-sweep uncertainty "outbox restart semantics" resolved by ruling (accepted; provider dedupes per key) |

### send-pickup-notice (translation)

| Claim | Type | Enforcement site | Test site | Status |
|---|---|---|---|---|
| INV-PKN-4 — same as above for the pickup outbox | invariant | `src/main/kotlin/com/toolshed/sendpickupnotice/SendPickupNoticeConfiguration.kt:62-69` (`PickupNoticeOutboxProcessorConfiguration`) | `src/test/kotlin/com/toolshed/axon/NoticeOutboxProcessorsOverAxonServerTest.kt:93` | covered |
| Outbox KDoc describes restart behaviour accurately | doc comment | `src/main/kotlin/com/toolshed/sendpickupnotice/PickupNoticeOutbox.kt:23` | — | covered |

### register-neighbor (state-change)

| Claim | Type | Enforcement site | Test site | Status |
|---|---|---|---|---|
| INV-REG-4 — two sign-ups with one email at the same moment record at most one account | invariant | `src/main/kotlin/com/toolshed/registerneighbor/NeighborRegistryState.kt:71-80` (DCB criteria) + `RegisterNeighborCommandHandler.kt:30` (append on the same context) — unchanged | `src/test/kotlin/com/toolshed/registerneighbor/RegisterNeighborConcurrencyOverAxonServerTest.kt:78` (8 contenders × 3 rounds over Axon Server: one 201, seven 422 email-in-use, one Neighbor Registered in the store) | covered — first-sweep uncertainty "concurrent sign-ups" resolved |

## Uncertainties

none new. Carried from the first sweep and still open by design, not re-raised here: the remaining first-sweep rulings pending their v2 doc deltas (findings 1, 5–8 and the INV-MYL-12 rephrase), and the observation from PR #43 that every projection also runs on a package-named processor in the app (not a finding against any slice doc, which say nothing about processor names; a candidate for a future ruling on whether fixture and app processor names should match everywhere).

## Applied this run

none — no findings to rule. Certification: `em slice conform` for the four in-scope slices at `d78f8326a04b4eda4da5c755223272103eed1491`; `em state set-conformance` at the same revision.
