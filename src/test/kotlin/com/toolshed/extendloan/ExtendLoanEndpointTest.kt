package com.toolshed.extendloan

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import io.axoniq.framework.testcontainer.AxonServerContainer
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.messaging.core.unitofwork.UnitOfWorkFactory
import org.axonframework.messaging.eventhandling.EventMessage
import org.axonframework.messaging.eventhandling.conversion.EventConverter
import org.axonframework.messaging.eventhandling.gateway.EventGateway
import org.axonframework.messaging.eventstreaming.StreamableEventSource
import org.axonframework.messaging.eventstreaming.StreamingCondition
import org.axonframework.messaging.eventstreaming.TrackingTokenSource
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Extend Loan over REST, through Spring and a real Axon Server. The shed is seeded through the event
 * gateway. Success is checked by what is recorded. Refusals must come back as 422 ProblemDetails
 * carrying the slice's `invariant` and `reason`.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ExtendLoanEndpointTest {
    companion object {
        @Container
        @JvmStatic
        val axonServer = AxonServerContainer().withDcbContext(true)

        @DynamicPropertySource
        @JvmStatic
        fun axonServerProperties(registry: DynamicPropertyRegistry) {
            registry.add("axon.axonserver.servers") { axonServer.axonServerAddress }
        }
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var eventGateway: EventGateway

    @Autowired
    lateinit var unitOfWorkFactory: UnitOfWorkFactory

    @Autowired
    lateinit var eventSource: StreamableEventSource

    @Autowired
    lateinit var tokenSource: TrackingTokenSource

    @Autowired
    lateinit var eventConverter: EventConverter

    private val ana = UUID.nameUUIDFromBytes("endpoint-ana".toByteArray())
    private val ben = UUID.nameUUIDFromBytes("endpoint-ben".toByteArray())
    private val unknownLoan = UUID.nameUUIDFromBytes("endpoint-no-such-loan".toByteArray())

    /** A drill on loan to Ana, due in three days so the extension is not overdue on the real clock. */
    private data class Loaned(
        val loan: LoanId,
        val borrower: NeighborId,
        val due: LocalDate,
    )

    private fun seedLoan(): Loaned {
        val today = LocalDate.now()
        val due = today.plusDays(3)
        val tool = ToolId(UUID.randomUUID())
        val loaned = Loaned(LoanId(UUID.randomUUID()), NeighborId(UUID.randomUUID()), due)
        val events: List<Any> =
            listOf(
                ToolAdded(tool, "Cordless drill", null, 7, Instant.now()),
                ToolCheckedOut(loaned.loan, tool, loaned.borrower, 7, due, Instant.now()),
            )
        val uow = unitOfWorkFactory.create()
        uow.onInvocation { ctx -> eventGateway.publish(ctx, events) }
        uow.execute().join()
        return loaned
    }

    private fun extend(
        neighbor: UUID,
        loan: UUID,
        body: String,
        signedInAs: UUID = neighbor,
    ) = mockMvc.post("/api/neighbors/$neighbor/loans/$loan/extensions") {
        header("X-Toolshed-Neighbor-Id", signedInAs.toString())
        contentType = MediaType.APPLICATION_JSON
        content = body
        accept = MediaType.APPLICATION_PROBLEM_JSON
    }

    @Test
    fun `INV-EXT-3 POST an extension from the due date answers 201 and records one Loan Extended for the default period`() {
        val loaned = seedLoan()
        val borrower = loaned.borrower.value
        val body = """{"fromDueDate":"${loaned.due}"}"""

        extend(borrower, loaned.loan.value, body).andExpect { status { isEqualTo(201) } }

        val recorded = extendedFor(loaned.loan)
        assertThat(recorded).hasSize(1)
        assertThat(recorded.single().fromDueDate).isEqualTo(loaned.due)
        assertThat(recorded.single().dueDate).isEqualTo(loaned.due.plusDays(7))
    }

    @Test
    fun `INV-EXT-10 POST the same extension again answers 201 and records nothing new`() {
        val loaned = seedLoan()
        val borrower = loaned.borrower.value
        val body = """{"fromDueDate":"${loaned.due}"}"""

        extend(borrower, loaned.loan.value, body).andExpect { status { isEqualTo(201) } }
        extend(borrower, loaned.loan.value, body).andExpect { status { isEqualTo(201) } }

        assertThat(extendedFor(loaned.loan)).hasSize(1)
    }

    @Test
    fun `INV-EXT-10 POST a malformed fromDueDate answers 400 and records nothing`() {
        val loaned = seedLoan()

        extend(loaned.borrower.value, loaned.loan.value, """{"fromDueDate":"not-a-date"}""").andExpect {
            status { isBadRequest() }
        }

        assertThat(extendedFor(loaned.loan)).isEmpty()
    }

    @Test
    fun `INV-EXT-1 POST a request naming Ana from Ben answers 422 requester-mismatch`() {
        extend(ana, unknownLoan, """{"fromDueDate":"2026-10-12"}""", signedInAs = ben).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-EXT-1") }
            jsonPath("$.reason") { value("requester-mismatch") }
            jsonPath("$.detail") { value("You can only extend loans for yourself.") }
        }
    }

    @Test
    fun `INV-EXT-1 POST with no sign-in answers 422 not-signed-in`() {
        mockMvc
            .post("/api/neighbors/$ana/loans/$unknownLoan/extensions") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"fromDueDate":"2026-10-12"}"""
                accept = MediaType.APPLICATION_PROBLEM_JSON
            }.andExpect {
                status { isEqualTo(422) }
                jsonPath("$.invariant") { value("INV-EXT-1") }
                jsonPath("$.reason") { value("not-signed-in") }
            }
    }

    @Test
    fun `INV-EXT-9 POST an extension of no such loan answers 422 loan-not-found`() {
        // Fails over Axon Server until the foundation fix maps a handler's CommandRejected to 422.
        extend(ana, unknownLoan, """{"fromDueDate":"2026-10-12"}""").andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-EXT-9") }
            jsonPath("$.reason") { value("loan-not-found") }
            jsonPath("$.detail") { value("There is no such loan.") }
        }
    }

    /** Reads the store from the start for a few seconds and returns the Loan Extended events for [loan]. */
    private fun extendedFor(loan: LoanId): List<LoanExtended> {
        val found = mutableListOf<LoanExtended>()
        val deadline = Instant.now().plus(Duration.ofSeconds(5))
        unitOfWorkFactory
            .create()
            .executeWithResult { ctx ->
                tokenSource.firstToken(ctx).thenApply { first ->
                    val stream = eventSource.open(StreamingCondition.startingFrom(first), ctx)
                    while (Instant.now().isBefore(deadline)) {
                        stream.next().ifPresent { entry ->
                            val message: EventMessage = entry.message()
                            if (message.type().name() == LoanExtended::class.java.name) {
                                decode(message)?.takeIf { it.loanId == loan }?.let { found.add(it) }
                            }
                        }
                    }
                    stream.close()
                }
            }.join()
        return found
    }

    private fun decode(message: EventMessage): LoanExtended? =
        eventConverter.convertPayload<EventMessage, LoanExtended>(message, LoanExtended::class.java)
}
