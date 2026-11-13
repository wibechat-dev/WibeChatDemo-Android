package com.wibechat.xmldemo.feature.call

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wibechat.android.room.participant.ConnectionQuality
import com.wibechat.demo.calls.AudioRoute
import com.wibechat.demo.calls.CallQuality
import com.wibechat.demo.calls.ProximityScreenLock
import com.wibechat.demo.calls.QualityMode
import com.wibechat.xmldemo.R
import com.wibechat.xmldemo.call.ActiveCall
import com.wibechat.xmldemo.call.CallArgs
import com.wibechat.xmldemo.databinding.FragmentCallBinding
import com.wibechat.xmldemo.feature.home.formatDuration
import com.wibechat.xmldemo.ui.applySystemBarsPadding
import com.wibechat.xmldemo.ui.collectOnView
import com.wibechat.xmldemo.ui.showMessage
import kotlinx.coroutines.flow.combine

/**
 * Connects to a room and shows everyone's camera in an auto-sizing grid,
 * with mic / camera / flip / speaker / screen-share / hang-up controls.
 * All call logic lives in [CallViewModel] (shared with the Compose app);
 * this fragment only renders its state.
 */
class CallFragment : Fragment() {

    private var _binding: FragmentCallBinding? = null
    private val b get() = _binding!!

    val args: CallArgs by lazy {
        val a = requireArguments()
        CallArgs(
            roomId = a.getString(ARG_ROOM).orEmpty(),
            title = a.getString(ARG_TITLE),
            startWithVideo = a.getBoolean(ARG_VIDEO, true),
            isOutgoing = a.getBoolean(ARG_OUTGOING),
        )
    }

    private val vm: CallViewModel by viewModels {
        val app = requireActivity().application
        viewModelFactory { initializer { CallViewModel(app, args) } }
    }

    private var proximity: ProximityScreenLock? = null
    private var wasLightStatusBar: Boolean? = null

