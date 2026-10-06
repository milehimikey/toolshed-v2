package com.toolshed.registerneighbor

import com.toolshed.axon.NeighborId
import com.toolshed.closeaccount.NeighborAccountClosed
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** The pure decision, with no Axon fixture: each boundary, and the order the first failure is reported in. */
class RegisterNeighborDeciderTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val handler = RegisterNeighborCommandHandler(Clock.fixed(now, ZoneOffset.UTC))
    private val n1 = NeighborId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    private val n2 = NeighborId(UUID.fromString("00000000-0000-0000-0000-000000000002"))

    private fun decide(
        name: String?,
        email: String?,
        state: RegisterNeighborCommandHandler.State = RegisterNeighborCommandHandler.State(),
        id: NeighborId = n2,
    ) = handler.decide(RegisterNeighbor(id, name, email), state, now)

    private fun refusedWith(
        reason: String,
        block: () -> Unit,
    ) {
        val rejected = assertThrows<RegisterNeighborRejected>(block)
        assertThat(rejected.reason).isEqualTo(reason)
    }

    // --- INV-REG-3 ---

    @Test
    fun `INV-REG-3 a name of only Unicode whitespace is missing`() {
        refusedWith("name-missing") { decide("   \t", "ana@example.org") }
    }

    @Test
    fun `INV-REG-3 Unicode whitespace around the name and email is trimmed away`() {
        val recorded = decide(" Ana Ruiz ", "　ana@example.org ")
        assertThat(recorded.single().name).isEqualTo("Ana Ruiz")
        assertThat(recorded.single().email).isEqualTo("ana@example.org")
    }

    // --- INV-REG-10: lengths are user-visible characters ---

    @Test
    fun `INV-REG-10 an email of exactly 254 characters is accepted`() {
        val email = "a".repeat(242) + "@example.org"
        assertThat(email).hasSize(254)
        assertThat(decide("Ana Ruiz", email).single().email).isEqualTo(email)
    }

    @Test
    fun `INV-REG-10 a name of 100 user-visible characters written with combining marks is accepted`() {
        // Each "e" plus a combining acute is two code points but one user-visible character.
        val name = "é".repeat(100)
        assertThat(name.codePointCount(0, name.length)).isEqualTo(200)
        assertThat(decide(name, "ana@example.org").single().name).isEqualTo(name)
    }

    @Test
    fun `INV-REG-10 a name of 101 user-visible characters written with combining marks is refused`() {
        refusedWith("name-too-long") { decide("é".repeat(101), "ana@example.org") }
    }

    // --- INV-REG-11 ---

    @Test
    fun `INV-REG-11 nothing after the at sign is refused`() {
        refusedWith("email-invalid") { decide("Ana Ruiz", "ana@") }
    }

    // --- check order: the first failure is the reason returned ---

    @Test
    fun `name-missing is reported before email-missing`() {
        refusedWith("name-missing") { decide(null, null) }
    }

    @Test
    fun `name-too-long is reported before email-missing`() {
        refusedWith("name-too-long") { decide("n".repeat(101), null) }
    }

    @Test
    fun `email-too-long is reported before email-invalid`() {
        refusedWith("email-too-long") { decide("Ana Ruiz", "a".repeat(255)) }
    }

    // --- INV-REG-7: the repeat is judged before the email rules ---

    @Test
    fun `INV-REG-7 a repeat with the same details records nothing and is not refused by its own email`() {
        val state = stateWith(NeighborRegistered(n2, "Ana Ruiz", "ana@example.org", now))
        assertThat(decide("Ana Ruiz", "ANA@example.org", state)).isEmpty()
    }

    @Test
    fun `INV-REG-7 a repeat with a different name is refused neighbor-id-in-use`() {
        val state = stateWith(NeighborRegistered(n2, "Ana Ruiz", "ana@example.org", now))
        refusedWith("neighbor-id-in-use") { decide("Ben Cole", "ana@example.org", state) }
    }

    // --- INV-REG-4: email holding ---

    @Test
    fun `INV-REG-4 an email held by another account is refused email-in-use`() {
        val state = stateWith(NeighborRegistered(n1, "Ana Ruiz", "ana@example.org", now))
        refusedWith("email-in-use") { decide("Ben Cole", "ANA@example.org", state) }
    }

    @Test
    fun `INV-REG-4 an email freed by closing while active can be registered again`() {
        val state =
            stateWith(
                NeighborRegistered(n1, "Ana Ruiz", "ana@example.org", now),
                NeighborAccountClosed(n1, false, now),
            )
        assertThat(decide("Ana Ruiz", "ana@example.org", state)).hasSize(1)
    }

    private fun stateWith(vararg events: Any): RegisterNeighborCommandHandler.State {
        val state = RegisterNeighborCommandHandler.State()
        events.forEach { event ->
            when (event) {
                is NeighborRegistered -> state.evolve(event)
                is NeighborAccountClosed -> state.evolve(event)
                else -> error("unexpected event ${event::class.simpleName}")
            }
        }
        return state
    }
}
