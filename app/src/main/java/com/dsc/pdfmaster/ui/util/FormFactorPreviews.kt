package com.dsc.pdfmaster.ui.util

import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Small Phone", device = "spec:width=360dp,height=640dp,dpi=160", showBackground = true)
@Preview(name = "Large Phone", device = "spec:width=412dp,height=915dp,dpi=420", showBackground = true)
@Preview(name = "Foldable", device = "spec:width=673dp,height=841dp,dpi=300", showBackground = true)
@Preview(name = "Tablet", device = "spec:width=1280dp,height=800dp,dpi=240", showBackground = true)
@Preview(name = "Landscape Phone", device = "spec:width=640dp,height=360dp,dpi=160", showBackground = true)
@Preview(name = "Font Scale 2.0", device = "spec:width=360dp,height=640dp,dpi=160", fontScale = 2.0f, showBackground = true)
annotation class FormFactorPreviews
