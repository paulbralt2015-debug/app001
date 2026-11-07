package com.twins.videoplayer.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.KeyEvent
import android.view.View

/** Menu des effets et barre de lecture, dessinés par-dessus l'image et pilotés à la télécommande. */
class MenuView(context: Context, private val fx: FxSettings, private val host: Host) : View(context) {

    interface Host {
        fun onFxChanged()
        fun cycleAudio(): String
        fun osd(): Osd
    }

    class Osd(val title: String, val pos: Long, val dur: Long, val playing: Boolean)

    private class Item(
        val label: String, val kind: Int, val key: Int, val opts: Array<String> = emptyArray(),
        val min: Int = 0, val max: Int = 0, val step: Int = 1, val unit: String = ""
    )

    var visible = false
        private set
    private var osdUntil = 0L
    private var message = ""
    private var messageUntil = 0L
    private var sel = 0
    private var scroll = 0

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val items = listOf(
        Item("Norme TV", LIST, K.STD, arrayOf("Original", "PAL", "SECAM", "NTSC")),
        Item("Rendu", LIST, K.LOOK, arrayOf("Aucun", "VHS", "Betamax", "LaserDisc", "QuickTime Windows 95",
            "Rendu PAL", "Rendu SECAM", "Rendu NTSC")),
        Item("Intensité du rendu", RANGE, K.LOOKK, min = 0, max = 100, step = 5, unit = " %"),
        Item("Sparklies blancs", RANGE, K.SPW, min = 0, max = 100, step = 5, unit = " %"),
        Item("Sparklies noirs", RANGE, K.SPB, min = 0, max = 100, step = 5, unit = " %"),
        Item("Sparklies couleur", RANGE, K.SPC, min = 0, max = 100, step = 5, unit = " %"),
        Item("Mauvaise réception terrestre", RANGE, K.RECEP, min = 0, max = 100, step = 5, unit = " %"),
        Item("Destruction des couleurs PAL", RANGE, K.PALD, min = 0, max = 100, step = 5, unit = " %"),
        Item("Destruction totale PAL (Syster / VideoCrypt)", TOGGLE, K.PALTOT),
        Item("Cryptage de l'image", LIST, K.SCR, arrayOf("Désactivé", "Nagravision (Syster)", "VideoCrypt",
            "Eurocrypt", "VideoCipher II")),
        Item("Lignes de balayage", TOGGLE, K.SCAN),
        Item("Décodage audio", LIST, K.AMODE, arrayOf("Stéréo", "Stéréo élargie", "Dolby Surround",
            "Dolby Pro Logic", "Pro Logic II Movie", "Pro Logic II Music", "Pro Logic II Matrix")),
        Item("Sortie audio", LIST, K.OUT51, arrayOf("Casque / stéréo", "Enceintes 5.1")),
        Item("Surround / largeur", RANGE, K.SLEV, min = 0, max = 300, step = 10, unit = " %"),
        Item("Centre", RANGE, K.CLEV, min = 0, max = 200, step = 10, unit = " %"),
        Item("Retard surround", RANGE, K.SDEL, min = 0, max = 60, step = 1, unit = " ms"),
        Item("Basses", RANGE, K.BASS, min = 0, max = 10, step = 1),
        Item("Mode nuit", TOGGLE, K.NIGHT),
        Item("Crypter le son (Syster)", TOGGLE, K.SNDSCR),
        Item("Vitesse", LIST, K.SPEED, arrayOf("0,5x", "0,75x", "1x", "1,25x", "1,5x", "2x")),
        Item("Format d'image", LIST, K.ASPECT, arrayOf("Auto", "4:3", "16:9", "Étiré")),
        Item("Définition d'affichage", LIST, K.RES, arrayOf("Native", "720p", "540p", "360p")),
        Item("Qualité des effets", LIST, K.QUALITY, arrayOf("Faible", "Moyenne", "Élevée")),
        Item("Effets actifs", TOGGLE, K.FXON),
        Item("Piste audio suivante", ACTION, ACT_AUDIO),
        Item("Réinitialiser les effets", ACTION, ACT_RESET)
    )

    fun show() {
        visible = true
        invalidate()
    }

    fun hide() {
        visible = false
        invalidate()
    }

    fun showOsd() {
        osdUntil = System.currentTimeMillis() + 4000
        invalidate()
    }

    fun toast(m: String) {
        message = m
        messageUntil = System.currentTimeMillis() + 3500
        invalidate()
    }

    fun osdActive(): Boolean = System.currentTimeMillis() < osdUntil || visible

    /** Retourne vrai si la touche a été traitée par le menu. */
    fun onKey(code: Int, e: KeyEvent): Boolean {
        when (code) {
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU -> hide()
            KeyEvent.KEYCODE_DPAD_UP -> sel = (sel - 1 + items.size) % items.size
            KeyEvent.KEYCODE_DPAD_DOWN -> sel = (sel + 1) % items.size
            KeyEvent.KEYCODE_DPAD_LEFT -> change(-1, e.repeatCount)
            KeyEvent.KEYCODE_DPAD_RIGHT -> change(1, e.repeatCount)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> activate()
            else -> return false
        }
        invalidate()
        return true
    }

