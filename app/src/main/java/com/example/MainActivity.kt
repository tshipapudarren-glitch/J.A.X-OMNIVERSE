package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ai.MultiAiOrchestrator
import com.example.data.network.MarketDataService
import com.example.data.network.Mt5BridgeClient
import com.example.data.network.NewsIngestionService
import com.example.data.repository.JaxFirestoreRepository
import com.example.security.SecretVault
import com.example.security.configureAppCheck
import com.example.ui.JaxTerminalApp
import com.example.ui.auth.AuthGateScreen
import com.example.ui.theme.JaxOmniverseTheme
import com.example.ui.viewmodel.JaxViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // MUST execute before any Firebase AI Logic / App Check protected calls
        configureAppCheck(this, intent)

        setContent {
            JaxOmniverseTheme(darkTheme = true) {
                JaxAppRoot()
            }
        }
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
private fun JaxAppRoot() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val signedInUser = currentUser
    if (signedInUser == null) {
        AuthGateScreen(onAuthSuccess = {})
    } else {
        key(signedInUser.uid) {
            val viewModel: JaxViewModel = viewModel(
                key = signedInUser.uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val firestore = FirebaseFirestore.getInstance(databaseId)
                        val vault = SecretVault(app)
                        val repository = JaxFirestoreRepository(firestore)
                        val marketDataService = MarketDataService(vault)
                        val newsService = NewsIngestionService(vault)
                        val mt5Client = Mt5BridgeClient(vault)
                        val aiOrchestrator = MultiAiOrchestrator()
                        val displayName = signedInUser.displayName
                            ?: signedInUser.email?.substringBefore("@")
                            ?: "JAX Operator"

                        JaxViewModel(
                            repository = repository,
                            marketDataService = marketDataService,
                            newsIngestionService = newsService,
                            mt5BridgeClient = mt5Client,
                            aiOrchestrator = aiOrchestrator,
                            secretVault = vault,
                            currentUserId = signedInUser.uid,
                            operatorDisplayName = displayName
                        )
                    }
                }
            )
            JaxTerminalApp(
                viewModel = viewModel,
                onSignedOut = {}
            )
        }
    }
}
