package com.carsense.core.base

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Base ViewModel that provides state management for MVI architecture.
 * @param S The state type
 * @param I The intent type
 */
abstract class BaseViewModel<S, I> : ViewModel() {
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<S> = _state.asStateFlow()

    /** Provides the initial state for this ViewModel */
    abstract fun initialState(): S

    /** Processes user intents that modify the state */
    abstract fun processIntent(intent: I)

    /** Updates the current state using a reducer function */
    protected fun updateState(reduce: (S) -> S) {
        _state.value = reduce(_state.value)
    }
}
