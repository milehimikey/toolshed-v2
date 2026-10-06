---
schemaVersion: 1
pattern: state-change
swimlane: Neighbor → Loans
status: reviewed
reviewedBy: Mike Key
reviewedOn: 2026-10-05
version: 1
---
# Slice: Return Tool

![Diagram](./return-tool.svg)

## Intent
Let a borrower bring a tool back and end their loan themselves, from My Loans. The tool is then
back in the shed, for the next holder or for anyone to borrow. The return records whether it was
late, which is what counts toward a neighbor being flagged.

## Trigger & Actor
The borrower, from their own My Loans screen, for one of their own loans. My Loans offers Return
on every loan it lists (INV-MYL-12), including overdue loans, loans whose tool was retired while
out, and loans of a suspended neighbor. The shed manager does not record returns; a tool that
never comes back is recorded lost instead (Record Tool Lost).

## Command / Input
**Command:** `Return Tool`

A NeighborId or LoanId is a UUID.

| Field | Type | Required | Rules / Validation |
|-------|------|----------|--------------------|
| neighborId | NeighborId | yes | The neighbor returning, who must be the one making the request and the loan's borrower (INV-RET-1). |
| loanId | LoanId | yes | The loan to end. Must be a loan that has been checked out (INV-RET-11), and still out (INV-RET-2). |

There is no field for the tool's condition: nothing about it is recorded on return. A damaged
tool is the shed manager's to retire.

## Trigger
**Triggered by:** screen `My Loans` @Neighbor

## Event(s) Emitted
**Event:** `Tool Returned` → context `Loans`
**Read by:** My Loans (the loan drops off), Tool Inventory and Tool Catalog (the tool is back in the
shed), Holds To Fulfill (a returned tool with holds waiting), Overdue Loans To Notify and
Undelivered Notices (the loan has ended), and Neighbors To Flag and Flagged Neighbors (whether the
return was late).

| Field | Type | Immutable Fact? | Source / Notes |
|-------|------|-----------------|----------------|
| loanId | LoanId | yes | From the command. |
| toolId | ToolId | yes | The loan's tool, from its Tool Checked Out. Carried so views about tools and holds match on the tool directly. |
| returnedAt | Instant | yes | The UTC moment the event is recorded, set by the system. |
| returnedOn | Date | yes | Set by the system: the calendar date of returnedAt in the shed's local time zone. |
| late | Bool | yes | Set by the system: yes when returnedOn is after the loan's due date in force at the moment of return (INV-RET-4). |

The event never changes. returnedOn and late are worked out once, by the command's handler, so
every view reads the same answer.

## Read Model / View
This slice produces no view of its own. Tool Returned ends the loan in My Loans, Overdue Loans To
Notify and Undelivered Notices; puts the tool back in the shed on Tool Inventory and Tool Catalog
(or drops a tool retired while out); adds a tool with holds waiting to Holds To Fulfill; and gives
Neighbors To Flag and Flagged Neighbors a late or on-time return. Each is specified in its own
doc, and all are eventually consistent.

## Invariants / Business Rules
Once a loan is returned, no overdue notice goes out for it (INV-OLN-4, Overdue Loans To Notify). A
returned tool with no holds waiting is available to borrow straight away; one with holds waiting
is reserved, and until Hold Fulfilled is recorded only the neighbor at the front of the queue may
check it out (INV-CHK-3a, Check Out Tool). Late returns count toward a neighbor being flagged, 3
within 90 days (Neighbors To Flag). Those rules are owned by those slices.

- **INV-RET-1:** The borrower returns their own loan themselves, from My Loans.
  - The request must come from the neighbor it names, and the loan must be theirs. Sign-in, handled outside the model, supplies the acting neighbor's ID; a request whose neighbor ID isn't the signed-in neighbor's is refused `requester-mismatch`. The shed manager does not record returns.
- **INV-RET-2:** Only a loan that is still out can be returned, and only once.
  - A loan ended by the shed manager recording the tool lost can't be returned.
  - A closed account never has a loan out, because an account can't be closed while tools are
    on loan (INV-CLS-7, Close Account).
