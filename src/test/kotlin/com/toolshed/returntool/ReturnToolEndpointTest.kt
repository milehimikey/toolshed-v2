package com.toolshed.returntool

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.testsupport.SharedAxonServer
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
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.put
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Return Tool over REST, through Spring and a real Axon Server. The loan is seeded through the event
 * gateway. Success is checked by what is recorded. Refusals must come back as 422 ProblemDetails
 * carrying the slice's `invariant` and `reason`.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReturnToolEndpointTest {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun axonServerProperties(registry: DynamicPropertyRegistry) {
            registry.add("axon.axonserver.servers") { SharedAxonServer.address }
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
    private val unknownLoan = UUID.nameUUIDFromBytes("endpoint-no-such-loan".toByteArray())

    /** A drill on loan to Ana, due in three days so the return is on time on the real clock. */
    private data class Loaned(
        val loan: LoanId,
        val borrower: NeighborId,
    )

    private fun seedLoan(): Loaned {
        val tool = ToolId(UUID.randomUUID())
        val loaned = Loaned(LoanId(UUID.randomUUID()), NeighborId(UUID.randomUUID()))
        val due = LocalDate.now().plusDays(3)
        val events: List<Any> =
            listOf(
                ToolAdded(tool, "Cordless drill", null, 7, Instant.now()),
                NeighborRegistered(loaned.borrower, "Ana Ruiz", "ana-${loaned.borrower.value}@example.org", Instant.now()),
                ToolCheckedOut(loaned.loan, tool, loaned.borrower, 7, due, Instant.now()),
            )
        val uow = unitOfWorkFactory.create()
        uow.onInvocation { ctx -> eventGateway.publish(ctx, events) }
        uow.execute().join()
        return loaned
    }

    private fun returnTool(
        neighbor: UUID,
        loan: UUID,
        signedInAs: UUID = neighbor,
    ) = mockMvc.put("/api/neighbors/$neighbor/loans/$loan/return") {
        header("X-Toolshed-Neighbor-Id", signedInAs.toString())
        accept = org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON
    }

    @Test
    fun `INV-RET-1 INV-RET-3 POST a return answers 204 and records one Tool Returned, on time`() {
        val loaned = seedLoan()

        returnTool(loaned.borrower.value, loaned.loan.value).andExpect { status { isNoContent() } }

        val recorded = returnedFor(loaned.loan)
        assertThat(recorded).hasSize(1)
        assertThat(recorded.single().late).isFalse()
    }

    @Test
    fun `INV-RET-10 POST the same return again answers 204 and records nothing new`() {
        val loaned = seedLoan()

        returnTool(loaned.borrower.value, loaned.loan.value).andExpect { status { isNoContent() } }
        returnTool(loaned.borrower.value, loaned.loan.value).andExpect { status { isNoContent() } }

        assertThat(returnedFor(loaned.loan)).hasSize(1)
    }

    @Test
    fun `INV-RET-1 POST a return for Ana while signed in as Ben answers 422 requester-mismatch`() {
        val loaned = seedLoan()
        val ben = UUID.randomUUID()

        returnTool(loaned.borrower.value, loaned.loan.value, signedInAs = ben).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-RET-1") }
            jsonPath("$.reason") { value("requester-mismatch") }
            jsonPath("$.detail") { value("You can only return loans for yourself.") }
        }
    }

    @Test
    fun `INV-RET-11 POST a return of no such loan answers 422 loan-not-found`() {
        // Fails over Axon Server until the foundation fix maps a handler's CommandRejected to 422.
        returnTool(ana, unknownLoan).andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-RET-11") }
            jsonPath("$.reason") { value("loan-not-found") }
            jsonPath("$.detail") { value("There is no such loan.") }
        }
    }

    /** Reads the store from the start for a few seconds and returns the Tool Returned events for [loan]. */
    private fun returnedFor(loan: LoanId): List<ToolReturned> {
        val found = mutableListOf<ToolReturned>()
        val deadline = Instant.now().plus(Duration.ofSeconds(5))
        unitOfWorkFactory
            .create()
            .executeWithResult { ctx ->
                tokenSource.firstToken(ctx).thenApply { first ->
                    val stream = eventSource.open(StreamingCondition.startingFrom(first), ctx)
                    while (Instant.now().isBefore(deadline)) {
                        stream.next().ifPresent { entry ->
                            val message: EventMessage = entry.message()
                            if (message.type().name() == ToolReturned::class.java.name) {
                                decode(message)?.takeIf { it.loanId == loan }?.let { found.add(it) }
                            }
                        }
                    }
                    stream.close()
                }
            }.join()
        return found
    }

    private fun decode(message: EventMessage): ToolReturned? =
        eventConverter.convertPayload<EventMessage, ToolReturned>(message, ToolReturned::class.java)
}
