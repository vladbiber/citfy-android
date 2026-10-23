package com.ctify.player.player

import android.content.Context
import android.os.Bundle
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
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.ctify.player.CtifyApp
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** ExoPlayer + MediaSession; media3 provides the notification and lock-screen controls. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var playback: PlaybackController? = null

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

        playback = PlaybackController(this, player, (application as CtifyApp).repository)
        mediaSession = MediaSession.Builder(this, player).setCallback(SessionCallback()).build()
    }

    /** Only Ctify itself may send the custom playback commands. */
    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            if (controller.packageName != packageName) return super.onConnect(session, controller)
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .apply { Cmd.ALL.forEach { add(SessionCommand(it, Bundle.EMPTY)) } }
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val ok = controller.packageName == packageName &&
                playback?.handle(customCommand.customAction, args) == true
            return Futures.immediateFuture(SessionResult(if (ok) SessionResult.RESULT_SUCCESS else SessionError.ERROR_NOT_SUPPORTED))
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        playback?.release()
        playback = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
