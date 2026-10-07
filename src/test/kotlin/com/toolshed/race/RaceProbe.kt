package com.toolshed.race

import com.toolshed.axon.CommandRejected
import com.toolshed.axon.decideAndAppend
import com.toolshed.web.CommandDispatcher
import org.axonframework.eventsourcing.annotation.EventSourcedEntity
import org.axonframework.eventsourcing.annotation.EventSourcingHandler
import org.axonframework.eventsourcing.annotation.EventTag
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.messaging.commandhandling.annotation.CommandHandler
import org.axonframework.messaging.core.unitofwork.ProcessingContext
import org.axonframework.modelling.annotation.InjectEntity
import org.axonframework.modelling.annotation.TargetEntityId
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** A claim: the first request for a claim id wins; every later one is refused probe-claimed. */
data class ProbeClaimed(
    @field:EventTag(key = "claimId") val claimId: UUID,
)

data class ProbeClaim(
    @TargetEntityId val claimId: UUID,
)

class ProbeClaimedRejected : CommandRejected("INV-RACE-1", "probe-claimed", "Already claimed.")

@EventSourcedEntity(tagKey = "claimId")
class ProbeClaimState
    @EntityCreator
    constructor() {
        private var claimed = false

        val isClaimed: Boolean
            get() = claimed

        @EventSourcingHandler
        fun evolve(event: ProbeClaimed) {
            claimed = true
        }
    }

@Profile("probes-race")
@Component
class ProbeClaimHandler {
    @CommandHandler
    fun claim(
        command: ProbeClaim,
        @InjectEntity state: ProbeClaimState,
        context: ProcessingContext,
    ) {
        context.decideAndAppend {
            if (state.isClaimed) throw ProbeClaimedRejected()
            listOf(ProbeClaimed(command.claimId))
        }
    }
}

@Profile("probes-race")
@RestController
class RaceProbeController(
    private val dispatcher: CommandDispatcher,
) {
    @PostMapping("/probe-claim")
    fun claim(
        @RequestParam claimId: UUID,
    ): ResponseEntity<Void> {
        dispatcher.send(ProbeClaim(claimId), Any::class.java)
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build()
    }
}

@Profile("probes-race")
@Configuration
class RaceProbeWiring {
    @Bean
    fun probeClaimModule(): EventSourcedEntityModule<UUID, ProbeClaimState> =
        EventSourcedEntityModule.autodetected(UUID::class.java, ProbeClaimState::class.java)
}
