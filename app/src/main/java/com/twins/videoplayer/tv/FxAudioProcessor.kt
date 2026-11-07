package com.twins.videoplayer.tv

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Traitement audio du lecteur : décodage matriciel Dolby Surround / Pro Logic / Pro Logic II (approximation),
 * stéréo élargie, mode nuit et cryptage du son par inversion de spectre (simulation Syster).
 * S'applique aux flux PCM 16 bits stéréo ; les autres formats passent sans traitement.
 */
class FxAudioProcessor(private val s: FxSettings) : BaseAudioProcessor() {

    /** Filtre passe-bas du second ordre (Butterworth). */
    private class Biquad(fc: Double, fs: Double) {
        private val b0: Double
        private val b1: Double
        private val b2: Double
        private val a1: Double
        private val a2: Double
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        init {
            val w = 2.0 * PI * fc / fs
            val cs = cos(w)
            val al = sin(w) / (2.0 * 0.7071)
            val a0 = 1.0 + al
            b0 = (1.0 - cs) / 2.0 / a0
            b1 = (1.0 - cs) / a0
            b2 = b0
            a1 = -2.0 * cs / a0
            a2 = (1.0 - al) / a0
        }

        fun p(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            return y
        }
    }

    private var fs = 48000
    private var out51 = false
    private var outCh = 2
    private var carrier = 3800.0
    private var phase = 0.0
    private var wi = 0
    private var dLs = FloatArray(1)
    private var dRs = FloatArray(1)
    private var lpS = Biquad(7000.0, 48000.0)
    private var pre = Array(6) { Biquad(3800.0, 48000.0) }
    private var post = Array(6) { Biquad(3800.0, 48000.0) }
    private val ch = FloatArray(6)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET      // processeur inactif pour ce flux
        }
        fs = inputAudioFormat.sampleRate
        out51 = s.v[K.AMODE] >= 2 && s.v[K.OUT51] == 1
        outCh = if (out51) 6 else 2
        val n = (fs * 0.07).toInt() + 8
        dLs = FloatArray(n)
        dRs = FloatArray(n)
        wi = 0
        carrier = min(3800.0, fs * 0.24)
        val f = fs.toDouble()
        lpS = Biquad(min(7000.0, f * 0.45), f)
        pre = Array(6) { Biquad(carrier, f) }
        post = Array(6) { Biquad(carrier, f) }
        phase = 0.0
        return AudioProcessor.AudioFormat(fs, outCh, C.ENCODING_PCM_16BIT)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val frames = (inputBuffer.limit() - inputBuffer.position()) / 4
        val out = replaceOutputBuffer(frames * outCh * 2).order(ByteOrder.LITTLE_ENDIAN)

        val mode = s.v[K.AMODE]
        val sl = s.v[K.SLEV] / 100f
        val cl = s.v[K.CLEV] / 100f
        val night = s.v[K.NIGHT] == 1
        val scr = s.v[K.SNDSCR] == 1
        val n = dLs.size
        val dl = (s.v[K.SDEL] * fs / 1000).coerceIn(0, n - 1)
        val dr = (dl + fs * 3 / 1000).coerceIn(0, n - 1)
        // poids d'extraction du centre, surround mono ?, niveau surround
        val c = when (mode) { 2 -> 0f; 3 -> 1f; 4 -> 1f; 5 -> 0.7f; else -> 1f }
        val mono = mode == 2 || mode == 3
        val sm = when (mode) { 5 -> 0.75f; 6 -> 0.9f; else -> 1f }
        val a = 1f - 0.5f * c
        val b = -0.5f * c
        val k = sm * sl
        val cc = 0.707f * c * cl
        val dph = 2.0 * PI * carrier / fs

        for (i in 0 until frames) {
            val l = inputBuffer.getShort() / 32768f
            val r = inputBuffer.getShort() / 32768f
            var fl = l
            var fr = r
            var fc = 0f
            var ls = 0f
            var rs = 0f
            if (mode == 1) {                                   // stéréo élargie (mid / side)
                val m = (l + r) * 0.5f
                val sd = (l - r) * 0.5f * sl
                fl = m + sd
                fr = m - sd
            } else if (mode >= 2) {                            // décodage matriciel
                fl = a * l + b * r
                fr = a * r + b * l
                fc = cc * (l + r)
                val inL: Float
                val inR: Float
                if (mono) {
                    val sMono = lpS.p((0.707f * k * (l - r)).toDouble()).toFloat()
                    inL = sMono
                    inR = sMono
                } else {
                    inL = k * (l - 0.6f * r)
                    inR = k * (0.6f * l - r)
                }
                dLs[wi] = inL
                dRs[wi] = inR
                ls = dLs[(wi - dl + n) % n]
                rs = dRs[(wi - (if (mono) dl else dr) + n) % n]
                wi = (wi + 1) % n
            }
            if (mode >= 2) {
                if (out51) {
                    ch[0] = fl; ch[1] = fr; ch[2] = fc; ch[3] = 0f; ch[4] = ls; ch[5] = rs
                    for (j in 0 until 6) ch[j] *= 0.8f
                } else {                                       // casque : mélange stéréo
                    ch[0] = (fl + 0.707f * fc + 0.707f * ls) * 0.8f
                    ch[1] = (fr + 0.707f * fc + 0.707f * rs) * 0.8f
                }
            } else {
                ch[0] = fl
                ch[1] = fr
            }
            if (night) {                                       // compression : sons faibles relevés, pics écrêtés
                for (j in 0 until outCh) {
                    val x = ch[j]
                    val ax = abs(x)
                    val y = (if (ax > 0.25f) 0.25f + (ax - 0.25f) / 4f else ax) * 1.8f
                    ch[j] = if (x < 0f) -y else y
                }
            }
            if (scr) {                                         // inversion de spectre (porteuse 3,8 kHz)
                val car = 2.0 * cos(phase)
                for (j in 0 until outCh) {
                    var x = pre[j].p(ch[j].toDouble())
                    x *= car
                    ch[j] = post[j].p(x).toFloat()
                }
                phase += dph
                if (phase > 2.0 * PI) phase -= 2.0 * PI
            }
            for (j in 0 until outCh) {
                val x = ch[j].coerceIn(-1f, 1f)
                out.putShort((x * 32767f).toInt().toShort())
            }
        }
        out.flip()
    }
}
