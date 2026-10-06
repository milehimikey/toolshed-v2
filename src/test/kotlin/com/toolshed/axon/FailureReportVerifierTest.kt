package com.toolshed.axon

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The fail-closed verifier: no report is verified, signed or not, until a provider is chosen. */
class FailureReportVerifierTest {
    private val verifier = FailClosedFailureReportVerifier()

    @Test
    fun `a signed report is rejected while no provider is selected`() {
        val verdict = verifier.verify(FailureReport("""{"reference":"loan","reason":"bounced"}""", "signed"))
        assertEquals(FailureReportVerdict.Rejected("no-provider-selected"), verdict)
    }

    @Test
    fun `an unsigned report is rejected while no provider is selected`() {
        assertIs<FailureReportVerdict.Rejected>(verifier.verify(FailureReport("""{"reference":"loan"}""", null)))
    }
}
