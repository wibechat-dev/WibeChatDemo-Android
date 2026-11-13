package com.wibechat.demo.calls

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager

/**
 * Turns the screen off while the phone is held to the ear, like the phone
 * app, so a cheek can't tap "End call". Uses the proximity sensor through
 * Android's PROXIMITY_SCREEN_OFF_WAKE_LOCK: the system watches the sensor,
 * blanks the screen and blocks touches while something is near, and turns
 * it back on when it moves away. No sensor listener needed.
 *
 * Turn it on only while audio plays through the earpiece; on the
 * loudspeaker or a headset the phone isn't at the ear. Needs WAKE_LOCK.
 */
class ProximityScreenLock(context: Context) {

    private val wakeLock: PowerManager.WakeLock? = context.getSystemService(PowerManager::class.java)
        ?.takeIf { it.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) } // no sensor → no-op
        ?.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "wibechat:proximity")
        ?.apply { setReferenceCounted(false) }

    /** Whether this device has a proximity sensor that can turn the screen off. */
    val isSupported: Boolean get() = wakeLock != null

    @SuppressLint("WakelockTimeout") // held exactly while a call is on the earpiece; released by setActive(false)
    fun setActive(active: Boolean) {
        val lock = wakeLock ?: return
        if (active && !lock.isHeld) {
            lock.acquire()
        } else if (!active && lock.isHeld) {
            // If the phone is still at the ear, wait until it moves away before lighting the screen.
            lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        }
    }
}
