package com.vltv.play

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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

class SeriesActivity : AppCompatActivity() {

    private lateinit var rvCategories: RecyclerView
    private lateinit var rvSeries: RecyclerView
    private lateinit var progressBar: View
    private lateinit var tvCategoryTitle: TextView

    private var username = ""
    private var password = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_tv)

        rvCategories = findViewById(R.id.rvCategories)
        rvSeries = findViewById(R.id.rvChannels)
        progressBar = findViewById(R.id.progressBar)
        tvCategoryTitle = findViewById(R.id.tvCategoryTitle)

        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        username = prefs.getString("username", "") ?: ""
        password = prefs.getString("password", "") ?: ""

        rvCategories.layoutManager = LinearLayoutManager(this)
        rvSeries.layoutManager = GridLayoutManager(this, 5)

        carregarCategorias()
    }

    private fun carregarCategorias() {
        progressBar.visibility = View.VISIBLE

        XtreamApi.service.getSeriesCategories(username, password)
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
                                category_id = "FAV_SERIES",
                                category_name = "FAVORITOS"
                            )
                        )
                        categorias.addAll(originais)

                        rvCategories.adapter = SeriesCategoryAdapter(categorias) { categoria ->
                            if (categoria.id == "FAV_SERIES") {
                                carregarSeriesFavoritas()
                            } else {
                                carregarSeries(categoria)
                            }
                        }

                        if (categorias.size > 1) {
                            carregarSeries(categorias[1])
                        } else {
                            tvCategoryTitle.text = "FAVORITOS"
                            carregarSeriesFavoritas()
                        }
                    } else {
                        Toast.makeText(
                            this@SeriesActivity,
                            "Erro ao carregar categorias",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<List<LiveCategory>>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@SeriesActivity,
                        "Falha de conexão",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun carregarSeries(categoria: LiveCategory) {
        tvCategoryTitle.text = categoria.name
        progressBar.visibility = View.VISIBLE

        XtreamApi.service.getSeries(username, password, categoryId = categoria.id)
            .enqueue(object : Callback<List<SeriesStream>> {
                override fun onResponse(
                    call: Call<List<SeriesStream>>,
                    response: Response<List<SeriesStream>>
                ) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        rvSeries.adapter = SeriesAdapter(response.body()!!) { serie ->
                            abrirDetalhesSerie(serie)
                        }
                    }
                }

                override fun onFailure(call: Call<List<SeriesStream>>, t: Throwable) {
                    progressBar.visibility = View.GONE
                }
            })
    }

    private fun carregarSeriesFavoritas() {
        tvCategoryTitle.text = "FAVORITOS"
        progressBar.visibility = View.VISIBLE

        val favIds = getFavSeries(this)
        if (favIds.isEmpty()) {
            progressBar.visibility = View.GONE
            rvSeries.adapter = SeriesAdapter(emptyList()) {}
            Toast.makeText(this, "Nenhuma série favorita.", Toast.LENGTH_SHORT).show()
            return
        }

        XtreamApi.service.getSeries(username, password, categoryId = "0")
            .enqueue(object : Callback<List<SeriesStream>> {
                override fun onResponse(
                    call: Call<List<SeriesStream>>,
                    response: Response<List<SeriesStream>>
                ) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        val todas = response.body()!!
                        val apenasFav = todas.filter { favIds.contains(it.id) }
                        rvSeries.adapter = SeriesAdapter(apenasFav) { serie ->
                            abrirDetalhesSerie(serie)
                        }
                    }
                }

                override fun onFailure(call: Call<List<SeriesStream>>, t: Throwable) {
                    progressBar.visibility = View.GONE
                }
            })
    }

    private fun abrirDetalhesSerie(serie: SeriesStream) {
        val intent = Intent(this@SeriesActivity, SeriesDetailsActivity::class.java)
        intent.putExtra("series_id", serie.id)
        intent.putExtra("name", serie.name)
        intent.putExtra("icon", serie.icon)
        intent.putExtra("rating", serie.rating ?: "0.0")
        startActivity(intent)
    }

    private fun getFavSeries(context: Context): MutableSet<Int> {
        val prefs = context.getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val set = prefs.getStringSet("fav_series", emptySet()) ?: emptySet()
        return set.mapNotNull { it.toIntOrNull() }.toMutableSet()
    }

    class SeriesCategoryAdapter(
        private val list: List<LiveCategory>,
        private val onClick: (LiveCategory) -> Unit
    ) : RecyclerView.Adapter<SeriesCategoryAdapter.VH>() {

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

    class SeriesAdapter(
        private val list: List<SeriesStream>,
        private val onClick: (SeriesStream) -> Unit
    ) : RecyclerView.Adapter<SeriesAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tvName)
            val imgPoster: ImageView = v.findViewById(R.id.imgPoster)
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
        }

        override fun getItemCount() = list.size
    }
}
