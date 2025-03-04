package com.goodwy.filemanager.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AppOpsManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.Settings
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ScrollingView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.goodwy.commons.dialogs.ConfirmationAdvancedDialog
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.FAQItem
import com.goodwy.commons.models.RadioItem
import com.goodwy.commons.models.Release
import com.goodwy.commons.views.MySearchMenu
import com.goodwy.filemanager.BuildConfig
import com.goodwy.filemanager.R
import com.goodwy.filemanager.adapters.ViewPagerAdapter
import com.goodwy.filemanager.databinding.ActivityMainBinding
import com.goodwy.filemanager.databinding.ItemStorageVolumeBinding
import com.goodwy.filemanager.dialogs.ChangeSortingDialog
import com.goodwy.filemanager.dialogs.ChangeViewTypeDialog
import com.goodwy.filemanager.dialogs.InsertFilenameDialog
import com.goodwy.filemanager.extensions.config
import com.goodwy.filemanager.extensions.launchAbout
import com.goodwy.filemanager.extensions.tryOpenPathIntent
import com.goodwy.filemanager.fragments.ItemsFragment
import com.goodwy.filemanager.fragments.MyViewPagerFragment
import com.goodwy.filemanager.fragments.RecentsFragment
import com.goodwy.filemanager.fragments.StorageFragment
import com.goodwy.filemanager.helpers.FOLDER_HOME
import com.goodwy.filemanager.helpers.FOLDER_INTERNAL
import com.goodwy.filemanager.helpers.MAX_COLUMN_COUNT
import com.goodwy.filemanager.helpers.RootHelpers
import com.goodwy.filemanager.interfaces.ItemOperationsListener
import com.stericson.RootTools.RootTools
import me.grantland.widget.AutofitHelper
import java.io.File

class MainActivity : SimpleActivity() {
    companion object {
        private const val BACK_PRESS_TIMEOUT = 5000
        private const val MANAGE_STORAGE_RC = 201
        private const val USAGE_STATS_RC = 202
        private const val PICKED_PATH = "picked_path"
    }

    private val binding by viewBinding(ActivityMainBinding::inflate)

    private var wasBackJustPressed = false
    private var mTabsToShow = ArrayList<Int>()
    private var mHasStoragePermission = false

    private var mStoredFontSize = 0
    private var mStoredDateFormat = ""
    private var mStoredTimeFormat = ""
    private var mStoredShowTabs = 0
    private var currentOldScrollY = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
        refreshMenuItems()
        config.tabsChanged = false
        mTabsToShow = getTabsList()

        if (!config.wasStorageAnalysisTabAdded && isOreoPlus()) {
            config.wasStorageAnalysisTabAdded = true
            if (config.showTabs and TAB_STORAGE_ANALYSIS == 0) {
                config.showTabs += TAB_STORAGE_ANALYSIS
            }
        }

        storeStateVariables()
        setupTabs()

        updateMaterialActivityViews(binding.mainCoordinator, null, useTransparentNavigation = false, useTopSearchMenu = true)
        binding.mainMenu.updateTitle(getString(R.string.app_launcher_name_g))

