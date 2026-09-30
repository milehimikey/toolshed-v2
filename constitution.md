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
- **Branch / commit conventions:** one branch and one PR per slice, `impl/<slice-key>`. Commit
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
   same read model listening to more events. It is implemented once, in the package of the slice
   that introduces it, as one projection handling every event any of its `again` positions reads,
   with the invariants and scenarios of all those docs covered in that package's tests. The one
   PR implements all of them (`em slice mark-implemented` for each). (em finding: MIL-208.)
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

