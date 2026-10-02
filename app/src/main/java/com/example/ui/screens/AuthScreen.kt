package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*

/**
 * ============================================================
 *  AUTHENTICATION SCREEN (Auth & Onboarding PRD)
 * ============================================================
 * Email + password login / sign-up against Supabase Auth,
 * password recovery and Google OAuth entry.
 */
@Composable
fun AuthScreen(
    isLoading: Boolean,
    error: String?,
    notice: String?,
    onSignUp: (email: String, password: String) -> Unit,
    onSignIn: (email: String, password: String) -> Unit,
    onForgotPassword: (email: String) -> Unit,
    onGoogleSignIn: () -> Unit
) {
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showForgotPassword by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val emailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val passwordValid = password.length >= 6
    val confirmValid = !isSignUp || (confirmPassword == password && passwordValid)
    val formValid = emailValid && passwordValid && confirmValid

    fun submit() {
        localError = null
        if (isSignUp) onSignUp(email.trim(), password) else onSignIn(email.trim(), password)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---- Branding ----
            Image(
                painter = painterResource(id = R.drawable.quicky_logo),
                contentDescription = "Quicky Logo",
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Quicky",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = Color.White
            )

            Text(
                text = "Fast, fun dating with interactive games and deep personality matching.",
                style = MaterialTheme.typography.bodySmall,
                color = DarkTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, start = 16.dp, end = 16.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ---- Log In / Sign Up segmented toggle ----
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = DarkSurface
            ) {
                Row(modifier = Modifier.padding(5.dp)) {
                    AuthModeTab(
                        label = "Log In",
                        selected = !isSignUp,
                        onClick = {
                            isSignUp = false
                            localError = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                    AuthModeTab(
                        label = "Sign Up",
                        selected = isSignUp,
                        onClick = {
                            isSignUp = true
                            localError = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ---- Email ----
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    localError = null
                },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                colors = authFieldColors(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_email_field")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ---- Password ----
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    localError = null
                },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (isSignUp) ImeAction.Next else ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (formValid && !isLoading) submit() }
                ),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                            tint = DarkTextSecondary
                        )
                    }
                },
                supportingText = {
                    if (isSignUp) {
                        Text(
                            "At least 6 characters",
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkTextSecondary
                        )
                    }
                },
                colors = authFieldColors(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_password_field")
            )

            // ---- Confirm password (sign-up only) ----
            if (isSignUp) {
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        localError = null
                    },
                    label = { Text("Confirm Password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (formValid && !isLoading) submit() }
                    ),
                    isError = confirmPassword.isNotEmpty() && confirmPassword != password,
                    supportingText = {
                        if (confirmPassword.isNotEmpty() && confirmPassword != password) {
                            Text("Passwords don't match", color = ActionPass)
                        }
                    },
                    colors = authFieldColors(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_confirm_password_field")
                )
            }

            // ---- Inline messages ----
            val shownError = localError ?: error
            if (shownError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = ActionPass.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = shownError,
                        color = ActionPass,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            if (notice != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = ActionLike.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = notice,
                        color = ActionLike,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // ---- Primary action ----
            Button(
                onClick = { submit() },
                enabled = formValid && !isLoading,
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = QuickyPink,
                    disabledContainerColor = DarkSurfaceHighlight
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("auth_submit_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text(
                        text = if (isSignUp) "Create Account" else "Log In",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // ---- Forgot password ----
            TextButton(
                onClick = { showForgotPassword = !showForgotPassword },
                modifier = Modifier.testTag("auth_forgot_password_button")
            ) {
                Text("Forgot password?", color = QuickyCyan)
            }

            if (showForgotPassword) {
                var resetEmail by remember { mutableStateOf(email) }
                var resetSent by remember { mutableStateOf(false) }
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "We'll email you a secure reset link",
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkTextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = resetEmail,
                            onValueChange = { resetEmail = it },
                            label = { Text("Your email") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Done
                            ),
                            colors = authFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                onForgotPassword(resetEmail.trim())
                                resetSent = true
                            },
                            enabled = android.util.Patterns.EMAIL_ADDRESS.matcher(resetEmail.trim()).matches(),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = QuickyPurple),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (resetSent) "Resend Email" else "Send Reset Link")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ---- Divider + Google OAuth ----
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(
                    color = DarkBorder,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "  or  ",
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkTextSecondary
                )
                HorizontalDivider(
                    color = DarkBorder,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            OutlinedButton(
                onClick = onGoogleSignIn,
                enabled = !isLoading,
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White,
                    containerColor = DarkSurface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("auth_google_button")
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "G",
                        color = Color(0xFF4285F4),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Continue with Google",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "By continuing you agree to our Terms of Service and Privacy Policy. You must be 18 or older.",
                style = MaterialTheme.typography.labelSmall,
                color = DarkTextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

/** Selected/unselected pill for the Log In ↔ Sign Up toggle. */
@Composable
private fun AuthModeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) QuickyPink else Color.Transparent,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 10.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) Color.White else DarkTextSecondary
            )
        }
    }
}

/** Field colors tuned for the dark auth surface. */
@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = QuickyPink,
    unfocusedBorderColor = DarkBorder,
    focusedContainerColor = DarkSurface,
    unfocusedContainerColor = DarkSurface,
    focusedLabelColor = QuickyPink,
    unfocusedLabelColor = DarkTextSecondary,
    cursorColor = QuickyPink
)

/**
 * Cold-start session-restore screen shown while the persisted Supabase
 * session is being validated/refreshed (usually well under a second).
 */
@Composable
fun AuthCheckingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.quicky_logo),
                contentDescription = "Quicky Logo",
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(20.dp))
            CircularProgressIndicator(color = QuickyPink, strokeWidth = 3.dp)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "Signing you in…",
                style = MaterialTheme.typography.bodySmall,
                color = DarkTextSecondary
            )
        }
    }
}
