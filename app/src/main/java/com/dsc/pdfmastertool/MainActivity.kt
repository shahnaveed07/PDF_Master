package com.dsc.pdfmastertool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.dsc.pdfmastertool.ui.PdfMasterApp
import com.dsc.pdfmastertool.ui.PdfMasterViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[PdfMasterViewModel::class.java]

        setContent {
            PdfMasterApp(viewModel = viewModel)
        }
    }
}
