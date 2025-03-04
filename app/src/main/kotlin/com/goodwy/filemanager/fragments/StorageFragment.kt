package com.goodwy.filemanager.fragments

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AppOpsManager
import android.app.usage.StorageStats
import android.app.usage.StorageStatsManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.Settings
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.FileDirItem
import com.goodwy.commons.views.MyGridLayoutManager
import com.goodwy.filemanager.R
import com.goodwy.filemanager.activities.MainActivity
import com.goodwy.filemanager.activities.MimeTypesActivity
import com.goodwy.filemanager.activities.SimpleActivity
import com.goodwy.filemanager.adapters.ItemsAdapter
import com.goodwy.filemanager.databinding.ItemStorageVolumeBinding
import com.goodwy.filemanager.databinding.StorageFragmentBinding
import com.goodwy.filemanager.extensions.config
import com.goodwy.filemanager.extensions.formatSizeThousand
import com.goodwy.filemanager.extensions.getAllVolumeNames
import com.goodwy.filemanager.helpers.*
import com.goodwy.filemanager.interfaces.ItemOperationsListener
import com.goodwy.filemanager.models.ListItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*


class StorageFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment<MyViewPagerFragment.StorageInnerBinding>(context, attributeSet),
    ItemOperationsListener {
    //private val SIZE_DIVIDER = 100000
    private var allDeviceListItems = ArrayList<ListItem>()
    private var lastSearchedText = ""
    private var appsSizeLong: Long = 0
    private var totalStorageSpaceLong: Long = 0

    private lateinit var binding: StorageFragmentBinding
    private val volumes = mutableMapOf<String, ItemStorageVolumeBinding>()

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = StorageFragmentBinding.bind(this)
        innerBinding = StorageInnerBinding(binding)
    }

    @SuppressLint("StringFormatInvalid")
    override fun setupFragment(activity: SimpleActivity) {
        if (this.activity == null) {
            this.activity = activity
        }

        val volumeNames = activity.getAllVolumeNames()
        volumeNames.forEach { volumeName ->
            val volumeBinding = ItemStorageVolumeBinding.inflate(activity.layoutInflater)
            volumes[volumeName] = volumeBinding
            volumeBinding.apply {
                binding.storageSwipeRefresh.setOnRefreshListener {
                    (activity as? MainActivity)?.openedDirectory()
                    refreshFragment()
                    getSizes(volumeName)
                    if (volumeName == if (isQPlus()) PRIMARY_VOLUME_NAME else PRIMARY_VOLUME_NAME_OLD) getAllAppSize(volumeName)
                    getVolumeStorageStats(context)
                }

                if (volumeName == if (isQPlus()) PRIMARY_VOLUME_NAME else PRIMARY_VOLUME_NAME_OLD) {
                    storageName.setText(R.string.internal)
                    getAllAppSize(volumeName)
                } else {
                    storageName.setText(R.string.sd_card)
                }

                totalSpace.text = String.format(context.getString(R.string.storage_used), "…", "…")
                getSizes(volumeName)

                if (volumeNames.size > 1) {
                    val primaryColor = context.getProperPrimaryColor()
                    if (!context!!.config.getExpandedDetails(volumeName)) root.children.forEach { it.beGone() }
                    freeSpaceCard.beVisible()
                    expandButton.applyColorFilter(primaryColor)
                    expandButton.setImageResource(R.drawable.ic_chevron_down_vector)
                    expandButtonLabel.setTextColor(primaryColor)
                    expandButtonLabel.setText(R.string.more_info)

                    expandButtonHolder.setOnClickListener { _ ->
                        if (imagesHolder.isVisible) {
                            root.children.filterNot { it == freeSpaceCard }.forEach { it.beGone() }
                            expandButton.setImageResource(R.drawable.ic_chevron_down_vector)
                            expandButtonLabel.setText(R.string.more_info)
                            context!!.config.saveExpandedDetails(volumeName, false)
                        } else {
                            root.children.filterNot { it == freeSpaceCard }.forEach {
                                it.beVisible()
                                if (volumeName != if (isQPlus()) PRIMARY_VOLUME_NAME else PRIMARY_VOLUME_NAME_OLD) {
                                    appsHolder.beGone()
                                    appsDivider.beGone()
                                }
                            }
                            expandButton.setImageResource(R.drawable.ic_chevron_up_vector)
                            expandButtonLabel.setText(R.string.hide)
                            context!!.config.saveExpandedDetails(volumeName, true)
                        }
                    }
                    manageStorageChevron.beGone()
                } else {
                    expandButtonHolder.beGone()
                    manageStorageChevron.beVisible()
                }

                manageStorageHolder.setOnClickListener {
                    try {
                        val storageSettingsIntent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                        activity.startActivity(storageSettingsIntent)
                    } catch (e: Exception) {
                        activity.showErrorToast(e)
                    }
                }

                appsHolder.setOnClickListener {
                    try {
                        val storageSettingsIntent = Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS)
                        activity.startActivity(storageSettingsIntent)
                    } catch (e: Exception) {
                        activity.showErrorToast(e)
                    }
                }
                imagesHolder.setOnClickListener { launchMimetypeActivity(IMAGES, volumeName) }
                videosHolder.setOnClickListener { launchMimetypeActivity(VIDEOS, volumeName) }
                audioHolder.setOnClickListener { launchMimetypeActivity(AUDIO, volumeName) }
                documentsHolder.setOnClickListener { launchMimetypeActivity(DOCUMENTS, volumeName) }
                archivesHolder.setOnClickListener { launchMimetypeActivity(ARCHIVES, volumeName) }
                othersHolder.setOnClickListener { launchMimetypeActivity(OTHERS, volumeName) }
            }
            binding.storageVolumesHolder.addView(volumeBinding.root)
        }

        ensureBackgroundThread {
            getVolumeStorageStats(context)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            refreshFragment()
        }, 2000)
    }

    override fun onResume(textColor: Int) {
        context.updateTextColors(binding.root)

        val properPrimaryColor = context.getProperPrimaryColor()

        volumes.entries.forEach { (it, volumeBinding) ->
            getSizes(it)
            volumeBinding.apply {
//                mainStorageUsageProgressbar.setIndicatorColor(properPrimaryColor)
//                mainStorageUsageProgressbar.trackColor = properPrimaryColor.adjustAlpha(LOWER_ALPHA)
//
//                imagesProgressbar.setIndicatorColor(redColor)
//                imagesProgressbar.trackColor = redColor.adjustAlpha(LOWER_ALPHA)
//
//                videosProgressbar.setIndicatorColor(greenColor)
//                videosProgressbar.trackColor = greenColor.adjustAlpha(LOWER_ALPHA)
//
//                audioProgressbar.setIndicatorColor(lightBlueColor)
//                audioProgressbar.trackColor = lightBlueColor.adjustAlpha(LOWER_ALPHA)
//
//                documentsProgressbar.setIndicatorColor(yellowColor)
//                documentsProgressbar.trackColor = yellowColor.adjustAlpha(LOWER_ALPHA)
//
//                archivesProgressbar.setIndicatorColor(tealColor)
//                archivesProgressbar.trackColor = tealColor.adjustAlpha(LOWER_ALPHA)
//
//                othersProgressbar.setIndicatorColor(pinkColor)
//                othersProgressbar.trackColor = pinkColor.adjustAlpha(LOWER_ALPHA)

                expandButton.applyColorFilter(context.getProperPrimaryColor())

                freeSpaceCard.setCardBackgroundColor(context.getBottomNavigationBackgroundColor().adjustAlpha(0.8f))
                arrayOf(
                    manageStorageChevron,
                    appsChevron,
                    imagesChevron,
                    videosChevron,
                    audioChevron,
                    documentsChevron,
                    archivesChevron,
                    othersChevron
                ).forEach {
                    it.setColorFilter(textColor)
                }
                arrayOf(
                    imageDivider,
                    videoDivider,
                    audioDivider,
                    documentDivider,
                    archiveDivider,
                    otherDivider,
                    systemDivider,
                    systemEndDivider
                ).forEach {
                    it.setBackgroundColor(context.getBottomNavigationBackgroundColor().adjustAlpha(0.8f))
                }
            }
        }

        binding.apply {
            searchHolder.setBackgroundColor(context.getProperBackgroundColor())
            progressBar.setIndicatorColor(properPrimaryColor)
            progressBar.trackColor = properPrimaryColor.adjustAlpha(LOWER_ALPHA)
            storageSwipeRefresh.isEnabled = lastSearchedText.isEmpty() && activity?.config?.enablePullToRefresh != false

            searchResultsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    activity?.hideKeyboard()
                }
            })
            storageNestedScrollview.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
                if (scrollY != 0) {
                    (activity as? MainActivity)?.openedDirectory()
                }
            })
        }

        ensureBackgroundThread {
            getVolumeStorageStats(context)
        }
    }

    private fun launchMimetypeActivity(mimetype: String, volumeName: String) {
        Intent(context, MimeTypesActivity::class.java).apply {
            putExtra(SHOW_MIMETYPE, mimetype)
            putExtra(VOLUME_NAME, volumeName)
            context.startActivity(this)
        }
    }

    private fun getSizes(volumeName: String) {
        if (!isOreoPlus()) {
            return
        }

        ensureBackgroundThread {
            val filesSize = getSizesByMimeType(volumeName)
            val fileSizeImages = filesSize[IMAGES]!!
            val fileSizeVideos = filesSize[VIDEOS]!!
            val fileSizeAudios = filesSize[AUDIO]!!
            val fileSizeDocuments = filesSize[DOCUMENTS]!!
            val fileSizeArchives = filesSize[ARCHIVES]!!
            val fileSizeOthers = filesSize[OTHERS]!!

            post {
                volumes[volumeName]!!.apply {
                    imagesSize.text = fileSizeImages.formatSize()
                    //imagesProgressbar.progress = (fileSizeImages / SIZE_DIVIDER).toInt()

                    videosSize.text = fileSizeVideos.formatSize()
                    //videosProgressbar.progress = (fileSizeVideos / SIZE_DIVIDER).toInt()

                    audioSize.text = fileSizeAudios.formatSize()
                    //audioProgressbar.progress = (fileSizeAudios / SIZE_DIVIDER).toInt()

                    documentsSize.text = fileSizeDocuments.formatSize()
                    //documentsProgressbar.progress = (fileSizeDocuments / SIZE_DIVIDER).toInt()

                    archivesSize.text = fileSizeArchives.formatSize()
                    //archivesProgressbar.progress = (fileSizeArchives / SIZE_DIVIDER).toInt()

                    othersSize.text = fileSizeOthers.formatSize()
                    //othersProgressbar.progress = (fileSizeOthers / SIZE_DIVIDER).toInt()
                }
            }
        }
    }

    private fun getSizesByMimeType(volumeName: String): HashMap<String, Long> {
        val uri = MediaStore.Files.getContentUri(volumeName)
        val projection = arrayOf(
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATA
        )

        var imagesSize = 0L
        var videosSize = 0L
        var audioSize = 0L
        var documentsSize = 0L
        var archivesSize = 0L
        var othersSize = 0L
        try {
            context.queryCursor(uri, projection) { cursor ->
                try {
                    val mimeType = cursor.getStringValue(MediaStore.Files.FileColumns.MIME_TYPE)?.lowercase(Locale.getDefault())
                    val size = cursor.getLongValue(MediaStore.Files.FileColumns.SIZE)
                    if (mimeType == null) {
                        if (size > 0 && size != 4096L) {
                            val path = cursor.getStringValue(MediaStore.Files.FileColumns.DATA)
                            if (!context.getIsPathDirectory(path)) {
                                othersSize += size
                            }
                        }
                        return@queryCursor
                    }

                    when (mimeType.substringBefore("/")) {
                        "image" -> imagesSize += size
                        "video" -> videosSize += size
                        "audio" -> audioSize += size
                        "text" -> documentsSize += size
                        else -> {
                            when {
                                extraDocumentMimeTypes.contains(mimeType) -> documentsSize += size
                                extraAudioMimeTypes.contains(mimeType) -> audioSize += size
                                archiveMimeTypes.contains(mimeType) -> archivesSize += size
                                else -> othersSize += size
                            }
                        }
                    }
                } catch (e: Exception) {
                }
            }
        } catch (e: Exception) {
        }

        val mimeTypeSizes = HashMap<String, Long>().apply {
            put(IMAGES, imagesSize)
            put(VIDEOS, videosSize)
            put(AUDIO, audioSize)
            put(DOCUMENTS, documentsSize)
            put(ARCHIVES, archivesSize)
            put(OTHERS, othersSize)
        }

        return mimeTypeSizes
    }

    @SuppressLint("NewApi", "StringFormatInvalid")
    private fun getVolumeStorageStats(context: Context) {
        val externalDirs = context.getExternalFilesDirs(null)
        val storageManager = context.getSystemService(AppCompatActivity.STORAGE_SERVICE) as StorageManager

        externalDirs.forEach { file ->
            val volumeName: String
            val totalStorageSpace: Long
            val freeStorageSpace: Long
            val storageVolume = storageManager.getStorageVolume(file) ?: return
            if (storageVolume.isPrimary) {
                // internal storage
                volumeName = if (isQPlus()) PRIMARY_VOLUME_NAME else PRIMARY_VOLUME_NAME_OLD
                if (isOreoPlus()) {
                    val storageStatsManager = context.getSystemService(AppCompatActivity.STORAGE_STATS_SERVICE) as StorageStatsManager
                    val uuid = StorageManager.UUID_DEFAULT
                    totalStorageSpace = storageStatsManager.getTotalBytes(uuid)
                    freeStorageSpace = storageStatsManager.getFreeBytes(uuid)
                } else {
                    totalStorageSpace = file.totalSpace
                    freeStorageSpace = file.freeSpace
                }
                totalStorageSpaceLong = totalStorageSpace
            } else {
                // sd card
                volumeName = storageVolume.uuid!!.lowercase(Locale.US)
                totalStorageSpace = file.totalSpace
                freeStorageSpace = file.freeSpace
            }

            val filesSize = getSizesByMimeType(volumeName)
            val fileSizeImages = filesSize[IMAGES]!!
            val fileSizeVideos = filesSize[VIDEOS]!!
            val fileSizeAudios = filesSize[AUDIO]!!
            val fileSizeDocuments = filesSize[DOCUMENTS]!!
            val fileSizeArchives = filesSize[ARCHIVES]!!
            val fileSizeOthers = filesSize[OTHERS]!!

            post {
                volumes[volumeName]?.apply {
//                    arrayOf(
//                        mainStorageUsageProgressbar, imagesProgressbar, videosProgressbar, audioProgressbar, documentsProgressbar,
//                        archivesProgressbar, othersProgressbar
//                    ).forEach {
//                        it.max = (totalStorageSpace / SIZE_DIVIDER).toInt()
//                    }

                    //mainStorageUsageProgressbar.progress = ((totalStorageSpace - freeStorageSpace) / SIZE_DIVIDER).toInt()

                    //mainStorageUsageProgressbar.beVisible()
                    //freeSpaceValue.text = freeStorageSpace.formatSizeThousand()
                    //totalSpace.text = String.format(context.getString(R.string.total_storage), totalStorageSpace.formatSizeThousand())
                    totalSpace.text = String.format(context.getString(R.string.storage_used), freeStorageSpace.formatSizeThousand(), totalStorageSpace.formatSizeThousand())
                    //freeSpaceLabel.beVisible()

                    val appsSizeL = if (storageVolume.isPrimary) appsSizeLong else 0
                    val fileSizeSystem = totalStorageSpace - freeStorageSpace - appsSizeL - fileSizeImages - fileSizeVideos - fileSizeAudios - fileSizeDocuments - fileSizeArchives - fileSizeOthers
                    val widthMax = mainStorageProgressbar.width

                    if (storageVolume.isPrimary) {
                        val appsPercent = appsSizeLong.divideToPercent(totalStorageSpace)
                        setLayoutWidth(appsProgress, appsPercent, widthMax)
                    }

                    val imagesPercent = fileSizeImages.divideToPercent(totalStorageSpace)
                    setLayoutWidth(imageProgress, imagesPercent, widthMax)
                    imageDivider.beVisibleIf(imagesPercent != 0)

                    val videosPercent = fileSizeVideos.divideToPercent(totalStorageSpace)
                    setLayoutWidth(videoProgress, videosPercent, widthMax)
                    videoDivider.beVisibleIf(videosPercent != 0)

                    val audioPercent = fileSizeAudios.divideToPercent(totalStorageSpace)
                    setLayoutWidth(audioProgress, audioPercent, widthMax)
                    audioDivider.beVisibleIf(audioPercent != 0)

                    val documentsPercent = fileSizeDocuments.divideToPercent(totalStorageSpace)
                    setLayoutWidth(documentsProgress, documentsPercent, widthMax)
                    documentDivider.beVisibleIf(documentsPercent != 0)

                    val archivesPercent = fileSizeArchives.divideToPercent(totalStorageSpace)
                    setLayoutWidth(archivesProgress, archivesPercent, widthMax)
                    archiveDivider.beVisibleIf(archivesPercent != 0)

                    val othersPercent = fileSizeOthers.divideToPercent(totalStorageSpace)
                    setLayoutWidth(othersProgress, othersPercent, widthMax)
                    otherDivider.beVisibleIf(othersPercent != 0)

                    val systemPercent = fileSizeSystem.divideToPercent(totalStorageSpace)
                    setLayoutWidth(systemProgress, systemPercent, widthMax)
                    systemDivider.beVisibleIf(systemPercent != 0)
                    systemEndDivider.beVisibleIf(systemPercent != 0)

                    appsProgress.setOnClickListener {
                        toastColor(
                            message = context.getString(R.string.apps) + " " + appsSizeLong.divideToPercentText(totalStorageSpace),
                            color = context.resources.getColor(R.color.red_ios)) }
                    imageDivider.setOnClickListener {
                        toastColor(
                            message = context.getString(R.string.images) + " " + fileSizeImages.divideToPercentText(totalStorageSpace),
                            color = context.resources.getColor(R.color.orange_ios))
                    }
                    imageProgress.setOnClickListener {
                        toastColor(
                            message = context.getString(R.string.images) + " " + fileSizeImages.divideToPercentText(totalStorageSpace),
                            color = context.resources.getColor(R.color.orange_ios))
                    }
                    videoDivider.setOnClickListener {
                        toastColor(
                            message = context.getString(R.string.videos) + " " + fileSizeVideos.divideToPercentText(totalStorageSpace),
                            color = context.resources.getColor(R.color.yellow_ios))
                    }
                    videoProgress.setOnClickListener {
                        toastColor(
                            message = context.getString(R.string.videos) + " " + fileSizeVideos.divideToPercentText(totalStorageSpace),
                            color = context.resources.getColor(R.color.yellow_ios))
                    }
                    audioDivider.setOnClickListener {
                        toastColor(
                            message = context.getString(R.string.audio) + " " + fileSizeAudios.divideToPercentText(totalStorageSpace),
                            color = context.resources.getColor(R.color.green_ios))
                    }
                    audioProgress.setOnClickListener {
                        toastColor(
                            message = context.getString(R.string.audio) + " " + fileSizeAudios.divideToPercentText(totalStorageSpace),
                            color = context.resources.getColor(R.color.green_ios))
                    }
                    documentDivider.setOnClickListener {
                        toastColor(
                            message = context.getString(R.string.documents) + " " + fileSizeDocuments.divideToPercentText(totalStorageSpace),
                            color = context.resources.getColor(R.color.cyan_ios))
                    }
                    documentsProgress.setOnClickListener {
                        toastColor(
                            message = context.getString(R.string.documents) + " " + fileSizeDocuments.divideToPercentText(totalStorageSpace),
                            color = context.resources.getColor(R.color.cyan_ios))
                    }
                    archiveDivider.setOnClickListener {
                        toastColor(
                            message = context.getString(R.string.archives) + " " + fileSizeArchives.divideToPercentText(totalStorageSpace),
                            color = context.resources.getColor(R.color.blue_ios))
                    }
                    archivesProgress.setOnClickListener {
                        toastColor(
                            message = context.getString(R.string.archives) + " " + fileSizeArchives.divideToPercentText(totalStorageSpace),
                            color = context.resources.getColor(R.color.blue_ios))
                    }
                    otherDivider.setOnClickListener {
                        toastColor(
                            message = context.getString(R.string.others) + " " + fileSizeOthers.divideToPercentText(totalStorageSpace),
                            color = context.resources.getColor(R.color.purple_ios))
                    }
                    othersProgress.setOnClickListener {
                        toastColor(
                            message = context.getString(R.string.others) + " " + fileSizeOthers.divideToPercentText(totalStorageSpace),
                            color = context.resources.getColor(R.color.purple_ios))
                    }
                    systemDivider.setOnClickListener {
                        toastColor(
                            message = context.getString(R.string.system) + " " + fileSizeSystem.divideToPercentText(totalStorageSpace),
                            color = context.resources.getColor(R.color.gray_light_ios))
                    }
                    systemProgress.setOnClickListener {
                        toastColor(
                            message = context.getString(R.string.system) + " " + fileSizeSystem.divideToPercentText(totalStorageSpace),
                            color = context.resources.getColor(R.color.gray_light_ios))
                    }
                    systemEndDivider.setOnClickListener {
                        toastColor(
                            message = context.getString(R.string.storage_free).capitalize(Locale.getDefault()) + " " + freeStorageSpace.divideToPercentText(totalStorageSpace),
                            color = context.resources.getColor(R.color.gray_ios))
                    }
                    mainStorageProgressbar.setOnClickListener {
                        toastColor(
                            message = context.getString(R.string.storage_free).capitalize(Locale.getDefault()) + " " + freeStorageSpace.divideToPercentText(totalStorageSpace),
                            color = context.resources.getColor(R.color.gray_ios))
                    }

                    val listSize = mutableListOf(appsSizeL, fileSizeImages, fileSizeVideos, fileSizeAudios, fileSizeDocuments, fileSizeArchives, fileSizeOthers, fileSizeSystem)
                    val threeMax = getThreeMaxValues(listSize)
                    appsDecryptionImage.beVisibleIf(threeMax.contains(appsSizeL) && appsSizeL.toInt() != 0)
                    appsDecryption.beVisibleIf(threeMax.contains(appsSizeL) && appsSizeL.toInt() != 0)
                    imageDecryptionImage.beVisibleIf(threeMax.contains(fileSizeImages) && fileSizeImages.toInt() != 0)
                    imageDecryption.beVisibleIf(threeMax.contains(fileSizeImages) && fileSizeImages.toInt() != 0)
                    videoDecryptionImage.beVisibleIf(threeMax.contains(fileSizeVideos) && fileSizeVideos.toInt() != 0)
                    videoDecryption.beVisibleIf(threeMax.contains(fileSizeVideos) && fileSizeVideos.toInt() != 0)
                    audioDecryptionImage.beVisibleIf(threeMax.contains(fileSizeAudios) && fileSizeAudios.toInt() != 0)
                    audioDecryption.beVisibleIf(threeMax.contains(fileSizeAudios) && fileSizeAudios.toInt() != 0)
                    documentsDecryptionImage.beVisibleIf(threeMax.contains(fileSizeDocuments) && fileSizeDocuments.toInt() != 0)
                    documentsDecryption.beVisibleIf(threeMax.contains(fileSizeDocuments) && fileSizeDocuments.toInt() != 0)
                    archivesDecryptionImage.beVisibleIf(threeMax.contains(fileSizeArchives) && fileSizeArchives.toInt() != 0)
                    archivesDecryption.beVisibleIf(threeMax.contains(fileSizeArchives) && fileSizeArchives.toInt() != 0)
                    othersDecryptionImage.beVisibleIf(threeMax.contains(fileSizeOthers) && fileSizeOthers.toInt() != 0)
                    othersDecryption.beVisibleIf(threeMax.contains(fileSizeOthers) && fileSizeOthers.toInt() != 0)
                    systemDecryptionImage.beVisibleIf(threeMax.contains(fileSizeSystem) && fileSizeSystem.toInt() != 0)
                    systemDecryption.beVisibleIf(threeMax.contains(fileSizeSystem) && fileSizeSystem.toInt() != 0)
                }
            }
        }
    }

    override fun searchQueryChanged(text: String) {
        lastSearchedText = text
        binding.apply {
            storageSwipeRefresh.isEnabled = text.isEmpty() && activity?.config?.enablePullToRefresh != false
            if (text.isNotEmpty()) {
                if (searchHolder.alpha < 1f) {
                    searchHolder.fadeIn()
                }
            } else {
                searchHolder.animate().alpha(0f).setDuration(SHORT_ANIMATION_DURATION).withEndAction {
                    searchHolder.beGone()
                    (searchResultsList.adapter as? ItemsAdapter)?.updateItems(allDeviceListItems, text)
                }.start()
            }

            if (text.length == 1) {
                searchResultsList.beGone()
                searchPlaceholder.beVisible()
                searchPlaceholder2.beVisible()
                hideProgressBar()
            } else if (text.isEmpty()) {
                searchResultsList.beGone()
                hideProgressBar()
            } else {
                showProgressBar()
                ensureBackgroundThread {
                    val start = System.currentTimeMillis()
                    val filtered = allDeviceListItems.filter { it.mName.contains(text, true) }.toMutableList() as ArrayList<ListItem>
                    if (lastSearchedText != text) {
                        return@ensureBackgroundThread
                    }

                    (context as? Activity)?.runOnUiThread {
                        (searchResultsList.adapter as? ItemsAdapter)?.updateItems(filtered, text)
                        searchResultsList.beVisible()
                        searchPlaceholder.beVisibleIf(filtered.isEmpty())
                        searchPlaceholder2.beGone()
                        hideProgressBar()
                    }
                }
            }
        }
    }

    private fun setupLayoutManager() {
        if (context!!.config.getFolderViewType("") == VIEW_TYPE_GRID) {
            currentViewType = VIEW_TYPE_GRID
            setupGridLayoutManager()
        } else {
            currentViewType = VIEW_TYPE_LIST
            setupListLayoutManager()
        }

        binding.searchResultsList.adapter = null
        addItems()
    }

    private fun setupGridLayoutManager() {
        val layoutManager = binding.searchResultsList.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = context?.config?.fileColumnCnt ?: 3
    }

    private fun setupListLayoutManager() {
        val layoutManager = binding.searchResultsList.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = 1
    }

    private fun addItems() {
        ItemsAdapter(context as SimpleActivity, ArrayList(), this, binding.searchResultsList, false, null, false) {
            clickedPath((it as FileDirItem).path)
        }.apply {
            binding.searchResultsList.adapter = this
        }
        binding.storageSwipeRefresh.isRefreshing = false
    }

    private fun getAllFiles(volumeName: String): ArrayList<FileDirItem> {
        val fileDirItems = ArrayList<FileDirItem>()
        val showHidden = context?.config?.shouldShowHidden() ?: return fileDirItems
        val uri = MediaStore.Files.getContentUri(volumeName)
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )

        try {
            if (isOreoPlus()) {
                val queryArgs = bundleOf(
                    ContentResolver.QUERY_ARG_SORT_COLUMNS to arrayOf(MediaStore.Files.FileColumns.DATE_MODIFIED),
                    ContentResolver.QUERY_ARG_SORT_DIRECTION to ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                )
                context?.contentResolver?.query(uri, projection, queryArgs, null)
            } else {
                val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                context?.contentResolver?.query(uri, projection, null, null, sortOrder)
            }?.use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        try {
                            val name = cursor.getStringValue(MediaStore.Files.FileColumns.DISPLAY_NAME)
                            if (!showHidden && name.startsWith(".")) {
                                continue
                            }

                            val size = cursor.getLongValue(MediaStore.Files.FileColumns.SIZE)
                            if (size == 0L) {
                                continue
                            }

                            val path = cursor.getStringValue(MediaStore.Files.FileColumns.DATA)
                            val lastModified = cursor.getLongValue(MediaStore.Files.FileColumns.DATE_MODIFIED) * 1000
                            fileDirItems.add(FileDirItem(path, name, false, 0, size, lastModified))
                        } catch (e: Exception) {
                        }
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
            context?.showErrorToast(e)
        }

        return fileDirItems
    }

    private fun showProgressBar() {
        binding.progressBar.show()
    }

    private fun hideProgressBar() {
        binding.progressBar.hide()
    }

    private fun getRecyclerAdapter() = binding.searchResultsList.adapter as? ItemsAdapter

    override fun refreshFragment() {
        ensureBackgroundThread {
            val fileDirItems = volumes.keys.map { getAllFiles(it) }.flatten()
            allDeviceListItems = getListItemsFromFileDirItems(ArrayList(fileDirItems))
        }
        setupLayoutManager()
    }

    override fun deleteFiles(files: ArrayList<FileDirItem>) {
        handleFileDeleting(files, false)
    }

    override fun selectedPaths(paths: ArrayList<String>) {}

    override fun setupDateTimeFormat() {
        getRecyclerAdapter()?.updateDateTimeFormat()
    }

    override fun setupFontSize() {
        getRecyclerAdapter()?.updateFontSizes()
    }

    override fun toggleFilenameVisibility() {
        getRecyclerAdapter()?.updateDisplayFilenamesInGrid()
    }

    override fun columnCountChanged() {
        (binding.searchResultsList.layoutManager as MyGridLayoutManager).spanCount = context!!.config.fileColumnCnt
        getRecyclerAdapter()?.apply {
            notifyItemRangeChanged(0, listItems.size)
        }
    }

    override fun finishActMode() {
        getRecyclerAdapter()?.finishActMode()
    }

    //Goodwy
    private fun setLayoutWidth(view: View, percent: Int, width: Int) {
        val layoutParams = view.layoutParams
        layoutParams.width = percent.percentOf(width)
        view.layoutParams = layoutParams
    }

    private infix fun Int.percentOf(value: Int): Int {
        return if (this == 0) 0
        else ((value / 100) * this)
    }

    private fun Long.divideToPercent(divideTo: Long): Int {
        return if (divideTo.toInt() == 0 || this.toInt() == 0) 0
        else if ((this / divideTo.toFloat() * 100) < 1) 1 //less than 1% round up to 1%
        else (this / divideTo.toFloat() * 100).toInt()
    }

    private fun Long.divideToPercentText(divideTo: Long): String {
        return if (divideTo.toInt() == 0 || this.toInt() == 0) "0%"
        else if ((this / divideTo.toFloat() * 100) < 1) "< 1%" //less than 1% round up to 1%
        else (this / divideTo.toFloat() * 100).toInt().toString() + "%"
    }

    private fun getAllAppSize(volumeName: String) {
        if (checkAppOpsService()) {
            CoroutineScope(Dispatchers.IO).launch {
                val pm = context.packageManager
                val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

                appsSizeLong = 0
                for (appInfo in packages) {
                    try {
                        appsSizeLong += getAppStorage(context, appInfo.packageName)
                        post {
                            volumes[volumeName]!!.apply {
                                appsSize.text = appsSizeLong.formatSize()
                                //appsProgressbar.progress = (appsSizeLong / SIZE_DIVIDER).toInt()

                                val widthMax = mainStorageProgressbar.width
                                val appsPercent = appsSizeLong.divideToPercent(totalStorageSpaceLong)
                                setLayoutWidth(appsProgress, appsPercent, widthMax)
                            }
                        }
                        if (appInfo == packages.last()) getVolumeStorageStats(context)
                    } catch (e: PackageManager.NameNotFoundException) {
                        e.printStackTrace()
                    }
                }
            }
        } else if (!context!!.config.checkAppOpsService) {
            volumes[volumeName]!!.apply {
                appsSize.background = resources.getColoredDrawableWithColor(context, R.drawable.ripple_all_corners_56dp, context.getProperTextColor())
                appsSize.text = "???"
                appsSize.setOnClickListener {
                    (activity as? MainActivity)?.apply {
                        appOpsServiceDialog()
                    }
                }
            }
        } else {
            volumes[volumeName]!!.apply {
                appsSize.background = resources.getColoredDrawableWithColor(context, R.drawable.ripple_all_corners_56dp, context.getProperTextColor())
                appsSize.text = "???"
                appsSize.setOnClickListener {
                    (activity as? MainActivity)?.apply {
                        appOpsServiceDialog()
                    }
                }
            }
        }
    }

