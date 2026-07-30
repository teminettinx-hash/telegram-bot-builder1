package com.botbuilder.app.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.botbuilder.app.data.local.SecureStore
import com.botbuilder.app.data.supabase.SupabaseAuthManager
import com.botbuilder.app.databinding.ActivityAuthBinding
import com.botbuilder.app.ui.MainActivity
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private lateinit var authManager: SupabaseAuthManager
    private var isSignUpMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = SupabaseAuthManager(SecureStore(applicationContext))

        if (authManager.isSignedIn()) {
            goToMain()
            return
        }

        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonPrimary.setOnClickListener { submit() }
        binding.textSwitchMode.setOnClickListener { toggleMode() }
    }

    private fun toggleMode() {
        isSignUpMode = !isSignUpMode
        binding.nameField.visibility = if (isSignUpMode) View.VISIBLE else View.GONE
        binding.buttonPrimary.text = if (isSignUpMode) "Create account" else "Log in"
        binding.textSwitchMode.text = if (isSignUpMode) "Already have an account? Log in" else "Don't have an account? Sign up"
        binding.textStatus.text = ""
    }

    private fun submit() {
        val name = binding.editName.text.toString().trim()
        val email = binding.editEmail.text.toString().trim()
        val password = binding.editPassword.text.toString()

        if (email.isEmpty() || password.isEmpty()) {
            binding.textStatus.text = "Enter an email and password"
            return
        }
        if (isSignUpMode && name.isEmpty()) {
            binding.textStatus.text = "Enter your name"
            return
        }
        if (password.length < 6) {
            binding.textStatus.text = "Password must be at least 6 characters"
            return
        }

        binding.textStatus.text = "Please wait…"
        binding.buttonPrimary.isEnabled = false

        lifecycleScope.launch {
            val result = if (isSignUpMode) authManager.signUp(name, email, password) else authManager.signIn(email, password)
            binding.buttonPrimary.isEnabled = true
            result.onSuccess {
                Toast.makeText(this@AuthActivity, "Welcome!", Toast.LENGTH_SHORT).show()
                goToMain()
            }.onFailure {
                binding.textStatus.text = it.message ?: "Something went wrong"
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
