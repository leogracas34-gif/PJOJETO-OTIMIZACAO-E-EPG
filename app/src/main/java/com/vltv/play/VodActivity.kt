package com.vltv.play

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class VodActivity : AppCompatActivity() {

    private lateinit var rvCategories: RecyclerView
    private lateinit var rvMovies: RecyclerView
    private lateinit var progressBar: View
    private lateinit var tvCategoryTitle: TextView

    private var username = ""
    private var password = ""
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_tv)

        rvCategories = findViewById(R.id.rvCategories)
        rvMovies = findViewById(R.id.rvChannels)
        progressBar = findViewById(R.id.progressBar)
        tvCategoryTitle = findViewById(R.id.tvCategoryTitle)

        prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        username = prefs.getString("username", "") ?: ""
        password = prefs.getString("password", "") ?: ""

        rvCategories.layoutManager = LinearLayoutManager(this)
        rvMovies.layoutManager = GridLayoutManager(this, 5)

        carregarCategorias()
    }

    private fun carregarCategorias() {
        progressBar.visibility = View.VISIBLE

        XtreamApi.service.getVodCategories(username, password)
            .enqueue(object : Callback<List<LiveCategory>> {
                override fun onResponse(
                    call: Call<List<LiveCategory>>,
                    response: Response<List<LiveCategory>>
                ) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        val originais = response.body()!!

                        val categorias = mutableListOf<LiveCategory>()
                        categorias.add(
                            LiveCategory(
                                category_id = "FAV",
                                category_name = "FAVORITOS"
                            )
                        )
                        categorias.addAll(originais)

                        rvCategories.adapter = VodCategoryAdapter(categorias) { categoria ->
                            if (categoria.id == "FAV") {
                                carregarFilmesFavoritos()
                            } else {
                                carregarFilmes(categoria)
                            }
                        }

                        if (categorias.size > 1) {
                            carregarFilmes(categorias[1])
                        }
                    } else {
                        Toast.makeText(
                            this@VodActivity,
                            "Erro ao carregar categorias",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<List<LiveCategory>>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@VodActivity,
                        "Falha de conexão",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun carregarFilmes(categoria: LiveCategory) {
        tvCategoryTitle.text = categoria.name
        progressBar.visibility = View.VISIBLE

        XtreamApi.service.getVodStreams(username, password, categoryId = categoria.id)
            .enqueue(object : Callback<List<VodStream>> {
                override fun onResponse(
                    call: Call<List<VodStream>>,
                    response: Response<List<VodStream>>
                ) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        rvMovies.adapter = VodAdapter(
                            response.body()!!,
                            onClick = { filme -> abrirDetalhes(filme) },
                            onDownloadClick = { filme -> mostrarMenuDownload(filme) }
                        )
                    }
                }

                override fun onFailure(call: Call<List<VodStream>>, t: Throwable) {
                    progressBar.visibility = View.GONE
                }
            })
    }

    private fun carregarFilmesFavoritos() {
        tvCategoryTitle.text = "FAVORITOS"
        progressBar.visibility = View.VISIBLE

        val favIds = getFavMovies(this)
        if (favIds.isEmpty()) {
            progressBar.visibility = View.GONE
            rvMovies.adapter = VodAdapter(
                emptyList(),
                onClick = {},
                onDownloadClick = { }
            )
            Toast.makeText(this, "Nenhum filme favorito.", Toast.LENGTH_SHORT).show()
            return
        }

        XtreamApi.service.getVodStreams(username, password, categoryId = "0")
            .enqueue(object : Callback<List<VodStream>> {
                override fun onResponse(
                    call: Call<List<VodStream>>,
                    response: Response<List<VodStream>>
                ) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        val todos = response.body()!!
                        val apenasFav = todos.filter { favIds.contains(it.id) }
                        rvMovies.adapter = VodAdapter(
                            apenasFav,
                            onClick = { filme -> abrirDetalhes(filme) },
                            onDownloadClick = { filme -> mostrarMenuDownload(filme) }
                        )
                    }
                }

                override fun onFailure(call: Call<List<VodStream>>, t: Throwable) {
                    progressBar.visibility = View.GONE
                }
            })
    }

    private fun abrirDetalhes(filme: VodStream) {
        val intent = Intent(this@VodActivity, DetailsActivity::class.java)
        intent.putExtra("stream_id", filme.id)
        intent.putExtra("stream_ext", filme.extension ?: "mp4")
        intent.putExtra("title", filme.name)
        intent.putExtra("icon", filme.icon)
        intent.putExtra("rating", filme.rating ?: "0.0")
        startActivity(intent)
    }

    private fun getFavMovies(context: Context): MutableSet<Int> {
        val set = prefs.getStringSet("fav_movies", emptySet()) ?: emptySet()
        return set.mapNotNull { it.toIntOrNull() }.toMutableSet()
    }

    // ================= MENU DOWNLOAD =================

    private fun mostrarMenuDownload(filme: VodStream) {
        // ancora no root da activity; se quiser pode trocar por uma view do card
        val anchor = findViewById<View>(android.R.id.content)
        val popup = PopupMenu(this, anchor)
        menuInflater.inflate(R.menu.menu_download, popup.menu)

        val downloadId = filme.id
        val estaBaixando = prefs.getBoolean("downloading_$downloadId", false)

        popup.menu.findItem(R.id.action_download).isVisible = !estaBaixando
        popup.menu.findItem(R.id.action_pause).isVisible = estaBaixando
        popup.menu.findItem(R.id.action_cancel).isVisible = estaBaixando

        popup.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.action_download -> {
                    iniciarDownloadReal(filme)
                    true
                }
                R.id.action_pause -> {
                    pausarDownload(filme.id)
                    true
                }
                R.id.action_cancel -> {
                    cancelarDownload(filme.id)
                    true
                }
                R.id.action_meus_downloads -> {
                    abrirDownloadsPremium(filme)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun iniciarDownloadReal(filme: VodStream) {
        val dns = prefs.getString("dns", "") ?: ""
        val base = if (dns.endsWith("/")) dns else "$dns/"
        val url = "${base}movie/$username/$password/${filme.id}.${filme.extension ?: "mp4"}"

        prefs.edit()
            .putBoolean("downloading_${filme.id}", true)
            .apply()

        Toast.makeText(this, "Baixando: ${filme.name}", Toast.LENGTH_LONG).show()
        // aqui depois entra DownloadManager ou ExoPlayer offline usando 'url'
    }

    private fun pausarDownload(streamId: Int) {
        prefs.edit().putBoolean("downloading_$streamId", false).apply()
        Toast.makeText(this, "Download pausado", Toast.LENGTH_SHORT).show()
    }

    private fun cancelarDownload(streamId: Int) {
        prefs.edit().remove("downloading_$streamId").apply()
        Toast.makeText(this, "Download cancelado", Toast.LENGTH_SHORT).show()
    }

    private fun abrirDownloadsPremium(filme: VodStream) {
        Toast.makeText(
            this,
            "Meus downloads (premium) – ${filme.name}",
            Toast.LENGTH_LONG
        ).show()
        // depois você cria uma Activity/Fragment de downloads premium aqui
    }

    // ================= ADAPTERS =================

    class VodCategoryAdapter(
        private val list: List<LiveCategory>,
        private val onClick: (LiveCategory) -> Unit
    ) : RecyclerView.Adapter<VodCategoryAdapter.VH>() {

        private var selectedPos = 0

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tvName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.tvName.text = item.name

            if (selectedPos == position) {
                holder.tvName.setTextColor(
                    holder.itemView.context.getColor(R.color.red_primary)
                )
                holder.tvName.setBackgroundColor(0xFF252525.toInt())
            } else {
                holder.tvName.setTextColor(
                    holder.itemView.context.getColor(R.color.gray_text)
                )
                holder.tvName.setBackgroundColor(0x00000000)
            }

            holder.itemView.setOnClickListener {
                notifyItemChanged(selectedPos)
                selectedPos = holder.adapterPosition
                notifyItemChanged(selectedPos)
                onClick(item)
            }
        }

        override fun getItemCount() = list.size
    }

    class VodAdapter(
        private val list: List<VodStream>,
        private val onClick: (VodStream) -> Unit,
        private val onDownloadClick: (VodStream) -> Unit
    ) : RecyclerView.Adapter<VodAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tvName)
            val imgPoster: ImageView = v.findViewById(R.id.imgPoster)
            val imgDownload: ImageView = v.findViewById(R.id.imgDownload)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_vod, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.tvName.text = item.name

            Glide.with(holder.itemView)
                .load(item.icon)
                .placeholder(R.mipmap.ic_launcher)
                .into(holder.imgPoster)

            holder.itemView.setOnClickListener { onClick(item) }
            holder.imgDownload.setOnClickListener { onDownloadClick(item) }
        }

        override fun getItemCount() = list.size
    }
}
