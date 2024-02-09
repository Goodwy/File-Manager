package com.goodwy.filemanager

import android.app.Application
import com.github.ajalt.reprint.core.Reprint
import com.goodwy.commons.extensions.checkUseEnglish
import com.goodwy.commons.helpers.rustore.RuStoreModule

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        checkUseEnglish()
        Reprint.initialize(this)
        RuStoreModule.install(this, "2063507033") //TODO rustore
    }
}
