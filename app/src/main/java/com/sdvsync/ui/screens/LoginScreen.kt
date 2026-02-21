package com.sdvsync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sdvsync.steam.AuthState
import com.sdvsync.ui.viewmodels.LoginViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
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

    // Navigate on successful login
    LaunchedEffect(authState) {
        if (authState is AuthState.LoggedIn) {
            onLoginSuccess()
        }
    }

    // Try resuming existing session on first load
    LaunchedEffect(Unit) {
        viewModel.tryResumeSession()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("SDV Sync - Steam Login") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (val state = authState) {
                is AuthState.Idle, is AuthState.WaitingForCredentials -> {
                    UsernamePasswordForm(
                        username = username,
                        password = password,
                        passwordVisible = passwordVisible,
                        onUsernameChange = viewModel::updateUsername,
                        onPasswordChange = viewModel::updatePassword,
                        onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
                        onSubmit = {
                            viewModel.login()
                            viewModel.submitCredentials()
                        },
                        enabled = true,
                    )
                }

                is AuthState.Connecting, is AuthState.Authenticating, is AuthState.LoggingIn -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        when (state) {
                            is AuthState.Connecting -> "Connecting to Steam..."
                            is AuthState.Authenticating -> "Authenticating..."
                            is AuthState.LoggingIn -> "Logging in..."
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                is AuthState.WaitingFor2FA -> {
                    Text(
                        if (state.is2FACode) "Enter your Steam Guard code"
                        else "Enter the code sent to your email",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = twoFactorCode,
                        onValueChange = viewModel::updateTwoFactorCode,
                        label = { Text("Code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { viewModel.submit2FA() }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.submit2FA() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = twoFactorCode.isNotBlank(),
                    ) {
                        Text("Submit Code")
                    }
                }

                is AuthState.Error -> {
                    Text(
                        state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(24.dp))
                    UsernamePasswordForm(
                        username = username,
                        password = password,
                        passwordVisible = passwordVisible,
                        onUsernameChange = viewModel::updateUsername,
                        onPasswordChange = viewModel::updatePassword,
                        onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
                        onSubmit = {
                            viewModel.login()
                            viewModel.submitCredentials()
                        },
                        enabled = true,
                    )
                }

                is AuthState.LoggedIn -> {
                    // Will navigate away via LaunchedEffect
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun UsernamePasswordForm(
    username: String,
    password: String,
    passwordVisible: Boolean,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = { Text("Steam Username") },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text("Password") },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (passwordVisible) VisualTransformation.None
        else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onTogglePasswordVisibility) {
                Icon(
                    if (passwordVisible) Icons.Filled.Visibility
                    else Icons.Filled.VisibilityOff,
                    contentDescription = "Toggle password visibility",
                )
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onSubmit,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled && username.isNotBlank() && password.isNotBlank(),
    ) {
        Text("Log In")
    }
}
