package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

/**
 * Turns an attached video into pieces a chat model can actually receive:
 * sampled still frames (plain images) and the extracted audio track.
 *
 * Chat APIs cannot carry a video file itself. The old path base64'd the
 * whole video into the request, and OpenAI-compatible routes then stuffed
 * it into an image slot — which is why every model failed or ignored
 * video attachments. Frames and audio are formats the pipes can carry.
 */
object VideoFrameExtractor {

    const val MAX_FRAMES = 8
    private const val FRAME_INTERVAL_MS = 5_000L
    private const val MAX_FRAME_DIM = 768
    private const val MAX_AUDIO_BYTES = 24 * 1024 * 1024

    fun frameCountFor(durationMs: Long): Int {
        if (durationMs <= 0L) return 4
        return (durationMs / FRAME_INTERVAL_MS).toInt().coerceIn(1, MAX_FRAMES)
    }

    /**
     * Samples evenly spaced frames from the video at [uri] and returns
     * them as JPEG data URIs, in chronological order. Empty on failure.
     */
    fun extractFrameDataUris(context: Context, uri: Uri, durationHintMs: Long): List<String> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull()?.takeIf { it > 0 } ?: durationHintMs
            val count = frameCountFor(durationMs)
            val frames = mutableListOf<String>()
            for (i in 0 until count) {
                val timeUs = if (durationMs > 0L) {
                    (durationMs * (i + 0.5) / count * 1000).toLong()
                } else {
                    i * FRAME_INTERVAL_MS * 1000L
                }
                val bitmap = runCatching {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }.getOrNull() ?: continue
                val scaled = scaleDown(bitmap)
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
                frames += "data:image/jpeg;base64," +
                    Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
            }
            frames
        } catch (e: Exception) {
            emptyList()
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Extracts the AAC audio track without re-encoding and returns
     * (dataUri, mimeType), or null when there is no usable audio track.
     * Providers that accept audio input (e.g. Gemini inline data) can
     * transcribe and understand the speech from this; other providers
     * simply skip the payload.
     */
    fun extractAudioDataUri(context: Context, uri: Uri): Pair<String, String>? {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var tempFile: File? = null
        return try {
            extractor.setDataSource(context, uri, null)
            var audioTrack = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    // MediaMuxer's MPEG_4 output only carries AAC reliably.
                    if (mime != MediaFormat.MIMETYPE_AUDIO_AAC) return null
                    audioTrack = i
                    audioFormat = format
                    break
                }
            }
            if (audioTrack < 0 || audioFormat == null) return null
            extractor.selectTrack(audioTrack)

            tempFile = File.createTempFile("video_audio_", ".m4a", context.cacheDir)
            muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val buffer = ByteBuffer.allocate(256 * 1024)
            val info = android.media.MediaCodec.BufferInfo()
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = extractor.sampleTime
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(muxTrack, buffer, info)
                extractor.advance()
            }
            muxer.stop()

            val bytes = tempFile.readBytes()
            if (bytes.isEmpty() || bytes.size > MAX_AUDIO_BYTES) return null
            val dataUri = "data:audio/mp4;base64," +
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            dataUri to "audio/mp4"
        } catch (e: Exception) {
            null
        } finally {
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
            tempFile?.delete()
        }
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= MAX_FRAME_DIM) return bitmap
        val ratio = MAX_FRAME_DIM.toFloat() / maxSide
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
