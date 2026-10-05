---
schemaVersion: 1
pattern: state-view
swimlane: System → Holds
status: draft
version: 1
---
# Slice: Pickups To Expire

![Diagram](./pickups-to-expire.svg)

## Intent
The list of tools currently set aside for a holder, with the date each must be collected by. The
pickup expirer works through it, so a set-aside the holder doesn't collect in time ends and the
tool moves on to the next neighbor.

## Invariants / Business Rules
- **INV-PTE-1:** A set-aside is on the list from Hold Fulfilled, with its collect-by date: the
  set-aside date plus 3 days.
- **INV-PTE-2:** A set-aside is due to expire once its collect-by date has passed and the holder
  has not collected the tool.
  - This is worked out by comparing the collect-by date with today. No event marks the
    set-aside as lapsed.
  - The set-aside lapses at the start of the day after its collect-by date, in the shed's local
    time. The holder can collect at any time on the collect-by date itself.
- **INV-PTE-3:** When the holder checks the tool out, the set-aside leaves the list.
- **INV-PTE-4:** When the holder cancels the hold, the set-aside leaves the list.
- **INV-PTE-5:** When Hold Expired is recorded, the set-aside leaves the list.
- **INV-PTE-6:** When the tool is retired or the holder closes their account, the set-aside
  leaves the list.
  - Both end the hold.
- **INV-PTE-7:** The list has no screen. Only the pickup expirer reads it.
- **INV-PTE-8:** The 3-day window runs regardless of whether the holder can collect.
  - It doesn't pause while the holder is suspended or has an overdue loan. If they can't
    collect in time, the set-aside expires like any other.

## Scenarios (Given / When / Then)
- **Tool set aside**
  - **Given:** the cordless drill was returned, and Ben's hold is at the front of its queue
  - **When:** Hold Fulfilled is recorded for Ben's hold on 10 October
  - **Then:** the drill's set-aside for Ben is on the list, collect by 13 October, not yet due to
    expire.
- **Collect-by date passes**
  - **Given:** the drill is set aside for Ben, collect by 13 October, and Ben has not collected it
  - **When:** it is the start of 14 October in the shed's local time
  - **Then:** the set-aside is due to expire. Nothing is recorded until the pickup expirer acts.
- **Holder collects in time**
  - **Given:** the drill is set aside for Ben, collect by 13 October
  - **When:** Tool Checked Out is recorded for the drill and Ben on 12 October
  - **Then:** the set-aside leaves the list.
- **Holder cancels**
  - **Given:** the drill is set aside for Ben
  - **When:** Hold Cancelled is recorded for Ben's hold
  - **Then:** the set-aside leaves the list.
- **Set-aside expires**
  - **Given:** the drill's set-aside for Ben is due to expire
  - **When:** Hold Expired is recorded for Ben's hold
  - **Then:** the set-aside leaves the list. If the drill is then set aside for the next hold,
    that new set-aside joins the list with its own collect-by date.
- **Tool retired**
  - **Given:** the drill is set aside for Ben
  - **When:** Tool Retired is recorded for the drill
  - **Then:** the set-aside leaves the list.
- **Holder closes their account**
  - **Given:** the drill is set aside for Ben
  - **When:** Neighbor Account Closed is recorded for Ben
  - **Then:** the set-aside leaves the list.
- **Holder can't collect in time**
  - **Given:** the drill is set aside for Ben, collect by 13 October, and Ben is suspended
  - **When:** it is 14 October and Ben is still suspended
  - **Then:** the set-aside is due to expire, the same as for any holder.

## Open Questions
None.
