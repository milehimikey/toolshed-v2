package com.toolshed.events

import com.toolshed.addtool.ToolAdded
import com.toolshed.axon.HoldId
import com.toolshed.axon.LoanId
import com.toolshed.axon.NeighborId
import com.toolshed.axon.ToolId
import com.toolshed.checkouttool.ToolCheckedOut
import com.toolshed.flagneighbor.NeighborFlagged
import com.toolshed.placehold.HoldPlaced
import com.toolshed.registerneighbor.NeighborRegistered
import org.axonframework.eventsourcing.eventstore.AnnotationBasedTagResolver
import org.axonframework.messaging.core.MessageType
import org.axonframework.messaging.eventhandling.GenericEventMessage
import org.axonframework.messaging.eventstreaming.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

/** The DCB tag convention: each id field is tagged by its field name, so a key means the same thing on every event. */
class TagConventionTest {
    private val resolver = AnnotationBasedTagResolver()
    private val now = Instant.parse("2026-10-06T04:00:00Z")
    private val today = LocalDate.of(2026, 10, 6)

    private fun tagsOf(payload: Any): Set<Tag> = resolver.resolve(GenericEventMessage(MessageType(payload::class.java), payload))

    @Test
    fun `a tool id is tagged toolId`() {
        val tool = ToolId(UUID.randomUUID())
        assertEquals(setOf(Tag.of("toolId", tool.value.toString())), tagsOf(ToolAdded(tool, "Drill", null, 7, now)))
    }

    @Test
    fun `a neighbor id is tagged neighborId`() {
        val neighbor = NeighborId(UUID.randomUUID())
        assertEquals(
            setOf(Tag.of("neighborId", neighbor.value.toString())),
            tagsOf(NeighborRegistered(neighbor, "Ana", "ana@example.com", now)),
        )
    }

    @Test
    fun `a loan id is tagged loanId and the tool and neighbor ids alongside it`() {
        val loan = LoanId(UUID.randomUUID())
        val tool = ToolId(UUID.randomUUID())
        val neighbor = NeighborId(UUID.randomUUID())
        assertEquals(
            setOf(
                Tag.of("loanId", loan.value.toString()),
                Tag.of("toolId", tool.value.toString()),
                Tag.of("neighborId", neighbor.value.toString()),
            ),
            tagsOf(ToolCheckedOut(loan, tool, neighbor, 3, today, now)),
        )
    }

    @Test
    fun `a hold id is tagged holdId, and the tool and neighbor ids alongside it`() {
        val hold = HoldId(UUID.randomUUID())
        val tool = ToolId(UUID.randomUUID())
        val neighbor = NeighborId(UUID.randomUUID())
        assertEquals(
            setOf(
                Tag.of("holdId", hold.value.toString()),
                Tag.of("toolId", tool.value.toString()),
                Tag.of("neighborId", neighbor.value.toString()),
            ),
            tagsOf(HoldPlaced(hold, tool, neighbor, now)),
        )
    }

    @Test
    fun `NeighborFlagged tags the neighbor and leaves the loanIds list untagged`() {
        val neighbor = NeighborId(UUID.randomUUID())
        val loans = listOf(LoanId(UUID.randomUUID()), LoanId(UUID.randomUUID()), LoanId(UUID.randomUUID()))
        // Axon tags each list element, but its value would be the element's toString (`LoanId(value=...)`),
        // not the UUID that scalar loanId tags carry. So the list stays untagged rather than mixing formats.
        assertEquals(setOf(Tag.of("neighborId", neighbor.value.toString())), tagsOf(NeighborFlagged(neighbor, loans, now)))
    }
}
