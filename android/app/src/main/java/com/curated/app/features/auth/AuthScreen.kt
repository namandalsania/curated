package com.curated.app.features.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.curated.app.R
import com.curated.app.core.legal.LegalLinks
import com.curated.app.designsystem.CuratedTheme
import com.curated.app.designsystem.Spacing
import com.curated.app.designsystem.components.PrimaryButton

/**
 * Sign in, sign up and password reset. One screen whose page changes, rather
 * than a nav graph: the reset pages are short detours that always come back.
 */
@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val state by viewModel.state.collectAsState()
    val autofill = LocalAutofillManager.current

    // Every page but sign in goes back a step; back on sign in leaves the app as usual.
    BackHandler(enabled = state.page != AuthPage.SIGN_IN) {
        viewModel.goTo(if (state.page == AuthPage.RESET_VERIFY) AuthPage.RESET_REQUEST else AuthPage.SIGN_IN)
    }

    Crossfade(targetState = state.page, label = "auth-page") { page ->
        when (page) {
            AuthPage.SIGN_IN -> SignInPage(
                state = state,
                onEmailChange = viewModel::setEmail,
                onSubmit = { password ->
                    autofill?.commit()
                    viewModel.signIn(password)
                },
                onForgotPassword = { viewModel.goTo(AuthPage.RESET_REQUEST) },
                onResendConfirmation = viewModel::resendConfirmation,
                onSwitchToSignUp = { viewModel.goTo(AuthPage.SIGN_UP) }
            )
            AuthPage.SIGN_UP -> SignUpPage(
                state = state,
                onEmailChange = viewModel::setEmail,
                onUsernameChange = viewModel::onUsernameChanged,
                onSubmit = { name, username, password ->
                    autofill?.commit()
                    viewModel.signUp(name, username, password)
                },
                onSwitchToSignIn = { viewModel.goTo(AuthPage.SIGN_IN) }
            )
            AuthPage.RESET_REQUEST -> ResetRequestPage(
                state = state,
                onEmailChange = viewModel::setEmail,
                onSendCode = viewModel::requestResetCode,
                onBack = { viewModel.goTo(AuthPage.SIGN_IN) }
            )
            AuthPage.RESET_VERIFY -> ResetVerifyPage(
                state = state,
                onConfirm = { code, password, confirm ->
                    autofill?.commit()
                    viewModel.confirmReset(code, password, confirm)
                },
                onResend = viewModel::resendResetCode,
                onBack = { viewModel.goTo(AuthPage.RESET_REQUEST) }
            )
        }
    }
}

// --- Pages -------------------------------------------------------------------

@Composable
private fun SignInPage(
    state: AuthUiState,
    onEmailChange: (String) -> Unit,
    onSubmit: (password: String) -> Unit,
    onForgotPassword: () -> Unit,
    onResendConfirmation: () -> Unit,
    onSwitchToSignUp: () -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current

    HeroLayout {
        EmailField(state.email, onEmailChange, imeAction = ImeAction.Next, onNext = { focus.moveFocus(FocusDirection.Down) })
        Column {
            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                contentType = ContentType.Password,
                imeAction = ImeAction.Done,
                onDone = { onSubmit(password) }
            )
            TextButton(onClick = onForgotPassword, modifier = Modifier.align(Alignment.End)) {
                Text("Forgot password?", style = MaterialTheme.typography.labelLarge)
            }
        }

        Messages(state)
        if (state.emailNotConfirmed) {
            TextButton(onClick = onResendConfirmation, enabled = !state.isSubmitting) {
                Text("Resend confirmation email")
            }
        }

        SubmitButton("Sign in", isLoading = state.isSubmitting, onClick = { onSubmit(password) })
        SwitchLine("New to Curated?", "Create an account", onSwitchToSignUp)
    }
}

