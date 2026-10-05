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
- **INV-OVN-3:** Overdue notices go by email only, to the email address the borrower signed up
  with.
- **INV-OVN-4:** Overdue Notice Sent means the notice was handed to the email provider.
  - It does not mean the borrower received or read it.
- **INV-OVN-5:** No notice is sent for a loan whose tool has been returned.
  - A loan returned before its notice goes out leaves Overdue Loans To Notify and never gets one.
- **INV-OVN-6:** The notice goes out as soon as the loan is overdue, with no quiet hours.
  - A loan is overdue from the start of the day after its due date, in the shed's local time.
- **INV-OVN-7:** The notice gives the neighbor's name, the tool's name, the due date, a request
  to return the tool, and that an overdue loan blocks new checkouts and pickups.
  - Overdue Loans To Notify carries all of these with each loan.
- **INV-OVN-8:** If the email provider refuses the notice, or the email bounces, the system
  retries a few times. If it still fails, Overdue Notice Failed is recorded, and the shed manager
  sees it on the Undelivered Notices screen.
  - Retrying never sends a second notice for a loan once one has been delivered.

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
- **Notice can't be delivered**
  - **Given:** Ana's drill loan is overdue, and the email provider refuses the notice every time
    the system tries
  - **When:** the retries run out
  - **Then:** Overdue Notice Failed is recorded for the drill loan, and the shed manager's
    Undelivered Notices screen shows it.

## Open Questions
- [ ] How many retries, over how long, before a notice is recorded as failed?
  - Recommendation: 3 attempts over about an hour. The shed manager then hears about it the same
    day.
- [ ] After Overdue Notice Failed, does the system try that loan again later (for example, after
  the neighbor's email changes), or is the shed manager's follow-up the end of it?
  - Recommendation: no further attempts. The failure is the record that the neighbor wasn't
    told, and the shed manager follows up in person. For a refusal at hand-off, no Overdue Notice
    Sent is recorded, so Overdue Loans To Notify must also drop the loan on Overdue Notice
    Failed. Otherwise the gateway would keep picking it up.
