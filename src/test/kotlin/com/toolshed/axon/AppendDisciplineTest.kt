package com.toolshed.axon

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/** Architecture guard: only com.toolshed.axon may call EventAppender, so every append goes through decideAndAppend. */
class AppendDisciplineTest {
    @Test
    fun `no class outside com toolshed axon references EventAppender`() {
        val root = File("src/main/kotlin/com/toolshed")
        assertTrue(root.isDirectory, "source root not found: ${root.absolutePath}")
        val offenders =
            root
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filterNot { it.path.contains("/com/toolshed/axon/") }
                .filter { it.readText().contains("EventAppender") }
                .map { it.path }
                .toList()
        assertTrue(offenders.isEmpty(), "append outside decideAndAppend in: $offenders")
    }

    @Test
    fun `only com toolshed axon and com toolshed web use CommandGateway directly`() {
        // Slices and reactions dispatch through CommandDispatcher or ReactionDispatcher, never the gateway itself.
        val root = File("src/main/kotlin/com/toolshed")
        assertTrue(root.isDirectory, "source root not found: ${root.absolutePath}")
        val offenders =
            root
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filterNot { it.path.contains("/com/toolshed/axon/") || it.path.contains("/com/toolshed/web/") }
                .filter { it.readText().contains("CommandGateway") }
                .map { it.path }
                .toList()
        assertTrue(offenders.isEmpty(), "gateway used outside the dispatchers in: $offenders")
    }
}