@Composable
private fun SignUpPage(
    state: AuthUiState,
    onEmailChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onSubmit: (name: String, username: String, password: String) -> Unit,
    onSwitchToSignIn: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordTouched by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val next = { focus.moveFocus(FocusDirection.Down); Unit }

    if (state.awaitingConfirmation) {
        HeroLayout {
            Text(
                "Check your inbox",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "We sent a confirmation link to ${state.email.trim()}. Open it, then sign in - your profile will be ready.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SubmitButton("Back to sign in", isLoading = false, onClick = onSwitchToSignIn)
        }
        return
    }

    HeroLayout(compactHero = true) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Your name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { next() }),
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.PersonFullName }
        )
        UsernameField(
            value = username,
            status = state.usernameStatus,
            onValueChange = {
                username = it
                onUsernameChange(it)
            },
            onNext = next
        )
        EmailField(state.email, onEmailChange, imeAction = ImeAction.Next, onNext = next)
        PasswordField(
            value = password,
            onValueChange = {
                password = it
                passwordTouched = true
            },
            label = "Password",
            contentType = ContentType.NewPassword,
            imeAction = ImeAction.Done,
            onDone = { onSubmit(name, username, password) },
            supporting = AuthRules.passwordProblem(password)?.takeIf { passwordTouched }
                ?: "At least ${AuthRules.MIN_PASSWORD_LENGTH} characters.",
            isError = passwordTouched && password.isNotEmpty() && AuthRules.passwordProblem(password) != null
        )

        Messages(state)
        SubmitButton("Create account", isLoading = state.isSubmitting, onClick = { onSubmit(name, username, password) })
        AgreementLine()
        SwitchLine("Already have an account?", "Sign in", onSwitchToSignIn)
    }
}

@Composable
private fun ResetRequestPage(
    state: AuthUiState,
    onEmailChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onBack: () -> Unit
) {
    DetourLayout(
        title = "Reset your password",
        body = "Enter the email you signed up with. We'll send a ${AuthRules.CODE_LENGTH}-digit code to set a new password.",
        onBack = onBack
    ) {
        EmailField(state.email, onEmailChange, imeAction = ImeAction.Done, onDone = onSendCode)
        Messages(state)
        SubmitButton("Send code", isLoading = state.isSubmitting, onClick = onSendCode)
    }
}

@Composable
private fun ResetVerifyPage(
    state: AuthUiState,
    onConfirm: (code: String, password: String, confirm: String) -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit
) {
    var code by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val next = { focus.moveFocus(FocusDirection.Down); Unit }

    DetourLayout(
        title = "Enter your code",
        body = "Sent to ${state.email.trim()}. It can take a minute to arrive - check spam too.",
        onBack = onBack
    ) {
        if (!state.codeVerified) {
            OutlinedTextField(
                value = code,
                onValueChange = { raw -> code = raw.filter(Char::isDigit).take(AuthRules.CODE_LENGTH) },
                label = { Text("${AuthRules.CODE_LENGTH}-digit code") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(letterSpacing = 6.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { next() }),
                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.SmsOtpCode }
            )
        }
        PasswordField(
            value = password,
            onValueChange = { password = it },
            label = "New password",
            contentType = ContentType.NewPassword,
            imeAction = ImeAction.Next,
            onNext = next,
            supporting = "At least ${AuthRules.MIN_PASSWORD_LENGTH} characters."
        )
        PasswordField(
            value = confirm,
            onValueChange = { confirm = it },
            label = "Confirm new password",
            contentType = ContentType.NewPassword,
            imeAction = ImeAction.Done,
            onDone = { onConfirm(code, password, confirm) },
            isError = confirm.isNotEmpty() && confirm != password,
            supporting = if (confirm.isNotEmpty() && confirm != password) "Doesn't match." else null
        )

        Messages(state)
        SubmitButton("Set new password", isLoading = state.isSubmitting, onClick = { onConfirm(code, password, confirm) })

        if (!state.codeVerified) {
            TextButton(
                onClick = onResend,
                enabled = state.resendCooldown == 0 && !state.isSubmitting,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(if (state.resendCooldown > 0) "Resend code in ${state.resendCooldown}s" else "Resend code")
            }
        }
    }
}

// --- Layouts -----------------------------------------------------------------

/**
 * Photo collage over the top ~40% fading into the page, then the wordmark and
 * the form. Sign-up has more fields, so its collage is shorter.
 */
@Composable
private fun HeroLayout(compactHero: Boolean = false, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val heroHeight = maxHeight * if (compactHero) 0.28f else 0.4f
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(bottom = Spacing.lg)
        ) {
            HeroCollage(height = heroHeight)
            Column(
                modifier = Modifier.padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Wordmark()
                content()
            }
        }
    }
}

/** Reset pages: no hero, a back arrow, a title and one line of explanation. */
@Composable
private fun DetourLayout(title: String, body: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(top = Spacing.sm)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

/** Three bundled photos - never network-loaded, so the first screen draws offline. */
@Composable
private fun HeroCollage(height: Dp) {
    val gap = 3.dp
    val background = MaterialTheme.colorScheme.background
    Box(modifier = Modifier.fillMaxWidth().height(height)) {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            HeroPhoto(R.drawable.auth_hero_canal, Modifier.weight(1.15f).fillMaxHeight())
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                HeroPhoto(R.drawable.auth_hero_rock, Modifier.weight(1f).fillMaxWidth())
                HeroPhoto(R.drawable.auth_hero_coast, Modifier.weight(1f).fillMaxWidth())
            }
        }
        // Fade the lower half into the page so the form reads as one surface.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        1f to background
                    )
                )
        )
    }
}

