package com.toolshed

import com.toolshed.axon.EmailGateway
import com.toolshed.axon.EmailKey
import com.toolshed.axon.HandOffResult
import com.toolshed.axon.LoanId
import com.toolshed.axon.LoggingEmailGateway
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import java.time.Clock
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

@SpringBootTest
@TestPropertySource(properties = ["axon.axonserver.enabled=false"])
class ToolshedApplicationSmokeTest {
    @Autowired
    lateinit var zoneId: ZoneId

    @Autowired
    lateinit var clock: Clock

    @Autowired
    lateinit var emailGateway: EmailGateway

    @Test
    fun `context starts with the shed zone and a clock in that zone`() {
        assertEquals(ZoneId.of("America/Denver"), zoneId)
        assertEquals(zoneId, clock.zone)
    }

    @Test
    fun `context wires the logging email stub`() {
        assertIs<LoggingEmailGateway>(emailGateway)
        assertEquals(HandOffResult.ACCEPTED, emailGateway.handOff(EmailKey.ForLoan(LoanId(UUID.randomUUID())), "a@example.com", "s", "b"))
    }
}
