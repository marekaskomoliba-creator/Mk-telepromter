package com.mkstoryframe.teleprompter

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.Normalizer
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var script: EditText
    private lateinit var display: TextView
    private lateinit var status: TextView
    private lateinit var scroll: ScrollView
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var mirrorX = false
    private var mirrorY = false
    private var fontSize = 30f
    private var language = "lt-LT"
    private var lastPosition = 0
    private val requestAudio = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUi()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Speech recognition service is not available on this device."
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 16)
            setBackgroundColor(Color.rgb(12, 14, 18))
        }
        val heading = TextView(this).apply {
            text = "MK TELEPROMPTER"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(heading, lp(-1, 48))

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val lang = Spinner(this)
        val langs = listOf("Lietuvių" to "lt-LT", "Русский" to "ru-RU", "Polski" to "pl-PL", "English" to "en-US")
        lang.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, langs.map { it.first })
        lang.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                language = langs[position].second
            }
        }
        controls.addView(lang, LinearLayout.LayoutParams(0, 52, 1f))
        controls.addView(button("Įkelti tekstą") { importText() }, LinearLayout.LayoutParams(0, 52, 1f))
        root.addView(controls)

        script = EditText(this).apply {
            hint = "Įklijuokite arba įrašykite scenarijų čia…"
            setTextColor(Color.LTGRAY)
            setHintTextColor(Color.GRAY)
            textSize = 16f
            setPadding(12, 8, 12, 8)
            setBackgroundColor(Color.rgb(30, 33, 40))
            minLines = 3
            gravity = Gravity.TOP
        }
        root.addView(script, LinearLayout.LayoutParams(-1, 0, 0.9f))

        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        row.addView(button("Pradėti / sustabdyti balsą") { toggleListening() }, LinearLayout.LayoutParams(0, 54, 1f))
        row.addView(button("Atgal") { moveTo(lastPosition - 1) }, LinearLayout.LayoutParams(0, 54, 0.45f))
        row.addView(button("Toliau") { moveTo(lastPosition + 1) }, LinearLayout.LayoutParams(0, 54, 0.45f))
        root.addView(row)

        val mirrorRow = LinearLayout(this).apply { gravity = Gravity.CENTER }
        mirrorRow.addView(check("Veidrodis H") { mirrorX = it; applyMirror() }, LinearLayout.LayoutParams(0, 48, 1f))
        mirrorRow.addView(check("Veidrodis V") { mirrorY = it; applyMirror() }, LinearLayout.LayoutParams(0, 48, 1f))
        mirrorRow.addView(button("A−") { fontSize = (fontSize - 2).coerceAtLeast(16f); display.textSize = fontSize }, LinearLayout.LayoutParams(0, 48, 0.5f))
        mirrorRow.addView(button("A+") { fontSize = (fontSize + 2).coerceAtMost(64f); display.textSize = fontSize }, LinearLayout.LayoutParams(0, 48, 0.5f))
        root.addView(mirrorRow)

        status = TextView(this).apply { text = "Paruošta. Pasirinkite kalbą ir pradėkite skaityti."; setTextColor(Color.LTGRAY); textSize = 13f }
        root.addView(status, lp(-1, 34))

        scroll = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            isFillViewport = true
        }
        display = TextView(this).apply {
            text = "Scenarijaus tekstas bus rodomas čia"
            textSize = fontSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(28, 30, 28, 30)
            setLineSpacing(12f, 1.0f)
        }
        scroll.addView(display, ScrollView.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 2.1f))
        script.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { display.text = s; lastPosition = 0 }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        setContentView(root)
    }

    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 11f
        isAllCaps = false
        setOnClickListener { action() }
    }
    private fun check(label: String, changed: (Boolean) -> Unit) = CheckBox(this).apply {
        text = label
        setTextColor(Color.WHITE)
        buttonTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        setOnCheckedChangeListener { _, checked -> changed(checked) }
    }
    private fun applyMirror() {
        display.scaleX = if (mirrorX) -1f else 1f
        display.scaleY = if (mirrorY) -1f else 1f
    }

    private fun importText() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
        }
        startActivityForResult(i, 7)
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { script.setText(it.readText()) }
            }
        }
    }

    private fun toggleListening() {
        if (listening) {
            stopRecognition()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), requestAudio)
            return
        }
        startRecognition()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestAudio && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startRecognition()
    }

    private fun startRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Telefone neturi kalbos atpažinimo paslaugos."
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status.text = "Klausau…"; listening = true }
            override fun onBeginningOfSpeech() { status.text = "Kalbate…" }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status.text = "Apdoroju kalbą…" }
            override fun onError(error: Int) {
                if (listening) {
                    status.text = "Atpažinimo klaida ($error). Bandau tęsti…"
                    restartRecognition()
                }
            }
            override fun onResults(results: Bundle?) { process(results); restartRecognition() }
            override fun onPartialResults(partialResults: Bundle?) { process(partialResults) }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        listening = true
        launchRecognizer()
    }

    private fun launchRecognizer() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        try { recognizer?.startListening(intent) } catch (_: Exception) { status.text = "Nepavyko paleisti atpažinimo." }
    }

    private fun restartRecognition() {
        if (listening) display.postDelayed({ if (listening) launchRecognizer() }, 250)
    }

    private fun stopRecognition() {
        listening = false
        recognizer?.cancel()
        status.text = "Sustabdyta."
    }

    private fun normalize(s: String): List<String> =
        Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }

    private fun process(bundle: Bundle?) {
        val spoken = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
        val words = normalize(script.text.toString())
        val said = normalize(spoken)
        if (words.isEmpty() || said.isEmpty()) return
        val start = (lastPosition - 8).coerceAtLeast(0)
        val end = (lastPosition + 180).coerceAtMost(words.size)
        var bestIndex = -1
        var bestScore = 0
        for (i in start until end) {
            var score = 0
            for (j in said.indices) {
                if (i + j < words.size && said[j] == words[i + j]) score++
            }
            if (score > bestScore) { bestScore = score; bestIndex = i }
        }
        if (bestIndex >= 0 && bestScore >= minOf(2, said.size)) {
            lastPosition = (bestIndex + bestScore).coerceAtMost(words.size - 1)
            val charPosition = charOffsetForWord(script.text.toString(), lastPosition)
            val layout = display.layout
            if (layout != null) {
                val line = layout.getLineForOffset(charPosition.coerceIn(0, display.text.length))
                scroll.smoothScrollTo(0, layout.getLineTop(line).coerceAtLeast(0))
            }
            status.text = "Skaitoma: ${lastPosition + 1}/${words.size} žodžių"
        }
    }

    private fun charOffsetForWord(text: String, wordIndex: Int): Int {
        val regex = Regex("[\\p{L}\\p{N}]+")
        return regex.findAll(text).drop(wordIndex).firstOrNull()?.range?.first ?: text.length
    }

    private fun moveTo(delta: Int) {
        val words = normalize(script.text.toString())
        if (words.isEmpty()) return
        lastPosition = delta.coerceIn(0, words.lastIndex)
        val offset = charOffsetForWord(script.text.toString(), lastPosition)
        display.post {
            val layout = display.layout ?: return@post
            scroll.smoothScrollTo(0, layout.getLineTop(layout.getLineForOffset(offset.coerceIn(0, display.text.length))))
        }
    }

    override fun onDestroy() {
        listening = false
        recognizer?.destroy()
        super.onDestroy()
    }
}
