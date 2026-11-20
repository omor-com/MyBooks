package com.example.data.reader

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

data class TtsUiState(
    val isReady: Boolean = false,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val speechRate: Float = 1.0f,
    val currentChunkIndex: Int = 0,
    val totalChunks: Int = 0,
    val statusError: String? = null
)

class TtsReadAloudManager(private val context: Context) {
    private val _state = MutableStateFlow(TtsUiState())
    val state: StateFlow<TtsUiState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var initialized = false
    private var chunks: List<String> = emptyList()
    private var currentIndex: Int = 0
    private var onChapterFinishedCallback: (() -> Unit)? = null

    fun speakChunks(
        paragraphs: List<String>,
        startIndex: Int = 0,
        languageTag: String = "",
        onFinishedAll: (() -> Unit)? = null
    ) {
        val sanitized = paragraphs.map { it.trim() }.filter { it.isNotEmpty() }
        if (sanitized.isEmpty()) {
            _state.update { it.copy(statusError = "No readable text found for Read Aloud") }
            return
        }
        chunks = sanitized
        currentIndex = startIndex.coerceIn(0, sanitized.lastIndex)
        onChapterFinishedCallback = onFinishedAll

        ensureInitialized(languageTag) { ready ->
            if (ready) {
                playCurrentChunk()
            }
        }
    }

    fun pause() {
        tts?.stop()
        _state.update { it.copy(isPlaying = false, isPaused = chunks.isNotEmpty()) }
    }

    fun resume() {
        if (chunks.isEmpty()) return
        if (!initialized) {
            ensureInitialized("") { ready -> if (ready) playCurrentChunk() }
        } else {
            playCurrentChunk()
        }
    }

    fun stop() {
        tts?.stop()
        chunks = emptyList()
        currentIndex = 0
        onChapterFinishedCallback = null
        _state.update {
            it.copy(
                isPlaying = false,
                isPaused = false,
                currentChunkIndex = 0,
                totalChunks = 0,
                statusError = null
            )
        }
    }

    fun setSpeechRate(rate: Float) {
        val safeRate = rate.coerceIn(0.6f, 2.0f)
        tts?.setSpeechRate(safeRate)
        _state.update { it.copy(speechRate = safeRate) }
    }

    fun skipNext() {
        if (currentIndex + 1 < chunks.size) {
            currentIndex++
            playCurrentChunk()
        } else {
            onChapterFinishedCallback?.invoke()
        }
    }

    fun skipPrevious() {
        if (currentIndex > 0) {
            currentIndex--
            playCurrentChunk()
        }
    }

    private fun ensureInitialized(languageTag: String, onReady: (Boolean) -> Unit) {
        val existing = tts
        if (existing != null && initialized) {
            applyLanguageAndRate(existing, languageTag)
            onReady(true)
            return
        }
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    initialized = true
                    tts?.let { engine ->
                        applyLanguageAndRate(engine, languageTag)
                        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                _state.update {
                                    it.copy(
                                        isPlaying = true,
                                        isPaused = false,
                                        currentChunkIndex = currentIndex,
                                        totalChunks = chunks.size,
                                        statusError = null
                                    )
                                }
                            }

                            override fun onDone(utteranceId: String?) {
                                if (!_state.value.isPlaying) return
                                if (currentIndex + 1 < chunks.size) {
                                    currentIndex++
                                    playCurrentChunk()
                                } else {
                                    _state.update { it.copy(isPlaying = false, isPaused = false) }
                                    onChapterFinishedCallback?.invoke()
                                }
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                _state.update {
                                    it.copy(isPlaying = false, statusError = "Speech engine could not speak this text")
                                }
                            }
                        })
                    }
                    _state.update { it.copy(isReady = true, statusError = null) }
                    onReady(true)
                } else {
                    initialized = false
                    _state.update {
                        it.copy(
                            isReady = false,
                            isPlaying = false,
                            statusError = "Text-to-Speech engine unavailable on this device"
                        )
                    }
                    onReady(false)
                }
            }
        } catch (t: Throwable) {
            initialized = false
            _state.update {
                it.copy(
                    isReady = false,
                    isPlaying = false,
                    statusError = t.localizedMessage ?: "Text-to-Speech initialization failed"
                )
            }
            onReady(false)
        }
    }

    private fun applyLanguageAndRate(engine: TextToSpeech, languageTag: String) {
        val targetLocale = when {
            languageTag.startsWith("bn", ignoreCase = true) -> Locale.forLanguageTag("bn-BD")
            languageTag.isNotBlank() -> Locale.forLanguageTag(languageTag)
            else -> Locale.getDefault()
        }
        val res = engine.setLanguage(targetLocale)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.language = Locale.ENGLISH
        }
        engine.setSpeechRate(_state.value.speechRate)
    }

    private fun playCurrentChunk() {
        val engine = tts ?: return
        val text = chunks.getOrNull(currentIndex)?.take(3500) ?: return
        _state.update {
            it.copy(
                isPlaying = true,
                isPaused = false,
                currentChunkIndex = currentIndex,
                totalChunks = chunks.size,
                statusError = null
            )
        }
        val id = "chunk_${currentIndex}_${System.nanoTime()}"
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun release() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        initialized = false
        chunks = emptyList()
    }
}
