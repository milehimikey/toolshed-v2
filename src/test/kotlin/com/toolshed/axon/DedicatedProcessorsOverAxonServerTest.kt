package com.toolshed.axon

import com.toolshed.flaggedneighbors.FlaggedNeighborsConfiguration
import com.toolshed.flaggedneighbors.FlaggedNeighborsProjection
import com.toolshed.holdstofulfill.HoldsToFulfillConfiguration
import com.toolshed.holdstofulfill.HoldsToFulfillProjection
import com.toolshed.myaccount.MyAccountConfiguration
import com.toolshed.myaccount.MyAccountProjection
import com.toolshed.myholds.MyHoldsConfiguration
import com.toolshed.myholds.MyHoldsProjection
import com.toolshed.myloans.MyLoansConfiguration
import com.toolshed.myloans.MyLoansProjection
import com.toolshed.neighbors.NeighborsConfiguration
import com.toolshed.neighbors.NeighborsProjection
import com.toolshed.neighborstoflag.NeighborsToFlagConfiguration
import com.toolshed.neighborstoflag.NeighborsToFlagProjection
import com.toolshed.overdueloanstonotify.OverdueLoansToNotifyConfiguration
import com.toolshed.overdueloanstonotify.OverdueLoansToNotifyProjection
import com.toolshed.pickupnoticestosend.PickupNoticesToSendConfiguration
import com.toolshed.pickupnoticestosend.PickupNoticesToSendProjection
import com.toolshed.pickupstoexpire.PickupsToExpireConfiguration
import com.toolshed.pickupstoexpire.PickupsToExpireProjection
import com.toolshed.sendoverduenotice.OverdueNoticeOutbox
import com.toolshed.sendoverduenotice.OverdueNoticeSent
import com.toolshed.sendoverduenotice.SendOverdueNoticeConfiguration
import com.toolshed.sendpickupnotice.PickupNoticeOutbox
import com.toolshed.sendpickupnotice.PickupNoticeSent
import com.toolshed.sendpickupnotice.SendPickupNoticeConfiguration
import com.toolshed.suspendedneighbors.SuspendedNeighborsConfiguration
import com.toolshed.suspendedneighbors.SuspendedNeighborsProjection
import com.toolshed.toolcatalog.ToolCatalogConfiguration
import com.toolshed.toolcatalog.ToolCatalogProjection
import com.toolshed.toolinventory.ToolInventoryConfiguration
import com.toolshed.toolinventory.ToolInventoryProjection
import com.toolshed.undeliverednotices.UndeliveredNoticesConfiguration
import com.toolshed.undeliverednotices.UndeliveredNoticesProjection
import io.axoniq.framework.testcontainer.AxonServerContainer
import org.axonframework.common.configuration.AxonConfiguration
import org.axonframework.messaging.core.QualifiedName
import org.axonframework.messaging.eventhandling.EventHandlingComponent
import org.axonframework.messaging.eventhandling.processing.EventProcessor
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessor
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Every event handling component in the running app, over a real Axon Server, is alone on the named pooled-streaming
 * processor its slice fixture uses: the fourteen projections and the two notice outboxes. Without an explicit definition
 * the Spring starter names each processor after the component's package, so fixture and app would disagree on names and
 * an outbox that stops its processor on purpose (INV-OVN-4, INV-PKN-4) could stall a neighbour.
 */
@SpringBootTest
@Testcontainers
class DedicatedProcessorsOverAxonServerTest {
    companion object {
        @Container
        @JvmStatic
        val axonServer = AxonServerContainer().withDcbContext(true)

        @DynamicPropertySource
        @JvmStatic
        fun axonServerProperties(registry: DynamicPropertyRegistry) {
            registry.add("axon.axonserver.servers") { axonServer.axonServerAddress }
        }

        @JvmStatic
        fun projections(): Stream<Arguments> =
            Stream.of(
                Arguments.of(FlaggedNeighborsConfiguration.PROCESSOR, FlaggedNeighborsProjection::class.java),
                Arguments.of(HoldsToFulfillConfiguration.PROCESSOR, HoldsToFulfillProjection::class.java),
                Arguments.of(MyAccountConfiguration.PROCESSOR, MyAccountProjection::class.java),
                Arguments.of(MyHoldsConfiguration.PROCESSOR, MyHoldsProjection::class.java),
                Arguments.of(MyLoansConfiguration.PROCESSOR, MyLoansProjection::class.java),
                Arguments.of(NeighborsConfiguration.PROCESSOR, NeighborsProjection::class.java),
                Arguments.of(NeighborsToFlagConfiguration.PROCESSOR, NeighborsToFlagProjection::class.java),
                Arguments.of(OverdueLoansToNotifyConfiguration.PROCESSOR, OverdueLoansToNotifyProjection::class.java),
                Arguments.of(PickupNoticesToSendConfiguration.PROCESSOR, PickupNoticesToSendProjection::class.java),
                Arguments.of(PickupsToExpireConfiguration.PROCESSOR, PickupsToExpireProjection::class.java),
                Arguments.of(SuspendedNeighborsConfiguration.PROCESSOR, SuspendedNeighborsProjection::class.java),
                Arguments.of(ToolCatalogConfiguration.PROCESSOR, ToolCatalogProjection::class.java),
                Arguments.of(ToolInventoryConfiguration.PROCESSOR, ToolInventoryProjection::class.java),
                Arguments.of(UndeliveredNoticesConfiguration.PROCESSOR, UndeliveredNoticesProjection::class.java),
            )
    }

