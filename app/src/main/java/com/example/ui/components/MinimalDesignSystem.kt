package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import com.example.ui.theme.StatusGrayDot
import com.example.ui.theme.StatusGreenDot

/**
 * Consistent 1.5px thin-line Lucide-style vector icon set.
 * Active icons use the theme primary (lifted Sage in Concept A Dark,
 * Sage in the light theme), inactive ones use neutral gray.
 */
object LucideIcons {

    private inline fun lucideIcon(
        name: String,
        block: ImageVector.Builder.() -> Unit
    ): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()
    }

    private inline fun ImageVector.Builder.strokePath(
        pathBuilder: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit
    ) {
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathBuilder
        )
    }

    val Menu: ImageVector by lazy {
        lucideIcon("LucideMenu") {
            strokePath {
                moveTo(4f, 6f); lineTo(20f, 6f)
                moveTo(4f, 12f); lineTo(20f, 12f)
                moveTo(4f, 18f); lineTo(14f, 18f)
            }
        }
    }

    val MoreHorizontal: ImageVector by lazy {
        lucideIcon("LucideMoreHorizontal") {
            strokePath {
                moveTo(12f, 11f); arcToRelative(1f, 1f, 0f, true, true, 0f, 2f); arcToRelative(1f, 1f, 0f, true, true, 0f, -2f)
                moveTo(19f, 11f); arcToRelative(1f, 1f, 0f, true, true, 0f, 2f); arcToRelative(1f, 1f, 0f, true, true, 0f, -2f)
                moveTo(5f, 11f); arcToRelative(1f, 1f, 0f, true, true, 0f, 2f); arcToRelative(1f, 1f, 0f, true, true, 0f, -2f)
            }
        }
    }

    val ChevronDown: ImageVector by lazy {
        lucideIcon("LucideChevronDown") {
            strokePath {
                moveTo(6f, 9f)
                lineTo(12f, 15f)
                lineTo(18f, 9f)
            }
        }
    }

    val ChevronRight: ImageVector by lazy {
        lucideIcon("LucideChevronRight") {
            strokePath {
                moveTo(9f, 18f)
                lineTo(15f, 12f)
                lineTo(9f, 6f)
            }
        }
    }

    val Plus: ImageVector by lazy {
        lucideIcon("LucidePlus") {
            strokePath {
                moveTo(12f, 5f); lineTo(12f, 19f)
                moveTo(5f, 12f); lineTo(19f, 12f)
            }
        }
    }

    val ArrowUp: ImageVector by lazy {
        lucideIcon("LucideArrowUp") {
            strokePath {
                moveTo(12f, 19f); lineTo(12f, 5f)
                moveTo(5f, 12f); lineTo(12f, 5f); lineTo(19f, 12f)
            }
        }
    }

    val Check: ImageVector by lazy {
        lucideIcon("LucideCheck") {
            strokePath {
                moveTo(20f, 6f)
                lineTo(9f, 17f)
                lineTo(4f, 12f)
            }
        }
    }

    val X: ImageVector by lazy {
        lucideIcon("LucideX") {
            strokePath {
                moveTo(18f, 6f); lineTo(6f, 18f)
                moveTo(6f, 6f); lineTo(18f, 18f)
            }
        }
    }

    val Search: ImageVector by lazy {
        lucideIcon("LucideSearch") {
            strokePath {
                moveTo(11f, 4f)
                arcToRelative(7f, 7f, 0f, true, true, 0f, 14f)
                arcToRelative(7f, 7f, 0f, true, true, 0f, -14f)
                moveTo(20f, 20f); lineTo(16.15f, 16.15f)
            }
        }
    }

    val MessageSquare: ImageVector by lazy {
        lucideIcon("LucideMessageSquare") {
            strokePath {
                moveTo(21f, 15f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                lineTo(7f, 17f)
                lineTo(3f, 21f)
                lineTo(3f, 5f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                lineTo(19f, 3f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                close()
            }
        }
    }

    val Layers: ImageVector by lazy {
        lucideIcon("LucideLayers") {
            strokePath {
                moveTo(12f, 2f)
                lineTo(2f, 7f)
                lineTo(12f, 12f)
                lineTo(22f, 7f)
                close()
                moveTo(2f, 12f); lineTo(12f, 17f); lineTo(22f, 12f)
                moveTo(2f, 17f); lineTo(12f, 22f); lineTo(22f, 17f)
            }
        }
    }

    val Zap: ImageVector by lazy {
        lucideIcon("LucideZap") {
            strokePath {
                moveTo(13f, 2f)
                lineTo(3f, 14f)
                lineTo(12f, 14f)
                lineTo(11f, 22f)
                lineTo(21f, 10f)
                lineTo(12f, 10f)
                close()
            }
        }
    }

    val Link: ImageVector by lazy {
        lucideIcon("LucideLink") {
            strokePath {
                moveTo(10f, 13f)
                arcToRelative(5f, 5f, 0f, false, false, 7.54f, 0.54f)
                lineTo(20.54f, 10.54f)
                arcToRelative(5f, 5f, 0f, false, false, -7.07f, -7.07f)
                lineTo(11.75f, 5.18f)
                moveTo(14f, 11f)
                arcToRelative(5f, 5f, 0f, false, false, -7.54f, -0.54f)
                lineTo(3.46f, 13.46f)
                arcToRelative(5f, 5f, 0f, false, false, 7.07f, 7.07f)
                lineTo(12.24f, 18.82f)
            }
        }
    }

    val Grid: ImageVector by lazy {
        lucideIcon("LucideGrid") {
            strokePath {
                moveTo(3f, 3f); lineTo(10f, 3f); lineTo(10f, 10f); lineTo(3f, 10f); close()
                moveTo(14f, 3f); lineTo(21f, 3f); lineTo(21f, 10f); lineTo(14f, 10f); close()
                moveTo(14f, 14f); lineTo(21f, 14f); lineTo(21f, 21f); lineTo(14f, 21f); close()
                moveTo(3f, 14f); lineTo(10f, 14f); lineTo(10f, 21f); lineTo(3f, 21f); close()
            }
        }
    }

    val Sparkles: ImageVector by lazy {
        lucideIcon("LucideSparkles") {
            strokePath {
                moveTo(12f, 3f)
                lineTo(13.9f, 8.1f)
                lineTo(19f, 10f)
                lineTo(13.9f, 11.9f)
                lineTo(12f, 17f)
                lineTo(10.1f, 11.9f)
                lineTo(5f, 10f)
                lineTo(10.1f, 8.1f)
                close()
                moveTo(19f, 16f); lineTo(19f, 20f)
                moveTo(17f, 18f); lineTo(21f, 18f)
            }
        }
    }

    val Sliders: ImageVector by lazy {
        lucideIcon("LucideSliders") {
            strokePath {
                moveTo(4f, 21f); lineTo(4f, 14f)
                moveTo(4f, 10f); lineTo(4f, 3f)
                moveTo(12f, 21f); lineTo(12f, 12f)
                moveTo(12f, 8f); lineTo(12f, 3f)
                moveTo(20f, 21f); lineTo(20f, 16f)
                moveTo(20f, 12f); lineTo(20f, 3f)
                moveTo(2f, 14f); lineTo(6f, 14f)
                moveTo(10f, 8f); lineTo(14f, 8f)
                moveTo(18f, 16f); lineTo(22f, 16f)
            }
        }
    }

    val Terminal: ImageVector by lazy {
        lucideIcon("LucideTerminal") {
            strokePath {
                moveTo(4f, 17f)
                lineTo(10f, 11f)
                lineTo(4f, 5f)
                moveTo(12f, 19f)
                lineTo(20f, 19f)
            }
        }
    }

    val GitCompare: ImageVector by lazy {
        lucideIcon("LucideGitCompare") {
            strokePath {
                moveTo(16f, 3f); lineTo(21f, 3f); lineTo(21f, 8f)
                moveTo(4f, 20f); lineTo(21f, 3f)
                moveTo(21f, 16f); lineTo(21f, 21f); lineTo(16f, 21f)
                moveTo(15f, 15f); lineTo(21f, 21f)
                moveTo(4f, 4f); lineTo(9f, 9f)
            }
        }
    }

    val Mic: ImageVector by lazy {
        lucideIcon("LucideMic") {
            strokePath {
                moveTo(12f, 2f)
                arcToRelative(3f, 3f, 0f, false, false, -3f, 3f)
                lineTo(9f, 12f)
                arcToRelative(3f, 3f, 0f, false, false, 6f, 0f)
                lineTo(15f, 5f)
                arcToRelative(3f, 3f, 0f, false, false, -3f, -3f)
                close()
                moveTo(19f, 10f)
                lineTo(19f, 12f)
                arcToRelative(7f, 7f, 0f, false, true, -14f, 0f)
                lineTo(5f, 10f)
                moveTo(12f, 19f); lineTo(12f, 22f)
            }
        }
    }

    val Play: ImageVector by lazy {
        lucideIcon("LucidePlay") {
            strokePath {
                moveTo(6f, 4f)
                lineTo(20f, 12f)
                lineTo(6f, 20f)
                close()
            }
        }
    }

    val RotateCcw: ImageVector by lazy {
        lucideIcon("LucideRotateCcw") {
            strokePath {
                moveTo(3f, 12f)
                arcToRelative(9f, 9f, 0f, true, false, 9f, -9f)
                arcToRelative(9.75f, 9.75f, 0f, false, false, -6.74f, 2.74f)
                lineTo(3f, 8f)
                moveTo(3f, 3f); lineTo(3f, 8f); lineTo(8f, 8f)
            }
        }
    }

    val Key: ImageVector by lazy {
        lucideIcon("LucideKey") {
            strokePath {
                moveTo(15.5f, 3.5f)
                arcToRelative(4.5f, 4.5f, 0f, true, true, 0f, 9f)
                arcToRelative(4.5f, 4.5f, 0f, true, true, 0f, -9f)
                moveTo(12.3f, 11.2f)
                lineTo(3f, 20.5f)
                moveTo(6f, 17.5f); lineTo(8.5f, 20f)
            }
        }
    }

    val Edit2: ImageVector by lazy {
        lucideIcon("LucideEdit2") {
            strokePath {
                moveTo(17f, 3f)
                arcToRelative(2.83f, 2.83f, 0f, true, true, 4f, 4f)
                lineTo(7.5f, 20.5f)
                lineTo(2f, 22f)
                lineTo(3.5f, 16.5f)
                close()
            }
        }
    }

    val Trash2: ImageVector by lazy {
        lucideIcon("LucideTrash2") {
            strokePath {
                moveTo(3f, 6f); lineTo(21f, 6f)
                moveTo(19f, 6f); lineTo(19f, 20f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                lineTo(7f, 22f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                lineTo(5f, 6f)
                moveTo(8f, 6f); lineTo(8f, 4f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                lineTo(14f, 2f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                lineTo(16f, 6f)
            }
        }
    }

    val Copy: ImageVector by lazy {
        lucideIcon("LucideCopy") {
            strokePath {
                moveTo(9f, 9f); lineTo(20f, 9f); lineTo(20f, 20f); lineTo(9f, 20f); close()
                moveTo(5f, 15f); lineTo(4f, 15f)
                arcToRelative(1f, 1f, 0f, false, true, -1f, -1f)
                lineTo(3f, 4f)
                arcToRelative(1f, 1f, 0f, false, true, 1f, -1f)
                lineTo(14f, 3f)
                arcToRelative(1f, 1f, 0f, false, true, 1f, 1f)
                lineTo(15f, 5f)
            }
        }
    }

    val RefreshCw: ImageVector by lazy {
        lucideIcon("LucideRefreshCw") {
            strokePath {
                moveTo(21f, 12f)
                arcToRelative(9f, 9f, 0f, false, false, -9f, -9f)
                arcToRelative(9.75f, 9.75f, 0f, false, false, -6.74f, 2.74f)
                lineTo(3f, 8f)
                moveTo(3f, 3f); lineTo(3f, 8f); lineTo(8f, 8f)
                moveTo(3f, 12f)
                arcToRelative(9f, 9f, 0f, false, false, 9f, 9f)
                arcToRelative(9.75f, 9.75f, 0f, false, false, 6.74f, -2.74f)
                lineTo(21f, 16f)
                moveTo(16f, 16f); lineTo(21f, 16f); lineTo(21f, 21f)
            }
        }
    }

    val Image: ImageVector by lazy {
        lucideIcon("LucideImage") {
            strokePath {
                moveTo(5f, 3f); lineTo(19f, 3f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                lineTo(21f, 19f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                lineTo(5f, 21f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                lineTo(3f, 5f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                close()
                moveTo(9f, 7.5f)
                arcToRelative(1.5f, 1.5f, 0f, true, true, 0f, 3f)
                arcToRelative(1.5f, 1.5f, 0f, true, true, 0f, -3f)
                moveTo(21f, 15f)
                lineTo(16f, 10f)
                lineTo(5f, 21f)
            }
        }
    }

    val Video: ImageVector by lazy {
        lucideIcon("LucideVideo") {
            strokePath {
                moveTo(23f, 7f)
                lineTo(16f, 12f)
                lineTo(23f, 17f)
                close()
                moveTo(3f, 5f); lineTo(14f, 5f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                lineTo(16f, 17f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                lineTo(3f, 19f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                lineTo(1f, 7f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                close()
            }
        }
    }

    val Shield: ImageVector by lazy {
        lucideIcon("LucideShield") {
            strokePath {
                moveTo(12f, 22f)
                reflectiveCurveTo(20f, 18f, 20f, 12f)
                lineTo(20f, 5f)
                lineTo(12f, 2f)
                lineTo(4f, 5f)
                lineTo(4f, 12f)
                curveTo(4f, 18f, 12f, 22f, 12f, 22f)
                close()
            }
        }
    }

    val Activity: ImageVector by lazy {
        lucideIcon("LucideActivity") {
            strokePath {
                moveTo(22f, 12f)
                lineTo(18f, 12f)
                lineTo(15f, 21f)
                lineTo(9f, 3f)
                lineTo(6f, 12f)
                lineTo(2f, 12f)
            }
        }
    }

    val Sun: ImageVector by lazy {
        lucideIcon("LucideSun") {
            strokePath {
                moveTo(12f, 8f)
                arcToRelative(4f, 4f, 0f, true, true, 0f, 8f)
                arcToRelative(4f, 4f, 0f, true, true, 0f, -8f)
                moveTo(12f, 2f); lineTo(12f, 4f)
                moveTo(12f, 20f); lineTo(12f, 22f)
                moveTo(4.93f, 4.93f); lineTo(6.34f, 6.34f)
                moveTo(17.66f, 17.66f); lineTo(19.07f, 19.07f)
                moveTo(2f, 12f); lineTo(4f, 12f)
                moveTo(20f, 12f); lineTo(22f, 12f)
                moveTo(6.34f, 17.66f); lineTo(4.93f, 19.07f)
                moveTo(19.07f, 4.93f); lineTo(17.66f, 6.34f)
            }
        }
    }

    val Moon: ImageVector by lazy {
        lucideIcon("LucideMoon") {
            strokePath {
                moveTo(21f, 12.79f)
                arcToRelative(9f, 9f, 0f, true, true, -9.79f, -9.79f)
                arcToRelative(7f, 7f, 0f, false, false, 9.79f, 9.79f)
                close()
            }
        }
    }

    val Square: ImageVector by lazy {
        lucideIcon("LucideSquare") {
            strokePath {
                moveTo(6f, 6f); lineTo(18f, 6f); lineTo(18f, 18f); lineTo(6f, 18f); close()
            }
        }
    }

    val ChevronUp: ImageVector by lazy {
        lucideIcon("LucideChevronUp") {
            strokePath {
                moveTo(18f, 15f)
                lineTo(12f, 9f)
                lineTo(6f, 15f)
            }
        }
    }

    val Lock: ImageVector by lazy {
        lucideIcon("LucideLock") {
            strokePath {
                moveTo(5f, 11f); lineTo(19f, 11f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                lineTo(21f, 20f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                lineTo(5f, 22f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                lineTo(3f, 13f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                close()
                moveTo(7f, 11f); lineTo(7f, 7f)
                arcToRelative(5f, 5f, 0f, false, true, 10f, 0f)
                lineTo(17f, 11f)
            }
        }
    }

    val Bot: ImageVector by lazy {
        lucideIcon("LucideBot") {
            strokePath {
                moveTo(12f, 8f); lineTo(12f, 4f)
                moveTo(8f, 4f); lineTo(16f, 4f)
                moveTo(6f, 8f); lineTo(18f, 8f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                lineTo(20f, 18f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                lineTo(6f, 20f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                lineTo(4f, 10f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                close()
                moveTo(9f, 13f); lineTo(9f, 15f)
                moveTo(15f, 13f); lineTo(15f, 15f)
            }
        }
    }

    val Plug: ImageVector by lazy {
        lucideIcon("LucidePlug") {
            strokePath {
                moveTo(12f, 22f); lineTo(12f, 17f)
                moveTo(9f, 8f); lineTo(9f, 2f)
                moveTo(15f, 8f); lineTo(15f, 2f)
                moveTo(18f, 8f); lineTo(18f, 13f)
                arcToRelative(6f, 6f, 0f, false, true, -12f, 0f)
                lineTo(6f, 8f)
                close()
            }
        }
    }

    val History: ImageVector by lazy {
        lucideIcon("LucideHistory") {
            strokePath {
                moveTo(3f, 12f)
                arcToRelative(9f, 9f, 0f, true, false, 9f, -9f)
                arcToRelative(9.75f, 9.75f, 0f, false, false, -6.74f, 2.74f)
                lineTo(3f, 8f)
                moveTo(3f, 3f); lineTo(3f, 8f); lineTo(8f, 8f)
                moveTo(12f, 7f); lineTo(12f, 12f); lineTo(16f, 14f)
            }
        }
    }

    val Download: ImageVector by lazy {
        lucideIcon("LucideDownload") {
            strokePath {
                moveTo(21f, 15f); lineTo(21f, 19f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                lineTo(5f, 21f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                lineTo(3f, 15f)
                moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
                moveTo(12f, 15f); lineTo(12f, 3f)
            }
        }
    }

    val ArrowLeft: ImageVector by lazy {
        lucideIcon("LucideArrowLeft") {
            strokePath {
                moveTo(19f, 12f); lineTo(5f, 12f)
                moveTo(12f, 19f); lineTo(5f, 12f); lineTo(12f, 5f)
            }
        }
    }

    val Compass: ImageVector by lazy {
        lucideIcon("LucideCompass") {
            strokePath {
                moveTo(22f, 12f)
                arcToRelative(10f, 10f, 0f, true, true, -20f, 0f)
                arcToRelative(10f, 10f, 0f, true, true, 20f, 0f)
                moveTo(16.24f, 7.76f)
                lineTo(14.12f, 14.12f)
                lineTo(7.76f, 16.24f)
                lineTo(9.88f, 9.88f)
                close()
            }
        }
    }

    val GitBranch: ImageVector by lazy {
        lucideIcon("LucideGitBranch") {
            strokePath {
                moveTo(6f, 3f); lineTo(6f, 15f)
                moveTo(18f, 9f)
                arcToRelative(3f, 3f, 0f, true, false, 0f, -6f)
                arcToRelative(3f, 3f, 0f, true, false, 0f, 6f)
                moveTo(6f, 21f)
                arcToRelative(3f, 3f, 0f, true, false, 0f, -6f)
                arcToRelative(3f, 3f, 0f, true, false, 0f, 6f)
                moveTo(18f, 9f)
                arcToRelative(9f, 9f, 0f, false, true, -9f, 9f)
            }
        }
    }

    val Server: ImageVector by lazy {
        lucideIcon("LucideServer") {
            strokePath {
                moveTo(4f, 2f); lineTo(20f, 2f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                lineTo(22f, 8f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                lineTo(4f, 10f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                lineTo(2f, 4f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                moveTo(4f, 14f); lineTo(20f, 14f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                lineTo(22f, 20f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                lineTo(4f, 22f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                lineTo(2f, 16f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                moveTo(6f, 6f); lineTo(6.01f, 6f)
                moveTo(6f, 18f); lineTo(6.01f, 18f)
            }
        }
    }

    val AlertTriangle: ImageVector by lazy {
        lucideIcon("LucideAlertTriangle") {
            strokePath {
                moveTo(10.29f, 3.86f)
                lineTo(1.82f, 18f)
                arcToRelative(2f, 2f, 0f, false, false, 1.71f, 3f)
                lineTo(20.47f, 21f)
                arcToRelative(2f, 2f, 0f, false, false, 1.71f, -3f)
                lineTo(13.71f, 3.86f)
                arcToRelative(2f, 2f, 0f, false, false, -3.42f, 0f)
                moveTo(12f, 9f); lineTo(12f, 13f)
                moveTo(12f, 17f); lineTo(12.01f, 17f)
            }
        }
    }

    val Code: ImageVector by lazy {
        lucideIcon("LucideCode") {
            strokePath {
                moveTo(16f, 18f); lineTo(22f, 12f); lineTo(16f, 6f)
                moveTo(8f, 6f); lineTo(2f, 12f); lineTo(8f, 18f)
            }
        }
    }

    val Chat: ImageVector get() = MessageSquare
    val Close: ImageVector get() = X
    val Undo: ImageVector get() = RotateCcw
    val Bolt: ImageVector get() = Zap
    val Compare: ImageVector get() = GitCompare
    val Refresh: ImageVector get() = RefreshCw
    val Trash: ImageVector get() = Trash2
    val Edit: ImageVector get() = Edit2
    val Clock: ImageVector get() = History
}

// ============================================================================
// SINGLE REUSABLE MINIMAL COMPONENT LIBRARY
// (Button, Chip, Card, Sheet, Toast, Toggle, SegmentedControl, StatusDot, SectionHeader)
// ============================================================================

@Composable
fun MinimalSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = if (trailing != null) modifier.fillMaxWidth() else modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (trailing != null) Modifier.weight(1f, fill = false) else Modifier
        )
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun MinimalCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(contentPadding)
    } else {
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(contentPadding)
    }

    Column(
        modifier = cardModifier,
        content = content
    )
}

@Composable
fun MinimalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isPrimary: Boolean = true,
    filled: Boolean = isPrimary,
    enabled: Boolean = true,
    testTag: String? = null
) {
    val bgColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        filled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        filled -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderStroke = if (filled) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    val finalModifier = if (!testTag.isNullOrBlank()) {
        modifier.heightIn(min = 44.dp).testTag(testTag)
    } else {
        modifier.heightIn(min = 44.dp)
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = bgColor,
        border = borderStroke,
        modifier = finalModifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MinimalChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    testTag: String? = null
) {
    // Concept A: a selected chip is a FILLED accent pill in both modes.
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val iconTint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val finalModifier = if (!testTag.isNullOrBlank()) {
        modifier.heightIn(min = 36.dp).testTag(testTag)
    } else {
        modifier.heightIn(min = 36.dp)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = finalModifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun MinimalToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null
) {
    val finalModifier = if (!testTag.isNullOrBlank()) {
        modifier.testTag(testTag)
    } else {
        modifier
    }
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline
        ),
        modifier = finalModifier
    )
}

@Composable
fun DialogWindowFocusGuard(
    onRegisterCleaner: ((() -> Unit) -> Unit)? = null
): () -> Unit {
    val dialogFocusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val dialogKeyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val dialogView = androidx.compose.ui.platform.LocalView.current

    val clearDialogIme: () -> Unit = androidx.compose.runtime.remember(dialogFocusManager, dialogKeyboardController, dialogView) {
        {
            runCatching {
                dialogFocusManager.clearFocus(force = true)
                dialogKeyboardController?.hide()
                dialogView.clearFocus()
                val imm = dialogView.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                    as? android.view.inputmethod.InputMethodManager
                imm?.hideSoftInputFromWindow(dialogView.windowToken, 0)
            }
        }
    }

    androidx.compose.runtime.SideEffect {
        onRegisterCleaner?.invoke(clearDialogIme)
    }

    androidx.compose.runtime.DisposableEffect(dialogView) {
        onDispose {
            clearDialogIme()
        }
    }

    return clearDialogIme
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinimalSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val hostFocusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val hostKeyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val hostView = androidx.compose.ui.platform.LocalView.current
    val innerSheetCleanerState = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<(() -> Unit)?>(null)
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching {
            hostFocusManager.clearFocus(force = true)
            hostKeyboardController?.hide()
        }
    }

    val safeDismiss: () -> Unit = {
        innerSheetCleanerState.value?.invoke()
        runCatching {
            hostFocusManager.clearFocus(force = true)
            hostKeyboardController?.hide()
            val imm = hostView.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as? android.view.inputmethod.InputMethodManager
            imm?.hideSoftInputFromWindow(hostView.windowToken, 0)
        }
        onDismiss()
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = safeDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier
    ) {
        DialogWindowFocusGuard(onRegisterCleaner = { innerSheetCleanerState.value = it })
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (title != null) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            content()
        }
    }
}

/**
 * Slim, rounded dark toast card at the top with a small check icon,
 * a one-line summary, and an "Undo" text button. Auto-dismisses after 4s.
 */
@Composable
fun MinimalToast(
    message: String?,
    onUndo: (() -> Unit)? = null,
    canUndo: Boolean = onUndo != null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = fadeIn(tween(170)) + slideInVertically(tween(170)) { -it / 2 },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 2 },
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("minimal_action_toast")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = LucideIcons.Check,
                    contentDescription = "Done",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = message.orEmpty().removePrefix("✓ ").trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (canUndo && onUndo != null) {
                    Text(
                        text = "Undo",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(onClick = onUndo)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("minimal_toast_undo_button")
                    )
                }
                Icon(
                    imageVector = LucideIcons.X,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(onClick = onDismiss)
                )
            }
        }
    }
}

@Composable
fun MinimalSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                    ),
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun MinimalStatusDot(
    isOnline: Boolean = false,
    isConnected: Boolean = isOnline,
    size: Dp = 8.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isConnected) StatusGreenDot else StatusGrayDot)
    )
}

/**
 * Aerodock logo rendered natively at 512x512:
 * - Background: rounded square (~22% corner radius, rx=112 at 512), solid #0B0D10
 * - Mark: an aeroplane taking off over a runway strip (Material flight_takeoff
 *   glyph), filled with the theme accent
 */
@Composable
fun RunwayLogoIcon(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val logoAccent = MaterialTheme.colorScheme.primary
    val runwayVector = androidx.compose.runtime.remember(logoAccent) {
        ImageVector.Builder(
            name = "AerodockLogo512",
            defaultWidth = 512.dp,
            defaultHeight = 512.dp,
            viewportWidth = 512f,
            viewportHeight = 512f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF0B0D10)),
                stroke = null
            ) {
                moveTo(112f, 0f)
                lineTo(400f, 0f)
                arcTo(112f, 112f, 0f, false, true, 512f, 112f)
                lineTo(512f, 400f)
                arcTo(112f, 112f, 0f, false, true, 400f, 512f)
                lineTo(112f, 512f)
                arcTo(112f, 112f, 0f, false, true, 0f, 400f)
                lineTo(0f, 112f)
                arcTo(112f, 112f, 0f, false, true, 112f, 0f)
                close()
            }
            group(
                scaleX = 15.5f,
                scaleY = 15.5f,
                translationX = 71f,
                translationY = 69f
            ) {
                path(
                    fill = SolidColor(logoAccent),
                    stroke = null
                ) {
                    moveTo(2.50f, 19.00f)
                    lineTo(21.50f, 19.00f)
                    lineTo(21.50f, 21.00f)
                    lineTo(2.50f, 21.00f)
                    lineTo(2.50f, 19.00f)
                    close()
                    moveTo(22.07f, 9.64f)
                    curveTo(21.86f, 8.84f, 21.03f, 8.36f, 20.23f, 8.58f)
                    lineTo(14.92f, 10.00f)
                    lineTo(8.02f, 3.57f)
                    lineTo(6.09f, 4.08f)
                    lineTo(10.23f, 11.25f)
                    lineTo(5.26f, 12.58f)
                    lineTo(3.29f, 11.04f)
                    lineTo(1.84f, 11.43f)
                    lineTo(4.43f, 15.92f)
                    curveTo(4.43f, 15.92f, 11.55f, 14.02f, 21.00f, 11.49f)
                    curveTo(21.81f, 11.26f, 22.28f, 10.44f, 22.07f, 9.64f)
                    close()
                }
            }
        }.build()
    }

    Icon(
        imageVector = runwayVector,
        contentDescription = "Aerodock",
        tint = Color.Unspecified,
        modifier = modifier.size(size)
    )
}

/**
 * Wordmark: "Runway" in Inter Medium, 20px, slight letter-spacing (0.5px),
 * white on dark and #0B0D10 on light.
 */
@Composable
fun RunwayWordmark(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground
) {
    Text(
        text = "Aerodock",
        style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = androidx.compose.ui.unit.TextUnit(0.5f, androidx.compose.ui.unit.TextUnitType.Sp)
        ),
        color = color,
        modifier = modifier
    )
}

