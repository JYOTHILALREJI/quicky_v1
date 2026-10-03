package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.AppContent
import com.example.model.OnboardingDraft
import com.example.ui.components.CassyGradientButton
import com.example.ui.components.cassyCinematicBrush
import com.example.ui.theme.*

/**
 * ============================================================
 *  ONBOARDING — Welcome + four progressive profile stages
 *  (Auth & Onboarding PRD)
 * ============================================================
 *
 *  Step 0  Welcome & 18+ eligibility confirmation
 *  Stage 1 The basics: full name, date of birth, gender
 *  Stage 2 Personality: bio, interests, interested in, looking for
 *  Stage 3 Background: qualification, hobbies, body, city
 *  Stage 4 Photos: up to 3, at least one face-validated
 *
 *  Every stage is validated before the Next button unlocks, and the
 *  draft is pushed to the ViewModel (which persists it) whenever the
 *  stage advances — so an interrupted onboarding resumes exactly
 *  where it stopped.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    draft: OnboardingDraft,
    interestCatalog: List<String>,
    hobbyCatalog: List<String>,
    isUploading: Boolean,
    isProcessingPhoto: Boolean,
    isLocating: Boolean,
    error: String?,
    onSaveDraft: (OnboardingDraft) -> Unit,
    onStageChanged: (Int) -> Unit,
    onAddPhoto: (Uri) -> Unit,
    onRemovePhoto: (String) -> Unit,
    onCaptureLocation: () -> Unit,
    onComplete: () -> Unit
) {
    // Resume directly into the saved stage; a fresh draft starts at the
    // welcome step.
    var stage by remember { mutableIntStateOf(if (draft.step > 1) draft.step else 0) }
    var isAgeConfirmed by remember { mutableStateOf(false) }

    fun goToStage(next: Int) {
        stage = next.coerceIn(0, 4)
        onStageChanged(stage)
    }

    val stageValid = when (stage) {
        0 -> isAgeConfirmed
        1 -> draft.isStage1Valid
        2 -> draft.isStage2Valid
        3 -> draft.isStage3Valid
        else -> draft.isStage4Valid
    }

    // System back moves back one stage (disabled on the welcome step).
    BackHandler(enabled = stage > 0) { goToStage(stage - 1) }

    // The onboarding flow always renders in the light Cassy theme,
    // independent of the in-app light/dark mode setting.
    QuickyTheme(darkTheme = false) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Cassy cinematic full-bleed gradient (PRD §5.4): a warm
                // ivory-to-blush wash that deepens towards the bottom.
                .background(cassyCinematicBrush())
        ) {
        if (stage == 0) {
            WelcomeStage(
                isAgeConfirmed = isAgeConfirmed,
                onAgeConfirmed = { isAgeConfirmed = it },
                onGetStarted = { goToStage(1) }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                OnboardingHeader(
                    stage = stage,
                    onBack = { goToStage(stage - 1) }
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (stage) {
                        1 -> StageBasics(draft = draft, onSave = onSaveDraft)
                        2 -> StagePersonality(
                            draft = draft,
                            interestCatalog = interestCatalog,
                            onSave = onSaveDraft
                        )
                        3 -> StageBackground(
                            draft = draft,
                            hobbyCatalog = hobbyCatalog,
                            isLocating = isLocating,
                            onCaptureLocation = onCaptureLocation,
                            onSave = onSaveDraft
                        )
                        else -> StagePhotos(
                            draft = draft,
                            isProcessingPhoto = isProcessingPhoto,
                            onAddPhoto = onAddPhoto,
                            onRemovePhoto = onRemovePhoto
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                OnboardingFooter(
                    stage = stage,
                    enabled = stageValid && !isUploading,
                    isUploading = isUploading,
                    error = error,
                    onNext = {
                        if (stage < 4) goToStage(stage + 1) else onComplete()
                    }
                )
            }
        }
    }
    }
}

// ================================================================
// STEP 0 — WELCOME & 18+ ELIGIBILITY
// ================================================================

@Composable
private fun WelcomeStage(
    isAgeConfirmed: Boolean,
    onAgeConfirmed: (Boolean) -> Unit,
    onGetStarted: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.img_onboarding_hero),
            contentDescription = "Welcome to Quicky",
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            LightBg.copy(alpha = 0.85f),
                            LightBg,
                            LightBg
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
                .testTag("onboarding_welcome"),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.quicky_logo),
                contentDescription = "Quicky Logo",
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Quicky",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = LightTextPrimary
            )

            Text(
                text = "Fast, fun dating with interactive Truth or Dare chat games, deep personality matching, and spontaneous connections.",
                style = MaterialTheme.typography.bodyMedium,
                color = LightTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = LightSurface,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isAgeConfirmed) QuickyPink.copy(alpha = 0.5f) else Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Checkbox(
                        checked = isAgeConfirmed,
                        onCheckedChange = onAgeConfirmed,
                        colors = CheckboxDefaults.colors(
                            checkedColor = QuickyPink,
                            checkmarkColor = Color.White
                        ),
                        modifier = Modifier.testTag("onboarding_age_checkbox")
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                tint = QuickyGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Adult-Only Platform (18+)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = LightTextPrimary
                            )
                        }

                        Text(
                            text = "I certify that I am at least 18 years of age and agree to the Terms of Service & Community Guidelines.",
                            style = MaterialTheme.typography.bodySmall,
                            color = LightTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Cassy primary CTA — gradient pill (PRD §5.4)
            CassyGradientButton(
                onClick = onGetStarted,
                enabled = isAgeConfirmed,
                text = "Get Started",
                modifier = Modifier.testTag("onboarding_continue_button")
            )
        }
    }
}

// ================================================================
// HEADER / FOOTER CHROME
// ================================================================

@Composable
private fun OnboardingHeader(stage: Int, onBack: () -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(40.dp)
                .testTag("onboarding_back_button")
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = LightTextPrimary
            )
        }
        Column {
            Text(
                text = when (stage) {
                    1 -> "Let's start with the basics"
                    2 -> "Show off your personality"
                    3 -> "A bit of background"
                    else -> "Add your photos"
                },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = LightTextPrimary
            )
            Text(
                text = "Step $stage of 4",
                style = MaterialTheme.typography.labelMedium,
                color = LightTextSecondary
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        // Cassy progress ring (PRD §5.4) replaces the flat linear bar —
        // a champagne-track ring with a rosewood gradient sweep.
        CassyProgressRing(
            progress = stage / 4f,
            label = "$stage/4"
        )
    }
    Spacer(modifier = Modifier.height(14.dp))
}

/**
 * Cassy onboarding progress ring — gradient sweep over a champagne track
 * with the step counter in the center.
 */
