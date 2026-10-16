package com.example.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.ActiveTab

@Composable
fun AppBottomNavigation(
    currentTab: ActiveTab,
    onTabSelected: (ActiveTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .testTag("app_bottom_navigation"),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        NavigationBarItem(
            selected = currentTab == ActiveTab.SCAN,
            onClick = { onTabSelected(ActiveTab.SCAN) },
            icon = {
                Icon(
                    imageVector = if (currentTab == ActiveTab.SCAN) Icons.Filled.CenterFocusStrong else Icons.Outlined.CenterFocusStrong,
                    contentDescription = stringResource(R.string.tab_scan),
                    modifier = Modifier.size(24.dp)
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.tab_scan),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (currentTab == ActiveTab.SCAN) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp
                    )
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.testTag("tab_scan")
        )

        NavigationBarItem(
            selected = currentTab == ActiveTab.CALCULATOR,
            onClick = { onTabSelected(ActiveTab.CALCULATOR) },
            icon = {
                Icon(
                    imageVector = if (currentTab == ActiveTab.CALCULATOR) Icons.Filled.Calculate else Icons.Outlined.Calculate,
                    contentDescription = stringResource(R.string.tab_calculator),
                    modifier = Modifier.size(24.dp)
                )
            },
            label = {
                Text(
                    text = stringResource(R.string.tab_calculator),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (currentTab == ActiveTab.CALCULATOR) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp
                    )
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.testTag("tab_calculator")
        )
    }
}
