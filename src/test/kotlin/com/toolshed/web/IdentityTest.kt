package com.toolshed.web

import com.toolshed.axon.NeighborId
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import kotlin.test.assertTrue

class IdentityTest {
    private val neighbor = UUID.fromString("11111111-2222-3333-4444-555555555555")

    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(IdentityController())
            .setControllerAdvice(CommandRejectedAdvice())
            .setCustomArgumentResolvers(CallerArgumentResolver())
            .build()

    @RestController
    class IdentityController {
        @GetMapping("/whoami")
        fun whoami(caller: Caller): Map<String, Any?> =
            mapOf(
                "neighborId" to caller.neighborId?.value?.toString(),
                "isShedManager" to caller.isShedManager,
                "isSignedIn" to caller.isSignedIn,
            )

        @GetMapping("/manage")
        fun manage(caller: Caller): String {
            caller.requireShedManager(
                notSignedIn = Refusal("INV-TEST-1", "Please sign in."),
                notShedManager = Refusal("INV-TEST-4", "Only the shed manager can do this."),
            )
            return "ok"
        }

        @GetMapping("/mine")
        fun mine(
            caller: Caller,
            @RequestHeader("X-Loan-Owner") owner: String,
        ): String {
            caller.requireNeighbor(
                neighborId = NeighborId(UUID.fromString(owner)),
                notSignedIn = Refusal("INV-TEST-2", "Please sign in."),
                mismatch = Refusal("INV-TEST-5", "That is not your loan."),
            )
            return "ok"
        }

        @GetMapping("/signed-in")
        fun signedIn(caller: Caller): String {
            caller.requireSignedIn(Refusal("INV-TEST-3", "Please sign in."))
            return "ok"
        }
    }

    // --- resolver cases ---

    @Test
    fun `no headers resolves to not signed in`() {
        mockMvc.get("/whoami").andExpect {
            jsonPath("$.isSignedIn") { value(false) }
            jsonPath("$.neighborId") { doesNotExist() }
            jsonPath("$.isShedManager") { value(false) }
        }
    }

    @Test
    fun `neighbor header alone resolves to that neighbor`() {
        mockMvc.get("/whoami") { header("X-Toolshed-Neighbor-Id", neighbor.toString()) }.andExpect {
            jsonPath("$.neighborId") { value(neighbor.toString()) }
            jsonPath("$.isShedManager") { value(false) }
            jsonPath("$.isSignedIn") { value(true) }
        }
    }

    @Test
    fun `role header alone resolves to shed manager with no neighbor`() {
        mockMvc.get("/whoami") { header("X-Toolshed-Role", "shed-manager") }.andExpect {
            jsonPath("$.neighborId") { doesNotExist() }
            jsonPath("$.isShedManager") { value(true) }
            jsonPath("$.isSignedIn") { value(true) }
        }
    }

    @Test
    fun `both headers resolve to a neighbor who is also shed manager`() {
        mockMvc
            .get("/whoami") {
                header("X-Toolshed-Neighbor-Id", neighbor.toString())
                header("X-Toolshed-Role", "shed-manager")
            }.andExpect {
                jsonPath("$.neighborId") { value(neighbor.toString()) }
                jsonPath("$.isShedManager") { value(true) }
            }
    }

    @Test
    fun `a malformed neighbor id makes the request not signed in, even with the role`() {
        mockMvc
            .get("/whoami") {
                header("X-Toolshed-Neighbor-Id", "not-a-uuid")
                header("X-Toolshed-Role", "shed-manager")
            }.andExpect {
                jsonPath("$.isSignedIn") { value(false) }
                jsonPath("$.isShedManager") { value(false) }
                jsonPath("$.neighborId") { doesNotExist() }
            }
    }

    @Test
    fun `an unrecognised role value grants nothing`() {
        mockMvc.get("/whoami") { header("X-Toolshed-Role", "Shed-Manager") }.andExpect {
            jsonPath("$.isShedManager") { value(false) }
            jsonPath("$.isSignedIn") { value(false) }
        }
    }

