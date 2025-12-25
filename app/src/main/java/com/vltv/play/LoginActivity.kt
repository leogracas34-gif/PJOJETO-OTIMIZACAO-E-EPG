package com.vltv.play

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vltv.play.databinding.ActivityLoginBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    // SEUS 3 SERVIDORES XTREAM
    private val SERVERS = listOf(
        "http://tvblack.shop",
        "http://redeinternadestiny.top",
        "http://fibercdn.sbs"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val savedUser = prefs.getString("username", null)
        val savedPass = prefs.getString("password", null)

        if (!savedUser.isNullOrBlank() && !savedPass.isNullOrBlank()) {
            startHomeActivity()
            return
        }

        binding.btnLogin.setOnClickListener {
            val user = binding.etUsername.text.toString().trim()
            val pass = binding.etPassword.text.toString().trim()

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
            } else {
                realizarLoginMultiServidor(user, pass)
            }
        }
    }

    private fun realizarLoginMultiServidor(user: String, pass: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            var success = false
            var lastError: String? = null

            for (server in SERVERS) {
                val base = if (server.endsWith("/")) server.dropLast(1) else server
                val urlString = "$base/player_api.php?username=$user&password=$pass"

                try {
                    val url = URL(urlString)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000

                    val responseCode = connection.responseCode
                    if (responseCode == 200) {
                        // Achou servidor válido
                        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
                        prefs.edit().apply {
                            putString("dns", base)
                            putString("username", user)
                            putString("password", pass)
                            apply()
                        }

                        // Atualiza baseUrl dinâmica da API
                        XtreamApi.setBaseUrl("$base/")

                        success = true
                        break
                    } else {
                        lastError = "Servidor $base retornou código $responseCode"
                    }
                } catch (e: Exception) {
                    lastError = "Servidor $server: ${e.message}"
                }
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    startHomeActivity()
                } else {
                    Toast.makeText(
                        applicationContext,
                        "Erro de Login em todos os servidores.\n$lastError",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                }
            }
        }
    }

    private fun startHomeActivity() {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val savedDns = prefs.getString("dns", null)
        if (!savedDns.isNullOrBlank()) {
            XtreamApi.setBaseUrl(if (savedDns.endsWith("/")) savedDns else "$savedDns/")
        }
        
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }
}
