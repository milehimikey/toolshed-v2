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
- **INV-ONF-2:** Overdue Notice Failed is recorded only once the retries have run out.
  - A failure the next retry recovers from is not recorded.
- **INV-ONF-3:** Overdue Notice Failed is recorded for the loan the notice was about.
- **INV-ONF-4:** Once Overdue Notice Failed is recorded, the loan leaves Overdue Loans To Notify.
  - This applies whether or not Overdue Notice Sent was recorded first: a refusal at hand-off
    records no Overdue Notice Sent, and a later bounce does.
- **INV-ONF-5:** Every recorded failure is shown to the shed manager on the Undelivered Notices
  screen.

## Scenarios (Given / When / Then)
- **Refused at hand-off**
  - **Given:** Ana's cordless drill loan is overdue and on Overdue Loans To Notify, and the email
    provider has refused the notice on every retry
  - **When:** the provider reports that the notice to ana@example.org was refused
  - **Then:** Overdue Notice Failed is recorded for Ana's drill loan. The loan leaves Overdue
    Loans To Notify, and the shed manager's Undelivered Notices screen shows it.
- **Bounced after it was sent**
  - **Given:** Overdue Notice Sent was recorded for Ana's drill loan, and the email bounced on
    every retry
  - **When:** the provider reports that the notice to ana@example.org bounced
  - **Then:** Overdue Notice Failed is recorded for Ana's drill loan, and the shed manager's
    Undelivered Notices screen shows it.

## Open Questions
- [ ] Who does the retrying: the email provider (which reports only a final failure), or the
  shed's gateway, sending the notice again after each failure report?
  - Recommendation: the provider. Email providers already retry delivery and report a final
    bounce. The shed then records the one final report and does no retrying of its own. If the
    gateway retries instead, it has to count attempts per loan, which this model doesn't carry.
  - How many retries, over how long, is open on Send Overdue Notice.
- [ ] Once a failure is recorded for a loan, can another notice ever be sent for it? Or is the
  shed manager's follow-up the end of it?
  - Recommendation: no further notice, so a loan has at most one Overdue Notice Failed.
- [ ] What happens to a failure report that doesn't match any overdue notice the shed sent?
  - Recommendation: ignore it, and record nothing.
