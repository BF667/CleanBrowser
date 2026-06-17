package com.cleanbrowser.browser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

class LoginActivity : AppCompatActivity() {

    private var auth: FirebaseAuth? = null
    private var googleSignInClient: GoogleSignInClient? = null
    private lateinit var inputEmail: EditText
    private lateinit var inputName: EditText
    private lateinit var inputPassword: EditText
    private lateinit var btnAuth: Button
    private lateinit var btnToggle: TextView
    private lateinit var btnSkip: TextView
    private lateinit var btnGoogle: Button
    private lateinit var textError: TextView
    private var isSignUp = false
    private var firebaseReady = false

    companion object {
        private const val RC_GOOGLE_SIGN_IN = 9001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Safely initialize Firebase — placeholder config will fail silently
        firebaseReady = initFirebase()

        // If already logged in from a previous session, go straight to browser
        if (firebaseReady) {
            val user = auth?.currentUser
            if (user != null) {
                launchBrowser()
                return
            }
        }

        inputEmail = findViewById(R.id.input_email)
        inputName = findViewById(R.id.input_name)
        inputPassword = findViewById(R.id.input_password)
        btnAuth = findViewById(R.id.btn_auth)
        btnToggle = findViewById(R.id.btn_toggle_mode)
        btnSkip = findViewById(R.id.btn_skip)
        btnGoogle = findViewById(R.id.btn_google_signin)
        textError = findViewById(R.id.text_error)

        // Guest skip — no Firebase needed
        btnSkip.setOnClickListener {
            getSharedPreferences("cleanbrowser", Context.MODE_PRIVATE)
                .edit().putBoolean("is_guest", true).apply()
            launchBrowser()
        }

        btnToggle.setOnClickListener { toggleMode() }
        btnAuth.setOnClickListener { handleEmailAuth() }

        // Google Sign-In — only enable if Firebase is properly configured
        if (firebaseReady && initGoogleSignIn()) {
            btnGoogle.setOnClickListener { startGoogleSignIn() }
        } else {
            btnGoogle.setOnClickListener {
                Toast.makeText(this, "Google Sign-In requires Firebase configuration", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun initFirebase(): Boolean {
        return try {
            auth = FirebaseAuth.getInstance()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun initGoogleSignIn(): Boolean {
        return try {
            val webClientId = getString(R.string.default_web_client_id)
            if (webClientId.contains("PLACEHOLDER")) return false

            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(this, gso)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun toggleMode() {
        isSignUp = !isSignUp
        if (isSignUp) {
            inputName.visibility = View.VISIBLE
            btnAuth.text = "Sign Up"
            btnToggle.text = "Already have an account? Sign in"
        } else {
            inputName.visibility = View.GONE
            btnAuth.text = "Sign In"
            btnToggle.text = "Don't have an account? Sign up"
        }
        textError.visibility = View.GONE
    }

    private fun handleEmailAuth() {
        if (!firebaseReady) {
            showError("Firebase is not configured. Use guest mode or set up Firebase.")
            return
        }

        val email = inputEmail.text.toString().trim()
        val password = inputPassword.text.toString().trim()
        val name = inputName.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            showError("Email and password are required")
            return
        }
        if (password.length < 6) {
            showError("Password must be at least 6 characters")
            return
        }
        if (isSignUp && name.isEmpty()) {
            showError("Display name is required")
            return
        }

        textError.visibility = View.GONE
        btnAuth.isEnabled = false
        btnAuth.text = if (isSignUp) "Creating account..." else "Signing in..."

        val authInstance = auth ?: return

        if (isSignUp) {
            authInstance.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .build()
                    result.user?.updateProfile(profileUpdates)
                    Toast.makeText(this, "Welcome, $name!", Toast.LENGTH_SHORT).show()
                    getSharedPreferences("cleanbrowser", Context.MODE_PRIVATE)
                        .edit().putBoolean("is_guest", false).apply()
                    launchBrowser()
                }
                .addOnFailureListener { e ->
                    btnAuth.isEnabled = true
                    btnAuth.text = "Sign Up"
                    showError(e.message ?: "Sign up failed")
                }
        } else {
            authInstance.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show()
                    getSharedPreferences("cleanbrowser", Context.MODE_PRIVATE)
                        .edit().putBoolean("is_guest", false).apply()
                    launchBrowser()
                }
                .addOnFailureListener { e ->
                    btnAuth.isEnabled = true
                    btnAuth.text = "Sign In"
                    showError(e.message ?: "Sign in failed")
                }
        }
    }

    private fun startGoogleSignIn() {
        val client = googleSignInClient ?: return
        val signInIntent = client.signInIntent
        @Suppress("DEPRECATION")
        startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    firebaseAuthWithGoogle(idToken)
                } else {
                    showError("Google sign-in returned no token")
                }
            } catch (e: ApiException) {
                showError("Google sign-in failed: ${e.statusCode}")
            } catch (e: Exception) {
                showError("Google sign-in failed")
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val authInstance = auth ?: return
        val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
        authInstance.signInWithCredential(credential)
            .addOnSuccessListener {
                Toast.makeText(this, "Welcome, ${it.user?.displayName}!", Toast.LENGTH_SHORT).show()
                getSharedPreferences("cleanbrowser", Context.MODE_PRIVATE)
                    .edit().putBoolean("is_guest", false).apply()
                launchBrowser()
            }
            .addOnFailureListener { e ->
                showError(e.message ?: "Authentication failed")
            }
    }

    private fun launchBrowser() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun showError(msg: String) {
        textError.text = msg
        textError.visibility = View.VISIBLE
    }
}