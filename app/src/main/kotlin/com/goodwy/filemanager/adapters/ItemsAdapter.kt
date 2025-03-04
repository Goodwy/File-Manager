package com.goodwy.filemanager.adapters

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.res.Resources
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.goodwy.commons.adapters.MyRecyclerViewAdapter
import com.goodwy.commons.dialogs.*
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.interfaces.ItemTouchHelperContract
import com.goodwy.commons.models.FileDirItem
import com.goodwy.commons.models.RadioItem
import com.goodwy.commons.views.MyRecyclerView
import com.goodwy.filemanager.R
import com.goodwy.filemanager.activities.SimpleActivity
import com.goodwy.filemanager.activities.SplashActivity
import com.goodwy.filemanager.databinding.*
import com.goodwy.filemanager.dialogs.CompressAsDialog
import com.goodwy.filemanager.extensions.*
import com.goodwy.filemanager.helpers.*
import com.goodwy.filemanager.interfaces.ItemOperationsListener
import com.goodwy.filemanager.models.ListItem
import com.stericson.RootTools.RootTools
import me.thanel.swipeactionview.SwipeActionView
import me.thanel.swipeactionview.SwipeDirection
import me.thanel.swipeactionview.SwipeGestureListener
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.io.inputstream.ZipInputStream
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.LocalFileHeader
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.util.LinkedList
import java.util.Locale

