package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.ui.theme.BlackGlass
import com.example.ui.theme.BlackGlassElevated
import com.example.ui.theme.CoolGrey
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.CyanInfo
import com.example.ui.theme.DeepRedBearish
import com.example.ui.theme.EmeraldBullish
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderGold
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.ObsidianBlack
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

enum class AuthFlowMode {
    SIGN_IN,
    SIGN_UP
}

data class GoogleAuthOutcome(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val isNewUser: Boolean = false
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: (GoogleAuthOutcome) -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    val existingUser = Firebase.auth.currentUser
    if (existingUser != null) {
        onAuthSuccess(
            GoogleAuthOutcome(
                uid = existingUser.uid,
                email = existingUser.email.orEmpty(),
                displayName = existingUser.displayName.orEmpty(),
                isNewUser = false
            )
        )
        return
    }

    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onUnauthenticated()
        return
    }

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

    scope.launch {
        try {
            val activity = context.findActivity() ?: context
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                val user = authResult.user
                onAuthSuccess(
                    GoogleAuthOutcome(
                        uid = user?.uid.orEmpty(),
                        email = user?.email.orEmpty(),
                        displayName = user?.displayName.orEmpty(),
                        isNewUser = authResult.additionalUserInfo?.isNewUser == true
                    )
                )
            } else {
                onUnauthenticated()
            }
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    mode: AuthFlowMode = AuthFlowMode.SIGN_IN,
    onAuthSuccess: (GoogleAuthOutcome) -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }

    // One credential option per GetCredentialRequest for cross-API compatibility.
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

    scope.launch {
        try {
            val activity = context.findActivity()
                ?: error("Activity context required for Google ${if (mode == AuthFlowMode.SIGN_UP) "Sign-Up" else "Sign-In"}")
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                val user = authResult.user
                onAuthSuccess(
                    GoogleAuthOutcome(
                        uid = user?.uid.orEmpty(),
                        email = user?.email.orEmpty(),
                        displayName = user?.displayName.orEmpty(),
                        isNewUser = authResult.additionalUserInfo?.isNewUser == true
                    )
                )
            } else {
                onAuthError("Unexpected credential type returned from Credential Manager.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google ${mode.name} flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: NoCredentialException) {
            Log.w("Auth", "No Google credentials available on device: ${e.message}", e)
            val verb = if (mode == AuthFlowMode.SIGN_UP) "Sign Up" else "Sign In"
            onAuthError(
                "No Google account is configured on this device. Open Android Settings > Passwords & accounts > Add Google account, then tap $verb again."
            )
        } catch (e: FirebaseAuthException) {
            Log.e("Auth", "Firebase authentication rejected Google credential", e)
            onAuthError(e.localizedMessage ?: "Firebase authentication failed (${e.errorCode}).")
        } catch (e: Exception) {
            Log.e("Auth", "Google ${mode.name} failed", e)
            onAuthError(e.localizedMessage ?: "Google authentication failed.")
        }
    }
}

fun signOutUser(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}

