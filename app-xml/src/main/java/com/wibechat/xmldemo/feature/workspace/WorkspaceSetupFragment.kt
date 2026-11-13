package com.wibechat.xmldemo.feature.workspace

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.wibechat.demo.core.CoreServices
import com.wibechat.demo.core.data.WorkspaceConfig
import com.wibechat.demo.core.data.WorkspaceConfigParser
import com.wibechat.xmldemo.R
import com.wibechat.xmldemo.databinding.FragmentWorkspaceSetupBinding
import com.wibechat.xmldemo.feature.auth.GoogleSignIn
import com.wibechat.xmldemo.ui.applySystemBarsPadding
import com.wibechat.xmldemo.ui.hideKeyboard
import com.wibechat.xmldemo.ui.setLoading
import com.wibechat.xmldemo.ui.showMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val ClientIdPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
private val UrlPattern = Regex("^https?://[^\\s/]+.*$")

/**
 * Shown once per account, right after sign-in, until the user joins a
 * workspace. Takes the three values WibeChat gives each client — typed into
 * the fields, or pasted as the whole onboarding message into the box at the
 * top, which fills the fields for you ([WorkspaceConfigParser]).
 *
 * The API key goes to the `workspace` Cloud Function once and is stored
 * server-side; it is never saved on the phone.
 */
class WorkspaceSetupFragment : Fragment() {

    private var _binding: FragmentWorkspaceSetupBinding? = null
    private val b get() = _binding!!

    private var testing = false
    private var connecting = false
    private val busy get() = testing || connecting

    /** Set while the paste box fills the fields, so those edits don't clear the "verified" note. */
    private var filling = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWorkspaceSetupBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.root.applySystemBarsPadding(ime = true)
        b.header.title.text = "Connect your workspace."
        b.header.subtitle.text =
            "Enter the WibeChat details you received. You only do this once. Everyone with the same client ID can see and call each other."

        b.paste.doAfterTextChanged { if (b.paste.hasFocus()) applyConfig(it?.toString().orEmpty()) }
        b.pasteButton.setOnClickListener { pasteFromClipboard() }

        b.serverUrl.doAfterTextChanged { b.serverUrlLayout.error = null; clearVerified() }
        b.apiKey.doAfterTextChanged { b.apiKeyLayout.error = null; clearVerified() }
        b.clientId.doAfterTextChanged { b.clientIdLayout.error = null; clearVerified() }
        b.clientId.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { submit(dryRun = false); true } else false
        }

        b.connect.setOnClickListener { submit(dryRun = false) }
        b.test.setOnClickListener { submit(dryRun = true) }
        b.differentAccount.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                GoogleSignIn.signOut(requireContext())
                CoreServices.auth.signOut()
            }
        }
    }

    private fun clearVerified() {
        if (!filling) b.verifiedRow.isVisible = false
    }

    private fun pasteFromClipboard() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(requireContext())?.toString().orEmpty()
        if (text.isBlank()) {
            showMessage("The clipboard is empty.")
            return
        }
        b.paste.setText(text)
        applyConfig(text)
    }

    private fun applyConfig(text: String) {
        val config = WorkspaceConfigParser.parse(text)
        filling = true
        config.serverUrl?.let { b.serverUrl.setText(it) }
        config.apiKey?.let { b.apiKey.setText(it) }
        config.clientId?.let { b.clientId.setText(it) }
        filling = false
        b.verifiedRow.isVisible = false

        val found = listOfNotNull(
            "server URL".takeIf { config.serverUrl != null },
            "API key".takeIf { config.apiKey != null },
            "client ID".takeIf { config.clientId != null },
        )
        val (note, isError) = when {
            text.isBlank() -> "Paste the message and the fields below fill in." to false
            found.isEmpty() -> "Couldn't find WibeChat details in that text." to true
            config.isComplete -> "Filled in all three fields." to false
            else -> "Found ${found.joinToString()}; fill in the rest below." to false
        }
        b.pasteNote.text = note
        b.pasteNote.setTextColor(ContextCompat.getColor(requireContext(), if (isError) R.color.error else R.color.text_secondary))
    }

    private fun validate(): Boolean {
        val url = b.serverUrl.text.toString().trim()
        b.serverUrlLayout.error = if (!UrlPattern.matches(url)) "Use a URL like https://api.wibechat.com" else null
        b.apiKeyLayout.error = if (b.apiKey.text.isNullOrBlank()) "API key is required" else null
        b.clientIdLayout.error =
            if (!ClientIdPattern.matches(b.clientId.text.toString().trim())) "Client ID looks like 13bb8ea1-e5a9-4718-ad13-a464f0831ce3" else null
        return b.serverUrlLayout.error == null && b.apiKeyLayout.error == null && b.clientIdLayout.error == null
    }

    private fun render() {
        b.connect.setLoading(b.connectProgress, connecting)
        b.test.setLoading(b.testProgress, testing)
        listOf(b.paste, b.pasteButton, b.serverUrl, b.apiKey, b.clientId, b.connect, b.test, b.differentAccount)
            .forEach { it.isEnabled = !busy }
    }

    private fun submit(dryRun: Boolean) {
        if (busy || !validate()) return
        b.root.hideKeyboard()
        if (dryRun) testing = true else connecting = true
        render()
        val config = WorkspaceConfig(
            serverUrl = b.serverUrl.text.toString(),
            apiKey = b.apiKey.text.toString(),
            clientId = b.clientId.text.toString(),
        )
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val workspace = CoreServices.workspace.connect(config, dryRun)
                if (dryRun) {
                    b.verifiedText.text = "Connection works: ${workspace.name}"
                    b.verifiedRow.isVisible = true
                } else {
                    // Joined: users/{uid}.workspaceId is set and MainActivity switches to Home.
                    b.apiKey.setText("")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                b.verifiedRow.isVisible = false
                showMessage(e.message ?: "Couldn't connect. Please try again.")
            } finally {
                testing = false
                connecting = false
                if (_binding != null) render()
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
