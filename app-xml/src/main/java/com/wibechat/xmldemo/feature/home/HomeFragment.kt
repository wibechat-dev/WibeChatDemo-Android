package com.wibechat.xmldemo.feature.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wibechat.demo.core.CoreServices
import com.wibechat.demo.core.auth.AuthErrors
import com.wibechat.demo.core.model.RoomInfo
import com.wibechat.demo.core.model.Workspace
import com.wibechat.xmldemo.BuildConfig
import com.wibechat.xmldemo.R
import com.wibechat.xmldemo.call.CallArgs
import com.wibechat.xmldemo.call.CallLauncher
import com.wibechat.xmldemo.databinding.DialogTextInputBinding
import com.wibechat.xmldemo.databinding.FragmentHomeBinding
import com.wibechat.xmldemo.feature.auth.GoogleSignIn
import com.wibechat.xmldemo.ui.applySystemBarsPadding
import com.wibechat.xmldemo.ui.collectOnView
import com.wibechat.xmldemo.ui.dp
import com.wibechat.xmldemo.ui.showMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private enum class HomeTab(
    val menuId: Int,
    val title: String,
    val subtitle: String,
    val searchHint: String,
) {
    Rooms(R.id.tab_rooms, "A space to connect.", "Gather your people in a room.", "Search rooms"),
    People(R.id.tab_people, "Your next conversation.", "A voice or video call is just a tap away.", "Search people"),
    Recents(R.id.tab_recents, "Pick up where you left off.", "Recent conversations, all in one place.", "Search recent calls"),
}

/** What the list area shows. */
private sealed interface ListContent {
    data object Loading : ListContent
    data class Empty(@DrawableRes val icon: Int, val title: String, val message: String) : ListContent
    data class Items(val items: List<Any>) : ListContent
}