@Composable
fun AuthGateScreen(
    initialMode: AuthFlowMode = AuthFlowMode.SIGN_IN,
    onAuthSuccess: (GoogleAuthOutcome) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var authMode by remember { mutableStateOf(initialMode) }
    var isCheckingAutoSignIn by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = { outcome ->
                isCheckingAutoSignIn = false
                onAuthSuccess(outcome)
            },
            onUnauthenticated = {
                isCheckingAutoSignIn = false
            },
            scope = scope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(BlackGlass, ObsidianBlack)
                )
            )
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp),
            colors = CardDefaults.cardColors(containerColor = BlackGlass),
            border = BorderStroke(1.dp, GlassBorderGold),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.jax_apex_icon_1794615347080),
                    contentDescription = "J.A.X. Omniverse Apex Crest",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )

                Text(
                    text = "J.A.X. OMNIVERSE",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MetallicGold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "APEX COMMAND INTELLIGENCE X",
                    style = MaterialTheme.typography.labelLarge,
                    color = CrispWhite,
                    textAlign = TextAlign.Center
                )

                // Segmented Sign In / Sign Up Switcher
                Surface(
                    color = ObsidianBlack,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AuthModeTab(
                            label = "Sign In",
                            selected = authMode == AuthFlowMode.SIGN_IN,
                            onClick = {
                                authMode = AuthFlowMode.SIGN_IN
                                errorMsg = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("auth_tab_sign_in")
                        )
                        AuthModeTab(
                            label = "Sign Up",
                            selected = authMode == AuthFlowMode.SIGN_UP,
                            onClick = {
                                authMode = AuthFlowMode.SIGN_UP
                                errorMsg = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("auth_tab_sign_up")
                        )
                    }
                }

                Text(
                    text = if (authMode == AuthFlowMode.SIGN_IN) {
                        "Sign in with your authorized Google account to resume your isolated J.A.X. market intelligence terminal."
                    } else {
                        "Create a new J.A.X. Omniverse workspace with Google Identity. Your profile, risk guardrails, and live feeds provision automatically."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoolGrey,
                    textAlign = TextAlign.Center
                )

                if (authMode == AuthFlowMode.SIGN_UP) {
                    Surface(
                        color = BlackGlassElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, GlassBorderGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "AUTOMATIC GOOGLE WORKSPACE PROVISIONING",
                                style = MaterialTheme.typography.labelSmall,
                                color = MetallicGold,
                                fontWeight = FontWeight.Bold
                            )
                            ProvisioningFeatureRow("Zero-trust personal Firestore namespace (/users/{uid})")
                            ProvisioningFeatureRow("8 pre-configured Crypto, FX, Gold & Index feeds")
                            ProvisioningFeatureRow("Sovereign Risk Guardian locked to ANALYSIS_ONLY")
                        }
                    }
                } else {
                    Surface(
                        color = ObsidianBlack,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, GlassBorderGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Zero Trust Security",
                                tint = EmeraldBullish,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (isCheckingAutoSignIn) {
                                    "Checking authorized Google session via Credential Manager..."
                                } else {
                                    "Zero-Trust Firestore Isolation • Hardware Keystore Vault • Execution Default: ANALYSIS ONLY"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCheckingAutoSignIn) CyanInfo else CoolGrey
                            )
                        }
                    }
                }

                if (errorMsg != null) {
                    Surface(
                        color = DeepRedBearish.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, DeepRedBearish),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMsg!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = DeepRedBearish,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                val isSignUp = authMode == AuthFlowMode.SIGN_UP
                Button(
                    onClick = {
                        errorMsg = null
                        isLoading = true
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            mode = authMode,
                            onAuthSuccess = { outcome ->
                                isLoading = false
                                onAuthSuccess(outcome)
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                errorMsg = msg
                            },
                            scope = scope,
                            onAuthCancelled = { isLoading = false }
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag(if (isSignUp) "google_sign_up_button" else "google_sign_in_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MetallicGold,
                        contentColor = ObsidianBlack
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = ObsidianBlack,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isSignUp) Icons.Default.PersonAdd else Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = if (isSignUp) "Sign up with Google" else "Sign in with Google",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                TextButton(
                    onClick = {
                        authMode = if (isSignUp) AuthFlowMode.SIGN_IN else AuthFlowMode.SIGN_UP
                        errorMsg = null
                    },
                    enabled = !isLoading,
                    modifier = Modifier.testTag("toggle_auth_mode_button")
                ) {
                    Text(
                        text = if (isSignUp) {
                            "Already have a J.A.X. workspace? Sign in with Google"
                        } else {
                            "New operator? Create a workspace via Google Sign-Up"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanInfo,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthModeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) MetallicGold else BlackGlass,
        shape = RoundedCornerShape(9.dp),
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) ObsidianBlack else CoolGrey
            )
        }
    }
}

@Composable
private fun ProvisioningFeatureRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = EmeraldBullish,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = CrispWhite
        )
    }
}
