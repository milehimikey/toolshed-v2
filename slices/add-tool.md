---
schemaVersion: 1
pattern: state-change
swimlane: Shed Manager → Tools
status: draft
version: 1
---
# Slice: Add Tool

![Diagram](./add-tool.svg)

## Intent
Put a tool into the shed so neighbors can borrow it. The shed manager records the tool's name,
an optional description, and its default checkout period. That period is the longest a checkout
of the tool can run.

## Invariants / Business Rules
- **INV-ADD-1:** Only the shed manager can add a tool.
  - Neighbors do not add tools.
- **INV-ADD-2:** Every tool is added with a default checkout period.
  - A checkout of this tool may choose a shorter period, never a longer one.
- **INV-ADD-3:** A tool's default checkout period is a whole number of days, at least 1 and no
  more than the shed's maximum.
  - The maximum is still to be named (see Open Questions).
- **INV-ADD-4:** Every tool has a name. A description is optional.
- **INV-ADD-5:** Each physical item is added as its own tool.
  - Two identical drills are two tools, each added separately. There is no quantity on a tool.
- **INV-ADD-8:** Tool names need not be unique.
  - Two identical drills may both be named "Cordless drill". Each tool is told apart by its own
    identity, not its name.
- **INV-ADD-6:** A tool can be borrowed as soon as it is added.
  - There is no step between adding a tool and lending it.
- **INV-ADD-7:** A tool's name, description and default checkout period never change after it
  is added.
  - To correct a mistake, the shed manager retires the tool and adds it again.

## Scenarios (Given / When / Then)
- **Happy path**
  - **Given:** the shed has no tools
  - **When:** the shed manager adds a tool named "Cordless drill", described as "18V, two
    batteries", with a default checkout period of 7 days, from the Tool Inventory screen
  - **Then:** Tool Added is recorded with that name, description and 7-day default checkout
    period. The drill appears in the shed manager's Tool Inventory, and in the neighbors' Tool
    Catalog as available to borrow.
- **Tool added without a description**
  - **Given:** the shed has no tools
  - **When:** the shed manager adds a tool named "Ladder" with a default checkout period of 3
    days and no description
  - **Then:** Tool Added is recorded with that name, no description, and the 3-day period.
- **A second identical item**
  - **Given:** the shed holds one tool named "Cordless drill"
  - **When:** the shed manager adds a second, identical drill, also named "Cordless drill"
  - **Then:** Tool Added is recorded for a second, separate tool. Both drills are listed in Tool
    Inventory and Tool Catalog.

## Open Questions
- [ ] What is the maximum default checkout period, in days?
  - A maximum exists, but its value has not been named.
