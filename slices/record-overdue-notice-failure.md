---
schemaVersion: 1
pattern: translation
swimlane: System → Loans
status: draft
implementedIn: https://github.com/milehimikey/toolshed-v2/pull/11
shippedVersion: 1
conformedVersion: 1
conformedAt: 54b94c75b4528330dea08fa62ca7fe63b7f9cc09
conformedOn: 2026-11-01
version: 2
---
# Slice: Record Overdue Notice Failure

![Diagram](./record-overdue-notice-failure.svg)

## Intent
Record that an overdue notice could not be delivered to the borrower, so the shed manager knows
the neighbor wasn't told and can follow up in person, and the borrower's My Loans shows that the
notice didn't reach them.

## Trigger & Actor
Externally triggered. The email provider, outside the shed, sends a final failure report for an
overdue notice the shed handed it. The Email Provider Failure Report translation turns that
report into the Record Overdue Notice Failure command. No person records a failure, and there is
no screen.

What the report carries: the reference the shed gave the notice when it handed it over, which is
the loan's ID, since each loan gets at most one overdue notice; and the reason, refused or
bounced. Anything else in the report, such as the email address, is not recorded.

## Command / Input
**Command:** `Record Overdue Notice Failure`

A LoanId is a UUID.

| Field | Type | Required | Rules / Validation |
|-------|------|----------|--------------------|
| loanId | LoanId | yes | The reference from the provider's report: the loan the notice was about. Must match an Overdue Notice Sent (INV-ONF-7), for a loan that hasn't ended (INV-ONF-8). |
| reason | Text | yes | `refused` or `bounced`, from the report (INV-ONF-3). Any other value is bad input. |

