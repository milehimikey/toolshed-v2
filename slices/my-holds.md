---
schemaVersion: 1
pattern: state-view
swimlane: Neighbor → Holds
status: draft
version: 1
---
# Slice: My Holds

![Diagram](./my-holds.svg)

## Intent
Shows a neighbor the holds they are waiting on, on the My Holds screen: where they stand in each
tool's queue, and when a tool has been set aside for them and is ready for pickup.

## Invariants / Business Rules
- **INV-MYHD-1:** A neighbor's My Holds shows only their own holds.
- **INV-MYHD-2:** Holds on a tool queue in the order they were placed. Each waiting hold shows
  the neighbor's position in that tool's queue.
  - Position 1 is the next hold to be fulfilled. A hold moves up as holds ahead of it are
    fulfilled or end.
  - A hold ends when it is cancelled or expires, the holder checks the tool out, the tool is
    retired or recorded lost, or the holder closes their account.
- **INV-MYHD-3:** Once a neighbor's hold is fulfilled, My Holds shows the tool as ready for
  pickup, with the date to collect it by.
  - The collect-by date is the date the tool was set aside plus 3 days.
  - My Holds is the only screen that tells a neighbor a tool is set aside for them. The Tool
    Catalog is the same for every neighbor.
- **INV-MYHD-4:** A tool stays ready for pickup until the end of its collect-by date. If the
  holder has not checked it out by then, the hold expires at the start of the next day, shed
  local time, and drops off My Holds.
  - The tool then passes to the next hold, which shows as ready for pickup on that neighbor's My
    Holds.
  - The neighbor is not told the hold expired, and an expired hold doesn't count against them.
    They may place a new hold on the same tool, which joins the back of the queue.
- **INV-MYHD-5:** When a holder checks out the tool they hold, their hold ends and drops off My
  Holds, whether or not it had been fulfilled.
  - The loan then appears on My Loans.
- **INV-MYHD-6:** A neighbor can cancel their own hold from My Holds, waiting or ready for
  pickup. A cancelled hold drops off My Holds.
  - Cancelling a hold that is ready for pickup passes the tool to the next hold, the same as
    expiry.
- **INV-MYHD-7:** Retiring a tool, or marking it lost, ends every hold on it, and those holds
  drop off My Holds.
  - The holders are not told.
- **INV-MYHD-8:** Closing an account ends all of that neighbor's holds. Their holds drop off My
  Holds, and holds behind them move up.
  - A tool that was ready for pickup for them passes to the next hold, the same as expiry.
- **INV-MYHD-9:** A suspended neighbor keeps their holds and their place in each queue.
  - They can't collect a tool while suspended, and can't place a new hold. They may still cancel
    a hold.
- **INV-MYHD-12:** A front holder who can't collect, because they are suspended or have an
  overdue loan, still has the tool set aside for them, and the pickup window runs regardless.
  - If they can't collect in time, the hold expires and the tool passes to the next hold.
- **INV-MYHD-13:** When a tool is set aside for a neighbor, they are emailed that it is ready for
  pickup, and My Holds shows that the email was sent.
  - If the email can't be delivered, My Holds shows that instead. The tool stays ready for
    pickup, with the same collect-by date.
- **INV-MYHD-10:** A neighbor has at most one hold on any one tool.
- **INV-MYHD-14:** Each hold shows the tool's name.
  - The name comes from Tool Added, which My Holds hears for that purpose.
- **INV-MYHD-11:** Only Hold Placed, Hold Cancelled, Hold Fulfilled, Pickup Notice Sent, Pickup
  Notice Failed, Hold Expired, Tool Checked Out by a holder of that tool, Tool Retired, Tool
  Lost and Neighbor Account Closed change My Holds. Tool Added supplies tool names.
  - A checkout by anyone else leaves every neighbor's My Holds unchanged.

## Scenarios (Given / When / Then)
- **Hold placed**
  - **Given:** Tool Added was recorded for a tool named "Cordless drill", the drill is checked
    out, and nobody has a hold on it
  - **When:** Hold Placed is recorded for Ben on the drill
  - **Then:** Ben's My Holds lists "Cordless drill", waiting, at position 1.
- **A second hold placed**
  - **Given:** Ben holds position 1 on the drill
  - **When:** Hold Placed is recorded for Cara on the drill
  - **Then:** Cara's My Holds lists the drill, waiting, at position 2. Ben's is unchanged.
