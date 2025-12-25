package com.vltv.play

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.bumptech.glide.Glide
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DetailsActivity : AppCompatActivity() {

    private lateinit var tvPlot: TextView
    private lateinit var tvGenre: TextView
    private lateinit var tvCast: TextView
    private lateinit var tvDirector: TextView
    private lateinit var tvRating: TextView
    private lateinit var imgPoster: ImageView
    private lateinit var btnPlay: Button
    private lateinit var btnFavorite: ImageButton
    private lateinit var btnResume: Button

    private lateinit var btnDownloadArea: LinearLayout
    private lateinit var imgDownloadState: ImageView
    private lateinit var tvDownloadState: TextView

    private var streamId: Int = 0
    private var extension: String = "mp4"
    private var movieTitle: String = "Sem Título"

    private enum class DownloadState { BAIXAR, BAIXANDO, BAIXADO }
    private var downloadState: DownloadState = DownloadState.BAIXAR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)

        movieTitle = intent.getStringExtra("title") ?: "Sem Título"
        val icon = intent.getStringExtra("icon")
        streamId = intent.getIntExtra("stream_id", 0)
        extension = intent.getStringExtra("extension") ?: "mp4"

        tvPlot = findViewById(R.id.tvPlot)
        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        tvGenre = findViewById(R.id.tvGenre)
        tvCast = findViewById(R.id.tvCast)
        tvDirector = findViewById(R.id.tvDirector)
        tvRating = findViewById(R.id.tvRating)
        imgPoster = findViewById(R.id.imgPoster)
        btnPlay = findViewById(R.id.btnPlay)
        btnFavorite = findViewById(R.id.btnFavorite)
        btnResume = findViewById(R.id.btnResume)

        btnDownloadArea = findViewById(R.id.btnDownloadArea)
        imgDownloadState = findViewById(R.id.imgDownloadState)
        tvDownloadState = findViewById(R.id.tvDownloadState)

        if (isTelevisionDevice()) {
            btnDownloadArea.visibility = View.GONE
        }

        tvTitle.text = movieTitle

        Glide.with(this)
            .load(icon)
            .placeholder(R.mipmap.ic_launcher)
            .into(imgPoster)

        val isFavInicial = getFavMovies(this).contains(streamId)
        atualizarIconeFavorito(isFavInicial)

        btnFavorite.setOnClickListener {
            val favs = getFavMovies(this)
            val novoFav: Boolean
            if (favs.contains(streamId)) {
                favs.remove(streamId)
                novoFav = false
            } else {
                favs.add(streamId)
                novoFav = true
            }
            saveFavMovies(this, favs)
            atualizarIconeFavorito(novoFav)
        }

        configurarBotaoResume()

        btnPlay.setOnClickListener {
            abrirPlayer(movieTitle, startPositionMs = 0L)
        }
        btnPlay.requestFocus()

        btnResume.setOnClickListener {
            val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
            val keyBase = "movie_resume_$streamId"
            val pos = prefs.getLong("${keyBase}_pos", 0L)
            abrirPlayer(movieTitle, startPositionMs = pos)
        }

        restaurarEstadoDownload()

        btnDownloadArea.setOnClickListener {
            when (downloadState) {
                DownloadState.BAIXAR -> {
                    val url = montarUrlFilme()

                    val safeTitle = movieTitle
                        .replace("[^a-zA-Z0-9 _.-]".toRegex(), "_")
                        .ifBlank { "movie" }
                    val fileName = "${safeTitle}_$streamId.$extension"

                    DownloadHelper.enqueueDownload(
                        this,
                        url,
                        fileName,
                        logicalId = "movie_$streamId",
                        type = "movie"
                    )

                    Toast.makeText(this, "Download iniciado", Toast.LENGTH_SHORT).show()
                    setDownloadState(DownloadState.BAIXANDO)
                }

                DownloadState.BAIXANDO -> {
                    val popup = PopupMenu(this, btnDownloadArea)
                    popup.menu.add("Ir para Meus downloads")

                    popup.setOnMenuItemClickListener { item ->
                        when (item.title) {
                            "Ir para Meus downloads" -> {
                                startActivity(Intent(this, DownloadsActivity::class.java))
                                true
                            }

                            else -> false
                        }
                    }
                    popup.show()
                }

                DownloadState.BAIXADO -> {
                    Toast.makeText(this, "Arquivo já baixado", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, DownloadsActivity::class.java))
                }
            }
        }

        carregarDetalhes(streamId)
        carregarDetalhesTmdb(movieTitle)
    }

    override fun onResume() {
        super.onResume()
        restaurarEstadoDownload()
    }

    private fun montarUrlFilme(): String {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val user = prefs.getString("username", "") ?: ""
        val pass = prefs.getString("password", "") ?: ""

        val serverList = listOf(
            "http://tvblack.shop",
            "http://firewallnaousardns.xyz:80",
            "http://fibercdn.sbs"
        )
        val server = serverList.first()

        return montarUrlStream(
            server = server,
            streamType = "movie",
            user = user,
            pass = pass,
            id = streamId,
            ext = extension
        )
    }

    private fun abrirPlayer(name: String, startPositionMs: Long) {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra("stream_id", streamId)
        intent.putExtra("stream_ext", extension)
        intent.putExtra("stream_type", "movie")
        intent.putExtra("channel_name", name)
        if (startPositionMs > 0L) {
            intent.putExtra("start_position_ms", startPositionMs)
        }
        startActivity(intent)
    }

    private fun configurarBotaoResume() {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val keyBase = "movie_resume_$streamId"
        val pos = prefs.getLong("${keyBase}_pos", 0L)
        val dur = prefs.getLong("${keyBase}_dur", 0L)
        val existe = pos > 30_000L && dur > 0L && pos < (dur * 0.95).toLong()
        btnResume.visibility = if (existe) Button.VISIBLE else Button.GONE
    }

    private fun getProgressText(): String {
        val progress = DownloadHelper.getDownloadProgress(this, "movie_$streamId")
        return when (downloadState) {
            DownloadState.BAIXAR -> "Baixar"
            DownloadState.BAIXANDO -> "Baixando ${progress}%"
            DownloadState.BAIXADO -> "Baixado 100%"
        }
    }

    private fun setDownloadState(state: DownloadState) {
        downloadState = state
        when (state) {
            DownloadState.BAIXAR -> {
                imgDownloadState.setImageResource(R.drawable.ic_dl_arrow)
                tvDownloadState.text = getProgressText()
            }

            DownloadState.BAIXANDO -> {
                imgDownloadState.setImageResource(R.drawable.ic_dl_loading)
                tvDownloadState.text = getProgressText()
            }

            DownloadState.BAIXADO -> {
                imgDownloadState.setImageResource(R.drawable.ic_dl_done)
                tvDownloadState.text = getProgressText()
            }
        }
        DownloadHelper.setDownloadState(this, "movie_$streamId", state.name)
    }

    private fun restaurarEstadoDownload() {
        val stateName = DownloadHelper.getDownloadState(this, "movie_$streamId")
        val state = try {
            DownloadState.valueOf(stateName)
        } catch (_: Exception) {
            DownloadState.BAIXAR
        }
        setDownloadState(state)
    }

    private fun getFavMovies(context: Context): MutableSet<Int> {
        val prefs = context.getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val set = prefs.getStringSet("fav_movies", emptySet()) ?: emptySet()
        return set.mapNotNull { it.toIntOrNull() }.toMutableSet()
    }

    private fun saveFavMovies(context: Context, ids: Set<Int>) {
        val prefs = context.getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet("fav_movies", ids.map { it.toString() }.toSet())
            .apply()
    }

    private fun atualizarIconeFavorito(isFav: Boolean) {
        val res = if (isFav) R.drawable.ic_star_filled else R.drawable.ic_star_border
        btnFavorite.setImageResource(res)
    }

    private fun carregarDetalhes(streamId: Int) {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val username = prefs.getString("username", "") ?: ""
        val password = prefs.getString("password", "") ?: ""

        XtreamApi.service.getVodInfo(username, password, vodId = streamId)
            .enqueue(object : Callback<VodInfoResponse> {
                override fun onResponse(
                    call: Call<VodInfoResponse>,
                    response: Response<VodInfoResponse>
                ) {
                    if (response.isSuccessful && response.body()?.info != null) {
                        val info = response.body()!!.info!!

                        tvPlot.text = info.plot ?: "Sinopse indisponível."
                        tvGenre.text = "Gênero: ${info.genre ?: "N/A"}"
                        tvCast.text = "Elenco: ${info.cast ?: "N/A"}"
                        tvRating.text = "Nota: ${info.rating ?: "N/A"}"
                        tvDirector.text = "Diretor: ${info.director ?: "N/A"}"

                        if (!info.movie_image.isNullOrEmpty()) {
                            Glide.with(this@DetailsActivity)
                                .load(info.movie_image)
                                .into(imgPoster)
                        }
                    } else {
                        tvPlot.text = "Não foi possível carregar detalhes."
                    }
                }

                override fun onFailure(call: Call<VodInfoResponse>, t: Throwable) {
                    tvPlot.text = "Erro de conexão ao buscar detalhes."
                }
            })
    }

    private fun carregarDetalhesTmdb(titulo: String) {
        val apiKey = TmdbConfig.API_KEY
        if (apiKey.isBlank()) return

        TmdbApi.service.searchMovie(apiKey, titulo)
            .enqueue(object : Callback<TmdbSearchResponse> {
                override fun onResponse(
                    call: Call<TmdbSearchResponse>,
                    response: Response<TmdbSearchResponse>
                ) {
                    val movie = response.body()?.results?.firstOrNull() ?: return

                    if (tvPlot.text.isNullOrBlank() || tvPlot.text == "Sinopse indisponível.") {
                        tvPlot.text = movie.overview ?: "Sinopse indisponível."
                    }

                    if (tvRating.text.isNullOrBlank() || tvRating.text.contains("N/A")) {
                        val nota = movie.vote_average ?: 0f
                        tvRating.text = "Nota: ${String.format("%.1f", nota)}"
                    }

                    if (movie.poster_path != null) {
                        val urlPoster = "https://image.tmdb.org/t/p/w500${movie.poster_path}"
                        Glide.with(this@DetailsActivity)
                            .load(urlPoster)
                            .into(imgPoster)
                    }
                }

                override fun onFailure(
                    call: Call<TmdbSearchResponse>,
                    t: Throwable
                ) {
                }
            })
    }
}
