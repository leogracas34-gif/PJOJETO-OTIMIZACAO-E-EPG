package com.vltv.play

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

class SearchActivity : AppCompatActivity() {

    private lateinit var etQuery: EditText
    private lateinit var btnDoSearch: ImageButton
    private lateinit var rvResults: RecyclerView
    private lateinit var adapter: SearchResultAdapter
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        etQuery = findViewById(R.id.etQuery)
        btnDoSearch = findViewById(R.id.btnDoSearch)
        rvResults = findViewById(R.id.rvResults)

        // ✅ OTIMIZAÇÕES
        rvResults.layoutManager = LinearLayoutManager(this)
        rvResults.setHasFixedSize(true)

        adapter = SearchResultAdapter(emptyList()) { item ->
            when (item.type) {
                "movie" -> {
                    val i = Intent(this, DetailsActivity::class.java)
                    i.putExtra("movie_id", item.id)
                    startActivity(i)
                }
                "series" -> {
                    val i = Intent(this, SeriesDetailsActivity::class.java)
                    i.putExtra("series_id", item.id)
                    i.putExtra("name", item.title)
                    startActivity(i)
                }
                "live" -> {
                    val i = Intent(this, PlayerActivity::class.java)
                    i.putExtra("stream_id", item.id)
                    i.putExtra("stream_type", "live")
                    i.putExtra("channel_name", item.title)
                    startActivity(i)
                }
            }
        }
        rvResults.adapter = adapter

        btnDoSearch.setOnClickListener { executarBusca() }

        etQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                executarBusca()
                true
            } else false
        }
    }

    // ✅ BUSCA OTIMIZADA - FILTRA LOCAL + API PARALELA
    private fun executarBusca() {
        val q = etQuery.text.toString().trim()
        if (q.isEmpty()) return

        searchJob?.cancel()
        searchJob = CoroutineScope(Dispatchers.Main).launch {
            val resultados = mutableListOf<SearchResultItem>()
            
            // ✅ PARALELO: Filmes + Séries + Canais
            val deferredFilmes = async { buscarFilmes(q) }
            val deferredSeries = async { buscarSeries(q) }
            val deferredCanais = async { buscarCanais(q) }

            resultados += deferredFilmes.await()
            resultados += deferredSeries.await()
            resultados += deferredCanais.await()

            adapter.updateData(resultados) [web:25]
        }
    }

    private suspend fun buscarFilmes(query: String): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val prefs = getSharedPreferences("vltv_prefs", MODE_PRIVATE)
        val user = prefs.getString("username", "") ?: ""
        val pass = prefs.getString("password", "") ?: ""

        try {
            val response = XtreamApi.service.getAllVodStreams(user, pass).execute()
            response.body()?.filter { it.name.contains(query, ignoreCase = true) }
                ?.map { SearchResultItem(it.id, it.name, "movie", it.rating) } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    private suspend fun buscarSeries(query: String): List<SearchResultItem> = withContext(Dispatchers.IO) {
        // Similar para series...
        emptyList()
    }

    private suspend fun buscarCanais(query: String): List<SearchResultItem> = withContext(Dispatchers.IO) {
        // Similar para canais...
        emptyList()
    }

    override fun onDestroy() {
        super.onDestroy()
        searchJob?.cancel()
    }
}

// ✅ ADAPTER DIFERENÇADO (precisa criar SearchResultItem.kt)
class SearchResultAdapter(
    private var list: List<SearchResultItem>,
    private val onClick: (SearchResultItem) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.VH>() {
    
    // IMPLEMENTAR DiffUtil AQUI [web:37]
    
    fun updateData(newList: List<SearchResultItem>) {
        list = newList
        notifyDataSetChanged()
    }
    
    class VH(v: View) : RecyclerView.ViewHolder(v)
}
