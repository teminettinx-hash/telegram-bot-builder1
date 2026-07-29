package com.botbuilder.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for "is the bot actually working right now".
 * BotPollingService pushes updates here as its real lifecycle progresses;
 * any screen (MainActivity, etc.) can collect [state] to reflect the true
 * status instead of guessing from button clicks.
 */
object BotStatusBus {

    sealed class State {
        /** Service isn't running at all. */
        object Stopped : State()
        /** Service just started, first poll hasn't succeeded yet. */
        object Starting : State()
        /** At least one successful poll against Telegram has completed. */
        data class Running(val since: Long) : State()
        /** Service is up but the last poll(s) failed (bad token, no internet, etc). */
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Stopped)
    val state: StateFlow<State> = _state.asStateFlow()

    fun update(newState: State) {
        _state.value = newState
    }
}
