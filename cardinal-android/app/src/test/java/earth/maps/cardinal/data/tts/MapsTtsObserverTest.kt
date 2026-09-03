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
import com.stadiamaps.ferrostar.core.AndroidTtsStatusListener
import earth.maps.cardinal.data.audio.MapsAudioFocusController
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import uniffi.ferrostar.SpokenInstruction
import java.util.UUID

class MapsTtsObserverTest {

    private val ttsFactory = mockk<TtsFactory>()
    private val audioFocus = mockk<MapsAudioFocusController>(relaxed = true)
    private val tts = mockk<TextToSpeech>(relaxed = true)
    private val statusObserver = mockk<AndroidTtsStatusListener>(relaxed = true)

    private lateinit var observer: MapsTtsObserver

    @Before
    fun setup() {
        every { ttsFactory.create(any(), any()) } returns tts

        observer = MapsTtsObserver(
            ttsFactory = ttsFactory,
            audioFocusController = audioFocus,
            statusObserver = statusObserver
        )
    }

    @Test
    fun `start should create TTS instance`() {
        observer.start()

        verify { ttsFactory.create(observer, null) }
    }

    @Test
    fun `should buffer instruction when TTS not initialized`() {
        observer.start()

        val instruction = instruction(text = "Turn left")

        observer.onSpokenInstructionTrigger(instruction)

        verify(exactly = 0) { tts.speak(any(), any(), any(), any()) }
        verify(exactly = 0) { audioFocus.requestFocus() }
    }

    @Test
    fun `should flush buffered instruction after init`() {
        observer.start()

        val id = UUID.randomUUID()
        val inst = instruction("Turn right", id)

        observer.onSpokenInstructionTrigger(inst)

        every { audioFocus.requestFocus() } returns true
        every { tts.speak(any(), any(), any(), any()) } returns TextToSpeech.SUCCESS

        observer.onInit(TextToSpeech.SUCCESS)

        verify { audioFocus.requestFocus() }
        verify {
            tts.speak(
                "Turn right",
                TextToSpeech.QUEUE_ADD,
                null,
                id.toString()
            )
        }
    }

    @Test
    fun `should request focus and speak`() {
        observer.start()
        observer.onInit(TextToSpeech.SUCCESS)

        val id = UUID.randomUUID()
        val inst = instruction("Go straight", id)

        every { audioFocus.requestFocus() } returns true
        every { tts.speak(any(), any(), any(), any()) } returns TextToSpeech.SUCCESS

        observer.onSpokenInstructionTrigger(inst)

        verify { audioFocus.requestFocus() }

        verify {
            tts.speak(
                "Go straight",
                TextToSpeech.QUEUE_ADD,
                null,
                id.toString()
            )
        }
    }

    @Test
    fun `should abandon focus on speak error`() {
        observer.start()
        observer.onInit(TextToSpeech.SUCCESS)

        val id = UUID.randomUUID()
        val inst = instruction("Error test", id)

        every { audioFocus.requestFocus() } returns true
        every { tts.speak(any(), any(), any(), any()) } returns TextToSpeech.ERROR

        observer.onSpokenInstructionTrigger(inst)

        verify { audioFocus.abandonFocus() }
        verify { statusObserver.onTtsSpeakError(id.toString(), TextToSpeech.ERROR) }
    }

    private fun instruction(
        text: String,
        id: UUID = UUID.randomUUID()
    ): SpokenInstruction {
        return SpokenInstruction(
            text = text,
            ssml = null,
            triggerDistanceBeforeManeuver = 10.0,
            utteranceId = id
        )
    }
}