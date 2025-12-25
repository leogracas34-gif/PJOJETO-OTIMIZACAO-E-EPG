package com.vltv.play

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SeriesDetailsActivity : AppCompatActivity() {

    private var seriesId: Int = 0
    private var seriesName: String = ""
    private var seriesIcon: String? = null
    private var seriesRating: String = "0.0"

    private lateinit var imgPoster: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvRating: TextView
    private lateinit var tvGenre: TextView
    private lateinit var tvPlot: TextView
    private lateinit var btnSeasonSelector: TextView
    private lateinit var rvEpisodes: RecyclerView
    private lateinit var btnFavoriteSeries: ImageButton

    private lateinit var btnPlaySeries: Button
    private lateinit var btnDownloadEpisodeArea: LinearLayout
    private lateinit var imgDownloadEpisodeState: ImageView
    private lateinit var tvDownloadEpisodeState: TextView

    private lateinit var btnDownloadSeason: Button

    private var episodesBySeason: Map<String, List<EpisodeStream>> = emptyMap()
    private var sortedSeasons: List<String> = emptyList()
    private var currentSeason: String = ""
    private var currentEpisode: EpisodeStream? = null

    private enum class DownloadState { BAIXAR, BAIXANDO, BAIXADO }
    private var downloadState: DownloadState = DownloadState.BAIXAR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_series_details)

        seriesId = intent.getIntExtra("series_id", 0)
        seriesName = intent.getStringExtra("name") ?: ""
        seriesIcon = intent.getStringExtra("icon")
        seriesRating = intent.getStringExtra("rating") ?: "0.0"

        imgPoster = findViewById(R.id.imgPosterSeries)
        tvTitle = findViewById(R.id.tvSeriesTitle)
        tvRating = findViewById(R.id.tvSeriesRating)
        tvGenre = findViewById(R.id.tvSeriesGenre)
        tvPlot = findViewById(R.id.tvSeriesPlot)
        btnSeasonSelector = findViewById(R.id.btnSeasonSelector)
        rvEpisodes = findViewById(R.id.rvEpisodes)
        btnFavoriteSeries = findViewById(R.id.btnFavoriteSeries)

        btnPlaySeries = findViewById(R.id.btnPlaySeries)

        btnDownloadEpisodeArea = findViewById(R.id.btnDownloadSeriesArea)
        imgDownloadEpisodeState = findViewById(R.id.imgDownloadSeriesState)
        tvDownloadEpisodeState = findViewById(R.id.tvDownloadSeriesState)

        btnDownloadSeason = findViewById(R.id.btnDownloadSeason)

        if (isTelevisionDevice()) {
            btnDownloadEpisodeArea.visibility = View.GONE
            btnDownloadSeason.visibility = View.GONE
        }

        tvTitle.text = seriesName
        tvRating.text = "Nota: $seriesRating"
        tvGenre.text = "Gênero: ..."
        tvPlot.text = "Sinopse..."

        btnSeasonSelector.setBackgroundColor(Color.parseColor("#333333"))

        Glide.with(this)
            .load(seriesIcon)
            .placeholder(R.mipmap.ic_launcher)
            .into(imgPoster)

        rvEpisodes.layoutManager = LinearLayoutManager(this)

        val isFavInicial = getFavSeries(this).contains(seriesId)
        atualizarIconeFavoritoSerie(isFavInicial)

        btnFavoriteSeries.setOnClickListener {
            val favs = getFavSeries(this)
            val novoFav: Boolean
            if (favs.contains(seriesId)) {
                favs.remove(seriesId)
                novoFav = false
            } else {
                favs.add(seriesId)
                novoFav = true
            }
            saveFavSeries(this, favs)
            atualizarIconeFavoritoSerie(novoFav)
        }

        btnSeasonSelector.setOnClickListener {
            mostrarSeletorDeTemporada()
        }

        btnPlaySeries.setOnClickListener {
            val ep = currentEpisode
            if (ep == null) {
                Toast.makeText(this, "Selecione um episódio.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            abrirPlayer(ep, false)
        }

        restaurarEstadoDownload()

        btnDownloadEpisodeArea.setOnClickListener {
            val ep = currentEpisode
            if (ep == null) {
                Toast.makeText(this, "Selecione um episódio para baixar.", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            when (downloadState) {
                DownloadState.BAIXAR -> {
                    val eid = ep.id.toIntOrNull() ?: 0
                    if (eid == 0) {
                        Toast.makeText(this, "ID do episódio inválido.", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    val url = montarUrlEpisodio(ep)

                    val safeTitle = seriesName
                        .replace("[^a-zA-Z0-9 _.-]".toRegex(), "_")
                        .ifBlank { "serie" }
                    val fileName =
                        "${safeTitle}_T${currentSeason}E${ep.episode_num}_${eid}.mp4"

                    DownloadHelper.enqueueDownload(
                        this,
                        url,
                        fileName,
                        logicalId = "series_$eid",
                        type = "series"
                    )

                    Toast.makeText(
                        this,
                        "Baixando episódio T${currentSeason}E${ep.episode_num}",
                        Toast.LENGTH_SHORT
                    ).show()

                    setDownloadState(DownloadState.BAIXANDO, ep)
                }

                DownloadState.BAIXANDO -> {
                    val popup = PopupMenu(this, btnDownloadEpisodeArea)
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
                    Toast.makeText(this, "Episódio já baixado.", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, DownloadsActivity::class.java))
                }
            }
        }

        btnDownloadSeason.setOnClickListener {
            if (currentSeason.isBlank()) {
                Toast.makeText(this, "Nenhuma temporada selecionada.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val lista = episodesBySeason[currentSeason] ?: emptyList()
            if (lista.isEmpty()) {
                Toast.makeText(this, "Sem episódios nesta temporada.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle("Baixar temporada")
                .setMessage("Baixar todos os ${lista.size} episódios da temporada $currentSeason?")
                .setPositiveButton("Sim") { _, _ ->
                    baixarTemporadaAtual(lista)
                }
                .setNegativeButton("Não", null)
                .show()
        }

        carregarSeriesInfo()
    }

    override fun onResume() {
        super.onResume()
        restaurarEstadoDownload()
    }

    private fun getFavSeries(context: Context): MutableSet<Int> {
        val prefs = context.getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val set = prefs.getStringSet("fav_series", emptySet()) ?: emptySet()
        return set.mapNotNull { it.toIntOrNull() }.toMutableSet()
    }

    private fun saveFavSeries(context: Context, ids: Set<Int>) {
        val prefs = context.getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet("fav_series", ids.map { it.toString() }.toSet())
            .apply()
    }

    private fun atualizarIconeFavoritoSerie(isFav: Boolean) {
        val res = if (isFav) R.drawable.ic_star_filled else R.drawable.ic_star_border
        btnFavoriteSeries.setImageResource(res)
    }

    private fun carregarSeriesInfo() {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val username = prefs.getString("username", "") ?: ""
        val password = prefs.getString("password", "") ?: ""

        XtreamApi.service.getSeriesInfoV2(username, password, seriesId = seriesId)
            .enqueue(object : Callback<SeriesInfoResponse> {
                override fun onResponse(
                    call: Call<SeriesInfoResponse>,
                    response: Response<SeriesInfoResponse>
                ) {
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        episodesBySeason = body.episodes ?: emptyMap()
                        sortedSeasons = episodesBySeason.keys.sortedBy { it.toIntOrNull() ?: 0 }

                        if (sortedSeasons.isNotEmpty()) {
                            mudarTemporada(sortedSeasons.first())
                        } else {
                            btnSeasonSelector.text = "Indisponível"
                        }
                    }
                }

                override fun onFailure(call: Call<SeriesInfoResponse>, t: Throwable) {
                    Toast.makeText(
                        this@SeriesDetailsActivity,
                        "Erro de conexão",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun mostrarSeletorDeTemporada() {
        if (sortedSeasons.isEmpty()) return

        val nomes = sortedSeasons.map { "Temporada $it" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Escolha a Temporada")
            .setItems(nomes) { _, which ->
                val seasonKey = sortedSeasons[which]
                mudarTemporada(seasonKey)
            }
            .show()
    }

    private fun mudarTemporada(seasonKey: String) {
        currentSeason = seasonKey
        btnSeasonSelector.text = "Temporada $seasonKey ▼"

        val lista = episodesBySeason[seasonKey] ?: emptyList()
        if (lista.isNotEmpty()) {
            currentEpisode = lista.first()
            restaurarEstadoDownload()
        }

        rvEpisodes.adapter = EpisodeAdapter(lista) { ep, _ ->
            currentEpisode = ep
            restaurarEstadoDownload()
            abrirPlayer(ep, true)
        }
    }

    private fun abrirPlayer(ep: EpisodeStream, usarResume: Boolean) {
        val streamId = ep.id.toIntOrNull() ?: 0
        val ext = ep.container_extension ?: "mp4"

        val lista = episodesBySeason[currentSeason] ?: emptyList()
        val position = lista.indexOfFirst { it.id == ep.id }
        val nextEp = lista.getOrNull(position + 1)
        val nextStreamId = nextEp?.id?.toIntOrNull() ?: 0
        val nextChannelName = nextEp?.let {
            "T${currentSeason}E${it.episode_num} - $seriesName"
        }

        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val keyBase = "series_resume_$streamId"
        val pos = prefs.getLong("${keyBase}_pos", 0L)
        val dur = prefs.getLong("${keyBase}_dur", 0L)
        val existe = usarResume && pos > 30_000L && dur > 0L && pos < (dur * 0.95).toLong()

        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra("stream_id", streamId)
        intent.putExtra("stream_ext", ext)
        intent.putExtra("stream_type", "series")
        intent.putExtra(
            "channel_name",
            "T${currentSeason}E${ep.episode_num} - $seriesName"
        )
        if (existe) {
            intent.putExtra("start_position_ms", pos)
        }
        if (nextStreamId != 0) {
            intent.putExtra("next_stream_id", nextStreamId)
            if (nextChannelName != null) {
                intent.putExtra("next_channel_name", nextChannelName)
            }
        }
        startActivity(intent)
    }

    private fun montarUrlEpisodio(ep: EpisodeStream): String {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val user = prefs.getString("username", "") ?: ""
        val pass = prefs.getString("password", "") ?: ""

        val serverList = listOf(
            "http://tvblack.shop",
            "http://firewallnaousardns.xyz:80",
            "http://fibercdn.sbs"
        )
        val server = serverList.first()

        val eid = ep.id.toIntOrNull() ?: 0
        val ext = ep.container_extension ?: "mp4"

        return montarUrlStream(
            server = server,
            streamType = "series",
            user = user,
            pass = pass,
            id = eid,
            ext = ext
        )
    }

    private fun baixarTemporadaAtual(lista: List<EpisodeStream>) {
        var count = 0
        for (ep in lista) {
            val eid = ep.id.toIntOrNull() ?: continue
            val url = montarUrlEpisodio(ep)

            val safeTitle = seriesName
                .replace("[^a-zA-Z0-9 _.-]".toRegex(), "_")
                .ifBlank { "serie" }
            val fileName = "${safeTitle}_T${currentSeason}E${ep.episode_num}_${eid}.mp4"

            DownloadHelper.enqueueDownload(
                this,
                url,
                fileName,
                logicalId = "series_$eid",
                type = "series"
            )

            count++
        }

        Toast.makeText(
            this,
            "Baixando $count episódios da temporada $currentSeason",
            Toast.LENGTH_LONG
        ).show()

        currentEpisode?.let { setDownloadState(DownloadState.BAIXANDO, it) }
    }

    private fun getProgressText(): String {
        val ep = currentEpisode ?: return "Baixar episódio"
        val episodeId = ep.id.toIntOrNull() ?: 0
        if (episodeId == 0) return "Baixar episódio"

        val progress = DownloadHelper.getDownloadProgress(this, "series_$episodeId")
        return when (downloadState) {
            DownloadState.BAIXAR -> "Baixar episódio"
            DownloadState.BAIXANDO -> "Baixando ${progress}%"
            DownloadState.BAIXADO -> "Baixado 100%"
        }
    }

    private fun setDownloadState(state: DownloadState, ep: EpisodeStream?) {
        downloadState = state

        val episodeId = ep?.id?.toIntOrNull() ?: currentEpisode?.id?.toIntOrNull() ?: 0
        if (episodeId != 0) {
            val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("series_download_state_$episodeId", state.name)
                .apply()
        }

        when (state) {
            DownloadState.BAIXAR -> {
                imgDownloadEpisodeState.setImageResource(R.drawable.ic_dl_arrow)
                tvDownloadEpisodeState.text = getProgressText()
            }

            DownloadState.BAIXANDO -> {
                imgDownloadEpisodeState.setImageResource(R.drawable.ic_dl_loading)
                tvDownloadEpisodeState.text = getProgressText()
            }

            DownloadState.BAIXADO -> {
                imgDownloadEpisodeState.setImageResource(R.drawable.ic_dl_done)
                tvDownloadEpisodeState.text = getProgressText()
            }
        }
    }

    private fun restaurarEstadoDownload() {
        val ep = currentEpisode ?: run {
            setDownloadState(DownloadState.BAIXAR, null)
            return
        }
        val episodeId = ep.id.toIntOrNull() ?: 0
        if (episodeId == 0) {
            setDownloadState(DownloadState.BAIXAR, ep)
            return
        }

        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getString(
            "series_download_state_$episodeId",
            DownloadState.BAIXAR.name
        )
        val state = try {
            DownloadState.valueOf(saved ?: DownloadState.BAIXAR.name)
        } catch (_: Exception) {
            DownloadState.BAIXAR
        }
        setDownloadState(state, ep)
    }

    class EpisodeAdapter(
        private val list: List<EpisodeStream>,
        private val onClick: (EpisodeStream, Int) -> Unit
    ) : RecyclerView.Adapter<EpisodeAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvTitle: TextView = v.findViewById(R.id.tvEpisodeTitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_episode, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val ep = list[position]
            val epNum = ep.episode_num.toString().padStart(2, '0')
            holder.tvTitle.text = "E$epNum - ${ep.title}"
            holder.itemView.setOnClickListener { onClick(ep, position) }
        }

        override fun getItemCount(): Int = list.size
    }
}
