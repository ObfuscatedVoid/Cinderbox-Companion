package com.sdvsync.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.sdvsync.R
import com.sdvsync.steam.AuthState
import com.sdvsync.ui.components.StardewButton
import com.sdvsync.ui.components.StardewButtonVariant
import com.sdvsync.ui.components.StardewCard
import com.sdvsync.ui.components.StardewOutlinedButton
import com.sdvsync.ui.components.StardewTopAppBar
import com.sdvsync.ui.viewmodels.LoginViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val authState by viewModel.authState.collectAsState()
    val username by viewModel.username.collectAsState()
    val password by viewModel.password.collectAsState()
    val twoFactorCode by viewModel.twoFactorCode.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(authState) {
        if (authState is AuthState.LoggedIn) {
            onLoginSuccess()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.tryResumeSession()
    }

    val warmTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
    )

    Scaffold(
        topBar = {
            StardewTopAppBar(title = stringResource(R.string.login_title))
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.weight(1f))

            // Branded header
            Text(
                "SDV Sync",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Stardew Valley Save Sync",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))

            when (val state = authState) {
                is AuthState.Idle, is AuthState.WaitingForCredentials -> {
                    LoginOptions(
                        username = username,
                        password = password,
                        passwordVisible = passwordVisible,
                        textFieldColors = warmTextFieldColors,
                        onUsernameChange = viewModel::updateUsername,
                        onPasswordChange = viewModel::updatePassword,
                        onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
                        onLogin = { viewModel.login() },
                        onQRLogin = { viewModel.loginWithQR() },
                    )
                }

                is AuthState.Connecting, is AuthState.Authenticating, is AuthState.LoggingIn -> {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        when (state) {
                            is AuthState.Connecting -> stringResource(R.string.login_connecting)
                            is AuthState.Authenticating -> stringResource(R.string.login_authenticating)
                            is AuthState.LoggingIn -> stringResource(R.string.login_logging_in)
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                is AuthState.WaitingForQRScan -> {
                    QRLoginView(
                        challengeUrl = state.challengeUrl,
                        onCancel = { viewModel.cancelQRLogin() },
                    )
                }

                is AuthState.WaitingFor2FA -> {
                    Text(
                        if (state.is2FACode) stringResource(R.string.login_2fa_steam_guard)
                        else stringResource(R.string.login_2fa_email_prompt),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = twoFactorCode,
                        onValueChange = viewModel::updateTwoFactorCode,
                        label = { Text(stringResource(R.string.login_2fa_label)) },
                        singleLine = true,
                        colors = warmTextFieldColors,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { viewModel.submit2FA() },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    StardewButton(
                        onClick = { viewModel.submit2FA() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = twoFactorCode.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.login_2fa_submit))
                    }
                }

                is AuthState.Error -> {
                    Text(
                        state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(24.dp))
                    LoginOptions(
                        username = username,
                        password = password,
                        passwordVisible = passwordVisible,
                        textFieldColors = warmTextFieldColors,
                        onUsernameChange = viewModel::updateUsername,
                        onPasswordChange = viewModel::updatePassword,
                        onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
                        onLogin = { viewModel.login() },
                        onQRLogin = { viewModel.loginWithQR() },
                    )
                }

                is AuthState.LoggedIn -> {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun LoginOptions(
    username: String,
    password: String,
    passwordVisible: Boolean,
    textFieldColors: TextFieldColors,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onLogin: () -> Unit,
    onQRLogin: () -> Unit,
) {
    StardewOutlinedButton(
        onClick = onQRLogin,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            Icons.Default.QrCode2,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.login_qr_button))
    }

    Spacer(Modifier.height(24.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(Modifier.weight(1f))
        Text(
            stringResource(R.string.login_or_divider),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.weight(1f))
    }

    Spacer(Modifier.height(24.dp))

    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = { Text(stringResource(R.string.login_username_hint)) },
        singleLine = true,
        colors = textFieldColors,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(R.string.login_password_hint)) },
        singleLine = true,
        colors = textFieldColors,
        visualTransformation = if (passwordVisible) VisualTransformation.None
        else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onTogglePasswordVisibility) {
                Icon(
                    if (passwordVisible) Icons.Filled.Visibility
                    else Icons.Filled.VisibilityOff,
                    contentDescription = stringResource(R.string.login_toggle_password),
                )
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onLogin() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(24.dp))
    StardewButton(
        onClick = onLogin,
        modifier = Modifier.fillMaxWidth(),
        variant = StardewButtonVariant.Action,
        enabled = username.isNotBlank() && password.isNotBlank(),
    ) {
        Text(stringResource(R.string.login_button))
    }
}

@Composable
private fun QRLoginView(
    challengeUrl: String,
    onCancel: () -> Unit,
) {
    Text(
        stringResource(R.string.login_qr_title),
        style = MaterialTheme.typography.titleLarge,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.login_qr_instructions),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))

    StardewCard(modifier = Modifier.size(240.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            QrCodeImage(
                data = challengeUrl,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    Spacer(Modifier.height(24.dp))
    StardewOutlinedButton(onClick = onCancel) {
        Text(stringResource(R.string.action_cancel))
    }
}

@Composable
private fun QrCodeImage(
    data: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val sizePx = with(density) { 200.dp.roundToPx() }
    val qrContentDescription = stringResource(R.string.login_qr_content_description)

    var bitmap by remember(data) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(data) {
        bitmap = withContext(Dispatchers.IO) {
            val writer = QRCodeWriter()
            val hints = mapOf<EncodeHintType, Any>(EncodeHintType.MARGIN to 1)
            val matrix = writer.encode(data, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val w = matrix.width
            val h = matrix.height
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    pixels[y * w + x] = if (matrix[x, y]) {
                        android.graphics.Color.BLACK
                    } else {
                        android.graphics.Color.WHITE
                    }
                }
            }
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                it.setPixels(pixels, 0, w, 0, 0, w, h)
            }
        }
    }

    bitmap?.let {
        Image(
            painter = BitmapPainter(it.asImageBitmap()),
            contentDescription = qrContentDescription,
            modifier = modifier,
        )
    } ?: Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}
