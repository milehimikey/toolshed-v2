package com.toolshed.axon

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Clock
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Loads only [SharedPorts], so Axon's auto-configuration never runs and nothing connects to Axon
 * Server here. The real Axon Server path is covered by the Testcontainers round-trip test.
 */
@SpringBootTest(classes = [SharedPorts::class])
class SharedPortsTest {
    @Autowired
    lateinit var zoneId: ZoneId

    @Autowired
    lateinit var clock: Clock

    @Autowired
    lateinit var emailGateway: EmailGateway

    @Test
    fun `the shed zone is America Denver and the clock runs in that zone`() {
        assertEquals(ZoneId.of("America/Denver"), zoneId)
        assertEquals(zoneId, clock.zone)
    }

    @Test
    fun `the logging email stub accepts a hand-off`() {
        assertIs<LoggingEmailGateway>(emailGateway)
        assertEquals(HandOffResult.ACCEPTED, emailGateway.handOff(EmailKey.ForLoan(LoanId(UUID.randomUUID())), "a@example.com", "s", "b"))
    }

    @Test
    fun `the email provider is selected by name and an unknown one is refused`() {
        assertIs<LoggingEmailGateway>(SharedPorts().selectEmailGateway("logging"))
        assertFailsWith<IllegalArgumentException> { SharedPorts().selectEmailGateway("nope") }
    }

    @Test
    fun `the failure report verifier is fail-closed and rejects every report`() {
        assertIs<FailClosedFailureReportVerifier>(SharedPorts().selectFailureReportVerifier("logging"))
        assertIs<FailureReportVerdict.Rejected>(
            FailClosedFailureReportVerifier().verify(FailureReport("{}", "any-signature")),
        )
        assertFailsWith<IllegalArgumentException> { SharedPorts().selectFailureReportVerifier("nope") }
    }
}
