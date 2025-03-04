package com.goodwy.filemanager.extensions

import android.content.Context
import android.os.storage.StorageManager
import androidx.annotation.DimenRes
import com.goodwy.commons.extensions.isPathOnOTG
import com.goodwy.commons.extensions.isPathOnSD
import com.goodwy.commons.helpers.isNougatPlus
import com.goodwy.commons.helpers.isQPlus
import com.goodwy.filemanager.helpers.Config
import com.goodwy.filemanager.helpers.PRIMARY_VOLUME_NAME
import com.goodwy.filemanager.helpers.PRIMARY_VOLUME_NAME_OLD
import java.util.Locale

val Context.config: Config get() = Config.newInstance(applicationContext)

fun Context.isPathOnRoot(path: String) = !(path.startsWith(config.internalStoragePath) || isPathOnOTG(path) || (isPathOnSD(path)))

fun Context.getAllVolumeNames(): List<String> {
    val primaryVolumeName = if (isQPlus()) PRIMARY_VOLUME_NAME else PRIMARY_VOLUME_NAME_OLD
    val volumeNames = mutableListOf(primaryVolumeName)
    if (isNougatPlus()) {
        val storageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
        getExternalFilesDirs(null)
            .mapNotNull { storageManager.getStorageVolume(it) }
            .filterNot { it.isPrimary }
            .mapNotNull { it.uuid?.lowercase(Locale.US) }
            .forEach {
                volumeNames.add(it)
            }
    }
    return volumeNames
}

fun Context.pixels(@DimenRes dimen: Int): Float = resources.getDimensionPixelOffset(dimen).toFloat()
