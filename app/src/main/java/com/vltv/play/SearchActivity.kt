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

    private fun executarBusca() {
        val q = etQuery.text.toString().trim()
        if (q.isEmpty()) return

        val prefs = getSharedPreferences("vltv_prefs", MODE_PRIVATE)
        val username = prefs.getString("username", "") ?: ""
        val password = prefs.getString("password", "") ?: ""

        val resultados = mutableListOf<SearchResultItem>()

        // 1) Todos os filmes (sem category_id)
        XtreamApi.service.getAllVodStreams(username, password)
            .enqueue(object : retrofit2.Callback<List<VodStream>> {
                override fun onResponse(
                    call: retrofit2.Call<List<VodStream>>,
                    response: retrofit2.Response<List<VodStream>>
                ) {
                    if (response.isSuccessful && response.body() != null) {
                        val filmes = response.body()!!
                        resultados += filmes
                            .filter { it.name.contains(q, ignoreCase = true) }
                            .map { vod ->
                                SearchResultItem(
                                    id = vod.id,
                                    title = vod.name,
                                    type = "movie",
                                    extraInfo = vod.rating
                                )
                            }
                    }

                    // 2) Todas as séries (sem category_id)
                    XtreamApi.service.getAllSeries(username, password)
                        .enqueue(object : retrofit2.Callback<List<SeriesStream>> {
                            override fun onResponse(
                                call: retrofit2.Call<List<SeriesStream>>,
                                response: retrofit2.Response<List<SeriesStream>>
                            ) {
                                if (response.isSuccessful && response.body() != null) {
                                    val series = response.body()!!
                                    resultados += series
                                        .filter { it.name.contains(q, ignoreCase = true) }
                                        .map { s ->
                                            SearchResultItem(
                                                id = s.id,
                                                title = s.name,
                                                type = "series",
                                                extraInfo = s.rating
                                            )
                                        }
                                }
                                adapter.updateData(resultados)
                            }

                            override fun onFailure(
                                call: retrofit2.Call<List<SeriesStream>>,
                                t: Throwable
                            ) {
                                adapter.updateData(resultados)
                            }
                        })
                }

                override fun onFailure(
                    call: retrofit2.Call<List<VodStream>>,
                    t: Throwable
                ) {
                    // se filmes falharem, tenta só séries
                    XtreamApi.service.getAllSeries(username, password)
                        .enqueue(object : retrofit2.Callback<List<SeriesStream>> {
                            override fun onResponse(
                                call: retrofit2.Call<List<SeriesStream>>,
                                response: retrofit2.Response<List<SeriesStream>>
                            ) {
                                if (response.isSuccessful && response.body() != null) {
                                    val series = response.body()!!
                                    resultados += series
                                        .filter { it.name.contains(q, ignoreCase = true) }
                                        .map { s ->
                                            SearchResultItem(
                                                id = s.id,
                                                title = s.name,
                                                type = "series",
                                                extraInfo = s.rating
                                            )
                                        }
                                }
                                adapter.updateData(resultados)
                            }

                            override fun onFailure(
                                call: retrofit2.Call<List<SeriesStream>>,
                                t: Throwable
                            ) {
                                adapter.updateData(emptyList())
                            }
                        })
                }
            })
    }
}
