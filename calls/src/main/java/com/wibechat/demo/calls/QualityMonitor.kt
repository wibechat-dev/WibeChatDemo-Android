package com.wibechat.demo.calls

import android.os.SystemClock
import com.wibechat.android.room.track.Track
import livekit.org.webrtc.RTCStats
import livekit.org.webrtc.RTCStatsReport

/**
 * Turns raw WebRTC stats reports into [MediaStats].
 *
 * Bitrate isn't in the report directly: we keep the last byte counter per
 * track and divide the difference by the elapsed time between polls.
 */
internal class QualityMonitor {

    private data class Sample(val bytes: Long, val atMs: Long)

    private val lastSamples = mutableMapOf<String, Sample>()

    /** Video you receive on [track] (`inbound-rtp`). */
    suspend fun receiving(track: Track): MediaStats? {
        val report = runCatching { track.getRTCStats() }.getOrNull() ?: return null
        val inbound = report.videoRtp("inbound-rtp").maxByOrNull { it.long("frameWidth") } ?: return null
        return MediaStats(
            width = inbound.long("frameWidth").toInt(),
            height = inbound.long("frameHeight").toInt(),
            fps = inbound.double("framesPerSecond").toInt(),
            kbps = bitrateKbps("in:${track.sid}", inbound.long("bytesReceived")),
        )
    }

    /**
     * Video you send on [track] (`outbound-rtp`). With simulcast there is one
     * entry per layer: report the biggest active layer, and total bytes.
     */
    suspend fun sending(track: Track): MediaStats? {
        val report = runCatching { track.getRTCStats() }.getOrNull() ?: return null
        val layers = report.videoRtp("outbound-rtp")
        val top = layers.filter { it.long("frameWidth") > 0 }.maxByOrNull { it.long("frameWidth") } ?: return null
        val limitation = (top.members["qualityLimitationReason"] as? String)?.takeIf { it != "none" }
        return MediaStats(
            width = top.long("frameWidth").toInt(),
            height = top.long("frameHeight").toInt(),
            fps = top.double("framesPerSecond").toInt(),
            kbps = bitrateKbps("out:${track.sid}", layers.sumOf { it.long("bytesSent") }),
            limitedBy = limitation,
        )
    }

    fun reset() = lastSamples.clear()

    private fun bitrateKbps(key: String, bytes: Long): Int {
        val now = SystemClock.elapsedRealtime()
        val previous = lastSamples.put(key, Sample(bytes, now)) ?: return 0
        val elapsedMs = now - previous.atMs
        if (elapsedMs <= 0 || bytes < previous.bytes) return 0
        return ((bytes - previous.bytes) * 8 / elapsedMs).toInt() // bytes/ms * 8 = kbit/s
    }

    private fun RTCStatsReport.videoRtp(type: String): List<RTCStats> =
        statsMap.values.filter { it.type == type && (it.members["kind"] ?: it.members["mediaType"]) == "video" }

    private fun RTCStats.long(key: String): Long = (members[key] as? Number)?.toLong() ?: 0L
    private fun RTCStats.double(key: String): Double = (members[key] as? Number)?.toDouble() ?: 0.0
}