    private fun activate() {
        val item = items[sel]
        when (item.kind) {
            ACTION -> {
                if (item.key == ACT_RESET) {
                    fx.reset()
                    host.onFxChanged()
                    toast("Effets réinitialisés")
                } else if (item.key == ACT_AUDIO) {
                    toast(host.cycleAudio())
                }
            }
            else -> change(1, 0)
        }
    }

    private fun change(dir: Int, rep: Int) {
        val item = items[sel]
        val cur = fx.v[item.key]
        when (item.kind) {
            LIST -> fx.set(item.key, (cur + dir + item.opts.size) % item.opts.size)
            RANGE -> fx.set(item.key, (cur + dir * item.step * (1 + rep / 8)).coerceIn(item.min, item.max))
            TOGGLE -> fx.set(item.key, 1 - cur)
            else -> return
        }
        host.onFxChanged()
    }

    private fun valueText(item: Item): String {
        val x = fx.v[item.key]
        return when (item.kind) {
            LIST -> item.opts[x.coerceIn(0, item.opts.size - 1)]
            RANGE -> x.toString() + item.unit
            TOGGLE -> if (x == 1) "Oui" else "Non"
            else -> ""
        }
    }

    override fun onDraw(canvas: Canvas) {
        val u = height / 1080f
        if (visible) drawMenu(canvas, u) else if (System.currentTimeMillis() < osdUntil) drawOsd(canvas, u)
        if (System.currentTimeMillis() < messageUntil) {
            paint.textSize = 34f * u
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(message, width / 2f, height * 0.12f, paint)
        }
    }

    private fun drawMenu(c: Canvas, u: Float) {
        val pw = width * 0.50f
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#E614151B")
        c.drawRect(0f, 0f, pw, height.toFloat(), paint)
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.parseColor("#3FD0D4")
        paint.textSize = 40f * u
        c.drawText("Effets et réglages", 40f * u, 70f * u, paint)
        val rowH = 56f * u
        val top = 110f * u
        val rows = ((height - top - 70f * u) / rowH).toInt().coerceAtLeast(3)
        if (sel < scroll) scroll = sel
        if (sel >= scroll + rows) scroll = sel - rows + 1
        paint.textSize = 30f * u
        for (r in 0 until rows) {
            val idx = scroll + r
            if (idx >= items.size) break
            val y = top + r * rowH
            if (idx == sel) {
                paint.color = Color.parseColor("#2A2D3C")
                c.drawRect(RectF(20f * u, y, pw - 20f * u, y + rowH - 6f * u), paint)
                paint.color = Color.parseColor("#F0558A")
                c.drawRect(RectF(20f * u, y, 28f * u, y + rowH - 6f * u), paint)
            }
            val item = items[idx]
            paint.color = if (idx == sel) Color.WHITE else Color.parseColor("#B8BAC6")
            paint.textAlign = Paint.Align.LEFT
            c.drawText(item.label, 44f * u, y + 38f * u, paint)
            paint.textAlign = Paint.Align.RIGHT
            val t = valueText(item)
            c.drawText(if (idx == sel && item.kind != ACTION) "<  $t  >" else t, pw - 40f * u, y + 38f * u, paint)
        }
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 24f * u
        paint.color = Color.parseColor("#8D90A0")
        c.drawText("Haut / Bas : choisir   Gauche / Droite : modifier   Retour : fermer", 40f * u, height - 28f * u, paint)
    }

    private fun drawOsd(c: Canvas, u: Float) {
        val o = host.osd()
        val x0 = 80f * u
        val x1 = width - 80f * u
        val y = height - 110f * u
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#B314151B")
        c.drawRect(0f, y - 70f * u, width.toFloat(), height.toFloat(), paint)
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.WHITE
        paint.textSize = 32f * u
        c.drawText((if (o.playing) "▶  " else "❚❚  ") + o.title, x0, y - 20f * u, paint)
        paint.color = Color.parseColor("#2E3040")
        c.drawRect(x0, y + 10f * u, x1, y + 22f * u, paint)
        if (o.dur > 0) {
            paint.color = Color.parseColor("#3FD0D4")
            c.drawRect(x0, y + 10f * u, x0 + (x1 - x0) * (o.pos.toFloat() / o.dur).coerceIn(0f, 1f), y + 22f * u, paint)
        }
        paint.color = Color.parseColor("#B8BAC6")
        paint.textSize = 26f * u
        c.drawText(fmt(o.pos) + " / " + fmt(o.dur), x0, y + 62f * u, paint)
        paint.textAlign = Paint.Align.RIGHT
        c.drawText("Haut : effets   Gauche / Droite : ±10 s", x1, y + 62f * u, paint)
    }

    private fun fmt(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = s % 3600 / 60
        val r = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, r) else String.format("%d:%02d", m, r)
    }

    companion object {
        private const val LIST = 0
        private const val RANGE = 1
        private const val TOGGLE = 2
        private const val ACTION = 3
        private const val ACT_RESET = 1000
        private const val ACT_AUDIO = 1001
    }
}
