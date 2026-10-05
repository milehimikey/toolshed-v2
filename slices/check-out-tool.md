---
schemaVersion: 1
pattern: state-change
swimlane: Neighbor → Loans
status: draft
version: 1
---
# Slice: Check Out Tool

![Diagram](./check-out-tool.svg)

## Intent
Let a neighbor borrow a tool, self-service, from the Tool Catalog. The checkout records a loan with
a due date set from the tool's default checkout period, or from a shorter period the neighbor
chooses.

## Invariants / Business Rules
- **INV-CHK-1:** A neighbor checks a tool out themselves, from the Tool Catalog.
  - The shed manager does not record checkouts.
- **INV-CHK-2:** A tool that is already checked out can't be checked out again until it is
  returned.
- **INV-CHK-3:** A tool set aside for a holder can be checked out only by that holder.
  - The holder's pickup is an ordinary checkout. There is no separate pickup event.
  - A set-aside runs through the end of its collect-by date, the set-aside date plus 3 days.
- **INV-CHK-3b:** A holder's claim on a set-aside tool ends at the start of the day after the
  collect-by date, shed local time, whether or not Hold Expired has been recorded yet.
  - From that moment the tool is treated as if the hold had expired: only the neighbor whose
    hold is now at the front of the queue can check it out (INV-CHK-3a), or anyone if no hold is
    waiting. The former holder is refused.
- **INV-CHK-3a:** A returned tool with holds waiting can be checked out only by the neighbor whose
  hold is at the front of the queue, even before Hold Fulfilled is recorded.
  - The same applies after a set-aside ends, before the next hold is fulfilled.
- **INV-CHK-4:** A retired tool can't be checked out.
- **INV-CHK-5:** A neighbor with an overdue loan can't check out another tool.
  - A loan is overdue from the start of the day after its due date, in the shed's local time,
    while the tool hasn't been returned. It is worked out from the due date and today; nothing
    records it.
  - This also stops a holder collecting a tool set aside for them.
- **INV-CHK-7:** A suspended neighbor can't check out a tool, and neither can a neighbor whose
  account is closed.
  - This also stops a suspended holder collecting a tool set aside for them. Their hold keeps its
    place in the queue.
- **INV-CHK-8:** There is no limit on how many tools one neighbor can have checked out at once.
- **INV-CHK-9:** When a holder checks out the tool they hold, their hold ends, whether or not
  it had been fulfilled.
  - The tool is then on loan, so it is not set aside for anyone, and the next hold waits for its
    return.
- **INV-CHK-10:** Check Out is offered on a reserved or set-aside tool to every neighbor, not
  only to the holder whose turn it is. Anyone else who tries is refused with the reason.
  - The Tool Catalog is the same for every neighbor and offers actions by the tool's state
    alone.
- **INV-CHK-6:** The due date is set at checkout: the checkout date plus the loan period.
  - The loan period is the tool's default checkout period, unless the neighbor chooses a shorter
    one. It is never longer than the default.
  - A shorter period is chosen as a whole number of days, at least 1.

## Scenarios (Given / When / Then)
- **Happy path**
  - **Given:** a cordless drill with a 7-day default checkout period is available, and Ana is a
    registered neighbor with no overdue loans
  - **When:** Ana checks out the drill from the Tool Catalog on 5 October, keeping the default
    period
  - **Then:** Tool Checked Out is recorded for the drill and Ana, due back 12 October. The Tool
    Catalog shows the drill as checked out, due 12 October, and Ana's My Loans lists the loan.
- **Shorter period chosen**
  - **Given:** the same available drill with a 7-day default, and Ana with no overdue loans
  - **When:** Ana checks out the drill on 5 October and chooses a 3-day period
  - **Then:** Tool Checked Out is recorded, due back 8 October.
- **Holder collects a set-aside tool**
  - **Given:** the drill was returned and set aside for Ben, whose hold was at the front of the
    queue
  - **When:** Ben checks out the drill from the Tool Catalog
  - **Then:** Tool Checked Out is recorded for the drill and Ben, due back on the checkout date
    plus the drill's default period. The Tool Catalog shows the drill as checked out, Ben's My
    Loans lists the loan, and his My Holds no longer shows the drill as ready for pickup.
- **Front holder collects before the set-aside is recorded**
  - **Given:** the drill has just been returned, Ben's hold is at the front of its queue, and
    Hold Fulfilled has not yet been recorded
  - **When:** Ben checks out the drill
  - **Then:** Tool Checked Out is recorded for the drill and Ben. Ben's hold ends without ever
    being fulfilled, and the drill is not set aside for anyone.
- **Someone else tries to take a set-aside tool**
  - **Given:** the drill is set aside for Ben
  - **When:** Ana tries to check out the drill from the Tool Catalog
  - **Then:** the checkout is refused, because the drill is set aside for another neighbor. No
    event is recorded.
- **Holder too late, before the expiry is recorded**
  - **Given:** the drill was set aside for Ben on 14 October, to collect by 17 October, and
    Cara's hold is waiting behind his. Hold Expired has not yet been recorded.
  - **When:** Ben tries to check out the drill on 18 October
  - **Then:** the checkout is refused, because his pickup window has ended. Cara can check the
    drill out.

## Open Questions
None.
