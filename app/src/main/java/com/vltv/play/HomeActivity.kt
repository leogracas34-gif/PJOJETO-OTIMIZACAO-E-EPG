package com.vltv.play

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.vltv.play.databinding.ActivityHomeBinding
import com.vltv.play.DownloadHelper  // ✅ ADICIONADO IMPORT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import kotlin.random.Random

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val TMDB_API_KEY = "9b73f5dd15b8165b1b57419be2f29128"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Receiver de downloads ✅ JÁ ESTÁ CERTO
        DownloadHelper.registerReceiver(this)

        setupClicks()
    }

    // resto do código continua EXATAMENTE IGUAL...
    override fun onResume() {
        super.onResume()
        carregarBannerAleatorio()
    }

    private fun setupClicks() {
        // TV AO VIVO
        binding.cardLiveTv.setOnClickListener {
            startActivity(Intent(this, LiveTvActivity::class.java))
        }

        // FILMES (VOD)
        binding.cardMovies.setOnClickListener {
            startActivity(Intent(this, VodActivity::class.java))
        }

        // SÉRIES
        binding.cardSeries.setOnClickListener {
            startActivity(Intent(this, SeriesActivity::class.java))
        }

        // Campo de busca no cabeçalho
        binding.etSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val texto = v.text.toString().trim()
                if (texto.isNotEmpty()) {
                    val intent = Intent(this, VodActivity::class.java)
                    intent.putExtra("search_query", texto)
                    startActivity(intent)
                }
                true
            } else {
                false
            }
        }

        // Botão de configurações / menu
        binding.btnSettings.setOnClickListener {
            val itens = arrayOf("Meus downloads", "Configurações", "Sair")

            AlertDialog.Builder(this)
                .setTitle("Opções")
                .setItems(itens) { _, which ->
                    when (which) {
                        0 -> startActivity(Intent(this, DownloadsActivity::class.java))
                        1 -> startActivity(Intent(this, SettingsActivity::class.java))
                        2 -> mostrarDialogoSair()
                    }
                }
                .show()
        }

        // Foco inicial no banner
        binding.cardBanner.requestFocus()
    }

    private fun showKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun mostrarDialogoSair() {
        AlertDialog.Builder(this)
            .setTitle("Sair")
            .setMessage("Deseja realmente sair e desconectar?")
            .setPositiveButton("Sim") { _, _ ->
                val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
                prefs.edit().clear().apply()

                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Não", null)
            .show()
    }

    private fun carregarBannerAleatorio() {
        val urlString =
            "https://api.themoviedb.org/3/trending/all/day?api_key=$TMDB_API_KEY&language=pt-BR"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonTxt = URL(urlString).readText()
                val json = JSONObject(jsonTxt)
                val results = json.getJSONArray("results")

                if (results.length() > 0) {
                    val randomIndex = Random.nextInt(results.length())
                    val item = results.getJSONObject(randomIndex)

                    val titulo = if (item.has("title"))
                        item.getString("title")
                    else
                        item.getString("name")

                    val overview = if (item.has("overview"))
                        item.getString("overview")
                    else
                        ""

                    val backdropPath = item.getString("backdrop_path")

                    if (backdropPath != "null") {
                        val imageUrl = "https://image.tmdb.org/t/p/w1280$backdropPath"

                        withContext(Dispatchers.Main) {
                            binding.tvBannerTitle.text = titulo
                            binding.tvBannerOverview.text = overview

                            Glide.with(this@HomeActivity)
                                .load(imageUrl)
                                .transform(FitCenter())
                                .placeholder(android.R.color.black)
                                .into(binding.imgBanner)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
