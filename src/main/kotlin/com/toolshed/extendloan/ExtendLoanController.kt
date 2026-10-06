package com.toolshed.extendloan

import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.web.Caller
import com.toolshed.web.CommandDispatcher
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

/**
 * The neighbor is named in the path and must be the signed-in neighbor (Amendment 6). A repeat from
 * the same due date records nothing new and still answers 201, as Register Neighbor does.
 */
@RestController
@RequestMapping("/api/neighbors/{neighborId}/loans/{loanId}/extensions")
class ExtendLoanController(
    private val dispatcher: CommandDispatcher,
) {
    @PostMapping
    fun extend(
        @PathVariable neighborId: UUID,
        @PathVariable loanId: UUID,
        caller: Caller,
        @RequestBody body: ExtendLoanRequest,
    ): ResponseEntity<Void> {
        val command = ExtendLoan(NeighborId(neighborId), LoanId(loanId), body.fromDueDate)
        caller.requireBorrower(command)
        dispatcher.send(command, Any::class.java)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }
}

data class ExtendLoanRequest(
    val fromDueDate: LocalDate,
)
