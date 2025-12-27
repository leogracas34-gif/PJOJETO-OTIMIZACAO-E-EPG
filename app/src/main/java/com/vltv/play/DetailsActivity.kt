package com.vltv.play

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.bumptech.glide.Glide

class DetailsActivity : AppCompatActivity() {

    private var streamId: Int = 0
    private var name: String = ""
    private var icon: String? = null
    private var rating: String = "0.0"

    private lateinit var imgPoster: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvRating: TextView
    private lateinit var tvGenre: TextView
    private lateinit var tvDirector: TextView
    private lateinit var tvCast: TextView
    private lateinit var tvPlot: TextView
    private lateinit var btnPlay: Button
    private lateinit var btnResume: Button
    private lateinit var btnFavorite: ImageButton

    private lateinit var btnDownloadArea: LinearLayout
    private lateinit var imgDownloadState: ImageView
    private lateinit var tvDownloadState: TextView

    private enum class DownloadState { BAIXAR, BAIXANDO, BAIXADO }
    private var downloadState: DownloadState = DownloadState.BAIXAR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)

        streamId = intent.getIntExtra("stream_id", 0)
        name = intent.getStringExtra("name") ?: ""
        icon = intent.getStringExtra("icon")
        rating = intent.getStringExtra("rating") ?: "0.0"

        imgPoster = findViewById(R.id.imgPoster)
        tvTitle = findViewById(R.id.tvTitle)
        tvRating = findViewById(R.id.tvRating)
        tvGenre = findViewById(R.id.tvGenre)
        tvDirector = findViewById(R.id.tvDirector)
        tvCast = findViewById(R.id.tvCast)
        tvPlot = findViewById(R.id.tvPlot)
        btnPlay = findViewById(R.id.btnPlay)
        btnResume = findViewById(R.id.btnResume)
        btnFavorite = findViewById(R.id.btnFavorite)

        btnDownloadArea = findViewById(R.id.btnDownloadArea)
        imgDownloadState = findViewById(R.id.imgDownloadState)
        tvDownloadState = findViewById(R.id.tvDownloadState)

        if (isTelevisionDevice()) {
            btnDownloadArea.visibility = View.GONE
        }

        tvTitle.text = name
        tvRating.text = "Nota: $rating"
        tvGenre.text = "Gênero: ..."
        tvDirector.text = "Diretor: Informação não disponível"
        tvCast.text = "Elenco: Informação não disponível"
        tvPlot.text = "..."

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

        btnPlay.setOnClickListener {
            abrirPlayer(false)
        }

        btnResume.setOnClickListener {
            abrirPlayer(true)
        }

        restaurarEstadoDownload()
        btnDownloadArea.setOnClickListener {
            when (downloadState) {
                DownloadState.BAIXAR -> iniciarDownload()
                DownloadState.BAIXANDO -> mostrarMenuBaixando()
                DownloadState.BAIXADO -> {
                    Toast.makeText(this, "Filme já baixado.", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, DownloadsActivity::class.java))
                }
            }
        }

        verificarResume()
    }

    override fun onResume() {
        super.onResume()
        restaurarEstadoDownload()
        verificarResume()
    }

    // -------- Favoritos --------

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

    // -------- Resume / Player --------

    private fun verificarResume() {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val keyBase = "movie_resume_$streamId"
        val pos = prefs.getLong("${keyBase}_pos", 0L)
        val dur = prefs.getLong("${keyBase}_dur", 0L)
        val existe = pos > 30_000L && dur > 0L && pos < (dur * 0.95).toLong()
        btnResume.visibility = if (existe) View.VISIBLE else View.GONE
    }

    private fun abrirPlayer(usarResume: Boolean) {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val keyBase = "movie_resume_$streamId"
        val pos = prefs.getLong("${keyBase}_pos", 0L)
        val dur = prefs.getLong("${keyBase}_dur", 0L)
        val existe = usarResume && pos > 30_000L && dur > 0L && pos < (dur * 0.95).toLong()

        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra("stream_id", streamId)
        intent.putExtra("stream_ext", "mkv")
        intent.putExtra("stream_type", "movie")
        intent.putExtra("channel_name", name)
        if (existe) {
            intent.putExtra("start_position_ms", pos)
        }
        startActivity(intent)
    }

    // -------- Download --------

    private fun iniciarDownload() {
        if (streamId == 0) {
            Toast.makeText(this, "ID inválido.", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val user = prefs.getString("username", "") ?: ""
        val pass = prefs.getString("password", "") ?: ""

        val serverList = listOf(
            "http://tvblack.shop",
            "http://firewallnaousardns.xyz:80",
            "http://fibercdn.sbs"
        )
        val server = serverList.first()

        val url = montarUrlStream(
            server = server,
            streamType = "movie",
            user = user,
            pass = pass,
            id = streamId,
            ext = "mkv"
        )

        val safeTitle = name
            .replace("[^a-zA-Z0-9 _.-]".toRegex(), "_")
            .ifBlank { "filme" }
        val fileName = "${safeTitle}_$streamId.mkv"

        DownloadHelper.enqueueDownload(
            this,
            url,
            fileName,
            logicalId = "movie_$streamId",
            type = "movie"
        )

        Toast.makeText(this, "Baixando filme...", Toast.LENGTH_SHORT).show()
        setDownloadState(DownloadState.BAIXANDO)
    }

    private fun mostrarMenuBaixando() {
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

    private fun getProgressText(): String {
        val progress = DownloadHelper.getDownloadProgress(this, "movie_$streamId")
        return when (downloadState) {
            DownloadState.BAIXAR -> "Baixar filme"
            DownloadState.BAIXANDO -> "Baixando ${progress}%"
            DownloadState.BAIXADO -> "Baixado 100%"
        }
    }

    private fun setDownloadState(state: DownloadState) {
        downloadState = state

        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("movie_download_state_$streamId", state.name)
            .apply()

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
    }

    private fun restaurarEstadoDownload() {
        if (streamId == 0) {
            setDownloadState(DownloadState.BAIXAR)
            return
        }

        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val saved =
            prefs.getString("movie_download_state_$streamId", DownloadState.BAIXAR.name)
        val state = try {
            DownloadState.valueOf(saved ?: DownloadState.BAIXAR.name)
        } catch (_: Exception) {
            DownloadState.BAIXAR
        }
        setDownloadState(state)
    }
}
