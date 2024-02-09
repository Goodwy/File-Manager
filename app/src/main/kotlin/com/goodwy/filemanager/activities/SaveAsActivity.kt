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

        if (intent.action == Intent.ACTION_SEND && intent.extras?.containsKey(Intent.EXTRA_STREAM) == true) {
            FilePickerDialog(this, pickFile = false, showHidden = config.shouldShowHidden(), showFAB = true, showFavoritesButton = true) {
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

                            val source = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                            val mimeType = source!!.toString().getMimeType()
                            val inputStream = contentResolver.openInputStream(source)
                            val filename = source.toString().getFilenameFromPath()

                            val destinationPath = "$destination/$filename"
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
        setupToolbar(binding.activitySaveAsToolbar, NavigationIcon.Arrow)
    }
}
