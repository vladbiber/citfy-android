package com.ctify.player.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.ctify.player.player.fx.FxProcessorChain
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** ExoPlayer + MediaSession; media3 provides the notification and lock-screen controls. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        PlayerHub.init(this)
        // effects run in our own processor chain, after ExoPlayer's speed stage
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessorChain(FxProcessorChain(PlayerHub.fx) { PlayerHub.spectrum.value = it })
                .build()
        }
        val player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            // keeps the CPU awake with the screen off; without it playback dies in deep sleep
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        // one unreadable file should not end the session: skip it and keep going
        var audioRetries = 0
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) audioRetries = 0
            }

            override fun onPlayerError(error: PlaybackException) {
                // the audio output died (Bluetooth switch, call, audio service restart): pick up where it stopped
                if (error.errorCode in PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED..PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED &&
                    audioRetries++ < 3
                ) {
                    player.prepare()
                    player.play()
                } else if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }
        })

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
