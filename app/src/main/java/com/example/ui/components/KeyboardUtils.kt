package com.example.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController

/**
 * ============================================================================
 * GLOBAL KEYBOARD DISMISS — Quicky v2.1 §3.5
 *
 * Tapping anywhere on the screen (outside an input field) hides the
 * keyboard — not just the system back gesture. Apply this modifier to the
 * background / message-list container of every screen that shows a text
 * field (Discovery, Matches, 1:1 Chat, Club Chat, Ludo Arena, Profile,
 * Settings, Onboarding, filter & create sheets, …).
 *
 * The tap is observed WITHOUT consuming it for layout purposes (taps on
 * real interactive children still work because their own click handlers
 * run higher in the hit-test chain).
 * ============================================================================
 */
fun Modifier.dismissKeyboardOnTap(
    focusManager: FocusManager,
    keyboard: SoftwareKeyboardController?
): Modifier = pointerInput(focusManager, keyboard) {
    detectTapGestures(
        onTap = {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    )
}

/** Composable-scope convenience: resolves the focus + keyboard controllers itself. */
@Composable
fun Modifier.dismissKeyboardOnTap(): Modifier {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return this.then(
        Modifier.dismissKeyboardOnTap(focusManager, keyboard)
    )
}