class ItemsAdapter(
    activity: SimpleActivity,
    var listItems: MutableList<ListItem>,
    private val listener: ItemOperationsListener?,
    recyclerView: MyRecyclerView,
    private val isPickMultipleIntent: Boolean,
    private val swipeRefreshLayout: SwipeRefreshLayout?,
    canHaveIndividualViewType: Boolean = true,
    itemClick: (Any) -> Unit,
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick), ItemTouchHelperContract, RecyclerViewFastScroller.OnPopupTextUpdate {

    private lateinit var fileDrawable: Drawable
    private lateinit var folderDrawable: Drawable
    private var fileDrawables = HashMap<String, Drawable>()
    private var currentItemsHash = listItems.hashCode()
    private var textToHighlight = ""
    private val hasOTGConnected = activity.hasOTGConnected()
    private var fontSize = 0f
    private var smallerFontSize = 0f
    private var dateFormat = ""
    private var timeFormat = ""

    private val config = activity.config
    private val viewType = if (canHaveIndividualViewType) {
        config.getFolderViewType(listItems.firstOrNull { !it.isSectionTitle }?.mPath?.getParentPath() ?: "")
    } else {
        config.viewType
    }
    private val isListViewType = viewType == VIEW_TYPE_LIST
    private var displayFilenamesInGrid = config.displayFilenames

    companion object {
        private const val TYPE_FILE = 1
        private const val TYPE_DIR = 2
        private const val TYPE_SECTION = 3
        private const val TYPE_GRID_TYPE_DIVIDER = 4
    }

    init {
        setupDragListener(true)
        initDrawables()
        updateFontSizes()
        dateFormat = config.dateFormat
        timeFormat = activity.getTimeFormat()
    }

    override fun getActionMenuId() = R.menu.cab

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_decompress).isVisible = getSelectedFileDirItems().map { it.path }.any { it.isZipFile() }
            findItem(R.id.cab_confirm_selection).isVisible = isPickMultipleIntent
            findItem(R.id.cab_copy_path).isVisible = isOneItemSelected()
            findItem(R.id.cab_open_with).isVisible = isOneFileSelected()
            findItem(R.id.cab_open_as).isVisible = isOneFileSelected()
            findItem(R.id.cab_set_as).isVisible = isOneFileSelected()
            findItem(R.id.cab_create_shortcut).isVisible = isOreoPlus() && isOneItemSelected()

            checkHideBtnVisibility(this)
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_confirm_selection -> confirmSelection()
            R.id.cab_rename -> displayRenameDialog()
            R.id.cab_properties -> showProperties()
            R.id.cab_share -> shareFiles()
            R.id.cab_hide -> toggleFileVisibility(true)
            R.id.cab_unhide -> toggleFileVisibility(false)
            R.id.cab_create_shortcut -> createShortcut()
            R.id.cab_copy_path -> copyPath()
            R.id.cab_set_as -> setAs()
            R.id.cab_open_with -> openWith()
            R.id.cab_open_as -> openAs()
            R.id.cab_copy_to -> copyMoveTo(true)
            R.id.cab_move_to -> tryMoveFiles()
            R.id.cab_compress -> compressSelection()
            R.id.cab_decompress -> decompressSelection()
            R.id.cab_select_all -> selectAll()
            R.id.cab_delete -> if (config.skipDeleteConfirmation) deleteFiles() else askConfirmDelete()
        }
    }

    override fun getSelectableItemCount() = listItems.filter { !it.isSectionTitle && !it.isGridTypeDivider }.size

    override fun getIsItemSelectable(position: Int) = !listItems[position].isSectionTitle && !listItems[position].isGridTypeDivider

    override fun getItemSelectionKey(position: Int) = listItems.getOrNull(position)?.path?.hashCode()

    override fun getItemKeyPosition(key: Int) = listItems.indexOfFirst { it.path.hashCode() == key }

    override fun onActionModeCreated() {
        swipeRefreshLayout?.isRefreshing = false
        swipeRefreshLayout?.isEnabled = false
    }

    override fun onActionModeDestroyed() {
        swipeRefreshLayout?.isEnabled = activity.config.enablePullToRefresh
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            listItems[position].isGridTypeDivider -> TYPE_GRID_TYPE_DIVIDER
            listItems[position].isSectionTitle -> TYPE_SECTION
            listItems[position].mIsDirectory -> TYPE_DIR
            else -> TYPE_FILE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = Binding.getByItemViewType(viewType, isListViewType, activity.config.useSwipeToAction).inflate(layoutInflater, parent, false)

        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: MyRecyclerViewAdapter.ViewHolder, position: Int) {
        if (position == listItems.lastIndex){
            val params = holder.itemView.layoutParams as RecyclerView.LayoutParams
            val margin = activity.resources.getDimension(R.dimen.shortcut_size).toInt()
            params.bottomMargin = margin
            holder.itemView.layoutParams = params
        } else {
            val params = holder.itemView.layoutParams as RecyclerView.LayoutParams
            params.bottomMargin = 0
            holder.itemView.layoutParams = params
        }

        val fileDirItem = listItems[position]
        holder.bindView(fileDirItem, true, !fileDirItem.isSectionTitle) { itemView, layoutPosition ->
            val viewType = getItemViewType(position)
            setupView(Binding.getByItemViewType(viewType, isListViewType, activity.config.useSwipeToAction).bind(itemView), fileDirItem, holder)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = listItems.size

    private fun getItemWithKey(key: Int): FileDirItem? = listItems.firstOrNull { it.path.hashCode() == key }

    private fun isOneFileSelected() = isOneItemSelected() && getItemWithKey(selectedKeys.first())?.isDirectory == false

    private fun checkHideBtnVisibility(menu: Menu) {
        var hiddenCnt = 0
        var unhiddenCnt = 0
        getSelectedFileDirItems().map { it.name }.forEach {
            if (it.startsWith(".")) {
                hiddenCnt++
            } else {
                unhiddenCnt++
            }
        }

        menu.findItem(R.id.cab_hide).isVisible = unhiddenCnt > 0
        menu.findItem(R.id.cab_unhide).isVisible = hiddenCnt > 0
    }

    private fun confirmSelection() {
        if (selectedKeys.isNotEmpty()) {
            val paths = getSelectedFileDirItems().asSequence().filter { !it.isDirectory }.map { it.path }.toMutableList() as ArrayList<String>
            if (paths.isEmpty()) {
                finishActMode()
            } else {
                listener?.selectedPaths(paths)
            }
        }
    }

    private fun displayRenameDialog() {
        val fileDirItems = getSelectedFileDirItems()
        val paths = fileDirItems.asSequence().map { it.path }.toMutableList() as ArrayList<String>
        when {
            paths.size == 1 -> {
                val oldPath = paths.first()
                RenameItemDialog(activity, oldPath) {
                    config.moveFavorite(oldPath, it)
                    activity.runOnUiThread {
                        listener?.refreshFragment()
                        finishActMode()
                    }
                }
            }

            fileDirItems.any { it.isDirectory } -> RenameItemsDialog(activity, paths) {
                activity.runOnUiThread {
                    listener?.refreshFragment()
                    finishActMode()
                }
            }

            else -> RenameDialog(activity, paths, false) {
                activity.runOnUiThread {
                    listener?.refreshFragment()
                    finishActMode()
                }
            }
        }
    }

    private fun showProperties() {
        if (selectedKeys.size <= 1) {
            PropertiesDialog(activity, getFirstSelectedItemPath(), config.shouldShowHidden())
        } else {
            val paths = getSelectedFileDirItems().map { it.path }
            PropertiesDialog(activity, paths, config.shouldShowHidden())
        }
    }

    private fun shareFiles() {
        val selectedItems = getSelectedFileDirItems()
        val paths = ArrayList<String>(selectedItems.size)
        selectedItems.forEach {
            addFileUris(it.path, paths)
        }
        activity.sharePaths(paths)
    }

    private fun toggleFileVisibility(hide: Boolean) {
        ensureBackgroundThread {
            getSelectedFileDirItems().forEach {
                activity.toggleItemVisibility(it.path, hide)
            }
            activity.runOnUiThread {
                listener?.refreshFragment()
                finishActMode()
            }
        }
    }

    @SuppressLint("NewApi")
    private fun createShortcut() {
        val manager = activity.getSystemService(ShortcutManager::class.java)
        if (manager.isRequestPinShortcutSupported) {
            val path = getFirstSelectedItemPath()
            val drawable = resources.getDrawable(R.drawable.shortcut_folder).mutate()
            getShortcutImage(path, drawable) {
                val intent = Intent(activity, SplashActivity::class.java)
                intent.action = Intent.ACTION_VIEW
                intent.flags = intent.flags or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
                intent.data = Uri.fromFile(File(path))

                val shortcut = ShortcutInfo.Builder(activity, path)
                    .setShortLabel(path.getFilenameFromPath())
                    .setIcon(Icon.createWithBitmap(drawable.convertToBitmap()))
                    .setIntent(intent)
                    .build()

                manager.requestPinShortcut(shortcut, null)
            }
        }
    }

    private fun getShortcutImage(path: String, drawable: Drawable, callback: () -> Unit) {
        val appIconColor = baseConfig.primaryColor //baseConfig.appIconColor
        (drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_folder_background).applyColorFilter(appIconColor)
        if (activity.getIsPathDirectory(path)) {
            callback()
        } else {
            ensureBackgroundThread {
                val options = RequestOptions()
                    .format(DecodeFormat.PREFER_ARGB_8888)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .fitCenter()

                val size = activity.resources.getDimension(R.dimen.shortcut_size).toInt()
                val builder = Glide.with(activity)
                    .asDrawable()
                    .load(getImagePathToLoad(path))
                    .apply(options)
                    .centerCrop()
                    .into(size, size)

                try {
                    val bitmap = builder.get()
                    drawable.findDrawableByLayerId(R.id.shortcut_folder_background).applyColorFilter(0)
                    drawable.setDrawableByLayerId(R.id.shortcut_folder_image, bitmap)
                } catch (e: Exception) {
                    val fileIcon = fileDrawables.getOrElse(path.substringAfterLast(".").lowercase(Locale.getDefault()), { fileDrawable })
                    drawable.setDrawableByLayerId(R.id.shortcut_folder_image, fileIcon)
                }

                activity.runOnUiThread {
                    callback()
                }
            }
        }
    }

    @SuppressLint("NewApi")
    private fun addFileUris(path: String, paths: ArrayList<String>) {
        if (activity.getIsPathDirectory(path)) {
            val shouldShowHidden = config.shouldShowHidden()
            when {
                activity.isRestrictedSAFOnlyRoot(path) -> {
                    activity.getAndroidSAFFileItems(path, shouldShowHidden, false) { files ->
                        files.forEach {
                            addFileUris(activity.getAndroidSAFUri(it.path).toString(), paths)
                        }
                    }
                }

                activity.isPathOnOTG(path) -> {
                    activity.getDocumentFile(path)?.listFiles()?.filter { if (shouldShowHidden) true else !it.name!!.startsWith(".") }?.forEach {
                        addFileUris(it.uri.toString(), paths)
                    }
                }

                else -> {
                    File(path).listFiles()?.filter { if (shouldShowHidden) true else !it.name.startsWith('.') }?.forEach {
                        addFileUris(it.absolutePath, paths)
                    }
                }
            }
        } else {
            paths.add(path)
        }
    }

    private fun copyPath() {
        activity.copyToClipboard(getFirstSelectedItemPath())
        finishActMode()
    }

    private fun setAs() {
        activity.setAs(getFirstSelectedItemPath())
    }

    private fun openWith() {
        activity.tryOpenPathIntent(getFirstSelectedItemPath(), true)
    }

    private fun openAs() {
        val res = activity.resources
        val items = arrayListOf(
            RadioItem(OPEN_AS_TEXT, res.getString(R.string.text_file)),
            RadioItem(OPEN_AS_IMAGE, res.getString(R.string.image_file)),
            RadioItem(OPEN_AS_AUDIO, res.getString(R.string.audio_file)),
            RadioItem(OPEN_AS_VIDEO, res.getString(R.string.video_file)),
            RadioItem(OPEN_AS_OTHER, res.getString(R.string.other_file))
        )

        RadioGroupDialog(activity, items) {
            activity.tryOpenPathIntent(getFirstSelectedItemPath(), false, it as Int)
        }
    }

    private fun tryMoveFiles() {
        activity.handleDeletePasswordProtection {
            copyMoveTo(false)
        }
    }

    private fun copyMoveTo(isCopyOperation: Boolean) {
        val files = getSelectedFileDirItems()
        val firstFile = files[0]
        val source = firstFile.getParentPath()
        val titleText = if (isCopyOperation) R.string.copy_to else R.string.move_to
        FilePickerDialog(
            activity = activity,
            currPath = activity.getDefaultCopyDestinationPath(config.shouldShowHidden(), source),
            pickFile = false,
            showHidden = config.shouldShowHidden(),
            showFAB = true,
            canAddShowHiddenButton = true,
            showFavoritesButton = true,
            titleText = titleText,
            useAccentColor = true
        ) { it ->
            config.lastCopyPath = it
            if (files.any { file -> it.contains(file.path) }) {
                activity.toast(R.string.cannot_be_inserted_into_itself, Toast.LENGTH_LONG)
                return@FilePickerDialog
            }
            if (activity.isPathOnRoot(it) || activity.isPathOnRoot(firstFile.path)) {
                copyMoveRootItems(files, it, isCopyOperation)
            } else {
                activity.copyMoveFilesTo(files, source, it, isCopyOperation, false, config.shouldShowHidden()) {
                    if (!isCopyOperation) {
                        files.forEach { sourceFileDir ->
                            val sourcePath = sourceFileDir.path
                            if (activity.isRestrictedSAFOnlyRoot(sourcePath) && activity.getDoesFilePathExist(sourcePath)) {
                                activity.deleteFile(sourceFileDir, true) {
                                    listener?.refreshFragment()
                                    activity.runOnUiThread {
                                        finishActMode()
                                    }
                                }
                            } else {
                                val sourceFile = File(sourcePath)
                                if (activity.getDoesFilePathExist(source) && activity.getIsPathDirectory(source) &&
                                    sourceFile.list()?.isEmpty() == true && sourceFile.getProperSize(true) == 0L && sourceFile.getFileCount(true) == 0
                                ) {
                                    val sourceFolder = sourceFile.toFileDirItem(activity)
                                    activity.deleteFile(sourceFolder, true) {
                                        listener?.refreshFragment()
                                        activity.runOnUiThread {
                                            finishActMode()
                                        }
                                    }
                                } else {
                                    listener?.refreshFragment()
                                    finishActMode()
                                }
                            }
                        }
                    } else {
                        listener?.refreshFragment()
                        finishActMode()
                    }
                }
            }
        }
    }

    private fun copyMoveRootItems(files: ArrayList<FileDirItem>, destinationPath: String, isCopyOperation: Boolean) {
        activity.toast(R.string.copying)
        ensureBackgroundThread {
            val fileCnt = files.size
            RootHelpers(activity).copyMoveFiles(files, destinationPath, isCopyOperation) {
                when (it) {
                    fileCnt -> activity.toast(R.string.copying_success)
                    0 -> activity.toast(R.string.copy_failed)
                    else -> activity.toast(R.string.copying_success_partial)
                }

                activity.runOnUiThread {
                    listener?.refreshFragment()
                    finishActMode()
                }
            }
        }
    }

    private fun compressSelection(path: String? = null) {
        val firstPath = path ?: getFirstSelectedItemPath()
        if (activity.isPathOnOTG(firstPath)) {
            activity.toast(R.string.unknown_error_occurred)
            return
        }

        CompressAsDialog(activity, firstPath) { destination, password ->
            activity.handleAndroidSAFDialog(firstPath) { granted ->
                if (!granted) {
                    return@handleAndroidSAFDialog
                }
                activity.handleSAFDialog(firstPath) {
                    if (!it) {
                        return@handleSAFDialog
                    }

                    activity.toast(R.string.compressing)
                    val paths = if (path == null) getSelectedFileDirItems().map { it.path } else listOf (path)
                    ensureBackgroundThread {
                        if (compressPaths(paths, destination, password)) {
                            activity.runOnUiThread {
                                activity.toast(R.string.compression_successful)
                                listener?.refreshFragment()
                                finishActMode()
                            }
                        } else {
                            activity.toast(R.string.compressing_failed)
                        }
                    }
                }
            }
        }
    }

    private fun decompressSelection(path: String? = null) {
        val firstPath = path ?: getFirstSelectedItemPath()
        if (activity.isPathOnOTG(firstPath)) {
            activity.toast(R.string.unknown_error_occurred)
            return
        }

        activity.handleSAFDialog(firstPath) {
            if (!it) {
                return@handleSAFDialog
            }

            val paths =
                if (path == null) getSelectedFileDirItems().asSequence().map { it.path }.filter { it.isZipFile() }.toList() else listOf (path)
            ensureBackgroundThread {
                tryDecompressingPaths(paths) { success ->
                    activity.runOnUiThread {
                        if (success) {
                            activity.toast(R.string.decompression_successful)
                            listener?.refreshFragment()
                            finishActMode()
                        } else {
                            activity.toast(R.string.decompressing_failed)
                        }
                    }
                }
            }
        }
    }

    private fun tryDecompressingPaths(sourcePaths: List<String>, callback: (success: Boolean) -> Unit) {
        sourcePaths.forEach { path ->
            ZipInputStream(BufferedInputStream(activity.getFileInputStreamSync(path))).use { zipInputStream ->
                try {
                    val fileDirItems = ArrayList<FileDirItem>()
                    var entry = zipInputStream.nextEntry
                    while (entry != null) {
                        val currPath = if (entry.isDirectory) path else "${path.getParentPath().trimEnd('/')}/${entry.fileName}"
                        val fileDirItem = FileDirItem(currPath, entry.fileName, entry.isDirectory, 0, entry.uncompressedSize)
                        fileDirItems.add(fileDirItem)
                        entry = zipInputStream.nextEntry
                    }
                    val destinationPath = fileDirItems.first().getParentPath().trimEnd('/')
                    activity.runOnUiThread {
                        activity.checkConflicts(fileDirItems, destinationPath, 0, LinkedHashMap()) {
                            ensureBackgroundThread {
                                decompressPaths(sourcePaths, it, callback)
                            }
                        }
                    }
                } catch (zipException: ZipException) {
                    if (zipException.type == ZipException.Type.WRONG_PASSWORD) {
                        activity.showErrorToast(activity.getString(R.string.invalid_password))
                    } else {
                        activity.showErrorToast(zipException)
                    }
                } catch (exception: Exception) {
                    activity.showErrorToast(exception)
                }
            }
        }
    }

    private fun decompressPaths(paths: List<String>, conflictResolutions: LinkedHashMap<String, Int>, callback: (success: Boolean) -> Unit) {
        paths.forEach { path ->
            val zipInputStream = ZipInputStream(BufferedInputStream(activity.getFileInputStreamSync(path)))
            zipInputStream.use {
                try {
                    var entry = zipInputStream.nextEntry
                    val zipFileName = path.getFilenameFromPath()
                    val newFolderName = zipFileName.subSequence(0, zipFileName.length - 4)
                    while (entry != null) {
                        val parentPath = path.getParentPath()
                        val newPath = "$parentPath/$newFolderName/${entry.fileName.trimEnd('/')}"

                        val resolution = getConflictResolution(conflictResolutions, newPath)
                        val doesPathExist = activity.getDoesFilePathExist(newPath)
                        if (doesPathExist && resolution == CONFLICT_OVERWRITE) {
                            val fileDirItem = FileDirItem(newPath, newPath.getFilenameFromPath(), entry.isDirectory)
                            if (activity.getIsPathDirectory(path)) {
                                activity.deleteFolderBg(fileDirItem, false) {
                                    if (it) {
                                        extractEntry(newPath, entry, zipInputStream)
                                    } else {
                                        callback(false)
                                    }
                                }
                            } else {
                                activity.deleteFileBg(fileDirItem, false, false) {
                                    if (it) {
                                        extractEntry(newPath, entry, zipInputStream)
                                    } else {
                                        callback(false)
                                    }
                                }
                            }
                        } else if (!doesPathExist) {
                            extractEntry(newPath, entry, zipInputStream)
                        }

                        entry = zipInputStream.nextEntry
                    }
                    callback(true)
                } catch (e: Exception) {
                    activity.showErrorToast(e)
                    callback(false)
                }
            }
        }
    }

    private fun extractEntry(newPath: String, entry: LocalFileHeader, zipInputStream: ZipInputStream) {
        if (entry.isDirectory) {
            if (!activity.createDirectorySync(newPath) && !activity.getDoesFilePathExist(newPath)) {
                val error = String.format(activity.getString(R.string.could_not_create_file), newPath)
                activity.showErrorToast(error)
            }
        } else {
            val fos = activity.getFileOutputStreamSync(newPath, newPath.getMimeType())
            if (fos != null) {
                zipInputStream.copyTo(fos)
            }
        }
    }

    private fun getConflictResolution(conflictResolutions: LinkedHashMap<String, Int>, path: String): Int {
        return if (conflictResolutions.size == 1 && conflictResolutions.containsKey("")) {
            conflictResolutions[""]!!
        } else if (conflictResolutions.containsKey(path)) {
            conflictResolutions[path]!!
        } else {
            CONFLICT_SKIP
        }
    }

    @SuppressLint("NewApi")
    private fun compressPaths(sourcePaths: List<String>, targetPath: String, password: String? = null): Boolean {
        val queue = LinkedList<String>()
        val fos = activity.getFileOutputStreamSync(targetPath, "application/zip") ?: return false

        val zout = password?.let { ZipOutputStream(fos, password.toCharArray()) } ?: ZipOutputStream(fos)
        var res: Closeable = fos

        fun zipEntry(name: String) = ZipParameters().also {
            it.fileNameInZip = name
            if (password != null) {
                it.isEncryptFiles = true
                it.encryptionMethod = EncryptionMethod.AES
            }
        }

        try {
            sourcePaths.forEach { currentPath ->
                var name: String
                var mainFilePath = currentPath
                val base = "${mainFilePath.getParentPath()}/"
                res = zout
                queue.push(mainFilePath)
                if (activity.getIsPathDirectory(mainFilePath)) {
                    name = "${mainFilePath.getFilenameFromPath()}/"
                    zout.putNextEntry(
                        ZipParameters().also {
                            it.fileNameInZip = name
                        }
                    )
                }

                while (!queue.isEmpty()) {
                    mainFilePath = queue.pop()
                    if (activity.getIsPathDirectory(mainFilePath)) {
                        if (activity.isRestrictedSAFOnlyRoot(mainFilePath)) {
                            activity.getAndroidSAFFileItems(mainFilePath, true) { files ->
                                for (file in files) {
                                    name = file.path.relativizeWith(base)
                                    if (activity.getIsPathDirectory(file.path)) {
                                        queue.push(file.path)
                                        name = "${name.trimEnd('/')}/"
                                        zout.putNextEntry(zipEntry(name))
                                    } else {
                                        zout.putNextEntry(zipEntry(name))
                                        activity.getFileInputStreamSync(file.path)!!.copyTo(zout)
                                        zout.closeEntry()
                                    }
                                }
                            }
                        } else {
                            val mainFile = File(mainFilePath)
                            for (file in mainFile.listFiles()) {
                                name = file.path.relativizeWith(base)
                                if (activity.getIsPathDirectory(file.absolutePath)) {
                                    queue.push(file.absolutePath)
                                    name = "${name.trimEnd('/')}/"
                                    zout.putNextEntry(zipEntry(name))
                                } else {
                                    zout.putNextEntry(zipEntry(name))
                                    activity.getFileInputStreamSync(file.path)!!.copyTo(zout)
                                    zout.closeEntry()
                                }
                            }
                        }

                    } else {
                        name = if (base == currentPath) currentPath.getFilenameFromPath() else mainFilePath.relativizeWith(base)
                        zout.putNextEntry(zipEntry(name))
                        activity.getFileInputStreamSync(mainFilePath)!!.copyTo(zout)
                        zout.closeEntry()
                    }
                }
            }
        } catch (exception: Exception) {
            activity.showErrorToast(exception)
            return false
        } finally {
            res.close()
        }
        return true
    }

    private fun askConfirmDelete() {
        activity.handleDeletePasswordProtection {
            val itemsCnt = selectedKeys.size
            val items = if (itemsCnt == 1) {
                "\"${getFirstSelectedItemPath().getFilenameFromPath()}\""
            } else {
                resources.getQuantityString(R.plurals.delete_items, itemsCnt, itemsCnt)
            }

            val question = String.format(resources.getString(R.string.deletion_confirmation), items)
            ConfirmationDialog(activity, question) {
                deleteFiles()
            }
        }
    }

    private fun deleteFiles() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val SAFPath = getFirstSelectedItemPath()
        if (activity.isPathOnRoot(SAFPath) && !RootTools.isRootAvailable()) {
            activity.toast(R.string.rooted_device_only)
            return
        }

        activity.handleSAFDialog(SAFPath) { granted ->
            if (!granted) {
                return@handleSAFDialog
            }

            val files = ArrayList<FileDirItem>(selectedKeys.size)
            val positions = ArrayList<Int>()
            ensureBackgroundThread {
                selectedKeys.forEach { key ->
                    config.removeFavorite(getItemWithKey(key)?.path ?: "")
                    val position = listItems.indexOfFirst { it.path.hashCode() == key }
                    if (position != -1) {
                        positions.add(position)
                        files.add(listItems[position])
                    }
                }

                positions.sortDescending()
                activity.runOnUiThread {
                    removeSelectedItems(positions)
                    listener?.deleteFiles(files)
                    positions.forEach {
                        listItems.removeAt(it)
                    }
                }
            }
        }
    }

    private fun getFirstSelectedItemPath() = getSelectedFileDirItems().first().path

    private fun getSelectedFileDirItems() = listItems.filter { selectedKeys.contains(it.path.hashCode()) } as ArrayList<FileDirItem>

    fun updateItems(newItems: ArrayList<ListItem>, highlightText: String = "") {
        if (newItems.hashCode() != currentItemsHash) {
            currentItemsHash = newItems.hashCode()
            textToHighlight = highlightText
            listItems = newItems.clone() as ArrayList<ListItem>
            notifyDataSetChanged()
            finishActMode()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
    }

    fun updateFontSizes() {
        fontSize = activity.getTextSize()
        smallerFontSize = fontSize * 0.8f
        notifyDataSetChanged()
    }

    fun updateDateTimeFormat() {
        dateFormat = config.dateFormat
        timeFormat = activity.getTimeFormat()
        notifyDataSetChanged()
    }

    fun updateDisplayFilenamesInGrid() {
        displayFilenamesInGrid = config.displayFilenames
        notifyDataSetChanged()
    }

    fun updateChildCount(path: String, count: Int) {
        val position = getItemKeyPosition(path.hashCode())
        val item = listItems.getOrNull(position) ?: return
        item.children = count
        notifyItemChanged(position, Unit)
    }

    fun isASectionTitle(position: Int) = listItems.getOrNull(position)?.isSectionTitle ?: false

    fun isGridTypeDivider(position: Int) = listItems.getOrNull(position)?.isGridTypeDivider ?: false

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            val icon = Binding.getByItemViewType(holder.itemViewType, isListViewType, activity.config.useSwipeToAction).bind(holder.itemView).itemIcon
            if (icon != null) {
                Glide.with(activity).clear(icon)
            }
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables", "SetTextI18n")
    private fun setupView(binding: ItemViewBinding, listItem: ListItem, holder: MyRecyclerViewAdapter.ViewHolder) {
        val isSelected = selectedKeys.contains(listItem.path.hashCode())
        binding.apply {
            if (listItem.isSectionTitle) {
                itemIcon?.setImageDrawable(folderDrawable)
                itemSection?.text = if (textToHighlight.isEmpty()) listItem.mName else listItem.mName.highlightTextPart(textToHighlight, properPrimaryColor)
                itemSection?.setTextColor(textColor)
                itemSection?.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            } else if (!listItem.isGridTypeDivider) {
                if (isListViewType) {
                    itemIcon?.apply {
                        setOnClickListener { holder.viewClicked(listItem) } //if (selectedKeys.isEmpty()) holder.viewLongClicked() else holder.viewClicked(listItem)
                        setOnLongClickListener {
                            showPopupMenu(itemName!!, listItem)
                            true
                        }
                    }
                }

                if (!activity.config.useSwipeToAction) root.setupViewBackground(activity)
                itemFrame.isSelected = isSelected
                val fileName = listItem.name
                itemName?.text = if (textToHighlight.isEmpty()) fileName else fileName.highlightTextPart(textToHighlight, properPrimaryColor)
                itemName?.setTextColor(textColor)
                itemName?.setTextSize(TypedValue.COMPLEX_UNIT_PX, if (isListViewType) fontSize else smallerFontSize)

                itemDetails?.setTextColor(textColor)
                itemDetails?.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallerFontSize) //fontSize

//                item_date?.setTextColor(textColor)
//                item_date?.setTextSize(TypedValue.COMPLEX_UNIT_PX, smallerFontSize)

                itemCheck?.beVisibleIf(isSelected)
                if (isSelected) {
                    itemCheck?.background?.applyColorFilter(properPrimaryColor)
                    itemCheck?.applyColorFilter(contrastColor)
                }

                if (!isListViewType && !listItem.isDirectory) {
                    itemName?.beVisibleIf(displayFilenamesInGrid)
                } else {
                    itemName?.beVisible()
                }

                if (listItem.isDirectory) {
                    itemIcon?.setImageDrawable(folderDrawable)
                    if (isListViewType) itemIcon?.updatePadding(top = resources.getDimensionPixelSize(R.dimen.smaller_margin), bottom = resources.getDimensionPixelSize(R.dimen.ten_dpi))
                    itemDetails?.text = listItem.modified.formatDate(activity, dateFormat, timeFormat) + " – " + getChildrenCnt(listItem)
                    itemDetails?.beGoneIf(config.showOnlyFilename)
//                    item_date?.beGone()

                    itemAdditionalIcon?.applyColorFilter(textColor)
                    chevron?.beVisible()
                    chevron?.applyColorFilter(textColor)

                    if (config.showFolderIcon) {
                        when (fileName) {
                            "Download", activity.getString(R.string.download), activity.getString(R.string.downloads) -> {
                                itemAdditionalIcon?.setImageDrawable(resources.getDrawable(R.drawable.ic_download))
                                itemAdditionalIcon?.beVisible()
                            }

                            "Android" -> {
                                itemAdditionalIcon?.setImageDrawable(resources.getDrawable(R.drawable.ic_android))
                                itemAdditionalIcon?.beVisible()
                            }

                            "Audiobook", "Audiobooks", "Book", "Books" -> {
                                itemAdditionalIcon?.setImageDrawable(resources.getDrawable(R.drawable.ic_book))
                                itemAdditionalIcon?.beVisible()
                            }

                            "Bluetooth" -> {
                                itemAdditionalIcon?.setImageDrawable(resources.getDrawable(R.drawable.ic_bluetooth))
                                itemAdditionalIcon?.beVisible()
                            }

                            "DCIM", "Camera" -> {
                                itemAdditionalIcon?.setImageDrawable(resources.getDrawable(R.drawable.ic_camera))
                                itemAdditionalIcon?.beVisible()
                            }

                            "Documents", "Document" -> {
                                itemAdditionalIcon?.setImageDrawable(resources.getDrawable(R.drawable.ic_document))
                                itemAdditionalIcon?.beVisible()
                            }

                            "Games", "Game" -> {
                                itemAdditionalIcon?.setImageDrawable(resources.getDrawable(R.drawable.ic_controller))
                                itemAdditionalIcon?.beVisible()
                            }

                            "Media" -> {
                                itemAdditionalIcon?.setImageDrawable(resources.getDrawable(R.drawable.ic_media))
                                itemAdditionalIcon?.beVisible()
                            }

                            "Movies", "Movie" -> {
                                itemAdditionalIcon?.setImageDrawable(resources.getDrawable(R.drawable.ic_tape))
                                itemAdditionalIcon?.beVisible()
                            }

                            "Videos", "Video" -> {
                                itemAdditionalIcon?.setImageDrawable(resources.getDrawable(R.drawable.ic_videocam_outline))
                                itemAdditionalIcon?.beVisible()
                            }

                            "Music", "Audio" -> {
                                itemAdditionalIcon?.setImageDrawable(resources.getDrawable(R.drawable.ic_itunes_note))
                                itemAdditionalIcon?.beVisible()
                            }

                            "Note", "Notes" -> {
                                itemAdditionalIcon?.setImageDrawable(resources.getDrawable(R.drawable.ic_note_sticky))
                                itemAdditionalIcon?.beVisible()
                            }

                            "Pictures", "Picture", "Images", "Image", "Photos", "Photo", "Photographs", "Photograph" -> {
                                itemAdditionalIcon?.setImageDrawable(resources.getDrawable(R.drawable.ic_images))
                                itemAdditionalIcon?.beVisible()
                            }

                            "Podcasts", "Podcast" -> {
                                itemAdditionalIcon?.setImageDrawable(resources.getDrawable(R.drawable.ic_podcast))
                                itemAdditionalIcon?.beVisible()
                            }

                            "Recordings", "Recording" -> {
                                itemAdditionalIcon?.setImageDrawable(resources.getDrawable(R.drawable.ic_microphone_lines))
                                itemAdditionalIcon?.beVisible()
                            }

                            "Alarms", "Notifications", "Ringtones" -> {
                                itemAdditionalIcon?.setImageDrawable(resources.getDrawable(R.drawable.ic_bell))
                                itemAdditionalIcon?.beVisible()
                            }
                            else -> itemAdditionalIcon?.beGone()
                        }
                    } else if (fileName == "Download") {
                        itemAdditionalIcon?.setImageDrawable(resources.getDrawable(R.drawable.ic_download))
                        itemAdditionalIcon?.beVisible()
                    } else {
                        itemAdditionalIcon?.beGone()
                    }
                } else {
                    itemDetails?.text = listItem.modified.formatDate(activity, dateFormat, timeFormat) + " – " + listItem.size.formatSize()
                    itemDetails?.beGoneIf(config.showOnlyFilename)
//                    item_date?.beGone()
//                    itemDetails?.beVisible()
//                    itemDetails?.text = listItem.modified.formatDate(activity, dateFormat, timeFormat)

                    val drawable = fileDrawables.getOrElse(fileName.substringAfterLast(".").lowercase(Locale.getDefault()), { fileDrawable })
                    val options = RequestOptions()
                        .signature(listItem.getKey())
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                        .error(drawable)
                        .transform(CenterCrop(), RoundedCorners(20))

                    val itemToLoad = getImagePathToLoad(listItem.path)
                    if (!activity.isDestroyed && itemIcon != null) {
                        Glide.with(activity)
                            .load(itemToLoad)
                            .transition(DrawableTransitionOptions.withCrossFade())
                            .apply(options)
                            .into(itemIcon!!)
                    }
                }

                if (config.useDividers) {
                    divider?.setBackgroundColor(textColor)
                    divider?.beVisible()
                } else divider?.beInvisible()

                //swipe
                if (activity.config.useSwipeToAction && itemSwipe != null) {
                    itemHolder?.setupViewBackground(activity)
                    itemFrame.setBackgroundColor(backgroundColor)

                    val isRTL = activity.isRTLLayout
                    val swipeLeftAction = if (isRTL) activity.config.swipeRightAction else activity.config.swipeLeftAction
                    swipeLeftIcon!!.setImageResource(swipeActionImageResource(swipeLeftAction))
                    swipeLeftIcon!!.setColorFilter(properPrimaryColor.getContrastColor())
                    swipeLeftIconHolder!!.setBackgroundColor(swipeActionColor(swipeLeftAction))

                    val swipeRightAction = if (isRTL) activity.config.swipeLeftAction else activity.config.swipeRightAction
                    swipeRightIcon!!.setImageResource(swipeActionImageResource(swipeRightAction))
                    swipeRightIcon!!.setColorFilter(properPrimaryColor.getContrastColor())
                    swipeRightIconHolder!!.setBackgroundColor(swipeActionColor(swipeRightAction))

                    if (activity.config.swipeRipple) {
                        itemSwipe!!.setRippleColor(SwipeDirection.Left, swipeActionColor(swipeLeftAction))
                        itemSwipe!!.setRippleColor(SwipeDirection.Right, swipeActionColor(swipeRightAction))
                    }

                    val fileColumnCnt = activity.config.fileColumnCnt
                    if (viewType == VIEW_TYPE_GRID && fileColumnCnt > 1) {
                        val width =
                            (Resources.getSystem().displayMetrics.widthPixels / fileColumnCnt / 2.5).toInt()
                        swipeLeftIconHolder!!.setWidth(width)
                        swipeRightIconHolder!!.setWidth(width)
                    }

                    itemSwipe!!.useHapticFeedback = activity.config.swipeVibration
                    itemSwipe!!.swipeGestureListener = object : SwipeGestureListener {
                        override fun onSwipedLeft(swipeActionView: SwipeActionView): Boolean {
                            finishActMode()
                            val swipeLeftOrRightAction = if (activity.isRTLLayout) activity.config.swipeRightAction else activity.config.swipeLeftAction
                            swipeAction(swipeLeftOrRightAction, listItem.path)
                            return true
                        }

                        override fun onSwipedRight(swipeActionView: SwipeActionView): Boolean {
                            finishActMode()
                            val swipeRightOrLeftAction = if (activity.isRTLLayout) activity.config.swipeLeftAction else activity.config.swipeRightAction
                            swipeAction(swipeRightOrLeftAction, listItem.path)
                            return true
                        }
                    }
                }
            }
        }
    }

    private fun getChildrenCnt(item: FileDirItem): String {
        val children = item.children
        return activity.resources.getQuantityString(R.plurals.items, children, children)
    }

    private fun getOTGPublicPath(itemToLoad: String) =
        "${baseConfig.OTGTreeUri}/document/${baseConfig.OTGPartition}%3A${itemToLoad.substring(baseConfig.OTGPath.length).replace("/", "%2F")}"

    private fun getImagePathToLoad(path: String): Any {
        var itemToLoad = if (path.endsWith(".apk", true)) {
            val packageInfo = activity.packageManager.getPackageArchiveInfo(path, PackageManager.GET_ACTIVITIES)
            if (packageInfo != null) {
                val appInfo = packageInfo.applicationInfo
                appInfo.sourceDir = path
                appInfo.publicSourceDir = path
                appInfo.loadIcon(activity.packageManager)
            } else {
                path
            }
        } else {
            path
        }

        if (activity.isRestrictedSAFOnlyRoot(path)) {
            itemToLoad = activity.getAndroidSAFUri(path)
        } else if (hasOTGConnected && itemToLoad is String && activity.isPathOnOTG(itemToLoad) && baseConfig.OTGTreeUri.isNotEmpty() && baseConfig.OTGPartition.isNotEmpty()) {
            itemToLoad = getOTGPublicPath(itemToLoad)
        }

        return itemToLoad
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    fun initDrawables() {
        folderDrawable = resources.getColoredDrawableWithColor(R.drawable.ic_folder_color, accentColor)
        folderDrawable.alpha = 180
        fileDrawable = resources.getDrawable(R.drawable.ic_file_generic)
        fileDrawables = getFilePlaceholderDrawables(activity)
    }

    override fun onChange(position: Int) = listItems.getOrNull(position)?.getBubbleText(activity, dateFormat, timeFormat) ?: ""

    private sealed interface Binding {
        companion object {
            fun getByItemViewType(viewType: Int, isListViewType: Boolean, useSwipeToAction: Boolean): Binding {
                return when (viewType) {
                    TYPE_SECTION -> ItemSection
                    TYPE_GRID_TYPE_DIVIDER -> ItemEmpty
                    else -> {
                        if (isListViewType) {
                            if (useSwipeToAction) ItemFileDirListSwipe else ItemFileDirList
                        } else if (viewType == TYPE_DIR) {
                            if (useSwipeToAction) ItemDirGridSwipe else ItemDirGrid
                        } else {
                            if (useSwipeToAction) ItemFileGridSwipe else ItemFileGrid
                        }
                    }
                }
            }
        }

        fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding
        fun bind(view: View): ItemViewBinding

        data object ItemSection : Binding {
            override fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding {
                return ItemSectionBindingAdapter(ItemSectionBinding.inflate(layoutInflater, viewGroup, attachToRoot))
            }

            override fun bind(view: View): ItemViewBinding {
                return ItemSectionBindingAdapter(ItemSectionBinding.bind(view))
            }
        }

        data object ItemEmpty : Binding {
            override fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding {
                return ItemEmptyBindingAdapter(ItemEmptyBinding.inflate(layoutInflater, viewGroup, attachToRoot))
            }

            override fun bind(view: View): ItemViewBinding {
                return ItemEmptyBindingAdapter(ItemEmptyBinding.bind(view))
            }
        }

        data object ItemFileDirList : Binding {
            override fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding {
                return ItemFileDirListBindingAdapter(ItemFileDirListBinding.inflate(layoutInflater, viewGroup, attachToRoot))
            }

            override fun bind(view: View): ItemViewBinding {
                return ItemFileDirListBindingAdapter(ItemFileDirListBinding.bind(view))
            }
        }

        data object ItemFileDirListSwipe : Binding {
            override fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding {
                return ItemFileDirListSwipeBindingAdapter(ItemFileDirListSwipeBinding.inflate(layoutInflater, viewGroup, attachToRoot))
            }

            override fun bind(view: View): ItemViewBinding {
                return ItemFileDirListSwipeBindingAdapter(ItemFileDirListSwipeBinding.bind(view))
            }
        }

        data object ItemDirGrid : Binding {
            override fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding {
                return ItemDirGridBindingAdapter(ItemDirGridBinding.inflate(layoutInflater, viewGroup, attachToRoot))
            }

            override fun bind(view: View): ItemViewBinding {
                return ItemDirGridBindingAdapter(ItemDirGridBinding.bind(view))
            }
        }

        data object ItemDirGridSwipe : Binding {
            override fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding {
                return ItemDirGridSwipeBindingAdapter(ItemDirGridSwipeBinding.inflate(layoutInflater, viewGroup, attachToRoot))
            }

            override fun bind(view: View): ItemViewBinding {
                return ItemDirGridSwipeBindingAdapter(ItemDirGridSwipeBinding.bind(view))
            }
        }

        data object ItemFileGrid : Binding {
            override fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding {
                return ItemFileGridBindingAdapter(ItemFileGridBinding.inflate(layoutInflater, viewGroup, attachToRoot))
            }

            override fun bind(view: View): ItemViewBinding {
                return ItemFileGridBindingAdapter(ItemFileGridBinding.bind(view))
            }
        }

        data object ItemFileGridSwipe : Binding {
            override fun inflate(layoutInflater: LayoutInflater, viewGroup: ViewGroup, attachToRoot: Boolean): ItemViewBinding {
                return ItemFileGridSwipeBindingAdapter(ItemFileGridSwipeBinding.inflate(layoutInflater, viewGroup, attachToRoot))
            }

            override fun bind(view: View): ItemViewBinding {
                return ItemFileGridSwipeBindingAdapter(ItemFileGridSwipeBinding.bind(view))
            }
        }
    }

    private interface ItemViewBinding : ViewBinding {
        val itemFrame: FrameLayout
        val itemName: TextView?
        val itemIcon: ImageView?
        val itemCheck: ImageView?
        val itemDetails: TextView?
        val itemDate: TextView?
        val itemSection: TextView?
        val itemAdditionalIcon: ImageView?
        val chevron: ImageView?
        val divider: ImageView?
        val itemSwipe: SwipeActionView?
        val itemHolder: ConstraintLayout?
        val swipeLeftIconHolder: RelativeLayout?
        val swipeRightIconHolder: RelativeLayout?
        val swipeLeftIcon: ImageView?
        val swipeRightIcon: ImageView?
    }

    private class ItemSectionBindingAdapter(val binding: ItemSectionBinding) : ItemViewBinding {
        override val itemFrame: FrameLayout = binding.itemFrame
        override val itemName: TextView? = null
        override val itemIcon: ImageView = binding.itemIcon
        override val itemDetails: TextView? = null
        override val itemDate: TextView? = null
        override val itemCheck: ImageView? = null
        override val itemSection: TextView = binding.itemSection
        override val itemAdditionalIcon: ImageView? = null
        override val chevron: ImageView? = null
        override val divider: ImageView? = null
        override val itemSwipe: SwipeActionView? = null
        override val itemHolder: ConstraintLayout? = null
        override val swipeLeftIconHolder: RelativeLayout? = null
        override val swipeRightIconHolder: RelativeLayout? = null
        override val swipeLeftIcon: ImageView? = null
        override val swipeRightIcon: ImageView? = null

        override fun getRoot(): View = binding.root
    }

    private class ItemEmptyBindingAdapter(val binding: ItemEmptyBinding) : ItemViewBinding {
        override val itemFrame: FrameLayout = binding.itemFrame
        override val itemName: TextView? = null
        override val itemIcon: ImageView? = null
        override val itemDetails: TextView? = null
        override val itemDate: TextView? = null
        override val itemCheck: ImageView? = null
        override val itemSection: TextView? = null
        override val itemAdditionalIcon: ImageView? = null
        override val chevron: ImageView? = null
        override val divider: ImageView? = null
        override val itemSwipe: SwipeActionView? = null
        override val itemHolder: ConstraintLayout? = null
        override val swipeLeftIconHolder: RelativeLayout? = null
        override val swipeRightIconHolder: RelativeLayout? = null
        override val swipeLeftIcon: ImageView? = null
        override val swipeRightIcon: ImageView? = null

        override fun getRoot(): View = binding.root
    }

    private class ItemFileDirListBindingAdapter(val binding: ItemFileDirListBinding) : ItemViewBinding {
        override val itemFrame: FrameLayout = binding.itemFrame
        override val itemName: TextView = binding.itemName
        override val itemIcon: ImageView = binding.itemIcon
        override val itemDetails: TextView = binding.itemDetails
        override val itemDate: TextView = binding.itemDate
        override val itemCheck: ImageView? = null
        override val itemSection: TextView? = null
        override val itemAdditionalIcon: ImageView = binding.itemAdditionalIcon
        override val chevron: ImageView = binding.chevron
        override val divider: ImageView = binding.divider
        override val itemSwipe: SwipeActionView? = null
        override val itemHolder: ConstraintLayout = binding.itemHolder
        override val swipeLeftIconHolder: RelativeLayout? = null
        override val swipeRightIconHolder: RelativeLayout? = null
        override val swipeLeftIcon: ImageView? = null
        override val swipeRightIcon: ImageView? = null

        override fun getRoot(): View = binding.root
    }

    private class ItemFileDirListSwipeBindingAdapter(val binding: ItemFileDirListSwipeBinding) : ItemViewBinding {
        override val itemFrame: FrameLayout = binding.itemFrame
        override val itemName: TextView = binding.itemName
        override val itemIcon: ImageView = binding.itemIcon
        override val itemDetails: TextView = binding.itemDetails
        override val itemDate: TextView = binding.itemDate
        override val itemCheck: ImageView? = null
        override val itemSection: TextView? = null
        override val itemAdditionalIcon: ImageView = binding.itemAdditionalIcon
        override val chevron: ImageView = binding.chevron
        override val divider: ImageView = binding.divider
        override val itemSwipe: SwipeActionView = binding.itemSwipe
        override val itemHolder: ConstraintLayout = binding.itemHolder
        override val swipeLeftIconHolder: RelativeLayout = binding.swipeLeftIconHolder
        override val swipeRightIconHolder: RelativeLayout = binding.swipeRightIconHolder
        override val swipeLeftIcon: ImageView = binding.swipeLeftIcon
        override val swipeRightIcon: ImageView = binding.swipeRightIcon

        override fun getRoot(): View = binding.root
    }

    private class ItemDirGridBindingAdapter(val binding: ItemDirGridBinding) : ItemViewBinding {
        override val itemFrame: FrameLayout = binding.itemFrame
        override val itemName: TextView = binding.itemName
        override val itemIcon: ImageView = binding.itemIcon
        override val itemDetails: TextView? = null
        override val itemDate: TextView? = null
        override val itemCheck: ImageView = binding.itemCheck
        override val itemSection: TextView? = null
        override val itemAdditionalIcon: ImageView = binding.itemAdditionalIcon
        override val chevron: ImageView? = null
        override val divider: ImageView? = null
        override val itemSwipe: SwipeActionView? = null
        override val itemHolder: ConstraintLayout? = null
        override val swipeLeftIconHolder: RelativeLayout? = null
        override val swipeRightIconHolder: RelativeLayout? = null
        override val swipeLeftIcon: ImageView? = null
        override val swipeRightIcon: ImageView? = null

        override fun getRoot(): View = binding.root
    }

    private class ItemDirGridSwipeBindingAdapter(val binding: ItemDirGridSwipeBinding) : ItemViewBinding {
        override val itemFrame: FrameLayout = binding.itemFrame
        override val itemName: TextView = binding.itemName
        override val itemIcon: ImageView = binding.itemIcon
        override val itemDetails: TextView? = null
        override val itemDate: TextView? = null
        override val itemCheck: ImageView = binding.itemCheck
        override val itemSection: TextView? = null
        override val itemAdditionalIcon: ImageView = binding.itemAdditionalIcon
        override val chevron: ImageView? = null
        override val divider: ImageView? = null
        override val itemSwipe: SwipeActionView = binding.itemSwipe
        override val itemHolder: ConstraintLayout = binding.itemHolder
        override val swipeLeftIconHolder: RelativeLayout = binding.swipeLeftIconHolder
        override val swipeRightIconHolder: RelativeLayout = binding.swipeRightIconHolder
        override val swipeLeftIcon: ImageView = binding.swipeLeftIcon
        override val swipeRightIcon: ImageView = binding.swipeRightIcon

        override fun getRoot(): View = binding.root
    }

    private class ItemFileGridBindingAdapter(val binding: ItemFileGridBinding) : ItemViewBinding {
        override val itemFrame: FrameLayout = binding.itemFrame
        override val itemName: TextView = binding.itemName
        override val itemIcon: ImageView = binding.itemIcon
        override val itemDetails: TextView? = null
        override val itemDate: TextView? = null
        override val itemCheck: ImageView = binding.itemCheck
        override val itemSection: TextView? = null
        override val itemAdditionalIcon: ImageView? = null
        override val chevron: ImageView? = null
        override val divider: ImageView? = null
        override val itemSwipe: SwipeActionView? = null
        override val itemHolder: ConstraintLayout? = null
        override val swipeLeftIconHolder: RelativeLayout? = null
        override val swipeRightIconHolder: RelativeLayout? = null
        override val swipeLeftIcon: ImageView? = null
        override val swipeRightIcon: ImageView? = null

        override fun getRoot(): View = binding.root
    }

    private class ItemFileGridSwipeBindingAdapter(val binding: ItemFileGridSwipeBinding) : ItemViewBinding {
        override val itemFrame: FrameLayout = binding.itemFrame
        override val itemName: TextView = binding.itemName
        override val itemIcon: ImageView = binding.itemIcon
        override val itemDetails: TextView? = null
        override val itemDate: TextView? = null
        override val itemCheck: ImageView = binding.itemCheck
        override val itemSection: TextView? = null
        override val itemAdditionalIcon: ImageView? = null
        override val chevron: ImageView? = null
        override val divider: ImageView? = null
        override val itemSwipe: SwipeActionView = binding.itemSwipe
        override val itemHolder: ConstraintLayout = binding.itemHolder
        override val swipeLeftIconHolder: RelativeLayout = binding.swipeLeftIconHolder
        override val swipeRightIconHolder: RelativeLayout = binding.swipeRightIconHolder
        override val swipeLeftIcon: ImageView = binding.swipeLeftIcon
        override val swipeRightIcon: ImageView = binding.swipeRightIcon

        override fun getRoot(): View = binding.root
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
    }

    override fun onRowSelected(myViewHolder: ViewHolder?) {
        swipeRefreshLayout?.isEnabled = false
        swipeRefreshLayout?.isRefreshing = false
    }

    override fun onRowClear(myViewHolder: ViewHolder?) {
        swipeRefreshLayout?.isEnabled = activity.config.enablePullToRefresh
        swipeRefreshLayout?.isRefreshing = false
    }

    private fun swipeActionImageResource(swipeAction: Int): Int {
        return when (swipeAction) {
            SWIPE_ACTION_DELETE -> R.drawable.ic_delete_outline
            SWIPE_ACTION_MOVE -> R.drawable.ic_move_vector
            SWIPE_ACTION_INFO -> R.drawable.ic_info_vector
            else -> R.drawable.ic_copy_vector
        }
    }

    private fun swipeActionColor(swipeAction: Int): Int {
        return when (swipeAction) {
            SWIPE_ACTION_DELETE -> resources.getColor(R.color.red_missed, activity.theme)
            SWIPE_ACTION_MOVE -> resources.getColor(R.color.swipe_purple, activity.theme)
            SWIPE_ACTION_INFO -> resources.getColor(R.color.yellow_ios, activity.theme)
            else -> resources.getColor(R.color.primary, activity.theme)
        }
    }

    private fun swipeAction(swipeAction: Int, path: String) {
        when (swipeAction) {
            SWIPE_ACTION_DELETE -> swipedDelete(path)
            SWIPE_ACTION_MOVE -> swipedCopyMoveTo(false, path)
            SWIPE_ACTION_INFO -> swipeShowProperties(path)
            else -> swipedCopyMoveTo(true, path)
        }
    }

    private fun swipeShowProperties(path: String) {
        PropertiesDialog(activity, path, config.shouldShowHidden())
    }

    private fun swipedCopyMoveTo(isCopyOperation: Boolean, path: String) {
        val files = listItems.filter { it.path.hashCode() == path.hashCode() } as ArrayList<FileDirItem>
        val firstFile = files[0]
        val source = firstFile.getParentPath()
        val titleText = if (isCopyOperation) R.string.copy_to else R.string.move_to
        FilePickerDialog(
            activity,
            activity.getDefaultCopyDestinationPath(config.shouldShowHidden(), source),
            false,
            config.shouldShowHidden(),
            true,
            true,
            showFavoritesButton = true,
            titleText = titleText,
            useAccentColor = true
        ) { it ->
            config.lastCopyPath = it
            if (files.any { file -> it.contains(file.path) }) {
                activity.toast(R.string.cannot_be_inserted_into_itself, Toast.LENGTH_LONG)
                return@FilePickerDialog
            }
            if (activity.isPathOnRoot(it) || activity.isPathOnRoot(firstFile.path)) {
                copyMoveRootItems(files, it, isCopyOperation)
            } else {
                activity.copyMoveFilesTo(files, source, it, isCopyOperation, false, config.shouldShowHidden()) {
                    if (!isCopyOperation) {
                        files.forEach { sourceFileDir ->
                            val sourcePath = sourceFileDir.path
                            if (activity.isRestrictedSAFOnlyRoot(sourcePath) && activity.getDoesFilePathExist(sourcePath)) {
                                activity.deleteFile(sourceFileDir, true) {
                                    listener?.refreshFragment()
                                }
                            } else {
                                val sourceFile = File(sourcePath)
                                if (activity.getDoesFilePathExist(source) && activity.getIsPathDirectory(source) &&
                                    sourceFile.list()?.isEmpty() == true && sourceFile.getProperSize(true) == 0L && sourceFile.getFileCount(true) == 0
                                ) {
                                    val sourceFolder = sourceFile.toFileDirItem(activity)
                                    activity.deleteFile(sourceFolder, true) {
                                        listener?.refreshFragment()
                                    }
                                } else {
                                    listener?.refreshFragment()
                                }
                            }
                        }
                    } else {
                        listener?.refreshFragment()
                    }
                }
            }
        }
    }

    private fun swipedDelete(path: String) {
        if (activity.config.skipDeleteConfirmation) {
            swipedDeleteFiles(path)
        } else swipedAskConfirmDelete(path)
    }

    private fun swipedAskConfirmDelete(path: String) {
        activity.handleDeletePasswordProtection {
            val item = "\"${path.getFilenameFromPath()}\""

            val question = String.format(resources.getString(R.string.deletion_confirmation), item)
            ConfirmationDialog(activity, question) {
                swipedDeleteFiles(path)
            }
        }
    }

    private fun swipedDeleteFiles(path: String) {
        val SAFPath = path
        if (activity.isPathOnRoot(SAFPath) && !RootTools.isRootAvailable()) {
            activity.toast(R.string.rooted_device_only)
            return
        }

        activity.handleSAFDialog(SAFPath) {
            if (!it) {
                return@handleSAFDialog
            }

            val files = ArrayList<FileDirItem>(selectedKeys.size)
            val positions = ArrayList<Int>()
            val key = path.hashCode()
            config.removeFavorite(getItemWithKey(key)?.path ?: "")
            val position = listItems.indexOfFirst { it.path.hashCode() == key }
            if (position != -1) {
                positions.add(position)
                files.add(listItems[position])
            }

            positions.sortDescending()
            removeSelectedItems(positions)
            listener?.deleteFiles(files)
            positions.forEach {
                listItems.removeAt(it)
            }
        }
    }

    private fun showPopupMenu(view: View, listItem: ListItem) {
        finishActMode()
        val theme = activity.getPopupMenuTheme()
        val contextTheme = ContextThemeWrapper(activity, theme)

        val itemKey = listItem.path.hashCode()
        val isOneFileSelected = getItemWithKey(itemKey)?.isDirectory == false
        val isHiddenItem = listItem.name.startsWith(".")

        PopupMenu(contextTheme, view, Gravity.START).apply {
            inflate(R.menu.menu_item_options)
            menu.apply {
                findItem(R.id.cab_decompress).isVisible = listItem.path.isZipFile()
                findItem(R.id.cab_open_with).isVisible = isOneFileSelected
                findItem(R.id.cab_set_as).isVisible = isOneFileSelected
                findItem(R.id.cab_create_shortcut).isVisible = isOreoPlus()// && isOneItemSelected()
                findItem(R.id.cab_hide).isVisible = !isHiddenItem
                findItem(R.id.cab_unhide).isVisible = isHiddenItem
            }
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.cab_rename -> {
                        executeItemMenuOperation(itemKey) {
                            displayRenameDialog()
                        }
                    }
                    R.id.cab_properties -> {
                        executeItemMenuOperation(itemKey) {
                            showProperties()
                        }
                    }
                    R.id.cab_share -> {
                        executeItemMenuOperation(itemKey) {
                            shareFiles()
                        }
                    }
                    R.id.cab_hide -> {
                        selectedKeys.add(itemKey)
                        toggleFileVisibility(true)
                    }
                    R.id.cab_unhide -> {
                        selectedKeys.add(itemKey)
                        toggleFileVisibility(false)
                    }
                    R.id.cab_create_shortcut -> {
                        executeItemMenuOperation(itemKey) {
                            createShortcut()
                        }
                    }
                    R.id.cab_copy_path -> {
                        executeItemMenuOperation(itemKey) {
                            copyPath()
                        }
                    }
                    R.id.cab_set_as -> {
                        executeItemMenuOperation(itemKey) {
                            setAs()
                        }
                    }
                    R.id.cab_open_with -> {
                        executeItemMenuOperation(itemKey) {
                            openWith()
                        }
                    }
                    R.id.cab_copy_to -> {
                        swipedCopyMoveTo(true, listItem.path)
                    }
                    R.id.cab_move_to -> {
                        swipedCopyMoveTo(false, listItem.path)
                    }
                    R.id.cab_compress -> {
                        compressSelection(listItem.path)
                    }
                    R.id.cab_decompress -> {
                        decompressSelection(listItem.path)
                    }
                    R.id.cab_delete -> {
                        swipedDelete(listItem.path)
                    }
                }
                true
            }

//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                setForceShowIcon(true)
//                menu.apply {
//                    for (index in 0 until this.size()) {
//                        val item = this.getItem(index)
//
//                        item.icon!!.colorFilter = BlendModeColorFilter(
//                            textColor, BlendMode.SRC_IN
//                        )
//                    }
//                }
//            }
            show()
        }
    }

    private fun executeItemMenuOperation(itemKey: Int, callback: () -> Unit) {
        selectedKeys.clear()
        selectedKeys.add(itemKey)
        callback()
        selectedKeys.remove(itemKey)
    }
}
