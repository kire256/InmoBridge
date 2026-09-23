package com.droidforge.inmobridge.phone

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.droidforge.inmobridge.core.BridgeMessage
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * Conversation mode: pick two languages, talk; the phone runs speech
 * recognition + on-device ML Kit translation and pushes chat bubbles to the
 * glasses (my speech right/teal, their translated speech left/white).
 */
class ConversationActivity : Activity() {

    private val d get() = resources.displayMetrics.density
    private fun dp(v: Float) = (d * v).toInt()

    private lateinit var status: TextView
    private var recognizer: SpeechRecognizer? = null
    private var translatorMe: com.google.mlkit.nl.translate.Translator? = null
    private var translatorThem: com.google.mlkit.nl.translate.Translator? = null

    private var myLang = TranslateLanguage.ENGLISH
    private var theirLang = TranslateLanguage.SPANISH

    private val langs = listOf(
        "English" to TranslateLanguage.ENGLISH, "Spanish" to TranslateLanguage.SPANISH,
        "French" to TranslateLanguage.FRENCH, "German" to TranslateLanguage.GERMAN,
        "Italian" to TranslateLanguage.ITALIAN, "Portuguese" to TranslateLanguage.PORTUGUESE,
        "Chinese" to TranslateLanguage.CHINESE, "Japanese" to TranslateLanguage.JAPANESE,
        "Korean" to TranslateLanguage.KOREAN, "Russian" to TranslateLanguage.RUSSIAN,
        "Arabic" to TranslateLanguage.ARABIC, "Hindi" to TranslateLanguage.HINDI,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(20f), dp(24f), dp(20f), dp(20f))
        }
        root.addView(title())

        val names = langs.map { it.first }
        val from = android.widget.Spinner(this).apply {
            adapter = ArrayAdapter(this@ConversationActivity, android.R.layout.simple_spinner_dropdown_item, names)
        }
        val to = android.widget.Spinner(this).apply {
            adapter = ArrayAdapter(this@ConversationActivity, android.R.layout.simple_spinner_dropdown_item, names)
            setSelection(1)
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(from, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(this).apply { text = "  <->  "; setTextColor(0xFF9AA0A6.toInt()) })
        row.addView(to, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)

        status = TextView(this).apply {
            text = "Pick languages, then Start. You speak; their replies are translated."
            setTextColor(0xFF9AA0A6.toInt())
            setPadding(0, dp(16f), 0, dp(8f))
        }
        root.addView(status)

        root.addView(Button(this).apply {
            text = "Start conversation on glasses"
            isAllCaps = false
            setOnClickListener {
                myLang = langs[from.selectedItemPosition].second
                theirLang = langs[to.selectedItemPosition].second
                startConversation()
            }
        })
        root.addView(Button(this).apply {
            text = "Stop"
            isAllCaps = false
            setOnClickListener { stopConversation() }
        })

        setContentView(root)
    }

    private fun title() = TextView(this).apply {
        text = "Conversation"
        textSize = 28f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(Color.WHITE)
        setPadding(0, 0, 0, dp(12f))
    }

    private fun pill(bg: Int): GradientDrawable =
        GradientDrawable().apply { setColor(bg); cornerRadius = dp(18f).toFloat() }

    private fun translator(from: String, to: String) =
        Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build()
        )

    private fun startConversation() {
        if (!BridgeService.running) {
            Toast.makeText(this, "Bridge not running", Toast.LENGTH_LONG).show()
            return
        }
        translatorMe = translator(myLang, theirLang)
        translatorThem = translator(theirLang, myLang)
        val ok = BridgeService.instance?.sendToGlasses(
            BridgeMessage.chat("start", "listening", System.nanoTime())
        ) == true
        if (!ok) {
            Toast.makeText(this, "Glasses not connected", Toast.LENGTH_LONG).show()
            return
        }
        status.text = "Live — speak now"
        startListening()
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "No speech recognition available on this phone"
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(listener)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                try {
                    java.util.Locale.forLanguageTag(
                        langs.first { it.second == myLang }.first.substring(0, 2)
                    ).let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it.language) }
                } catch (_: Exception) {}
            }
            startListening(intent)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (text.isNotBlank()) {
                translateAndPush(text, mine = true, live = true)
            }
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (text.isNotBlank()) {
                translateAndPush(text, mine = true, live = false) { startListening() }
            } else {
                startListening()
            }
        }

        override fun onError(error: Int) {
            // 6 = speech timeout, 7 = no match: just restart; others: report + retry
            status.text = when (error) {
                6 -> "Listening…"
                7 -> "Listening…"
                8 -> "Speech service busy — retrying"
                else -> "Recognizer error $error — retrying"
            }
            startListening()
        }

        override fun onReadyForSpeech(params: Bundle?) { status.text = "Listening… speak" }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /** Recognize -> translate -> push a bubble; optionally continue listening. */
    private fun translateAndPush(text: String, mine: Boolean, live: Boolean, done: (() -> Unit)? = null) {
        val t = if (mine) translatorMe else translatorThem
        if (t == null) { done?.invoke(); return }
        t.translate(text)
            .addOnSuccessListener { translated ->
                BridgeService.instance?.sendToGlasses(
                    BridgeMessage.chat(
                        if (mine) "me" else "them",
                        translated, System.nanoTime(),
                        orig = text, live = live,
                    )
                )
                done?.invoke()
            }
            .addOnFailureListener { done?.invoke() }
    }

    private fun stopConversation() {
        recognizer?.destroy(); recognizer = null
        translatorMe?.close(); translatorMe = null
        translatorThem?.close(); translatorThem = null
        BridgeService.instance?.sendToGlasses(BridgeMessage.chat("stop", "", System.nanoTime()))
        status.text = "Stopped. Pick languages, then Start."
    }

    override fun onDestroy() {
        recognizer?.destroy()
        translatorMe?.close()
        translatorThem?.close()
        super.onDestroy()
    }
}
