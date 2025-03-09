package com.tutu.adplayer

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import java.io.File

class PlayerActivity : AppCompatActivity() {

    private lateinit var videoView: VideoView
    private lateinit var seekBar: SeekBar
    private lateinit var fileName: TextView
    private lateinit var playbackMode: Button
    private lateinit var previousButton: Button
    private lateinit var playPauseButton: Button
    private lateinit var nextButton: Button
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var controlLayout: View

    private lateinit var videoFiles: ArrayList<String>
    private var currentPosition = 0
    private var mode = 0
    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "PlayerActivity"
    private val progressRunnable = object : Runnable {
        override fun run() {
            if (videoView.isPlaying) {
                val currentPos = videoView.currentPosition
                seekBar.progress = currentPos
                updateTimeDisplay()
                savePlaybackState(currentPos) // 实时保存播放状态
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置全屏模式，确保在 setContentView 之前
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        // 添加沉浸式模式，隐藏状态栏和导航栏
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

        setContentView(R.layout.activity_player)

        videoView = findViewById(R.id.video_view)
        seekBar = findViewById(R.id.seek_bar)
        fileName = findViewById(R.id.file_name)
        playbackMode = findViewById(R.id.playback_mode)
        previousButton = findViewById(R.id.previous_button)
        playPauseButton = findViewById(R.id.play_pause_button)
        nextButton = findViewById(R.id.next_button)
        currentTime = findViewById(R.id.current_time)
        totalTime = findViewById(R.id.total_time)
        controlLayout = findViewById(R.id.control_layout)

        videoFiles = intent.getStringArrayListExtra("video_files") ?: ArrayList()
        mode = getSharedPreferences("settings", MODE_PRIVATE).getInt("playback_mode", 0)

        val prefs = getSharedPreferences("playback_state", MODE_PRIVATE)
        currentPosition = prefs.getInt("last_video_index", intent.getIntExtra("position", 0))
        val lastProgress = prefs.getInt("last_progress", 0)

        if (videoFiles.isEmpty()) {
            finish()
            return
        }

        setupControls()
        setupFocus()
        setupTouch()
        playVideo(lastProgress)
    }

    private fun setupControls() {
        playbackMode.text = when (mode) {
            0 -> getString(R.string.cycle_all)
            1 -> getString(R.string.cycle_single)
            else -> getString(R.string.random)
        }
        playbackMode.setOnClickListener {
            mode = (mode + 1) % 3
            playbackMode.text = when (mode) {
                0 -> getString(R.string.cycle_all)
                1 -> getString(R.string.cycle_single)
                else -> getString(R.string.random)
            }
            getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("playback_mode", mode).apply()
            showControls()
        }

        previousButton.setOnClickListener { playPrevious(); showControls() }
        playPauseButton.setOnClickListener { togglePlayPause(); showControls() }
        nextButton.setOnClickListener { playNext(); showControls() }

        videoView.setOnPreparedListener { mp ->
            seekBar.max = if (mp.duration > 0) mp.duration else 0
            seekBar.progress = videoView.currentPosition
            updateTimeDisplay()
            handler.removeCallbacks(progressRunnable)
            handler.postDelayed(progressRunnable, 0)
        }

        videoView.setOnCompletionListener {
            handler.removeCallbacks(progressRunnable)
            when (mode) {
                0 -> playNext()
                1 -> playVideo()
                2 -> {
                    currentPosition = (0 until videoFiles.size).random()
                    playVideo()
                }
            }
        }

        videoView.setOnErrorListener { _, _, _ ->
            handler.removeCallbacks(progressRunnable)
            handleVideoError()
            true
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    videoView.seekTo(progress)
                    updateTimeDisplay()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupFocus() {
        val buttons = listOf(playbackMode, previousButton, playPauseButton, nextButton)
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
        seekBar.setOnFocusChangeListener { _, hasFocus ->
            val params = seekBar.layoutParams
            if (hasFocus) {
                params.height = (params.height * 1.5).toInt()
                seekBar.layoutParams = params
            } else {
                params.height = (params.height / 1.5).toInt()
                seekBar.layoutParams = params
            }
        }
    }

    private fun setupTouch() {
        videoView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                showControls()
            }
            true
        }
    }

    private fun playVideo(progress: Int = 0) {
        if (videoFiles.isNotEmpty() && currentPosition in videoFiles.indices) {
            val path = videoFiles[currentPosition]
            val file = File(path)
            if (file.exists()) {
                fileName.text = file.name
                videoView.setVideoPath(path)
            } else {
                // 尝试将路径作为URI处理（兼容SAF）
                videoView.setVideoURI(android.net.Uri.parse(path))
                fileName.text = path.substringAfterLast("/")
            }
            videoView.start()
            if (progress > 0) videoView.seekTo(progress) // 恢复进度
            playPauseButton.text = getString(R.string.pause)
            handler.removeCallbacks(progressRunnable)
            handler.postDelayed(progressRunnable, 0)
            showControls()
            hideControlsAfterDelay() // 确保每次播放都触发隐藏
        } else {
            finish()
        }
    }

    private fun handleVideoError() {
        val currentFile = File(videoFiles[currentPosition]).name
        val nextPosition = (currentPosition + 1) % videoFiles.size
        val nextFile = File(videoFiles[nextPosition]).name
        var countdown = 3
        val toast = Toast.makeText(this, "", Toast.LENGTH_SHORT)
        handler.removeCallbacksAndMessages(null)
        handler.post(object : Runnable {
            override fun run() {
                if (countdown > 0) {
                    toast.setText("无法播放 $currentFile，即将播放 $nextFile ($countdown)")
                    toast.show()
                    countdown--
                    handler.postDelayed(this, 1000)
                } else {
                    currentPosition = nextPosition
                    playVideo()
                }
            }
        })
    }

    private fun playNext() {
        if (videoFiles.isNotEmpty()) {
            currentPosition = (currentPosition + 1) % videoFiles.size
            playVideo()
        }
    }

    private fun playPrevious() {
        if (videoFiles.isNotEmpty()) {
            currentPosition = if (currentPosition - 1 < 0) videoFiles.size - 1 else currentPosition - 1
            playVideo()
        }
    }

    private fun updateTimeDisplay() {
        val current = videoView.currentPosition / 1000
        val total = videoView.duration / 1000
        currentTime.text = String.format("%02d:%02d", current / 60, current % 60)
        totalTime.text = if (total > 0) String.format("%02d:%02d", total / 60, total % 60) else "00:00"
    }

    private fun togglePlayPause() {
        if (videoView.isPlaying) {
            videoView.pause()
            playPauseButton.text = getString(R.string.play)
            controlLayout.visibility = View.VISIBLE
            handler.removeCallbacks(progressRunnable)
        } else {
            videoView.start()
            playPauseButton.text = getString(R.string.pause)
            seekBar.progress = videoView.currentPosition
            updateTimeDisplay()
            handler.removeCallbacks(progressRunnable)
            handler.postDelayed(progressRunnable, 0)
            hideControlsAfterDelay()
        }
    }

    private fun showControls() {
        controlLayout.visibility = View.VISIBLE
    }

    private fun hideControlsAfterDelay() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, 5000)
    }

