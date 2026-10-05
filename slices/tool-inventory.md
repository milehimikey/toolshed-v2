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
- **INV-TINV-2:** A retired tool drops off the inventory.
  - Retired tools are not listed, not even marked as retired.
  - A tool recorded lost is retired by that, so it drops off too.
- **INV-TINV-3:** The inventory changes only when a tool is added, checked out, returned, retired
  or recorded lost.
  - Extensions, holds and overdue notices leave it unchanged. Hold state is shown to neighbors
    on the Tool Catalog, not here.
  - A tool's details never change after it is added. A mistake is corrected by retiring the
    tool and adding it again.
- **INV-TINV-4:** Each entry shows the tool's name, its description when it has one, and its
  default checkout period.
- **INV-TINV-5:** Each entry shows whether the tool is out on loan.
  - A tool is out from Tool Checked Out until Tool Returned or Tool Lost.
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
  - **When:** Tool Checked Out is recorded for the drill
  - **Then:** the Tool Inventory shows the drill as out on loan, and offers "mark lost" on it.
- **Tool returned**
  - **Given:** the Tool Inventory shows the drill as out on loan
  - **When:** Tool Returned is recorded for the drill
  - **Then:** the Tool Inventory shows the drill as in the shed, with no "mark lost".
- **Tool retired**
  - **Given:** the Tool Inventory lists a cordless drill and a ladder, both in the shed
  - **When:** Tool Retired is recorded for the cordless drill
  - **Then:** the cordless drill drops off the Tool Inventory, and the ladder is still listed.
- **Tool recorded lost**
  - **Given:** the Tool Inventory shows the ladder as out on loan
  - **When:** Tool Lost is recorded for the ladder
  - **Then:** the ladder drops off the Tool Inventory.

## Open Questions
- [ ] A tool can be retired while it is on loan, and a lost tool is recorded from this screen.
  But a retired tool drops off the inventory at once. If a tool retired while on loan never comes
  back, where does the shed manager record it lost?
  - Options:
    - (a) A tool retired while out stays on the inventory, marked retired and out, until it is
      returned or recorded lost. Then it drops off.
    - (b) Retiring a tool that is out is refused. This was turned down.
    - (c) Record the loss somewhere else. A separate loans screen for the shed manager was
      turned down.
  - Recommendation: (a). It is the only option that keeps both decisions. The inventory already
    hears every event it needs.
- [ ] Besides "out on loan", does an entry show who has the tool and when it is due?
  - Recommendation: yes, the borrower's name and the due date. The shed manager needs both to
    judge whether a tool is lost and to contact the borrower first. The inventory would also
    hear Neighbor Registered and Loan Extended, which is a model change.
