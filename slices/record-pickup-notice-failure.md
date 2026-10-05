---
schemaVersion: 1
pattern: translation
swimlane: System → Holds
status: draft
version: 1
---
# Slice: Record Pickup Notice Failure

![Diagram](./record-pickup-notice-failure.svg)

## Intent
Record that a pickup email could not be delivered to the holder, so the shed manager can see the
neighbor wasn't told their tool is ready, and can let them know another way.

## Invariants / Business Rules
- **INV-PNF-1:** A failure is recorded from the email provider's report that it refused a pickup
  email or that the email bounced. No person records it.
  - The email provider is outside the shed. Its failure report triggers Record Pickup Notice
    Failure, which records Pickup Notice Failed.
- **INV-PNF-2:** The shed records only the provider's one final failure report.
  - The provider makes 3 delivery attempts over about an hour and reports a final failure only
    if all of them fail. The shed does no retrying of its own.
- **INV-PNF-3:** Pickup Notice Failed is recorded for the hold the email was about, with the date
  of the failure and whether the email was refused or bounced.
- **INV-PNF-4:** A set-aside has at most one Pickup Notice Failed. No further pickup email is
  attempted for it.
- **INV-PNF-5:** Every Pickup Notice Failed follows a Pickup Notice Sent for the same hold.
  - Pickup Notice Sent is recorded at hand-off, so the hold has already left Pickup Notices To
    Send. Recording the failure doesn't change that list.
  - Refused and bounced are the two reasons the provider's final report can give.
- **INV-PNF-6:** A failed pickup email changes nothing about the hold.
  - The tool stays set aside for the holder until the end of the collect-by date, and the window
    is not extended.
- **INV-PNF-7:** Every recorded failure is shown to the shed manager on the Undelivered Notices
  screen, until the set-aside ends.
  - The entry shows the holder's name and email, the tool, and the collect-by date.
  - The entry drops off when the holder collects the tool, or the hold is cancelled, expires, or
    ends because the tool is retired or the holder closes their account.
- **INV-PNF-8:** A failure report that matches no pickup email the shed sent is ignored, and
  nothing is recorded.
- **INV-PNF-9:** A failure report that arrives after the set-aside has ended is ignored, and
  nothing is recorded.
  - For example, the holder collected the tool during the hour the provider spent retrying.

## Scenarios (Given / When / Then)
- **Bounced**
  - **Given:** the cordless drill was set aside for Ben Ito on 14 October, Pickup Notice Sent was
    recorded for his hold, and the email to ben@example.org bounced on all 3 of the provider's
    attempts
  - **When:** the provider reports that the pickup email to ben@example.org bounced
  - **Then:** Pickup Notice Failed is recorded for Ben's hold, as bounced, on 14 October. The
    shed manager's Undelivered Notices screen shows Ben Ito, ben@example.org, the cordless
    drill, and 17 October. Ben's My Holds shows the email couldn't be delivered. The drill stays
    set aside for Ben, to collect by 17 October.
- **Refused**
  - **Given:** Pickup Notice Sent was recorded for Ben's hold on the drill, and the provider
    refused the email on all 3 of its attempts
  - **When:** the provider reports that the pickup email to ben@example.org was refused
  - **Then:** Pickup Notice Failed is recorded for Ben's hold, as refused, and Undelivered
    Notices shows it. No further pickup email is sent for the set-aside.
- **Report matching no email**
  - **Given:** the shed has sent no pickup email to cara@example.org
  - **When:** the provider reports that a pickup email to cara@example.org bounced
  - **Then:** nothing is recorded.
- **Report arrives after the holder collected**
  - **Given:** the drill was set aside for Ben, and Ben checked it out while the provider was
    still retrying his pickup email
  - **When:** the provider reports that the pickup email to ben@example.org bounced
  - **Then:** nothing is recorded.

## Open Questions
None.
