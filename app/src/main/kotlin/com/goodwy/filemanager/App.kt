package com.goodwy.filemanager

import com.github.ajalt.reprint.core.Reprint
import com.goodwy.commons.RightApp
import com.goodwy.commons.helpers.PurchaseHelper

class App : RightApp() {
    override val isAppLockFeatureAvailable = true

    override fun onCreate() {
        super.onCreate()
        Reprint.initialize(this)
        PurchaseHelper().initPurchaseIfNeed(this, "309929407")
    }
}
