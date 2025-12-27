package com.vltv.play

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var loading: View
    private lateinit var tvChannelName: TextView
    private lateinit var tvNowPlaying: TextView
    private lateinit var btnAspect: ImageButton
    private lateinit var topBar: View

    private lateinit var nextEpisodeContainer: View
    private lateinit var tvNextEpisodeTitle: TextView
    private lateinit var btnPlayNextEpisode: Button

    private var player: ExoPlayer? = null

    private var streamId = 0
    private var streamExtension = "ts"
    private var streamType = "live"
    private var nextStreamId: Int = 0
    private var nextChannelName: String? = null
    private var startPositionMs: Long = 0L

    private var offlineUri: String? = null

    private val serverList = listOf(
        "http://tvblack.shop",
        "http://firewallnaousardns.xyz:80",
        "http://fibercdn.sbs"
    )

    private var serverIndex = 0
    private val extensoesTentativa = mutableListOf<String>()
    private var extIndex = 0

    private val USER_AGENT = "IPTVSmartersPro"

    private val handler = Handler(Looper.getMainLooper())
    private val nextChecker = object : Runnable {
        override fun run() {
            val p = player ?: return
            if (streamType == "series" && nextStreamId != 0) {
                val dur = p.duration
                val pos = p.currentPosition
                if (dur > 0) {
                    val remaining = dur - pos
                    if (remaining in 1..60_000) {
                        val seconds = (remaining / 1000L).toInt()
                        tvNextEpisodeTitle.text = "Próximo episódio em ${seconds}s"
                        nextEpisodeContainer.visibility = View.VISIBLE
                        if (remaining <= 1000L) {
                            nextEpisodeContainer.visibility = View.GONE
                        }
                    } else if (remaining > 60_000) {
                        nextEpisodeContainer.visibility = View.GONE
                    }
                }
                handler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        playerView = findViewById(R.id.playerView)
        loading = findViewById(R.id.loading)
        tvChannelName = findViewById(R.id.tvChannelName)
        tvNowPlaying = findViewById(R.id.tvNowPlaying)
        btnAspect = findViewById(R.id.btnAspect)
        topBar = findViewById(R.id.topBar)

        nextEpisodeContainer = findViewById(R.id.nextEpisodeContainer)
        tvNextEpisodeTitle = findViewById(R.id.tvNextEpisodeTitle)
        btnPlayNextEpisode = findViewById(R.id.btnPlayNextEpisode)

        streamId = intent.getIntExtra("stream_id", 0)
        streamExtension = intent.getStringExtra("stream_ext") ?: "ts"
        streamType = intent.getStringExtra("stream_type") ?: "live"
        startPositionMs = intent.getLongExtra("start_position_ms", 0L)
        nextStreamId = intent.getIntExtra("next_stream_id", 0)
        nextChannelName = intent.getStringExtra("next_channel_name")

        offlineUri = intent.getStringExtra("offline_uri")

        val channelName = intent.getStringExtra("channel_name") ?: ""
        tvChannelName.text = if (channelName.isNotBlank()) channelName else "Canal"

        tvNowPlaying.text = if (streamType == "live") "Carregando programação..." else ""

        btnAspect.setOnClickListener {
            val current = playerView.resizeMode
            val next = when (current) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT -> {
                    Toast.makeText(this, "Modo: Preencher", Toast.LENGTH_SHORT).show()
                    AspectRatioFrameLayout.RESIZE_MODE_FILL
                }
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> {
                    Toast.makeText(this, "Modo: Zoom", Toast.LENGTH_SHORT).show()
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
                else -> {
                    Toast.makeText(this, "Modo: Ajustar", Toast.LENGTH_SHORT).show()
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            }
            playerView.resizeMode = next
        }

        playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                topBar.visibility = visibility
            }
        )

        btnPlayNextEpisode.setOnClickListener {
            if (nextStreamId != 0) abrirProximoEpisodio()
        }

        if (streamType == "movie") {
            extensoesTentativa.add(streamExtension)
            extensoesTentativa.add("mp4")
            extensoesTentativa.add("mkv")
        } else {
            extensoesTentativa.add("m3u8")
            extensoesTentativa.add("ts")
            extensoesTentativa.add("")
        }

        iniciarPlayer()

        if (streamType == "live" && streamId != 0) {
            carregarEpg()
        }

        if (streamType == "series" && nextStreamId != 0) {
            handler.post(nextChecker)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        }
    }

    @OptIn(UnstableApi::class)
    private fun iniciarPlayer() {
        if (streamType == "vod_offline") {
            val uriStr = offlineUri
            if (uriStr.isNullOrBlank()) {
                Toast.makeText(this, "Arquivo offline inválido.", Toast.LENGTH_LONG).show()
                loading.visibility = View.GONE
                return
            }

            player?.release()
            player = ExoPlayer.Builder(this).build()
            playerView.player = player

            val mediaItem = MediaItem.fromUri(Uri.parse(uriStr))
            player?.setMediaItem(mediaItem)
            player?.prepare()
            player?.playWhenReady = true

            player?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> loading.visibility = View.GONE
                        Player.STATE_BUFFERING -> loading.visibility = View.VISIBLE
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(
                        this@PlayerActivity,
                        "Erro ao reproduzir arquivo offline.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
            return
        }

        if (extIndex >= extensoesTentativa.size) {
            serverIndex++
            extIndex = 0
            if (serverIndex >= serverList.size) {
                Toast.makeText(
                    this,
                    "Falha ao reproduzir: Servidores indisponíveis.",
                    Toast.LENGTH_LONG
                ).show()
                loading.visibility = View.GONE
                return
            }
        }

        val currentServer = serverList[serverIndex]
        val currentExt = extensoesTentativa[extIndex]

        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val user = prefs.getString("username", "") ?: ""
        val pass = prefs.getString("password", "") ?: ""

        val url = montarUrlStream(
            server = currentServer,
            streamType = streamType,
            user = user,
            pass = pass,
            id = streamId,
            ext = currentExt
        )

        player?.release()

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12000)
            .setReadTimeoutMs(15000)

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val isLive = streamType == "live"
        val minBufferMs = if (isLive) 5_000 else 15_000
        val maxBufferMs = if (isLive) 15_000 else 50_000
        val playBufferMs = if (isLive) 1_500 else 3_000
        val playRebufferMs = if (isLive) 3_000 else 5_000

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                playBufferMs,
                playRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()  // [web:627][web:628]

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()

        playerView.player = player

        try {
            val mediaItem = MediaItem.fromUri(Uri.parse(url))
            player?.setMediaItem(mediaItem)
            player?.prepare()

            if (startPositionMs > 0L && (streamType == "movie" || streamType == "series")) {
                player?.seekTo(startPositionMs)
            }

            player?.playWhenReady = true
        } catch (e: Exception) {
            tentarProximo()
            return
        }

        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> loading.visibility = View.GONE
                    Player.STATE_BUFFERING -> loading.visibility = View.VISIBLE
                    Player.STATE_ENDED -> {
                        if (streamType == "movie") {
                            clearMovieResume(streamId)
                        } else if (streamType == "series") {
                            clearSeriesResume(streamId)
                            if (nextStreamId != 0) abrirProximoEpisodio()
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                loading.visibility = View.VISIBLE
                handler.postDelayed({ tentarProximo() }, 1000L)  // [web:631][web:616]
            }
        })
    }

    private fun tentarProximo() {
        extIndex++
        iniciarPlayer()
    }

    private fun abrirProximoEpisodio() {
        if (nextStreamId == 0) return

        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra("stream_id", nextStreamId)
        intent.putExtra("stream_ext", "mp4")
        intent.putExtra("stream_type", "series")
        intent.putExtra(
            "channel_name",
            nextChannelName ?: tvChannelName.text.toString()
        )
        startActivity(intent)
        finish()
    }

    private fun getMovieKey(id: Int) = "movie_resume_$id"

    private fun saveMovieResume(id: Int, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        val percent = positionMs.toDouble() / durationMs.toDouble()
        if (positionMs < 30_000L || percent > 0.95) {
            clearMovieResume(id)
            return
        }
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("${getMovieKey(id)}_pos", positionMs)
            .putLong("${getMovieKey(id)}_dur", durationMs)
            .apply()
    }

    private fun clearMovieResume(id: Int) {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("${getMovieKey(id)}_pos")
            .remove("${getMovieKey(id)}_dur")
            .apply()
    }

    private fun getSeriesKey(episodeStreamId: Int) = "series_resume_$episodeStreamId"

    private fun saveSeriesResume(id: Int, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        val percent = positionMs.toDouble() / durationMs.toDouble()
        if (positionMs < 30_000L || percent > 0.95) {
            clearSeriesResume(id)
            return
        }
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("${getSeriesKey(id)}_pos", positionMs)
            .putLong("${getSeriesKey(id)}_dur", durationMs)
            .apply()
    }

    private fun clearSeriesResume(id: Int) {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("${getSeriesKey(id)}_pos")
            .remove("${getSeriesKey(id)}_dur")
            .apply()
    }

    private fun decodeBase64(text: String?): String {
        return try {
            if (text.isNullOrEmpty()) "" else String(
                Base64.decode(text, Base64.DEFAULT),
                Charsets.UTF_8
            )
        } catch (e: Exception) {
            text ?: ""
        }
    }

    private fun carregarEpg() {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val user = prefs.getString("username", "") ?: ""
        val pass = prefs.getString("password", "") ?: ""

        if (user.isBlank() || pass.isBlank()) {
            tvNowPlaying.text = "Sem informação de programação"
            return
        }

        val streamIdString = streamId.toString()

        XtreamApi.service.getShortEpg(
            user = user,
            pass = pass,
            streamId = streamIdString,
            limit = 2
        ).enqueue(object : Callback<EpgWrapper> {
            override fun onResponse(
                call: Call<EpgWrapper>,
                response: Response<EpgWrapper>
            ) {
                if (!response.isSuccessful || response.body()?.epg_listings.isNullOrEmpty()) {
                    tvNowPlaying.text = "Sem informação de programação"
                    return
                }

                val list = response.body()!!.epg_listings!!
                val epg = list.firstOrNull()
                if (epg == null || epg.title.isNullOrBlank()) {
                    tvNowPlaying.text = "Sem informação de programação"
                    return
                }

                val titulo = decodeBase64(epg.title)
                val inicio = epg.start ?: ""
                val fim = epg.stop ?: epg.end.orEmpty()
                val textoHora = if (inicio.isNotBlank() && fim.isNotBlank()) {
                    " ($inicio - $fim)"
                } else ""

                tvNowPlaying.text = "$titulo$textoHora"
            }

            override fun onFailure(call: Call<EpgWrapper>, t: Throwable) {
                tvNowPlaying.text = "Falha ao carregar programação"
            }
        })
    }

    override fun onPause() {
        super.onPause()
        val p = player ?: return
        if (streamType == "movie") {
            saveMovieResume(streamId, p.currentPosition, p.duration)
        } else if (streamType == "series") {
            saveSeriesResume(streamId, p.currentPosition, p.duration)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(nextChecker)
        val p = player
        if (p != null) {
            if (streamType == "movie") {
                saveMovieResume(streamId, p.currentPosition, p.duration)
            } else if (streamType == "series") {
                saveSeriesResume(streamId, p.currentPosition, p.duration)
            }
        }
        player?.release()
        player = null
    }
}
