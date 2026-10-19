package com.jarves.mh.runtime

import com.jarves.mh.model.AgentKind
import org.junit.Assert.*
import org.junit.Test

class MultiAgentPlanQueueTest {
    private val plan = listOf(
        MultiAgentStep(AgentKind.CLAUDE_CODE, "Plan"),
        MultiAgentStep(AgentKind.DEEPSEEK_HARNESS, "Implement"),
        MultiAgentStep(AgentKind.ANTIGRAVITY, "Review"),
    )

    @Test fun runsOrderedStepsOnlyAfterPreviousSuccess() {
        val queue = MultiAgentPlanQueue(plan)
        assertEquals("Plan", queue.begin()!!.prompt)
        assertEquals("Implement", queue.onTerminal(success = true)!!.prompt)
        assertEquals("Review", queue.onTerminal(success = true)!!.prompt)
        assertNull(queue.onTerminal(success = true))
        assertFalse(queue.active)
    }

    @Test fun failedStepStopsAllRemainingSteps() {
        val queue = MultiAgentPlanQueue(plan)
        queue.begin()
        assertNull(queue.onTerminal(success = false))
        assertFalse(queue.active)
    }

    @Test fun invalidAndOversizedPlansAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { MultiAgentPlanQueue(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { MultiAgentPlanQueue(plan + plan + plan) }
        assertThrows(IllegalArgumentException::class.java) {
            MultiAgentPlanQueue(listOf(MultiAgentStep(AgentKind.CLAUDE_CODE, " ")))
        }
    }

    @Test fun cancelNeverStartsFurtherSteps() {
        val queue = MultiAgentPlanQueue(plan)
        queue.begin()
        queue.cancel()
        assertNull(queue.onTerminal(success = true))
    }
}
