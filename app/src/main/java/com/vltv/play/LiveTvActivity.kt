package com.vltv.play

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.bumptech.glide.load.engine.DiskCacheStrategy
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
    private var currentCategories: List<LiveCategory> = emptyList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_tv)
        
        initViews()
        getCredentials()
        setupRecyclerViews()
        carregarCategorias()
    }
    
    private fun initViews() {
        rvCategories = findViewById(R.id.rvCategories)
        rvChannels = findViewById(R.id.rvChannels)
        tvCategoryTitle = findViewById(R.id.tvCategoryTitle)
        progressBar = findViewById(R.id.progressBar)
    }
    
    private fun getCredentials() {
        username = intent.getStringExtra("username") ?: ""
        password = intent.getStringExtra("password") ?: ""
    }
    
    private fun setupRecyclerViews() {
        rvCategories.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvChannels.layoutManager = GridLayoutManager(this, 3)
    }
    
    private fun carregarCategorias() {
        progressBar.visibility = View.VISIBLE
        XtreamApi.service.getLiveCategories(username, password)
            .enqueue(object : retrofit2.Callback<List<LiveCategory>> {
                override fun onResponse(call: retrofit2.Call<List<LiveCategory>>, response: retrofit2.Response<List<LiveCategory>>) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        currentCategories = response.body()!!
                        if (currentCategories.isNotEmpty()) {
                            categoryAdapter = CategoryAdapter(currentCategories) { categoria ->
                                carregarCanais(categoria)
                            }
                            rvCategories.adapter = categoryAdapter
                            carregarCanais(currentCategories[0])
                        }
                    } else {
                        Toast.makeText(this@LiveTvActivity, "Erro ao carregar categorias", Toast.LENGTH_SHORT).show()
                    }
                }
                
                override fun onFailure(call: retrofit2.Call<List<LiveCategory>>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@LiveTvActivity, "Falha de conexão", Toast.LENGTH_SHORT).show()
                }
            })
    }
    
    private fun carregarCanais(categoria: LiveCategory) {
        progressBar.visibility = View.VISIBLE
        tvCategoryTitle.text = categoria.name
        
        XtreamApi.service.getLiveStreams(username, password, categoria.id)
            .enqueue(object : retrofit2.Callback<List<LiveStream>> {
                override fun onResponse(call: retrofit2.Call<List<LiveStream>>, response: retrofit2.Response<List<LiveStream>>) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        val canais = response.body()!!
                        channelAdapter = ChannelAdapter(canais, username, password) { canal ->
                            val intent = Intent(this@LiveTvActivity, PlayerActivity::class.java).apply {
                                putExtra("stream_id", canal.id)
                                putExtra("stream_ext", "ts")
                                putExtra("stream_type", "live")
                                putExtra("channel_name", canal.name)
                            }
                            startActivity(intent)
                        }
                        rvChannels.adapter = channelAdapter
                    } else {
                        Toast.makeText(this@LiveTvActivity, "Erro ao carregar canais", Toast.LENGTH_SHORT).show()
                    }
                }
                
                override fun onFailure(call: retrofit2.Call<List<LiveStream>>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@LiveTvActivity, "Falha de conexão", Toast.LENGTH_SHORT).show()
                }
            })
    }
    
    // CategoryAdapter
    class CategoryAdapter(
        private val list: List<LiveCategory>,
        private val onClick: (LiveCategory) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.VH>() {
        private var selectedPos = 0
        
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tvName)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
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
    
    // ChannelAdapter OTIMIZADO (usa sua API exata)
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
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
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
            
            carregarEpgLazy(holder, item, position)
            holder.itemView.setOnClickListener { onClick(item) }
        }
        
        private fun carregarEpgLazy(holder: VH, canal: LiveStream, position: Int) {
            val channelId = canal.id
            
            // Cache primeiro
            epgCache[channelId]?.let { epg ->
                mostrarEpg(holder, epg)
                return
            }
            
            // Lazy loading: só primeiros 12 + delay progressivo
            if (position >= 12) {
                holder.tvNow.text = "Toque canal"
                holder.tvNext.text = ""
                return
            }
            
            if (loadingChannels.contains(channelId)) return
            loadingChannels.add(channelId)
            
            holder.tvNow.text = "EPG..."
            holder.tvNext.text = ""
            
            // Delay para evitar timeout (0ms, 200ms, 400ms...)
            val delay = (position * 200L)
            Handler(Looper.getMainLooper()).postDelayed({
                carregarEpg(holder, canal)
            }, delay)
        }
        
        private fun carregarEpg(holder: VH, canal: LiveStream) {
            val epgChannelId = canal.epg_channel_id ?: canal.id.toString()
            
            XtreamApi.service.getShortEpg(username, password, epgChannelId, 3)
                .enqueue(object : retrofit2.Callback<EpgWrapper> {
                    override fun onResponse(call: retrofit2.Call<EpgWrapper>, response: retrofit2.Response<EpgWrapper>) {
                        loadingChannels.remove(canal.id)
                        if (response.isSuccessful && response.body()?.epg_listings != null) {
                            val epg = response.body()!!.epg_listings!!
                            epgCache[canal.id] = epg
                            mostrarEpg(holder, epg)
                        } else {
                            mostrarEpgVazio(holder)
                        }
                    }
                    
                    override fun onFailure(call: retrofit2.Call<EpgWrapper>, t: Throwable) {
                        loadingChannels.remove(canal.id)
                        mostrarEpgVazio(holder)
                    }
                })
        }
        
        private fun decodeBase64(text: String?): String {
            return try {
                if (text.isNullOrEmpty()) return ""
                String(Base64.decode(text, Base64.DEFAULT), Charset.forName("UTF-8"))
            } catch (e: Exception) {
                text ?: ""
            }
        }
        
        private fun mostrarEpg(holder: VH, epg: List<EpgResponseItem>) {
            if (epg.isNotEmpty()) {
                val agora = epg[0]
                val nowTitle = decodeBase64(agora.title).take(20)
                holder.tvNow.text = if (nowTitle.length < 20) nowTitle else "$nowTitle..."
                
                if (epg.size > 1) {
                    val proximo = epg[1]
                    val nextTitle = decodeBase64(proximo.title).take(16)
                    holder.tvNext.text = if (nextTitle.length < 16) nextTitle else "$nextTitle..."
                }
            } else {
                mostrarEpgVazio(holder)
            }
        }
        
        private fun mostrarEpgVazio(holder: VH) {
            holder.tvNow.text = "Sem EPG"
            holder.tvNext.text = ""
        }
        
        override fun getItemCount() = list.size
    }
}
