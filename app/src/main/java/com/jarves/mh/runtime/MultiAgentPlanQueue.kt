package com.jarves.mh.runtime

import com.jarves.mh.model.AgentKind

data class MultiAgentStep(val agent: AgentKind, val prompt: String)

/**
 * Serial step handoff. An approved plan never silently continues after a failed step.
 * The UI remains responsible for explicit confirmation and existing provider credentials.
 */
class MultiAgentPlanQueue(steps: List<MultiAgentStep>) {
    private val planned: List<MultiAgentStep>
    private var nextIndex = 0
    private var started = false
    var active = false
        private set

    init {
        require(steps.size in 1..6) { "Multi-agent plan must contain 1–6 steps" }
        require(steps.all { it.prompt.isNotBlank() && it.prompt.length <= 12_000 }) { "Invalid prompt" }
        require(steps.sumOf { it.prompt.length } <= 24_000) { "Plan exceeds context limit" }
        planned = steps.map { it.copy(prompt = it.prompt.trim()) }
    }

    fun begin(): MultiAgentStep? {
        check(!started) { "Plan already started" }
        started = true
        active = true
        return planned[nextIndex++]
    }

    fun onTerminal(success: Boolean): MultiAgentStep? {
        if (!active) return null
        if (!success || nextIndex >= planned.size) {
            active = false
            return null
        }
        return planned[nextIndex++]
    }

    fun cancel() { active = false }
}
