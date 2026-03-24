package com.goodwy.filemanager.fragments

import android.content.ContentResolver
import android.content.Context
import android.provider.MediaStore.Files
import android.provider.MediaStore.Files.FileColumns
import android.util.AttributeSet
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.RecyclerView
import com.goodwy.commons.extensions.areSystemAnimationsEnabled
import com.goodwy.commons.extensions.beVisibleIf
import com.goodwy.commons.extensions.getDoesFilePathExist
import com.goodwy.commons.extensions.getFilenameFromPath
import com.goodwy.commons.extensions.getLongValue
import com.goodwy.commons.extensions.getProperBackgroundColor
import com.goodwy.commons.extensions.getStringValue
import com.goodwy.commons.extensions.getSurfaceColor
import com.goodwy.commons.extensions.hideKeyboard
import com.goodwy.commons.extensions.isDynamicTheme
import com.goodwy.commons.extensions.isSystemInDarkMode
import com.goodwy.commons.extensions.normalizeString
import com.goodwy.commons.extensions.showErrorToast
import com.goodwy.commons.helpers.VIEW_TYPE_GRID
import com.goodwy.commons.helpers.VIEW_TYPE_LIST
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.goodwy.commons.models.FileDirItem
import com.goodwy.commons.views.MyGridLayoutManager
import com.goodwy.commons.views.MyRecyclerView
import com.goodwy.filemanager.activities.MainActivity
import com.goodwy.filemanager.activities.SimpleActivity
import com.goodwy.filemanager.adapters.ItemsAdapter
import com.goodwy.filemanager.databinding.RecentsFragmentBinding
import com.goodwy.filemanager.extensions.config
import com.goodwy.filemanager.extensions.isPathInHiddenFolder
import com.goodwy.filemanager.helpers.MAX_COLUMN_COUNT
import com.goodwy.filemanager.helpers.RECENTS_FRAGMENT_PATH
import com.goodwy.filemanager.interfaces.ItemOperationsListener
import com.goodwy.filemanager.models.ListItem
import java.io.File

class RecentsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment<MyViewPagerFragment.RecentsInnerBinding>(context, attributeSet),
    ItemOperationsListener {
//    private val RECENTS_LIMIT = 50
    private var filesIgnoringSearch = ArrayList<ListItem>()
    private var lastSearchedText = ""
    private var zoomListener: MyRecyclerView.MyZoomListener? = null
    private lateinit var binding: RecentsFragmentBinding

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = RecentsFragmentBinding.bind(this)
        innerBinding = RecentsInnerBinding(binding)
    }

    override fun setupFragment(activity: SimpleActivity) {
        if (this.activity == null) {
            this.activity = activity
            val useSurfaceColor = activity.isDynamicTheme() && !activity.isSystemInDarkMode()
            val backgroundColor = if (useSurfaceColor) activity.getSurfaceColor() else activity.getProperBackgroundColor()
            binding.recentsFragment.setBackgroundColor(backgroundColor)
            binding.recentsSwipeRefresh.setOnRefreshListener { refreshFragment() }
        }

        refreshFragment()
    }

    override fun refreshFragment() {
        ensureBackgroundThread {
            getRecents { recents ->
                binding.apply {
                    recentsSwipeRefresh.isRefreshing = false
                    recentsList.beVisibleIf(recents.isNotEmpty())
                    recentsPlaceholder.beVisibleIf(recents.isEmpty())
                }
                filesIgnoringSearch = recents
                addItems(recents, false)

                if (context != null && currentViewType != context!!.config.getFolderViewType(RECENTS_FRAGMENT_PATH)) {
                    setupLayoutManager()
                }
            }
        }
    }

    private fun addItems(recents: ArrayList<ListItem>, forceRefresh: Boolean) {
        if (!forceRefresh && recents.hashCode() == (binding.recentsList.adapter as? ItemsAdapter)?.listItems.hashCode()) {
            return
        }

        ItemsAdapter(
            activity = activity as SimpleActivity,
            listItems = recents,
            listener = this,
            recyclerView = binding.recentsList,
            isPickMultipleIntent = isPickMultipleIntent,
            swipeRefreshLayout = binding.recentsSwipeRefresh,
            canHaveIndividualViewType = false,
            usePath = RECENTS_FRAGMENT_PATH
        ) {
            clickedPath((it as FileDirItem).path)
        }.apply {
            setupZoomListener(zoomListener)
            binding.recentsList.adapter = this
        }

        if (context.areSystemAnimationsEnabled) {
            binding.recentsList.scheduleLayoutAnimation()
        }
    }

    override fun onResume(textColor: Int) {
        binding.recentsPlaceholder.setTextColor(textColor)

        getRecyclerAdapter()?.apply {
            updatePrimaryColor()
            updateTextColor(textColor)
            initDrawables()
        }

        binding.recentsSwipeRefresh.isEnabled = lastSearchedText.isEmpty() && activity?.config?.enablePullToRefresh != false

        binding.recentsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                activity?.hideKeyboard()
            }
        })
    }

    private fun setupLayoutManager() {
        if (context!!.config.getFolderViewType(RECENTS_FRAGMENT_PATH) == VIEW_TYPE_GRID) {
            currentViewType = VIEW_TYPE_GRID
            setupGridLayoutManager()
        } else {
            currentViewType = VIEW_TYPE_LIST
            setupListLayoutManager()
        }

        val oldItems = (binding.recentsList.adapter as? ItemsAdapter)?.listItems?.toMutableList() as ArrayList<ListItem>
        binding.recentsList.adapter = null
        initZoomListener()
        addItems(oldItems, true)
    }

    private fun setupGridLayoutManager() {
        val layoutManager = binding.recentsList.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = context?.config?.fileColumnCnt ?: 3
    }

    private fun setupListLayoutManager() {
        val layoutManager = binding.recentsList.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = 1
        zoomListener = null
    }

    private fun initZoomListener() {
        if (context?.config?.getFolderViewType(RECENTS_FRAGMENT_PATH) == VIEW_TYPE_GRID) {
            val layoutManager = binding.recentsList.layoutManager as MyGridLayoutManager
            zoomListener = object : MyRecyclerView.MyZoomListener {
                override fun zoomIn() {
                    if (layoutManager.spanCount > 1) {
                        reduceColumnCount()
                        getRecyclerAdapter()?.finishActMode()
                    }
                }

                override fun zoomOut() {
                    if (layoutManager.spanCount < MAX_COLUMN_COUNT) {
                        increaseColumnCount()
                        getRecyclerAdapter()?.finishActMode()
                    }
                }
            }
        } else {
            zoomListener = null
        }
    }

    private fun getRecents(callback: (recents: ArrayList<ListItem>) -> Unit) {
        val showHidden = context?.config?.shouldShowHidden() ?: return
        val listItems = arrayListOf<ListItem>()

        val uri = Files.getContentUri("external")
        val projection = arrayOf(
            FileColumns.DATA,
            FileColumns.DISPLAY_NAME,
            FileColumns.DATE_MODIFIED,
            FileColumns.SIZE
        )

        val recentsLimit = context?.config?.queryLimitRecent

        try {
            val queryArgs = bundleOf(
                ContentResolver.QUERY_ARG_LIMIT to recentsLimit,
                ContentResolver.QUERY_ARG_SORT_COLUMNS to arrayOf(FileColumns.DATE_MODIFIED),
                ContentResolver.QUERY_ARG_SORT_DIRECTION to ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            )

            context?.contentResolver?.query(uri, projection, queryArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        val path = cursor.getStringValue(FileColumns.DATA)
                        if (File(path).isDirectory) {
                            continue
                        }

                        val name = cursor.getStringValue(FileColumns.DISPLAY_NAME) ?: path.getFilenameFromPath()
                        val size = cursor.getLongValue(FileColumns.SIZE)
                        val modified = cursor.getLongValue(FileColumns.DATE_MODIFIED) * 1000
                        val isHiddenFile = name.startsWith(".")
                        val shouldShow = showHidden || (!isHiddenFile && !path.isPathInHiddenFolder())
                        if (shouldShow && activity?.getDoesFilePathExist(path) == true) {
                            if (wantedMimeTypes.any { isProperMimeType(it, path, false) }) {
                                val fileDirItem = ListItem(path, name, false, 0, size, modified, false, false)
                                listItems.add(fileDirItem)
                            }
                        }
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
            activity?.showErrorToast(e)
        }

        activity?.runOnUiThread {
            callback(listItems)
        }
    }

    private fun getRecyclerAdapter() = binding.recentsList.adapter as? ItemsAdapter

    override fun toggleFilenameVisibility() {
        getRecyclerAdapter()?.updateDisplayFilenamesInGrid()
    }

    private fun increaseColumnCount() {
        if (currentViewType == VIEW_TYPE_GRID) {
            context!!.config.fileColumnCnt += 1
            (activity as? MainActivity)?.updateFragmentColumnCounts()
        }
    }

    private fun reduceColumnCount() {
        if (currentViewType == VIEW_TYPE_GRID) {
            context!!.config.fileColumnCnt -= 1
            (activity as? MainActivity)?.updateFragmentColumnCounts()
        }
    }

    override fun columnCountChanged() {
        (binding.recentsList.layoutManager as MyGridLayoutManager).spanCount = context!!.config.fileColumnCnt
        (activity as? MainActivity)?.refreshMenuItems()
        getRecyclerAdapter()?.apply {
            notifyItemRangeChanged(0, listItems.size)
        }
    }

    override fun setupFontSize() {
        getRecyclerAdapter()?.updateFontSizes()
    }

    override fun setupDateTimeFormat() {
        getRecyclerAdapter()?.updateDateTimeFormat()
    }

    override fun selectedPaths(paths: ArrayList<String>) {
        (activity as MainActivity).pickedPaths(paths)
    }

    override fun deleteFiles(files: ArrayList<FileDirItem>) {
        handleFileDeleting(files, false)
    }

    override fun searchQueryChanged(text: String) {
        lastSearchedText = text
        val normalizedText = text.normalizeString()
        val filtered = filesIgnoringSearch.filter {
            it.mName.normalizeString().contains(normalizedText, true)
        }.toMutableList() as ArrayList<ListItem>

        binding.apply {
            (recentsList.adapter as? ItemsAdapter)?.updateItems(filtered, text)
            recentsPlaceholder.beVisibleIf(filtered.isEmpty())
            recentsSwipeRefresh.isEnabled = lastSearchedText.isEmpty() && activity?.config?.enablePullToRefresh != false
        }
    }

    override fun finishActMode() {
        getRecyclerAdapter()?.finishActMode()
    }

    override fun myRecyclerView() = binding.recentsList
}
