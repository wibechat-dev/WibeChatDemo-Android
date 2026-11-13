package com.wibechat.xmldemo.feature.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wibechat.demo.core.CoreServices
import com.wibechat.demo.core.auth.AuthErrors
import com.wibechat.xmldemo.R
import com.wibechat.xmldemo.databinding.DialogTextInputBinding
import com.wibechat.xmldemo.databinding.FragmentLoginBinding
import com.wibechat.xmldemo.ui.Validators
import com.wibechat.xmldemo.ui.applySystemBarsPadding
import com.wibechat.xmldemo.ui.hideKeyboard
import com.wibechat.xmldemo.ui.setLoading
import com.wibechat.xmldemo.ui.showMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Email + password and Google sign-in. MainActivity moves on once Firebase reports a user. */
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val b get() = _binding!!

    private var loading = false
    private var googleLoading = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.root.applySystemBarsPadding(ime = true)
        b.header.title.text = "Welcome back."
        b.header.subtitle.text = "Your people are one conversation away."
        b.newHere.text = "New to ${getString(R.string.app_name_title_case)}?"

        b.email.doAfterTextChanged { b.emailLayout.error = null }
        b.password.doAfterTextChanged { b.passwordLayout.error = null }
        b.password.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
        }
        b.signIn.setOnClickListener { submit() }
        b.google.setOnClickListener { signInWithGoogle() }
        b.forgotPassword.setOnClickListener { showResetDialog() }
        b.createAccount.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.container, SignupFragment())
                .addToBackStack("signup")
                .commit()
        }
    }

    private fun render() {
        val busy = loading || googleLoading
        b.signIn.setLoading(b.signInProgress, loading)
        b.google.setLoading(b.googleProgress, googleLoading)
        listOf(b.email, b.password, b.signIn, b.google, b.forgotPassword, b.createAccount).forEach { it.isEnabled = !busy }
    }

    private fun submit() {
        if (loading || googleLoading) return
        val email = b.email.text.toString()
        val password = b.password.text.toString()
        b.emailLayout.error = Validators.email(email)
        b.passwordLayout.error = Validators.password(password)
        if (b.emailLayout.error != null || b.passwordLayout.error != null) return
        b.root.hideKeyboard()
        loading = true
        render()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                CoreServices.auth.signIn(email, password) // MainActivity switches screens on success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(AuthErrors.messageFor(e))
            } finally {
                loading = false
                if (_binding != null) render()
            }
        }
    }

    private fun signInWithGoogle() {
        if (loading || googleLoading) return
        googleLoading = true
        render()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val idToken = GoogleSignIn.requestIdToken(requireActivity()) ?: return@launch // picker dismissed
                CoreServices.auth.signInWithGoogle(idToken)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                showMessage(e.message ?: "Could not sign in with Google.")
            } catch (e: Exception) {
                showMessage("Could not sign in with Google: ${AuthErrors.messageFor(e)}")
            } finally {
                googleLoading = false
                if (_binding != null) render()
            }
        }
    }

    private fun showResetDialog() {
        val dialogBinding = DialogTextInputBinding.inflate(layoutInflater)
        dialogBinding.inputLayout.hint = "Email"
        dialogBinding.inputLayout.placeholderText = "you@example.com"
        dialogBinding.input.inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or android.text.InputType.TYPE_CLASS_TEXT
        dialogBinding.input.setText(b.email.text.toString().trim())
        dialogBinding.input.doAfterTextChanged { dialogBinding.inputLayout.error = null }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Reset password")
            .setView(dialogBinding.root)
            .setPositiveButton("Send link", null) // set below so a bad email keeps the dialog open
            .setNegativeButton("Cancel", null)
            .create()

        fun send() {
            val address = dialogBinding.input.text.toString()
            dialogBinding.inputLayout.error = Validators.email(address)
            if (dialogBinding.inputLayout.error != null) return
            dialog.dismiss()
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    CoreServices.auth.sendPasswordResetEmail(address.trim())
                    showMessage("Password reset link sent to ${address.trim()}")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showMessage(AuthErrors.messageFor(e))
                }
            }
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { send() }
            dialogBinding.input.requestFocus()
        }
        dialogBinding.input.setOnEditorActionListener { _, _, _ -> send(); true }
        dialog.show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
