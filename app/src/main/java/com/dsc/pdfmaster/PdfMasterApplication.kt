package com.dsc.pdfmaster

import android.app.Application
import com.dsc.pdfmaster.di.AppModule
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PdfMasterApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        AppModule.initialize(this)
    }
}
