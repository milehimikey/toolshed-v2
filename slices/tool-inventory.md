---
schemaVersion: 1
pattern: state-view
swimlane: Shed Manager → Tools
status: draft
version: 1
---
# Slice: Tool Inventory

![Diagram](./tool-inventory.svg)

## Intent
Shows the shed manager every tool in the shed on the Tool Inventory screen, and which of them are
out on loan. They add and retire tools from this screen, and record a tool lost when it is out
and never coming back.

## Invariants / Business Rules
- **INV-TINV-1:** Every added tool appears in the inventory exactly once.
  - One entry per Tool Added; nothing else creates an entry.
  - Each physical item is its own tool, so two identical drills are two entries.
- **INV-TINV-2:** A retired tool drops off the inventory once it is back in the shed or recorded
  lost.
  - A tool retired while it is in the shed drops off at once.
  - A tool retired while it is out on loan stays listed, marked retired and out, until it is
    returned or recorded lost. Then it drops off. This lets the shed manager record it lost if
    it never comes back.
  - A tool recorded lost is retired by that, so it drops off too.
- **INV-TINV-3:** The inventory changes only when a tool is added, checked out, extended, returned,
  retired or recorded lost, or when a neighbor registers.
  - Neighbor Registered supplies the borrower names shown on tools that are out.
  - Holds and overdue notices leave it unchanged. Hold state is shown to neighbors
    on the Tool Catalog, not here.
  - A tool's details never change after it is added. A mistake is corrected by retiring the
    tool and adding it again.
- **INV-TINV-4:** Each entry shows the tool's name, its description when it has one, and its
  default checkout period.
- **INV-TINV-5:** Each entry shows whether the tool is out on loan. A tool that is out also shows
  the borrower's name and the due date.
  - A tool is out from Tool Checked Out until Tool Returned or Tool Lost.
  - The due date moves with each extension.
- **INV-TINV-6:** The inventory offers "mark lost" only on a tool that is out on loan.
  - Recording a tool lost ends its loan, retires the tool and ends its holds. It never counts
    as a late return for the borrower.

## Scenarios (Given / When / Then)
- **Tool added**
  - **Given:** the shed has no tools
  - **When:** Tool Added is recorded for a cordless drill, described as "18V, two batteries",
    with a default checkout period of 7 days
  - **Then:** the Tool Inventory lists the cordless drill with its description and its 7-day
    default checkout period, in the shed.
- **Tool added without a description**
  - **Given:** the shed has no tools
  - **When:** Tool Added is recorded for a ladder with no description and a default checkout
    period of 3 days
  - **Then:** the Tool Inventory lists the ladder with its 3-day default checkout period and no
    description.
- **Two of the same tool**
  - **Given:** the Tool Inventory lists one cordless drill
  - **When:** Tool Added is recorded for a second cordless drill
  - **Then:** the Tool Inventory lists both drills as separate entries.
- **Tool checked out**
  - **Given:** the Tool Inventory lists the cordless drill, in the shed
  - **When:** Tool Checked Out is recorded for Ana borrowing the drill, due 12 October
  - **Then:** the Tool Inventory shows the drill as out on loan to Ana, due 12 October, and offers
    "mark lost" on it.
- **Borrower's name comes from their registration**
  - **Given:** Neighbor Registered was recorded for Ana Ruiz, and the drill is in the shed
  - **When:** Tool Checked Out is recorded for Ana borrowing the drill, due 12 October
  - **Then:** the Tool Inventory shows the drill out on loan to Ana Ruiz, the name she
    registered with.
- **Loan extended**
  - **Given:** the Tool Inventory shows the drill out on loan to Ana, due 12 October
  - **When:** Loan Extended is recorded for Ana's drill loan, moving the due date to 19 October
  - **Then:** the Tool Inventory shows the drill out on loan to Ana, due 19 October.
- **Tool returned**
  - **Given:** the Tool Inventory shows the drill as out on loan
  - **When:** Tool Returned is recorded for the drill
  - **Then:** the Tool Inventory shows the drill as in the shed, with no "mark lost".
- **Tool retired**
  - **Given:** the Tool Inventory lists a cordless drill and a ladder, both in the shed
  - **When:** Tool Retired is recorded for the cordless drill
  - **Then:** the cordless drill drops off the Tool Inventory, and the ladder is still listed.
- **Tool retired while out on loan**
  - **Given:** the Tool Inventory shows the ladder out on loan to Ben, due 20 October
  - **When:** Tool Retired is recorded for the ladder
  - **Then:** the Tool Inventory still lists the ladder, marked retired and out on loan to Ben,
    due 20 October, with "mark lost" still offered.
- **Retired tool comes back**
  - **Given:** the Tool Inventory shows the ladder marked retired and out on loan to Ben
  - **When:** Tool Returned is recorded for the ladder
  - **Then:** the ladder drops off the Tool Inventory.
- **Tool recorded lost**
  - **Given:** the Tool Inventory shows the ladder as out on loan
  - **When:** Tool Lost is recorded for the ladder
  - **Then:** the ladder drops off the Tool Inventory.

## Open Questions
None.
