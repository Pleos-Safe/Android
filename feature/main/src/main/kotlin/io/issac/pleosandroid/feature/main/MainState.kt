package io.issac.pleosandroid.feature.main

import io.issac.pleosandroid.core.ui.mvi.UiState

internal data class MainState(val canUseTabs: Boolean = false, val showJourneyResult: Boolean = false) : UiState