    // Ask for mic + camera once, then join (listen-only if refused).
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        vm.onPermissionsResult(result.values.all { it })
    }

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) vm.startScreenShare(data)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCallBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.root.applySystemBarsPadding()
        keepScreenOnWithLightIcons(true)
        proximity = ProximityScreenLock(requireContext())

        if (savedInstanceState == null) {
            val permissions = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            val granted = permissions.all {
                ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
            }
            if (granted) vm.onPermissionsResult(true) else permissionLauncher.launch(permissions)
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = vm.leave()
        })

        val title = args.title ?: args.roomId
        b.title.text = title
        b.statusTitle.text = title
        b.statusAvatar.bind(title)

        val white = Color.WHITE
        val error = ContextCompat.getColor(requireContext(), R.color.error)
        b.statusEnd.set("End call", R.drawable.ic_call_end, error, white)
        b.end.set("End call", R.drawable.ic_call_end, error, white)
        b.flip.set("Flip", R.drawable.ic_cameraswitch, ContextCompat.getColor(requireContext(), R.color.white_12), white)

        b.back.setOnClickListener { vm.leave() }
        b.statusEnd.setOnClickListener { vm.leave() }
        b.end.setOnClickListener { vm.leave() }
        b.errorEnd.setOnClickListener { vm.leave() }
        b.retry.setOnClickListener { vm.join() }
        b.mic.setOnClickListener { vm.toggleMicrophone() }
        b.camera.setOnClickListener { vm.toggleCamera() }
        b.flip.setOnClickListener { vm.flipCamera() }
        b.speaker.setOnClickListener { vm.toggleSpeaker() }
        b.statusSpeaker.setOnClickListener { vm.toggleSpeaker() }
        b.share.setOnClickListener {
            if (vm.ui.value.session.screenShareEnabled) {
                vm.stopScreenShare()
            } else {
                val mpm = requireContext().getSystemService(MediaProjectionManager::class.java)
                projectionLauncher.launch(mpm.createScreenCaptureIntent())
            }
        }
        b.addToCall.setOnClickListener { showAddToCallSheet(vm) }
        b.qualityBadge.setOnClickListener { showQualitySheet(vm) }

        collectOnView(vm.close) { close() }
        collectOnView(vm.messages) { showMessage(it) }
        collectOnView(combine(vm.ui, vm.quality, ActiveCall.inPipMode, ::Triple)) { (ui, quality, inPip) ->
            render(ui, quality, inPip)
        }
    }

    private fun render(ui: CallUiState, quality: CallQuality, inPip: Boolean) {
        val session = ui.session
        val remotes = session.remotes
        val inCall = !ui.connecting && ui.error == null && remotes.isNotEmpty()

        // Screen off at the ear while audio is on the earpiece (the system uses the proximity sensor).
        proximity?.setActive(session.audioRoute == AudioRoute.Earpiece && !inPip)

        // Android's PiP window is tiny: only the other person's video, no chrome.
        if (inPip) {
            b.root.setBackgroundColor(Color.BLACK)
            listOf(b.topBar, b.networkBanner, b.statusView, b.errorView, b.listenOnly, b.qualityBadge, b.controls)
                .forEach { it.isVisible = false }
            b.callView.isVisible = true
            b.grid.update(vm.session.room, session.local, remotes, pipMode = true)
            return
        }
        b.root.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.call_background))
        b.topBar.isVisible = true
        b.qualityBadge.isVisible = true
        b.controls.isVisible = true

        b.addToCall.isVisible = inCall && vm.isDirectRoom
        b.timer.isVisible = inCall
        b.timer.text = formatDuration(ui.elapsedSeconds).ifEmpty { "00:00" }
        renderNetworkBanner(ui)

        val status = when {
            ui.connecting -> "Connecting…"
            ui.error != null -> null
            remotes.isEmpty() -> if (args.isOutgoing) "Calling…" else "Connecting…"
            else -> null
        }
        b.statusView.isVisible = status != null
        b.errorView.isVisible = ui.error != null && !ui.connecting
        b.callView.isVisible = inCall

        if (status != null) {
            b.statusText.text = if (ui.reconnecting) "Reconnecting…" else status
            bindSpeaker(b.statusSpeaker, session.speakerphone)
        }
        ui.error?.let { b.errorText.text = "Could not connect\n$it" }
        if (inCall) {
            b.listenOnly.isVisible = !ui.mediaGranted
            b.grid.update(vm.session.room, session.local, remotes, pipMode = false)
            renderControls(ui)
            renderQualityBadge(quality)
        }
    }

    private fun renderNetworkBanner(ui: CallUiState) {
        val q = ui.session.localConnectionQuality
        val (text, colorRes, progress) = when {
            ui.reconnecting -> Triple("Connection lost. Reconnecting…", R.color.warning, true)
            q == ConnectionQuality.POOR || q == ConnectionQuality.LOST -> Triple("Low network quality", R.color.warning_light, false)
            else -> Triple(null, 0, false)
        }
        b.networkBanner.isVisible = text != null
        if (text != null) {
            b.networkBanner.setBackgroundColor(ContextCompat.getColor(requireContext(), colorRes))
            b.bannerText.text = text
            b.bannerProgress.isVisible = progress
            b.bannerIcon.isVisible = !progress
        }
    }

    private fun renderControls(ui: CallUiState) {
        val s = ui.session
        val white = Color.WHITE
        val subtle = ContextCompat.getColor(requireContext(), R.color.white_12)
        val dark = ContextCompat.getColor(requireContext(), R.color.call_background)
        val primary = ContextCompat.getColor(requireContext(), R.color.primary)

        val micOn = s.microphoneEnabled
        b.mic.set(if (micOn) "Mute" else "Unmute", if (micOn) R.drawable.ic_mic else R.drawable.ic_mic_off, if (micOn) subtle else white, if (micOn) white else dark)
        val camOn = s.cameraEnabled
        b.camera.set(if (camOn) "Video off" else "Video on", if (camOn) R.drawable.ic_videocam else R.drawable.ic_videocam_off, if (camOn) subtle else white, if (camOn) white else dark)
        b.flip.isVisible = camOn
        bindSpeaker(b.speaker, s.speakerphone)
        val sharing = s.screenShareEnabled
        b.share.set(if (sharing) "Stop sharing" else "Share", if (sharing) R.drawable.ic_stop_screen_share else R.drawable.ic_screen_share, if (sharing) primary else subtle, white)
    }

    /** Loudspeaker on = highlighted, like the phone app. */
    private fun bindSpeaker(button: ControlButtonView, on: Boolean) {
        val subtle = ContextCompat.getColor(requireContext(), R.color.white_12)
        val dark = ContextCompat.getColor(requireContext(), R.color.call_background)
        button.set(
            "Speaker",
            if (on) R.drawable.ic_volume_up else R.drawable.ic_phone_in_talk,
            if (on) Color.WHITE else subtle,
            if (on) dark else Color.WHITE,
        )
    }

    /** Coloured dot for the connection + the resolution you're receiving ("HD"), or your own when nobody sends video. */
    private fun renderQualityBadge(q: CallQuality) {
        val stats = q.receiving ?: q.sending
        b.qualityDot.backgroundTintList = ColorStateList.valueOf(q.connection.color(requireContext()))
        b.qualityText.text = stats?.shortLabel ?: q.connection.label()
        b.qualityMax.isVisible = q.mode == QualityMode.Highest
    }

    private fun close() {
        val fm = parentFragmentManager
        if (!fm.isStateSaved) fm.popBackStack(BACK_STACK_NAME, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    /** Keeps the screen awake and switches status-bar icons to light while on this dark screen. */
    private fun keepScreenOnWithLightIcons(on: Boolean) {
        val window = activity?.window ?: return
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            wasLightStatusBar = controller.isAppearanceLightStatusBars
            controller.isAppearanceLightStatusBars = false
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            wasLightStatusBar?.let { controller.isAppearanceLightStatusBars = it }
        }
    }

    override fun onDestroyView() {
        proximity?.setActive(false)
        proximity = null
        keepScreenOnWithLightIcons(false)
        b.grid.release()
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val BACK_STACK_NAME = "call"
        private const val ARG_ROOM = "room"
        private const val ARG_TITLE = "title"
        private const val ARG_VIDEO = "video"
        private const val ARG_OUTGOING = "outgoing"

        fun newInstance(args: CallArgs) = CallFragment().apply {
            arguments = bundleOf(
                ARG_ROOM to args.roomId,
                ARG_TITLE to args.title,
                ARG_VIDEO to args.startWithVideo,
                ARG_OUTGOING to args.isOutgoing,
            )
        }
    }
}