@Composable
private fun CassyProgressRing(progress: Float, label: String) {
    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 420),
        label = "onboarding_ring"
    )
    Box(contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(44.dp)) {
            val strokePx = 4.5.dp.toPx()
            val inset = strokePx / 2 + 1f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            // Champagne track
            drawArc(
                color = CassyAccent.copy(alpha = 0.35f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            // Rosewood sweep
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(CassyPrimary, CassyPrimaryGradientEnd)
                ),
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = LightTextSecondary
        )
    }
}

@Composable
private fun OnboardingFooter(
    stage: Int,
    enabled: Boolean,
    isUploading: Boolean,
    error: String?,
    onNext: () -> Unit
) {
    Surface(color = Color.Transparent) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
            if (error != null) {
                Text(
                    text = error,
                    color = ActionPass,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            // Cassy primary CTA — rosewood→blush gradient pill (PRD §5.4)
            // with the upload spinner built in.
            CassyGradientButton(
                onClick = onNext,
                enabled = enabled,
                isLoading = isUploading,
                text = if (stage == 4) "Finish & Enter Quicky" else "Continue",
                modifier = Modifier.testTag("onboarding_next_button")
            )
        }
    }
}

// ================================================================
// STAGE 1 — THE BASICS
// ================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StageBasics(
    draft: OnboardingDraft,
    onSave: (OnboardingDraft) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }

    SectionLabel("FULL NAME")
    OutlinedTextField(
        value = draft.fullName,
        onValueChange = { onSave(draft.copy(fullName = it.take(60))) },
        placeholder = { Text("e.g. Jyothi Lal") },
        singleLine = true,
        colors = lightFieldColors(),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_name_field")
    )

    SectionLabel("DATE OF BIRTH")
    Surface(
        onClick = { showDatePicker = true },
        shape = RoundedCornerShape(16.dp),
        color = LightSurface,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_dob_field")
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = draft.dateOfBirthEpochDay?.let {
                        java.time.LocalDate.ofEpochDay(it).let { d ->
                            "%02d %s %d".format(d.dayOfMonth, d.month.name.lowercase().replaceFirstChar { c -> c.uppercase() }, d.year)
                        }
                    } ?: "Select your date of birth",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (draft.dateOfBirthEpochDay != null) LightTextPrimary else LightTextSecondary
                )
                Text(
                    text = "Your age is calculated automatically and never shown exactly",
                    style = MaterialTheme.typography.labelSmall,
                    color = LightTextMuted
                )
            }
            Text(
                text = draft.calculatedAge?.let { "$it yrs" } ?: "",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = QuickyGold
            )
        }
    }

    SectionLabel("GENDER")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AppContent.genderOptions.forEach { option ->
            FilterChip(
                selected = draft.gender == option,
                onClick = { onSave(draft.copy(gender = if (draft.gender == option) "" else option)) },
                label = { Text(option) },
                colors = chipColors(),
                modifier = Modifier.testTag("onboarding_gender_chip")
            )
        }
    }

    if (draft.gender == "Other") {
        OutlinedTextField(
            value = draft.customGender,
            onValueChange = { onSave(draft.copy(customGender = it.take(30))) },
            placeholder = { Text("How do you identify?") },
            singleLine = true,
            colors = lightFieldColors(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_custom_gender_field")
        )
    }

    // calculatedAge has a custom getter — capture it in a local val so Kotlin can smart-cast.
    val calculatedAge = draft.calculatedAge
    if (calculatedAge != null && calculatedAge < 18) {
        InfoBanner(
            icon = Icons.Filled.WarningAmber,
            tint = ActionPass,
            text = "You must be at least 18 years old to use Quicky."
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = draft.dateOfBirthEpochDay?.let { it * 86_400_000L }
                ?: (System.currentTimeMillis() - 18L * 365L * 86_400_000L)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onSave(draft.copy(dateOfBirthEpochDay = millis / 86_400_000L))
                        }
                        showDatePicker = false
                    }
                ) { Text("OK", color = QuickyPink) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = LightTextSecondary)
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

