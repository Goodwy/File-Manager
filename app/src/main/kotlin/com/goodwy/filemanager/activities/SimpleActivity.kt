package com.goodwy.filemanager.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Environment
import android.provider.Settings
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.dialogs.ConfirmationAdvancedDialog
import com.goodwy.commons.extensions.hasPermission
import com.goodwy.commons.extensions.showErrorToast
import com.goodwy.commons.helpers.PERMISSION_WRITE_STORAGE
import com.goodwy.commons.helpers.isRPlus
import com.goodwy.filemanager.R
import androidx.core.net.toUri

open class SimpleActivity : BaseSimpleActivity() {
    override fun getAppIconIDs() = arrayListOf(
        R.mipmap.ic_launcher,
        R.mipmap.ic_launcher_one,
        R.mipmap.ic_launcher_two,
        R.mipmap.ic_launcher_three,
        R.mipmap.ic_launcher_four,
        R.mipmap.ic_launcher_five,
        R.mipmap.ic_launcher_six,
        R.mipmap.ic_launcher_seven,
        R.mipmap.ic_launcher_eight,
        R.mipmap.ic_launcher_nine,
        R.mipmap.ic_launcher_ten,
        R.mipmap.ic_launcher_eleven
    )

    companion object {
        private const val MANAGE_STORAGE_RC = 201
    }

    override fun getAppLauncherName() = getString(R.string.app_launcher_name_g)

    override fun getRepositoryName() = "File-Manager"

    @SuppressLint("NewApi")
    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        isAskingPermissions = false
        if (requestCode == MANAGE_STORAGE_RC && isRPlus()) {
            actionOnPermission?.invoke(Environment.isExternalStorageManager())
        }
    }

    @SuppressLint("NewApi")
    fun hasStoragePermission(): Boolean {
        return if (isRPlus()) {
            Environment.isExternalStorageManager()
        } else {
            hasPermission(PERMISSION_WRITE_STORAGE)
        }
    }

    @SuppressLint("InlinedApi")
    fun handleStoragePermission(callback: (granted: Boolean) -> Unit) {
        actionOnPermission = null
        if (hasStoragePermission()) {
            callback(true)
        } else {
            if (isRPlus()) {
                ConfirmationAdvancedDialog(this, "", R.string.access_storage_prompt, R.string.ok, 0, false) { success ->
                    if (success) {
                        isAskingPermissions = true
                        actionOnPermission = callback
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.addCategory("android.intent.category.DEFAULT")
                            intent.data = "package:$packageName".toUri()
                            startActivityForResult(intent, MANAGE_STORAGE_RC)
                        } catch (e: android.content.ActivityNotFoundException) {
                            showErrorToast(e)
                            val intent = Intent()
                            intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                            startActivityForResult(intent, MANAGE_STORAGE_RC)
                        } catch (e: SecurityException) {
                            showErrorToast(e)
                            finish()
                        }
                    } else {
                        finish()
                    }
                }
            } else {
                handlePermission(PERMISSION_WRITE_STORAGE, callback)
            }
        }
    }
}