- **INV-RET-3:** Returning a tool records only Tool Returned.
  - Setting the tool aside for the first waiting hold is a separate system reaction to the
    return, recorded as Hold Fulfilled (Fulfill Hold).
- **INV-RET-4:** A return is late when it happens after the loan's due date: from the start of
  the day after the due date, in the shed's local time.
  - A return on the due date itself is on time. The due date is the one in force at the moment
    of return: the latest Loan Extended's dueDate, or Tool Checked Out's if never extended.
  - Tool Returned records the answer as `late`, with the date of return as `returnedOn`.
- **INV-RET-5:** An overdue loan can be returned.
- **INV-RET-7:** A suspended neighbor can return a tool.
- **INV-RET-9:** A tool retired while on loan can still be returned.
  - The return ends the loan as usual. The tool stays retired: it doesn't come back into the Tool
    Catalog or Tool Inventory, and no hold is waiting on it, because retiring it ended its holds.
- **INV-RET-10:** Returning a loan that this borrower has already returned succeeds and records
  nothing.
  - So a double submit, or a retry after a lost response, never records a second return.
- **INV-RET-11:** The loan must have been checked out.
- **INV-RET-12:** A request with no sign-in is refused before anything else is checked.

## Scenarios (Given / When / Then)
- **Happy path: on-time return, no holds (INV-RET-1, INV-RET-3, INV-RET-4)**
  - **Given:** Ana (N1) has the cordless drill (T1) on loan L1, due 12 October, and no holds are
    waiting on it
  - **When:** at 15:00 on 11 October, shed time, Ana returns L1 from My Loans
  - **Then:** Tool Returned is recorded for L1, with toolId T1, the time it was recorded,
    returnedOn 11 October, and late no. Nothing else is recorded.
- **Return on the due date (INV-RET-4)**
  - **Given:** L1 is due 12 October
  - **When:** Ana returns L1 at 23:59 on 12 October, shed time
  - **Then:** Tool Returned is recorded with returnedOn 12 October, late no.
- **Late return (INV-RET-4, INV-RET-5)**
  - **Given:** L1 was due 12 October, and Overdue Notice Sent has been recorded for it
  - **When:** Ana returns L1 at 00:05 on 13 October, shed time
  - **Then:** Tool Returned is recorded with returnedOn 13 October, late yes.
- **Return after an extension (INV-RET-4)**
  - **Given:** L1 was due 12 October, and Loan Extended moved its due date to 19 October
  - **When:** Ana returns L1 on 15 October
  - **Then:** Tool Returned is recorded with late no, judged against 19 October.
- **Return with a hold waiting (INV-RET-3)**
  - **Given:** Ana has L1, and Ben's hold on T1 is at the front of the queue
  - **When:** Ana returns L1
  - **Then:** Tool Returned is recorded. No Hold Fulfilled is recorded by this command; setting T1
    aside for Ben is Fulfill Hold's.
- **Suspended neighbor returns a tool (INV-RET-7)**
  - **Given:** Ana is suspended and has L1 out
  - **When:** Ana returns L1 from My Loans
  - **Then:** Tool Returned is recorded for L1.
- **Returning a tool that was retired while on loan (INV-RET-9)**
  - **Given:** Ana has the ladder (T2) on loan L2, and Tool Retired was recorded for T2 while it
    was out
  - **When:** Ana returns L2 from My Loans
  - **Then:** Tool Returned is recorded for L2, with toolId T2. T2 stays retired.
- **The same return twice (INV-RET-10)**
  - **Given:** Tool Returned was recorded for L1
  - **When:** Ana's same Return Tool request for L1 arrives again
  - **Then:** the request succeeds and nothing new is recorded.
- **Loan already recorded lost (INV-RET-2)**
  - **Given:** Tool Lost was recorded for Ana's loan L3
  - **When:** Ana asks to return L3
  - **Then:** refused with `loan-ended`: "This loan has ended." No event is recorded.
- **Someone else's loan (INV-RET-1)**
  - **Given:** Ana has L1 out
  - **When:** Ben asks, for himself, to return L1
  - **Then:** refused with `not-your-loan`: "You can only return your own loans." No event is
    recorded.
