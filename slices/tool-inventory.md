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
Shows the shed manager every tool in the shed on the Tool Inventory screen — the screen they add
and retire tools from — so they can see what the shed holds.

## Invariants / Business Rules
- **INV-TINV-1:** Every added tool appears in the inventory exactly once.
  - One entry per Tool Added; nothing else creates an entry.
  - Each physical item is its own tool, so two identical drills are two entries.
- **INV-TINV-2:** A retired tool drops off the inventory.
  - The inventory shows only tools in the shed now. Retired tools are not listed, not even
    marked as retired.
- **INV-TINV-3:** Only adding and retiring tools changes the inventory.
  - Checkouts, returns, extensions and holds leave it unchanged. Loan and hold state is shown
    to neighbors on the Tool Catalog, not here.
  - A tool's details never change after it is added. A mistake is corrected by retiring the
    tool and adding it again.
- **INV-TINV-4:** Each entry shows the tool's name, its description when it has one, and its
  default checkout period.

## Scenarios (Given / When / Then)
- **Tool added**
  - **Given:** the shed has no tools
  - **When:** Tool Added is recorded for a cordless drill, described as "18V, two batteries",
    with a default checkout period of 7 days
  - **Then:** the Tool Inventory lists the cordless drill with its description and its 7-day
    default checkout period.
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
- **Tool retired**
  - **Given:** the Tool Inventory lists a cordless drill and a ladder
  - **When:** Tool Retired is recorded for the cordless drill
  - **Then:** the cordless drill drops off the Tool Inventory, and the ladder is still listed.

## Open Questions
None.