- **Hold cancelled**
  - **Given:** Ben holds position 1 and Cara position 2 on the drill
  - **When:** Hold Cancelled is recorded for Ben's hold
  - **Then:** the drill leaves Ben's My Holds. Cara's shows the drill, waiting, at position 1.
- **Hold fulfilled**
  - **Given:** Ben holds position 1 and Cara position 2 on the drill, and the drill has been
    returned
  - **When:** Hold Fulfilled is recorded for Ben's hold
  - **Then:** Ben's My Holds shows the drill as ready for pickup, to collect by 3 days from
    today. Cara's shows the drill, waiting, at position 1.
- **Pickup email sent**
  - **Given:** Ben's My Holds shows the drill as ready for pickup
  - **When:** Pickup Notice Sent is recorded for Ben's hold
  - **Then:** Ben's My Holds shows the drill as ready for pickup, and that he was emailed about
    it.
- **Pickup email couldn't be delivered**
  - **Given:** Ben's My Holds shows the drill as ready for pickup, to collect by 17 October, and
    that he was emailed about it
  - **When:** Pickup Notice Failed is recorded for Ben's hold
  - **Then:** Ben's My Holds shows the drill as ready for pickup, to collect by 17 October, and
    that the pickup email couldn't be delivered.
- **Suspended holder can't collect in time**
  - **Given:** Ben is suspended, his hold on the drill is at the front, and Cara's is behind it
  - **When:** the drill is returned and Hold Fulfilled is recorded for Ben's hold
  - **Then:** Ben's My Holds shows the drill as ready for pickup, to collect by 3 days from
    today. If Ben is still suspended when his collect-by date has passed, Hold Expired is
    recorded, the drill drops off his My Holds, and it passes to Cara.
- **Holder collects the tool**
  - **Given:** Ben's My Holds shows the drill as ready for pickup
  - **When:** Tool Checked Out is recorded for the drill and Ben
  - **Then:** the drill leaves Ben's My Holds. Cara's is unchanged.
- **Hold expired**
  - **Given:** Ben's My Holds shows the drill as ready for pickup, and Cara holds position 1
  - **When:** Hold Expired is recorded for Ben's hold
  - **Then:** the drill drops off Ben's My Holds. Once Hold Fulfilled
    is recorded for Cara's hold, her My Holds shows the drill as ready for pickup.
- **Ready-for-pickup hold cancelled**
  - **Given:** Ben's My Holds shows the drill as ready for pickup, and Cara holds position 1
  - **When:** Hold Cancelled is recorded for Ben's hold
  - **Then:** the drill drops off Ben's My Holds. Once Hold Fulfilled is recorded for Cara's
    hold, her My Holds shows the drill as ready for pickup.
- **Front holder collects before the set-aside is recorded**
  - **Given:** Ben holds position 1 and Cara position 2 on the drill, and the drill has just
    been returned
  - **When:** Tool Checked Out is recorded for the drill and Ben, before Hold Fulfilled
  - **Then:** the drill drops off Ben's My Holds. Cara's shows the drill, waiting, at position
    1.
- **Tool retired**
  - **Given:** Ben holds position 1 and Cara position 2 on the drill
  - **When:** Tool Retired is recorded for the drill
  - **Then:** the drill drops off both Ben's and Cara's My Holds.
- **Tool marked lost**
  - **Given:** Ana has the drill on loan, and Ben holds position 1 and Cara position 2 on it
  - **When:** Tool Lost is recorded for the drill
  - **Then:** the drill drops off both Ben's and Cara's My Holds.
- **Hold again after expiry**
  - **Given:** Ben's hold on the drill expired, and Cara now holds the drill, ready for pickup
  - **When:** Hold Placed is recorded for Ben on the drill
  - **Then:** Ben's My Holds lists the drill, waiting, at position 1, behind Cara's set-aside.
- **Holder closes their account**
  - **Given:** Ben holds position 1 and Cara position 2 on the drill
  - **When:** Neighbor Account Closed is recorded for Ben
  - **Then:** the drill leaves Ben's My Holds. Cara's shows the drill, waiting, at position 1.
- **Checkout by a neighbor with no hold**
  - **Given:** Ana has no holds, and the hammer is available
  - **When:** Tool Checked Out is recorded for the hammer and Ana
  - **Then:** no neighbor's My Holds changes.

## Open Questions
None.
