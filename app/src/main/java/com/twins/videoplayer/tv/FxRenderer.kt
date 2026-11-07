package com.twins.videoplayer.tv

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Reçoit les images du lecteur dans une SurfaceTexture et les dessine avec le shader d'effets.
 * OpenGL ES 2.0 seulement, pour fonctionner aussi sur les GPU des vieilles TV (Mali-4xx, etc.).
 */
class FxRenderer(
    private val onSurface: (Surface) -> Unit,
    private val onMessage: (String) -> Unit
) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    @Volatile var candidates: List<String> = listOf(ShaderBuilder.PASSTHROUGH)
    @Volatile var dirty = true
    @Volatile var videoW = 1280
    @Volatile var videoH = 720
    @Volatile var pixelRatio = 1f
    @Volatile var aspectMode = 0

    private var tex = 0
    private var st: SurfaceTexture? = null
    private val stm = FloatArray(16)
    private var prog = 0
    private var aPos = -1
    private var uST = -1
    private var uSize = -1
    private var uFrame = -1
    private var uTex = -1
    @Volatile private var newFrame = false
    private var frame = 0
    private var sw = 1
    private var sh = 1
    private var lastLog = ""
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Matrix.setIdentityM(stm, 0)
        val t = IntArray(1)
        GLES20.glGenTextures(1, t, 0)
        tex = t[0]
        val target = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
        GLES20.glBindTexture(target, tex)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        st?.release()
        val s = SurfaceTexture(tex)
        s.setOnFrameAvailableListener(this)
        st = s
        onSurface(Surface(s))
        prog = 0
        dirty = true
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        sw = width
        sh = height
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        newFrame = true
    }

    override fun onDrawFrame(gl: GL10?) {
        if (dirty) {
            dirty = false
            rebuild()
        }
        if (newFrame) {
            newFrame = false
            try {
                st?.updateTexImage()
                st?.getTransformMatrix(stm)
                frame = (frame + 1) % 8192
            } catch (e: Exception) {
                Log.e(TAG, "updateTexImage", e)
            }
        }
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (prog == 0) return

        // cadrage : conserve le format de l'image (ou impose 4:3, 16:9, ou étire)
        var w = sw
        var h = sh
        if (aspectMode != 3 && sw > 0 && sh > 0) {
            val sar = videoW * pixelRatio / videoH
            val tar = when (aspectMode) {
                1 -> 4f / 3f
                2 -> 16f / 9f
                else -> sar
            }
            val surfAr = sw.toFloat() / sh
            if (tar > surfAr) h = (sw / tar).toInt() else w = (sh * tar).toInt()
        }
        GLES20.glViewport((sw - w) / 2, (sh - h) / 2, w, h)
        GLES20.glUseProgram(prog)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex)
        GLES20.glUniform1i(uTex, 0)
        GLES20.glUniformMatrix4fv(uST, 1, false, stm, 0)
        GLES20.glUniform2f(uSize, videoW.toFloat(), videoH.toFloat())
        GLES20.glUniform1f(uFrame, frame.toFloat())
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
    }

    private fun rebuild() {
        if (prog != 0) {
            GLES20.glDeleteProgram(prog)
            prog = 0
        }
        var first = true
        for (src in candidates) {
            val p = link(src)
            if (p != 0) {
                prog = p
                break
            }
            if (first) onMessage("Shader refusé par le GPU : qualité réduite. " + lastLog.take(120))
            first = false
        }
        if (prog != 0) {
            aPos = GLES20.glGetAttribLocation(prog, "aPos")
            uST = GLES20.glGetUniformLocation(prog, "uST")
            uSize = GLES20.glGetUniformLocation(prog, "sizeu")
            uFrame = GLES20.glGetUniformLocation(prog, "uFrame")
            uTex = GLES20.glGetUniformLocation(prog, "sTex")
        }
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            lastLog = GLES20.glGetShaderInfoLog(s)
            Log.e(TAG, "Shader : " + lastLog)
            GLES20.glDeleteShader(s)
            return 0
        }
        return s
    }

    private fun link(frag: String): Int {
        val vs = compile(GLES20.GL_VERTEX_SHADER, VERTEX)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, frag)
        if (vs == 0 || fs == 0) return 0
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs)
        GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            lastLog = GLES20.glGetProgramInfoLog(p)
            Log.e(TAG, "Link : " + lastLog)
            GLES20.glDeleteProgram(p)
            return 0
        }
        return p
    }

    companion object {
        private const val TAG = "TwinsFx"
        private const val VERTEX = """attribute vec2 aPos;
varying vec2 pos;
void main() {
    pos = vec2(aPos.x * 0.5 + 0.5, 0.5 - aPos.y * 0.5);
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""
    }
}
