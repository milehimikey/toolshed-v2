---
schemaVersion: 1
pattern: state-change
swimlane: Neighbor → Neighbors
status: draft
version: 1
---
# Slice: Register Neighbor

![Diagram](./register-neighbor.svg)

## Intent
Let a neighbor join the tool shed by signing themselves up, so they can borrow tools. Signing up
records the neighbor's name and the email address the shed uses to reach them. A new neighbor
is active and can borrow straight away.

## Trigger & Actor
A person who wants to join, from the Sign Up screen. They sign themselves up; the shed manager
doesn't enroll neighbors. Someone whose earlier account is closed may sign up again here, as a
new account. How a person is recognised once signed up is the open identity question below.

## Command / Input
**Command:** `Register Neighbor`

A NeighborId is a UUID. Text fields have leading and trailing whitespace
(any Unicode whitespace) removed; text that is empty after that counts as absent.

| Field | Type | Required | Rules / Validation |
|-------|------|----------|--------------------|
| neighborId | NeighborId | yes | A UUID chosen by the caller for the new account, so a retried request is recognised. Must not belong to a different account already registered (INV-REG-7). |
| name | Text | yes | Trimmed. Must not then be empty (INV-REG-3). Need not be unique (INV-REG-9). |
| email | Text | yes | Trimmed. Must not then be empty (INV-REG-3). Must not be held by another account under INV-REG-4. Format and comparison rules are open questions below. |

## Trigger
**Triggered by:** screen `Sign Up` @Neighbor

## Event(s) Emitted
**Event:** `Neighbor Registered` → context `Neighbors`
**Read by:** Neighbors and My Account (their own slices, right after this one); and, for the
neighbor's name and email, Tool Inventory (name only), Overdue Loans To Notify, Undelivered
Notices, Pickup Notices To Send, Flagged Neighbors and Suspended Neighbors.

| Field | Type | Immutable Fact? | Source / Notes |
|-------|------|-----------------|----------------|
| neighborId | NeighborId | yes | From the command. Identifies the account everywhere from here on. |
| name | Text | yes | From the command, trimmed. |
| email | Text | yes | From the command, trimmed. The account's email until a later Neighbor Email Changed replaces it. |
| registeredAt | Instant | yes | The UTC moment the event is recorded, set by the system. Shown as the sign-up date. |

The event records a fact that never changes. A later email change is its own event; the name is
never changed.

## Read Model / View
This slice produces no view of its own. Neighbor Registered starts the new account's entry in
two views, each specified in its own doc:
- Neighbors (slices/neighbors.md) lists the new neighbor to the shed manager as active.
- My Account (slices/my-account.md) shows the new neighbor their account.

Both are eventually consistent: the new account appears once each view has caught up with
Neighbor Registered.

## Invariants / Business Rules
A neighbor signs themselves up; the shed manager does not enroll neighbors. Every account starts
active: Neighbor Registered is the only event that starts an account, and suspension and closure
come later, from other slices. The Neighbors view shows a new account as active (INV-NBRS-2), and
Check Out Tool lends to it under its usual rules.

- **INV-REG-3:** Sign-up requires a name and an email address.
  - The shed emails neighbors, and only by email: overdue notices to borrowers and pickup
    notices to holders both go to this address.
  - A name or email that is empty, or only whitespace, is missing.
- **INV-REG-4:** A sign-up is refused if its email belongs to an account that is not closed, or
  to a closed account that was suspended when it was closed.
  - An account's email is its current one: the email it registered with, or the latest Neighbor
    Email Changed for it. An email an account has changed away from belongs to no one.
  - One account per email. A closed account's email can be used again, so a neighbor who left
    can come back with the same address.
  - An account closed while suspended keeps its email for good, so a suspended neighbor can't
    get around the suspension by closing their account and signing up again with the same
    email. A suspended neighbor can't change their email (INV-CEM-3a, Change Email), so that
    email is the one they were suspended under.
  - A sign-up with a different email is accepted, even from someone whose earlier account was
    closed while suspended. Email is the only thing that identifies a neighbor. The shed
    manager sees both accounts on the Neighbors screen and can suspend the new one.
  - The same rule applies when a neighbor changes their email (INV-CEM-2, Change Email).
