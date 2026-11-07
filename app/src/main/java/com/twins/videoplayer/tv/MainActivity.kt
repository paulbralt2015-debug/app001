package com.twins.videoplayer.tv

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

class MainActivity : Activity() {
    private val videos = ArrayList<Pair<String, Uri>>()
    private lateinit var list: ListView
    private lateinit var info: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(80, 50, 80, 40)
        root.setBackgroundColor(0xFF14151B.toInt())

        val title = TextView(this)
        title.text = "Twins Video Player 2"
        title.textSize = 36f
        title.setTextColor(0xFF3FD0D4.toInt())

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.START
        val b1 = Button(this)
        b1.text = "Ouvrir des fichiers"
        b1.setOnClickListener { openPicker() }
        val b2 = Button(this)
        b2.text = "Actualiser la liste"
        b2.setOnClickListener { loadVideos() }
        row.addView(b1)
        row.addView(b2)

        info = TextView(this)
        info.textSize = 20f
        info.setTextColor(0xFFB8BAC6.toInt())

        list = ListView(this)
        list.setOnItemClickListener { _, _, pos, _ -> play(pos) }

        root.addView(title)
        root.addView(row)
        root.addView(info)
        root.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        loadVideos()
    }

    private fun permission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun loadVideos() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(permission()) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission()), 1)
            return
        }
        videos.clear()
        try {
            val base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val proj = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME)
            contentResolver.query(base, proj, null, null, MediaStore.Video.Media.DISPLAY_NAME + " ASC")?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = c.getString(1) ?: ("Vidéo " + id)
                    videos.add(Pair(name, ContentUris.withAppendedId(base, id)))
                }
            }
        } catch (e: Exception) {
            info.text = "Lecture de la médiathèque impossible : " + e.message
        }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, videos.map { it.first })
        info.text = if (videos.isEmpty()) {
            "Aucune vidéo trouvée. Utilisez « Ouvrir des fichiers » ou branchez une clé USB."
        } else {
            videos.size.toString() + " vidéo(s). Choisissez-en une pour lancer la lecture."
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadVideos()
        } else {
            info.text = "Permission refusée : seul « Ouvrir des fichiers » est disponible."
        }
    }

    private fun play(pos: Int) {
        startPlayer(videos.map { it.second }, videos.map { it.first }, pos)
    }

    private fun startPlayer(uris: List<Uri>, names: List<String>, index: Int) {
        val i = Intent(this, PlayerActivity::class.java)
        i.putStringArrayListExtra("uris", ArrayList(uris.map { it.toString() }))
        i.putStringArrayListExtra("names", ArrayList(names))
        i.putExtra("index", index)
        startActivity(i)
    }

    private fun openPicker() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "video/*"
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        try {
            startActivityForResult(i, 2)
        } catch (e: Exception) {
            info.text = "Aucun explorateur de fichiers sur cette TV : utilisez la liste ci-dessous."
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 2 || resultCode != RESULT_OK || data == null) return
        val uris = ArrayList<Uri>()
        val clip = data.clipData
        if (clip != null) {
            for (k in 0 until clip.itemCount) uris.add(clip.getItemAt(k).uri)
        } else {
            data.data?.let { uris.add(it) }
        }
        if (uris.isNotEmpty()) startPlayer(uris, uris.map { Uri.decode(it.lastPathSegment ?: "") }, 0)
    }
}
