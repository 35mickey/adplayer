package com.tutu.adplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import java.io.File

class PathSelectionActivity : AppCompatActivity() {

    private lateinit var fileList: ListView
    private lateinit var confirmButton: Button
    private lateinit var deleteButton: Button
    private val items = mutableListOf<String>()
    private var currentDir: File? = null
    private val selectedFiles = mutableListOf<String>()
    private val TAG = "PathSelectionActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_path_selection)

        fileList = findViewById(R.id.file_list)
        confirmButton = findViewById(R.id.confirm_button)
        deleteButton = findViewById(R.id.delete_button)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        } else {
            loadStorageDevices()
        }

        fileList.setOnItemClickListener { _, _, position, _ ->
            val selectedItem = items[position]
            if (selectedItem == "..") {
                currentDir?.parentFile?.let { parent ->
                    currentDir = parent
                    loadDirectory(parent)
                } ?: run {
                    loadStorageDevices()
                }
            } else if (selectedItem.startsWith("/")) {
                val file = File(selectedItem.substringBeforeLast(" "))
                if (file.isDirectory) {
                    currentDir = file
                    loadDirectory(file)
                }
            }
        }

        confirmButton.setOnClickListener {
            currentDir?.let { dir ->
                selectedFiles.clear()
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && file.extension.lowercase() == "mp4") {
                        selectedFiles.add(file.absolutePath)
                    }
                }
                val intent = Intent()
                intent.putStringArrayListExtra("video_files", ArrayList(selectedFiles))
                setResult(RESULT_OK, intent)
                finish()
            }
        }

        deleteButton.setOnClickListener {
            val checkedPositions = fileList.checkedItemPositions
            val filesToDelete = mutableListOf<File>()
            for (i in 0 until checkedPositions.size()) {
                if (checkedPositions.valueAt(i)) {
                    val item = items[checkedPositions.keyAt(i)]
                    if (item != ".." && item.contains(".mp4")) {
                        val file = File(item.substringBeforeLast(" "))
                        filesToDelete.add(file)
                    }
                }
            }
            filesToDelete.forEach {
                val deleted = it.delete()
                Log.d(TAG, "Deleting ${it.absolutePath}: $deleted")
            }
            loadDirectory(currentDir ?: return@setOnClickListener)
            fileList.clearChoices()
            fileList.requestLayout()
        }

        setupButtonFocus()
    }

    private fun setupButtonFocus() {
        val buttons = listOf(confirmButton, deleteButton)
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadStorageDevices()
        } else {
            Log.e(TAG, "Permissions not granted")
            finish()
        }
    }

    private fun loadStorageDevices() {
        items.clear()
        val externalStorage = Environment.getExternalStorageDirectory()
        items.add(externalStorage.absolutePath)
        currentDir = externalStorage
        updateFileList()
    }

    private fun loadDirectory(dir: File) {
        items.clear()
        if (dir.parentFile != null && dir.absolutePath != Environment.getExternalStorageDirectory().absolutePath) {
            items.add("..")
        }
        dir.listFiles()?.forEach { file ->
            val size = if (file.isFile) " ${getString(R.string.file_size)} ${(file.length() / 1024)} KB" else ""
            items.add(file.absolutePath + size)
        }
        updateFileList()
    }

    private fun updateFileList() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, items)
        fileList.adapter = adapter
    }
}