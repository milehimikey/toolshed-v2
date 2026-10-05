---
schemaVersion: 1
pattern: state-view
swimlane: System → Holds
status: draft
version: 1
---
# Slice: Pickup Notices To Send

![Diagram](./pickup-notices-to-send.svg)

## Intent
The pickup notice gateway's to-do list: every tool set aside for a holder whose pickup email has
not gone out yet, with everything the email needs.

## Invariants / Business Rules
- **INV-PNS-1:** A hold is on the list from the moment Hold Fulfilled is recorded for it until its
  pickup email is sent.
- **INV-PNS-2:** Each item carries what the email needs: the holder's name and email, the tool's
  name, and the date the tool was set aside.
  - The name and email come from Neighbor Registered, the tool's name from Tool Added. The
    collect-by date is the set-aside date plus 3 days.
- **INV-PNS-3:** Once Pickup Notice Sent is recorded for a hold, it leaves the list and never
  comes back.
- **INV-PNS-4:** A hold leaves the list, unsent, when its set-aside ends first: the hold is
  cancelled, the tool is retired, or the holder closes their account.
- **INV-PNS-5:** The list has no screen. Only the pickup notice gateway reads it.

## Scenarios (Given / When / Then)
- **Set-aside joins the list**
  - **Given:** Ben Ito registered with ben@example.org, the cordless drill was added, and Ben's
    hold on the drill is at the front of its queue
  - **When:** Hold Fulfilled is recorded for Ben's hold on 14 October
  - **Then:** Ben's hold is on the list with Ben Ito, ben@example.org, "Cordless drill", and a
    set-aside date of 14 October.
- **Pickup email sent**
  - **Given:** Ben's hold is on the list
  - **When:** Pickup Notice Sent is recorded for it
  - **Then:** Ben's hold leaves the list.
- **Hold cancelled before the email**
  - **Given:** Ben's hold is on the list
  - **When:** Hold Cancelled is recorded for it
  - **Then:** Ben's hold leaves the list.
- **Tool retired before the email**
  - **Given:** Ben's hold on the drill is on the list
  - **When:** Tool Retired is recorded for the drill
  - **Then:** Ben's hold leaves the list.
- **Holder closes their account before the email**
  - **Given:** Ben's hold is on the list
  - **When:** Neighbor Account Closed is recorded for Ben
  - **Then:** Ben's hold leaves the list.
- **No set-aside, nothing to send**
  - **Given:** the list is empty
  - **When:** Neighbor Registered is recorded for Cara, and Tool Added for a ladder
  - **Then:** the list is still empty.

## Open Questions
- [ ] If the gateway can't get the email delivered and gives up without recording anything,
  what takes the hold off the list?
  - As modeled, nothing does, so the gateway would keep picking the hold up for as long as the
    tool stays set aside, and beyond.
  - Recommendation: the list also hears Hold Expired. An unsent hold then leaves the list when
    the pickup window closes, and the gateway keeps retrying until then. If a pickup-email
    failure event is chosen instead, that event takes it off. Either way it is a model change.
- [ ] If the holder collects the tool before the email goes out, does the hold leave the list?
  - Recommendation: yes, which means the list also hears Tool Checked Out. This is the same
    question as on Send Pickup Notice.
