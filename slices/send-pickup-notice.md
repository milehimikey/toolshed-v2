---
schemaVersion: 1
pattern: translation
swimlane: System → Holds
status: draft
version: 1
---
# Slice: Send Pickup Notice

![Diagram](./send-pickup-notice.svg)

## Intent
Email a neighbor when a tool they hold has been set aside for them, so they collect it within the
3-day pickup window. The shed records that the email was handed to the email provider, and My
Holds shows the neighbor that it was sent.

## Invariants / Business Rules
- **INV-PKN-1:** The system sends pickup emails. No person sends them.
  - The pickup notice gateway watches Pickup Notices To Send and issues Send Pickup Notice for
    each set-aside on it.
- **INV-PKN-2:** At most one pickup email is sent per set-aside.
  - Once Pickup Notice Sent is recorded for a hold, it leaves Pickup Notices To Send and is never
    emailed again.
- **INV-PKN-3:** Pickup emails go by email only, through the same provider as overdue notices, to
  the email address the holder signed up with.
- **INV-PKN-4:** Pickup Notice Sent means the email was handed to the email provider.
  - It does not mean the holder received or read it.
- **INV-PKN-5:** No pickup email is sent for a set-aside that has already ended.
  - If the hold is cancelled, the tool is retired, or the holder closes their account before the
    email goes out, it leaves Pickup Notices To Send and no email is sent.

## Scenarios (Given / When / Then)
- **Happy path**
  - **Given:** Ben Ito (ben@example.org) held the cordless drill at the front of its queue, and
    Hold Fulfilled has just been recorded for his hold, so it is on Pickup Notices To Send
  - **When:** the pickup notice gateway hands the email provider a pickup email to
    ben@example.org about the cordless drill
  - **Then:** Pickup Notice Sent is recorded for Ben's hold. It leaves Pickup Notices To Send,
    and Ben's My Holds shows the drill as ready for pickup and that he was emailed about it.
- **Hold cancelled before the email goes out**
  - **Given:** Hold Fulfilled has been recorded for Ben's hold on the drill, and no pickup email
    has been sent
  - **When:** Ben cancels his hold
  - **Then:** the hold leaves Pickup Notices To Send, and no pickup email is sent.

## Open Questions
- [ ] What happens when the email provider refuses the pickup email, or it bounces?
  - (a) The overdue-notice treatment: retry, then record the failure and show it on Undelivered
    Notices. That is a new event. (b) Retry, then give up.
  - Recommendation: (b). The hold still shows as ready for pickup on My Holds, and an uncollected
    tool passes on after 3 days. The shed is owed nothing, unlike with an overdue loan.
- [ ] What does the pickup email say?
  - Recommendation: the holder's name, the tool's name, the collect-by date, and that the hold
    expires and the tool passes to the next neighbor if it isn't collected by then. The
    collect-by date is the set-aside date plus 3 days, so it can be worked out from Hold
    Fulfilled.
- [ ] When does the email go out? Are there quiet hours?
  - Recommendation: as soon as the tool is set aside, with no quiet hours, the same as overdue
    notices. The 3-day window starts at the set-aside, not when the email is sent.
- [ ] If the holder collects the tool before the pickup email goes out, is the email still sent?
  - Pickup Notices To Send doesn't hear Tool Checked Out, so as modeled it would still be sent.
  - (a) Don't send it. The list would then also hear Tool Checked Out, which is a model change.
    (b) Send it anyway. The window between the two is usually seconds.
  - Recommendation: (a). An email about a tool already in the holder's hands is confusing.
