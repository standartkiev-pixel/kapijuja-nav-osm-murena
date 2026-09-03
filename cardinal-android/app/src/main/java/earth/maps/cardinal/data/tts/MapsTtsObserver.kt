/*
 *     Cardinal Maps
 *     Copyright (C) 2026 Cardinal Maps Authors
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package earth.maps.cardinal.data.tts

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.stadiamaps.ferrostar.core.AndroidTtsStatusListener
import com.stadiamaps.ferrostar.core.SpokenInstructionObserver
import earth.maps.cardinal.data.audio.MapsAudioFocusController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uniffi.ferrostar.SpokenInstruction

class MapsTtsObserver(
    private val ttsFactory: TtsFactory,
    private val audioFocusController: MapsAudioFocusController,
    private val engine: String? = null,
    private val statusObserver: AndroidTtsStatusListener? = null
) : SpokenInstructionObserver, TextToSpeech.OnInitListener {

    private val pendingInstructions = ArrayDeque<SpokenInstruction>()
    private val _muteState = MutableStateFlow(false)
    override val muteState: StateFlow<Boolean> = _muteState.asStateFlow()
    private var tts: TextToSpeech? = null
    private var initStatus: Int? = null
    val isInitializedSuccessfully: Boolean
        get() = initStatus == TextToSpeech.SUCCESS

    fun start() {
        if (tts != null) return
        tts = ttsFactory.create(this, engine)
    }

    override fun onInit(status: Int) {
        initStatus = status

        if (status != TextToSpeech.SUCCESS) {
            statusObserver?.onTtsInitialized(null, status)
            shutdown()
            return
        }

        tts?.setOnUtteranceProgressListener(progressListener)
        statusObserver?.onTtsInitialized(tts, status)
        flushPendingInstructions()
    }

    override fun setMuted(isMuted: Boolean) {
        _muteState.value = isMuted
        if (isMuted) stopAndClearQueue()
    }

    override fun onSpokenInstructionTrigger(spokenInstruction: SpokenInstruction) {
        val tts = tts ?: return
        if (!isInitializedSuccessfully) {
            if (pendingInstructions.isNotEmpty()) {
                pendingInstructions.clear()
            }
            pendingInstructions.add(spokenInstruction)
            return
        }

        if (isMuted) return

        if (!audioFocusController.requestFocus()) return

        val utteranceId = spokenInstruction.utteranceId.toString()

        val result = tts.speak(
            spokenInstruction.text,
            TextToSpeech.QUEUE_ADD,
            null,
            utteranceId
        )

        if (result != TextToSpeech.SUCCESS) {
            audioFocusController.abandonFocus()
            statusObserver?.onTtsSpeakError(utteranceId, result)
        }
    }

    private fun flushPendingInstructions() {
        while (pendingInstructions.isNotEmpty()) {
            val instruction = pendingInstructions.removeFirst()
            onSpokenInstructionTrigger(instruction)
        }
    }

    override fun stopAndClearQueue() {
        tts?.stop()
        audioFocusController.abandonFocus()
    }

    fun shutdown() {
        stopAndClearQueue()
        tts?.shutdown()
        tts = null
        statusObserver?.onTtsShutdownAndRelease()
    }

    private val progressListener = object : UtteranceProgressListener() {

        override fun onStart(utteranceId: String?) {
            // No-op
        }

        override fun onDone(utteranceId: String?) {
            audioFocusController.abandonFocus()
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            audioFocusController.abandonFocus()
        }
    }
}