/**
 * Rooms / People / Recents with a search box, plus the account drawer.
 * All three lists stay subscribed in [HomeViewModel], so switching tabs is instant.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val b get() = _binding!!

    private val workspace: Workspace by lazy {
        val a = requireArguments()
        Workspace(
            id = a.getString(ARG_ID).orEmpty(),
            name = a.getString(ARG_NAME).orEmpty(),
            apiUrl = "",
            serverUrl = "",
            clientId = a.getString(ARG_CLIENT_ID).orEmpty(),
            apiKeyMasked = a.getString(ARG_KEY_MASKED).orEmpty(),
        )
    }

    private val vm: HomeViewModel by viewModels {
        viewModelFactory { initializer { HomeViewModel(workspace.id) } }
    }

    private val tab = MutableStateFlow(HomeTab.Rooms)
    private val query = MutableStateFlow("")
    private val myUid get() = CoreServices.auth.currentUser?.uid

    private lateinit var roomsAdapter: RoomsAdapter
    private lateinit var peopleAdapter: PeopleAdapter
    private lateinit var callLogAdapter: CallLogAdapter

    private val closeDrawerOnBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = b.drawer.closeDrawer(GravityCompat.START)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString(KEY_TAB)?.let { name -> tab.value = HomeTab.valueOf(name) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.main.applySystemBarsPadding(bottom = false) // the bottom bar pads itself
        b.drawerPanel.applySystemBarsPadding()
        b.list.layoutManager = LinearLayoutManager(requireContext())

        val scope = viewLifecycleOwner.lifecycleScope
        roomsAdapter = RoomsAdapter(
            scope = scope,
            myUid = myUid,
            presence = vm::presence,
            onJoin = { CallLauncher.open(CallArgs(roomId = it.id, title = it.name)) },
            onDelete = ::confirmDeleteRoom,
        )
        peopleAdapter = PeopleAdapter(onCall = ::startCall)
        callLogAdapter = CallLogAdapter(onCall = ::startCall)

        setUpTopBarAndDrawer()

        b.tabs.selectedItemId = tab.value.menuId
        b.tabs.setOnItemSelectedListener { item ->
            val next = HomeTab.entries.first { it.menuId == item.itemId }
            if (next != tab.value) {
                b.search.clearFocus()
                b.search.setText("")
                tab.value = next
            }
            true
        }
        b.search.doAfterTextChanged { query.value = it?.toString()?.trim()?.lowercase().orEmpty() }
        b.createRoom.setOnClickListener { showCreateRoomDialog() }

        // Hide the big tab header when there isn't room for it (landscape, split screen).
        b.root.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val show = v.height > 560.dp
            if (b.tabHeader.isVisible != show) b.tabHeader.post { _binding?.tabHeader?.isVisible = show }
        }

        collectOnView(tab) { t ->
            b.tabTitle.text = t.title
            b.tabSubtitle.text = t.subtitle
            b.search.hint = t.searchHint
            if (t == HomeTab.Rooms) b.createRoom.show() else b.createRoom.hide()
            b.list.adapter = when (t) {
                HomeTab.Rooms -> roomsAdapter
                HomeTab.People -> peopleAdapter
                HomeTab.Recents -> callLogAdapter
            }
            // Room cards float above the "Create room" button.
            b.list.setPadding(24.dp, 0, 24.dp, if (t == HomeTab.Rooms) 96.dp else 24.dp)
        }

        val content = combine(tab, query, vm.rooms, vm.users, vm.callLogs) { t, q, rooms, users, logs ->
            when (t) {
                HomeTab.Rooms -> roomsContent(rooms, q)
                HomeTab.People -> peopleContent(users, q)
                HomeTab.Recents -> recentsContent(logs, q)
            }
        }
        collectOnView(content, ::render)
    }

    // ---- list content (same rules and copy as the Compose tabs) ---------------

    private fun roomsContent(state: LoadState<List<RoomInfo>>, q: String): ListContent = when (state) {
        LoadState.Loading -> ListContent.Loading
        LoadState.Failed -> ListContent.Empty(R.drawable.ic_cloud_off, "Rooms couldn’t load", "Please check your connection and try again.")
        is LoadState.Loaded -> {
            val rooms = state.data.filter { !it.direct && it.name.lowercase().contains(q) }
            when {
                rooms.isNotEmpty() -> ListContent.Items(rooms)
                q.isEmpty() -> ListContent.Empty(R.drawable.ic_forum, "Make room for your people", "Create a room, invite someone, and let the conversation flow.")
                else -> ListContent.Empty(R.drawable.ic_search_off, "No rooms found", "Try a different room name.")
            }
        }
    }

    private fun peopleContent(state: LoadState<List<com.wibechat.demo.core.model.AppUser>>, q: String): ListContent = when (state) {
        LoadState.Loading -> ListContent.Loading
        LoadState.Failed -> ListContent.Empty(R.drawable.ic_cloud_off, "People couldn’t load", "Check your connection. Your contacts will appear when you’re back online.")
        is LoadState.Loaded -> {
            val people = state.data.filter {
                it.uid != myUid && (it.label.lowercase().contains(q) || it.email.orEmpty().lowercase().contains(q))
            }
            when {
                people.isNotEmpty() -> ListContent.Items(people)
                q.isEmpty() -> ListContent.Empty(
                    R.drawable.ic_people,
                    "Better with your people",
                    "When someone joins ${getString(R.string.app_name_title_case)}, you can find them here and start a call.",
                )
                else -> ListContent.Empty(R.drawable.ic_search_off, "No people found", "Try another name or email address.")
            }
        }
    }

    private fun recentsContent(state: LoadState<List<com.wibechat.demo.core.model.CallLogEntry>>, q: String): ListContent = when (state) {
        LoadState.Loading -> ListContent.Loading
        LoadState.Failed -> ListContent.Empty(R.drawable.ic_cloud_off, "Recent calls couldn’t load", "Please check your connection and try again.")
        is LoadState.Loaded -> {
            val entries = state.data.filter { it.otherLabel.lowercase().contains(q) }
            when {
                entries.isNotEmpty() -> ListContent.Items(entries)
                q.isEmpty() -> ListContent.Empty(R.drawable.ic_call, "Your story starts with a hello", "Make a call from People. Your recent conversations will appear here.")
                else -> ListContent.Empty(R.drawable.ic_search_off, "No calls found", "Try searching for another person.")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun render(content: ListContent) {
        b.progress.isVisible = content is ListContent.Loading
        b.empty.root.isVisible = content is ListContent.Empty
        b.list.isVisible = content is ListContent.Items
        when (content) {
            is ListContent.Empty -> {
                b.empty.emptyIcon.setImageResource(content.icon)
                b.empty.emptyTitle.text = content.title
                b.empty.emptyMessage.text = content.message
            }
            is ListContent.Items -> when (tab.value) {
                HomeTab.Rooms -> roomsAdapter.submitList(content.items as List<RoomInfo>)
                HomeTab.People -> peopleAdapter.submitList(content.items as List<com.wibechat.demo.core.model.AppUser>)
                HomeTab.Recents -> callLogAdapter.submitList(content.items as List<com.wibechat.demo.core.model.CallLogEntry>)
            }
            ListContent.Loading -> Unit
        }
    }

    // ---- actions ---------------------------------------------------------------

    private fun startCall(uid: String, label: String, video: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                vm.startCall(uid, label, video)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage("Could not start call: ${e.message}")
            }
        }
    }

    /** Room names double as Firestore doc ids, so keep them simple. */
    private fun showCreateRoomDialog() {
        val validName = Regex("^[a-zA-Z0-9_-]{1,40}$")
        val d = DialogTextInputBinding.inflate(layoutInflater)
        d.inputLayout.hint = "Room name"
        d.inputLayout.placeholderText = "e.g. team-standup"
        d.input.doAfterTextChanged { d.inputLayout.error = null }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Create room")
            .setView(d.root)
            .setPositiveButton("Create", null)
            .setNegativeButton("Cancel", null)
            .create()

        fun submit() {
            val name = d.input.text.toString().trim()
            d.inputLayout.error = when {
                name.isEmpty() -> "Room name is required"
                !validName.matches(name) -> "Letters, numbers, - and _ only"
                else -> null
            }
            if (d.inputLayout.error != null) return
            dialog.dismiss()
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    vm.createRoom(name)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showMessage("Could not create room: ${e.message}")
                }
            }
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit() }
            d.input.requestFocus()
        }
        d.input.setOnEditorActionListener { _, _, _ -> submit(); true }
        dialog.show()
    }

    private fun confirmDeleteRoom(room: RoomInfo) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete room")
            .setMessage("Delete \"${room.name}\"? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        vm.deleteRoom(room.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        showMessage("Could not delete room: ${e.message}")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
            .redPositiveButton()
    }

    // ---- top bar + account drawer ------------------------------------------

    private fun setUpTopBarAndDrawer() {
        val user = CoreServices.auth.sessionUser
        val label = user?.displayName ?: user?.email.orEmpty()
        b.accountAvatar.bind(label, user?.photoUrl)
        b.accountAvatar.setOnClickListener { b.drawer.openDrawer(GravityCompat.START) }

        b.drawerAvatar.bind(label, user?.photoUrl)
        b.drawerName.text = user?.displayName?.takeIf { it.isNotBlank() } ?: "Signed in"
        b.drawerEmail.text = user?.email.orEmpty()
        b.workspaceName.text = workspace.name
        b.workspaceDetails.text = "Client ${workspace.clientId.take(8)}…  ·  Key ${workspace.apiKeyMasked}"

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, closeDrawerOnBack)
        b.drawer.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) { closeDrawerOnBack.isEnabled = true }
            override fun onDrawerClosed(drawerView: View) { closeDrawerOnBack.isEnabled = false }
        })

        b.leaveWorkspace.setOnClickListener {
            b.drawer.closeDrawers()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Leave workspace")
                .setMessage("You'll stop seeing people and rooms in ${workspace.name}, and you'll need the server URL, API key and client ID to join again.")
                .setPositiveButton("Leave") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            vm.leaveWorkspace() // MainActivity shows the setup screen
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            showMessage("Could not leave workspace: ${e.message}")
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
                .redPositiveButton()
        }

        // Debug aid for testing the Cloud Functions with curl. Hidden in release
        // builds because it hands out a live session token.
        b.copyIdToken.isVisible = BuildConfig.DEBUG
        b.copyIdToken.setOnClickListener {
            b.drawer.closeDrawers()
            viewLifecycleOwner.lifecycleScope.launch {
                val token = runCatching { CoreServices.auth.idToken() }.getOrNull()
                if (token == null) {
                    showMessage("No active session")
                } else {
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("ID token", token))
                    showMessage("Copied to clipboard!")
                }
            }
        }

        b.signOut.setOnClickListener {
            b.drawer.closeDrawers()
            viewLifecycleOwner.lifecycleScope.launch {
                GoogleSignIn.signOut(requireContext())
                CoreServices.auth.signOut()
            }
        }

        b.deleteAccount.setOnClickListener {
            b.drawer.closeDrawers()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete account")
                .setMessage("This permanently deletes your account and profile. This cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            CoreServices.auth.deleteAccount() // MainActivity returns to sign-in
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            showMessage(AuthErrors.messageFor(e))
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
                .redPositiveButton()
        }
    }

    private fun AlertDialog.redPositiveButton() {
        getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ContextCompat.getColor(requireContext(), R.color.error))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_TAB, tab.value.name)
    }

    override fun onDestroyView() {
        b.list.adapter = null // stops the room presence listeners
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_ID = "workspaceId"
        private const val ARG_NAME = "workspaceName"
        private const val ARG_CLIENT_ID = "clientId"
        private const val ARG_KEY_MASKED = "apiKeyMasked"
        private const val KEY_TAB = "tab"

        fun newInstance(workspace: Workspace) = HomeFragment().apply {
            arguments = bundleOf(
                ARG_ID to workspace.id,
                ARG_NAME to workspace.name,
                ARG_CLIENT_ID to workspace.clientId,
                ARG_KEY_MASKED to workspace.apiKeyMasked,
            )
        }
    }
}
