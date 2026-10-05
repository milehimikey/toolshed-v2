---
schemaVersion: 1
pattern: state-change
swimlane: Neighbor → Neighbors
status: draft
version: 1
---
# Slice: Close Account

![Diagram](./close-account.svg)

## Intent
Let a neighbor who is moving away or no longer wants to take part close their own account, from
My Account. Closing is the neighbor's own choice, and it is final.

## Invariants / Business Rules
- **INV-CLS-1:** A neighbor closes their own account themselves, from My Account.
  - The shed manager doesn't close accounts.
- **INV-CLS-2:** An active or a suspended neighbor can close their account. An account that is
  already closed can't be closed again.
- **INV-CLS-3:** Closed is final.
  - A closed account is never reopened, suspended or reinstated. A neighbor who wants to come
    back signs up again as a new account.
  - If the account was suspended when it was closed, its email can't be used to sign up again.
- **INV-CLS-4:** Closing an account ends all of the neighbor's holds.
  - Holds waiting behind theirs move up. A tool set aside for them passes to the next hold, or
    becomes available if no hold is waiting, as when a set-aside expires.
- **INV-CLS-5:** After closing, the neighbor can still view My Account, read-only, and can't
  borrow.
  - They can't check out, extend, or place holds.
- **INV-CLS-6:** A closed account is never flagged, and drops off Flagged Neighbors and Suspended
  Neighbors. The Neighbors screen shows it as closed, and My Account shows it closed.

## Scenarios (Given / When / Then)
- **Happy path**
  - **Given:** Ana is active, with no tools on loan, and a hold waiting at position 1 on the
    cordless drill, with Cara's hold behind hers
  - **When:** Ana closes her account from My Account
  - **Then:** Neighbor Account Closed is recorded for Ana. Her hold on the drill ends and Cara's
    moves to position 1. The Neighbors screen shows Ana as closed, and her My Account shows her
    account closed, read-only.
- **Suspended neighbor closes their account**
  - **Given:** Ana is suspended, with no tools on loan
  - **When:** Ana closes her account from My Account
  - **Then:** Neighbor Account Closed is recorded for Ana. She drops off Suspended Neighbors, and
    the Neighbors screen shows her as closed, not suspended.
- **Closing while a tool is set aside**
  - **Given:** the drill is set aside for Ana, and Cara's hold is waiting
  - **When:** Ana closes her account
  - **Then:** Neighbor Account Closed is recorded. Ana's set-aside ends, and the drill passes to
    Cara.

## Open Questions
- [ ] Can a neighbor close their account while they still have tools on loan, including overdue
  ones?
  - Options:
    - (a) Refused until every tool is returned, with a reason they can see.
    - (b) Allowed. The loans stay open: the neighbor can still return the tools, overdue
      notices still go out, and a late return doesn't count toward a flag, because closed
      accounts are never flagged.
  - Recommendation: (a). It keeps the shed's tools from walking off with a closed account, and
    the questions that depend on this all fall away: returning after closing, notices to closed
    accounts, and late returns by closed accounts. With (b), each of those needs its own rule.
  - Holds don't need this rule: closing ends them.
