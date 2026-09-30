# Pilot notes — toolshed-v2

**What this is:** a ground-up rebuild of toolshed by an OpenRig team — the `em-factory` rig
(`~/projects/em-factory-rig`) — run as a semi-real rehearsal for using OpenRig on larger em
projects. Started 2026-09-29.

**Honesty label, stated up front:** this is a **self-pilot and a rebuild**. The ratifier (Mike
Key) also ratified toolshed v1 and remembers its answers, so discovery here is not naïve. The
agent seats are kept blind to v1 (see below); the human is not. The carried-forward
constitution names some v1 decisions in its examples and NFR baselines (field names, the phone
number as the neighbor key) — a known leak into the delivery pod, accepted with the constitution.

## Selection record

- **Capability:** neighborhood tool lending — checkout, return, holds, due windows. Stated
  invariants: a tool can't be checked out twice; a loan has a due window; holds queue in order;
  overdue loans block new checkouts. (The same one-line brief v1 started from — the brief, not
  v1's model.)
- **Entry path:** full-loop, greenfield. `em scaffold toolshed` (em 1.13.0), `em skill install`,
  `em ci init toolshed.em --tests src/test/kotlin`, CODEOWNERS on `slices/**`.
- **Constitution:** carried forward verbatim from toolshed v1 (ratified by Mike Key 2026-09-06,
  amendments 1–5 of 2026-09-07). The stack is not what this pilot tests.

## Roles

- **Domain expert and sole ratifier:** Mike Key, live. Only Mike runs `em slice ratify` and
  merges PRs.
- **Facilitator:** `model.lead` seat (modeling orchestrator).
- **Implementing-agent operator:** `deliver.lead` seat (delivery orchestrator).
- The playbook's one required separation — ratifier ≠ implementing-agent operator — **holds**.
  The v1 facilitator/operator collapse does **not** recur: they are separate seats.
- **Conform-cadence owner:** `oversight.steward` seat.

## Blind-rebuild rule

No seat reads `~/projects/toolshed` (v1). The domain comes from the human only. Any seat that
finds itself reading v1 stops and says so; the steward records it here.

## Week log

### Week 0 — 2026-09-29

Repo scaffolded, constitution carried forward, rig authored and validated.