// ================================================================
// STAGE 2 — PERSONALITY
// ================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StagePersonality(
    draft: OnboardingDraft,
    interestCatalog: List<String>,
    onSave: (OnboardingDraft) -> Unit
) {
    var customInterest by remember { mutableStateOf("") }

    SectionLabel("ABOUT YOU  ·  ${draft.bio.trim().length}/10 min")
    OutlinedTextField(
        value = draft.bio,
        onValueChange = { if (it.length <= 280) onSave(draft.copy(bio = it)) },
        placeholder = { Text("Tell people what makes you, you…") },
        minLines = 3,
        colors = lightFieldColors(),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_bio_field")
    )

    SectionLabel("INTERESTS  ·  ${draft.interests.size}/${AppContent.MAX_INTERESTS}")
    Text(
        text = "Pick the things you genuinely love — these power your matches.",
        style = MaterialTheme.typography.labelSmall,
        color = LightTextMuted
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        interestCatalog.forEach { interest ->
            FilterChip(
                selected = interest in draft.interests,
                onClick = {
                    val current = draft.interests
                    val next = if (interest in current) current - interest
                    else if (current.size >= AppContent.MAX_INTERESTS) current
                    else current + interest
                    if (next != current) onSave(draft.copy(interests = next))
                },
                label = { Text(interest) },
                colors = chipColors(),
                modifier = Modifier.testTag("onboarding_interest_chip")
            )
        }
    }

    // Custom interest input — becomes a chip on Add.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = customInterest,
            onValueChange = {
                if (it.length <= 30) customInterest = it
            },
            placeholder = { Text("Add your own…") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Done
            ),
            colors = lightFieldColors(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .weight(1f)
                .testTag("onboarding_custom_interest_field")
        )
        FilledTonalIconButton(
            onClick = {
                val name = customInterest.trim()
                if (name.isNotEmpty() && name !in draft.interests &&
                    draft.interests.size < AppContent.MAX_INTERESTS
                ) {
                    onSave(draft.copy(interests = draft.interests + name))
                }
                customInterest = ""
            },
            enabled = customInterest.isNotBlank() &&
                    draft.interests.size < AppContent.MAX_INTERESTS,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = QuickyPurple,
                contentColor = Color.White
            ),
            modifier = Modifier.testTag("onboarding_add_custom_interest_button")
        ) {
            Icon(Icons.Filled.Verified, contentDescription = "Add custom interest")
        }
    }
    if (draft.interests.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            draft.interests.forEach { interest ->
                InputChip(
                    selected = true,
                    onClick = { onSave(draft.copy(interests = draft.interests - interest)) },
                    label = { Text(interest) },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove $interest",
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = InputChipDefaults.inputChipColors(
                        containerColor = QuickyPurple.copy(alpha = 0.12f),
                        labelColor = QuickyPurple
                    )
                )
            }
        }
    }

    SectionLabel("INTERESTED IN")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AppContent.interestedInOptions.forEach { option ->
            FilterChip(
                selected = draft.interestedIn == option,
                onClick = { onSave(draft.copy(interestedIn = if (draft.interestedIn == option) "" else option)) },
                label = { Text(option) },
                colors = chipColors(),
                modifier = Modifier.testTag("onboarding_interested_in_chip")
            )
        }
    }

    SectionLabel("LOOKING FOR  ·  pick any")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AppContent.lookingForOptions.forEach { option ->
            FilterChip(
                selected = option in draft.lookingFor,
                onClick = {
                    val next = if (option in draft.lookingFor) draft.lookingFor - option
                    else draft.lookingFor + option
                    onSave(draft.copy(lookingFor = next))
                },
                label = { Text(option) },
                colors = chipColors(),
                modifier = Modifier.testTag("onboarding_looking_for_chip")
            )
        }
    }
}