//    private fun isSystemPackage(pkgInfo: PackageInfo): Boolean {
//        return pkgInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
//    }

    private fun openAppOpsService() {
        try {
            val storageSettingsIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            context.startActivity(storageSettingsIntent)
        } catch (e: Exception) {
            context.showErrorToast(e)
        }
    }

    private fun checkAppOpsService(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    @SuppressLint("NewApi")
    fun getAppStorage(context: Context, packageName: String?): Long {
        val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val storageVolumes = storageManager.storageVolumes
        var appSizeL: Long = 0
        for (storageVolume in storageVolumes) {
            var uuid: UUID? = null
            val uuidStr = storageVolume.uuid
            uuid = try {
                if (TextUtils.isEmpty(uuidStr)) {
                    StorageManager.UUID_DEFAULT
                } else {
                    UUID.fromString(uuidStr)
                }
            } catch (e: java.lang.Exception) {
                StorageManager.UUID_DEFAULT
            }
            var storageStats: StorageStats? = null
            storageStats = try {
                val userHandle: UserHandle = android.os.Process.myUserHandle()
                storageStatsManager.queryStatsForPackage(uuid!!, packageName!!, userHandle)
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                return 0
            }
            // Get the total size of the app
            appSizeL = storageStats!!.appBytes + storageStats.cacheBytes + storageStats.dataBytes
        }
        return appSizeL
    }

    private fun getThreeMaxValues(list: MutableList<Long>): List<Long> {
        val max1 = list.maxOrNull() ?: 0
        list.remove(max1)
        val max2 = list.maxOrNull() ?: 0
        list.remove(max2)
        val max3 = list.maxOrNull() ?: 0
        list.remove(max3)
        return listOf(max1, max2, max3)
    }

    private fun toastColor(message: String, color: Int, length: Int = Toast.LENGTH_SHORT) {
        val toast: Toast = Toast.makeText(context, message, length)
        toast.view?.backgroundTintList = ColorStateList.valueOf(color)
        toast.show()
    }

    override fun myRecyclerView() = binding.storageNestedScrollview
}
