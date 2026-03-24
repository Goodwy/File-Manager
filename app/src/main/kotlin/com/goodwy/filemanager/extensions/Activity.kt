package com.goodwy.filemanager.extensions

import android.app.Activity
import android.content.Intent
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.FileProvider
import com.goodwy.commons.activities.BaseSimpleActivity
import com.goodwy.commons.dialogs.NewAppDialog
import com.goodwy.commons.extensions.getFilenameFromPath
import com.goodwy.commons.extensions.getMimeTypeFromUri
import com.goodwy.commons.extensions.getParentPath
import com.goodwy.commons.extensions.isNewApp
import com.goodwy.commons.extensions.launchActivityIntent
import com.goodwy.commons.extensions.openPathIntent
import com.goodwy.commons.extensions.renameFile
import com.goodwy.commons.extensions.setAsIntent
import com.goodwy.commons.extensions.sharePathsIntent
import com.goodwy.commons.helpers.LICENSE_AUTOFITTEXTVIEW
import com.goodwy.commons.helpers.LICENSE_GESTURE_VIEWS
import com.goodwy.commons.helpers.LICENSE_GLIDE
import com.goodwy.commons.helpers.LICENSE_PATTERN
import com.goodwy.commons.helpers.LICENSE_REPRINT
import com.goodwy.commons.helpers.LICENSE_ZIP4J
import com.goodwy.commons.models.FAQItem
import com.goodwy.filemanager.BuildConfig
import com.goodwy.filemanager.R
import com.goodwy.filemanager.activities.SimpleActivity
import com.goodwy.filemanager.helpers.OPEN_AS_AUDIO
import com.goodwy.filemanager.helpers.OPEN_AS_DEFAULT
import com.goodwy.filemanager.helpers.OPEN_AS_IMAGE
import com.goodwy.filemanager.helpers.OPEN_AS_TEXT
import com.goodwy.filemanager.helpers.OPEN_AS_VIDEO
import java.io.File

fun Activity.sharePaths(paths: ArrayList<String>) {
    sharePathsIntent(paths, BuildConfig.APPLICATION_ID)
}

fun Activity.tryOpenPathIntent(path: String, forceChooser: Boolean, openAsType: Int = OPEN_AS_DEFAULT, finishActivity: Boolean = false) {
    if (!forceChooser && path.endsWith(".apk", true)) {
        val uri = FileProvider.getUriForFile(
            this, "${BuildConfig.APPLICATION_ID}.provider", File(path)
        )

        Intent().apply {
            action = Intent.ACTION_VIEW
            setDataAndType(uri, getMimeTypeFromUri(uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            launchActivityIntent(this)
        }
    } else {
        openPath(path, forceChooser, openAsType)

        if (finishActivity) {
            finish()
        }
    }
}

fun Activity.openPath(path: String, forceChooser: Boolean, openAsType: Int = OPEN_AS_DEFAULT) {
    openPathIntent(path, forceChooser, BuildConfig.APPLICATION_ID, getMimeType(openAsType))
}

private fun getMimeType(type: Int) = when (type) {
    OPEN_AS_DEFAULT -> ""
    OPEN_AS_TEXT -> "text/*"
    OPEN_AS_IMAGE -> "image/*"
    OPEN_AS_AUDIO -> "audio/*"
    OPEN_AS_VIDEO -> "video/*"
    else -> "*/*"
}

fun Activity.setAs(path: String) {
    setAsIntent(path, BuildConfig.APPLICATION_ID)
}

fun BaseSimpleActivity.toggleItemVisibility(oldPath: String, hide: Boolean, callback: ((newPath: String) -> Unit)? = null) {
    val path = oldPath.getParentPath()
    var filename = oldPath.getFilenameFromPath()
    if ((hide && filename.startsWith('.')) || (!hide && !filename.startsWith('.'))) {
        callback?.invoke(oldPath)
        return
    }

    filename = if (hide) {
        ".${filename.trimStart('.')}"
    } else {
        filename.substring(1, filename.length)
    }

    val newPath = "$path/$filename"
    if (oldPath != newPath) {
        renameFile(oldPath, newPath, false) { success, useAndroid30Way ->
            callback?.invoke(newPath)
        }
    }
}

fun SimpleActivity.launchAbout() {
    val licenses = LICENSE_GLIDE or LICENSE_PATTERN or LICENSE_REPRINT or LICENSE_GESTURE_VIEWS or LICENSE_AUTOFITTEXTVIEW or LICENSE_ZIP4J

    val faqItems = arrayListOf(
        FAQItem(R.string.faq_3_title_commons, R.string.faq_3_text_commons),
        FAQItem(R.string.faq_9_title_commons, R.string.faq_9_text_commons)
    )

    if (!resources.getBoolean(R.bool.hide_google_relations)) {
        faqItems.add(FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons_g))
        faqItems.add(FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons_g))
        faqItems.add(FAQItem(R.string.faq_7_title_commons, R.string.faq_7_text_commons))
        faqItems.add(FAQItem(R.string.faq_10_title_commons, R.string.faq_10_text_commons))
    }

    val productIdX1 = BuildConfig.PRODUCT_ID_X1
    val productIdX2 = BuildConfig.PRODUCT_ID_X2
    val productIdX3 = BuildConfig.PRODUCT_ID_X3
    val subscriptionIdX1 = BuildConfig.SUBSCRIPTION_ID_X1
    val subscriptionIdX2 = BuildConfig.SUBSCRIPTION_ID_X2
    val subscriptionIdX3 = BuildConfig.SUBSCRIPTION_ID_X3
    val subscriptionYearIdX1 = BuildConfig.SUBSCRIPTION_YEAR_ID_X1
    val subscriptionYearIdX2 = BuildConfig.SUBSCRIPTION_YEAR_ID_X2
    val subscriptionYearIdX3 = BuildConfig.SUBSCRIPTION_YEAR_ID_X3

    val flavorName = BuildConfig.FLAVOR
    val storeDisplayName = when (flavorName) {
        "gplay" -> "Google Play"
        "foss" -> "FOSS"
        "rustore" -> "RuStore"
        else -> "Huawei"
    }
    val versionName = BuildConfig.VERSION_NAME
    val fullVersionText = "$versionName ($storeDisplayName)"

    startAboutActivity(
        appNameId = R.string.app_name_g,
        licenseMask = licenses,
        versionName = fullVersionText,
        flavorName = BuildConfig.FLAVOR,
        faqItems = faqItems,
        showFAQBeforeMail = true,
        productIdList = arrayListOf(productIdX1, productIdX2, productIdX3),
        productIdListRu = arrayListOf(productIdX1, productIdX2, productIdX3),
        subscriptionIdList = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
        subscriptionIdListRu = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
        subscriptionYearIdList = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
        subscriptionYearIdListRu = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
    )
}

fun Activity.newAppRecommendation() {
    if (resources.getBoolean(com.goodwy.commons.R.bool.is_foss)) {
        if (!isNewApp()) {
            if ((0..config.newAppRecommendationDialogCount).random() == 2) {
                val packageName = "reganamelif.ywdoog.ved".reversed()
                NewAppDialog(
                    activity = this,
                    packageName = packageName,
                    title = getString(com.goodwy.strings.R.string.notification_of_new_application),
                    text = "Alright Files",
                    drawable = AppCompatResources.getDrawable(this, com.goodwy.commons.R.drawable.ic_files_new),
                    showSubtitle = true
                ) {
                }
            }
        }
    }
}
