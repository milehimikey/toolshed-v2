---
schemaVersion: 1
pattern: translation
swimlane: System → Loans
status: draft
version: 1
---
# Slice: Send Overdue Notice

![Diagram](./send-overdue-notice.svg)

## Intent
Tell a borrower by email that their loan is overdue, once per loan, so they return the tool. The
shed records that the notice was handed to the email provider, which shows the borrower was told
and stops a second notice.

## Invariants / Business Rules
- **INV-OVN-1:** The system sends overdue notices. No person sends them.
  - The overdue notice gateway watches Overdue Loans To Notify and issues Send Overdue Notice for
    each loan on it.
- **INV-OVN-2:** At most one overdue notice is sent per loan.
  - Once Overdue Notice Sent is recorded for a loan, the loan leaves Overdue Loans To Notify and
    is never sent another notice. Notices do not repeat on a schedule.
- **INV-OVN-3:** Overdue notices go by email only, to the borrower's current email address.
  - Neighbor Registered sets it, Neighbor Email Changed replaces it.
- **INV-OVN-4:** Overdue Notice Sent is recorded when the notice is handed to the email provider,
  every time a notice is handed over.
  - It does not mean the borrower received or read it. Any failure the provider reports later
    follows this Overdue Notice Sent.
  - A hand-off call that errors is retried by the gateway until the provider accepts the notice.
    It is not a failure of the notice.
- **INV-OVN-5:** No notice is sent for a loan whose tool has been returned or recorded lost.
  - A loan that ends before its notice goes out leaves Overdue Loans To Notify and never gets
    one.
- **INV-OVN-6:** The notice goes out as soon as the loan is overdue, with no quiet hours.
  - A loan is overdue from the start of the day after its due date, in the shed's local time.
- **INV-OVN-7:** The notice gives the neighbor's name, the tool's name, the due date, a request
  to return the tool, and that an overdue loan blocks new checkouts and pickups.
  - Overdue Loans To Notify carries all of these with each loan.
- **INV-OVN-8:** Delivery retries are the email provider's: 3 delivery attempts over about an
  hour. If the notice is refused or bounces on all of them, the provider reports one final
  failure, which is recorded as Overdue Notice Failed.
  - Once the provider has accepted a notice, the gateway never hands it over again.
- **INV-OVN-9:** After Overdue Notice Failed, no further notice is attempted for that loan.
  - The loan already left Overdue Loans To Notify at hand-off. The shed manager follows up in
    person.

## Scenarios (Given / When / Then)
- **Happy path**
  - **Given:** Ana Ruiz (ana@example.org) borrowed the cordless drill, due 12 October, and has
    not returned it. At the start of 13 October, shed time, her loan joins Overdue Loans To
    Notify.
  - **When:** the overdue notice gateway hands the email provider a notice to ana@example.org,
    telling Ana Ruiz the cordless drill was due 12 October, asking her to return it, and saying
    that an overdue loan blocks new checkouts and pickups
  - **Then:** Overdue Notice Sent is recorded for the drill loan. The loan leaves Overdue Loans To
    Notify, and Ana's My Loans shows that an overdue notice was sent.
- **No second notice**
  - **Given:** Overdue Notice Sent has been recorded for Ana's drill loan, and the drill is still
    out a week later
  - **When:** the overdue notice gateway checks Overdue Loans To Notify
  - **Then:** the drill loan is not on the list, and no further notice is sent.

## Open Questions
None.
