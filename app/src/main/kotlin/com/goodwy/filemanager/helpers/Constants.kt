package com.goodwy.filemanager.helpers

import com.goodwy.commons.helpers.TAB_FILES
import com.goodwy.commons.helpers.TAB_RECENT_FILES
import com.goodwy.commons.helpers.TAB_STORAGE_ANALYSIS
import com.goodwy.commons.models.FileDirItem
import com.goodwy.filemanager.models.ListItem

const val MAX_COLUMN_COUNT = 15

// shared preferences
const val SHOW_HIDDEN = "show_hidden"
const val PRESS_BACK_TWICE = "press_back_twice"
const val HOME_FOLDER = "home_folder"
const val TEMPORARILY_SHOW_HIDDEN = "temporarily_show_hidden"
const val IS_ROOT_AVAILABLE = "is_root_available"
const val ENABLE_ROOT_ACCESS = "enable_root_access"
const val EDITOR_TEXT_ZOOM = "editor_text_zoom"
const val VIEW_TYPE_PREFIX = "view_type_folder_"
const val FILE_COLUMN_CNT = "file_column_cnt"
const val FILE_LANDSCAPE_COLUMN_CNT = "file_landscape_column_cnt"
const val DISPLAY_FILE_NAMES = "display_file_names"
const val SHOW_TABS = "show_tabs"
const val WAS_STORAGE_ANALYSIS_TAB_ADDED = "was_storage_analysis_tab_added"
// Goodwy
const val SHOW_FOLDER_ICON = "show_folder_icon"
const val CHECK_APP_OPS_SERVICE = "check_app_ops_service"
const val SHOW_HOME_BUTTON = "show_home_button"
const val LAST_FOLDER = "last_folder"
const val DEFAULT_FOLDER = "default_folder"
const val SHOW_EXPANDED_DETAILS = "show_expanded_details"
const val SHOW_EXPANDED_DETAILS_PREFIX = "show_expanded_details_storage_"
const val SHOW_ONLY_FILENAME = "show_only_filename"

// default folder
const val FOLDER_LAST_USED = 0
const val FOLDER_HOME = 1
const val FOLDER_INTERNAL = 2

// open as
const val OPEN_AS_DEFAULT = 0
const val OPEN_AS_TEXT = 1
const val OPEN_AS_IMAGE = 2
const val OPEN_AS_AUDIO = 3
const val OPEN_AS_VIDEO = 4
const val OPEN_AS_OTHER = 5

const val ALL_TABS_MASK = TAB_RECENT_FILES or TAB_FILES or TAB_STORAGE_ANALYSIS

const val IMAGES = "images"
const val VIDEOS = "videos"
const val AUDIO = "audio"
const val DOCUMENTS = "documents"
const val ARCHIVES = "archives"
const val OTHERS = "others"
const val SHOW_MIMETYPE = "show_mimetype"

const val VOLUME_NAME = "volume_name"
const val PRIMARY_VOLUME_NAME = "external_primary" // On API <= 28, use VOLUME_EXTERNAL instead.
const val PRIMARY_VOLUME_NAME_OLD = "external"

// swiped left action
const val SWIPE_RIGHT_ACTION = "swipe_right_action"
const val SWIPE_LEFT_ACTION = "swipe_left_action"
const val SWIPE_ACTION_DELETE = 2
const val SWIPE_ACTION_BLOCK = 4 //!! isNougatPlus()
const val SWIPE_ACTION_CALL = 5
const val SWIPE_ACTION_MESSAGE = 6
const val SWIPE_ACTION_EDIT = 7
const val SWIPE_ACTION_COPY = 8
const val SWIPE_ACTION_MOVE = 9
const val SWIPE_ACTION_INFO = 10
const val SWIPE_VIBRATION = "swipe_vibration"
const val SWIPE_RIPPLE = "swipe_ripple"

// what else should we count as an audio except "audio/*" mimetype
val extraAudioMimeTypes = arrayListOf("application/ogg")
val extraDocumentMimeTypes = arrayListOf(
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/javascript",
    "application/vnd.ms-excel"
)

val archiveMimeTypes = arrayListOf(
    "application/zip",
    "application/octet-stream",
    "application/json",
    "application/x-tar",
    "application/x-rar-compressed",
    "application/x-zip-compressed",
    "application/x-7z-compressed",
    "application/x-compressed",
    "application/x-gzip",
    "application/java-archive",
    "multipart/x-zip",
    "application/x-rar",
    "application/vnd.rar",
    "application/vnd.comicbook-rar",
    "application/vnd.android.ota",
    //"application/octet-stream",  //rar?
    "application/apk",
    "application/vnd.android.package-archive"
)

fun getListItemsFromFileDirItems(fileDirItems: ArrayList<FileDirItem>): ArrayList<ListItem> {
    val listItems = ArrayList<ListItem>()
    fileDirItems.forEach {
        val listItem = ListItem(it.path, it.name, false, 0, it.size, it.modified, false, false)
        listItems.add(listItem)
    }
    return listItems
}
