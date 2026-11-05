package com.dsc.pdfmaster

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.screens.ConvertPdfScreen
import com.dsc.pdfmaster.ui.theme.PdfMasterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConvertPdfScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var viewModel: PdfMasterViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        viewModel = PdfMasterViewModel(app)
        viewModel.resetProcessingState()
        viewModel.clearConversionState()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    }

    @Test
    fun `opening Convert screen displays converter dashboard first without preselecting Word form`() {
        var backClicked = false

        composeTestRule.setContent {
            PdfMasterTheme {
                ConvertPdfScreen(
                    viewModel = viewModel,
                    onBack = { backClicked = true }
                )
            }
        }

        // Verify dashboard heading & subtitle are displayed
        composeTestRule.onNodeWithText("Convert your files").assertIsDisplayed()
        composeTestRule.onNodeWithText("Choose a format and convert files directly on your device.").assertIsDisplayed()

        // Verify category section headers are displayed
        composeTestRule.onNodeWithText("OFFICE TO PDF").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("PDF CONVERSION").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("IMAGE CONVERSION").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("TEXT CONVERSION").performScrollTo().assertIsDisplayed()

        // Verify contextual conversion form elements (e.g. office output name input) are NOT shown initially
        composeTestRule.onNodeWithTag("office_output_name_input").assertDoesNotExist()
        composeTestRule.onNodeWithTag("execute_convert_office_to_pdf_button").assertDoesNotExist()

        assertFalse("Back should not be triggered initially", backClicked)
    }

    @Test
    fun `all conversion operations are discoverable on the dashboard`() {
        composeTestRule.setContent {
            PdfMasterTheme {
                ConvertPdfScreen(viewModel = viewModel)
            }
        }

        // Verify all 9 options are discoverable
        composeTestRule.onNodeWithTag("option_card_word_to_pdf").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("option_card_excel_to_pdf").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("option_card_ppt_to_pdf").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("option_card_pdf_to_images").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("option_card_pdf_to_text").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("option_card_images_to_pdf").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("option_card_image_format").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("option_card_txt_to_pdf").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("option_card_typed_text_to_pdf").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `selecting Word to PDF opens dedicated contextual form`() {
        composeTestRule.setContent {
            PdfMasterTheme {
                ConvertPdfScreen(viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithText("Word to PDF (.docx)").performScrollTo().performClick()

        // Dashboard header should no longer be present
        composeTestRule.onNodeWithText("Convert your files").assertDoesNotExist()

        // Office form elements should now be visible
        composeTestRule.onNodeWithTag("office_fidelity_disclosure_card").assertExists()
        composeTestRule.onNodeWithTag("selected_office_file_name").assertExists()
        composeTestRule.onNodeWithTag("select_office_source_button").assertExists()
        composeTestRule.onNodeWithTag("office_output_name_input").assertExists()
        composeTestRule.onNodeWithTag("execute_convert_office_to_pdf_button").assertExists()

        // Password input should NOT be shown in Word form
        composeTestRule.onNodeWithTag("conversion_pdf_password_input").assertDoesNotExist()
    }

    @Test
    fun `selecting PDF to Images opens dedicated contextual form`() {
        composeTestRule.setContent {
            PdfMasterTheme {
                ConvertPdfScreen(viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithTag("option_card_pdf_to_images").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("selected_conversion_pdf_name").assertIsDisplayed()
        composeTestRule.onNodeWithTag("select_conversion_pdf_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("format_png_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("format_jpeg_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("execute_pdf_to_images_button").assertIsDisplayed()
    }

    @Test
    fun `selecting Images to PDF opens dedicated contextual form`() {
        composeTestRule.setContent {
            PdfMasterTheme {
                ConvertPdfScreen(viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithTag("option_card_images_to_pdf").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("pick_images_for_pdf_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("images_to_pdf_name_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("execute_images_to_pdf_button").assertIsDisplayed()
    }

    @Test
    fun `selecting PNG JPEG Converter opens dedicated contextual form`() {
        composeTestRule.setContent {
            PdfMasterTheme {
                ConvertPdfScreen(viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithTag("option_card_image_format").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("selected_conversion_image_name").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pick_single_image_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("target_jpeg_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("target_png_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("execute_image_format_button").assertIsDisplayed()
    }

    @Test
    fun `selecting TXT File to PDF opens dedicated contextual form`() {
        composeTestRule.setContent {
            PdfMasterTheme {
                ConvertPdfScreen(viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithTag("option_card_txt_to_pdf").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("selected_txt_file_name").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pick_txt_file_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("txt_to_pdf_name_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("execute_txt_file_to_pdf_button").assertIsDisplayed()
    }

    @Test
    fun `selecting Type Text to PDF opens dedicated contextual form`() {
        composeTestRule.setContent {
            PdfMasterTheme {
                ConvertPdfScreen(viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithText("Type Text to PDF").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("convert_title_input").assertExists()
        composeTestRule.onNodeWithTag("convert_body_input").assertExists()
        composeTestRule.onNodeWithTag("convert_text_to_pdf_button").assertExists()
    }

    @Test
    fun `internal back button returns from workflow to dashboard`() {
        var screenBackTriggered = false

        composeTestRule.setContent {
            PdfMasterTheme {
                ConvertPdfScreen(
                    viewModel = viewModel,
                    onBack = { screenBackTriggered = true }
                )
            }
        }

        // Open Word form
        composeTestRule.onNodeWithText("Word to PDF (.docx)").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("execute_convert_office_to_pdf_button").assertExists()

        // Press back in toolbar
        composeTestRule.onNodeWithTag("back_button").performClick()

        // Should be back on dashboard
        composeTestRule.onNodeWithText("Convert your files").assertIsDisplayed()
        composeTestRule.onNodeWithTag("execute_convert_office_to_pdf_button").assertDoesNotExist()

        // screenBackTriggered should still be false because internal back navigated to dashboard
        assertFalse("Screen back should not be called when returning to dashboard", screenBackTriggered)

        // Pressing back again on dashboard triggers screen back
        composeTestRule.onNodeWithTag("back_button").performClick()
        assertEquals(true, screenBackTriggered)
    }

    @Test
    fun `change format button in workflow header returns to dashboard`() {
        composeTestRule.setContent {
            PdfMasterTheme {
                ConvertPdfScreen(viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithTag("option_card_excel_to_pdf").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("change_format_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("change_format_button").performClick()

        composeTestRule.onNodeWithText("Convert your files").assertIsDisplayed()
    }
}