@Composable
private fun HeroPhoto(res: Int, modifier: Modifier) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

@Composable
private fun Wordmark() {
    Column(modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs)) {
        Text(
            "Curated",
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Real trips, lived day by day.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs)
        )
    }
}

// --- Fields ------------------------------------------------------------------

@Composable
private fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    imeAction: ImeAction,
    onNext: () -> Unit = {},
    onDone: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress }
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    contentType: ContentType,
    imeAction: ImeAction,
    onNext: () -> Unit = {},
    onDone: () -> Unit = {},
    supporting: String? = null,
    isError: Boolean = false
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password"
                )
            }
        },
        isError = isError,
        supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth().semantics { this.contentType = contentType }
    )
}

/** Availability is checked as you type; the verdict sits under the field. */
@Composable
private fun UsernameField(value: String, status: UsernameStatus, onValueChange: (String) -> Unit, onNext: () -> Unit) {
    val (message, isError) = when (status) {
        UsernameStatus.Idle -> "Letters, numbers, dots and underscores." to false
        UsernameStatus.Checking -> "Checking…" to false
        UsernameStatus.Available -> "Available" to false
        UsernameStatus.Taken -> "Taken - try another." to true
        is UsernameStatus.Invalid -> status.reason to true
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Username") },
        prefix = { Text("@") },
        singleLine = true,
        isError = isError,
        supportingText = { Text(message) },
        trailingIcon = when (status) {
            UsernameStatus.Checking -> {
                { CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp)) }
            }
            UsernameStatus.Available -> {
                { Icon(Icons.Outlined.CheckCircle, contentDescription = "Available", tint = MaterialTheme.colorScheme.primary) }
            }
            else -> null
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Ascii,
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(onNext = { onNext() }),
        modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.NewUsername }
    )
}

// --- Bits --------------------------------------------------------------------

@Composable
private fun Messages(state: AuthUiState) {
    state.error?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    state.info?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

/** Disabled with a spinner while a request is out, so it can't be sent twice. */
@Composable
private fun SubmitButton(label: String, isLoading: Boolean, onClick: () -> Unit) {
    PrimaryButton(onClick = onClick, enabled = !isLoading, modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
        if (isLoading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Text(label)
        }
    }
}

/** "By creating an account, you agree to the Terms of Use and Privacy Policy." Both open in the browser. */
@Composable
private fun AgreementLine() {
    val linkStyle = TextLinkStyles(
        style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)
    )
    val text = buildAnnotatedString {
        append("By creating an account, you agree to the ")
        withLink(LinkAnnotation.Url(LegalLinks.TERMS_OF_USE, linkStyle)) { append("Terms of Use") }
        append(" and ")
        withLink(LinkAnnotation.Url(LegalLinks.PRIVACY_POLICY, linkStyle)) { append("Privacy Policy") }
        append(".")
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SwitchLine(prompt: String, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(prompt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onClick) { Text(action) }
    }
}

// --- Previews ----------------------------------------------------------------

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun SignInPreview() {
    CuratedTheme {
        SignInPage(
            state = AuthUiState(email = "maya@example.com"),
            onEmailChange = {}, onSubmit = {}, onForgotPassword = {}, onResendConfirmation = {}, onSwitchToSignUp = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SignUpPreview() {
    CuratedTheme {
        SignUpPage(
            state = AuthUiState(page = AuthPage.SIGN_UP, email = "maya@example.com", usernameStatus = UsernameStatus.Available),
            onEmailChange = {}, onUsernameChange = {}, onSubmit = { _, _, _ -> }, onSwitchToSignIn = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun ResetRequestPreview() {
    CuratedTheme {
        ResetRequestPage(
            state = AuthUiState(page = AuthPage.RESET_REQUEST, email = "maya@example.com"),
            onEmailChange = {}, onSendCode = {}, onBack = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun ResetVerifyPreview() {
    CuratedTheme {
        ResetVerifyPage(
            state = AuthUiState(
                page = AuthPage.RESET_VERIFY,
                email = "maya@example.com",
                info = "If an account exists for that email, we sent it a code.",
                resendCooldown = 42
            ),
            onConfirm = { _, _, _ -> }, onResend = {}, onBack = {}
        )
    }
}
