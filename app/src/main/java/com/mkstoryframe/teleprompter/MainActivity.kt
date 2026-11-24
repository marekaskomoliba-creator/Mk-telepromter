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
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(Color.rgb(12, 14, 18))
        }
        val heading = TextView(this).apply {
            text = "MK TELEPROMPTER"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(heading, lp(-1, dp(40)))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val lang = Spinner(this).apply {
            setBackgroundColor(Color.rgb(40, 44, 52))
        }
        val langs = listOf("Lietuvių" to "lt-LT", "Русский" to "ru-RU", "Polski" to "pl-PL", "English" to "en-US")
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, langs.map { it.first }) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(Color.WHITE)
                    textSize = 15f
                    setPadding(dp(12), 0, dp(12), 0)
                }
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                return (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.rgb(30, 33, 40))
                    textSize = 16f
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                }
            }
        }
        lang.adapter = adapter
        lang.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                language = langs[position].second
            }
        }
        controls.addView(lang, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) })
        controls.addView(button("Įkelti tekstą") { importText() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(controls, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })

        script = EditText(this).apply {
            hint = "Įklijuokite arba įrašykite scenarijų čia…"
            setTextColor(Color.LTGRAY)
            setHintTextColor(Color.GRAY)
            textSize = 15f
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.rgb(30, 33, 40))
            minLines = 2
            maxLines = 4
            gravity = Gravity.TOP
        }
        root.addView(script, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })

        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        row.addView(button("Balsas Start/Stop") { toggleListening() }, LinearLayout.LayoutParams(0, dp(48), 1.3f))
        row.addView(button("Atgal") { moveTo(lastPosition - 1) }, LinearLayout.LayoutParams(0, dp(48), 0.7f))
        row.addView(button("Toliau") { moveTo(lastPosition + 1) }, LinearLayout.LayoutParams(0, dp(48), 0.7f))
        root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })

        val mirrorRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        mirrorRow.addView(check("Veidr. H") { mirrorX = it; applyMirror() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        mirrorRow.addView(check("Veidr. V") { mirrorY = it; applyMirror() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        mirrorRow.addView(button("A−") { fontSize = (fontSize - 2).coerceAtLeast(16f); display.textSize = fontSize }, LinearLayout.LayoutParams(0, dp(44), 0.6f))
        mirrorRow.addView(button("A+") { fontSize = (fontSize + 2).coerceAtMost(64f); display.textSize = fontSize }, LinearLayout.LayoutParams(0, dp(44), 0.6f))
        root.addView(mirrorRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })

        status = TextView(this).apply {
            text = "Paruošta. Pasirinkite kalbą ir pradėkite skaityti."
            setTextColor(Color.LTGRAY)
            textSize = 13f
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })

        scroll = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            isFillViewport = true
        }
        display = TextView(this).apply {
            text = "Scenarijaus tekstas bus rodomas čia"
            textSize = fontSize
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(20))
            setLineSpacing(12f, 1.0f)
        }
        scroll.addView(display, FrameLayout.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        script.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { display.text = s; lastPosition = 0 }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
                var isFull = false
        display.setOnClickListener {
            isFull = !isFull
            val vis = if (isFull) View.GONE else View.VISIBLE
            heading.visibility = vis
            controls.visibility = vis
            script.visibility = vis
            row.visibility = vis
            mirrorRow.visibility = vis
            status.visibility = vis
        }

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
        scroll.scaleX = if (mirrorX) -1f else 1f
        scroll.scaleY = if (mirrorY) -1f else 1f
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
            updateHighlight(charPosition)

            val layout = display.layout
            if (layout != null) {
                val line = layout.getLineForOffset(charPosition.coerceIn(0, display.text.length))
                val targetY = if (mirrorY) (display.height - layout.getLineBottom(line) - scroll.height / 3) else (layout.getLineTop(line) - scroll.height / 3)
scroll.smoothScrollTo(0, targetY.coerceAtLeast(0))


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
        updateHighlight(offset)

        display.post {
            val layout = display.layout ?: return@post
            val line = layout.getLineForOffset(offset.coerceIn(0, display.text.length))
val targetY = if (mirrorY) (display.height - layout.getLineBottom(line) - scroll.height / 3) else (layout.getLineTop(line) - scroll.height / 3)
scroll.smoothScrollTo(0, targetY.coerceAtLeast(0))


        }
    }

    override fun onDestroy() {
        listening = false
        recognizer?.destroy()
        super.onDestroy()
    }
        private fun updateHighlight(charOffset: Int) {
        val raw = script.text.toString()
        val safeOffset = charOffset.coerceIn(0, raw.length)
        val spannable = SpannableString(raw)
        if (safeOffset > 0) {
            spannable.setSpan(
                ForegroundColorSpan(Color.rgb(100, 105, 115)),
                0,
                safeOffset,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        display.text = spannable
    }
}
