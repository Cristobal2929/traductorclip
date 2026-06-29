package com.fenix.traductorclip

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.fenix.traductorclip.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var clipboardManager: android.content.ClipboardManager
    private lateinit var textToSpeech: TextToSpeech
    private var isListening = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale("es", "ES")
            }
        }

        setupSpinner()
        setupButton()
        setupClipboardListener()
    }

    private fun setupSpinner() {
        ArrayAdapter.createFromResource(
            this,
            R.array.languages_array,
            R.layout.spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerLanguage.adapter = adapter
        }
    }

    private fun setupButton() {
        binding.buttonToggle.setOnClickListener {
            isListening = !isListening
            updateUI()
        }
    }

    private fun setupClipboardListener() {
        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
    }

    private val clipboardListener = android.content.ClipboardManager.OnPrimaryClipChangedListener {
        if (isListening) {
            val clipData = clipboardManager.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).text.toString().take(500)
                if (text != viewModel.lastTranslatedText) {
                    translateText(text)
                }
            }
        }
    }

    private fun translateText(text: String) {
        val sourceLang = when (binding.spinnerLanguage.selectedItemPosition) {
            0 -> "fr"
            1 -> "en"
            2 -> "it"
            3 -> "pt"
            4 -> "de"
            else -> "fr"
        }
        val url = "https://api.mymemory.translated.net/get?q=$text&langpair=$sourceLang|es"

        CoroutineScope(Dispatchers.IO).launch {
            val request = Request.Builder().url(url).build()
            val response = OkHttpClient().newCall(request).execute()
            if (response.isSuccessful) {
                val jsonResponse = JSONObject(response.body?.string())
                val translatedText = jsonResponse.getJSONObject("responseData").getString("translatedText")
                runOnUiThread {
                    viewModel.updateTexts(text, translatedText)
                    speakText(translatedText)
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, R.string.error_translation, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun speakText(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun updateUI() {
        binding.textViewStatus.text = if (isListening) getString(R.string.status_active) else getString(R.string.status_paused)
        binding.textViewStatus.setTextColor(if (isListening) getColor(R.color.green) else getColor(R.color.red))
    }

    override fun onResume() {
        super.onResume()
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
    }

    override fun onPause() {
        super.onPause()
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
    }

    override fun onDestroy() {
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }
}