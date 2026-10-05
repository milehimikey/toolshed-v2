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
  - If the holder has already collected the tool, the hold is cancelled, the tool is retired, or
    the holder closes their account before the email goes out, the hold leaves Pickup Notices To
    Send and no email is sent.
- **INV-PKN-6:** The email goes out as soon as the tool is set aside, with no quiet hours.
  - The 3-day pickup window starts at the set-aside, not when the email is sent.
- **INV-PKN-7:** The email gives the holder's name, the tool's name, the collect-by date, and
  that the hold expires and the tool passes to the next neighbor if it isn't collected by then.
  - The collect-by date is the set-aside date plus 3 days. The set-aside lapses at the start of
    the following day, shed local time.
- **INV-PKN-8:** The email provider does the retrying: 3 delivery attempts over about an hour. If
  the email is refused or bounces on all of them, Pickup Notice Failed is recorded, and the shed
  manager sees it on the Undelivered Notices screen.
  - No further pickup email is attempted for that set-aside. The hold still shows as ready for
    pickup on My Holds, and the 3-day window runs as usual.

## Scenarios (Given / When / Then)
- **Happy path**
  - **Given:** Ben Ito (ben@example.org) held the cordless drill at the front of its queue, and
    Hold Fulfilled has just been recorded for his hold, so it is on Pickup Notices To Send
  - **When:** on 14 October, the pickup notice gateway hands the email provider a pickup email
    to ben@example.org, telling Ben Ito the cordless drill is ready for him, to collect by 17
    October, and that otherwise his hold expires and the drill passes to the next neighbor
  - **Then:** Pickup Notice Sent is recorded for Ben's hold. It leaves Pickup Notices To Send,
    and Ben's My Holds shows the drill as ready for pickup and that he was emailed about it.
- **Hold cancelled before the email goes out**
  - **Given:** Hold Fulfilled has been recorded for Ben's hold on the drill, and no pickup email
    has been sent
  - **When:** Ben cancels his hold
  - **Then:** the hold leaves Pickup Notices To Send, and no pickup email is sent.
- **Holder collected before the email went out**
  - **Given:** Hold Fulfilled has been recorded for Ben's hold on the drill, and no pickup email
    has been sent
  - **When:** Ben checks out the drill
  - **Then:** the hold leaves Pickup Notices To Send, and no pickup email is sent.
- **Pickup email can't be delivered**
  - **Given:** Pickup Notice Sent was recorded for Ben's hold on the drill, and the email bounced
    on all 3 of the provider's attempts
  - **When:** the provider reports a final failure
  - **Then:** Pickup Notice Failed is recorded for Ben's hold, and the shed manager's Undelivered
    Notices screen shows it. Ben's My Holds still shows the drill as ready for pickup, to collect
    by 17 October.
