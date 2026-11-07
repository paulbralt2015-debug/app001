package com.twins.videoplayer.tv

import android.content.Context
import java.util.Locale

/**
 * Assemble le shader d'effets (GLSL ES 1.00, compatible GPU anciens) à partir du modèle
 * assets/fx_body.glsl, du même texte que celui du lecteur de bureau.
 */
class ShaderBuilder(context: Context) {
    private val body: String = context.assets.open("fx_body.glsl").bufferedReader().use { it.readText() }

    private fun fl(x: Double): String = String.format(Locale.US, "%.4f", x)

    /** Shaders à essayer dans l'ordre : qualité choisie, qualité minimale, puis sans effet. */
    fun candidates(s: FxSettings): List<String> {
        val taps = intArrayOf(2, 4, 6)[s.v[K.QUALITY].coerceIn(0, 2)]
        val list = ArrayList<String>()
        val full = build(s, taps)
        if (full != null) {
            list.add(full)
            if (taps > 2) build(s, 2)?.let { list.add(it) }
        }
        list.add(PASSTHROUGH)
        return list
    }

    private fun build(s: FxSettings, taps: Int): String? {
        val v = s.v
        if (v[K.FXON] == 0) return null
        var std = v[K.STD]
        val look = v[K.LOOK]
        val k = v[K.LOOKK] / 100.0
        val scr = v[K.SCR]
        val spw = v[K.SPW]
        val spb = v[K.SPB]
        val spc = v[K.SPC]
        val recep = v[K.RECEP]
        val pald = v[K.PALD]
        val scan = v[K.SCAN]
        if (look in 5..7) std = look - 4
        val paltot = if (v[K.PALTOT] == 1 && (scr == 1 || scr == 2)) 1 else 0
        if (paltot == 1) std = 1

        var sigl = 0.0
        var sigc = 0.0
        var vmode = 0
        var sat = 1.0
        var setup = 0
        var lines = 0
        if (std in 1..3) {
            val t = STD[std - 1]
            sigl = t[0]
            sigc = t[1]
            vmode = t[2].toInt()
            sat = t[3]
            setup = t[4].toInt()
            lines = t[5].toInt()
        }
        var cshift = 0.0
        var con = 1.0
        var noise = 0.0
        var sharp = 0.0
        when (look) {
            1 -> {
                sigl = Math.hypot(sigl, 0.6 + 1.2 * k)
                sigc = Math.hypot(sigc, 2.0 + 5.0 * k)
                cshift = 2.0 + 4.0 * k
                sat *= (1.0 - 0.2 * k)
                con = 1.0 - 0.08 * k
                noise = (3.0 + 8.0 * k) / 255.0
            }
            2 -> {
                sigl = Math.hypot(sigl, 0.5 + 0.5 * k)
                sigc = Math.hypot(sigc, 1.5 + 3.0 * k)
                cshift = 1.0 + k
                con = 0.98
                noise = (2.0 + 4.0 * k) / 255.0
            }
            3 -> {
                sigl = Math.hypot(sigl, 0.8)
                sigc = Math.hypot(sigc, 1.0 + 1.5 * k)
                cshift = 1.0
                sat *= 1.05
                noise = (2.0 + 3.0 * k) / 255.0
                sharp = 0.3 + 0.5 * k
            }
            5 -> noise = (1.0 + 2.0 * k) / 255.0
            7 -> noise = (2.0 + 3.0 * k) / 255.0
        }
        val nothing = look == 0 && scr == 0 && spw == 0 && spb == 0 && spc == 0 && recep == 0 &&
            pald == 0 && paltot == 0 && scan == 0 && std !in 1..3
        if (nothing) return null

        fun b(x: Boolean) = if (x) "1" else "0"
        val m = mapOf(
            "SIGL" to fl(sigl), "SIGC" to fl(sigc), "VMODE" to vmode.toString(), "CSHIFT" to fl(cshift),
            "SAT" to fl(sat), "CON" to fl(con), "NOISE" to fl(noise), "SETUP" to setup.toString(),
            "SHARP" to fl(sharp), "LINES" to lines.toString(), "LOOK" to look.toString(), "LOOKK" to fl(k),
            "SPW" to fl(spw / 100.0 * 0.006), "SPB" to fl(spb / 100.0 * 0.006), "SPC" to fl(spc / 100.0 * 0.006),
            "RECEP" to fl(recep / 100.0), "PALD" to fl(pald / 100.0), "PALTOT" to paltot.toString(),
            "SCR" to scr.toString(), "SCAN" to scan.toString(), "TAPS" to taps.toString(),
            "HAS_RECEP" to b(recep > 0), "HAS_PALD" to b(pald > 0),
            "HAS_SPARK" to b(spw > 0 || spb > 0 || spc > 0),
            "HAS_NOISE" to b(noise > 0.0), "HAS_SHARP" to b(sharp > 0.0)
        )
        var src = body
        for ((key, value) in m) src = src.replace("@" + key + "@", value)
        return HEADER + src + FOOTER
    }

    companion object {
        // norme : sigma luma, sigma chroma, mode vertical, saturation, noir relevé, lignes
        private val STD = arrayOf(
            doubleArrayOf(0.0, 2.0, 1.0, 1.0, 0.0, 576.0),   // PAL
            doubleArrayOf(0.0, 2.0, 2.0, 0.92, 0.0, 576.0),  // SECAM
            doubleArrayOf(0.6, 3.0, 0.0, 1.08, 1.0, 480.0)   // NTSC
        )

        const val HEADER = """#extension GL_OES_EGL_image_external : require
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform samplerExternalOES sTex;
uniform mat4 uST;
uniform vec2 sizeu;
uniform float uFrame;
varying vec2 pos;
#define frame uFrame
#define HOOKED_pos pos
#define HOOKED_size sizeu
vec4 HOOKED_tex(vec2 p) { return texture2D(sTex, (uST * vec4(p.x, 1.0 - p.y, 0.0, 1.0)).xy); }
"""
        const val FOOTER = "\nvoid main() { gl_FragColor = hook(); }\n"
        const val PASSTHROUGH = HEADER + "vec4 hook() { return HOOKED_tex(HOOKED_pos); }\n" + FOOTER
    }
}