// ================================================================
// STAGE 3 — BACKGROUND
// ================================================================

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun StageBackground(
    draft: OnboardingDraft,
    hobbyCatalog: List<String>,
    isLocating: Boolean,
    onCaptureLocation: () -> Unit,
    onSave: (OnboardingDraft) -> Unit
) {
    var heightInput by remember(draft.heightCm) {
        mutableStateOf(draft.heightCm?.toString().orEmpty())
    }
    var weightInput by remember(draft.weightKg) {
        mutableStateOf(draft.weightKg?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }.orEmpty())
    }

    SectionLabel("HIGHEST QUALIFICATION")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AppContent.qualificationOptions.forEach { option ->
            FilterChip(
                selected = draft.qualification == option,
                onClick = { onSave(draft.copy(qualification = if (draft.qualification == option) "" else option)) },
                label = { Text(option) },
                colors = chipColors(),
                modifier = Modifier.testTag("onboarding_qualification_chip")
            )
        }
    }

    SectionLabel("OCCUPATION")
    OutlinedTextField(
        value = draft.occupation,
        onValueChange = { onSave(draft.copy(occupation = it.take(40))) },
        placeholder = { Text("e.g. Software Engineer") },
        singleLine = true,
        colors = lightFieldColors(),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_occupation_field")
    )

    SectionLabel("HOBBIES  ·  pick any")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        hobbyCatalog.forEach { hobby ->
            FilterChip(
                selected = hobby in draft.hobbies,
                onClick = {
                    val next = if (hobby in draft.hobbies) draft.hobbies - hobby
                    else draft.hobbies + hobby
                    onSave(draft.copy(hobbies = next))
                },
                label = { Text(hobby) },
                colors = chipColors(),
                modifier = Modifier.testTag("onboarding_hobby_chip")
            )
        }
    }

    // -------------------------------------------------------------
    // LANGUAGES YOU SPEAK (change request #6) — powers the language
    // discovery filter; at least one required to continue.
    // -------------------------------------------------------------
    SectionLabel("LANGUAGES YOU SPEAK  ·  pick at least one")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AppContent.languageCatalog.forEach { language ->
            FilterChip(
                selected = language in draft.languages,
                onClick = {
                    val next = if (language in draft.languages) draft.languages - language
                    else draft.languages + language
                    onSave(draft.copy(languages = next))
                },
                label = { Text(language) },
                colors = chipColors(),
                modifier = Modifier.testTag("onboarding_language_chip")
            )
        }
    }

    SectionLabel("BODY  ·  optional, visibility controlled later")
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = heightInput,
            onValueChange = { raw ->
                heightInput = raw.filter { it.isDigit() }.take(3)
                onSave(draft.copy(heightCm = heightInput.toIntOrNull()?.takeIf { it in 90..250 }))
            },
            label = { Text("Height (cm)") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            ),
            colors = lightFieldColors(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .weight(1f)
                .testTag("onboarding_height_field")
        )
        OutlinedTextField(
            value = weightInput,
            onValueChange = { raw ->
                weightInput = raw.filter { it.isDigit() || it == '.' }.take(6)
                onSave(
                    draft.copy(
                        weightKg = weightInput.toFloatOrNull()?.takeIf { it in 25f..350f }
                    )
                )
            },
            label = { Text("Weight (kg)") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done
            ),
            colors = lightFieldColors(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .weight(1f)
                .testTag("onboarding_weight_field")
        )
    }

    // -------------------------------------------------------------
    // LOCATION / STREET NAME — tapping the field (or the locator
    // icon) asks for the location permission and auto-fills from the
    // user's current GPS position. The coordinates power the
    // distance-based discovery filter.
    // -------------------------------------------------------------
    val context = LocalContext.current
    val locationFieldInteractions = remember { MutableInteractionSource() }
    var locationPermissionAsked by rememberSaveable { mutableStateOf(false) }

    fun hasLocationPermission(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) onCaptureLocation()
    }

    fun requestCurrentLocation() {
        if (hasLocationPermission()) onCaptureLocation()
        else locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // First tap on the field itself asks for permission + captures the
    // location once; after that the field behaves as a normal input.
    LaunchedEffect(locationFieldInteractions) {
        locationFieldInteractions.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press && !locationPermissionAsked) {
                locationPermissionAsked = true
                requestCurrentLocation()
            }
        }
    }

    val locationCaptured = draft.latitude != null && draft.longitude != null
    SectionLabel("LOCATION / STREET NAME")
    OutlinedTextField(
        value = draft.city,
        onValueChange = { onSave(draft.copy(city = it.take(60))) },
        placeholder = { Text("Street / area, city") },
        singleLine = true,
        interactionSource = locationFieldInteractions,
        trailingIcon = {
            if (isLocating) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = QuickyPink,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                IconButton(
                    onClick = { requestCurrentLocation() },
                    modifier = Modifier.testTag("onboarding_locate_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = "Use my current location",
                        tint = QuickyPurple
                    )
                }
            }
        },
        supportingText = {
            Text(
                text = if (locationCaptured) "Location captured — used for distance-based matching."
                else "Tap to auto-fill from your current location.",
                style = MaterialTheme.typography.labelSmall,
                color = if (locationCaptured) ActionLike else LightTextMuted
            )
        },
        colors = lightFieldColors(),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_location_field")
    )
}

