---
schemaVersion: 1
pattern: automation
swimlane: System → Holds
status: draft
version: 1
---
# Slice: Expire Hold

![Diagram](./expire-hold.svg)

## Intent
When a holder doesn't collect a set-aside tool within its 3-day pickup window, end their hold so
the tool passes to the next neighbor in the queue, or becomes available to borrow. The system
does this itself; no person decides it.

## Invariants / Business Rules
- **INV-EXP-1:** Holds are expired by the system, never by a person.
  - The pickup expirer acts on each set-aside on Pickups To Expire once it is due to expire.
    The shed manager doesn't cancel set-asides by hand.
- **INV-EXP-2:** A set-aside expires once its collect-by date has passed and the holder has not
  collected the tool.
  - The collect-by date is the set-aside date plus 3 days. The exact cut-off is an open question
    below.
- **INV-EXP-3:** Only a set-aside still in force can expire.
  - If the holder has collected the tool or cancelled the hold, or the tool was retired, or the
    holder closed their account, nothing expires.
- **INV-EXP-4:** The window runs regardless of whether the holder can collect.
  - A holder who is suspended or has an overdue loan can't collect, and their set-aside expires
    on the same terms as anyone's.
- **INV-EXP-5:** When a hold expires, it ends and drops off the holder's My Holds.
  - The holder loses their place in the queue.
- **INV-EXP-6:** After a hold expires, the tool passes to the next hold waiting, or becomes
  available to borrow if no hold is waiting.
  - Setting the tool aside for the next hold is the hold fulfiller's job, recorded as Hold
    Fulfilled.

## Scenarios (Given / When / Then)
- **Happy path: set-aside lapses, next hold waiting**
  - **Given:** the cordless drill was set aside for Ben on 10 October, collect by 13 October.
    Ben has not collected it, Cara's hold is waiting behind his, and it is 14 October.
  - **When:** the pickup expirer acts on Ben's set-aside
  - **Then:** Hold Expired is recorded for Ben's hold, and the drill drops off Ben's My Holds.
    The drill then goes back on Holds To Fulfill with Cara's hold at the front.
- **Set-aside lapses, no hold waiting**
  - **Given:** the drill was set aside for Ben, collect by 13 October, no other hold is waiting,
    and it is 14 October
  - **When:** the pickup expirer acts on Ben's set-aside
  - **Then:** Hold Expired is recorded for Ben's hold, and the Tool Catalog shows the drill
    available.
- **Suspended holder's set-aside lapses**
  - **Given:** the drill was set aside for Ben, collect by 13 October. Ben is suspended, Cara's
    hold is waiting, and it is 14 October.
  - **When:** the pickup expirer acts on Ben's set-aside
  - **Then:** Hold Expired is recorded for Ben's hold, and the drill passes to Cara.

## Open Questions
- [ ] When exactly does a set-aside lapse?
  - Recommendation: at the start of the day after the collect-by date, in the shed's local
    time, so the holder can collect any time on that day. Loans become overdue the same way.
- [ ] After a hold expires, may the neighbor place a new hold on the same tool?
  - Recommendation: yes, joining the back of the queue, as after cancelling a hold.
- [ ] Is the holder told when their hold expires, beyond it dropping off My Holds?
  - Options: (a) no message; (b) an email through the same provider (a new event and email
    slice, a model change).
  - Recommendation: (a). They were emailed when the tool was set aside, and My Holds shows the
    collect-by date.
- [ ] Does an expired hold count against the neighbor in any way, the way late returns count
  toward a flag?
  - Recommendation: no. An uncollected tool costs the next neighbor at most 3 days, and only
    late returns are on record as counting.
