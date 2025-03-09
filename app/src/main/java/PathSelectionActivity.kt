package com.tutu.adplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.documentfile.provider.DocumentFile
import java.io.File

class PathSelectionActivity : AppCompatActivity() {

    private lateinit var fileList: ListView
    private lateinit var confirmButton: Button
    private lateinit var deleteButton: Button
    private val items = mutableListOf<String>()
    private var currentDir: File? = null
    private val selectedFiles = mutableListOf<String>()
    private val TAG = "PathSelectionActivity"
    private val STORAGE_REQUEST_CODE = 1
    private val SAF_REQUEST_CODE = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_path_selection)

        fileList = findViewById(R.id.file_list)
        confirmButton = findViewById(R.id.confirm_button)
        deleteButton = findViewById(R.id.delete_button)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // API 29+
            // 使用 SAF 选择目录
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, SAF_REQUEST_CODE)
        } else {
            // 检查传统存储权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_REQUEST_CODE)
            } else {
                loadStorageDevices()
            }
        }

        fileList.setOnItemClickListener { _, _, position, _ ->
            val selectedItem = items[position]
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val docFile = DocumentFile.fromTreeUri(this, android.net.Uri.parse(selectedItem))
                if (docFile?.isDirectory == true) {
                    loadDirectoryFromUri(docFile.uri)
                }
            } else {
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
        }

        confirmButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                selectedFiles.clear()
                items.forEach { uri ->
                    val docFile = DocumentFile.fromSingleUri(this, android.net.Uri.parse(uri))
                    if (docFile?.isFile == true && docFile.name?.endsWith(".mp4", true) == true) {
                        selectedFiles.add(uri)
                    }
                }
            } else {
                currentDir?.let { dir ->
                    selectedFiles.clear()
                    dir.listFiles()?.forEach { file ->
                        if (file.isFile && file.extension.lowercase() == "mp4") {
                            selectedFiles.add(file.absolutePath)
                        }
                    }
                }
            }
            if (selectedFiles.isNotEmpty()) {
                val intent = Intent()
                intent.putStringArrayListExtra("video_files", ArrayList(selectedFiles))
                setResult(RESULT_OK, intent)
                finish()
            } else {
                Toast.makeText(this, "未选择任何MP4文件", Toast.LENGTH_SHORT).show()
            }
        }

        deleteButton.setOnClickListener {
            val checkedPositions = fileList.checkedItemPositions
            if (checkedPositions.size() > 0) {
                AlertDialog.Builder(this)
                    .setTitle("确认删除")
                    .setMessage("确定要删除选中的文件吗？此操作不可撤销。")
                    .setPositiveButton("删除") { _, _ ->
                        val filesToDelete = mutableListOf<File>()
                        for (i in 0 until checkedPositions.size()) {
                            if (checkedPositions.valueAt(i)) {
                                val item = items[checkedPositions.keyAt(i)]
                                if (item != ".." && item.contains(".mp4")) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        val docFile = DocumentFile.fromSingleUri(this, android.net.Uri.parse(item))
                                        docFile?.delete()
                                    } else {
                                        val file = File(item.substringBeforeLast(" "))
                                        filesToDelete.add(file)
                                    }
                                }
                            }
                        }
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            filesToDelete.forEach {
                                val deleted = it.delete()
                                Log.d(TAG, "Deleting ${it.absolutePath}: $deleted")
                            }
                            loadDirectory(currentDir ?: return@setPositiveButton)
                        } else {
                            loadDirectoryFromUri(android.net.Uri.parse(items.firstOrNull() ?: return@setPositiveButton))
                        }
                        fileList.clearChoices()
                        fileList.requestLayout()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                Toast.makeText(this, "请先选择要删除的文件", Toast.LENGTH_SHORT).show()
            }
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
        if (requestCode == STORAGE_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadStorageDevices()
        } else {
            Log.e(TAG, "Permissions not granted")
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SAF_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.data?.let { treeUri ->
                contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                loadDirectoryFromUri(treeUri)
            }
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

    private fun loadDirectoryFromUri(treeUri: android.net.Uri) {
        items.clear()
        val documentFile = DocumentFile.fromTreeUri(this, treeUri)
        documentFile?.listFiles()?.forEach { file ->
            if (file.isFile && file.name?.endsWith(".mp4", true) == true) {
                items.add(file.uri.toString())
            } else if (file.isDirectory) {
                items.add(file.uri.toString())
            }
        }
        updateFileList()
    }

    private fun updateFileList() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, items)
        fileList.adapter = adapter
    }
}