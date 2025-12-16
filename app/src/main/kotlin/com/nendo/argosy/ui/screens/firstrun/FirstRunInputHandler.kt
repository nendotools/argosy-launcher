package com.nendo.argosy.ui.screens.firstrun

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult

class FirstRunInputHandler(
    private val viewModel: FirstRunViewModel,
    private val onComplete: () -> Unit,
    private val onRequestPermission: () -> Unit,
    private val onChooseFolder: () -> Unit
) : InputHandler {

    override fun onUp(): InputResult {
        return if (viewModel.moveFocus(-1)) InputResult.HANDLED else InputResult.UNHANDLED
    }

    override fun onDown(): InputResult {
        return if (viewModel.moveFocus(1)) InputResult.HANDLED else InputResult.UNHANDLED
    }

    override fun onConfirm(): InputResult {
        val state = viewModel.uiState.value
        if (state.currentStep == FirstRunStep.COMPLETE) {
            viewModel.completeSetup()
            onComplete()
            return InputResult.HANDLED
        }
        viewModel.handleConfirm(onRequestPermission, onChooseFolder)
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        val state = viewModel.uiState.value
        if (state.currentStep == FirstRunStep.WELCOME) {
            return InputResult.UNHANDLED
        }
        viewModel.previousStep()
        return InputResult.HANDLED
    }
}
