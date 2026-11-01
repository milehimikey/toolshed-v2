package com.toolshed.axon

import com.toolshed.sendoverduenotice.OverdueNoticeOutbox
import com.toolshed.sendoverduenotice.OverdueNoticeSent
import com.toolshed.sendoverduenotice.SendOverdueNoticeConfiguration
import com.toolshed.sendpickupnotice.PickupNoticeOutbox
import com.toolshed.sendpickupnotice.PickupNoticeSent
import com.toolshed.sendpickupnotice.SendPickupNoticeConfiguration
import io.axoniq.framework.testcontainer.AxonServerContainer
import org.axonframework.common.configuration.AxonConfiguration
import org.axonframework.messaging.core.QualifiedName
import org.axonframework.messaging.eventhandling.EventHandlingComponent
import org.axonframework.messaging.eventhandling.processing.EventProcessor
import org.axonframework.messaging.eventhandling.processing.streaming.pooled.PooledStreamingEventProcessor
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The two notice outboxes in the running app, over a real Axon Server: each is the only event handling component on its
 * own named pooled-streaming processor, so a refused hand-off that stops that processor (INV-OVN-4, INV-PKN-4) stalls
 * nothing else, and nothing else can stall the outbox. Without an explicit definition the Spring starter would name the
 * processor after the outbox's package and share it with whatever else handles events there.
 */
@SpringBootTest
@Testcontainers
class NoticeOutboxProcessorsOverAxonServerTest {
    companion object {
        @Container
        @JvmStatic
        val axonServer = AxonServerContainer().withDcbContext(true)

        @DynamicPropertySource
        @JvmStatic
        fun axonServerProperties(registry: DynamicPropertyRegistry) {
            registry.add("axon.axonserver.servers") { axonServer.axonServerAddress }
        }
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

    private fun assertDedicated(
        processor: String,
        outbox: Class<*>,
        event: Class<*>,
    ) {
        val beanName = context.getBeanNamesForType(outbox).single()
        val all = processors()
        val found = assertNotNull(all[processor], "processor $processor is not registered; processors: ${all.keys}")
        assertIs<PooledStreamingEventProcessor>(found, "$processor is not pooled-streaming")

        val components = componentsOn(processor)
        assertEquals(1, components.size, "$processor should carry only the outbox, has: ${components.keys}")
        val (name, component) = components.entries.single()
        assertTrue(name.contains("[$beanName]"), "the one component on $processor is not the outbox bean $beanName: $name")
        assertEquals(setOf(QualifiedName(event)), component.supportedEvents(), "the outbox handles exactly its notice event")

        val elsewhere =
            all.keys
                .filter { it != processor }
                .filter { other -> componentsOn(other).keys.any { it.contains("[$beanName]") } }
        assertTrue(elsewhere.isEmpty(), "outbox $beanName is also on: $elsewhere")
        assertTrue(all.keys.none { it == outbox.packageName }, "a package-named processor exists for ${outbox.packageName}")
    }

    @Test
    fun `INV-OVN-4 - the overdue notice outbox is alone on its own pooled-streaming processor in the running app`() {
        assertDedicated(SendOverdueNoticeConfiguration.OUTBOX_PROCESSOR, OverdueNoticeOutbox::class.java, OverdueNoticeSent::class.java)
    }

    @Test
    fun `INV-PKN-4 - the pickup notice outbox is alone on its own pooled-streaming processor in the running app`() {
        assertDedicated(SendPickupNoticeConfiguration.OUTBOX_PROCESSOR, PickupNoticeOutbox::class.java, PickupNoticeSent::class.java)
    }
}