- **INV-REG-5:** Signing up has no approval or verification step.
  - Neighbor Registered is recorded straight from the request. Nothing waits for the shed
    manager, and the email address isn't confirmed first. The new neighbor can borrow straight
    away, under Check Out Tool's usual rules.
- **INV-REG-6:** A neighbor whose account is closed may sign up again, and doing so creates a
  new account.
  - The new account has its own neighbor ID. The closed account stays closed; it is never
    reopened.
- **INV-REG-7:** Registering with a neighbor ID that is already used adds nothing new.
  - A repeat with the same name and email, compared after trimming, succeeds and records nothing
    new, whatever has happened to the account since. A repeat with different details is refused
    (`neighbor-id-in-use`).
- **INV-REG-8:** Each account gets its own neighbor ID when it registers, and keeps it for good.
  - The neighbor ID is how every later event, view and command refers to the account. Names and
    emails are for people to read and for the shed to send email to.
- **INV-REG-9:** Neighbor names need not be unique.
  - Two neighbors may both be called "Sam Lee". Accounts are told apart by neighbor ID, and only
    the email is checked for uniqueness.

## Scenarios (Given / When / Then)
- **Happy path (INV-REG-3, INV-REG-5, INV-REG-8)**
  - **Given:** no account uses the email address ana@example.org
  - **When:** Ana signs up from the Sign Up screen as neighbor N1, with the name "Ana Ruiz" and
    ana@example.org
  - **Then:** Neighbor Registered is recorded for N1, "Ana Ruiz", ana@example.org, and the time
    it was recorded. Nothing waits for approval or email confirmation.
- **Whitespace is removed (INV-REG-3)**
  - **Given:** no account uses ana@example.org
  - **When:** Ana signs up with the name "  Ana Ruiz " and the email " ana@example.org "
  - **Then:** Neighbor Registered is recorded with "Ana Ruiz" and ana@example.org.
- **No name (INV-REG-3)**
  - **Given:** no account uses ana@example.org
  - **When:** someone tries to sign up with ana@example.org and a name that is empty, or only
    whitespace
  - **Then:** refused with `name-missing`: "Please enter your name." No event is recorded.
- **No email (INV-REG-3)**
  - **Given:** the shed has no neighbors
  - **When:** someone tries to sign up as "Ana Ruiz" with an email that is empty, or only
    whitespace
  - **Then:** refused with `email-missing`: "Please enter your email address." No event is
    recorded.
- **Email used by an active account (INV-REG-4)**
  - **Given:** Ana's active account N1 uses ana@example.org
  - **When:** someone tries to sign up as N2 with ana@example.org
  - **Then:** refused with `email-in-use`: "That email address is already used by an account."
    No event is recorded.
- **Email used by a suspended account (INV-REG-4)**
  - **Given:** Ben's account N2 uses ben@example.org and is suspended
  - **When:** someone tries to sign up as N3 with ben@example.org
  - **Then:** refused with `email-in-use`: "That email address is already used by an account."
    No event is recorded.
- **Signing up again after closing (INV-REG-4, INV-REG-6, INV-REG-8)**
  - **Given:** Ana's account N1, using ana@example.org, was closed while active
  - **When:** Ana signs up as N4 with "Ana Ruiz" and ana@example.org
  - **Then:** Neighbor Registered is recorded for N4, a new account with its own neighbor ID. N1
    keeps its neighbor ID and stays closed.
- **Email of an account closed while suspended (INV-REG-4)**
  - **Given:** Ben's account N2, using ben@example.org, was closed while suspended
  - **When:** someone tries to sign up as N5 with ben@example.org
  - **Then:** refused with `email-in-use`: "That email address is already used by an account."
    No event is recorded.
