package com.vltv.play

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
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
    private lateinit var progressBar: ProgressBar
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
                        rvChannels.adapter = ChannelAdapter(canais, username, password) { canal ->
                            val intent = Intent(this@LiveTvActivity, PlayerActivity::class.java)
                            intent.putExtra("stream_id", canal.id)
                            intent.putExtra("stream_ext", "ts")
                            intent.putExtra("stream_type", "live")
                            intent.putExtra("channel_name", canal.name)
                            startActivity(intent)
                        }
                    } else {
                        Toast.makeText(
                            this@LiveTvActivity,
                            "Erro ao carregar canais",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<List<LiveStream>>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@LiveTvActivity,
                        "Falha de conexão",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    // --------------------
    // ADAPTER DAS CATEGORIAS
    // --------------------
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

    // --------------------
    // ADAPTER DOS CANAIS + EPG
    // --------------------
    class ChannelAdapter(
        private val list: List<LiveStream>,
        private val username: String,
        private val password: String,
        private val onClick: (LiveStream) -> Unit
    ) : RecyclerView.Adapter<ChannelAdapter.VH>() {

        private val epgCache = mutableMapOf<Int, List<EpgResponseItem>>()

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tvName)
            val tvNow: TextView = v.findViewById(R.id.tvNow)
            val tvNext: TextView = v.findViewById(R.id.tvNext)
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

            Glide.with(holder.itemView.context)
                .load(item.icon)
                .placeholder(R.mipmap.ic_launcher)
                .into(holder.imgLogo)

            carregarEpg(holder, item)
            holder.itemView.setOnClickListener { onClick(item) }
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

        private fun carregarEpg(holder: VH, canal: LiveStream) {
            epgCache[canal.id]?.let { epg ->
                mostrarEpg(holder, epg)
                return
            }

            // ✅ stream_id agora é String
            val epgId = canal.id.toString()

            XtreamApi.service.getShortEpg(
                user = username,
                pass = password,
                streamId = epgId,
                limit = 2
            ).enqueue(object : Callback<EpgWrapper> {
                override fun onResponse(
                    call: Call<EpgWrapper>,
                    response: Response<EpgWrapper>
                ) {
                    if (response.isSuccessful && response.body()?.epg_listings != null) {
                        val epg = response.body()!!.epg_listings!!
                        epgCache[canal.id] = epg
                        mostrarEpg(holder, epg)
                    } else {
                        holder.tvNow.text = "Programação não disponível"
                        holder.tvNext.text = ""
                    }
                }

                override fun onFailure(call: Call<EpgWrapper>, t: Throwable) {
                    holder.tvNow.text = "Programação não disponível"
                    holder.tvNext.text = ""
                }
            })
        }

        private fun mostrarEpg(holder: VH, epg: List<EpgResponseItem>) {
            if (epg.isNotEmpty()) {
                val agora = epg[0]
                holder.tvNow.text = decodeBase64(agora.title)

                if (epg.size > 1) {
                    val proximo = epg[1]
                    holder.tvNext.text = decodeBase64(proximo.title)
                } else {
                    holder.tvNext.text = ""
                }
            } else {
                holder.tvNow.text = "Programação não disponível"
                holder.tvNext.text = ""
            }
        }

        override fun getItemCount() = list.size
    }
}
