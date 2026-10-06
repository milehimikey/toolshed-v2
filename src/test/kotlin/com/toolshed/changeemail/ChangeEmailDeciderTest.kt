package com.toolshed.changeemail

import com.toolshed.axon.NeighborId
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.suspendneighbor.NeighborSuspended
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** The pure decision, with no Axon fixture: each boundary, and the order the first failure is reported in. */
class ChangeEmailDeciderTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val handler = ChangeEmailCommandHandler(Clock.fixed(now, ZoneOffset.UTC))
    private val ana = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000a1"))
    private val ben = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000b1"))

    private fun stateWith(vararg events: Any): ChangeEmailCommandHandler.State {
        val state = ChangeEmailCommandHandler.State()
        events.forEach { event ->
            when (event) {
                is NeighborRegistered -> state.evolve(event)
                is NeighborEmailChanged -> state.evolve(event)
                is NeighborSuspended -> state.evolve(event)
                is NeighborReinstated -> state.evolve(event)
                is NeighborAccountClosed -> state.evolve(event)
                else -> error("unexpected event ${event::class.simpleName}")
            }
        }
        return state
    }

    private fun shed(vararg more: Any): Array<Any> =
        arrayOf(
            NeighborRegistered(ana, "Ana Ruiz", "ana@oldmail.example", now),
            NeighborRegistered(ben, "Ben Cole", "ben@example.org", now),
            *more,
        )

    private fun decide(
        state: ChangeEmailCommandHandler.State,
        email: String? = "ana@example.org",
        by: NeighborId = ana,
    ) = handler.decide(ChangeEmail(by, email), state, now)

    private fun refusedWith(
        reason: String,
        block: () -> Unit,
    ) {
        assertThat(assertThrows<ChangeEmailRejected>(block).reason).isEqualTo(reason)
    }

    // --- INV-CEM-12: a 254-character address is accepted, a 255 is refused ---

    @Test
    fun `INV-CEM-12 an address of exactly 254 characters is accepted`() {
        val email = "a".repeat(242) + "@example.org"
        assertThat(email).hasSize(254)
        assertThat(decide(stateWith(*shed()), email = email)).hasSize(1)
    }

    @Test
    fun `INV-CEM-12 an address with nothing after the at sign is refused email-invalid`() {
        refusedWith("email-invalid") { decide(stateWith(*shed()), email = "ana@") }
    }

    // --- INV-CEM-2: an account's own email never counts as held by another ---

    @Test
    fun `INV-CEM-2 a neighbor may correct the capitals of their own email`() {
        val state = stateWith(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now))
        assertThat(decide(state, email = "Ana@Example.ORG")).hasSize(1)
    }

    @Test
    fun `INV-CEM-2 an email held by another account in any case is refused`() {
        refusedWith("email-in-use") { decide(stateWith(*shed()), email = "BEN@example.ORG") }
    }

    // --- INV-CEM-5: the old email is released on change ---

    @Test
    fun `INV-CEM-5 the old email is free once the account has changed away from it`() {
        val state = stateWith(*shed(NeighborEmailChanged(ana, "ana@example.org", now)))
        assertThat(handler.decide(ChangeEmail(ben, "ana@oldmail.example"), state, now)).hasSize(1)
    }

    // --- INV-CEM-9: the same email as now succeeds with nothing recorded, compared exactly as typed ---

    @Test
    fun `INV-CEM-9 the same email as now records nothing`() {
        val state = stateWith(NeighborRegistered(ana, "Ana Ruiz", "ana@example.org", now))
        assertThat(decide(state, email = "ana@example.org")).isEmpty()
    }

    @Test
    fun `INV-CEM-9 a closed account asking for its current email records nothing, before the closed check`() {
        val state = stateWith(*shed(NeighborAccountClosed(ana, false, now)))
        assertThat(decide(state, email = "ana@oldmail.example")).isEmpty()
    }

    // --- INV-CEM-3 and INV-CEM-3a: closed is reported before suspended ---

    @Test
    fun `INV-CEM-3 a closed account is reported account-closed before suspended`() {
        val state = stateWith(*shed(NeighborSuspended(ana, now), NeighborAccountClosed(ana, true, now)))
        refusedWith("account-closed") { decide(state) }
    }

    @Test
    fun `INV-CEM-3a a suspended account is refused neighbor-suspended`() {
        refusedWith("neighbor-suspended") { decide(stateWith(*shed(NeighborSuspended(ana, now)))) }
    }

    // --- INV-CEM-10 and INV-CEM-11: order of the first failure ---

    @Test
    fun `INV-CEM-10 an unknown neighbor is reported before a missing email`() {
        val unknown = NeighborId(UUID.fromString("00000000-0000-0000-0000-0000000000ff"))
        refusedWith("neighbor-not-found") { decide(stateWith(*shed()), email = null, by = unknown) }
    }

    @Test
    fun `INV-CEM-11 a missing email is reported before an invalid one`() {
        refusedWith("email-missing") { decide(stateWith(*shed()), email = null) }
    }

    @Test
    fun `INV-CEM-12 a too-long email is reported before an invalid one`() {
        refusedWith("email-too-long") { decide(stateWith(*shed()), email = "a".repeat(255)) }
    }

    @Test
    fun `INV-CEM-2 an invalid email is reported before a held one`() {
        refusedWith("email-invalid") { decide(stateWith(*shed()), email = "ben.example.org") }
    }

    // --- INV-CEM-12: lengths are user-visible characters, not code points ---

    @Test
    fun `INV-CEM-12 an email of 254 user-visible characters with a combining mark is accepted`() {
        // "é" is e plus U+0301: two code points, one user-visible character.
        val email = "é" + "a".repeat(241) + "@example.org"
        assertThat(email.codePointCount(0, email.length)).isGreaterThan(254)
        assertThat(decide(stateWith(*shed()), email = email)).hasSize(1)
    }

    @Test
    fun `INV-CEM-12 an email of 255 user-visible characters with a combining mark is refused email-too-long`() {
        val email = "é" + "a".repeat(242) + "@example.org"
        refusedWith("email-too-long") { decide(stateWith(*shed()), email = email) }
    }

    // --- Text is trimmed of any Unicode whitespace; text empty after trimming is absent (INV-CEM-11) ---

    @Test
    fun `INV-CEM-11 an email wrapped in no-break space and ideographic space is trimmed and recorded`() {
        val recorded = decide(stateWith(*shed()), email = " ana@example.org　")
        assertThat(recorded.single().email).isEqualTo("ana@example.org")
    }

    @Test
    fun `INV-CEM-11 an email of only an em space is missing`() {
        refusedWith("email-missing") { decide(stateWith(*shed()), email = " ") }
    }
}