- **Suspended, reinstated, then closed (INV-REG-4)**
  - **Given:** Ben's account N2, using ben@example.org, was suspended, then reinstated, and then
    closed while active
  - **When:** Ben signs up as N9 with ben@example.org
  - **Then:** Neighbor Registered is recorded for N9. The account wasn't suspended when it was
    closed, so its email is free again.
- **Different email after closing while suspended (INV-REG-4, INV-REG-6)**
  - **Given:** Ben's account N2, using ben@example.org, was closed while suspended
  - **When:** Ben signs up as N5 with ben.new@example.org
  - **Then:** Neighbor Registered is recorded for N5, a new account with its own neighbor ID. N2
    stays closed.
- **Email an account has changed away from (INV-REG-4)**
  - **Given:** Ana's active account N1 registered with ana@oldmail.example and has since changed
    its email to ana@example.org
  - **When:** someone signs up as N6 with ana@oldmail.example
  - **Then:** Neighbor Registered is recorded for N6. No account holds ana@oldmail.example any
    more.
- **Two neighbors with the same name (INV-REG-9)**
  - **Given:** Sam Lee's account N7 uses sam@example.org
  - **When:** another person signs up as N8, also named "Sam Lee", with sam.lee@example.org
  - **Then:** Neighbor Registered is recorded for N8. There are two accounts named "Sam Lee".
- **The same request arrives twice (INV-REG-7)**
  - **Given:** Neighbor Registered was recorded for N1, "Ana Ruiz", ana@example.org
  - **When:** the same Register Neighbor request for N1, "Ana Ruiz", ana@example.org, arrives
    again
  - **Then:** the request succeeds and nothing new is recorded.
- **The same request after the account was closed (INV-REG-7)**
  - **Given:** Neighbor Registered was recorded for N1, "Ana Ruiz", ana@example.org, and N1 has
    since been closed
  - **When:** the same Register Neighbor request for N1 arrives again
  - **Then:** the request succeeds and nothing new is recorded. N1 stays closed.
- **A neighbor ID reused for different details (INV-REG-7)**
  - **Given:** Neighbor Registered was recorded for N1, "Ana Ruiz", ana@example.org
  - **When:** a Register Neighbor request arrives for N1 with "Ben Cole" and ben@example.org
  - **Then:** refused with `neighbor-id-in-use`: "That neighbor ID is already used by another
    account." No event is recorded.

## Alternate & Error Flows
Every refusal records nothing and returns one reason: the stable name, and the sentence shown to
the person on the Sign Up screen. The name is the contract; the sentence is the required wording
of the message.

| Reason | Sentence | Rule |
|--------|----------|------|
| `name-missing` | Please enter your name. | INV-REG-3 |
| `email-missing` | Please enter your email address. | INV-REG-3 |
| `email-in-use` | That email address is already used by an account. | INV-REG-4 |
| `neighbor-id-in-use` | That neighbor ID is already used by another account. | INV-REG-7 |

`email-in-use` is the same for every case in INV-REG-4, so a refused sign-up never reveals
whether the other account is active, suspended, or closed while suspended.

`neighbor-id-in-use` can only come from a faulty caller, not from the screen.

A missing or malformed neighbor ID (not a UUID) is bad input, rejected before any rule is
checked. It is not a refusal and has no reason name.

Idempotency: a repeated request with the same neighbor ID and the same name and email, compared
after trimming, succeeds and records nothing new, even if the account has since been suspended,
closed, or had its email changed. So a retry after a lost response never creates a second
account. The repeat is recognised by its neighbor ID, and is checked before the email rule, so
it is never refused as `email-in-use` by its own earlier registration.

Two sign-ups with the same email at the same moment: the email check and the recording of
Neighbor Registered happen as one step, so at most one is recorded. The other is refused with
`email-in-use`.

There are no retries, compensations or external calls in this slice. No email is sent on sign-up.

## Non-Functional Requirements
- **Security / authz:** anyone may sign up; signing up needs no existing account. How a
  neighbor is recognised afterwards, so that later actions are theirs, is the open identity
  question below.
