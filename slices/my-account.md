---
schemaVersion: 1
pattern: state-view
swimlane: Neighbor → Neighbors
status: draft
version: 1
---
# Slice: My Account

![Diagram](./my-account.svg)

## Intent
Shows a neighbor their own account on the My Account screen: their details, and whether the
account is active, suspended or closed. This is the screen they close their account from.

## Invariants / Business Rules
- **INV-MYAC-1:** A neighbor's My Account shows only their own account.
- **INV-MYAC-2:** My Account shows the name and email the neighbor signed up with.
- **INV-MYAC-3:** A newly registered neighbor's account shows as active.
  - One account per Neighbor Registered.
- **INV-MYAC-4:** A suspended neighbor's account shows as suspended until they are reinstated,
  and then shows as active again.
- **INV-MYAC-5:** Once a neighbor closes their account, it shows as closed, permanently.
  - Closed is final. A suspended neighbor who closes their account sees it as closed, not
    suspended.
- **INV-MYAC-6:** After closing their account, the neighbor can still view My Account,
  read-only.
  - Close Account is no longer offered on a closed account.
- **INV-MYAC-8:** A person who signs up again after closing an account sees only their new
  account on My Account.
  - The old, closed account is no longer shown to them. It stays on the shed manager's Neighbors
    screen.
- **INV-MYAC-7:** Only Neighbor Registered, Neighbor Suspended, Neighbor Reinstated and
  Neighbor Account Closed change My Account.

## Scenarios (Given / When / Then)
- **Neighbor registered**
  - **Given:** a person who has not signed up
  - **When:** Neighbor Registered is recorded for them, with the name "Ana Ruiz" and the email
    ana@example.org
  - **Then:** their My Account shows "Ana Ruiz", ana@example.org, and the account as active.
- **Neighbor suspended**
  - **Given:** Ana's My Account shows her account as active
  - **When:** Neighbor Suspended is recorded for her
  - **Then:** her My Account shows the account as suspended.
- **Neighbor reinstated**
  - **Given:** Ana's My Account shows her account as suspended
  - **When:** Neighbor Reinstated is recorded for her
  - **Then:** her My Account shows the account as active.
- **Account closed**
  - **Given:** Ana's My Account shows her account as active
  - **When:** Neighbor Account Closed is recorded for her
  - **Then:** her My Account shows the account as closed, read-only, with no Close Account
    action. No other neighbor's account changes.
- **Suspended neighbor closes their account**
  - **Given:** Ana's My Account shows her account as suspended
  - **When:** Neighbor Account Closed is recorded for her
  - **Then:** her My Account shows the account as closed.
- **Signed up again after closing**
  - **Given:** Ana's account is closed, and was not suspended when she closed it
  - **When:** Neighbor Registered is recorded for a new account with her email, ana@example.org
  - **Then:** her My Account shows only the new account, as active. The closed account is no
    longer shown to her.