        if (savedInstanceState == null) {
            initFragments()
            tryInitFileManager()
            checkWhatsNewDialog()
            checkIfRootAvailable()
            checkInvalidFavorites()
        }
    }

    override fun onResume() {
        super.onResume()
        if (mStoredShowTabs != config.showTabs || config.tabsChanged) {
            config.lastUsedViewPagerPage = 0
            finish()
            startActivity(intent)
            return
        }

        refreshMenuItems()
        setupTabColors()
        binding.mainMenu.updateColors(getStartRequiredStatusBarColor(), scrollingView?.computeVerticalScrollOffset() ?: 0)

        getAllFragments().forEach {
            it?.onResume(getProperTextColor())
            it?.setBackgroundColor(getProperBackgroundColor())
        }

        if (mStoredFontSize != config.fontSize) {
            getAllFragments().forEach {
                (it as? ItemOperationsListener)?.setupFontSize()
            }
        }

        if (mStoredDateFormat != config.dateFormat || mStoredTimeFormat != getTimeFormat()) {
            getAllFragments().forEach {
                (it as? ItemOperationsListener)?.setupDateTimeFormat()
            }
        }

        if (binding.mainViewPager.adapter == null) {
            initFragments()
        }

        //Screen slide animation
        val animation = when (config.screenSlideAnimation) {
            1 -> ZoomOutPageTransformer()
            2 -> DepthPageTransformer()
            else -> null
        }
        binding.mainViewPager.setPageTransformer(true, animation)
        binding.mainViewPager.setPagingEnabled(!config.useSwipeToAction)
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
        config.lastUsedViewPagerPage = binding.mainViewPager.currentItem
        config.tabsChanged = false
        val getCurrentFragment = getCurrentFragment()
        if (getCurrentFragment is ItemsFragment) config.lastFolder = getCurrentFragment.currentPath
    }

    override fun onDestroy() {
        super.onDestroy()
        config.temporarilyShowHidden = false
        config.tabsChanged = false
        val getCurrentFragment = getCurrentFragment()
        if (getCurrentFragment is ItemsFragment) config.lastFolder = getCurrentFragment.currentPath
    }

    override fun onBackPressed() {
        val currentFragment = getCurrentFragment()
        if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
        } else if (currentFragment is RecentsFragment || currentFragment is StorageFragment) {
            appLockManager.lock()
            super.onBackPressed()
        } else if ((currentFragment as ItemsFragment)!!.getBreadcrumbs()!!.getItemCount() <= 1) {
            if (!wasBackJustPressed && config.pressBackTwice) {
                wasBackJustPressed = true
                toast(R.string.press_back_again)
                Handler().postDelayed({
                    wasBackJustPressed = false
                }, BACK_PRESS_TIMEOUT.toLong())
            } else {
                appLockManager.lock()
                finish()
            }
        } else {
            currentFragment.getBreadcrumbs().removeBreadcrumb()
            openPath(currentFragment.getBreadcrumbs().getLastItem().path)
        }
    }

    fun refreshMenuItems() {
        val currentFragment = getCurrentFragment() ?: return
        val isCreateDocumentIntent = intent.action == Intent.ACTION_CREATE_DOCUMENT
        val currentViewType = config.getFolderViewType(currentFragment.currentPath)
        val favorites = config.favorites

        binding.mainMenu.getToolbar().menu.apply {
            findItem(R.id.sort).isVisible = currentFragment is ItemsFragment
            findItem(R.id.change_view_type).isVisible = currentFragment !is StorageFragment
            findItem(R.id.change_view_type).setIcon(getViewTypeIcon())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val properTextColor = getProperTextColor()
                findItem(R.id.change_view_type).iconTintList = ColorStateList.valueOf(properTextColor)
            }

            findItem(R.id.add_favorite).isVisible = currentFragment is ItemsFragment && !favorites.contains(currentFragment.currentPath)
            findItem(R.id.remove_favorite).isVisible = currentFragment is ItemsFragment && favorites.contains(currentFragment.currentPath)
            findItem(R.id.go_to_favorite).isVisible = currentFragment is ItemsFragment && favorites.isNotEmpty()

            findItem(R.id.toggle_filename).isVisible = currentViewType == VIEW_TYPE_GRID && currentFragment !is StorageFragment
            findItem(R.id.go_home).isVisible =
                currentFragment is ItemsFragment && currentFragment.currentPath != config.homeFolder && config.showHomeButton
                    && currentFragment.currentPath != "" //so that it doesn't appear for a moment when the app is launched
            findItem(R.id.set_as_home).isVisible = currentFragment is ItemsFragment && currentFragment.currentPath != config.homeFolder

            findItem(R.id.temporarily_show_hidden).isVisible = !config.shouldShowHidden() && currentFragment !is StorageFragment
            findItem(R.id.stop_showing_hidden).isVisible = config.temporarilyShowHidden && currentFragment !is StorageFragment

            findItem(R.id.column_count).isVisible = currentViewType == VIEW_TYPE_GRID && currentFragment !is StorageFragment

            //findItem(R.id.more_apps_from_us).isVisible = !resources.getBoolean(R.bool.hide_google_relations)
            findItem(R.id.settings).isVisible = !isCreateDocumentIntent
            findItem(R.id.about).isVisible = !isCreateDocumentIntent
        }
    }

    private fun setViewTypeIcon() {
        binding.mainMenu.getToolbar().menu.findItem(R.id.change_view_type).apply {
            setIcon(getViewTypeIcon())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val properTextColor = getProperTextColor()
                iconTintList = ColorStateList.valueOf(properTextColor)
            }
        }
    }

    private fun getViewTypeIcon(): Int {
        return when (config.viewType) {
            VIEW_TYPE_GRID -> R.drawable.ic_list_view
            else -> R.drawable.ic_grid_view
        }
    }

    private fun setupOptionsMenu() {
        binding.mainMenu.apply {
            getToolbar().inflateMenu(R.menu.menu)
            toggleHideOnScroll(false)
            setupMenu()

            onSearchClosedListener = {
                getAllFragments().forEach {
                    it?.searchQueryChanged("")
                }
            }

            onSearchTextChangedListener = { text ->
                getCurrentFragment()?.searchQueryChanged(text)
            }

            getToolbar().setOnMenuItemClickListener { menuItem ->
                if (getCurrentFragment() == null) {
                    return@setOnMenuItemClickListener true
                }

                when (menuItem.itemId) {
                    R.id.go_home -> goHome()
                    R.id.go_to_favorite -> goToFavorite()
                    R.id.sort -> showSortingDialog()
                    R.id.add_favorite -> addFavorite()
                    R.id.remove_favorite -> removeFavorite()
                    R.id.toggle_filename -> toggleFilenameVisibility()
                    R.id.set_as_home -> setAsHome()
                    R.id.change_view_type -> changeViewType()
                    R.id.temporarily_show_hidden -> tryToggleTemporarilyShowHidden()
                    R.id.stop_showing_hidden -> tryToggleTemporarilyShowHidden()
                    R.id.column_count -> changeColumnCount()
                    R.id.more_apps_from_us -> launchMoreAppsFromUsIntent()
                    R.id.settings -> launchSettings()
                    R.id.about -> launchAbout()
                    else -> return@setOnMenuItemClickListener false
                }
                return@setOnMenuItemClickListener true
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(PICKED_PATH, getItemsFragment()?.currentPath ?: "")
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val path = savedInstanceState.getString(PICKED_PATH) ?: internalStoragePath

        if (binding.mainViewPager.adapter == null) {
            binding.mainViewPager.onGlobalLayout {
                restorePath(path)
            }
        } else {
            restorePath(path)
        }
    }

    @SuppressLint("NewApi")
    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        isAskingPermissions = false
        if (requestCode == MANAGE_STORAGE_RC && isRPlus()) {
            actionOnPermission?.invoke(Environment.isExternalStorageManager())
        }
        if (requestCode == USAGE_STATS_RC) {
            if (checkAppOpsService()) {
                finish()
                startActivity(intent)
                return
            } else appOpsServiceDialog()
        }
    }

    private fun restorePath(path: String) {
        openPath(path, true)
    }

    private fun storeStateVariables() {
        config.apply {
            mStoredFontSize = fontSize
            mStoredDateFormat = dateFormat
            mStoredTimeFormat = context.getTimeFormat()
            mStoredShowTabs = showTabs
        }
    }

    private fun tryInitFileManager() {
        val hadPermission = hasStoragePermission()
        handleStoragePermission {
            checkOTGPath()
            if (it) {
                mHasStoragePermission = true
                if (binding.mainViewPager.adapter == null) {
                    initFragments()
                }

                binding.mainViewPager.onGlobalLayout {
                    initFileManager(!hadPermission)
                }
            } else {
                toast(R.string.no_storage_permissions)
                finish()
            }
        }
    }

    @SuppressLint("InlinedApi")
    private fun handleStoragePermission(callback: (granted: Boolean) -> Unit) {
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
                            intent.data = Uri.parse("package:$packageName")
                            startActivityForResult(intent, MANAGE_STORAGE_RC)
                        } catch (e: Exception) {
                            showErrorToast(e)
                            val intent = Intent()
                            intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                            startActivityForResult(intent, MANAGE_STORAGE_RC)
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

    private fun initFileManager(refreshRecents: Boolean) {
        val defaultFolder = when(config.defaultFolder) {
            FOLDER_HOME -> config.homeFolder
            FOLDER_INTERNAL -> internalStoragePath
            else -> config.lastFolder
        }
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            val data = intent.data
            if (data?.scheme == "file") {
                openPath(data.path!!)
            } else {
                val path = getRealPathFromURI(data!!)
                if (path != null) {
                    openPath(path)
                } else {
                    openPath(defaultFolder)
                }
            }

            if (!File(data.path!!).isDirectory) {
                tryOpenPathIntent(data.path!!, false, finishActivity = true)
            }

            binding.mainViewPager.currentItem = 0
        } else {
            openPath(defaultFolder)
        }

        if (refreshRecents) {
            getRecentsFragment()?.refreshFragment()
        }
    }

    private fun initFragments() {
        binding.mainViewPager.apply {
            adapter = ViewPagerAdapter(this@MainActivity, mTabsToShow)
            offscreenPageLimit = 2
            addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
                override fun onPageScrollStateChanged(state: Int) {}

                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

                override fun onPageSelected(position: Int) {
                    scrollChange()
                    binding.mainTabsHolder.getTabAt(position)?.select()
                    getAllFragments().forEach {
                        (it as? ItemOperationsListener)?.finishActMode()
                    }
                    refreshMenuItems()

                    if (getCurrentFragment() is StorageFragment) {
                        if (!checkAppOpsService() && config.checkAppOpsService) appOpsServiceDialog()
                    }
                }
            })
            currentItem = config.lastUsedViewPagerPage

            onGlobalLayout {
                refreshMenuItems()
                scrollChange()
            }
        }
    }

    private fun scrollChange() {
        val myRecyclerView = getCurrentFragment()?.myRecyclerView()
        scrollingView = myRecyclerView
        val scrollingViewOffset = scrollingView?.computeVerticalScrollOffset() ?: 0
        currentOldScrollY = scrollingViewOffset
        binding.mainMenu.updateColors(getStartRequiredStatusBarColor(), scrollingViewOffset)
        setupSearchMenuScrollListenerNew(myRecyclerView, binding.mainMenu)
    }

    private fun setupSearchMenuScrollListenerNew(scrollingView: ScrollingView?, searchMenu: MySearchMenu) {
        this.scrollingView = scrollingView
        this.mySearchMenu = searchMenu
        if (scrollingView is RecyclerView) {
            scrollingView.setOnScrollChangeListener { _, _, _, _, _ ->
                val newScrollY = scrollingView.computeVerticalScrollOffset()
                if (newScrollY == 0 || currentOldScrollY == 0) scrollingChanged(newScrollY)
                currentScrollY = newScrollY
                currentOldScrollY = currentScrollY
            }
        } else if (scrollingView is NestedScrollView) {
            scrollingView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                if (scrollY == 0 || currentOldScrollY == 0) scrollingChanged(scrollY)
                currentOldScrollY = oldScrollY
            }
        }
    }

    private fun scrollingChanged(newScrollY: Int) {
        if (newScrollY > 0 && currentOldScrollY == 0) {
            val colorFrom = window.statusBarColor
            val colorTo = getColoredMaterialStatusBarColor()
            animateMySearchMenuColors(colorFrom, colorTo)
        } else if (newScrollY == 0 && currentOldScrollY > 0) {
            val colorFrom = window.statusBarColor
            val colorTo = getRequiredStatusBarColor()
            animateMySearchMenuColors(colorFrom, colorTo)
        }
    }

    private fun getStartRequiredStatusBarColor(): Int {
        val scrollingViewOffset = scrollingView?.computeVerticalScrollOffset() ?: 0
        return if (scrollingViewOffset == 0) {
            getProperBackgroundColor()
        } else {
            getColoredMaterialStatusBarColor()
        }
    }

    private fun setupTabs() {
        binding.mainTabsHolder.removeAllTabs()
        val action = intent.action
        val isPickFileIntent = action == RingtoneManager.ACTION_RINGTONE_PICKER || action == Intent.ACTION_GET_CONTENT || action == Intent.ACTION_PICK
        val isCreateDocumentIntent = action == Intent.ACTION_CREATE_DOCUMENT

        if (isPickFileIntent) {
            mTabsToShow.remove(TAB_STORAGE_ANALYSIS)
            if (mTabsToShow.none { it and config.showTabs != 0 }) {
                config.showTabs = TAB_FILES
                mStoredShowTabs = TAB_FILES
                mTabsToShow = arrayListOf(TAB_FILES)
            }
        } else if (isCreateDocumentIntent) {
            mTabsToShow.clear()
            mTabsToShow = arrayListOf(TAB_FILES)
        }

        mTabsToShow.forEachIndexed { index, value ->
            if (config.showTabs and value != 0) {
                binding.mainTabsHolder.newTab().setCustomView(R.layout.bottom_tablayout_item).apply {
                    customView?.findViewById<ImageView>(R.id.tab_item_icon)?.setImageDrawable(getTabIcon(index))
                    customView?.findViewById<TextView>(R.id.tab_item_label)?.apply {
                        text = getTabLabel(index)
                        beGoneIf(config.useIconTabs)
                    }
                    AutofitHelper.create(customView?.findViewById(R.id.tab_item_label))
                    binding.mainTabsHolder.addTab(this)
                }
            }
        }

        binding.mainTabsHolder.apply {
            onTabSelectionChanged(
                tabUnselectedAction = {
                    updateBottomTabItemColors(it.customView, false, getDeselectedTabDrawableIds()[it.position])
                },
                tabSelectedAction = {
                    binding.mainMenu.closeSearch()
                    binding.mainViewPager.currentItem = it.position
                    updateBottomTabItemColors(it.customView, true, getSelectedTabDrawableIds()[it.position])
                    val currentFragment = getCurrentFragment()
                    if (currentFragment is StorageFragment) {
                        currentFragment.refreshFragment()
                    }
                }
            )

            beGoneIf(tabCount == 1)
        }
    }

    private fun setupTabColors() {
        binding.apply {
            val activeView = mainTabsHolder.getTabAt(mainViewPager.currentItem)?.customView
            updateBottomTabItemColors(activeView, true, getSelectedTabDrawableIds()[mainViewPager.currentItem])

            getInactiveTabIndexes(mainViewPager.currentItem).forEach { index ->
                val inactiveView = mainTabsHolder.getTabAt(index)?.customView
                updateBottomTabItemColors(inactiveView, false, getDeselectedTabDrawableIds()[index])
            }

            val bottomBarColor = getBottomNavigationBackgroundColor()
            updateNavigationBarColor(bottomBarColor)
            mainTabsHolder.setBackgroundColor(bottomBarColor)
        }
    }

    private fun getTabIcon(position: Int): Drawable {
        val drawableId = when (position) {
            0 -> R.drawable.ic_clock_filled
            1 -> R.drawable.ic_folder_closed
            else -> R.drawable.ic_storage_vector
        }

        return resources.getColoredDrawableWithColor(drawableId, getProperTextColor())
    }

    private fun getTabLabel(position: Int): String {
        val stringId = when (position) {
            0 -> R.string.recents
            1 -> R.string.files_tab
            else -> R.string.storage
        }

        return resources.getString(stringId)
    }

    private fun checkOTGPath() {
        ensureBackgroundThread {
            if (!config.wasOTGHandled && hasPermission(PERMISSION_WRITE_STORAGE) && hasOTGConnected() && config.OTGPath.isEmpty()) {
                getStorageDirectories().firstOrNull { it.trimEnd('/') != internalStoragePath && it.trimEnd('/') != sdCardPath }?.apply {
                    config.wasOTGHandled = true
                    config.OTGPath = trimEnd('/')
                }
            }
        }
    }

    private fun openPath(path: String, forceRefresh: Boolean = false) {
        var newPath = path
        val file = File(path)
        if (config.OTGPath.isNotEmpty() && config.OTGPath == path.trimEnd('/')) {
            newPath = path
        } else if (file.exists() && !file.isDirectory) {
            newPath = file.parent
        } else if (!file.exists() && !isPathOnOTG(newPath)) {
            newPath = internalStoragePath
        }

        getItemsFragment()?.openPath(newPath, forceRefresh)
    }

    private fun goHome() {
        if (config.homeFolder != getCurrentFragment()!!.currentPath) {
            openPath(config.homeFolder)
        }
    }

    private fun showSortingDialog() {
        ChangeSortingDialog(this, getCurrentFragment()!!.currentPath) {
            (getCurrentFragment() as? ItemsFragment)?.refreshFragment()
        }
    }

    private fun addFavorite() {
        config.addFavorite(getCurrentFragment()!!.currentPath)
        refreshMenuItems()
    }

    private fun removeFavorite() {
        config.removeFavorite(getCurrentFragment()!!.currentPath)
        refreshMenuItems()
    }

    private fun toggleFilenameVisibility() {
        config.displayFilenames = !config.displayFilenames
        getAllFragments().forEach {
            (it as? ItemOperationsListener)?.toggleFilenameVisibility()
        }
    }

    private fun changeColumnCount() {
        val items = ArrayList<RadioItem>()
        for (i in 1..MAX_COLUMN_COUNT) {
            items.add(RadioItem(i, resources.getQuantityString(R.plurals.column_counts, i, i)))
        }

        val currentColumnCount = config.fileColumnCnt
        RadioGroupDialog(this, items, config.fileColumnCnt) {
            val newColumnCount = it as Int
            if (currentColumnCount != newColumnCount) {
                config.fileColumnCnt = newColumnCount
                getAllFragments().forEach {
                    (it as? ItemOperationsListener)?.columnCountChanged()
                }
            }
        }
    }

    fun updateFragmentColumnCounts() {
        getAllFragments().forEach {
            (it as? ItemOperationsListener)?.columnCountChanged()
        }
    }

    private fun goToFavorite() {
        val favorites = config.favorites
        val items = ArrayList<RadioItem>(favorites.size)
        var currFavoriteIndex = -1

        favorites.forEachIndexed { index, path ->
            val visiblePath = humanizePath(path).replace("/", " / ")
            items.add(RadioItem(index, visiblePath, path))
            if (path == getCurrentFragment()!!.currentPath) {
                currFavoriteIndex = index
            }
        }

        RadioGroupDialog(this, items, currFavoriteIndex, R.string.go_to_favorite) {
            openPath(it.toString())
        }
    }

    private fun setAsHome() {
        config.homeFolder = getCurrentFragment()!!.currentPath
        toast(R.string.home_folder_updated)
        refreshMenuItems()
    }

    private fun changeViewType() {
        if (getCurrentFragment() is ItemsFragment) {
            ChangeViewTypeDialog(this, getCurrentFragment()!!.currentPath, getCurrentFragment() is ItemsFragment) {
                getAllFragments().forEach {
                    it?.refreshFragment()
                }
            }
        } else {
            if (config.viewType == VIEW_TYPE_GRID) config.viewType = VIEW_TYPE_LIST else config.viewType = VIEW_TYPE_GRID
            setViewTypeIcon()
            getAllFragments().forEach {
                it?.refreshFragment()
            }
        }
    }

    private fun tryToggleTemporarilyShowHidden() {
        if (config.temporarilyShowHidden) {
            toggleTemporarilyShowHidden(false)
        } else {
            handleHiddenFolderPasswordProtection {
                toggleTemporarilyShowHidden(true)
            }
        }
    }

    private fun toggleTemporarilyShowHidden(show: Boolean) {
        config.temporarilyShowHidden = show
        getAllFragments().forEach {
            it?.refreshFragment()
        }
    }

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun checkIfRootAvailable() {
        ensureBackgroundThread {
            config.isRootAvailable = RootTools.isRootAvailable()
            if (config.isRootAvailable && config.enableRootAccess) {
                RootHelpers(this).askRootIfNeeded {
                    config.enableRootAccess = it
                }
            }
        }
    }

    private fun checkInvalidFavorites() {
        ensureBackgroundThread {
            config.favorites.forEach {
                if (!isPathOnOTG(it) && !isPathOnSD(it) && !File(it).exists()) {
                    config.removeFavorite(it)
                }
            }
        }
    }

    fun pickedPath(path: String) {
        val resultIntent = Intent()
        val uri = getFilePublicUri(File(path), BuildConfig.APPLICATION_ID)
        val type = path.getMimeType()
        resultIntent.setDataAndType(uri, type)
        resultIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    // used at apps that have no file access at all, but need to work with files. For example Simple Calendar uses this at exporting events into a file
    fun createDocumentConfirmed(path: String) {
        val filename = intent.getStringExtra(Intent.EXTRA_TITLE) ?: ""
        if (filename.isEmpty()) {
            InsertFilenameDialog(this, internalStoragePath) { newFilename ->
                finishCreateDocumentIntent(path, newFilename)
            }
        } else {
            finishCreateDocumentIntent(path, filename)
        }
    }

    private fun finishCreateDocumentIntent(path: String, filename: String) {
        val resultIntent = Intent()
        val uri = getFilePublicUri(File(path, filename), BuildConfig.APPLICATION_ID)
        val type = path.getMimeType()
        resultIntent.setDataAndType(uri, type)
        resultIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    fun pickedRingtone(path: String) {
        val uri = getFilePublicUri(File(path), BuildConfig.APPLICATION_ID)
        val type = path.getMimeType()
        Intent().apply {
            setDataAndType(uri, type)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            putExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, uri)
            setResult(Activity.RESULT_OK, this)
        }
        finish()
    }

    fun pickedPaths(paths: ArrayList<String>) {
        val newPaths = paths.map { getFilePublicUri(File(it), BuildConfig.APPLICATION_ID) } as ArrayList
        val clipData = ClipData("Attachment", arrayOf(paths.getMimeType()), ClipData.Item(newPaths.removeAt(0)))

        newPaths.forEach {
            clipData.addItem(ClipData.Item(it))
        }

        Intent().apply {
            this.clipData = clipData
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            setResult(Activity.RESULT_OK, this)
        }
        finish()
    }

    fun openedDirectory() {
        if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
        }
    }

    private fun getInactiveTabIndexes(activeIndex: Int) = (0 until binding.mainTabsHolder.tabCount).filter { it != activeIndex }

    private fun getSelectedTabDrawableIds(): ArrayList<Int> {
        val showTabs = config.showTabs
        val icons = ArrayList<Int>()

        if (showTabs and TAB_RECENT_FILES != 0) {
            icons.add(R.drawable.ic_clock_filled_scaled)
        }

        if (showTabs and TAB_FILES != 0) {
            icons.add(R.drawable.ic_folder_closed_scaled)
        }

        if (showTabs and TAB_STORAGE_ANALYSIS != 0) {
            icons.add(R.drawable.ic_storage_scaled)
        }

        return icons
    }

    private fun getDeselectedTabDrawableIds(): ArrayList<Int> {
        val showTabs = config.showTabs
        val icons = ArrayList<Int>()

        if (showTabs and TAB_RECENT_FILES != 0) {
            icons.add(R.drawable.ic_clock_filled)
        }

        if (showTabs and TAB_FILES != 0) {
            icons.add(R.drawable.ic_folder_closed)
        }

        if (showTabs and TAB_STORAGE_ANALYSIS != 0) {
            icons.add(R.drawable.ic_storage_vector)
        }

        return icons
    }

    private fun getRecentsFragment() = findViewById<RecentsFragment>(R.id.recents_fragment)
    private fun getItemsFragment() = findViewById<ItemsFragment>(R.id.items_fragment)
    private fun getStorageFragment() = findViewById<StorageFragment>(R.id.storage_fragment)
    private fun getAllFragments(): ArrayList<MyViewPagerFragment<*>?> =
        arrayListOf(getRecentsFragment(), getItemsFragment(), getStorageFragment())

    private fun getCurrentFragment(): MyViewPagerFragment<*>? {
        val showTabs = config.showTabs
        val fragments = arrayListOf<MyViewPagerFragment<*>>()
        if (showTabs and TAB_RECENT_FILES != 0) {
            fragments.add(getRecentsFragment())
        }

        if (showTabs and TAB_FILES != 0) {
            fragments.add(getItemsFragment())
        }

        if (showTabs and TAB_STORAGE_ANALYSIS != 0) {
            fragments.add(getStorageFragment())
        }

        return fragments.getOrNull(binding.mainViewPager.currentItem)
    }

    private fun getTabsList() = arrayListOf(TAB_RECENT_FILES, TAB_FILES, TAB_STORAGE_ANALYSIS)

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            add(Release(500, R.string.release_500))
            add(Release(501, R.string.release_501))
            add(Release(510, R.string.release_510))
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }

    fun appOpsServiceDialog() {
        ConfirmationAdvancedDialog(this, "", R.string.access_app_ops_service, R.string.grant_permission, R.string.cancel, false) { success ->
            if (success) {
                if (!checkAppOpsService()) openAppOpsService()
            } else {
                val volumeBinding = ItemStorageVolumeBinding.inflate(layoutInflater)
                config.checkAppOpsService = false
                volumeBinding.appsSize.background = resources.getColoredDrawableWithColor(R.drawable.ripple_all_corners_56dp, getProperTextColor())
                volumeBinding.appsSize.text = "???"
                volumeBinding.appsSize.setOnClickListener { appOpsServiceDialog() }
            }
        }
    }

    private fun openAppOpsService() {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            intent.addCategory("android.intent.category.DEFAULT")
            intent.data = Uri.parse("package:$packageName")
            startActivityForResult(intent, USAGE_STATS_RC)
        } catch (e: Exception) {
            //showErrorToast(e)
            val intent = Intent()
            intent.action = Settings.ACTION_USAGE_ACCESS_SETTINGS
            startActivityForResult(intent, USAGE_STATS_RC)
        }
    }

    private fun checkAppOpsService(): Boolean {
        return try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
