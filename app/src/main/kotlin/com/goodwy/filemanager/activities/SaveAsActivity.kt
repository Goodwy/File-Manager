package com.goodwy.filemanager.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.goodwy.commons.dialogs.FilePickerDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.NavigationIcon
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.filemanager.R
import com.goodwy.filemanager.databinding.ActivitySaveAsBinding
import com.goodwy.filemanager.extensions.config
import java.io.File

class SaveAsActivity : SimpleActivity() {
    private val binding by viewBinding(ActivitySaveAsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        useChangeAutoTheme = false
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        tryInitFileManager()
    }

    private fun tryInitFileManager() {
        handleStoragePermission { granted ->
            if (granted) {
                saveAsDialog()
            } else {
                toast(R.string.no_storage_permissions)
                finish()
            }
        }
    }

    private fun saveAsDialog() {
        if (intent.action == Intent.ACTION_SEND && intent.extras?.containsKey(Intent.EXTRA_STREAM) == true) {
            FilePickerDialog(this, pickFile = false, showHidden = config.shouldShowHidden(), showFAB = true, showFavoritesButton = true, useAccentColor = true) {
                val destination = it
                handleSAFDialog(destination) {
                    toast(R.string.saving)
                    ensureBackgroundThread {
                        try {
                            if (!getDoesFilePathExist(destination)) {
                                if (needsStupidWritePermissions(destination)) {
                                    val document = getDocumentFile(destination)
                                    document!!.createDirectory(destination.getFilenameFromPath())
                                } else {
                                    File(destination).mkdirs()
                                }
                            }

                            val source = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
                            val originalFilename = getFilenameFromContentUri(source)
                                ?: source.toString().getFilenameFromPath()
                            val filename = sanitizeFilename(originalFilename)
                            val mimeType = contentResolver.getType(source)
                                ?: intent.type?.takeIf { it != "*/*" }
                                ?: filename.getMimeType()
                            val inputStream = contentResolver.openInputStream(source)

                            val destinationPath = getAvailablePath("$destination/$filename")
                            val outputStream = getFileOutputStreamSync(destinationPath, mimeType, null)!!
                            inputStream!!.copyTo(outputStream)
                            rescanPaths(arrayListOf(destinationPath))
                            toast(R.string.file_saved)
                            finish()
                        } catch (e: Exception) {
                            showErrorToast(e)
                            finish()
                        }
                    }
                }
            }
        } else {
            toast(R.string.unknown_error_occurred)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.activitySaveAsAppbar, NavigationIcon.Arrow)
    }

    private fun sanitizeFilename(filename: String): String {
        return filename.replace("[/\\\\<>:\"|?*\u0000-\u001F]".toRegex(), "_")
            .takeIf { it.isNotBlank() } ?: "unnamed_file"
    }

    private fun getAvailablePath(destinationPath: String): String {
        if (!getDoesFilePathExist(destinationPath)) {
            return destinationPath
        }

        val file = File(destinationPath)
        return findAvailableName(file)
    }

    private fun findAvailableName(file: File): String {
        val parent = file.parent ?: return file.absolutePath
        val name = file.nameWithoutExtension
        val ext = if (file.extension.isNotEmpty()) ".${file.extension}" else ""

        var index = 1
        var newPath: String

        do {
            newPath = "$parent/${name}_$index$ext"
            index++
        } while (getDoesFilePathExist(newPath))

        return newPath
    }

}
