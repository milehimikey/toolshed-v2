---
schemaVersion: 1
ratifiedBy: Mike Key
ratifiedOn: 2026-09-06
---
# toolshed — implementation constitution

The house rules for how implementation happens in this project. They bound every "purely
technical choice" an implementing agent would otherwise make alone. Answers elicited from
Mike Key in the implement-phase conversation of 2026-09-06; the shape follows the Meridian
Goods reference implementation (`~/projects/meridian-goods`) deliberately, so the pilot's
findings compare like with like.

## Stack and architectural shape

> **What do we build on, what shape does the code take, and which implementation skill or
> approach does each slice pattern route to?**

- **Languages / runtimes:** Kotlin 2.x on JVM 21.
- **Frameworks / platform:** Spring Boot; Axon Framework 5 (`axon-framework-bom`,
  `axon-spring-boot-starter`, `axon-kotlin`) with Axon Server as the event store; Gradle with the
  Kotlin DSL; a JDBC/JPA read-model store only where a State View slice needs one.
- **Architectural pattern:** event-sourced CQRS with vertical slices. Command models are Axon 5
  event-sourced entities with Dynamic Consistency Boundaries (tags + `EventCriteria`), written in
  the decider idiom: a pure `decide` function, a separate command-handler class, an
  `EventAppender`. Read models are plain projection classes with `@EventHandler` methods.
  Automations are stateless (or event-sourced `State`) event handlers that dispatch commands.
- **Where a slice's code lives:** one package per slice, `com.toolshed.<slicekey>` (kebab-case
  slice key with the hyphens dropped, e.g. `com.toolshed.listtool`), holding that slice's
  commands, events, entity/handler or projection, and its `configure` function. Events shared
  across slices live in the package of the slice that emits them; other slices import them from
  there. Cross-cutting Axon wiring lives in `com.toolshed.axon`.

Routing — which implementation skill or approach each of the four slice patterns uses:

| Pattern | Skill / approach |
|---|---|
| State Change | the `axon-*` skill set — `axon-slice-implementation` to sequence, then `axon-dcb-modeling`, `axon-entity-patterns`, `axon-entity-testing` |
| State View | the `axon-*` skill set — `axon-slice-implementation`, then `axon-projection-patterns`, `axon-projection-testing` |
| Automation | the `axon-*` skill set — `axon-slice-implementation`, then `axon-policy-patterns`, `axon-policy-testing` (plus the projection skills for the to-do-list view the automation's doc `covers:`) |
| Translation | the `axon-*` skill set — `axon-slice-implementation`, then `axon-translation-patterns`, tested per `axon-policy-testing` |

An agent implementing a slice reads the doc's `pattern:` frontmatter and follows that row — this
table is the only routing rule. Project bootstrap (first slice only) follows `axon-project-setup`
and `axon-testing-setup`; configuration follows `axon-configuration`.

## Code style

> **What does "idiomatic" mean in this codebase — formatting, naming, module layout?**

- **Formatter / linter:** ktlint via the Gradle plugin, Kotlin official style.
  `./gradlew ktlintCheck` must pass; `./gradlew ktlintFormat` applies it. Never hand-format
  against it.
- **Naming:** commands and events are Kotlin data classes named exactly as the model's elements
  in PascalCase (`ListTool`, `ToolListed`); field names match the slice doc's field tables
  verbatim (`ownerPhone`, `maxLoanDays`). Command handlers are `<Command>CommandHandler`,
  entities `<Context>` or `<Concept>State`, projections `<ViewName>Projection`, automations
  `<ProcessorName>Handler`. Test classes are `<Slice>AxonFixtureTest`, `<Slice>DeciderTest`,
  `<View>ProjectionTest`, `<View>ProjectionFixtureTest`.
- **Module layout:** one package per slice (see above). A slice package never imports another
  slice's handler, entity, or projection — only its events and, for a State Change slice's
  cross-boundary invariants, the read-model query types it declares. Shared infrastructure
  (`com.toolshed.axon`) has no dependency on any slice.
- **Idiomatic here means:** the decider is pure (no framework types inside `decide`); invariants
  are checked in `decide` and surfaced as named domain exceptions carrying the slice doc's
  rejection reason; nullable event fields stay nullable (`Int?`, not a sentinel); no `!!`.

## Testing norms

> **What must be tested for each slice pattern, where do those tests live, and how are they named
> so `em coverage` can trace invariant IDs?**

- **Levels required per pattern:** State Change — decider unit tests (pure, no fixture) for each
  invariant boundary, plus an `AxonTestFixture` given/when/then test per scenario built from the
  slice's own `configure` function. State View — projection unit tests (handlers applied in
  sequence, then query) plus a fixture test through the query bus. Automation / Translation —
  reaction tests through the fixture including at least one negative case (the event that must
  *not* trigger the command) and an idempotency case.
