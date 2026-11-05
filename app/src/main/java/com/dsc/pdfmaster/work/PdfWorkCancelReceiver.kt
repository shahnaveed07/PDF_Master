package com.dsc.pdfmaster.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PdfWorkCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == PdfWorkManager.ACTION_CANCEL_PDF_WORK) {
            val workId = intent.getStringExtra(PdfWorkManager.EXTRA_WORK_ID)
            PdfWorkManager.sharedInstance?.cancelActiveWork(workId)
        }
    }
}
