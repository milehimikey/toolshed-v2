package com.toolshed.registerneighbor

import com.toolshed.axon.NeighborId
import com.toolshed.changeemail.NeighborEmailChanged
import com.toolshed.closeaccount.NeighborAccountClosed
import org.assertj.core.api.Assertions.assertThat
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer
import org.axonframework.test.fixture.AxonTestFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** One test per scenario in slices/register-neighbor.md, built from the slice's own configure function. */
class RegisterNeighborAxonFixtureTest {
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private lateinit var fixture: AxonTestFixture

    private val n1 = NeighborId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    private val n2 = NeighborId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
    private val n4 = NeighborId(UUID.fromString("00000000-0000-0000-0000-000000000004"))
    private val n5 = NeighborId(UUID.fromString("00000000-0000-0000-0000-000000000005"))
    private val n6 = NeighborId(UUID.fromString("00000000-0000-0000-0000-000000000006"))
    private val n7 = NeighborId(UUID.fromString("00000000-0000-0000-0000-000000000007"))
    private val n8 = NeighborId(UUID.fromString("00000000-0000-0000-0000-000000000008"))
    private val n9 = NeighborId(UUID.fromString("00000000-0000-0000-0000-000000000009"))
    private val n10 = NeighborId(UUID.fromString("00000000-0000-0000-0000-000000000010"))
    private val newcomer = NeighborId(UUID.fromString("00000000-0000-0000-0000-00000000000a"))

    @BeforeEach
    fun beforeEach() {
        fixture = AxonTestFixture.with(RegisterNeighborConfiguration.configure(EventSourcingConfigurer.create(), clock))
    }

    @AfterEach
    fun afterEach() {
        fixture.stop()
    }

    private fun registered(
        id: NeighborId,
        name: String,
        email: String,
    ) = NeighborRegistered(id, name, email, now)

    private fun signUp(
        id: NeighborId,
        name: String?,
        email: String?,
    ) = RegisterNeighbor(id, name, email)

    // --- happy path and input cleaning ---

    @Test
    fun `INV-REG-3 INV-REG-5 INV-REG-8 given no account uses the email, when Ana signs up, then recorded at once`() {
        fixture
            .given()
            .`when`()
            .command(signUp(n1, "Ana Ruiz", "ana@example.org"))
            .then()
            .success()
            .events(registered(n1, "Ana Ruiz", "ana@example.org"))
    }

    @Test
    fun `INV-REG-3 given no account uses the email, when name and email have surrounding whitespace, then trimmed`() {
        fixture
            .given()
            .`when`()
            .command(signUp(n1, "  Ana Ruiz ", " ana@example.org "))
            .then()
            .success()
            .events(registered(n1, "Ana Ruiz", "ana@example.org"))
    }

    @Test
    fun `INV-REG-3 given a blank name, when signing up, then refused name-missing and nothing recorded`() {
        fixture
            .given()
            .`when`()
            .command(signUp(n1, "   ", "ana@example.org"))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-REG-3", "name-missing", "Please enter your name.") }
    }

    @Test
    fun `INV-REG-3 given a missing name, when signing up, then refused name-missing`() {
        fixture
            .given()
            .`when`()
            .command(signUp(n1, null, "ana@example.org"))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-REG-3", "name-missing", "Please enter your name.") }
    }

