package com.toolshed.changeemail

import com.toolshed.axon.CommandRejected
import com.toolshed.axon.NeighborId
import com.toolshed.closeaccount.NeighborAccountClosed
import com.toolshed.registerneighbor.NeighborRegistered
import com.toolshed.reinstateneighbor.NeighborReinstated
import com.toolshed.suspendneighbor.NeighborSuspended
import com.toolshed.web.Caller
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.test.fixture.AxonTestFixture
import org.axonframework.test.fixture.AxonTestPhase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** One test per scenario in slices/change-email.md, built from the slice's own configure function. */
class ChangeEmailAxonFixtureTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val n1 = NeighborId(UUID.nameUUIDFromBytes("n1".toByteArray()))
    private val n2 = NeighborId(UUID.nameUUIDFromBytes("n2".toByteArray()))
    private val n3 = NeighborId(UUID.nameUUIDFromBytes("n3".toByteArray()))
    private val n4 = NeighborId(UUID.nameUUIDFromBytes("n4".toByteArray()))
    private val n5 = NeighborId(UUID.nameUUIDFromBytes("n5".toByteArray()))
    private val n9 = NeighborId(UUID.nameUUIDFromBytes("n9".toByteArray()))

    private lateinit var fixture: AxonTestFixture

    @BeforeEach
    fun beforeEach() {
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        fixture = AxonTestFixture.with(ChangeEmailConfiguration.configure(EventSourcingConfigurer.create(), clock))
    }

    @AfterEach
    fun afterEach() {
        fixture.stop()
    }

    private fun AxonTestPhase.Given.recorded(events: List<Any>): AxonTestPhase.Given = events.fold(this) { phase, e -> phase.event(e) }

    private fun registered(
        id: NeighborId,
        name: String,
        email: String,
    ) = NeighborRegistered(id, name, email, now)

    /** Ana's account N1 with ana@oldmail.example, plus Ben's N2 with ben@example.org. */
    private fun anaAndBen(): List<Any> =
        listOf(
            registered(n1, "Ana Ruiz", "ana@oldmail.example"),
            registered(n2, "Ben Cole", "ben@example.org"),
        )

    private fun change(
        email: String?,
        neighbor: NeighborId = n1,
    ) = ChangeEmail(neighbor, email)

    private fun changed(
        id: NeighborId,
        email: String,
    ) = NeighborEmailChanged(id, email, now)

    private fun assertRejected(
        ex: Throwable,
        invariant: String,
        reason: String,
        sentence: String,
    ) {
        val rejected =
            generateSequence<Throwable>(ex) { it.cause }
                .filterIsInstance<ChangeEmailRejected>()
                .firstOrNull()
        requireNotNull(rejected) { "no ChangeEmailRejected in the exception chain: $ex" }
        assertThat(rejected.invariant).isEqualTo(invariant)
        assertThat(rejected.reason).isEqualTo(reason)
        assertThat(rejected.message).isEqualTo(sentence)
    }

    // --- the happy path and input cleaning ---

    @Test
    fun `INV-CEM-1 INV-CEM-4 INV-CEM-7 given Ana is active, when she changes her email to ana at example, then recorded at once`() {
        fixture
            .given()
            .recorded(anaAndBen())
            .`when`()
            .command(change("ana@example.org"))
            .then()
            .success()
            .events(changed(n1, "ana@example.org"))
    }

    @Test
    fun `INV-CEM-11 given Ana is active, when the email has surrounding whitespace, then trimmed and recorded`() {
        fixture
            .given()
            .recorded(anaAndBen())
            .`when`()
            .command(change(" ana@example.org "))
            .then()
            .success()
            .events(changed(n1, "ana@example.org"))
    }

    @Test
    fun `INV-CEM-9 given N1's current email is ana at example, when Ana asks for the same, then nothing is recorded`() {
        fixture
            .given()
            .recorded(listOf(registered(n1, "Ana Ruiz", "ana@example.org")))
            .`when`()
            .command(change("ana@example.org"))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-CEM-9 given Neighbor Email Changed was recorded for N1, when the same request arrives again, then nothing new is recorded`() {
        fixture
            .given()
            .recorded(anaAndBen() + changed(n1, "ana@example.org"))
            .`when`()
            .command(change("ana@example.org"))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-CEM-11 given Ana is active, when the email is empty or only whitespace, then refused email-missing`() {
        fixture
            .given()
            .recorded(anaAndBen())
            .`when`()
            .command(change("   "))
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(ex, "INV-CEM-11", "email-missing", "Please enter your email address.")
            }
    }

    // --- email held by another account ---

    @Test
    fun `INV-CEM-2 given Ben's active account uses ben at example, when Ana asks for it, then refused email-in-use`() {
        fixture
            .given()
            .recorded(anaAndBen())
            .`when`()
            .command(change("ben@example.org"))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-CEM-2", "email-in-use", "That email address can't be used.") }
    }

    @Test
    fun `INV-CEM-2 given Cleo's account is suspended with cleo at example, when Ana asks for it, then refused email-in-use`() {
        fixture
            .given()
            .recorded(
                anaAndBen() +
                    registered(n3, "Cleo Park", "cleo@example.org") +
                    NeighborSuspended(n3, now),
            ).`when`()
            .command(change("cleo@example.org"))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-CEM-2", "email-in-use", "That email address can't be used.") }
    }

    @Test
    fun `INV-CEM-2 given Dan's account was closed while suspended, when Ana asks for his email, then refused email-in-use`() {
        fixture
            .given()
            .recorded(
                anaAndBen() +
                    registered(n4, "Dan Ellis", "dan@example.org") +
                    NeighborSuspended(n4, now) +
                    NeighborAccountClosed(n4, true, now),
            ).`when`()
            .command(change("dan@example.org"))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-CEM-2", "email-in-use", "That email address can't be used.") }
    }

    @Test
    fun `INV-CEM-2 given Eve's account was closed while active, when Ana changes to her email, then recorded`() {
        fixture
            .given()
            .recorded(
                anaAndBen() +
                    registered(n5, "Eve Stone", "eve@example.org") +
                    NeighborAccountClosed(n5, false, now),
            ).`when`()
            .command(change("eve@example.org"))
            .then()
            .success()
            .events(changed(n1, "eve@example.org"))
    }

    @Test
    fun `INV-CEM-2 given Ben's account uses ben at example, when Ana asks for Ben at Example dot ORG, then refused email-in-use`() {
        fixture
            .given()
            .recorded(anaAndBen())
            .`when`()
            .command(change("Ben@Example.ORG"))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-CEM-2", "email-in-use", "That email address can't be used.") }
    }

    @Test
    fun `INV-CEM-2 INV-CEM-9 given N1's current email is ana at example, when Ana corrects it to capitals, then recorded`() {
        fixture
            .given()
            .recorded(listOf(registered(n1, "Ana Ruiz", "ana@example.org")))
            .`when`()
            .command(change("Ana@Example.org"))
            .then()
            .success()
            .events(changed(n1, "Ana@Example.org"))
    }

    // --- shape and length ---

    @Test
    fun `INV-CEM-12 given Ana is active, when the email is not shaped like an address, then refused email-invalid`() {
        fixture
            .given()
            .recorded(anaAndBen())
            .`when`()
            .command(change("ana.example.org"))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-CEM-12", "email-invalid", "Please enter a valid email address.") }
    }

    @Test
    fun `INV-CEM-12 given Ana is active, when the email is 255 characters, then refused email-too-long`() {
        fixture
            .given()
            .recorded(anaAndBen())
            .`when`()
            .command(change("a".repeat(243) + "@example.org"))
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(ex, "INV-CEM-12", "email-too-long", "Your email address can be at most 254 characters.")
            }
    }

    // --- the old email is released and the new one is held ---

    @Test
    fun `INV-CEM-5 given Ana changed from oldmail to example, when Ben asks for the old address, then recorded for Ben`() {
        fixture
            .given()
            .recorded(anaAndBen() + changed(n1, "ana@example.org"))
            .`when`()
            .command(change("ana@oldmail.example", neighbor = n2))
            .then()
            .success()
            .events(changed(n2, "ana@oldmail.example"))
    }

    @Test
    fun `INV-CEM-4 given Ana changed her email to ana at example, when Ben asks for it, then refused email-in-use`() {
        fixture
            .given()
            .recorded(anaAndBen() + changed(n1, "ana@example.org"))
            .`when`()
            .command(change("ana@example.org", neighbor = n2))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-CEM-2", "email-in-use", "That email address can't be used.") }
    }

    // --- suspension and closure ---

    @Test
    fun `INV-CEM-3a given N1 is suspended, when Ana asks to change her email, then refused neighbor-suspended`() {
        fixture
            .given()
            .recorded(anaAndBen() + NeighborSuspended(n1, now))
            .`when`()
            .command(change("ana@example.org"))
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(ex, "INV-CEM-3a", "neighbor-suspended", "You can't change your email while your account is suspended.")
            }
    }

    @Test
    fun `INV-CEM-3a given N1 was suspended and reinstated, when Ana changes her email, then recorded`() {
        fixture
            .given()
            .recorded(anaAndBen() + NeighborSuspended(n1, now) + NeighborReinstated(n1, now))
            .`when`()
            .command(change("ana.ruiz@example.org"))
            .then()
            .success()
            .events(changed(n1, "ana.ruiz@example.org"))
    }

    @Test
    fun `INV-CEM-3 given N1 is closed, when a change of N1's email is requested, then refused account-closed`() {
        fixture
            .given()
            .recorded(anaAndBen() + NeighborAccountClosed(n1, false, now))
            .`when`()
            .command(change("ana@example.org"))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-CEM-3", "account-closed", "This account is closed.") }
    }

    // --- identity and unknown accounts ---

    @Test
    fun `INV-CEM-1 given N1 and N2 are active, when a request from Ben asks to change N1's email, then refused requester-mismatch`() {
        val refused =
            assertThrows<CommandRejected> {
                with(Caller(neighborId = n2, isShedManager = false)) { requireAccountHolder(change("x@example.org", neighbor = n1)) }
            }
        assertThat(refused.reason).isEqualTo("requester-mismatch")
        assertThat(refused.invariant).isEqualTo("INV-CEM-1")
        assertThat(refused.message).isEqualTo("You can only change your own email.")
    }

    @Test
    fun `INV-CEM-13 given nobody is signed in, when a request for changing an email arrives, then refused not-signed-in`() {
        val refused =
            assertThrows<CommandRejected> {
                with(Caller.ANONYMOUS) { requireAccountHolder(change("x@example.org")) }
            }
        assertThat(refused.reason).isEqualTo("not-signed-in")
        assertThat(refused.invariant).isEqualTo("INV-CEM-13")
        assertThat(refused.message).isEqualTo("Please sign in.")
    }

    @Test
    fun `INV-CEM-10 given no account has neighbor ID N9, when a change of N9's email is requested, then refused neighbor-not-found`() {
        fixture
            .given()
            .recorded(anaAndBen())
            .`when`()
            .command(change("x@example.org", neighbor = n9))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-CEM-10", "neighbor-not-found", "There is no such neighbor.") }
    }
}
