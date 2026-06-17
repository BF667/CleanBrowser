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
import com.cleanbrowser.browser.data.DatabaseHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class LoginActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var inputEmail: EditText
    private lateinit var inputName: EditText
    private lateinit var inputPassword: EditText
    private lateinit var btnAuth: Button
    private lateinit var btnToggle: TextView
    private lateinit var btnSkip: TextView
    private lateinit var btnGoogle: Button
    private lateinit var textError: TextView
    private var isSignUp = false
    private lateinit var db: DatabaseHelper

    companion object {
        private const val RC_GOOGLE_SIGN_IN = 9001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        db = DatabaseHelper(this)

        // If already logged in, go straight to browser
        val prefs = getSharedPreferences("cleanbrowser", Context.MODE_PRIVATE)
        if (prefs.getBoolean("is_logged_in", false)) {
            launchBrowser()
            return
        }

        // Google Sign-In client (no Firebase needed)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        inputEmail = findViewById(R.id.input_email)
        inputName = findViewById(R.id.input_name)
        inputPassword = findViewById(R.id.input_password)
        btnAuth = findViewById(R.id.btn_auth)
        btnToggle = findViewById(R.id.btn_toggle_mode)
        btnSkip = findViewById(R.id.btn_skip)
        btnGoogle = findViewById(R.id.btn_google_signin)
        textError = findViewById(R.id.text_error)

        btnToggle.setOnClickListener { toggleMode() }

        btnSkip.setOnClickListener {
            prefs.edit().putBoolean("is_guest", true).apply()
            launchBrowser()
        }

        btnAuth.setOnClickListener { handleEmailAuth() }
        btnGoogle.setOnClickListener { startGoogleSignIn() }
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

        if (isSignUp) {
            // Create account locally
            db.createUser(email, password, name)
            saveAndLaunch(email, name)
            Toast.makeText(this, "Welcome, $name!", Toast.LENGTH_SHORT).show()
        } else {
            // Sign in locally
            val (success, displayName) = db.authenticateUser(email, password)
            if (success) {
                saveAndLaunch(email, displayName)
                Toast.makeText(this, "Welcome back, $displayName!", Toast.LENGTH_SHORT).show()
            } else {
                showError("Invalid email or password")
            }
        }
    }

    private fun startGoogleSignIn() {
        val signInIntent = googleSignInClient.signInIntent
        @Suppress("DEPRECATION")
        startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account: GoogleSignInAccount? = task.getResult(ApiException::class.java)
                if (account != null) {
                    handleGoogleSignIn(account)
                } else {
                    showError("Google sign-in failed")
                }
            } catch (e: ApiException) {
                showError("Google sign-in failed: ${e.statusCode}")
            }
        }
    }

    private fun handleGoogleSignIn(account: GoogleSignInAccount) {
        val email = account.email ?: return
        val displayName = account.displayName ?: email.substringBefore("@")
        db.createGoogleUser(email, displayName)
        saveAndLaunch(email, displayName)
        Toast.makeText(this, "Welcome, $displayName!", Toast.LENGTH_SHORT).show()
    }

    private fun saveAndLaunch(email: String, displayName: String) {
        getSharedPreferences("cleanbrowser", Context.MODE_PRIVATE).edit().apply {
            putBoolean("is_logged_in", true)
            putBoolean("is_guest", false)
            putString("user_email", email)
            putString("user_name", displayName)
            apply()
        }
        launchBrowser()
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