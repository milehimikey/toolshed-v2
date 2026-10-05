---
schemaVersion: 1
pattern: automation
swimlane: System → Holds
status: draft
version: 1
---
# Slice: Fulfill Hold

![Diagram](./fulfill-hold.svg)

## Intent
When a tool is back in the shed with holds waiting, set it aside for the neighbor at the front of
the queue, so the tool goes to holders in the order they asked. The system does this itself; no
person decides it.

## Invariants / Business Rules
- **INV-FUL-1:** Holds are fulfilled by the system, never by a person.
  - The hold fulfiller acts on each tool on the Holds To Fulfill list, as soon as it appears
    there.
  - Returning a tool records only Tool Returned. Hold Fulfilled is recorded separately.
- **INV-FUL-2:** A tool is set aside for the hold at the front of its queue: the earliest-placed
  hold still waiting.
- **INV-FUL-3:** A hold is fulfilled only while the tool is in the shed and not set aside for
  anyone, and the hold is still waiting.
  - If the tool has been checked out, retired, or set aside since it joined the list, or the
    hold has ended, nothing is fulfilled.
- **INV-FUL-4:** A tool is set aside for one holder at a time.
- **INV-FUL-5:** Once a hold is fulfilled, the tool is set aside for that holder for 3 days, and
  only they may check it out.
  - Their My Holds shows the tool as ready for pickup, with a collect-by date of the set-aside
    date plus 3 days. The Tool Catalog shows the tool as set aside, the same for every neighbor.
  - The holder collects it with an ordinary checkout.
- **INV-FUL-6:** When a set-aside ends without the holder collecting it and another hold is
  waiting, the tool is set aside for the next hold in the same way.
  - A set-aside ends this way when the hold expires or is cancelled, or the holder closes their
    account. With no hold waiting, the tool becomes available.
- **INV-FUL-7:** Once a hold is fulfilled, the holder is emailed that the tool is set aside for
  them.
  - A separate step sends the email through the same provider as overdue notices and records
    Pickup Notice Sent. Fulfilling the hold doesn't wait for it.
- **INV-FUL-8:** The tool is set aside for the front holder even if they can't collect it right
  now, because they are suspended or have an overdue loan.
  - They keep their place. If they can't collect within the 3 days, the hold expires and the
    tool passes to the next hold.

## Scenarios (Given / When / Then)
- **Happy path: tool returned with holds waiting**
  - **Given:** Ana has returned the cordless drill on 10 October. Ben's hold on it is at
    position 1 and Cara's at position 2, so the drill is on Holds To Fulfill with Ben's hold at
    the front.
  - **When:** the hold fulfiller acts on the drill
  - **Then:** Hold Fulfilled is recorded for Ben's hold. Ben's My Holds shows the drill ready for
    pickup, collect by 13 October, and Ben is emailed that the drill is waiting for him. Cara's
    hold moves to position 1. The Tool Catalog shows the drill as set aside.
- **Next hold after an expired set-aside**
  - **Given:** the drill was set aside for Ben, Hold Expired has been recorded for his hold, and
    Cara's hold is waiting, so the drill is back on Holds To Fulfill with Cara's hold at the
    front
  - **When:** the hold fulfiller acts on the drill
  - **Then:** Hold Fulfilled is recorded for Cara's hold, and her My Holds shows the drill ready
    for pickup.
- **Next hold after a cancelled set-aside**
  - **Given:** the drill was set aside for Ben, Ben cancelled his hold, and Cara's hold is waiting
  - **When:** the hold fulfiller acts on the drill
  - **Then:** Hold Fulfilled is recorded for Cara's hold.
- **Front holder is suspended**
  - **Given:** the drill is on Holds To Fulfill with Ben's hold at the front, and Ben is
    suspended
  - **When:** the hold fulfiller acts on the drill
  - **Then:** Hold Fulfilled is recorded for Ben's hold. If Ben is still unable to collect after
    3 days, his hold expires and the drill passes to the next hold.

## Open Questions
None.
