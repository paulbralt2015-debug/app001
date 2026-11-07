package com.twins.videoplayer.tv

import android.content.Context

/** Indices des réglages (tableau d'entiers : simple à enregistrer et à lire depuis le fil audio). */
object K {
    const val STD = 0        // 0 Original, 1 PAL, 2 SECAM, 3 NTSC
    const val LOOK = 1       // 0 aucun, 1 VHS, 2 Betamax, 3 LaserDisc, 4 QuickTime 95, 5 PAL, 6 SECAM, 7 NTSC
    const val LOOKK = 2
    const val SPW = 3
    const val SPB = 4
    const val SPC = 5
    const val RECEP = 6
    const val PALD = 7
    const val PALTOT = 8
    const val SCR = 9        // 0 aucun, 1 Nagravision (Syster), 2 VideoCrypt, 3 Eurocrypt, 4 VideoCipher II
    const val SCAN = 10
    const val AMODE = 11     // 0 stéréo, 1 élargie, 2 Dolby Surround, 3 Pro Logic, 4 PL II Movie, 5 Music, 6 Matrix
    const val OUT51 = 12
    const val SLEV = 13
    const val CLEV = 14
    const val SDEL = 15
    const val BASS = 16
    const val NIGHT = 17
    const val SNDSCR = 18
    const val SPEED = 19
    const val ASPECT = 20    // 0 auto, 1 4:3, 2 16:9, 3 étiré
    const val FXON = 21
    const val QUALITY = 22   // 0 faible, 1 moyenne, 2 élevée
    const val RES = 23       // 0 native, 1 720p, 2 540p, 3 360p
    const val N = 24
}

class FxSettings(context: Context) {
    private val prefs = context.getSharedPreferences("fx", Context.MODE_PRIVATE)
    val v = IntArray(K.N)

    @Volatile
    var version = 0

    init {
        defaults()
        for (i in 0 until K.N) v[i] = prefs.getInt("k$i", v[i])
    }

    private fun defaults() {
        v.fill(0)
        v[K.LOOKK] = 70
        v[K.SLEV] = 100
        v[K.CLEV] = 100
        v[K.SDEL] = 15
        v[K.SPEED] = 2
        v[K.FXON] = 1
        v[K.QUALITY] = 1
    }

    fun reset() {
        defaults()
        save()
        version++
    }

    fun set(k: Int, x: Int) {
        v[k] = x
        when (k) {
            K.LOOK -> if (x in 5..7) v[K.STD] = x - 4
            K.SCR -> {
                if (x in 1..4) v[K.STD] = intArrayOf(0, 2, 1, 1, 3)[x]
                if (v[K.PALTOT] == 1 && (x == 1 || x == 2)) v[K.STD] = 1
            }
            K.PALTOT -> if (x == 1) {
                if (v[K.SCR] != 1 && v[K.SCR] != 2) v[K.SCR] = 1
                v[K.STD] = 1
            }
        }
        save()
        version++
    }

    private fun save() {
        val e = prefs.edit()
        for (i in 0 until K.N) e.putInt("k$i", v[i])
        e.apply()
    }
}
