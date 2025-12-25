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

class LiveTvActivity : AppCompatActivity() {

    private lateinit var rvCategories: RecyclerView
    private lateinit var rvChannels: RecyclerView
    private lateinit var progressBar: View
    private lateinit var tvCategoryTitle: TextView

    private var username = ""
    private var password = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_tv)

        rvCategories = findViewById(R.id.rvCategories)
        rvChannels = findViewById(R.id.rvChannels)
        progressBar = findViewById(R.id.progressBar)
        tvCategoryTitle = findViewById(R.id.tvCategoryTitle)

        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        username = prefs.getString("username", "") ?: ""
        password = prefs.getString("password", "") ?: ""

        rvCategories.layoutManager = LinearLayoutManager(this)
        rvChannels.layoutManager = GridLayoutManager(this, 5)

        carregarCategorias()
    }

    private fun carregarCategorias() {
        progressBar.visibility = View.VISIBLE

        XtreamApi.service.getLiveCategories(username, password)
            .enqueue(object : Callback<List<LiveCategory>> {
                override fun onResponse(
                    call: Call<List<LiveCategory>>,
                    response: Response<List<LiveCategory>>
                ) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        val categorias = response.body()!!
                        rvCategories.adapter = CategoryAdapter(categorias) { categoria ->
                            carregarCanais(categoria)
                        }
                        if (categorias.isNotEmpty()) {
                            carregarCanais(categorias[0])
                        }
                    } else {
                        Toast.makeText(
                            this@LiveTvActivity,
                            "Erro ao carregar categorias",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<List<LiveCategory>>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@LiveTvActivity,
                        "Falha de conexão",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun carregarCanais(categoria: LiveCategory) {
        tvCategoryTitle.text = categoria.name
        progressBar.visibility = View.VISIBLE

        XtreamApi.service.getLiveStreams(username, password, categoryId = categoria.id)
            .enqueue(object : Callback<List<LiveStream>> {
                override fun onResponse(
                    call: Call<List<LiveStream>>,
                    response: Response<List<LiveStream>>
                ) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        val canais = response.body()!!
                        rvChannels.adapter = ChannelAdapter(canais) { canal ->
                            val intent = Intent(this@LiveTvActivity, PlayerActivity::class.java)
                            intent.putExtra("stream_id", canal.id)
                            intent.putExtra("stream_ext", "ts")      // canais geralmente .ts
                            intent.putExtra("stream_type", "live")   // informa tipo ao player
                            intent.putExtra("channel_name", canal.name) // NOVO: nome do canal
                            startActivity(intent)
                        }
                    }
                }

                override fun onFailure(call: Call<List<LiveStream>>, t: Throwable) {
                    progressBar.visibility = View.GONE
                }
            })
    }

    // ADAPTERS
    class CategoryAdapter(
        private val list: List<LiveCategory>,
        private val onClick: (LiveCategory) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.VH>() {

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
                holder.tvName.setTextColor(holder.itemView.context.getColor(R.color.red_primary))
                holder.tvName.setBackgroundColor(0xFF252525.toInt())
            } else {
                holder.tvName.setTextColor(holder.itemView.context.getColor(R.color.gray_text))
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

    class ChannelAdapter(
        private val list: List<LiveStream>,
        private val onClick: (LiveStream) -> Unit
    ) : RecyclerView.Adapter<ChannelAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tvName)
            val imgLogo: ImageView = v.findViewById(R.id.imgLogo)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_channel, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.tvName.text = item.name

            Glide.with(holder.itemView)
                .load(item.icon)
                .placeholder(R.mipmap.ic_launcher)
                .into(holder.imgLogo)

            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = list.size
    }
}
