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
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.extractor.DefaultExtractorsFactory
import com.ctify.player.radio.RewindRecorder
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.ctify.player.player.fx.FxProcessorChain
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.ctify.player.CtifyApp
import com.ctify.player.net.NetworkGate
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
        com.ctify.player.player.sound.SoundEngine.init(this)
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
        // http(s) goes through the network gate; files, content:// and assets stay local
        val dataSources = RoutingDataSourceFactory(DefaultDataSource.Factory(this, OkHttpDataSource.Factory(NetworkGate.mediaClient)))
        // the rewind buffer has no length; constant-bitrate seeking makes MP3 and AAC seekable anyway
        val extractors = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
        RewindRecorder.clearStale(this)
        val player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSources, extractors))
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
        // YouTube's progressive file carries video too; this is an audio player
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
        // one unreadable file should not end the session: skip it and keep going
        var audioRetries = 0
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) audioRetries = 0
            }

            override fun onPlayerError(error: PlaybackException) {
                // radio reconnects on its own; skipping would jump to nothing
                if (playback?.onPlayerError(error) == true) return
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

        val app = application as CtifyApp
        playback = PlaybackController(this, player, app.repository, app.radio, ::showStationButtons)
        mediaSession = MediaSession.Builder(this, player).setCallback(SessionCallback()).build()
    }

    /** Live radio has one item, so the system next/previous buttons switch stations instead. */
    private fun showStationButtons(radio: Boolean) {
        val session = mediaSession ?: return
        session.setMediaButtonPreferences(
            if (!radio) emptyList() else listOf(
                CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                    .setSessionCommand(SessionCommand(Cmd.PREVIOUS, Bundle.EMPTY))
                    .setDisplayName("Previous station")
                    .setSlots(CommandButton.SLOT_BACK)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_NEXT)
                    .setSessionCommand(SessionCommand(Cmd.NEXT, Bundle.EMPTY))
                    .setDisplayName("Next station")
                    .setSlots(CommandButton.SLOT_FORWARD)
                    .build(),
            )
        )
    }

    /** Only Ctify itself may send most custom commands; next/previous station are open to all. */
    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val allowed = if (controller.packageName == packageName) Cmd.ALL else Cmd.PUBLIC
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .apply { allowed.forEach { add(SessionCommand(it, Bundle.EMPTY)) } }
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
            val action = customCommand.customAction
            val ok = (controller.packageName == packageName || action in Cmd.PUBLIC) &&
                playback?.handle(action, args) == true
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
