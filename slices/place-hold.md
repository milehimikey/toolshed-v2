---
schemaVersion: 1
pattern: state-change
swimlane: Neighbor → Holds
status: draft
version: 1
---
# Slice: Place Hold

![Diagram](./place-hold.svg)

## Intent
Let a neighbor get in line for a tool that someone else has, by placing a hold from the Tool
Catalog. When the tool comes back it is set aside for holders in the order they placed their
holds.

## Invariants / Business Rules
- **INV-HOLD-1:** A neighbor places a hold themselves, from the Tool Catalog.
- **INV-HOLD-2:** A hold can be placed only on a tool that is checked out or set aside.
  - A hold on an available tool is refused. The neighbor checks it out instead.
- **INV-HOLD-3:** A hold can't be placed on a retired tool.
  - Retiring a tool ends every hold on it.
- **INV-HOLD-4:** A neighbor whose account is closed can't place a hold.
  - Closing an account ends all of that neighbor's holds.
- **INV-HOLD-5:** A new hold joins the back of the tool's queue. Holds on a tool are fulfilled in
  the order they were placed.
- **INV-HOLD-6:** A hold stays waiting until it is fulfilled, cancelled, or ended because the tool
  was retired or the holder closed their account.
  - Once fulfilled, the tool is set aside for the holder for 3 days. The hold ends when the
    holder checks the tool out, or expires if they don't.
  - If the holder at the front of the queue checks the tool out before their hold is fulfilled,
    that checkout also ends their hold.
- **INV-HOLD-7:** A neighbor can have only one hold on a given tool.
  - A second hold by the same neighbor on the same tool is refused, whether their first hold is
    waiting or set aside.
- **INV-HOLD-8:** A neighbor can't place a hold on a tool they have on loan themselves.
- **INV-HOLD-9:** A suspended neighbor can't place a new hold.
  - Holds they placed before the suspension keep their place in the queue.
- **INV-HOLD-10:** A neighbor with an overdue loan may place a hold.
  - The overdue loan stops them collecting the tool if it is set aside for them, not placing
    the hold.
- **INV-HOLD-11:** There is no limit on how many holds one neighbor can have at once.
- **INV-HOLD-12:** A neighbor who isn't eligible to hold a tool is refused with a reason they can
  see.
  - The Tool Catalog is the same for every neighbor, so it offers Place Hold on every checked-out
    or set-aside tool. It can't hide the action from a neighbor who may not use it.
  - The reasons are: they already hold this tool (INV-HOLD-7), they have it on loan
    (INV-HOLD-8), they are suspended (INV-HOLD-9), or their account is closed (INV-HOLD-4).

## Scenarios (Given / When / Then)
- **Happy path: first hold on a checked-out tool**
  - **Given:** Ana has the cordless drill on loan, due 12 October, and no holds are waiting on it
  - **When:** Ben places a hold on the drill from the Tool Catalog
  - **Then:** Hold Placed is recorded for Ben on the drill. Ben's My Holds shows the drill with
    queue position 1, and the Tool Catalog shows one hold waiting on the drill.
- **Second hold queues behind the first**
  - **Given:** the drill is checked out, and Ben's hold on it is at position 1
  - **When:** Cleo places a hold on the drill
  - **Then:** Hold Placed is recorded for Cleo on the drill. Cleo's My Holds shows the drill with
    queue position 2, Ben's position is unchanged, and the Tool Catalog shows two holds waiting.
- **Hold on a set-aside tool**
  - **Given:** the drill was returned and is set aside for Ben, and no other holds are waiting
  - **When:** Cleo places a hold on the drill
  - **Then:** Hold Placed is recorded for Cleo on the drill, and her hold waits behind Ben's
    set-aside.
- **Holder with an overdue loan**
  - **Given:** Ben has a ladder on loan that is overdue, and the cordless drill is checked out
    by Ana
  - **When:** Ben places a hold on the drill
  - **Then:** Hold Placed is recorded for Ben on the drill.
- **Holds on several tools**
  - **Given:** Ben has a hold waiting on the cordless drill, and the hedge trimmer is checked out
    by Ana
  - **When:** Ben places a hold on the hedge trimmer
  - **Then:** Hold Placed is recorded for Ben on the hedge trimmer. His My Holds shows both
    holds.

## Open Questions
None.
