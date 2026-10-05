---
schemaVersion: 1
pattern: state-change
swimlane: Neighbor → Neighbors
status: draft
version: 1
---
# Slice: Change Email

![Diagram](./change-email.svg)

## Intent
Let a neighbor change the email address the shed uses for them, from My Account, so a wrong or
outdated address can be put right and overdue and pickup emails reach them.

## Invariants / Business Rules
- **INV-CEM-1:** A neighbor changes their own email themselves, from My Account.
  - The shed manager doesn't change neighbors' emails.
- **INV-CEM-2:** The new email is refused if it belongs to an account that is not closed, or to a
  closed account that was suspended when it was closed.
  - This is the same one-account-per-email rule as at sign-up. An account's email is always its
    current one.
- **INV-CEM-3:** A neighbor whose account is closed can't change their email.
  - After closing, My Account is read-only.
- **INV-CEM-3a:** A suspended neighbor can't change their email. The change is refused with a
  reason they can see, and they can change it once reinstated.
  - Otherwise a suspended neighbor could change to a new address, close their account, and sign
    up again with their original address.
- **INV-CEM-4:** Once changed, the new email replaces the old one everywhere.
  - Every overdue or pickup email the shed sends from then on goes to the new address, including
    one for a loan or set-aside already waiting to be emailed.
  - Neighbors, My Account, Undelivered Notices, Flagged Neighbors and Suspended Neighbors show
    the new email.
- **INV-CEM-5:** The old email is released.
  - No account holds it any more, so it can be used to sign up or by another neighbor's change.
- **INV-CEM-6:** Changing the email doesn't resend anything.
  - A notice already sent or already recorded as undelivered is not tried again at the new
    address.
- **INV-CEM-7:** The new email takes effect at once, with no verification.
- **INV-CEM-8:** Only the email can be changed. A neighbor's name stays as they registered it.

## Scenarios (Given / When / Then)
- **Happy path**
  - **Given:** Ana is active with ana@oldmail.example, and no other account uses ana@example.org
  - **When:** Ana changes her email to ana@example.org from My Account
  - **Then:** Neighbor Email Changed is recorded for Ana, with ana@example.org. My Account and the
    shed manager's Neighbors screen show ana@example.org.
- **Next email goes to the new address**
  - **Given:** Ana changed her email to ana@example.org, and her drill loan, due 12 October, is
    still out
  - **When:** her loan joins Overdue Loans To Notify at the start of 13 October
  - **Then:** the overdue notice for the drill is addressed to ana@example.org.
- **Changing the email after reinstatement**
  - **Given:** Ana was suspended and has since been reinstated, and no other account uses
    ana.ruiz@example.org
  - **When:** Ana changes her email to ana.ruiz@example.org from My Account
  - **Then:** Neighbor Email Changed is recorded for Ana, with ana.ruiz@example.org.

## Open Questions
None.
