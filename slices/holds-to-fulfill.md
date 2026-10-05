---
schemaVersion: 1
pattern: state-view
swimlane: System → Holds
status: draft
version: 1
---
# Slice: Holds To Fulfill

![Diagram](./holds-to-fulfill.svg)

## Intent
The hold fulfiller's to-do list: every tool that is back in the shed, not set aside for anyone,
with a hold waiting, and which hold is next in line for it.

## Invariants / Business Rules
- **INV-HTF-1:** A tool is on the list while it is in the shed, not set aside for anyone, and has
  at least one hold waiting.
  - A tool on loan is never on the list, however many holds wait on it.
- **INV-HTF-2:** Each item names the hold at the front of the tool's queue: the earliest-placed
  hold still waiting.
  - The front holder is never passed over, even when they are suspended or have an overdue loan
    and can't collect. The tool is set aside for them. If they don't collect it by the end of
    the collect-by date (the set-aside date plus 3 days), the hold expires and the tool comes back
    onto the list for the next hold.
- **INV-HTF-3:** A tool leaves the list when one of its holds is fulfilled.
  - The tool is then set aside for that holder.
- **INV-HTF-4:** A tool comes back onto the list when its set-aside ends without the holder
  collecting it (the hold expires or is cancelled, or the holder closes their account) and
  another hold is waiting.
  - With no hold waiting, the tool becomes available and stays off the list.
- **INV-HTF-5:** A tool leaves the list when the holder at the front of its queue checks it out
  before their hold is fulfilled.
  - That checkout ends their hold. The tool is on loan again, and the next hold waits for its
    return.
- **INV-HTF-6:** When a waiting hold ends, the next hold behind it moves to the front. If no hold
  is left, the tool leaves the list.
  - A hold ends when it is cancelled or expires, the holder checks the tool out, the tool is
    retired or recorded lost, or the holder closes their account.
- **INV-HTF-7:** Retiring a tool, or recording it lost, ends all its holds. A retired tool leaves
  the list.
  - A tool is recorded lost only while it is on loan, so it is never on the list at that moment.
    Its holds end, and it never comes back onto the list.
- **INV-HTF-8:** The list has no screen. Only the hold fulfiller reads it.

## Scenarios (Given / When / Then)
- **Returned with a hold waiting**
  - **Given:** Ana has the cordless drill on loan, and Ben's hold on it is at position 1, Cara's
    at position 2
  - **When:** Tool Returned is recorded for the drill
  - **Then:** the drill is on the list, with Ben's hold at the front.
- **Returned with no hold waiting**
  - **Given:** Ana has the drill on loan, with no holds on it
  - **When:** Tool Returned is recorded for the drill
  - **Then:** the drill is not on the list.
- **Hold placed on a tool on loan**
  - **Given:** Ana has the drill on loan
  - **When:** Hold Placed is recorded for Ben on the drill
  - **Then:** the drill is not on the list.
- **Hold fulfilled**
  - **Given:** the drill is on the list, with Ben's hold at the front and Cara's behind it
  - **When:** Hold Fulfilled is recorded for Ben's hold
  - **Then:** the drill leaves the list.
- **Set-aside expires, another hold waiting**
  - **Given:** the drill is set aside for Ben, and Cara's hold is waiting
  - **When:** Hold Expired is recorded for Ben's hold
  - **Then:** the drill is back on the list, with Cara's hold at the front.
- **Set-aside expires, no hold waiting**
  - **Given:** the drill is set aside for Ben, and no other hold is waiting
  - **When:** Hold Expired is recorded for Ben's hold
  - **Then:** the drill is not on the list.
- **Set-aside cancelled, another hold waiting**
  - **Given:** the drill is set aside for Ben, and Cara's hold is waiting
  - **When:** Hold Cancelled is recorded for Ben's hold
  - **Then:** the drill is back on the list, with Cara's hold at the front.
- **Set-aside holder closes their account**
  - **Given:** the drill is set aside for Ben, and Cara's hold is waiting
  - **When:** Neighbor Account Closed is recorded for Ben
  - **Then:** the drill is back on the list, with Cara's hold at the front.
- **Front hold cancelled before it is fulfilled**
  - **Given:** the drill is on the list, with Ben's hold at the front and Cara's behind it
  - **When:** Hold Cancelled is recorded for Ben's hold
  - **Then:** the drill stays on the list, with Cara's hold at the front.
- **Front holder collects before the hold is fulfilled**
  - **Given:** the drill is on the list, with Ben's hold at the front and Cara's behind it
  - **When:** Tool Checked Out is recorded for the drill and Ben
  - **Then:** the drill leaves the list. Cara's hold waits for the drill's next return.
- **Tool retired**
  - **Given:** the drill is on the list, with Ben's hold at the front
  - **When:** Tool Retired is recorded for the drill
  - **Then:** the drill leaves the list.
- **Front holder is suspended**
  - **Given:** Ana has the drill on loan, Ben is suspended, and Ben's hold is at position 1 with
    Cara's at position 2
  - **When:** Tool Returned is recorded for the drill
  - **Then:** the drill is on the list, with Ben's hold at the front.
- **Tool recorded lost while holds wait**
  - **Given:** Ana has the drill on loan, and Ben's hold on it is at position 1
  - **When:** Tool Lost is recorded for the drill
  - **Then:** Ben's hold ends, and the drill never joins the list.

## Open Questions
None.
