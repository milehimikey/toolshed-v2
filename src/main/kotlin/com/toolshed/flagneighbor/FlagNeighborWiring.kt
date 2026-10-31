package com.toolshed.flagneighbor

import com.toolshed.axon.ReactionDispatcher
import com.toolshed.neighborstoflag.ListNeighborsToFlag
import com.toolshed.neighborstoflag.NeighborsToFlag
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule
import org.axonframework.messaging.queryhandling.gateway.QueryGateway
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.UUID

/** Registers this slice's entity. The id type is UUID: NeighborId is a value class, erased to its UUID at runtime. */
@Configuration
class FlagNeighborWiring {
    @Bean
    fun flagNeighborStateModule(): EventSourcedEntityModule<UUID, FlagState> =
        EventSourcedEntityModule.autodetected(UUID::class.java, FlagState::class.java)

    /** Spring injects this into the Neighbors To Flag projection, which invokes it after each list change (Amendment 11). */
    @Bean
    fun lateReturnFlaggerHandler(
        queries: QueryGateway,
        reactions: ReactionDispatcher,
    ): LateReturnFlaggerHandler =
        LateReturnFlaggerHandler(
            readList = { queries.query(ListNeighborsToFlag(), NeighborsToFlag::class.java).join() },
            send = reactions::send,
        )
}