- **A request in someone else's name (INV-RET-1)**
  - **Given:** Ana has L1 out
  - **When:** a request from Ben asks to return L1 for Ana
  - **Then:** refused with `requester-mismatch`: "You can only return loans for yourself." No
    event is recorded.
- **Unknown loan (INV-RET-11)**
  - **Given:** no loan has ID L9
  - **When:** Ana asks to return L9
  - **Then:** refused with `loan-not-found`: "There is no such loan." No event is recorded.
- **Not signed in (INV-RET-12)**
  - **Given:** nobody is signed in
  - **When:** a request for returning a tool arrives
  - **Then:** refused with `not-signed-in`: "Please sign in." No event is recorded.

## Alternate & Error Flows
Every refusal records nothing and returns one reason: the stable name, and the sentence shown to
the borrower on the My Loans screen. The name is the contract; the sentence is the required
wording of the message.

The checks run in this order, and the first that fails is the reason returned:
1. `not-signed-in` (INV-RET-12): a request with no sign-in is refused first.
2. `requester-mismatch` (INV-RET-1).
3. `loan-not-found` (INV-RET-11).
4. `not-your-loan` (INV-RET-1).
5. If Tool Returned is already recorded for the loan, the request is a repeat: it succeeds and
   records nothing (INV-RET-10).
6. `loan-ended`, if Tool Lost is recorded for the loan (INV-RET-2).

| Reason | Sentence | Rule |
|--------|----------|------|
| `not-signed-in` | Please sign in. | INV-RET-12 |
| `requester-mismatch` | You can only return loans for yourself. | INV-RET-1 |
| `loan-not-found` | There is no such loan. | INV-RET-11 |
| `not-your-loan` | You can only return your own loans. | INV-RET-1 |
| `loan-ended` | This loan has ended. | INV-RET-2 |

`requester-mismatch` and `loan-not-found` can only come from a faulty or impersonating caller, not
from the screen. A missing or malformed ID (not a UUID) is bad input, rejected before any rule is
checked. It is not a refusal and has no reason name. The names mean the same conditions as in
Extend Loan, and `loan-ended` uses the same sentence.

Suspension, an overdue loan and a retired tool never stop a return; there is no refusal for them.

Idempotency: a return is identified by its loan. A repeat after the loan was returned succeeds and
records nothing. A loan ended by Tool Lost is refused, because returning it would be a different
outcome from the one recorded.

Requests at the same moment: each Return Tool is decided against the loan's events as already
recorded, one at a time per loan. Of a return and a Record Tool Lost for the same loan at once,
whichever is decided first is recorded; the other finds the loan ended (a return then gets
`loan-ended`, or succeeds as a repeat if the first was itself a return).

There are no external calls, retries or compensations in this slice.

## Non-Functional Requirements
- **Security / authz:** a borrower may return only their own loans (INV-RET-1); the shed manager
  doesn't record returns. Sign-in, handled outside the model, supplies the acting neighbor's ID; a request whose neighbor ID isn't the signed-in neighbor's is refused `requester-mismatch`.
- **PII & compliance:** Tool Returned holds no name or email; it records which loan ended, when,
  and whether it was late.
- **Performance / SLA:** none beyond the one-at-a-time rule above.

## Dependencies & Read Models Affected
- **Upstream events this slice relies on**, to apply its rules, and the fields it reads from each:
  - Tool Checked Out (Check Out Tool): loanId, toolId, neighborId, dueDate.
  - Loan Extended (Extend Loan): loanId, dueDate.
  - Tool Returned (this slice): loanId.
  - Tool Lost (Record Tool Lost): loanId.
  - These match slices/check-out-tool.md and slices/extend-loan.md. Record Tool Lost is
    specified in a slice not yet written in depth; it is checked against this key when it is.
- **Downstream read models / slices affected:** My Loans, Tool Inventory, Tool Catalog, Holds To
  Fulfill, Overdue Loans To Notify, Undelivered Notices, Neighbors To Flag and Flagged Neighbors.
  Fulfill Hold reacts to a returned tool with holds waiting.

## Open Questions
None.
