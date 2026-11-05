package com.dsc.pdfmaster.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsc.pdfmaster.R

private data class OnboardingPageData(
    val titleRes: Int,
    val bodyRes: Int,
    val icon: ImageVector,
    val iconDescriptionRes: Int
)

private val onboardingPages = listOf(
    OnboardingPageData(
        titleRes = R.string.onboarding_page1_title,
        bodyRes = R.string.onboarding_page1_body,
        icon = Icons.Default.Security,
        iconDescriptionRes = R.string.onboarding_page1_title
    ),
    OnboardingPageData(
        titleRes = R.string.onboarding_page2_title,
        bodyRes = R.string.onboarding_page2_body,
        icon = Icons.Default.PictureAsPdf,
        iconDescriptionRes = R.string.onboarding_page2_title
    ),
    OnboardingPageData(
        titleRes = R.string.onboarding_page3_title,
        bodyRes = R.string.onboarding_page3_body,
        icon = Icons.Default.Draw,
        iconDescriptionRes = R.string.onboarding_page3_title
    )
)

@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPage by rememberSaveable { mutableIntStateOf(0) }
    val page = onboardingPages[currentPage]

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("onboarding_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.onboarding_step_indicator,
                            currentPage + 1,
                            onboardingPages.size
                        ),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("onboarding_step_indicator")
                    )

                    TextButton(
                        onClick = onFinishOnboarding,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("onboarding_skip_button")
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_skip),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = page.icon,
                                contentDescription = stringResource(page.iconDescriptionRes),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = stringResource(page.titleRes),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .semantics { heading() }
                                .testTag("onboarding_page_title")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(page.bodyRes),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("onboarding_page_body")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Page indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    onboardingPages.indices.forEach { idx ->
                        val isSelected = idx == currentPage
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(if (isSelected) 28.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                    }
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentPage > 0) {
                        OutlinedButton(
                            onClick = { currentPage-- },
                            modifier = Modifier
                                .weight(1f)
                                .sizeIn(minWidth = 48.dp, minHeight = 52.dp)
                                .testTag("onboarding_previous_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.cd_navigate_back))
                        }
                    }

                    Button(
                        onClick = {
                            if (currentPage < onboardingPages.lastIndex) {
                                currentPage++
                            } else {
                                onFinishOnboarding()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .sizeIn(minWidth = 48.dp, minHeight = 52.dp)
                            .testTag("onboarding_next_or_finish_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (currentPage < onboardingPages.lastIndex) {
                                stringResource(R.string.onboarding_next)
                            } else {
                                stringResource(R.string.onboarding_get_started)
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
