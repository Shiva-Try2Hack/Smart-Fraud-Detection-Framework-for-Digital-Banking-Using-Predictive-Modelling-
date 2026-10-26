package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GPayBlue
import com.example.ui.theme.GPayGreen
import com.example.ui.viewmodel.AppScreen

@Composable
fun FloatingGlassTaskbar(
    currentScreen: AppScreen,
    isDarkMode: Boolean,
    onNavigate: (AppScreen) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVisible = currentScreen in listOf(
        AppScreen.HOME,
        AppScreen.TAP_AND_PAY,
        AppScreen.TRANSACTION_HISTORY
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Main row container holding the Music-player-style oblong capsule and the circular quick action
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Apple Music Player Oblong Floating Glass Pill
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isDarkMode) Color(0xDD1E2028) else Color(0xEEFFFFFF),
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .height(62.dp)
                        .clip(RoundedCornerShape(50))
                        .border(
                            width = 1.2.dp,
                            brush = Brush.verticalGradient(
                                colors = if (isDarkMode) {
                                    listOf(Color(0x60FFFFFF), Color(0x15FFFFFF))
                                } else {
                                    listOf(Color(0xFFFFFFFF), Color(0x4090A4AE))
                                }
                            ),
                            shape = RoundedCornerShape(50)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Home Tab
                        AppleMusicTabItem(
                            icon = Icons.Default.Home,
                            label = "Home",
                            isSelected = currentScreen == AppScreen.HOME,
                            isDarkMode = isDarkMode,
                            onClick = { onNavigate(AppScreen.HOME) }
                        )

                        // NFC Pay Tab
                        AppleMusicTabItem(
                            icon = Icons.Default.Contactless,
                            label = "Tap & Pay",
                            isSelected = currentScreen == AppScreen.TAP_AND_PAY,
                            isDarkMode = isDarkMode,
                            onClick = { onNavigate(AppScreen.TAP_AND_PAY) }
                        )

                        // History Tab
                        AppleMusicTabItem(
                            icon = Icons.Default.History,
                            label = "History",
                            isSelected = currentScreen == AppScreen.TRANSACTION_HISTORY,
                            isDarkMode = isDarkMode,
                            onClick = { onNavigate(AppScreen.TRANSACTION_HISTORY) }
                        )
                    }
                }

                // 2. Circular Glass Floating Button (Settings / Config) like the Search circle in the Music player example
                Surface(
                    shape = CircleShape,
                    color = if (isDarkMode) Color(0xDD1E2028) else Color(0xEEFFFFFF),
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .border(
                            width = 1.2.dp,
                            brush = Brush.verticalGradient(
                                colors = if (isDarkMode) {
                                    listOf(Color(0x60FFFFFF), Color(0x15FFFFFF))
                                } else {
                                    listOf(Color(0xFFFFFFFF), Color(0x4090A4AE))
                                }
                            ),
                            shape = CircleShape
                        )
                        .clickable(onClick = onOpenSettings)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Settings",
                            tint = if (isDarkMode) Color.White else Color(0xFF202124),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppleMusicTabItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val activeBg = if (isDarkMode) Color(0xFF383D4D) else Color(0xFFE0EBFC)
    val inactiveBg = Color.Transparent
    val activeContentColor = if (isDarkMode) Color.White else GPayBlue
    val inactiveContentColor = if (isDarkMode) Color(0xFF9AA0A6) else Color(0xFF5F6368)

    val contentColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isSelected) activeContentColor else inactiveContentColor,
        animationSpec = androidx.compose.animation.core.tween(300),
        label = "tabContentColor"
    )

    val surfaceBg by androidx.compose.animation.animateColorAsState(
        targetValue = if (isSelected) activeBg else inactiveBg,
        animationSpec = androidx.compose.animation.core.tween(300),
        label = "tabSurfaceBg"
    )

    val iconScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "tabIconScale"
    )

    Surface(
        shape = RoundedCornerShape(50),
        color = surfaceBg,
        shadowElevation = if (isSelected) 4.dp else 0.dp,
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 0.8.dp,
                        color = if (isDarkMode) Color(0x50FFFFFF) else Color(0x401A73E8),
                        shape = RoundedCornerShape(50)
                    )
                } else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .animateContentSize(
                    animationSpec = spring<IntSize>(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
                .padding(
                    horizontal = if (isSelected) 16.dp else 12.dp,
                    vertical = 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier
                    .size(22.dp)
                    .scale(iconScale)
            )

            AnimatedVisibility(
                visible = isSelected,
                enter = expandHorizontally(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeIn(animationSpec = tween(200)),
                exit = shrinkHorizontally(
                    animationSpec = tween(150)
                ) + fadeOut(animationSpec = tween(100))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
