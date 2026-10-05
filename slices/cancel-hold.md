---
schemaVersion: 1
pattern: state-change
swimlane: Neighbor → Holds
status: draft
version: 1
---
# Slice: Cancel Hold

![Diagram](./cancel-hold.svg)

## Intent
Let a neighbor give up a hold they no longer want, from My Holds, so the tool goes to the next
neighbor in the queue instead of waiting on them.

## Invariants / Business Rules
- **INV-CNH-1:** A neighbor cancels a hold themselves, from My Holds, and only their own hold.
  - The shed manager does not cancel holds.
- **INV-CNH-2:** A hold can be cancelled while it is waiting or while the tool is ready for
  pickup.
  - A suspended neighbor may cancel their own hold. Cancelling only gives up their own claim.
- **INV-CNH-3:** A hold that has already ended can't be cancelled.
  - A hold ends when it is cancelled or expires, when the holder checks the tool out, when the
    tool is retired, or when the holder closes their account.
- **INV-CNH-4:** Cancelling a waiting hold moves every hold behind it on that tool up one place.
- **INV-CNH-5:** Cancelling a hold that is ready for pickup ends the set-aside. The tool passes
  to the next hold, or becomes available if no holds are waiting.
  - The same as when a set-aside expires.
- **INV-CNH-6:** After cancelling, the neighbor may place a new hold on the same tool. It joins
  the back of the queue.
  - A cancelled hold gives up its place for good.

## Scenarios (Given / When / Then)
- **Cancel a waiting hold**
  - **Given:** the cordless drill is checked out, Ben's hold on it is at position 1 and Cara's at
    position 2
  - **When:** Ben cancels his hold from My Holds
  - **Then:** Hold Cancelled is recorded for Ben's hold. The drill drops off Ben's My Holds,
    Cara's My Holds shows her hold at position 1, and the Tool Catalog shows 1 hold waiting.
- **Cancel a hold that is ready for pickup, another hold waiting**
  - **Given:** the drill is set aside for Ben, and Cara's hold is waiting at position 1
  - **When:** Ben cancels his hold from My Holds
  - **Then:** Hold Cancelled is recorded for Ben's hold, and the drill drops off his My Holds.
    The drill is not shown as available. Once Hold Fulfilled is recorded for Cara's hold, it is
    set aside for her and her My Holds shows it as ready for pickup.
- **Cancel a hold that is ready for pickup, no holds waiting**
  - **Given:** the drill is set aside for Ben, and no other holds are waiting
  - **When:** Ben cancels his hold from My Holds
  - **Then:** Hold Cancelled is recorded for Ben's hold. The drill drops off his My Holds, and
    the Tool Catalog lists the drill as available.
- **Suspended neighbor cancels a hold**
  - **Given:** Ben is suspended, and his hold on the drill is waiting at position 1, with Cara's
    at position 2
  - **When:** Ben cancels his hold from My Holds
  - **Then:** Hold Cancelled is recorded for Ben's hold, and Cara's hold moves to position 1.
