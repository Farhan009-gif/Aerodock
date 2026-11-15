package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalAerodockExtraColors

data class AerodockTabItem(
    val label: String,
    val icon: ImageVector
)

// The six sections in bar order. Icons match the Home section cards.
val AerodockTabItems: List<AerodockTabItem> = listOf(
    AerodockTabItem("Home", LucideIcons.House),
    AerodockTabItem("Chat", LucideIcons.Chat),
    AerodockTabItem("Create", LucideIcons.Image),
    AerodockTabItem("Autopilot", LucideIcons.Bot),
    AerodockTabItem("Connect", LucideIcons.Plug),
    AerodockTabItem("More", LucideIcons.MoreHorizontal)
)

/**
 * Soft Dark tab bar (spec Part 3): no container and no fill — it sits on
 * the screen background under a 1 dp divider. Icon-only tabs; the active
 * tab carries a 4 dp accent dot below its icon, and inactive tabs keep
 * the same dot space so the icons never shift. Labels stay hidden unless
 * [showLabels] is turned on.
 */
@Composable
fun AerodockDotTabBar(
    items: List<AerodockTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showLabels: Boolean = false
) {
    val dividerColor = LocalAerodockExtraColors.current.tabDivider
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(dividerColor)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp)
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val tint = if (selected) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(index) }
                        .padding(vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                    )
                    if (showLabels) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            fontSize = 11.sp,
                            color = tint,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
