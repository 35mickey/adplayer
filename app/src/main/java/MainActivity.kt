package com.tutu.adplayer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat

class MainActivity : AppCompatActivity() {

    private lateinit var playlist: ListView
    private val videoFiles = mutableListOf<String>()
    private val REQUEST_CODE_SETTINGS = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tabSelectPath = findViewById<Button>(R.id.tab_select_path)
        val tabSettings = findViewById<Button>(R.id.tab_settings)
        playlist = findViewById(R.id.playlist)

        tabSelectPath.setOnClickListener {
            startActivityForResult(Intent(this, PathSelectionActivity::class.java), 1)
        }

        tabSettings.setOnClickListener {
            startActivityForResult(Intent(this, SettingsActivity::class.java), REQUEST_CODE_SETTINGS)
        }

        setupButtonFocus(tabSelectPath, tabSettings)
        loadPlaylist()
        updatePlaylist()

        if (getSharedPreferences("settings", MODE_PRIVATE).getBoolean("auto_play", false) && videoFiles.isNotEmpty()) {
            startPlayer(0)
        }
    }

    private fun setupButtonFocus(vararg buttons: Button) {
        buttons.forEach { button ->
            button.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    ViewCompat.animate(button)
                        .scaleX(1.2f)
                        .scaleY(1.2f)
                        .setDuration(200)
                        .start()
                } else {
                    ViewCompat.animate(button)
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(200)
                        .start()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) {
            val selectedFiles = data?.getStringArrayListExtra("video_files")
            if (selectedFiles != null) {
                videoFiles.clear()
                videoFiles.addAll(selectedFiles)
                savePlaylist()
                updatePlaylist()
            }
        } else if (requestCode == REQUEST_CODE_SETTINGS && resultCode == RESULT_OK) {
            loadPlaylist()
            updatePlaylist()
        }
    }

    private fun updatePlaylist() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            if (videoFiles.isEmpty()) listOf(getString(R.string.playlist_empty)) else videoFiles
        )
        playlist.adapter = adapter
        playlist.setOnItemClickListener { _, _, position, _ ->
            if (videoFiles.isNotEmpty()) {
                startPlayer(position)
            }
        }
    }

    private fun startPlayer(position: Int) {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putStringArrayListExtra("video_files", ArrayList(videoFiles))
        intent.putExtra("position", position)
        startActivity(intent)
    }

    private fun savePlaylist() {
        val prefs = getSharedPreferences("playlist", MODE_PRIVATE)
        prefs.edit().putStringSet("videos", videoFiles.toSet()).apply()
    }

    private fun loadPlaylist() {
        val prefs = getSharedPreferences("playlist", MODE_PRIVATE)
        videoFiles.clear()
        videoFiles.addAll(prefs.getStringSet("videos", emptySet()) ?: emptySet())
    }
}