- **Invariant tests:** `src/test/kotlin/com/toolshed/<slicekey>/` — every `INV-<MNEMONIC>-n` in a
  slice doc needs at least one test **citing that exact ID** in its name or a section comment, so
  `em coverage toolshed.em --tests src/test/kotlin` can trace it.
- **Scenario tests:** same package — one per `## Scenarios (Given / When / Then)` entry, with
  rejection scenarios asserting the doc's named rejection reason.
- **Test naming convention:** backtick-quoted JUnit 5 method names carrying the ID and the
  scenario, e.g. `` fun `INV-LT-5 - given Cordless drill listed by owner, when List Tool cordless DRILL, then rejected already listed`() ``;
  a section comment `// --- INV-LT-3 ---` is acceptable when several tests share an ID.
- **What "green" means:** `./gradlew ktlintCheck test` locally and in CI, plus
  `em coverage toolshed.em --tests src/test/kotlin --strict` reporting zero uncovered invariants
  for implemented slices.

## NFR baselines

> **What standing non-functional defaults does every slice inherit — authz, PII, performance,
> observability?**

- **Authorization model:** none at the command layer. Neighbors self-identify by phone number
  (the model's owner and borrower key, decided 2026-09-05); the only enforcement is the
  invariants each slice names (owner ≠ borrower, only the loan's borrower or the tool's owner
  records a return, and so on). No accounts, no texted-code verification.
- **PII / data handling:** `ownerName`, `ownerPhone`, `borrowerName`, `borrowerPhone`, and
  `location` are personal data. They are never written to logs at INFO or above (log IDs, not
  people), and they are retained with the loan/hold history after delisting. No erasure flow.
- **Performance defaults:** no latency or throughput budgets. Read models are eventually
  consistent; a change should appear within seconds.
- **Observability:** structured logging via kotlin-logging (`kotlin-logging-jvm`), one line per
  command handled and per event applied, keyed by IDs. No metrics or tracing yet.

A slice doc's own `## Non-Functional Requirements` section overrides these for that slice; where
it is silent, these apply.

## Review and merge norms

> **Who reviews an implementation PR, what blocks a merge, and how does the slice doc's status
> flip fit in?**

- **Reviewers:** Mike Key, the pilot's ratifier, reviews and merges every implementation PR
  (the CODEOWNERS convention recorded in `pilot/NOTES.md`; the implementing-agent operator never
  merges their own work).
- **What blocks a merge:** a failing `./gradlew ktlintCheck test`; an invariant of the slice
  under implementation left uncovered by `em coverage --strict`; any gap the implementer had to
  guess at rather than surface — that goes back to the model as an open question or a ratified
  delta before the code merges.
- **Branch / commit conventions:** one branch and one PR per slice doc, `impl/<slice-key>`. Commit
  messages name the slice. The PR description cites the slice doc and lists each invariant with
  the test that covers it.
- **At merge:** run `em slice mark-implemented toolshed.em <slice-key> <pr-url>` — the one edit an
  implementing agent makes to a ratified slice doc, run by the implementing agent after the
  ratifier merges, in a follow-up PR together with `em slice index toolshed.em`. Never bump
  `version:`; that moves only when a delta is ratified.

## Amendments

Ratified in conversation by Mike Key on 2026-09-07 at the start of the batch implementation of
slices 2–40 (recorded in `.event-modeling.md`); they refine the sections above and win where
they differ.

1. **Events first.** Every event in the model exists as a data class in its emitting slice's
   package before any further slice is implemented, so a slice never creates, edits, or reaches
   into another slice's code. A slice imports events (and only events) from other packages.
2. **One read slice per read model.** A view that the model shows `again` in later slices is the
   same read model listening to more events. Each later position is a continuation of the slice
   that first declares the view (em 1.12.0+, MIL-208): it has no doc, status, ratification,
   branch, or PR of its own. The read model is implemented once, in the originating slice's
   package, as one projection handling every event `em export toolshed.em --slice
   <originating-key>` lists for it, including `alsoReads`. The originating doc's invariants and
   scenarios (one scenario per event a continuation feeds) are covered in that package's tests;
   a continuation event with no scenario there is a gap to surface, not to fill. One PR, and
   `em slice mark-implemented` runs once, for the originating key only. (Revised 2026-10-04 by
   Mike Key for em finding MIL-208.)
3. **Commands and views are the API.** Every State Change slice exposes its command over REST and
   every State View slice exposes its query; the model's UI boxes are the consumers. Endpoints are
   resource-style, designed per slice and listed in the PR for ratification; invariant rejections
   are `422` ProblemDetails via `com.toolshed.web.CommandRejectedAdvice`, with `invariant` and
   `reason` members. Each slice's rejection exception extends `com.toolshed.axon.CommandRejected`.
4. **Shared ports** live in `com.toolshed.axon`: `SmsGateway` (accepted/refused; a logging stub
   until a provider is chosen) and the neighborhood `ZoneId`/`Clock` beans from
   `toolshed.timezone` (`America/Denver`). Slices inject them; they never configure their own.
5. **Read-model storage** is in-memory (a repository class in the slice package) until a slice's
   NFRs require otherwise; no JPA/JDBC dependency is added by a slice PR.


Ratified by Mike Key on 2026-10-05 in decision brief 10 (A1 and A5), during the deep pass of the
toolshed v2 slice docs (recorded in `.event-modeling.md`). They win over the sections above,
and over Amendments 1–5, where they differ.

6. **Identity and authorization.** Sign-in happens outside the model. It supplies each request
   with the acting neighbor's `NeighborId`, or with the shed-manager role. It always resolves a
   person to their newest account, and to a closed account only when there's no newer one.
   Every neighbor command carries the `neighborId` it acts for. A request whose signed-in
   neighbor isn't that neighbor is refused `requester-mismatch`. A shed-manager command from a
   caller without the role is refused `not-shed-manager`. Both checks come before any other.
   Neighbors only ever see their own account, loans and holds. Views are guarded the same way. A
   shed-manager view refuses other callers `not-shed-manager`, and a neighbor's own views refuse
   another neighbor's data `requester-mismatch`. Any caller who isn't signed in is refused
   `not-signed-in` everywhere except Sign Up. The system's own automations aren't signed in;
   they are internal and aren't on the API. This replaces the "self-identify,
   no accounts" authorization baseline above. The PII baseline still applies to v2's personal
   data (neighbor name and email): it is kept with the loan and hold history, with no erasure.
7. **Shape conventions.** Every slice follows these:
   - Ids (`ToolId`, `NeighborId`, `LoanId`, `HoldId`) are UUIDs chosen by the caller of the
     command that creates the thing. A missing or malformed id is bad input, not an invariant
     refusal.
   - A command that creates a thing is safe to repeat. The same id with the same details
     (compared after trimming) succeeds and records nothing, and the same id with different
     details is refused. A command that changes something is also safe to repeat: when its
     effect has already happened, it succeeds and records nothing. State-setting commands carry
     no "from" value. Adding commands (Extend Loan) carry one.
   - Event times are UTC `Instant`s that the handler sets. Every day rule uses calendar dates in
     the shed's zone, from the `ZoneId`/`Clock` beans. Values computed once by a handler
     (`dueDate`, `collectByDate`, `late`, `whileSuspended`) are carried on the event, and no
     view recomputes them.
   - Text is trimmed of any Unicode whitespace, and text that's empty after trimming is absent.
   - A refusal records nothing and returns one reason: a stable kebab-case name plus the slice
     doc's sentence. A name means the same condition in every slice.
   - A lapsed or derived condition is worked out from current facts. State changes are never
     replayed as "when X happens" steps.
   - The two time-driven automations, the overdue-notice gateway and the pickup expirer, read
     their lists every 5 minutes at any hour. Every other reaction acts when its list changes.
8. **Email port.** `EmailGateway` in `com.toolshed.axon` replaces `SmsGateway` (Amendment 4). Its
   contract:
   - A notice is handed over with a key: the loan or hold id.
   - The provider sends at most one email per key.
   - It retries delivery itself and sends exactly one final failure report, quoting the key.
   - Failure reports are signed, and the shed checks the signature before acting on one.

   Outgoing notices are recorded first (Overdue Notice Sent, Pickup Notice Sent), carrying
   what was sent. They are then handed over from an outbox driven by those events, and the
   hand-off resumes after a restart until the provider accepts it. A logging stub stands in
   until a provider is chosen.

Ratified by Mike Key on 2026-10-06 (recorded in `.event-modeling.md`).

9. **Identity mechanism and code style.** Amendment 6's sign-in is a layer in front of the app.
   It passes the caller's identity (the neighbor's `NeighborId`, or the shed-manager role) in
   trusted request headers. The app does no authentication of its own; it reads those headers.
   The app must be reachable only through that layer.
   - The headers are `X-Toolshed-Neighbor-Id` (the acting neighbor's `NeighborId`) and
     `X-Toolshed-Role` (exactly `shed-manager` for the role).
   - A malformed neighbor id means not signed in, and any role header on that request is
     dropped too.

   The formatter's "Kotlin official style" (Code style) means ktlint's `ktlint_official` code
   style.

Approved by Mike Key on 2026-10-06 ("yes to amendment 10", relayed by deliver.lead; recorded in
`.event-modeling.md`).

10. **API layout and code shape.** These refine Amendments 3 and 7.
    - **API root and paths.** Every endpoint lives under `/api`.
      - What a neighbor acts on or owns sits under `/api/neighbors/{neighborId}/...`.
      - Tools sit under `/api/tools/{toolId}`.
      - The shed manager's actions on a neighbor also sit under
        `/api/neighbors/{neighborId}/...`.
    - **Verbs.**
      - `PUT` is for a command that creates or sets state at a known URI: a caller-chosen id,
        or a singleton sub-resource. For example, check out is
        `PUT /api/neighbors/{n}/loans/{l}`, suspend is `PUT .../suspension`, and return is
        `PUT .../loans/{l}/return`.
      - `DELETE` is for undoing a singleton state or removing a resource. For example,
        reinstate is `DELETE .../suspension`, and cancel hold is
        `DELETE /api/neighbors/{n}/holds/{h}`.
      - `POST` is only for Amendment 7's "adding" commands, which have no caller-chosen id. For
        example, extend is `POST .../loans/{l}/extensions`.
    - **Decider shape.** Each State Change slice has three parts:
      - a separate `<Slice>Decider` object holding the pure decide function;
      - a top-level `<Concept>State` entity;
      - a `<Command>CommandHandler` that calls the foundation's
        `context.decideAndAppend { ... }`.

      Slices never reference `EventAppender`. `AppendDisciplineTest` enforces this.
    - **Dispatch.** REST controllers send commands through `com.toolshed.web.CommandDispatcher`.
      It retries a server-side failure up to 3 times, which is safe because Amendment 7 makes
      commands safe to repeat. It never retries a refusal or a timeout.
    - **Refusal exceptions** are named `<Command>Rejected` and extend
      `com.toolshed.axon.CommandRejected`.
    - **Success status.** On the first call and on a repeat alike:
      - a command that creates a new resource with its own id (a tool, neighbor, loan, hold or
        extension) answers `201 Created`;
      - a command that sets, changes or removes state on an existing resource (suspension,
        closure, retirement, return, email, cancel, reinstate) answers `204 No Content`.

      (Added 2026-10-06 with Mike Key's approval.)
