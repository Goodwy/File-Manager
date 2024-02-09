package com.goodwy.filemanager.fragments

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import androidx.core.view.ScrollingView
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.VIEW_TYPE_LIST
import com.goodwy.commons.models.FileDirItem
import com.goodwy.commons.views.MyFloatingActionButton
import com.goodwy.commons.views.MyRecyclerView
import com.goodwy.filemanager.R
import com.goodwy.filemanager.activities.MainActivity
import com.goodwy.filemanager.activities.SimpleActivity
import com.goodwy.filemanager.databinding.ItemsFragmentBinding
import com.goodwy.filemanager.databinding.RecentsFragmentBinding
import com.goodwy.filemanager.databinding.StorageFragmentBinding
import com.goodwy.filemanager.extensions.isPathOnRoot
import com.goodwy.filemanager.extensions.tryOpenPathIntent
import com.goodwy.filemanager.helpers.RootHelpers

abstract class MyViewPagerFragment<BINDING : MyViewPagerFragment.InnerBinding>(context: Context, attributeSet: AttributeSet) :
    RelativeLayout(context, attributeSet) {
    protected var activity: SimpleActivity? = null
    protected var currentViewType = VIEW_TYPE_LIST

    var currentPath = ""
    var isGetContentIntent = false
    var isGetRingtonePicker = false
    var isPickMultipleIntent = false
    var wantedMimeTypes = listOf("")
    protected var isCreateDocumentIntent = false
    protected lateinit var innerBinding: BINDING

    protected fun clickedPath(path: String) {
        if (isGetContentIntent || isCreateDocumentIntent) {
            (activity as MainActivity).pickedPath(path)
        } else if (isGetRingtonePicker) {
            if (path.isAudioFast()) {
                (activity as MainActivity).pickedRingtone(path)
            } else {
                activity?.toast(R.string.select_audio_file)
            }
        } else {
            activity?.tryOpenPathIntent(path, false)
        }
    }

    fun updateIsCreateDocumentIntent(isCreateDocumentIntent: Boolean) {
        val iconId = if (isCreateDocumentIntent) {
            R.drawable.ic_check_vector
        } else {
            R.drawable.ic_plus_vector
        }

        this.isCreateDocumentIntent = isCreateDocumentIntent
        val fabIcon = context.resources.getColoredDrawableWithColor(iconId, context.getProperPrimaryColor().getContrastColor())
        innerBinding.itemsFab?.setImageDrawable(fabIcon)
    }

    fun handleFileDeleting(files: ArrayList<FileDirItem>, hasFolder: Boolean) {
        val firstPath = files.firstOrNull()?.path
        if (firstPath == null || firstPath.isEmpty() || context == null) {
            return
        }

        if (context!!.isPathOnRoot(firstPath)) {
            RootHelpers(activity!!).deleteFiles(files)
        } else {
            (activity as SimpleActivity).deleteFiles(files, hasFolder) {
                if (!it) {
                    activity!!.runOnUiThread {
                        activity!!.toast(R.string.unknown_error_occurred)
                    }
                }
            }
        }
    }

    protected fun isProperMimeType(wantedMimeType: String, path: String, isDirectory: Boolean): Boolean {
        return if (wantedMimeType.isEmpty() || wantedMimeType == "*/*" || isDirectory) {
            true
        } else {
            val fileMimeType = path.getMimeType()
            if (wantedMimeType.endsWith("/*")) {
                fileMimeType.substringBefore("/").equals(wantedMimeType.substringBefore("/"), true)
            } else {
                fileMimeType.equals(wantedMimeType, true)
            }
        }
    }

    abstract fun setupFragment(activity: SimpleActivity)

    abstract fun onResume(textColor: Int)

    abstract fun refreshFragment()

    abstract fun searchQueryChanged(text: String)

    abstract fun myRecyclerView(): ScrollingView

    interface InnerBinding {
        val itemsFab: MyFloatingActionButton?
    }

    class ItemsInnerBinding(val binding: ItemsFragmentBinding) : InnerBinding {
        override val itemsFab: MyFloatingActionButton = binding.itemsFab
    }

    class RecentsInnerBinding(val binding: RecentsFragmentBinding) : InnerBinding {
        override val itemsFab: MyFloatingActionButton? = null
    }

    class StorageInnerBinding(val binding: StorageFragmentBinding) : InnerBinding {
        override val itemsFab: MyFloatingActionButton? = null
    }
}
