package com.tutu.adplayer

import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import android.widget.AdapterView

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val playbackModeSpinner = findViewById<Spinner>(R.id.playback_mode_spinner)
        val clearPlaylistButton = findViewById<Button>(R.id.clear_playlist_button)
        val autoPlayCheckbox = findViewById<CheckBox>(R.id.auto_play_checkbox)

        val modes = arrayOf("循环所有", "单曲循环", "随机")
        playbackModeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        playbackModeSpinner.setSelection(prefs.getInt("playback_mode", 0))
        autoPlayCheckbox.isChecked = prefs.getBoolean("auto_play", false)

        playbackModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                prefs.edit().putInt("playback_mode", position).apply()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        clearPlaylistButton.setOnClickListener {
            getSharedPreferences("playlist", MODE_PRIVATE).edit().clear().apply()
            setResult(RESULT_OK) // 通知 MainActivity 刷新
            finish()
        }

        autoPlayCheckbox.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_play", isChecked).apply()
        }
    }
}