    private val hideControlsRunnable = Runnable {
        if (videoView.isPlaying) {
            controlLayout.visibility = View.GONE
        }
    }

    private fun savePlaybackState(progress: Int) {
        getSharedPreferences("playback_state", MODE_PRIVATE).edit()
            .putInt("last_video_index", currentPosition)
            .putInt("last_progress", progress)
            .apply()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                togglePlayPause()
                playPauseButton.requestFocus()
                showControls()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                seekBar.requestFocus()
                showControls()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                playPauseButton.requestFocus()
                showControls()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                showControls()
                if (seekBar.isFocused) {
                    val current = videoView.currentPosition
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            val newPosition = (current - 10000).coerceAtLeast(0)
                            videoView.seekTo(newPosition)
                            seekBar.progress = newPosition
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            val newPosition = (current + 10000).coerceAtMost(seekBar.max)
                            videoView.seekTo(newPosition)
                            seekBar.progress = newPosition
                        }
                    }
                    updateTimeDisplay()
                } else if (controlLayout.visibility == View.VISIBLE) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (playPauseButton.isFocused) previousButton.requestFocus()
                            else if (previousButton.isFocused) playbackMode.requestFocus()
                            else if (nextButton.isFocused) playPauseButton.requestFocus()
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (playbackMode.isFocused) previousButton.requestFocus()
                            else if (previousButton.isFocused) playPauseButton.requestFocus()
                            else if (playPauseButton.isFocused) nextButton.requestFocus()
                        }
                    }
                } else {
                    seekBar.requestFocus()
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        videoView.stopPlayback()
    }
}