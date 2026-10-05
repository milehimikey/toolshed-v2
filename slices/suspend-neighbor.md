---
schemaVersion: 1
pattern: state-change
swimlane: Shed Manager → Neighbors
status: draft
version: 1
---
# Slice: Suspend Neighbor

![Diagram](./suspend-neighbor.svg)

## Intent
Let the shed manager suspend a flagged neighbor, so someone who keeps returning tools late stops
borrowing until the shed manager reinstates them. The system flags; the shed manager decides.

## Invariants / Business Rules
- **INV-SUS-1:** Only the shed manager suspends a neighbor, from Flagged Neighbors.
  - The system never suspends anyone. Automatic suspension at the late-return threshold was
    turned down.
- **INV-SUS-2:** Only an active neighbor can be suspended.
  - A neighbor who is already suspended can't be suspended again. A neighbor whose account is
    closed can't be suspended, because closed is final.
- **INV-SUS-3:** A suspension lasts until the shed manager reinstates the neighbor.
  - It never lifts by itself after a set time, and it is never permanent.
- **INV-SUS-4:** A suspended neighbor can't check out a tool, extend a loan, or place a new hold.
  - That includes collecting a tool set aside for them. A set-aside they can't collect expires
    after its 3 days, as any other.
- **INV-SUS-5:** A suspended neighbor keeps what they already have.
  - Their loans stay out, and they can return them. Their holds keep their places in the queue,
    and they can cancel them.
- **INV-SUS-6:** Once suspended, the neighbor leaves Flagged Neighbors and appears on Suspended
  Neighbors. The Neighbors screen and the neighbor's own My Account show them as suspended.

## Scenarios (Given / When / Then)
- **Happy path**
  - **Given:** Ana is active and on Flagged Neighbors after three late returns within 90 days.
    She has the ladder on loan and a hold waiting on the cordless drill.
  - **When:** the shed manager suspends Ana from Flagged Neighbors
  - **Then:** Neighbor Suspended is recorded for Ana. She leaves Flagged Neighbors and appears on
    Suspended Neighbors, and the Neighbors screen and her My Account show her as suspended. She
    still has the ladder on loan, and her hold on the drill keeps its place.

## Open Questions
- [ ] Can the shed manager suspend a neighbor who hasn't been flagged?
  - As modeled, Suspend is offered only on Flagged Neighbors. But a neighbor suspended when they
    closed their account can sign up again with a different email, and the shed manager is meant
    to be able to suspend that new account. It won't have been flagged.
  - Options: (a) also offer Suspend on the Neighbors screen, for any active neighbor (a model
    change: Neighbors also triggers Suspend Neighbor); (b) flagged neighbors only.
  - Recommendation: (a). Without it, the shed manager can't act on the known gap in sign-up.
- [ ] Is the neighbor told by email that they've been suspended?
  - Today they see it on My Account, and in the reason given when a checkout, extension or hold
    is refused.
  - Recommendation: no email. My Account and the refusal reason are enough, and the shed is a
    neighborhood where the shed manager can talk to them.
- [ ] Does the shed manager record a reason or note with the suspension?
  - Recommendation: no. The flag and its late returns are the reason, and nothing on record asks
    for a note.
