package com.yourname.jarvisapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ai.client.generativeai.GenerativeModel
import com.yourname.jarvisapp.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var generativeModel: GenerativeModel
    private lateinit var cameraManager: CameraManager
    private lateinit var wifiManager: WifiManager

    private val PERMISSIONS_REQUEST_CODE = 101
    private var isFlashlightOn = false
    private val userName = "Bilal" // Memory Feature

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAndRequestPermissions()

        initializeComponents()

        binding.btnMic.setOnClickListener {
            if (textToSpeech.isSpeaking) {
                textToSpeech.stop() // Interrupt feature
            }
            startListening()
        }
    }

    private fun initializeComponents() {
        // TextToSpeech
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale("ur", "PK")
            }
        }

        // Generative AI Model
        generativeModel = GenerativeModel(
            modelName = "gemini-pro",
            apiKey = "AIzaSyDUae7to2iOgd7oHKFRIh5ENUNOssrr4cU" // <-- YAHAN APNI API KEY DAALEIN
        )

        // SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        setupSpeechRecognitionListener()

        // Device Managers
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Boliye...")
        }
        speechRecognizer.startListening(intent)
        binding.tvStatus.text = "Suniye..."
    }

    private fun setupSpeechRecognitionListener() {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val userInput = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) ?: ""
                if (userInput.isNotBlank()) {
                    binding.tvStatus.text = "Soch raha hoon..."
                    getResponseFromGemini(userInput)
                }
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { binding.tvStatus.text = "Tap to Speak" }
            override fun onError(error: Int) { binding.tvStatus.text = "Error, try again" }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun getResponseFromGemini(userInput: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prompt = """
                    You are a highly advanced AI assistant named Jarvis, integrated into an Android app. Your user's name is $userName.
                    Your task is to understand the user's Urdu command and respond in two parts:
                    1. A natural, friendly conversational response in Urdu.
                    2. A JSON object that the app's code can parse to perform an action.

                    The JSON object must have an "action" key and necessary "parameters".
                    Possible actions are: "speak", "toggle_flashlight", "toggle_wifi", "open_app", "open_playstore".

                    Examples:
                    - User: "flashlight on kardo" -> Response: "Theek hai Bilal bhai, flashlight on kar raha hoon. {"action": "toggle_flashlight", "parameters": {"state": "on"}}"
                    - User: "wifi band kar do" -> Response: "WiFi band kar diya hai. {"action": "toggle_wifi", "parameters": {"state": "off"}}"
                    - User: "tiktok download karni hai" -> Response: "Zaroor, main Play Store par TikTok search kar raha hoon. {"action": "open_playstore", "parameters": {"query": "TikTok"}}"
                    - User: "kya haal hai" -> Response: "Main theek hoon, shukriya. Aap sunayein. {"action": "speak", "parameters": {}}"

                    User's command: "$userInput"
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                val fullResponse = response.text ?: ""
                
                val jsonStartIndex = fullResponse.indexOf('{')
                if (jsonStartIndex != -1) {
                    val conversationalPart = fullResponse.substring(0, jsonStartIndex).trim()
                    val jsonPart = fullResponse.substring(jsonStartIndex)
                    
                    runOnUiThread {
                        speak(conversationalPart)
                        processJarvisAction(jsonPart)
                    }
                } else {
                    runOnUiThread { speak(fullResponse) }
                }

            } catch (e: Exception) {
                Log.e("GeminiError", "Error: ${e.message}")
                runOnUiThread { speak("Maazrat, internet mein kuch masla lag raha hai.") }
            }
        }
    }

    private fun processJarvisAction(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            when (json.getString("action")) {
                "toggle_flashlight" -> {
                    val state = json.getJSONObject("parameters").getString("state") == "on"
                    toggleFlashlight(state)
                }
                "toggle_wifi" -> {
                    val state = json.getJSONObject("parameters").getString("state") == "on"
                    toggleWifi(state)
                }
                "open_playstore" -> {
                    val query = json.getJSONObject("parameters").getString("query")
                    openPlayStore(query)
                }
                "speak" -> { /* Do nothing extra */ }
            }
        } catch (e: Exception) {
            Log.e("JSONError", "Error parsing action: ${e.message}")
        }
    }

    private fun speak(text: String) {
        binding.tvStatus.text = "Tap to Speak"
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun toggleFlashlight(enable: Boolean) {
        try {
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, enable)
            isFlashlightOn = enable
        } catch (e: Exception) {
            speak("Flashlight control nahi ho saki.")
        }
    }

    private fun toggleWifi(enable: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            wifiManager.isWifiEnabled = enable
        } else {
            speak("Bilal bhai, security reasons ki wajah se, main Android 10 ya us se naye versions par WiFi direct control nahi kar sakta. Aapko settings se karna hoga.")
            val intent = Intent(Settings.Panel.ACTION_WIFI)
            startActivity(intent)
        }
    }

    private fun openPlayStore(query: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://search?q=$query")
                setPackage("com.android.vending")
            }
            startActivity(intent)
        } catch (e: Exception) {
            speak("Play Store kholne mein masla aa raha hai.")
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSIONS_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "App ko kaam karne ke liye permissions zaroori hain.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.stop()
        textToSpeech.shutdown()
        speechRecognizer.destroy()
    }
}
