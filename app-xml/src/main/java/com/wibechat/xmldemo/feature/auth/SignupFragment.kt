package com.wibechat.xmldemo.feature.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.wibechat.demo.core.CoreServices
import com.wibechat.demo.core.auth.AuthErrors
import com.wibechat.xmldemo.databinding.FragmentSignupBinding
import com.wibechat.xmldemo.ui.Validators
import com.wibechat.xmldemo.ui.applySystemBarsPadding
import com.wibechat.xmldemo.ui.hideKeyboard
import com.wibechat.xmldemo.ui.setLoading
import com.wibechat.xmldemo.ui.showMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Name + email + password. MainActivity replaces the whole auth flow once the account exists. */
class SignupFragment : Fragment() {

    private var _binding: FragmentSignupBinding? = null
    private val b get() = _binding!!
    private var loading = false

    /** Stay put while the account is being created. */
    private val blockBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = Unit
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSignupBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.root.applySystemBarsPadding(ime = true)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, blockBack)
        b.header.title.text = "Closer starts here."
        b.header.subtitle.text = "Create your account and make room for great conversations."
        b.header.back.isVisible = true
        b.header.back.setOnClickListener { goBack() }
        b.signIn.setOnClickListener { goBack() }

        b.name.doAfterTextChanged { b.nameLayout.error = null }
        b.email.doAfterTextChanged { b.emailLayout.error = null }
        b.password.doAfterTextChanged { b.passwordLayout.error = null }
        b.password.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
        }
        b.createAccount.setOnClickListener { submit() }
    }

    private fun goBack() {
        if (!loading) parentFragmentManager.popBackStack()
    }

    private fun render() {
        b.createAccount.setLoading(b.createProgress, loading)
        listOf(b.name, b.email, b.password, b.createAccount, b.signIn).forEach { it.isEnabled = !loading }
        b.header.back.isVisible = !loading
        blockBack.isEnabled = loading
    }

    private fun submit() {
        if (loading) return
        val name = b.name.text.toString()
        val email = b.email.text.toString()
        val password = b.password.text.toString()
        b.nameLayout.error = if (name.isBlank()) "Name is required" else null
        b.emailLayout.error = Validators.email(email)
        b.passwordLayout.error = Validators.password(password)
        if (b.nameLayout.error != null || b.emailLayout.error != null || b.passwordLayout.error != null) return
        b.root.hideKeyboard()
        loading = true
        render()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                CoreServices.auth.signUp(email, password, name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(AuthErrors.messageFor(e))
                loading = false
                if (_binding != null) render()
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