    @Test
    fun `INV-REG-3 given a blank email, when signing up, then refused email-missing`() {
        fixture
            .given()
            .`when`()
            .command(signUp(n1, "Ana Ruiz", "\t"))
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(ex, "INV-REG-3", "email-missing", "Please enter your email address.")
            }
    }

    // --- email uniqueness ---

    @Test
    fun `INV-REG-4 given an active account uses the email, when someone signs up with it, then refused email-in-use`() {
        fixture
            .given()
            .event(registered(n1, "Ana Ruiz", "ana@example.org"))
            .`when`()
            .command(signUp(n2, "Someone Else", "ana@example.org"))
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(ex, "INV-REG-4", "email-in-use", "That email address can't be used to sign up.")
            }
    }

    @Test
    fun `INV-REG-4 given a suspended account uses the email, when someone signs up with it, then refused email-in-use`() {
        // Suspension changes nothing here: a suspended account is not closed, so its email stays held.
        fixture
            .given()
            .event(registered(n2, "Ben Cole", "ben@example.org"))
            .`when`()
            .command(signUp(n3(), "Someone Else", "ben@example.org"))
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(ex, "INV-REG-4", "email-in-use", "That email address can't be used to sign up.")
            }
    }

    @Test
    fun `INV-REG-4 given an account closed while suspended, when someone signs up with its email, then refused email-in-use`() {
        fixture
            .given()
            .event(registered(n2, "Ben Cole", "ben@example.org"))
            .event(NeighborAccountClosed(n2, true, now))
            .`when`()
            .command(signUp(n5, "Ben Cole", "ben@example.org"))
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(ex, "INV-REG-4", "email-in-use", "That email address can't be used to sign up.")
            }
    }

    @Test
    fun `INV-REG-4 INV-REG-6 given an account closed while active, when the same person signs up again, then recorded as a new account`() {
        fixture
            .given()
            .event(registered(n1, "Ana Ruiz", "ana@example.org"))
            .event(NeighborAccountClosed(n1, false, now))
            .`when`()
            .command(signUp(n4, "Ana Ruiz", "ana@example.org"))
            .then()
            .success()
            .events(registered(n4, "Ana Ruiz", "ana@example.org"))
    }

    @Test
    fun `INV-REG-4 given suspended then reinstated then closed while active, when signing up with its email, then recorded`() {
        fixture
            .given()
            .event(registered(n2, "Ben Cole", "ben@example.org"))
            .event(NeighborAccountClosed(n2, false, now))
            .`when`()
            .command(signUp(n9, "Ben Cole", "ben@example.org"))
            .then()
            .success()
            .events(registered(n9, "Ben Cole", "ben@example.org"))
    }

    @Test
    fun `INV-REG-4 INV-REG-6 given an account closed while suspended, when signing up with a different email, then recorded`() {
        fixture
            .given()
            .event(registered(n2, "Ben Cole", "ben@example.org"))
            .event(NeighborAccountClosed(n2, true, now))
            .`when`()
            .command(signUp(n5, "Ben Cole", "ben.new@example.org"))
            .then()
            .success()
            .events(registered(n5, "Ben Cole", "ben.new@example.org"))
    }

    @Test
    fun `INV-REG-4 given an email changed away from, when someone signs up with the old email, then recorded`() {
        fixture
            .given()
            .event(registered(n1, "Ana Ruiz", "ana@oldmail.example"))
            .event(NeighborEmailChanged(n1, "ana@example.org", now))
            .`when`()
            .command(signUp(n6, "Someone Else", "ana@oldmail.example"))
            .then()
            .success()
            .events(registered(n6, "Someone Else", "ana@oldmail.example"))
    }

    @Test
    fun `INV-REG-4 given an active account, when the email differs only in capitals, then refused email-in-use`() {
        fixture
            .given()
            .event(registered(n1, "Ana Ruiz", "ana@example.org"))
            .`when`()
            .command(signUp(n10, "Ana Ruiz", "Ana@Example.ORG"))
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(ex, "INV-REG-4", "email-in-use", "That email address can't be used to sign up.")
            }
    }

    @Test
    fun `INV-REG-4 given no account uses the email in any case, when signing up, then the email is kept as typed`() {
        fixture
            .given()
            .`when`()
            .command(signUp(n1, "Ana Ruiz", "Ana@Example.org"))
            .then()
            .success()
            .events(registered(n1, "Ana Ruiz", "Ana@Example.org"))
    }

    // --- names are not unique ---

    @Test
    fun `INV-REG-9 given Sam Lee already registered, when another Sam Lee signs up, then both accounts exist`() {
        fixture
            .given()
            .event(registered(n7, "Sam Lee", "sam@example.org"))
            .`when`()
            .command(signUp(n8, "Sam Lee", "sam.lee@example.org"))
            .then()
            .success()
            .events(registered(n8, "Sam Lee", "sam.lee@example.org"))
    }

    // --- email shape ---

    @Test
    fun `INV-REG-11 given an email with no at sign, when signing up, then refused email-invalid`() {
        fixture
            .given()
            .`when`()
            .command(signUp(n1, "Ana Ruiz", "ana.example.org"))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-REG-11", "email-invalid", "Please enter a valid email address.") }
    }

    @Test
    fun `INV-REG-11 given nothing before the at sign, when signing up, then refused email-invalid`() {
        fixture
            .given()
            .`when`()
            .command(signUp(n1, "Ana Ruiz", "@example.org"))
            .then()
            .exceptionSatisfies { ex -> assertRejected(ex, "INV-REG-11", "email-invalid", "Please enter a valid email address.") }
    }

    // --- length limits ---

    @Test
    fun `INV-REG-10 given a name of exactly 100 characters, when signing up, then recorded`() {
        val name = "n".repeat(100)
        fixture
            .given()
            .`when`()
            .command(signUp(n1, name, "ana@example.org"))
            .then()
            .success()
            .events(registered(n1, name, "ana@example.org"))
    }

    @Test
    fun `INV-REG-10 given a name of 101 characters, when signing up, then refused name-too-long`() {
        fixture
            .given()
            .`when`()
            .command(signUp(n1, "n".repeat(101), "ana@example.org"))
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(ex, "INV-REG-10", "name-too-long", "Your name can be at most 100 characters.")
            }
    }

    @Test
    fun `INV-REG-10 given an email of 255 characters, when signing up, then refused email-too-long`() {
        val email = "a".repeat(243) + "@example.org"
        assertThat(email).hasSize(255)
        fixture
            .given()
            .`when`()
            .command(signUp(n1, "Ana Ruiz", email))
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(ex, "INV-REG-10", "email-too-long", "Your email address can be at most 254 characters.")
            }
    }

    // --- retries and neighbor ids ---

    @Test
    fun `INV-REG-7 given the account was recorded, when the same request arrives again, then succeeds and records nothing`() {
        fixture
            .given()
            .event(registered(n1, "Ana Ruiz", "ana@example.org"))
            .`when`()
            .command(signUp(n1, "Ana Ruiz", "ana@example.org"))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-REG-7 given the account was closed, when the same request arrives again, then records nothing and stays closed`() {
        fixture
            .given()
            .event(registered(n1, "Ana Ruiz", "ana@example.org"))
            .event(NeighborAccountClosed(n1, false, now))
            .`when`()
            .command(signUp(n1, "Ana Ruiz", "ana@example.org"))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-REG-7 given the neighbor ID was recorded, when a request reuses it for different details, then refused neighbor-id-in-use`() {
        fixture
            .given()
            .event(registered(n1, "Ana Ruiz", "ana@example.org"))
            .`when`()
            .command(signUp(n1, "Ben Cole", "ben@example.org"))
            .then()
            .exceptionSatisfies { ex ->
                assertRejected(ex, "INV-REG-7", "neighbor-id-in-use", "That neighbor ID is already used by another account.")
            }
    }

    @Test
    fun `INV-REG-7 given the account was recorded, when a repeat has an email changed since, then it is still the same request`() {
        // The repeat carries the email as registered; a later change does not make it a different request.
        fixture
            .given()
            .event(registered(n1, "Ana Ruiz", "ana@example.org"))
            .event(NeighborEmailChanged(n1, "ana@new.example", now))
            .`when`()
            .command(signUp(n1, "Ana Ruiz", "ANA@example.org"))
            .then()
            .success()
            .noEvents()
    }

    @Test
    fun `INV-REG-6 given a new neighbor ID, when a closed neighbor signs up again, then the closed account is untouched`() {
        fixture
            .given()
            .event(registered(n1, "Ana Ruiz", "ana@example.org"))
            .event(NeighborAccountClosed(n1, false, now))
            .`when`()
            .command(signUp(newcomer, "Ana Ruiz", "ana@example.org"))
            .then()
            .success()
            .events(registered(newcomer, "Ana Ruiz", "ana@example.org"))
    }

    private fun n3() = NeighborId(UUID.fromString("00000000-0000-0000-0000-000000000003"))

    private fun assertRejected(
        ex: Throwable,
        invariant: String,
        reason: String,
        sentence: String,
    ) {
        val rejected =
            generateSequence<Throwable>(ex) { it.cause }
                .filterIsInstance<RegisterNeighborRejected>()
                .firstOrNull()
        assertThat(rejected).isNotNull
        requireNotNull(rejected) { "no RegisterNeighborRejected in the exception chain: $ex" }
        assertThat(rejected.invariant).isEqualTo(invariant)
        assertThat(rejected.reason).isEqualTo(reason)
        assertThat(rejected.message).isEqualTo(sentence)
    }
}
