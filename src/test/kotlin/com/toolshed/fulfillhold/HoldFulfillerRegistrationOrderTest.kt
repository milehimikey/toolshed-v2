package com.toolshed.fulfillhold

import com.toolshed.axon.ReactionDispatcher
import com.toolshed.holdstofulfill.HoldsToFulfillProjection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.time.Clock

/**
 * Amendment 11: in the running app the Axon Spring extension hands a processor its handlers in bean registration order, so
 * the Hold Fulfiller must register after the Holds To Fulfill projection or it would read the list before the waking
 * event is applied.
 */
class HoldFulfillerRegistrationOrderTest {
    @Test
    fun `the Hold Fulfiller is registered after the Holds To Fulfill projection in the Spring context`() {
        AnnotationConfigApplicationContext().use { context ->
            context.registerBean(Clock::class.java, java.util.function.Supplier { Clock.systemUTC() })
            context.registerBean(
                ReactionDispatcher::class.java,
                java.util.function.Supplier { Mockito.mock(ReactionDispatcher::class.java) },
            )
            context.scan("com.toolshed.holdstofulfill", "com.toolshed.fulfillhold")
            context.refresh()

            val names = context.beanDefinitionNames.toList()
            val projection = names.indexOf(context.getBeanNamesForType(HoldsToFulfillProjection::class.java).single())
            val fulfiller = names.indexOf(context.getBeanNamesForType(HoldFulfillerHandler::class.java).single())
            assertThat(projection).isGreaterThanOrEqualTo(0)
            assertThat(fulfiller).isGreaterThan(projection)
        }
    }
}