// ================================================================
// STAGE 4 — PHOTOS (max 3, at least one face-validated)
// ================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StagePhotos(
    draft: OnboardingDraft,
    isProcessingPhoto: Boolean,
    onAddPhoto: (Uri) -> Unit,
    onRemovePhoto: (String) -> Unit
) {
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onAddPhoto(uri)
    }

    SectionLabel("PHOTOS  ·  ${draft.photos.size}/3")
    Text(
        text = "Add up to three photos. At least one must have a clearly visible face — this keeps Quicky authentic.",
        style = MaterialTheme.typography.labelSmall,
        color = LightTextMuted
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_photos_row")
    ) {
        draft.photos.take(3).forEach { photo ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = "Your photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                    IconButton(
                        onClick = { onRemovePhoto(photo.uri) },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.65f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(28.dp)
                            .testTag("onboarding_photo_remove_button")
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Remove photo",
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isProcessingPhoto && photo == draft.photos.lastOrNull() && !photo.faceValidated) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = QuickyPink,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Checking…", style = MaterialTheme.typography.labelSmall, color = LightTextSecondary)
                    } else if (photo.faceValidated) {
                        Icon(
                            Icons.Filled.Verified,
                            contentDescription = null,
                            tint = ActionLike,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Face ✓", style = MaterialTheme.typography.labelSmall, color = ActionLike)
                    } else {
                        Icon(
                            Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = QuickyGold,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("No face", style = MaterialTheme.typography.labelSmall, color = QuickyGold)
                    }
                }
            }
        }

        if (draft.photos.size < 3) {
            Surface(
                onClick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                shape = RoundedCornerShape(18.dp),
                color = LightSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, LightBorder),
                modifier = Modifier
                    .size(96.dp)
                    .testTag("onboarding_photo_add_tile")
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.AddAPhoto,
                        contentDescription = "Add photo",
                        tint = QuickyPink,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Add Photo",
                        style = MaterialTheme.typography.labelSmall,
                        color = LightTextSecondary
                    )
                }
            }
        }
    }

    if (draft.photos.isNotEmpty() && draft.photos.none { it.faceValidated }) {
        InfoBanner(
            icon = Icons.Filled.WarningAmber,
            tint = QuickyGold,
            text = "None of your photos shows a clearly visible face. Add one to continue — face checks run automatically on your device."
        )
    }

    Text(
        text = "Your date of birth stays private — only your age is shown to others.",
        style = MaterialTheme.typography.labelSmall,
        color = LightTextMuted
    )
}

// ================================================================
// SHARED SMALL PIECES
// ================================================================

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        ),
        color = LightTextSecondary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun InfoBanner(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, text: String) {
    Surface(
        color = tint.copy(alpha = 0.10f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = tint
            )
        }
    }
}

@Composable
private fun lightFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = LightTextPrimary,
    unfocusedTextColor = LightTextPrimary,
    focusedBorderColor = QuickyPink,
    unfocusedBorderColor = LightBorder,
    focusedContainerColor = LightSurface,
    unfocusedContainerColor = LightSurface,
    focusedLabelColor = QuickyPink,
    unfocusedLabelColor = LightTextSecondary,
    focusedPlaceholderColor = LightTextMuted,
    unfocusedPlaceholderColor = LightTextMuted,
    cursorColor = QuickyPink
)

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = LightSurfaceElevated,
    labelColor = LightTextSecondary,
    selectedContainerColor = QuickyPink,
    selectedLabelColor = Color.White
)
