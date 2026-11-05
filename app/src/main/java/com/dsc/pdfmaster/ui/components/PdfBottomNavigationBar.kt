package com.dsc.pdfmaster.ui.components

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dsc.pdfmaster.R
import com.dsc.pdfmaster.ui.ScreenRoute

data class NavItem(
    val route: ScreenRoute,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

val navItems = listOf(
    NavItem(
        route = ScreenRoute.HOME,
        labelRes = R.string.nav_dashboard,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        tag = "nav_home"
    ),
    NavItem(
        route = ScreenRoute.RECENT_FILES,
        labelRes = R.string.nav_recent_pdfs,
        selectedIcon = Icons.Filled.Folder,
        unselectedIcon = Icons.Outlined.Folder,
        tag = "nav_recent"
    ),
    NavItem(
        route = ScreenRoute.PDF_VIEWER,
        labelRes = R.string.nav_reader,
        selectedIcon = Icons.Filled.PictureAsPdf,
        unselectedIcon = Icons.Outlined.PictureAsPdf,
        tag = "nav_viewer"
    ),
    NavItem(
        route = ScreenRoute.SETTINGS,
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        tag = "nav_settings"
    )
)

@Composable
fun PdfBottomNavigationBar(
    currentRoute: ScreenRoute,
    onNavigate: (ScreenRoute) -> Unit
) {
    NavigationBar(
        modifier = Modifier.navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        navItems.forEach { item ->
            val isSelected = currentRoute == item.route
            val labelText = stringResource(item.labelRes)
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = labelText
                    )
                },
                label = { Text(labelText) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag(item.tag)
            )
        }
    }
}