- **PII & compliance:** Neighbor Registered holds the neighbor's name and email address. It is
  read by the shed manager's screens (Neighbors, Tool Inventory, Undelivered Notices, Flagged
  Neighbors, Suspended Neighbors), by the neighbor's own My Account, and by the two email to-do
  lists. Whether any neighbor-facing screen shows another neighbor's name is the open question
  in Tool Catalog (slices/tool-catalog.md) about showing who has a tool. Whether a closed
  account's details are kept for good is an open question below.
- **Performance / SLA:** none.

## Dependencies & Read Models Affected
- **Upstream events this slice relies on**, to apply INV-REG-4 and INV-REG-7, and the fields it
  reads from each:
  - Neighbor Registered (this slice): neighborId, name, email.
  - Neighbor Email Changed (Change Email): neighborId, email.
  - Neighbor Suspended (Suspend Neighbor): neighborId.
  - Neighbor Reinstated (Reinstate Neighbor): neighborId.
  - Neighbor Account Closed (Close Account): neighborId.
  - Those slices aren't yet written in depth. These names are this slice's assumptions; each of
    those slices is checked against them when it is.
- **Downstream read models / slices affected:** Neighbors and My Account (a new entry); Tool
  Inventory, Overdue Loans To Notify, Undelivered Notices, Pickup Notices To Send, Flagged
  Neighbors and Suspended Neighbors (the neighbor's name and email). Every later neighbor action
  (check out, hold, extend, return, change email, close account) and every shed-manager action on
  a neighbor refers to the account by its neighbor ID.

## Open Questions
- [ ] How does anyone identify themselves to the system, the shed manager and neighbors alike?
  - Signing up creates an account, but nothing in the model says how that person is recognised
    afterwards, so that a checkout or hold is theirs. The same question is open for the shed
    manager in Add Tool.
  - Options: (a) callers say who they are, and nothing checks it; (b) a sign-in outside the
    model supplies the neighbor's ID or the shed-manager role, with no new events; (c) sign-in
    and roles are modeled as events and slices.
  - Recommendation: (b). Neighbors need to be recognised before a loan can be theirs, and (b)
    adds nothing to the model.
- [ ] What makes an email address acceptable at sign-up?
  - Options: (a) only that it is present; (b) a basic shape check (one "@", with text on both
    sides and a dot in the part after it), refused as `email-invalid`: "Please enter a valid
    email address."
  - Recommendation: (b). A mistyped address otherwise only shows up later as an undelivered
    notice.
- [ ] Are two emails that differ only in capital letters the same address?
  - For example, Ana@Example.org and ana@example.org.
  - Recommendation: yes for the one-account-per-email rule: compare without regard to case,
    and keep the address as typed. Otherwise one person can hold two accounts with the same
    mailbox.
- [ ] Are there length limits on a neighbor's name and email?
  - Recommendation: name up to 100 characters, email up to 254. A longer one is refused with
    the limit in the reason, never cut short. This matches the limits proposed for tool names.
- [ ] Does the shed's existing baseline of keeping personal data with the loan and hold
  history, with no erasure, carry over to neighbors' names and emails?
  - A closed account's email is needed to refuse a sign-up with the email of an account closed
    while suspended (INV-REG-4), and the closed account stays on the shed manager's Neighbors
    screen.
  - Recommendation: yes, it carries over: details are kept and never erased. If a neighbor must
    be able to have their details erased, that is a new event and slice.
- [ ] What does a person see when they sign up with an email that can't be used?
  - INV-REG-4 refuses three cases with one reason, `email-in-use`, and the sentence "That email
    address is already used by an account." For an email held by an account closed while
    suspended, that sentence isn't true: the account is closed.
  - Options: (a) one reason and one sentence for all three cases, reworded to be true in each:
    "That email address can't be used to sign up."; (b) a separate reason for the
    closed-while-suspended case, telling the person why.
  - Recommendation: (a). It keeps one reason, never reveals another account's status, and stops
    the sentence misleading.