    @Autowired lateinit var configuration: AxonConfiguration

    @Autowired lateinit var context: ApplicationContext

    private fun processors(): Map<String, EventProcessor> =
        configuration.getComponents(EventProcessor::class.java).values.associateBy { it.name() }

    /** The event handling components registered on [processor]'s module, keyed by the module's component name. */
    private fun componentsOn(processor: String): Map<String, EventHandlingComponent> {
        val module = configuration.getModuleConfiguration("EventProcessor[$processor]")
        assertTrue(module.isPresent, "no module configuration for processor $processor; processors: ${processors().keys}")
        return module.get().getComponents(EventHandlingComponent::class.java)
    }

    /** [handler] is the one component on [processor], a pooled-streaming one, and sits on no other processor. */
    private fun assertDedicated(
        processor: String,
        handler: Class<*>,
    ): EventHandlingComponent {
        val beanName = context.getBeanNamesForType(handler).single()
        val all = processors()
        val found = assertNotNull(all[processor], "processor $processor is not registered; processors: ${all.keys}")
        assertIs<PooledStreamingEventProcessor>(found, "$processor is not pooled-streaming")

        val components = componentsOn(processor)
        assertEquals(1, components.size, "$processor should carry only ${handler.simpleName}, has: ${components.keys}")
        val (name, component) = components.entries.single()
        assertTrue(name.contains("[$beanName]"), "the one component on $processor is not the bean $beanName: $name")

        val elsewhere =
            all.keys
                .filter { it != processor }
                .filter { other -> componentsOn(other).keys.any { it.contains("[$beanName]") } }
        assertTrue(elsewhere.isEmpty(), "$beanName is also on: $elsewhere")
        return component
    }

    @ParameterizedTest(name = "{0} carries {1} alone")
    @MethodSource("projections")
    fun `every projection is alone on its fixture-named pooled-streaming processor in the running app`(
        processor: String,
        projection: Class<*>,
    ) {
        val component = assertDedicated(processor, projection)
        assertTrue(component.supportedEvents().isNotEmpty(), "${projection.simpleName} handles no events on $processor")
    }

    @Test
    fun `INV-OVN-4 - the overdue notice outbox is alone on its own pooled-streaming processor in the running app`() {
        val component = assertDedicated(SendOverdueNoticeConfiguration.OUTBOX_PROCESSOR, OverdueNoticeOutbox::class.java)
        assertEquals(
            setOf(QualifiedName(OverdueNoticeSent::class.java)),
            component.supportedEvents(),
            "the outbox handles exactly its notice event",
        )
    }

    @Test
    fun `INV-PKN-4 - the pickup notice outbox is alone on its own pooled-streaming processor in the running app`() {
        val component = assertDedicated(SendPickupNoticeConfiguration.OUTBOX_PROCESSOR, PickupNoticeOutbox::class.java)
        assertEquals(
            setOf(QualifiedName(PickupNoticeSent::class.java)),
            component.supportedEvents(),
            "the outbox handles exactly its notice event",
        )
    }

    @Test
    fun `no processor is named after a package - every handler has an explicit definition`() {
        val packageNamed = processors().keys.filter { it.startsWith("com.toolshed") }
        assertTrue(packageNamed.isEmpty(), "package-named processors still exist: $packageNamed")
        assertEquals(16, processors().size, "expected 14 projections + 2 outboxes; processors: ${processors().keys}")
    }
}
