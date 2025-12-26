package com.vltv.play

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SearchActivity : AppCompatActivity() {

    private lateinit var etQuery: EditText
    private lateinit var btnDoSearch: ImageButton
    private lateinit var rvResults: RecyclerView
    private lateinit var adapter: SearchResultAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        etQuery = findViewById(R.id.etQuery)
        btnDoSearch = findViewById(R.id.btnDoSearch)
        rvResults = findViewById(R.id.rvResults)

        adapter = SearchResultAdapter(emptyList()) { item ->
            when (item.type) {
                "movie" -> {
                    val i = Intent(this, DetailsActivity::class.java)
                    i.putExtra("stream_id", item.id)
                    i.putExtra("stream_ext", "mp4")
                    i.putExtra("title", item.title)
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

        rvResults.layoutManager = LinearLayoutManager(this)
        rvResults.adapter = adapter

        btnDoSearch.setOnClickListener { executarBusca() }

        etQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                executarBusca()
                true
            } else false
        }
    }

    // Normaliza texto para melhorar a busca (remove espaços extras, ignora maiúsculas)
    private fun normalizar(text: String?): String {
        return text
            ?.trim()
            ?.lowercase()
            ?: ""
    }

    private fun executarBusca() {
        val raw = etQuery.text.toString()
        val qNorm = normalizar(raw)
        if (qNorm.isEmpty()) return

        val prefs = getSharedPreferences("vltv_prefs", MODE_PRIVATE)
        val username = prefs.getString("username", "") ?: ""
        val password = prefs.getString("password", "") ?: ""

        val resultados = mutableListOf<SearchResultItem>()

        // 1) FILMES (todos)
        XtreamApi.service.getAllVodStreams(username, password)
            .enqueue(object : retrofit2.Callback<List<VodStream>> {
                override fun onResponse(
                    call: retrofit2.Call<List<VodStream>>,
                    response: retrofit2.Response<List<VodStream>>
                ) {
                    if (response.isSuccessful && response.body() != null) {
                        val filmes = response.body()!!
                        resultados += filmes
                            .filter { normalizar(it.name).contains(qNorm) }
                            .map { vod ->
                                SearchResultItem(
                                    id = vod.id,
                                    title = vod.name,
                                    type = "movie",
                                    extraInfo = vod.rating
                                )
                            }
                    }

                    // 2) SÉRIES (todas)
                    XtreamApi.service.getAllSeries(username, password)
                        .enqueue(object : retrofit2.Callback<List<SeriesStream>> {
                            override fun onResponse(
                                call: retrofit2.Call<List<SeriesStream>>,
                                response: retrofit2.Response<List<SeriesStream>>
                            ) {
                                if (response.isSuccessful && response.body() != null) {
                                    val series = response.body()!!
                                    resultados += series
                                        .filter { normalizar(it.name).contains(qNorm) }
                                        .map { s ->
                                            SearchResultItem(
                                                id = s.id,
                                                title = s.name,
                                                type = "series",
                                                extraInfo = s.rating
                                            )
                                        }
                                }

                                // 3) Canais ao vivo (todos)
                                buscarCanais(username, password, qNorm, resultados)
                            }

                            override fun onFailure(
                                call: retrofit2.Call<List<SeriesStream>>,
                                t: Throwable
                            ) {
                                // mesmo se der erro nas séries, tenta canais
                                buscarCanais(username, password, qNorm, resultados)
                            }
                        })
                }

                override fun onFailure(
                    call: retrofit2.Call<List<VodStream>>,
                    t: Throwable
                ) {
                    // se filmes falharem, começa direto por séries
                    XtreamApi.service.getAllSeries(username, password)
                        .enqueue(object : retrofit2.Callback<List<SeriesStream>> {
                            override fun onResponse(
                                call: retrofit2.Call<List<SeriesStream>>,
                                response: retrofit2.Response<List<SeriesStream>>
                            ) {
                                if (response.isSuccessful && response.body() != null) {
                                    val series = response.body()!!
                                    resultados += series
                                        .filter { normalizar(it.name).contains(qNorm) }
                                        .map { s ->
                                            SearchResultItem(
                                                id = s.id,
                                                title = s.name,
                                                type = "series",
                                                extraInfo = s.rating
                                            )
                                        }
                                }
                                buscarCanais(username, password, qNorm, resultados)
                            }

                            override fun onFailure(
                                call: retrofit2.Call<List<SeriesStream>>,
                                t: Throwable
                            ) {
                                buscarCanais(username, password, qNorm, resultados)
                            }
                        })
                }
            })
    }

    // Busca live streams em uma chamada "global" e filtra
    private fun buscarCanais(
        username: String,
        password: String,
        qNorm: String,
        resultados: MutableList<SearchResultItem>
    ) {
        XtreamApi.service.getLiveStreams(username, password, categoryId = "0")
            .enqueue(object : retrofit2.Callback<List<LiveStream>> {
                override fun onResponse(
                    call: retrofit2.Call<List<LiveStream>>,
                    response: retrofit2.Response<List<LiveStream>>
                ) {
                    if (response.isSuccessful && response.body() != null) {
                        val canais = response.body()!!
                        resultados += canais
                            .filter { normalizar(it.name).contains(qNorm) }
                            .map { c ->
                                SearchResultItem(
                                    id = c.id,
                                    title = c.name,
                                    type = "live",
                                    extraInfo = null
                                )
                            }
                    }
                    adapter.updateData(resultados)
                }

                override fun onFailure(
                    call: retrofit2.Call<List<LiveStream>>,
                    t: Throwable
                ) {
                    adapter.updateData(resultados)
                }
            })
    }
}
