package com.tutu.adplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
    private val displayItems = mutableListOf<String>()
    private var currentDir: File? = null
    private val selectedFiles = mutableListOf<String>()
    private val STORAGE_REQUEST_CODE = 1
    private val SAF_REQUEST_CODE = 2
    private var currentTreeUri: android.net.Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_path_selection)

        fileList = findViewById(R.id.file_list)
        confirmButton = findViewById(R.id.confirm_button)
        deleteButton = findViewById(R.id.delete_button)

        // 根据 Android 版本选择加载方式
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用 SAF，启动目录选择
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), SAF_REQUEST_CODE)
            Toast.makeText(this, "请在弹出的窗口中选择存储目录（如 U 盘）", Toast.LENGTH_LONG).show()
            confirmButton.text = "选择其他目录" // 备用按钮，用于重新选择
            confirmButton.setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), SAF_REQUEST_CODE)
            }
        } else {
            // Android 9 及以下，检查权限并加载存储设备
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_REQUEST_CODE
                )
            } else {
                loadStorageDevices()
            }
            confirmButton.text = getString(R.string.confirm)
        }

        // 文件列表点击事件
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
                    } ?: loadStorageDevices()
                } else {
                    val file = File(selectedItem)
                    if (file.isDirectory) {
                        currentDir = file
                        loadDirectory(file)
                    }
                }
            }
        }

        // 确认按钮点击事件（Android 9 及以下）
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            confirmButton.setOnClickListener {
                currentDir?.let { dir ->
                    selectedFiles.clear()
                    dir.listFiles()?.forEach { file ->
                        if (file.isFile && file.extension.lowercase() == "mp4") {
                            selectedFiles.add(file.absolutePath)
                        }
                    }
                }
                if (selectedFiles.isNotEmpty()) {
                    val intent = Intent()
                    intent.putStringArrayListExtra("video_files", ArrayList(selectedFiles))
                    setResult(RESULT_OK, intent)
                    finish()
                } else {
                    Toast.makeText(this, "未选择任何 MP4 文件", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 删除按钮点击事件
        deleteButton.setOnClickListener {
            val checkedPositions = fileList.checkedItemPositions
            if (checkedPositions.size() > 0) {
                AlertDialog.Builder(this)
                    .setTitle("确认删除")
                    .setMessage("确定要删除选中的文件吗？此操作不可撤销。")
                    .setPositiveButton("删除") { _, _ ->
                        val deletedFiles = mutableListOf<String>()
                        val failedFiles = mutableListOf<String>()
                        for (i in 0 until checkedPositions.size()) {
                            if (checkedPositions.valueAt(i)) {
                                val item = items[checkedPositions.keyAt(i)]
                                if (item != ".." && item.contains(".mp4", ignoreCase = true)) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        val docFile = DocumentFile.fromSingleUri(this, android.net.Uri.parse(item))
                                        if (docFile?.isFile == true && docFile.delete()) {
                                            deletedFiles.add(docFile?.name ?: "未知文件")
                                        } else {
                                            failedFiles.add(docFile?.name ?: "未知文件")
                                        }
                                    } else {
                                        val file = File(item)
                                        if (file.isFile && file.delete()) {
                                            deletedFiles.add(file.name)
                                        } else {
                                            failedFiles.add(file.name)
                                        }
                                    }
                                }
                            }
                        }
                        // 刷新列表
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            currentTreeUri?.let { loadDirectoryFromUri(it) }
                        } else {
                            loadDirectory(currentDir ?: return@setPositiveButton)
                        }
                        fileList.clearChoices()
                        fileList.requestLayout()
                        if (deletedFiles.isNotEmpty()) {
                            Toast.makeText(this, "删除 ${deletedFiles.joinToString(", ")} 成功", Toast.LENGTH_SHORT).show()
                        }
                        if (failedFiles.isNotEmpty()) {
                            Toast.makeText(this, "删除 ${failedFiles.joinToString(", ")} 失败，请检查权限或文件状态", Toast.LENGTH_LONG).show()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                AlertDialog.Builder(this)
                                    .setMessage("可能缺少写权限，是否重新选择目录？")
                                    .setPositiveButton("是") { _, _ ->
                                        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), SAF_REQUEST_CODE)
                                    }
                                    .setNegativeButton("否", null)
                                    .show()
                            }
                        }
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
                    ViewCompat.animate(button).scaleX(1.2f).scaleY(1.2f).setDuration(200).start()
                } else {
                    ViewCompat.animate(button).scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_REQUEST_CODE && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            loadStorageDevices()
        } else {
            Toast.makeText(this, "需要读写权限才能操作文件", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SAF_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.data?.let { treeUri ->
                contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                currentTreeUri = treeUri
                loadDirectoryFromUri(treeUri)
                // SAF 模式下，确认按钮用于返回选择的文件
                confirmButton.setOnClickListener {
                    selectedFiles.clear()
                    items.forEach { uri ->
                        val docFile = DocumentFile.fromSingleUri(this, android.net.Uri.parse(uri))
                        if (docFile?.isFile == true && docFile.name?.endsWith(".mp4", true) == true) {
                            selectedFiles.add(uri)
                        }
                    }
                    if (selectedFiles.isNotEmpty()) {
                        val intent = Intent()
                        intent.putStringArrayListExtra("video_files", ArrayList(selectedFiles))
                        setResult(RESULT_OK, intent)
                        finish()
                    } else {
                        Toast.makeText(this, "未选择任何 MP4 文件", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun loadStorageDevices() {
        items.clear()
        displayItems.clear()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // SAF 模式下，仅显示提示
            displayItems.add("请点击上方按钮选择存储目录")
        } else {
            // 添加内部存储
            val internalStorage = Environment.getExternalStorageDirectory()
            items.add(internalStorage.absolutePath)
            displayItems.add("内部存储: ${internalStorage.absolutePath}")

            // 检测可能的外部存储挂载点（包括 U 盘）
            val possibleMountPoints = listOf(
                "/storage", "/mnt", "/mnt/media_rw", "/mnt/usb", "/storage/usb"
            )
            for (path in possibleMountPoints) {
                val dir = File(path)
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { subDir ->
                        if (subDir.isDirectory && subDir.canRead() && subDir.absolutePath != internalStorage.absolutePath) {
                            items.add(subDir.absolutePath)
                            displayItems.add("外部存储: ${subDir.absolutePath}")
                        }
                    }
                }
            }
            if (items.isEmpty()) {
                displayItems.add("未检测到存储设备")
            }
            currentDir = internalStorage
        }
        updateFileList()
    }

    private fun loadDirectory(dir: File) {
        items.clear()
        displayItems.clear()
        if (dir.parentFile != null && dir.absolutePath != Environment.getExternalStorageDirectory().absolutePath) {
            items.add("..")
            displayItems.add("..")
        }
        dir.listFiles()?.forEach { file ->
            items.add(file.absolutePath)
            displayItems.add(
                if (file.isFile) "${file.absolutePath} ${getString(R.string.file_size)} ${(file.length() / 1024)} KB"
                else file.absolutePath
            )
        }
        updateFileList()
    }

    private fun loadDirectoryFromUri(treeUri: android.net.Uri) {
        items.clear()
        displayItems.clear()
        val documentFile = DocumentFile.fromTreeUri(this, treeUri)
        documentFile?.listFiles()?.forEach { file ->
            items.add(file.uri.toString())
            displayItems.add(
                if (file.isFile) "${file.name} ${getString(R.string.file_size)} ${(file.length() / 1024)} KB"
                else file.name ?: file.uri.toString()
            )
        }
        updateFileList()
    }

    private fun updateFileList() {
        fileList.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, displayItems)
    }
}