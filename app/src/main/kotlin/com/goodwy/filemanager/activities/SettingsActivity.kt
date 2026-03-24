package com.goodwy.filemanager.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.goodwy.commons.dialogs.*
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.RadioItem
import com.goodwy.filemanager.BuildConfig
import com.goodwy.filemanager.R
import com.goodwy.filemanager.databinding.ActivitySettingsBinding
import com.goodwy.filemanager.dialogs.ManageVisibleTabsDialog
import com.goodwy.filemanager.extensions.config
import com.goodwy.filemanager.extensions.launchAbout
import com.goodwy.filemanager.extensions.pixels
import com.goodwy.filemanager.helpers.*
import com.google.android.material.snackbar.Snackbar
import com.mikhaellopez.rxanimation.RxAnimation
import com.mikhaellopez.rxanimation.shake
import java.util.Locale
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {
    private val binding by viewBinding(ActivitySettingsBinding::inflate)

    private val productIdX1 = BuildConfig.PRODUCT_ID_X1
    private val productIdX2 = BuildConfig.PRODUCT_ID_X2
    private val productIdX3 = BuildConfig.PRODUCT_ID_X3
    private val subscriptionIdX1 = BuildConfig.SUBSCRIPTION_ID_X1
    private val subscriptionIdX2 = BuildConfig.SUBSCRIPTION_ID_X2
    private val subscriptionIdX3 = BuildConfig.SUBSCRIPTION_ID_X3
    private val subscriptionYearIdX1 = BuildConfig.SUBSCRIPTION_YEAR_ID_X1
    private val subscriptionYearIdX2 = BuildConfig.SUBSCRIPTION_YEAR_ID_X2
    private val subscriptionYearIdX3 = BuildConfig.SUBSCRIPTION_YEAR_ID_X3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupOptionsMenu()

        binding.apply {
//            setupEdgeToEdge(padBottomSystem = listOf(settingsNestedScrollview))
            setupMaterialScrollListener(binding.settingsNestedScrollview, binding.settingsAppbar)
        }

        val iapList: ArrayList<String> = arrayListOf(productIdX1, productIdX2, productIdX3)
        val subList: ArrayList<String> =
            arrayListOf(
                subscriptionIdX1, subscriptionIdX2, subscriptionIdX3,
                subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3
            )
        val ruStoreList: ArrayList<String> =
            arrayListOf(
                productIdX1, productIdX2, productIdX3,
                subscriptionIdX1, subscriptionIdX2, subscriptionIdX3,
                subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3
            )
        PurchaseHelper().checkPurchase(
            this@SettingsActivity,
            iapList = iapList,
            subList = subList,
            ruStoreList = ruStoreList
        ) { updatePro ->
            if (updatePro) updatePro()
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.settingsAppbar, NavigationIcon.Arrow)

        setupPurchaseThankYou()
        setupCustomizeColors()
        setupFloatingButtonStyle()

        setupDefaultFolder()
        setupManageFavorites()
        setupPressBackTwice()
        setupFontSize()
        setupChangeDateTimeFormat()
        setupUseEnglish()
        setupLanguage()

        setupManageShownTabs()
        setupUseIconTabs()
        setupScreenSlideAnimation()
        setupEnablePullToRefresh()
        setupShowHomeButton()

        setupUseSwipeToAction()
        setupSwipeWidth()
        setupSwipeVibration()
        setupSwipeRipple()
        setupSwipeRightAction()
        setupSwipeLeftAction()

        setupShowHidden()
        setupKeepLastModified()
        setupDeleteConfirmation()

        setupHiddenItemPasswordProtection()
        setupAppPasswordProtection()
        setupFileDeletionPasswordProtection()
        setupEnableRootAccess()

        setupOverflowIcon()
        setupShowSearchBar()
        setupChangeColourTopBar()

        setupQueryLimitRecent()
        setupShowFolderIcon()
        setupShowDividers()
        setupThumbnailsSize()
        setupShowOnlyFilename()

        setupTipJar()
        setupAbout()

        updateTextColors(binding.settingsNestedScrollview)

        binding.apply {
            arrayOf(
                settingsAppearanceLabel,
                settingsGeneralLabel,
                settingsTabsLabel,
                settingsSwipeGesturesLabel,
                settingsFileOperationsLabel,
                settingsSecurityLabel,
                settingsTopAppBarLabel,
                settingsListViewLabel,
                settingsOtherLabel
            ).forEach {
                it.setTextColor(getProperPrimaryColor())
            }
        }

        binding.apply {
            arrayOf(
                settingsColorCustomizationHolder,
                settingsGeneralHolder,
                settingsTabsHolder,
                settingsSwipeGesturesHolder,
                settingsFileOperationsHolder,
                settingsSecurityHolder,
                settingsTopAppBarHolder,
                settingsListViewHolder,
                settingsOtherHolder
            ).forEach {
                it.setCardBackgroundColor(getSurfaceColor())
            }
        }

        binding.apply {
            arrayOf(
                settingsCustomizeColorsChevron,
                settingsManageShownTabsChevron,
                settingsManageFavoritesChevron,
                settingsChangeDateTimeFormatChevron,
                settingsTipJarChevron,
                settingsAboutChevron
            ).forEach {
                it.applyColorFilter(getProperTextColor())
            }
        }
    }

    private fun updatePro(isPro: Boolean = isPro()) {
        binding.apply {
            settingsPurchaseThankYouHolder.beGoneIf(isPro)
            settingsSwipeLeftActionHolder.alpha = if (isPro) 1f else 0.4f
            settingsTipJarHolder.beVisibleIf(isPro)
        }
    }

    private fun setupPurchaseThankYou() = binding.apply {
        settingsPurchaseThankYouHolder.beGoneIf(isPro())
        settingsPurchaseThankYouHolder.onClick = { launchPurchase() }
    }

    private fun setupCustomizeColors() {
        binding.apply {
//            settingsCustomizeColorsLabel.text = if (isPro()) {
//                getString(R.string.customize_colors)
//            } else {
//                getString(R.string.customize_colors_locked)
//            }
            settingsCustomizeColorsHolder.setOnClickListener {
                startCustomizationActivity(
                    showAccentColor = true,
                    isCollection = false,
                    productIdList = arrayListOf(productIdX1, productIdX2, productIdX3),
                    productIdListRu = arrayListOf(productIdX1, productIdX2, productIdX3),
                    subscriptionIdList = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
                    subscriptionIdListRu = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
                    subscriptionYearIdList = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
                    subscriptionYearIdListRu = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
                    showAppIconColor = true
                )
            }
        }
    }

    private fun setupOverflowIcon() = binding.apply {
        settingsOverflowIcon.applyColorFilter(getProperTextColor())
        settingsOverflowIcon.setImageResource(getOverflowIcon(config.overflowIcon))
        settingsOverflowIconHolder.setOnClickListener {
            val items = arrayListOf(
                R.drawable.ic_more_horiz,
                R.drawable.ic_three_dots_vector,
                R.drawable.ic_more_horiz_round
            )

            IconListDialog(
                activity = this@SettingsActivity,
                items = items,
                checkedItemId = config.overflowIcon + 1,
                defaultItemId = OVERFLOW_ICON_HORIZONTAL + 1,
                titleId = R.string.overflow_icon,
                size = pixels(R.dimen.normal_icon_size).toInt(),
                color = getProperTextColor()
            ) { wasPositivePressed, newValue ->
                if (wasPositivePressed) {
                    if (config.overflowIcon != newValue - 1) {
                        config.overflowIcon = newValue - 1
                        settingsOverflowIcon.setImageResource(getOverflowIcon(config.overflowIcon))
                    }
                }
            }
        }
    }

    private fun setupFloatingButtonStyle() {
        binding.apply {
            settingsFloatingButtonStyle.applyColorFilter(getProperTextColor())
            settingsFloatingButtonStyle.setImageResource(
                if (baseConfig.materialDesign3) R.drawable.squircle_bg else R.drawable.ic_circle_filled
            )
            settingsFloatingButtonStyleHolder.setOnClickListener {
                val items = arrayListOf(
                    R.drawable.ic_circle_filled,
                    R.drawable.squircle_bg
                )

                IconListDialog(
                    activity = this@SettingsActivity,
                    items = items,
                    checkedItemId = if (baseConfig.materialDesign3) 2 else 1,
                    defaultItemId = 1,
                    titleId = com.goodwy.strings.R.string.floating_button_style,
                    size = pixels(com.goodwy.commons.R.dimen.normal_icon_size).toInt(),
                    color = getProperTextColor()
                ) { wasPositivePressed, newValue ->
                    if (wasPositivePressed) {
                        if (newValue != if (baseConfig.materialDesign3) 2 else 1) {
                            baseConfig.materialDesign3 = newValue == 2
                            settingsFloatingButtonStyle.setImageResource(
                                if (newValue == 2) R.drawable.squircle_bg else R.drawable.ic_circle_filled
                            )
                            config.needRestart = true
                        }
                    }
                }
            }
        }
    }

    private fun setupManageShownTabs() {
        binding.settingsManageShownTabsHolder.setOnClickListener {
            ManageVisibleTabsDialog(this)
        }
    }

    private fun setupUseIconTabs() {
        binding.apply {
            settingsUseIconTabs.isChecked = config.useIconTabs
            settingsUseIconTabsHolder.setOnClickListener {
                settingsUseIconTabs.toggle()
                config.useIconTabs = settingsUseIconTabs.isChecked
                config.needRestart = true
            }
        }
    }

    private fun setupScreenSlideAnimation() {
        binding.apply {
            settingsScreenSlideAnimation.text = getScreenSlideAnimationText()
            settingsScreenSlideAnimationHolder.setOnClickListener {
                val items = arrayListOf(
                    RadioItem(0, getString(R.string.no), icon = R.drawable.ic_view_array),
                    RadioItem(1, getString(R.string.screen_slide_animation_zoomout), icon = R.drawable.ic_view_carousel),
                    RadioItem(2, getString(R.string.screen_slide_animation_depth), icon = R.drawable.ic_playing_cards),
                )

                RadioGroupIconDialog(this@SettingsActivity, items, config.screenSlideAnimation, R.string.screen_slide_animation) {
                    config.screenSlideAnimation = it as Int
                    config.needRestart = true
                    binding.settingsScreenSlideAnimation.text = getScreenSlideAnimationText()
                }
            }
        }
    }

    private fun setupDefaultFolder() {
        binding.apply {
            settingsDefaultFolder.text = getDefaultFolderText()
            settingsDefaultFolderHolder.setOnClickListener {
                val items = arrayListOf(
                    RadioItem(FOLDER_LAST_USED, getString(R.string.last_used_g), icon = R.drawable.ic_clock_filled),
                    RadioItem(FOLDER_HOME, getString(R.string.home_folder_g), icon = R.drawable.ic_home),
                    RadioItem(FOLDER_INTERNAL, getString(R.string.internal), icon = R.drawable.ic_storage_vector)
                )

                RadioGroupIconDialog(this@SettingsActivity, items, config.defaultFolder, R.string.default_folder_to_open_g) {
                    config.defaultFolder = it as Int
                    settingsDefaultFolder.text = getDefaultFolderText()
                }
            }
        }
    }

    private fun Context.getDefaultFolderText() = getString(
        when (config.defaultFolder) {
            FOLDER_HOME -> R.string.home_folder_g
            FOLDER_INTERNAL -> R.string.internal
            else -> R.string.last_used_g
        }
    )

    private fun setupManageFavorites() {
        binding.settingsManageFavoritesHolder.setOnClickListener {
            startActivity(Intent(this, FavoritesActivity::class.java))
        }
    }

    private fun setupPressBackTwice() {
        binding.apply {
            settingsPressBackTwice.isChecked = config.pressBackTwice
            settingsPressBackTwiceHolder.setOnClickListener {
                settingsPressBackTwice.toggle()
                config.pressBackTwice = settingsPressBackTwice.isChecked
            }
        }
    }

    private fun setupUseEnglish() {
        binding.apply {
            settingsUseEnglishHolder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
            settingsUseEnglish.isChecked = config.useEnglish
            settingsUseEnglishHolder.setOnClickListener {
                settingsUseEnglish.toggle()
                config.useEnglish = settingsUseEnglish.isChecked
                exitProcess(0)
            }
        }
    }

    private fun setupLanguage() {
        binding.apply {
            settingsLanguage.text = Locale.getDefault().displayLanguage
            settingsLanguageHolder.beVisibleIf(isTiramisuPlus())
            settingsLanguageHolder.setOnClickListener {
                launchChangeAppLanguageIntent()
            }
        }
    }

    private fun setupChangeDateTimeFormat() {
        binding.settingsChangeDateTimeFormatHolder.setOnClickListener {
            ChangeDateTimeFormatDialog(this) {}
        }
    }

    private fun setupFontSize() {
        binding.apply {
            settingsFontSize.text = getFontSizeText()
            settingsFontSizeHolder.setOnClickListener {
                val items = arrayListOf(
                    RadioItem(FONT_SIZE_SMALL, getString(R.string.small)),
                    RadioItem(FONT_SIZE_MEDIUM, getString(R.string.medium)),
                    RadioItem(FONT_SIZE_LARGE, getString(R.string.large)),
                    RadioItem(FONT_SIZE_EXTRA_LARGE, getString(R.string.extra_large))
                )

                RadioGroupDialog(this@SettingsActivity, items, config.fontSize, R.string.font_size) {
                    config.fontSize = it as Int
                    settingsFontSize.text = getFontSizeText()
                }
            }
        }
    }

    private fun setupShowHidden() {
        binding.apply {
            settingsShowHidden.isChecked = config.showHidden
            settingsShowHiddenHolder.setOnClickListener {
                if (config.showHidden) {
                    toggleShowHidden()
                } else {
                    handleHiddenFolderPasswordProtection {
                        toggleShowHidden()
                    }
                }
            }
        }
    }

    private fun toggleShowHidden() {
        binding.settingsShowHidden.toggle()
        config.showHidden = binding.settingsShowHidden.isChecked
    }

    private fun setupEnablePullToRefresh() {
        binding.apply {
            settingsEnablePullToRefresh.isChecked = config.enablePullToRefresh
            settingsEnablePullToRefreshHolder.setOnClickListener {
                settingsEnablePullToRefresh.toggle()
                config.enablePullToRefresh = settingsEnablePullToRefresh.isChecked
            }
        }
    }

    private fun setupShowHomeButton() {
        binding.apply {
            settingsShowHomeButton.isChecked = config.showHomeButton
            settingsShowHomeButtonHolder.setOnClickListener {
                settingsShowHomeButton.toggle()
                config.showHomeButton = settingsShowHomeButton.isChecked
            }
        }
    }

    private fun setupUseSwipeToAction() {
        updateSwipeToActionVisible()
        binding.apply {
            settingsUseSwipeToAction.isChecked = config.useSwipeToAction
            settingsUseSwipeToActionHolder.setOnClickListener {
                settingsUseSwipeToAction.toggle()
                config.useSwipeToAction = settingsUseSwipeToAction.isChecked
                config.needRestart = true
                updateSwipeToActionVisible()
            }
        }
    }

    private fun setupSwipeWidth() = binding.apply {
        settingsSwipeWidthHolder.beVisibleIf(config.useSwipeToAction)
        settingsSwipeWidth.text = getSwipeWidthText(config.swipeToActionWidth)
        settingsSwipeWidthHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(2, "1/2"),
                RadioItem(3, "1/3"),
                RadioItem(4, "1/4"),
                RadioItem(5, "1/5"),
            )

            RadioGroupIconDialog(
                this@SettingsActivity,
                items,
                config.swipeToActionWidth,
                R.string.swipe_width,
                defaultItemId = 2
            ) {
                config.swipeToActionWidth = it as Int
                config.needRestart = true
                settingsSwipeWidth.text = getSwipeWidthText(config.swipeToActionWidth)
            }
        }
    }

    private fun getSwipeWidthText(swipeWidth: Int): String {
        return when (swipeWidth) {
            3 -> "1/3"
            4 -> "1/4"
            5 -> "1/5"
            else -> "1/2"
        }
    }

    private fun updateSwipeToActionVisible() {
        binding.apply {
            settingsSwipeWidthHolder.beVisibleIf(config.useSwipeToAction)
            settingsSwipeVibrationHolder.beVisibleIf(config.useSwipeToAction)
            settingsSwipeRippleHolder.beVisibleIf(config.useSwipeToAction)
            settingsSwipeRightActionHolder.beVisibleIf(config.useSwipeToAction)
            settingsSwipeLeftActionHolder.beVisibleIf(config.useSwipeToAction)
        }
    }

    private fun setupSwipeVibration() {
        binding.apply {
            settingsSwipeVibration.isChecked = config.swipeVibration
            settingsSwipeVibrationHolder.setOnClickListener {
                settingsSwipeVibration.toggle()
                config.swipeVibration = settingsSwipeVibration.isChecked
            }
        }
    }

    private fun setupSwipeRipple() {
        binding.apply {
            settingsSwipeRipple.isChecked = config.swipeRipple
            settingsSwipeRippleHolder.setOnClickListener {
                settingsSwipeRipple.toggle()
                config.swipeRipple = settingsSwipeRipple.isChecked
                config.needRestart = true
            }
        }
    }

    private fun setupSwipeRightAction() = binding.apply {
        if (isRTLLayout) settingsSwipeRightActionLabel.text = getString(R.string.swipe_left_action)
        settingsSwipeRightAction.text = getSwipeActionText(false)
        settingsSwipeRightActionHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(SWIPE_ACTION_DELETE, getString(R.string.delete), icon = R.drawable.ic_delete_outline),
                RadioItem(SWIPE_ACTION_MOVE, getString(R.string.move_to), icon = R.drawable.ic_move_vector),
                RadioItem(SWIPE_ACTION_COPY, getString(R.string.copy_to), icon = R.drawable.ic_copy_vector),
                RadioItem(SWIPE_ACTION_INFO, getString(R.string.properties), icon = R.drawable.ic_info_vector),
                RadioItem(SWIPE_ACTION_NONE, getString(com.goodwy.commons.R.string.nothing)),
            )

            val title =
                if (isRTLLayout) R.string.swipe_left_action else R.string.swipe_right_action
            RadioGroupIconDialog(this@SettingsActivity, items, config.swipeRightAction, title) {
                config.swipeRightAction = it as Int
                config.needRestart = true
                settingsSwipeRightAction.text = getSwipeActionText(false)
            }
        }
    }

    private fun setupSwipeLeftAction() = binding.apply {
        val pro = isPro()
        settingsSwipeLeftActionHolder.alpha = if (pro) 1f else 0.4f
        val stringId = if (isRTLLayout) R.string.swipe_right_action else R.string.swipe_left_action
        settingsSwipeLeftActionLabel.text = addLockedLabelIfNeeded(stringId, pro)
        settingsSwipeLeftAction.text = getSwipeActionText(true)
        settingsSwipeLeftActionHolder.setOnClickListener {
            if (pro) {
                val items = arrayListOf(
                    RadioItem(SWIPE_ACTION_DELETE, getString(R.string.delete), icon = R.drawable.ic_delete_outline),
                    RadioItem(SWIPE_ACTION_MOVE, getString(R.string.move_to), icon = R.drawable.ic_move_vector),
                    RadioItem(SWIPE_ACTION_COPY, getString(R.string.copy_to), icon = R.drawable.ic_copy_vector),
                    RadioItem(SWIPE_ACTION_INFO, getString(R.string.properties), icon = R.drawable.ic_info_vector),
                    RadioItem(SWIPE_ACTION_NONE, getString(com.goodwy.commons.R.string.nothing)),
                )

                val title =
                    if (isRTLLayout) R.string.swipe_right_action else R.string.swipe_left_action
                RadioGroupIconDialog(this@SettingsActivity, items, config.swipeLeftAction, title) {
                    config.swipeLeftAction = it as Int
                    config.needRestart = true
                    settingsSwipeLeftAction.text = getSwipeActionText(true)
                }
            } else {
                RxAnimation.from(settingsSwipeLeftActionHolder)
                    .shake(shakeTranslation = 2f)
                    .subscribe()

                showSnackbar(binding.root)
            }
        }
    }

    private fun getSwipeActionText(left: Boolean) = getString(
        when (if (left) config.swipeLeftAction else config.swipeRightAction) {
            SWIPE_ACTION_DELETE -> R.string.delete
            SWIPE_ACTION_MOVE -> R.string.move_to
            SWIPE_ACTION_INFO -> R.string.properties
            SWIPE_ACTION_COPY -> R.string.copy_to
            else -> R.string.nothing
        }
    )

    private fun showSnackbar(view: View) {
        view.performHapticFeedback()

        val snackbar = Snackbar.make(view, R.string.support_project_to_unlock, Snackbar.LENGTH_SHORT)
            .setAction(R.string.support) {
                launchPurchase()
            }

        val bgDrawable = ResourcesCompat.getDrawable(view.resources, R.drawable.button_background_16dp, null)
        snackbar.view.background = bgDrawable
        val properBackgroundColor = getProperBackgroundColor()
        val backgroundColor =
            if (properBackgroundColor == Color.BLACK) getSurfaceColor().lightenColor(6)
            else getSurfaceColor().darkenColor(6)
        snackbar.setBackgroundTint(backgroundColor)
        snackbar.setTextColor(getProperTextColor())
        snackbar.setActionTextColor(getProperPrimaryColor())
        snackbar.show()
    }

    private fun setupHiddenItemPasswordProtection() {
        binding.apply {
            settingsPasswordProtection.isChecked = config.isHiddenPasswordProtectionOn
            settingsPasswordProtectionHolder.setOnClickListener {
                val tabToShow = if (config.isHiddenPasswordProtectionOn) config.hiddenProtectionType else SHOW_ALL_TABS
                SecurityDialog(this@SettingsActivity, config.hiddenPasswordHash, tabToShow) { hash, type, success ->
                    if (success) {
                        val hasPasswordProtection = config.isHiddenPasswordProtectionOn
                        settingsPasswordProtection.isChecked = !hasPasswordProtection
                        config.isHiddenPasswordProtectionOn = !hasPasswordProtection
                        config.hiddenPasswordHash = if (hasPasswordProtection) "" else hash
                        config.hiddenProtectionType = type

                        if (config.isHiddenPasswordProtectionOn) {
                            val confirmationTextId = if (config.hiddenProtectionType == PROTECTION_FINGERPRINT)
                                R.string.fingerprint_setup_successfully else R.string.protection_setup_successfully
                            ConfirmationDialog(this@SettingsActivity, "", confirmationTextId, R.string.ok, 0) { }
                        }
                    }
                }
            }
        }
    }

    private fun setupAppPasswordProtection() {
        binding.apply {
            settingsAppPasswordProtection.isChecked = config.isAppPasswordProtectionOn
            settingsAppPasswordProtectionHolder.setOnClickListener {
                val tabToShow = if (config.isAppPasswordProtectionOn) config.appProtectionType else SHOW_ALL_TABS
                SecurityDialog(this@SettingsActivity, config.appPasswordHash, tabToShow) { hash, type, success ->
                    if (success) {
                        val hasPasswordProtection = config.isAppPasswordProtectionOn
                        settingsAppPasswordProtection.isChecked = !hasPasswordProtection
                        config.isAppPasswordProtectionOn = !hasPasswordProtection
                        config.appPasswordHash = if (hasPasswordProtection) "" else hash
                        config.appProtectionType = type

                        if (config.isAppPasswordProtectionOn) {
                            val confirmationTextId = if (config.appProtectionType == PROTECTION_FINGERPRINT)
                                R.string.fingerprint_setup_successfully else R.string.protection_setup_successfully
                            ConfirmationDialog(this@SettingsActivity, "", confirmationTextId, R.string.ok, 0) { }
                        }
                    }
                }
            }
        }
    }

    private fun setupFileDeletionPasswordProtection() {
        binding.apply {
            settingsFileDeletionPasswordProtection.isChecked = config.isDeletePasswordProtectionOn
            settingsFileDeletionPasswordProtectionHolder.setOnClickListener {
                val tabToShow = if (config.isDeletePasswordProtectionOn) config.deleteProtectionType else SHOW_ALL_TABS
                SecurityDialog(this@SettingsActivity, config.deletePasswordHash, tabToShow) { hash, type, success ->
                    if (success) {
                        val hasPasswordProtection = config.isDeletePasswordProtectionOn
                        settingsFileDeletionPasswordProtection.isChecked = !hasPasswordProtection
                        config.isDeletePasswordProtectionOn = !hasPasswordProtection
                        config.deletePasswordHash = if (hasPasswordProtection) "" else hash
                        config.deleteProtectionType = type

                        if (config.isDeletePasswordProtectionOn) {
                            val confirmationTextId = if (config.deleteProtectionType == PROTECTION_FINGERPRINT)
                                R.string.fingerprint_setup_successfully else R.string.protection_setup_successfully
                            ConfirmationDialog(this@SettingsActivity, "", confirmationTextId, R.string.ok, 0) { }
                        }
                    }
                }
            }
        }
    }

    private fun setupKeepLastModified() {
        binding.apply {
            settingsKeepLastModified.isChecked = config.keepLastModified
            settingsKeepLastModifiedHolder.setOnClickListener {
                settingsKeepLastModified.toggle()
                config.keepLastModified = settingsKeepLastModified.isChecked
            }
        }
    }

    private fun setupDeleteConfirmation() {
        binding.apply {
            settingsSkipDeleteConfirmation.isChecked = config.skipDeleteConfirmation
            settingsSkipDeleteConfirmationHolder.setOnClickListener {
                settingsSkipDeleteConfirmation.toggle()
                config.skipDeleteConfirmation = settingsSkipDeleteConfirmation.isChecked
            }
        }
    }

    private fun setupEnableRootAccess() {
        binding.apply {
            settingsEnableRootAccessHolder.beVisibleIf(config.isRootAvailable)
            settingsEnableRootAccess.isChecked = config.enableRootAccess
            settingsEnableRootAccessHolder.setOnClickListener {
                if (!config.enableRootAccess) {
                    RootHelpers(this@SettingsActivity).askRootIfNeeded {
                        toggleRootAccess(it)
                    }
                } else {
                    toggleRootAccess(false)
                }
            }
        }
    }

    private fun toggleRootAccess(enable: Boolean) {
        binding.settingsEnableRootAccess.isChecked = enable
        config.enableRootAccess = enable
    }

    private fun setupQueryLimitRecent() {
        binding.settingsQueryLimitRecent.text = getQueryLimitRecentText()
        binding.settingsQueryLimitRecentHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(QUERY_LIMIT_SMALL_VALUE, "≤20"),
                RadioItem(QUERY_LIMIT_MEDIUM_VALUE, "≤50"),
                RadioItem(QUERY_LIMIT_NORMAL_VALUE, "≤100"),
                RadioItem(QUERY_LIMIT_BIG_VALUE, "≤200")
            )

            RadioGroupDialog(this@SettingsActivity, items, config.queryLimitRecent, R.string.maximum_number_recent_files) {
                config.queryLimitRecent = it as Int
                binding.settingsQueryLimitRecent.text = getQueryLimitRecentText()
                config.needRestart = true
            }
        }
    }

    private fun getQueryLimitRecentText(): String {
        return "≤" + config.queryLimitRecent.toString()
    }

    private fun setupShowFolderIcon() {
        binding.apply {
            settingsShowFolderIcon.isChecked = config.showFolderIcon
            settingsShowFolderIconHolder.setOnClickListener {
                settingsShowFolderIcon.toggle()
                config.showFolderIcon = settingsShowFolderIcon.isChecked
            }
        }
    }

    private fun setupShowDividers() {
        binding.apply {
            settingsShowDividers.isChecked = config.useDividers
            settingsShowDividersHolder.setOnClickListener {
                settingsShowDividers.toggle()
                config.useDividers = settingsShowDividers.isChecked
            }
        }
    }

    private fun setupThumbnailsSize() = binding.apply {
        val pro = isPro()
        settingsThumbnailsSizeHolder.beVisibleIf(config.showContactThumbnails)
        settingsThumbnailsSizeHolder.alpha = if (pro) 1f else 0.4f
        settingsThumbnailsSizeLabel.text = addLockedLabelIfNeeded(R.string.size_of_icons, pro)
        settingsThumbnailsSize.text = getThumbnailsSizeText()
        settingsThumbnailsSizeHolder.setOnClickListener {
            if (pro) {
                val items = arrayListOf(
                    RadioItem(FONT_SIZE_SMALL, getString(R.string.small), CONTACT_THUMBNAILS_SIZE_SMALL),
                    RadioItem(FONT_SIZE_MEDIUM, getString(R.string.medium), CONTACT_THUMBNAILS_SIZE_MEDIUM),
                    RadioItem(FONT_SIZE_LARGE, getString(R.string.large), CONTACT_THUMBNAILS_SIZE_LARGE),
                    RadioItem(FONT_SIZE_EXTRA_LARGE, getString(R.string.extra_large), CONTACT_THUMBNAILS_SIZE_EXTRA_LARGE)
                )

                RadioGroupDialog(this@SettingsActivity, items, config.contactThumbnailsSize, R.string.size_of_icons) {
                    config.contactThumbnailsSize = it as Int
                    settingsThumbnailsSize.text = getThumbnailsSizeText()
                    config.needRestart = true
                }
            } else {
                RxAnimation.from(settingsThumbnailsSizeHolder)
                    .shake(shakeTranslation = 2f)
                    .subscribe()

                showSnackbar(binding.root)
            }
        }
    }

    private fun getThumbnailsSizeText() = getString(
        when (baseConfig.contactThumbnailsSize) {
            CONTACT_THUMBNAILS_SIZE_SMALL -> com.goodwy.commons.R.string.small
            CONTACT_THUMBNAILS_SIZE_MEDIUM -> com.goodwy.commons.R.string.medium
            CONTACT_THUMBNAILS_SIZE_LARGE -> com.goodwy.commons.R.string.large
            else -> com.goodwy.commons.R.string.extra_large
        }
    )

    private fun setupShowOnlyFilename() {
        binding.apply {
            settingsShowOnlyFilename.isChecked = config.showOnlyFilename
            settingsShowOnlyFilenameHolder.setOnClickListener {
                settingsShowOnlyFilename.toggle()
                config.showOnlyFilename = settingsShowOnlyFilename.isChecked
            }
        }
    }

    private fun setupShowSearchBar() = binding.apply {
        settingsShowSearchBar.isChecked = config.showSearchBar
        settingsShowSearchBarHolder.setOnClickListener {
            settingsShowSearchBar.toggle()
            config.showSearchBar = settingsShowSearchBar.isChecked
            config.needRestart = true
        }
    }

    private fun setupChangeColourTopBar() {
        binding.apply {
            settingsChangeColourTopBar.isChecked = config.changeColourTopBar
            settingsChangeColourTopBarHolder.setOnClickListener {
                settingsChangeColourTopBar.toggle()
                config.changeColourTopBar = settingsChangeColourTopBar.isChecked
                config.needRestart = true
            }
        }
    }

    private fun setupTipJar() = binding.apply {
        settingsTipJarHolder.apply {
            beVisibleIf(isPro())
            background.applyColorFilter(getColoredMaterialStatusBarColor().lightenColor(4))
            setOnClickListener {
                launchPurchase()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupAbout() = binding.apply {
        val flavorName = BuildConfig.FLAVOR
        val storeDisplayName = when (flavorName) {
            "gplay" -> "Google Play"
            "foss" -> "FOSS"
            "rustore" -> "RuStore"
            else -> "Huawei"
        }
        val versionName = BuildConfig.VERSION_NAME
        val fullVersionText = "Version: $versionName ($storeDisplayName)"

        settingsAboutVersion.text = fullVersionText
        settingsAboutHolder.setOnClickListener {
            launchAbout()
        }
    }

    private fun launchPurchase() {
        startPurchaseActivity(
            R.string.app_name_g,
            productIdList = arrayListOf(productIdX1, productIdX2, productIdX3),
            productIdListRu = arrayListOf(productIdX1, productIdX2, productIdX3),
            subscriptionIdList = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
            subscriptionIdListRu = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
            subscriptionYearIdList = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
            subscriptionYearIdListRu = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
            showCollection = false
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        updateMenuItemColors(menu)
        return super.onCreateOptionsMenu(menu)
    }

    private fun setupOptionsMenu() {
        binding.settingsToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.whats_new -> {
                    WhatsNewDialog(this@SettingsActivity, whatsNewList()) //arrayListOf(whatsNewList().last())
                    true
                }
                else -> false
            }
        }
    }
}
