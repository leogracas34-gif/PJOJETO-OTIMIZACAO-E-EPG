package com.vltv.play

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Callback
import retrofit2.Response

class SearchActivity : AppCompatActivity() {
    private lateinit var etQuery: EditText
    private lateinit var btnDoSearch: ImageButton
    private lateinit var rvResults: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: SearchResultAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        etQuery = findViewById(R.id.etQuery)
        btnDoSearch = findViewById(R.id.btnDoSearch)
        rvResults = findViewById(R.id.rvResults)
        progressBar = findViewById(R.id.progressBar)

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

    private fun executarBusca() {
        val q = etQuery.text.toString().trim()
        if (q.isEmpty()) return

        progressBar.visibility = android.view.View.VISIBLE
        adapter.updateData(emptyList())

        val prefs = getSharedPreferences("vltv_prefs", MODE_PRIVATE)
        val username = prefs.getString("username", "") ?: ""
        val password = prefs.getString("password", "") ?: ""

        // ✅ BUSCA REAL - 1 chamada (live + vod + series)!
        XtreamApi.service.search(username, password, q).enqueue(object : Callback<List<SearchResultItem>> {
            override fun onResponse(call: retrofit2.Call<List<SearchResultItem>>, response: Response<List<SearchResultItem>>) {
                progressBar.visibility = android.view.View.GONE
                if (response.isSuccessful && response.body() != null) {
                    adapter.updateData(response.body()!!)
                    if (response.body()!!.isEmpty()) {
                        Toast.makeText(this@SearchActivity, "Nada encontrado para '$q'", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    adapter.updateData(emptyList())
                }
            }
            override fun onFailure(call: retrofit2.Call<List<SearchResultItem>>, t: retrofit2.Callback<*>?) {
                progressBar.visibility = android.view.View.GONE
                adapter.updateData(emptyList())
                Toast.makeText(this@SearchActivity, "Erro na busca", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
