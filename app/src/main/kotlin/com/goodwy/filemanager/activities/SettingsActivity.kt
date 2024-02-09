package com.goodwy.filemanager.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.goodwy.commons.dialogs.*
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.helpers.rustore.RuStoreHelper
import com.goodwy.commons.helpers.rustore.model.StartPurchasesEvent
import com.goodwy.commons.models.FAQItem
import com.goodwy.commons.models.RadioItem
import com.goodwy.filemanager.BuildConfig
import com.goodwy.filemanager.R
import com.goodwy.filemanager.databinding.ActivitySettingsBinding
import com.goodwy.filemanager.dialogs.ManageVisibleTabsDialog
import com.goodwy.filemanager.extensions.config
import com.goodwy.filemanager.helpers.FOLDER_HOME
import com.goodwy.filemanager.helpers.FOLDER_INTERNAL
import com.goodwy.filemanager.helpers.FOLDER_LAST_USED
import com.goodwy.filemanager.helpers.RootHelpers
import kotlinx.coroutines.launch
import ru.rustore.sdk.core.feature.model.FeatureAvailabilityResult
import java.util.Locale
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {
    private val binding by viewBinding(ActivitySettingsBinding::inflate)

    private val purchaseHelper = PurchaseHelper(this)
    private val ruStoreHelper = RuStoreHelper(this)

    private val productIdX1 = BuildConfig.PRODUCT_ID_X1
    private val productIdX2 = BuildConfig.PRODUCT_ID_X2
    private val productIdX3 = BuildConfig.PRODUCT_ID_X3
    private val subscriptionIdX1 = BuildConfig.SUBSCRIPTION_ID_X1
    private val subscriptionIdX2 = BuildConfig.SUBSCRIPTION_ID_X2
    private val subscriptionIdX3 = BuildConfig.SUBSCRIPTION_ID_X3
    private val subscriptionYearIdX1 = BuildConfig.SUBSCRIPTION_YEAR_ID_X1
    private val subscriptionYearIdX2 = BuildConfig.SUBSCRIPTION_YEAR_ID_X2
    private val subscriptionYearIdX3 = BuildConfig.SUBSCRIPTION_YEAR_ID_X3

    private var ruStoreIsConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.apply {
            updateMaterialActivityViews(settingsCoordinator, settingsHolder, useTransparentNavigation = true, useTopSearchMenu = false)
            setupMaterialScrollListener(settingsNestedScrollview, settingsToolbar)
        }

        if (isPlayStoreInstalled()) {
            //PlayStore
            purchaseHelper.initBillingClient()
            val iapList: ArrayList<String> = arrayListOf(productIdX1, productIdX2, productIdX3)
            val subList: java.util.ArrayList<String> = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3, subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3)
            purchaseHelper.retrieveDonation(iapList, subList)

            purchaseHelper.isIapPurchased.observe(this) {
                when (it) {
                    is Tipping.Succeeded -> {
                        config.isPro = true
                        updatePro()
                    }
                    is Tipping.NoTips -> {
                        config.isPro = false
                        updatePro()
                    }
                    is Tipping.FailedToLoad -> {
                    }
                }
            }

            purchaseHelper.isSupPurchased.observe(this) {
                when (it) {
                    is Tipping.Succeeded -> {
                        config.isProSubs = true
                        updatePro()
                    }
                    is Tipping.NoTips -> {
                        config.isProSubs = false
                        updatePro()
                    }
                    is Tipping.FailedToLoad -> {
                    }
                }
            }
        }
        if (isRuStoreInstalled()) {
            //RuStore
            ruStoreHelper.checkPurchasesAvailability()

            lifecycleScope.launch {
                ruStoreHelper.eventStart
                    .flowWithLifecycle(lifecycle)
                    .collect { event ->
                        handleEventStart(event)
                    }
            }

            lifecycleScope.launch {
                ruStoreHelper.statePurchased
                    .flowWithLifecycle(lifecycle)
                    .collect { state ->
                        //update of purchased
                        if (!state.isLoading && ruStoreIsConnected) {
                            baseConfig.isProRuStore = state.purchases.firstOrNull() != null
                            updatePro()
                        }
                    }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.settingsToolbar, NavigationIcon.Arrow)

        setupPurchaseThankYou()
        setupCustomizeColors()
        setupMaterialDesign3()
        setupOverflowIcon()

        setupManageShownTabs()
        setupUseIconTabs()
        setupScreenSlideAnimation()
        setupEnablePullToRefresh()
        setupShowHomeButton()

        setupDefaultFolder()
        setupManageFavorites()
        setupPressBackTwice()
        setupFontSize()
        setupChangeDateTimeFormat()
        setupUseEnglish()
        setupLanguage()

        setupShowHidden()
        setupKeepLastModified()
        setupDeleteConfirmation()

        setupHiddenItemPasswordProtection()
        setupAppPasswordProtection()
        setupFileDeletionPasswordProtection()
        setupEnableRootAccess()

        setupShowFolderIcon()
        setupShowDividers()
        setupShowOnlyFilename()

        setupTipJar()
        setupAbout()

        updateTextColors(binding.settingsNestedScrollview)

        binding.apply {
            arrayOf(
                settingsAppearanceLabel,
                settingsTabsLabel,
                settingsGeneralLabel,
                settingsFileOperationsLabel,
                settingsSecurityLabel,
                settingsListViewLabel,
                settingsOtherLabel
            ).forEach {
                it.setTextColor(getProperPrimaryColor())
            }
        }

        binding.apply {
            arrayOf(
                settingsColorCustomizationHolder,
                settingsTabsHolder,
                settingsGeneralHolder,
                settingsFileOperationsHolder,
                settingsSecurityHolder,
                settingsListViewHolder,
                settingsOtherHolder
            ).forEach {
                it.background.applyColorFilter(getBottomNavigationBackgroundColor())
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
//            settingsCustomizeColorsLabel.text = if (isPro) {
//                getString(R.string.customize_colors)
//            } else {
//                getString(R.string.customize_colors_locked)
//            }
            settingsTipJarHolder.beVisibleIf(isPro)
        }
    }

    private fun setupPurchaseThankYou() {
        binding.apply {
            settingsPurchaseThankYouHolder.beGoneIf(isPro())
            settingsPurchaseThankYouHolder.setOnClickListener {
                launchPurchase()
            }
            moreButton.setOnClickListener {
                launchPurchase()
            }
            val appDrawable = resources.getColoredDrawableWithColor(this@SettingsActivity, R.drawable.ic_plus_support, getProperPrimaryColor())
            purchaseLogo.setImageDrawable(appDrawable)
            val drawable = resources.getColoredDrawableWithColor(this@SettingsActivity, R.drawable.button_gray_bg, getProperPrimaryColor())
            moreButton.background = drawable
            moreButton.setTextColor(getProperBackgroundColor())
            moreButton.setPadding(2, 2, 2, 2)
        }
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
                    true,
                    isCollection = false,
                    licensingKey = BuildConfig.GOOGLE_PLAY_LICENSING_KEY,
                    productIdList = arrayListOf(productIdX1, productIdX2, productIdX3),
                    productIdListRu = arrayListOf(productIdX1, productIdX2, productIdX3),
                    subscriptionIdList = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
                    subscriptionIdListRu = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
                    subscriptionYearIdList = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
                    subscriptionYearIdListRu = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
                    playStoreInstalled = isPlayStoreInstalled(),
                    ruStoreInstalled = isRuStoreInstalled()
                )
            }
        }
    }

    private fun setupMaterialDesign3() {
        binding.apply {
            settingsMaterialDesign3.isChecked = config.materialDesign3
            settingsMaterialDesign3Holder.setOnClickListener {
                settingsMaterialDesign3.toggle()
                config.materialDesign3 = settingsMaterialDesign3.isChecked
                config.tabsChanged = true
            }
        }
    }

    private fun setupOverflowIcon() {
        binding.apply {
            settingsOverflowIcon.applyColorFilter(getProperTextColor())
            settingsOverflowIcon.setImageResource(getOverflowIcon(baseConfig.overflowIcon))
            settingsOverflowIconHolder.setOnClickListener {
                OverflowIconDialog(this@SettingsActivity) {
                    settingsOverflowIcon.setImageResource(getOverflowIcon(baseConfig.overflowIcon))
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
                config.tabsChanged = true
            }
        }
    }

    private fun setupScreenSlideAnimation() {
        binding.apply {
            settingsScreenSlideAnimation.text = getScreenSlideAnimationText()
            settingsScreenSlideAnimationHolder.setOnClickListener {
                val items = arrayListOf(
                    RadioItem(0, getString(R.string.no)),
                    RadioItem(1, getString(R.string.screen_slide_animation_zoomout)),
                    RadioItem(2, getString(R.string.screen_slide_animation_depth))
                )

                RadioGroupDialog(this@SettingsActivity, items, config.screenSlideAnimation) {
                    config.screenSlideAnimation = it as Int
                    config.tabsChanged = true
                    settingsScreenSlideAnimation.text = getScreenSlideAnimationText()
                }
            }
        }
    }

    private fun setupDefaultFolder() {
        binding.apply {
            settingsDefaultFolder.text = getDefaultFolderText()
            settingsDefaultFolderHolder.setOnClickListener {
                val items = arrayListOf(
                    RadioItem(FOLDER_LAST_USED, getString(R.string.last_used_g)),
                    RadioItem(FOLDER_HOME, getString(R.string.home_folder_g)),
                    RadioItem(FOLDER_INTERNAL, getString(R.string.internal))
                )

                RadioGroupDialog(this@SettingsActivity, items, config.defaultFolder) {
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

                RadioGroupDialog(this@SettingsActivity, items, config.fontSize) {
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

    private fun setupShowOnlyFilename() {
        binding.apply {
            settingsShowOnlyFilename.isChecked = config.showOnlyFilename
            settingsShowOnlyFilenameHolder.setOnClickListener {
                settingsShowOnlyFilename.toggle()
                config.showOnlyFilename = settingsShowOnlyFilename.isChecked
            }
        }
    }

    private fun setupTipJar() {
        binding.settingsTipJarHolder.beVisibleIf(isPro())
        binding.settingsTipJarHolder.background.applyColorFilter(getBottomNavigationBackgroundColor().lightenColor(4))
        binding.settingsTipJarWrapper.setOnClickListener {
            launchPurchase()
        }
    }

    private fun setupAbout() {
        binding.settingsAboutVersion.text = "Version: " + BuildConfig.VERSION_NAME
        binding.settingsAboutHolder.setOnClickListener {
            launchAbout()
        }
    }

    private fun launchAbout() {
        val licenses =
            LICENSE_GLIDE or LICENSE_PATTERN or LICENSE_REPRINT or LICENSE_GESTURE_VIEWS or LICENSE_PDF_VIEWER or LICENSE_AUTOFITTEXTVIEW or LICENSE_ZIP4J

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_3_title_commons, R.string.faq_3_text_commons),
            FAQItem(R.string.faq_9_title_commons, R.string.faq_9_text_commons)
        )

        if (!resources.getBoolean(R.bool.hide_google_relations)) {
            faqItems.add(FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons))
            //faqItems.add(FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons))
            faqItems.add(FAQItem(R.string.faq_7_title_commons, R.string.faq_7_text_commons))
            faqItems.add(FAQItem(R.string.faq_10_title_commons, R.string.faq_10_text_commons))
        }

        startAboutActivity(
            appNameId = R.string.app_name_g,
            licenseMask = licenses,
            versionName = BuildConfig.VERSION_NAME,
            faqItems = faqItems,
            showFAQBeforeMail = true,
            licensingKey = BuildConfig.GOOGLE_PLAY_LICENSING_KEY,
            productIdList = arrayListOf(productIdX1, productIdX2, productIdX3),
            productIdListRu = arrayListOf(productIdX1, productIdX2, productIdX3),
            subscriptionIdList = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
            subscriptionIdListRu = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
            subscriptionYearIdList = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
            subscriptionYearIdListRu = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
            playStoreInstalled = isPlayStoreInstalled(),
            ruStoreInstalled = isRuStoreInstalled())
    }

    private fun launchPurchase() {
        startPurchaseActivity(
            R.string.app_name_g,
            BuildConfig.GOOGLE_PLAY_LICENSING_KEY,
            productIdList = arrayListOf(productIdX1, productIdX2, productIdX3),
            productIdListRu = arrayListOf(productIdX1, productIdX2, productIdX3),
            subscriptionIdList = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
            subscriptionIdListRu = arrayListOf(subscriptionIdX1, subscriptionIdX2, subscriptionIdX3),
            subscriptionYearIdList = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
            subscriptionYearIdListRu = arrayListOf(subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3),
            playStoreInstalled = isPlayStoreInstalled(),
            ruStoreInstalled = isRuStoreInstalled(),
            showCollection = false)
    }

    private fun updateProducts() {
        val productList: java.util.ArrayList<String> = arrayListOf(productIdX1, productIdX2, productIdX3, subscriptionIdX1, subscriptionIdX2, subscriptionIdX3, subscriptionYearIdX1, subscriptionYearIdX2, subscriptionYearIdX3)
        ruStoreHelper.getProducts(productList)
    }

    private fun handleEventStart(event: StartPurchasesEvent) {
        when (event) {
            is StartPurchasesEvent.PurchasesAvailability -> {
                when (event.availability) {
                    is FeatureAvailabilityResult.Available -> {
                        //Process purchases available
                        updateProducts()
                        ruStoreIsConnected = true
                    }

                    is FeatureAvailabilityResult.Unavailable -> {
                        //toast(event.availability.cause.message ?: "Process purchases unavailable", Toast.LENGTH_LONG)
                    }

                    else -> {}
                }
            }

            is StartPurchasesEvent.Error -> {
                //toast(event.throwable.message ?: "Process unknown error", Toast.LENGTH_LONG)
            }
        }
    }
}