## Trigger
**Triggered by:** translation `Email Provider Failure Report`, also in this slice (externally
triggered by the email provider's final failure report).

## Event(s) Emitted
**Event:** `Overdue Notice Failed` → context `Loans`
**Read by:** My Loans (the loan shows the notice couldn't be delivered) and Undelivered Notices
(the shed manager's follow-up list).

| Field | Type | Immutable Fact? | Source / Notes |
|-------|------|-----------------|----------------|
| loanId | LoanId | yes | From the command. |
| neighborId | NeighborId | yes | Set by the system: the borrower on the loan's Overdue Notice Sent. |
| neighborName | Text | yes | Set by the system: the name the notice was addressed to (Overdue Notice Sent.borrowerName). |
| neighborEmail | Text | yes | Set by the system: the address the notice went to (Overdue Notice Sent.sentTo). Readers that show a current address keep it current themselves. |
| toolName | Text | yes | Set by the system: the tool the notice named (Overdue Notice Sent.toolName). |
| dueDate | Date | yes | Set by the system: the due date the notice gave (Overdue Notice Sent.dueDate). |
| reason | Text | yes | From the command: `refused` or `bounced`. |
| failedAt | Instant | yes | The UTC moment the event is recorded, set by the system. Shown as the failure date: its calendar date in the shed's local time zone. |

The event never changes. No later event retries the notice or clears the failure.

## Read Model / View
This slice produces no view of its own. Overdue Notice Failed feeds two views, each specified in
its own doc:
- My Loans (slices/my-loans.md) shows the borrower that the overdue notice couldn't be
  delivered.
- Undelivered Notices (slices/undelivered-notices.md) lists the failure for the shed manager to
  follow up.

Both are eventually consistent. Overdue Loans To Notify doesn't hear this event: the loan left
that list when Overdue Notice Sent was recorded.

## Invariants / Business Rules
Every recorded failure is shown to the shed manager on Undelivered Notices, and to the borrower
on My Loans; those views own how it is shown.

A failure is recorded only from the email provider's report; no person records it, and the only
way in is the translation in Trigger & Actor. The shed records only the provider's one final
failure report: the provider makes 3 delivery attempts over about an hour and reports a final
failure only if all of them fail. The shed does no retrying of its own, and a failure a later
attempt recovers from is never reported. The provider signs each final report, under the shed's house email-port contract; the translation checks the signature and drops an unsigned or wrongly signed report without issuing any command. The provider's signature, not a sign-in, is what guards this command.

- **INV-ONF-3:** Overdue Notice Failed is recorded for the loan the notice was about, with the
  moment of the failure and whether the email was refused or bounced.
  - Refused and bounced are the two reasons the provider's final report can give.
- **INV-ONF-4:** Every Overdue Notice Failed follows an Overdue Notice Sent for the same loan.
  - Overdue Notice Sent is recorded at hand-off, so the loan has already left Overdue Loans To
    Notify. Recording the failure doesn't change that list.
- **INV-ONF-6:** A loan has at most one Overdue Notice Failed. No further notice is attempted
  for it after a failure.
  - The shed manager follows up in person.
- **INV-ONF-7:** A failure report that matches no overdue notice the shed sent is ignored, and
  nothing is recorded.
- **INV-ONF-8:** A failure report that arrives after the loan has ended, because the tool was
  returned or recorded lost, is ignored, and nothing is recorded.
  - For example, the borrower returned the tool during the hour the provider spent retrying.
- **INV-ONF-9:** A failure report repeated for a loan whose failure is already recorded, with the
  same reason, succeeds and records nothing new. With a different reason it is ignored, and
  nothing is recorded.

## Scenarios (Given / When / Then)
- **Refused (INV-ONF-3)**
  - **Given:** Overdue Notice Sent was recorded for Ana's drill loan L1, and the drill is still
    out
  - **When:** the provider's final report says the notice for L1 was refused
  - **Then:** Overdue Notice Failed is recorded for L1, reason `refused`, with the time it was
    recorded. No further notice is sent for L1.
- **Bounced (INV-ONF-3)**
  - **Given:** Overdue Notice Sent was recorded for L1, and the drill is still out
  - **When:** the provider's final report says the notice for L1 bounced
  - **Then:** Overdue Notice Failed is recorded for L1, reason `bounced`.
- **Report matching no notice (INV-ONF-4, INV-ONF-7)**
  - **Given:** no Overdue Notice Sent has been recorded for loan L2
  - **When:** a failure report names L2
  - **Then:** ignored with `no-matching-notice`. Nothing is recorded.
- **Report arrives after the tool was returned (INV-ONF-8)**
  - **Given:** Overdue Notice Sent was recorded for L1, and Tool Returned was then recorded for L1
    while the provider was still retrying
  - **When:** the provider's final report says the notice for L1 bounced
  - **Then:** ignored with `loan-ended`. Nothing is recorded.
- **Report arrives after the tool was recorded lost (INV-ONF-8)**
  - **Given:** Overdue Notice Sent was recorded for L1, and Tool Lost was then recorded for L1
  - **When:** the provider's final report says the notice for L1 was refused
  - **Then:** ignored with `loan-ended`. Nothing is recorded.
- **The same report arrives twice (INV-ONF-6, INV-ONF-9)**
  - **Given:** Overdue Notice Failed was recorded for L1, reason `bounced`
  - **When:** the same report, L1 bounced, arrives again
  - **Then:** it succeeds and nothing new is recorded. L1 still has one Overdue Notice Failed.
- **A second report with a different reason (INV-ONF-6, INV-ONF-9)**
  - **Given:** Overdue Notice Failed was recorded for L1, reason `bounced`
  - **When:** a report arrives saying the notice for L1 was refused
  - **Then:** ignored with `failure-already-recorded`. Nothing is recorded; L1 keeps its one
    failure, reason `bounced`.

## Alternate & Error Flows
Every refusal records nothing and returns one reason: a stable name and a sentence. No person
sees these; they go back to the translation, which takes no further action, and are kept for
diagnosing the provider link.

The checks run in this order, and the first that fails is the reason returned:
1. `no-matching-notice` (INV-ONF-7).
2. A failure already recorded for the loan (INV-ONF-9): a repeat with the same reason succeeds
   with nothing recorded; otherwise `failure-already-recorded`.
3. `loan-ended` (INV-ONF-8).

| Reason | Sentence | Rule |
|--------|----------|------|
| `no-matching-notice` | No overdue notice was sent for that loan. | INV-ONF-7 |
| `failure-already-recorded` | A failure is already recorded for that notice. | INV-ONF-9 |
| `loan-ended` | That loan has already ended. | INV-ONF-8 |

A missing or malformed loan ID, or a reason other than `refused` or `bounced`, is bad input,
rejected before any rule is checked. It is not a refusal and has no reason name.

Idempotency: the provider may deliver the same final report more than once. A repeat with the same
loan and reason succeeds and records nothing new, even if the loan has since ended. So one
failure never shows twice on Undelivered Notices.

Retries and compensations: none in the shed. The provider's own 3 attempts happen before its
final report. After a failure no further notice is attempted; following up is the shed manager's
job, in person.

## Non-Functional Requirements
- **Security / authz:** only the email provider's failure report can trigger this command,
  through the translation. The provider signs each final report, under the shed's house email-port contract; the translation checks the signature and drops an unsigned or wrongly signed report without issuing any command. The provider's signature, not a sign-in, is what guards this command.
- **PII & compliance:** Overdue Notice Failed carries the name and address the notice was sent to, the tool and
  the date it named, so Undelivered Notices can show the shed manager whom to reach without looking
  anything up. Nothing else from the provider's report is kept. Names and emails are kept with the
  shed's loan and hold history and never erased.
- **Performance / SLA:** none. A failure shows on Undelivered Notices shortly after the report
  arrives, about an hour after the notice was handed over.

## Dependencies & Read Models Affected
- **Upstream events this slice relies on**, to apply its rules, and the fields it reads from
  each:
  - Overdue Notice Sent (Send Overdue Notice): loanId.
  - Tool Returned (Return Tool): loanId.
  - Tool Lost (Record Tool Lost): loanId.
  - Overdue Notice Failed (this slice): loanId, reason.
  - Overdue Notice Sent, and the loan's ID as the reference the provider quotes back, match
    slices/send-overdue-notice.md. Return Tool and Record Tool Lost are specified in slices not
    yet written in depth; each is checked against these keys when it is.
- **Downstream read models / slices affected:** My Loans and Undelivered Notices (the failure).

## Open Questions
None.
