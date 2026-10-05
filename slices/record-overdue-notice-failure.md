---
schemaVersion: 1
pattern: translation
swimlane: System → Loans
status: draft
version: 1
---
# Slice: Record Overdue Notice Failure

![Diagram](./record-overdue-notice-failure.svg)

## Intent
Record that an overdue notice could not be delivered to the borrower, so the shed manager knows
the neighbor wasn't told and can follow up in person.

## Invariants / Business Rules
- **INV-ONF-1:** A failure is recorded from the email provider's report that it refused an
  overdue notice or that the notice bounced. No person records it.
  - The email provider is outside the shed. Its failure report triggers Record Overdue Notice
    Failure, which records Overdue Notice Failed.
- **INV-ONF-2:** The shed records only the provider's one final failure report.
  - The provider makes 3 delivery attempts over about an hour and reports a final failure only
    if all of them fail. The shed does no retrying of its own, and a failure a later attempt
    recovers from is never reported.
- **INV-ONF-3:** Overdue Notice Failed is recorded for the loan the notice was about, with the
  date of the failure and whether the email was refused or bounced.
- **INV-ONF-6:** A loan has at most one Overdue Notice Failed. No further notice is attempted
  for it after a failure.
  - The shed manager follows up in person.
- **INV-ONF-7:** A failure report that matches no overdue notice the shed sent is ignored, and
  nothing is recorded.
- **INV-ONF-4:** Once Overdue Notice Failed is recorded, the loan leaves Overdue Loans To Notify.
  - This applies whether or not Overdue Notice Sent was recorded first: a refusal at hand-off
    records no Overdue Notice Sent, and a later bounce does.
- **INV-ONF-5:** Every recorded failure is shown to the shed manager on the Undelivered Notices
  screen.

## Scenarios (Given / When / Then)
- **Refused at hand-off**
  - **Given:** Ana's cordless drill loan is overdue and on Overdue Loans To Notify, and the email
    provider has refused the notice on all 3 of its attempts
  - **When:** the provider reports that the notice to ana@example.org was refused
  - **Then:** Overdue Notice Failed is recorded for Ana's drill loan, as refused, on today's
    date. The loan leaves Overdue Loans To Notify, and the shed manager's Undelivered Notices
    screen shows it.
- **Bounced after it was sent**
  - **Given:** Overdue Notice Sent was recorded for Ana's drill loan, and the email bounced on
    all 3 of the provider's attempts
  - **When:** the provider reports that the notice to ana@example.org bounced
  - **Then:** Overdue Notice Failed is recorded for Ana's drill loan, as bounced, on today's
    date, and the shed manager's Undelivered Notices screen shows it.
- **Report matching no notice**
  - **Given:** the shed has sent no overdue notice to cara@example.org
  - **When:** the provider reports that a notice to cara@example.org bounced
  - **Then:** nothing is recorded.