    // --- guard refusals mapped to 422 ---

    @Test
    fun `shed manager guard refuses an anonymous caller with not-signed-in`() {
        mockMvc.get("/manage") { accept = MediaType.APPLICATION_PROBLEM_JSON }.andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-TEST-1") }
            jsonPath("$.reason") { value("not-signed-in") }
            jsonPath("$.detail") { value("Please sign in.") }
        }
    }

    @Test
    fun `shed manager guard refuses a neighbor with not-shed-manager`() {
        mockMvc
            .get("/manage") {
                header("X-Toolshed-Neighbor-Id", neighbor.toString())
                accept = MediaType.APPLICATION_PROBLEM_JSON
            }.andExpect {
                status { isEqualTo(422) }
                jsonPath("$.invariant") { value("INV-TEST-4") }
                jsonPath("$.reason") { value("not-shed-manager") }
                jsonPath("$.detail") { value("Only the shed manager can do this.") }
            }
    }

    @Test
    fun `shed manager guard lets the shed manager through`() {
        mockMvc.get("/manage") { header("X-Toolshed-Role", "shed-manager") }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `neighbor guard refuses another neighbor with requester-mismatch`() {
        val other = UUID.randomUUID()
        mockMvc
            .get("/mine") {
                header("X-Toolshed-Neighbor-Id", neighbor.toString())
                header("X-Loan-Owner", other.toString())
                accept = MediaType.APPLICATION_PROBLEM_JSON
            }.andExpect {
                status { isEqualTo(422) }
                jsonPath("$.invariant") { value("INV-TEST-5") }
                jsonPath("$.reason") { value("requester-mismatch") }
                jsonPath("$.detail") { value("That is not your loan.") }
            }
    }

    @Test
    fun `neighbor guard refuses an anonymous caller with not-signed-in before checking the neighbor`() {
        mockMvc
            .get("/mine") {
                header("X-Loan-Owner", neighbor.toString())
                accept = MediaType.APPLICATION_PROBLEM_JSON
            }.andExpect {
                status { isEqualTo(422) }
                jsonPath("$.reason") { value("not-signed-in") }
            }
    }

    @Test
    fun `neighbor guard lets the matching neighbor through`() {
        mockMvc
            .get("/mine") {
                header("X-Toolshed-Neighbor-Id", neighbor.toString())
                header("X-Loan-Owner", neighbor.toString())
            }.andExpect {
                status { isOk() }
            }
    }

    @Test
    fun `signed-in guard refuses an anonymous caller and passes any signed-in caller`() {
        mockMvc.get("/signed-in") { accept = MediaType.APPLICATION_PROBLEM_JSON }.andExpect {
            status { isEqualTo(422) }
            jsonPath("$.invariant") { value("INV-TEST-3") }
            jsonPath("$.reason") { value("not-signed-in") }
        }
        mockMvc.get("/signed-in") { header("X-Toolshed-Role", "shed-manager") }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `each refusal in a guard carries its own invariant, even when the two differ`() {
        // The docs cite different INV ids for not-signed-in and for mismatch or role; the 422 must carry the right one.
        val anonymousMine =
            mockMvc.get("/mine") {
                header("X-Loan-Owner", neighbor.toString())
                accept = MediaType.APPLICATION_PROBLEM_JSON
            }
        anonymousMine.andExpect { jsonPath("$.invariant") { value("INV-TEST-2") } }
        mockMvc
            .get("/mine") {
                header("X-Toolshed-Neighbor-Id", neighbor.toString())
                header("X-Loan-Owner", UUID.randomUUID().toString())
                accept = MediaType.APPLICATION_PROBLEM_JSON
            }.andExpect { jsonPath("$.invariant") { value("INV-TEST-5") } }
    }

    @Test
    fun `the web configuration registers the caller resolver`() {
        val resolvers = mutableListOf<org.springframework.web.method.support.HandlerMethodArgumentResolver>()
        WebConfiguration().addArgumentResolvers(resolvers)
        assertTrue(resolvers.any { it is CallerArgumentResolver })
    }
}
