package com.vltvplay.livetv // Ajuste o package se necessário

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
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.nio.charset.Charset

class LiveTvActivity : AppCompatActivity() {

    private lateinit var rvCategories: RecyclerView
    private lateinit var rvChannels: RecyclerView
    private lateinit var tvCategoryTitle: TextView
    private lateinit var progressBar: ProgressBar
    private var categoryAdapter: CategoryAdapter? = null
    private var channelAdapter: ChannelAdapter? = null
    private var username: String = ""
    private var password: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_tv) // Ajuste layout se necessário

        rvCategories = findViewById(R.id.rvCategories)
        rvChannels = findViewById(R.id.rvChannels)
        tvCategoryTitle = findViewById(R.id.tvCategoryTitle)
        progressBar = findViewById(R.id.progressBar)

        username = intent.getStringExtra("username") ?: ""
        password = intent.getStringExtra("password") ?: ""

        rvCategories.layoutManager = GridLayoutManager(this, 4)
        rvChannels.layoutManager = GridLayoutManager(this, 3)

        carregarCategorias()
    }

    private fun carregarCategorias() {
        progressBar.visibility = View.VISIBLE
        XtreamApi.service.getLiveCategories(
            user = username,
            pass = password
        ).enqueue(object : Callback<List<LiveCategory>> {
            override fun onResponse(call: Call<List<LiveCategory>>, response: Response<List<LiveCategory>>) {
                progressBar.visibility = View.GONE
                if (response.isSuccessful && response.body() != null) {
                    val categorias = response.body()!!
                    if (categorias.isNotEmpty()) {
                        categoryAdapter = CategoryAdapter(categorias) { categoria ->
                            carregarCanais(categoria)
                        }
                        rvCategories.adapter = categoryAdapter
                        // Carrega primeira categoria automaticamente
                        carregarCanais(categorias[0])
                    }
                } else {
                    Toast.makeText(this@LiveTvActivity, "Erro ao carregar categorias", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<List<LiveCategory>>, t: Throwable) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@LiveTvActivity, "Falha de conexão", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun carregarCanais(categoria: LiveCategory) {
        progressBar.visibility = View.VISIBLE
        XtreamApi.service.getLiveStreams(
            user = username,
            pass = password,
            categoryId = categoria.id.toString()
        ).enqueue(object : Callback<List<LiveStream>> {
            override fun onResponse(call: Call<List<LiveStream>>, response: Response<List<LiveStream>>) {
                progressBar.visibility = View.GONE
                if (response.isSuccessful && response.body() != null) {
                    aplicarCanais(categoria, response.body()!!)
                } else {
                    Toast.makeText(this@LiveTvActivity, "Erro ao carregar canais", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<List<LiveStream>>, t: Throwable) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@LiveTvActivity, "Falha de conexão", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun aplicarCanais(categoria: LiveCategory, canais: List<LiveStream>) {
        tvCategoryTitle.text = categoria.name

        channelAdapter = ChannelAdapter(canais, username, password) { canal ->
            val intent = Intent(this@LiveTvActivity, PlayerActivity::class.java)
            intent.putExtra("stream_id", canal.id)
            intent.putExtra("stream_ext", "ts")
            intent.putExtra("stream_type", "live")
            intent.putExtra("channel_name", canal.name)
            startActivity(intent)
        }
        rvChannels.adapter = channelAdapter
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
    // ADAPTER DOS CANAIS + EPG OTIMIZADO
    // --------------------
    class ChannelAdapter(
        private val list: List<LiveStream>,
        private val username: String,
        private val password: String,
        private val onClick: (LiveStream) -> Unit
    ) : RecyclerView.Adapter<ChannelAdapter.VH>() {

        private val epgCache = mutableMapOf<Int, List<EpgResponseItem>>()
        private val loadingChannels = mutableSetOf<Int>()

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
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .placeholder(R.drawable.bg_logo_placeholder)
                .error(R.drawable.bg_logo_placeholder)
                .centerCrop()
                .into(holder.imgLogo)

            carregarEpg(holder, item)
            holder.itemView.setOnClickListener { onClick(item) }
        }

        private fun decodeBase64(text: String?): String {
            return try {
                if (text.isNullOrEmpty()) "" else String(
                    Base64.decode(text, Base64.DEFAULT),
                    Charset.forName("UTF-8")
                )
            } catch (e: Exception) {
                text ?: ""
            }
        }

        private fun carregarEpg(holder: VH, canal: LiveStream) {
            val channelId = canal.id
            epgCache[channelId]?.let { epg ->
                mostrarEpg(holder, epg)
                return
            }

            if (loadingChannels.contains(channelId)) return
            loadingChannels.add(channelId)

            holder.tvNow.text = "Carregando..."
            holder.tvNext.text = "..."

            val epgId = canal.id.toString()

            XtreamApi.service.getShortEpg(
                user = username,
                pass = password,
                streamId = epgId,
                limit = 3 // Aumentado para 3 para garantir next
            ).enqueue(object : Callback<EpgResponseWrapper> {
                override fun onResponse(
                    call: Call<EpgResponseWrapper>,
                    response: Response<EpgResponseWrapper>
                ) {
                    loadingChannels.remove(channelId)
                    if (response.isSuccessful && response.body()?.epg_listings != null) {
                        val epg = response.body()!!.epg_listings!!
                        epgCache[channelId] = epg
                        mostrarEpg(holder, epg)
                    } else {
                        // Retry com EPG completo se short falhar
                        carregarEpgCompleto(holder, canal)
                    }
                }

                override fun onFailure(call: Call<EpgResponseWrapper>, t: Throwable) {
                    loadingChannels.remove(channelId)
                    carregarEpgCompleto(holder, canal)
                }
            })
        }

        private fun carregarEpgCompleto(holder: VH, canal: LiveStream) {
            val channelId = canal.id
            XtreamApi.service.getEpg(
                user = username,
                pass = password,
                streamId = channelId.toString()
            ).enqueue(object : Callback<EpgResponseWrapper> {
                override fun onResponse(call: Call<EpgResponseWrapper>, response: Response<EpgResponseWrapper>) {
                    if (response.isSuccessful && response.body()?.epg_listings != null) {
                        val epg = response.body()!!.epg_listings!!
                        epgCache[channelId] = epg
                        mostrarEpg(holder, epg)
                    } else {
                        mostrarEpgVazio(holder)
                    }
                }

                override fun onFailure(call: Call<EpgResponseWrapper>, t: Throwable) {
                    mostrarEpgVazio(holder)
                }
            })
        }

        private fun mostrarEpg(holder: VH, epg: List<EpgResponseItem>) {
            when {
                epg.isNotEmpty() -> {
                    val agora = epg[0]
                    holder.tvNow.text = decodeBase64(agora.title).take(25) + if (decodeBase64(agora.title).length > 25) "..." else ""

                    if (epg.size > 1) {
                        val proximo = epg[1]
                        holder.tvNext.text = decodeBase64(proximo.title).take(20) + if (decodeBase64(proximo.title).length > 20) "..." else ""
                    } else {
                        holder.tvNext.text = "Sem próxima"
                    }
                }
                else -> mostrarEpgVazio(holder)
            }
        }

        private fun mostrarEpgVazio(holder: VH) {
            holder.tvNow.text = "Sem EPG"
            holder.tvNext.text = ""
        }

        override fun getItemCount() = list.size
    }
}
