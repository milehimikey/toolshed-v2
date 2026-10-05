---
schemaVersion: 1
pattern: state-view
swimlane: Shed Manager → Neighbors
status: draft
version: 1
---
# Slice: Flagged Neighbors

![Diagram](./flagged-neighbors.svg)

## Intent
Shows the shed manager every neighbor the system has flagged for repeated late returns, on the
Flagged Neighbors screen they suspend neighbors from. The system flags; the shed manager decides.

## Invariants / Business Rules
- **INV-FLN-1:** A neighbor appears on Flagged Neighbors when Neighbor Flagged is recorded for
  them.
  - A neighbor is flagged by the system after 3 late returns within 90 days. Being on this list
    never suspends anyone by itself, and the neighbor is not told they were flagged.
  - A suspended neighbor is never flagged, so never appears here while suspended.
- **INV-FLN-2:** Each entry shows the neighbor's name and email, the date of their latest flag,
  and the late returns behind the flag, each with the tool's name.
  - The neighbor's name comes from Neighbor Registered. The email is their current one:
    Neighbor Registered sets it, Neighbor Email Changed replaces it.
  - Each tool's name comes from Tool Added. Each late return's due date and return date come from
    the loan's own events: Tool Checked Out, Loan Extended and Tool Returned.
  - Neighbor Flagged names the late returns it uses up, as the loans they belong to. It carries
    no copies of names or dates; the view looks those loans up in the loan events it hears.
- **INV-FLN-3:** A neighbor has at most one entry. If they are flagged again while still listed,
  the entry shows how many times they have been flagged, and the latest flag date.
- **INV-FLN-4:** A flagged neighbor stays on the list until they are suspended or close their
  account.
  - There is no dismiss action. A flag the shed manager decides not to act on stays on the list.
- **INV-FLN-5:** When a flagged neighbor is suspended, they leave the list.
  - Suspending is the only action this screen offers. A flagged neighbor suspended from the
    Neighbors screen leaves this list too.
- **INV-FLN-6:** When a flagged neighbor closes their account, they leave the list.
  - Closed is final, so the shed manager is never offered Suspend on a closed account.
- **INV-FLN-7:** Only the shed manager sees Flagged Neighbors.
- **INV-FLN-8:** Only Neighbor Flagged, Neighbor Suspended and Neighbor Account Closed change
  Flagged Neighbors. Neighbor Registered, Neighbor Email Changed and Tool Added supply names
  and emails; Tool Checked Out, Loan Extended and Tool Returned supply the late returns' dates.

## Scenarios (Given / When / Then)
- **Neighbor flagged**
  - **Given:** Flagged Neighbors is empty, and Ana Ruiz (ana@example.org) returned tools late on
    1 September, 20 September and 5 October
  - **When:** Neighbor Flagged is recorded for Ana on 5 October
  - **Then:** Flagged Neighbors lists Ana Ruiz, ana@example.org, flagged once, on 5 October, with
    those three late returns and a Suspend action.
- **Flagged again while still listed**
  - **Given:** Flagged Neighbors lists Ana, flagged once, on 5 October
  - **When:** Neighbor Flagged is recorded for Ana again on 15 November
  - **Then:** Flagged Neighbors still lists Ana once, now flagged twice, latest on 15 November,
    with the late returns behind the new flag.
- **Flagged neighbor suspended**
  - **Given:** Flagged Neighbors lists Ana and Ben
  - **When:** Neighbor Suspended is recorded for Ana
  - **Then:** Ana leaves Flagged Neighbors. Ben is still listed.
- **Flagged neighbor closes their account**
  - **Given:** Flagged Neighbors lists Ana
  - **When:** Neighbor Account Closed is recorded for Ana
  - **Then:** Ana leaves Flagged Neighbors.
- **Flag left alone**
  - **Given:** Flagged Neighbors has listed Ana since 5 October, and the shed manager has not
    suspended her
  - **When:** a month passes with no Neighbor Suspended recorded for Ana
  - **Then:** Ana is still listed.
- **Flagged neighbor changes their email**
  - **Given:** Flagged Neighbors lists Ana Ruiz, ana@example.org
  - **When:** Neighbor Email Changed is recorded for Ana, to ana.ruiz@example.net
  - **Then:** Flagged Neighbors lists Ana Ruiz, ana.ruiz@example.net. Her flags are unchanged.

## Open Questions
None.
