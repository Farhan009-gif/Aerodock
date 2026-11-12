package com.example.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.ui.theme.MonospaceFontFamily
import com.example.ui.theme.SoftIndigo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ============================================================================
// STEP (2): RICH REPLIES — STREAM-SAFE MARKDOWN, CODE, TABLES, MATH, MERMAID, IMAGES
// ============================================================================

sealed class RunwayRichBlock {
    data class Heading(val level: Int, val text: String) : RunwayRichBlock()
    data class Paragraph(val text: String) : RunwayRichBlock()
    data class Blockquote(val lines: List<String>) : RunwayRichBlock()
    data class BulletList(val items: List<ListItemData>) : RunwayRichBlock()
    data class NumberedList(val items: List<NumberedListItemData>) : RunwayRichBlock()
    data class TaskList(val items: List<TaskListItemData>) : RunwayRichBlock()
    data object HorizontalRule : RunwayRichBlock()
    data class CodeBlock(
        val language: String,
        val code: String,
        val isIncompleteStream: Boolean = false
    ) : RunwayRichBlock()
    data class MermaidBlock(
        val source: String,
        val isIncompleteStream: Boolean = false
    ) : RunwayRichBlock()
    data class MathBlock(
        val expression: String,
        val isIncompleteStream: Boolean = false
    ) : RunwayRichBlock()
    data class TableBlock(
        val headers: List<String>,
        val rows: List<List<String>>,
        val isIncompleteStream: Boolean = false
    ) : RunwayRichBlock()
    data class ImageBlock(
        val altText: String,
        val imageUrl: String
    ) : RunwayRichBlock()
}

data class ListItemData(val indentLevel: Int, val text: String)
data class NumberedListItemData(val numberLabel: String, val indentLevel: Int, val text: String)
data class TaskListItemData(val isChecked: Boolean, val text: String)

/**
 * Stream-safe Markdown block parser.
 * Handles unclosed code fences (```), unclosed block LaTeX ($$), and half-streamed tables
 * cleanly without flickering or throwing exceptions while tokens arrive.
 */
object RunwayRichMarkdownParser {

    private val headingRegex = Regex("^(#{1,6})\\s+(.+)$")
    private val hrRegex = Regex("^\\s*(?:---+|_{3,}|\\*{3,})\\s*$")
    private val taskItemRegex = Regex("^(\\s*)[-*+]\\s+\\[([ xX])]\\s+(.+)$")
    private val bulletItemRegex = Regex("^(\\s*)[-*+]\\s+(.+)$")
    private val numberedItemRegex = Regex("^(\\s*)(\\d+)\\.\\s+(.+)$")
    private val standaloneImageRegex = Regex("^!\\[([^\\]]*)]\\(([^)]+)\\)\\s*$")
    private val tableDividerCellRegex = Regex("^:?-{2,}:?$")

    fun parse(rawContent: String, isStreaming: Boolean): List<RunwayRichBlock> {
        if (rawContent.isBlank()) return emptyList()

        val normalized = rawContent.replace("\r\n", "\n")
        val lines = normalized.lines()
        val blocks = mutableListOf<RunwayRichBlock>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // 1. Blank line
            if (trimmed.isEmpty()) {
                i++
                continue
            }

            // 2. Fenced Code Block or Mermaid Block: ```lang
            if (trimmed.startsWith("```")) {
                val lang = trimmed.removePrefix("```").trim().lowercase()
                val codeLines = mutableListOf<String>()
                i++
                var closed = false
                while (i < lines.size) {
                    if (lines[i].trim().startsWith("```")) {
                        closed = true
                        i++
                        break
                    }
                    codeLines.add(lines[i])
                    i++
                }
                val codeContent = codeLines.joinToString("\n")
                val incomplete = !closed && isStreaming
                if (lang == "mermaid") {
                    blocks.add(
                        RunwayRichBlock.MermaidBlock(
                            source = codeContent,
                            isIncompleteStream = incomplete || !closed
                        )
                    )
                } else {
                    blocks.add(
                        RunwayRichBlock.CodeBlock(
                            language = lang.ifEmpty { "text" },
                            code = codeContent,
                            isIncompleteStream = incomplete || !closed
                        )
                    )
                }
                continue
            }

            // 3. Block Math: $$ ... $$ (single-line or multi-line)
            if (trimmed.startsWith("$$")) {
                if (trimmed.length > 4 && trimmed.endsWith("$$")) {
                    val expr = trimmed.removePrefix("$$").removeSuffix("$$").trim()
                    blocks.add(RunwayRichBlock.MathBlock(expression = expr, isIncompleteStream = false))
                    i++
                    continue
                } else {
                    val mathLines = mutableListOf<String>()
                    val firstLineRem = trimmed.removePrefix("$$").trim()
                    if (firstLineRem.isNotEmpty()) mathLines.add(firstLineRem)
                    i++
                    var closed = false
                    while (i < lines.size) {
                        val currentTrimmed = lines[i].trim()
                        if (currentTrimmed.endsWith("$$")) {
                            val beforeClose = currentTrimmed.removeSuffix("$$").trim()
                            if (beforeClose.isNotEmpty()) mathLines.add(beforeClose)
                            closed = true
                            i++
                            break
                        }
                        mathLines.add(lines[i])
                        i++
                    }
                    blocks.add(
                        RunwayRichBlock.MathBlock(
                            expression = mathLines.joinToString("\n").trim(),
                            isIncompleteStream = !closed
                        )
                    )
                    continue
                }
            }

            // 4. Horizontal Rule
            if (hrRegex.matches(trimmed)) {
                blocks.add(RunwayRichBlock.HorizontalRule)
                i++
                continue
            }

            // 5. Standalone Image ![alt](url)
            val imgMatch = standaloneImageRegex.matchEntire(trimmed)
            if (imgMatch != null) {
                blocks.add(
                    RunwayRichBlock.ImageBlock(
                        altText = imgMatch.groupValues[1],
                        imageUrl = imgMatch.groupValues[2].trim()
                    )
                )
                i++
                continue
            }

            // 6. Heading (# to ######)
            val headingMatch = headingRegex.matchEntire(trimmed)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length.coerceIn(1, 6)
                val title = headingMatch.groupValues[2].trim()
                blocks.add(RunwayRichBlock.Heading(level = level, text = title))
                i++
                continue
            }

            // 7. Markdown Table (header row + separator row `| --- | --- |`)
            if (trimmed.contains("|")) {
                val nextLine = lines.getOrNull(i + 1)?.trim()
                if (nextLine != null && isTableDividerRow(nextLine)) {
                    val headers = parseTableCells(trimmed)
                    i += 2 // skip header and divider
                    val rows = mutableListOf<List<String>>()
                    while (i < lines.size) {
                        val rowLine = lines[i].trim()
                        if (rowLine.isEmpty() || !rowLine.contains("|")) break
                        rows.add(parseTableCells(rowLine, expectedSize = headers.size))
                        i++
                    }
                    blocks.add(
                        RunwayRichBlock.TableBlock(
                            headers = headers,
                            rows = rows,
                            isIncompleteStream = isStreaming && i >= lines.size
                        )
                    )
                    continue
                } else if (isStreaming && i == lines.lastIndex && trimmed.startsWith("|")) {
                    // Stream-safe: half-typed first row of a table at the very end of stream
                    blocks.add(RunwayRichBlock.Paragraph(text = line))
                    i++
                    continue
                }
            }

            // 8. Blockquote (> ...)
            if (trimmed.startsWith(">")) {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quoteLines.add(lines[i].trim().removePrefix(">").trimStart())
                    i++
                }
                blocks.add(RunwayRichBlock.Blockquote(lines = quoteLines))
                continue
            }

            // 9. Task List (- [ ] or - [x])
            if (taskItemRegex.matches(line)) {
                val items = mutableListOf<TaskListItemData>()
                while (i < lines.size) {
                    val match = taskItemRegex.matchEntire(lines[i]) ?: break
                    val checked = match.groupValues[2].equals("x", ignoreCase = true)
                    val itemText = match.groupValues[3].trim()
                    items.add(TaskListItemData(isChecked = checked, text = itemText))
                    i++
                }
                blocks.add(RunwayRichBlock.TaskList(items = items))
                continue
            }

            // 10. Bullet List (-, *, +)
            if (bulletItemRegex.matches(line)) {
                val items = mutableListOf<ListItemData>()
                while (i < lines.size) {
                    val match = bulletItemRegex.matchEntire(lines[i]) ?: break
                    if (taskItemRegex.matches(lines[i])) break
                    val indent = (match.groupValues[1].length / 2).coerceIn(0, 3)
                    val itemText = match.groupValues[2].trim()
                    items.add(ListItemData(indentLevel = indent, text = itemText))
                    i++
                }
                blocks.add(RunwayRichBlock.BulletList(items = items))
                continue
            }

            // 11. Numbered List (1. 2. ...)
            if (numberedItemRegex.matches(line)) {
                val items = mutableListOf<NumberedListItemData>()
                while (i < lines.size) {
                    val match = numberedItemRegex.matchEntire(lines[i]) ?: break
                    val indent = (match.groupValues[1].length / 2).coerceIn(0, 3)
                    val num = match.groupValues[2]
                    val itemText = match.groupValues[3].trim()
                    items.add(NumberedListItemData(numberLabel = "$num.", indentLevel = indent, text = itemText))
                    i++
                }
                blocks.add(RunwayRichBlock.NumberedList(items = items))
                continue
            }

            // 12. Paragraph (accumulate consecutive plain lines)
            val paraLines = mutableListOf<String>()
            while (i < lines.size) {
                val cur = lines[i]
                val curTrimmed = cur.trim()
                if (curTrimmed.isEmpty() ||
                    curTrimmed.startsWith("```") ||
                    curTrimmed.startsWith("$$") ||
                    curTrimmed.startsWith(">") ||
                    hrRegex.matches(curTrimmed) ||
                    headingRegex.matches(curTrimmed) ||
                    standaloneImageRegex.matches(curTrimmed) ||
                    taskItemRegex.matches(cur) ||
                    bulletItemRegex.matches(cur) ||
                    numberedItemRegex.matches(cur) ||
                    (curTrimmed.contains("|") && lines.getOrNull(i + 1)?.trim()?.let { isTableDividerRow(it) } == true)
                ) {
                    break
                }
                paraLines.add(cur)
                i++
            }
            if (paraLines.isNotEmpty()) {
                blocks.add(RunwayRichBlock.Paragraph(text = paraLines.joinToString("\n")))
            } else {
                i++
            }
        }

        return blocks
    }

    private fun isTableDividerRow(line: String): Boolean {
        if (!line.contains("|") || !line.contains("-")) return false
        val cells = line.trim().removeSurrounding("|").split("|").map { it.trim() }
        return cells.isNotEmpty() && cells.all { tableDividerCellRegex.matches(it) }
    }

    private fun parseTableCells(line: String, expectedSize: Int? = null): List<String> {
        val raw = line.trim().removePrefix("|").removeSuffix("|")
        val split = raw.split("|").map { it.trim() }
        if (expectedSize == null) return split
        return if (split.size >= expectedSize) {
            split.take(expectedSize)
        } else {
            split + List(expectedSize - split.size) { "" }
        }
    }
}

/**
 * Primary entry-point Composable for rendering rich replies in Runway.
 * Supports:
 * - Full Markdown (Headings, Lists, Task lists, Blockquotes, Rules, Bold/Italic/Strikethrough/Links/Inline Code)
 * - Code blocks with syntax highlighting, language badge, Copy ("Copied ✓"), line-wrap toggle, and >30 line collapse
 * - Tables with sticky headers, horizontal scroll, 1px hairline borders, and "Copy as CSV"
 * - LaTeX math ($...$ inline and $$...$$ block) with raw fallback if invalid
 * - Mermaid diagrams with visual Canvas rendering and Diagram/Source toggle
 * - Stream-safe rendering during token streaming
 * - Tap-to-zoom full-screen image viewer with Save and Share
 */
@Composable
fun RunwayRichMarkdownRenderer(
    content: String,
    isStreaming: Boolean = false,
    onCopiedToast: (String) -> Unit = {},
    onRunCodeBlock: ((language: String, code: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val blocks = remember(content, isStreaming) {
        RunwayRichMarkdownParser.parse(content, isStreaming)
    }

    var fullScreenImageTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    SelectionContainer(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (blocks.isEmpty()) {
                Text(
                    text = if (isStreaming && content.isBlank()) "…" else content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                blocks.forEach { block ->
                    when (block) {
                        is RunwayRichBlock.Heading -> {
                            RunwayMarkdownHeading(level = block.level, text = block.text)
                        }

                        is RunwayRichBlock.Paragraph -> {
                            RunwayInlineRichText(
                                text = block.text,
                                isStreaming = isStreaming,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                onOpenImage = { alt, url -> fullScreenImageTarget = alt to url }
                            )
                        }

                        is RunwayRichBlock.Blockquote -> {
                            RunwayMarkdownBlockquote(
                                lines = block.lines,
                                isStreaming = isStreaming,
                                onOpenImage = { alt, url -> fullScreenImageTarget = alt to url }
                            )
                        }

                        is RunwayRichBlock.BulletList -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                block.items.forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = (item.indentLevel * 14).dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "•",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.width(16.dp)
                                        )
                                        Box(modifier = Modifier.weight(1f)) {
                                            RunwayInlineRichText(
                                                text = item.text,
                                                isStreaming = isStreaming,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                onOpenImage = { alt, url -> fullScreenImageTarget = alt to url }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        is RunwayRichBlock.NumberedList -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                block.items.forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = (item.indentLevel * 14).dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = item.numberLabel,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.width(24.dp)
                                        )
                                        Box(modifier = Modifier.weight(1f)) {
                                            RunwayInlineRichText(
                                                text = item.text,
                                                isStreaming = isStreaming,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                onOpenImage = { alt, url -> fullScreenImageTarget = alt to url }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        is RunwayRichBlock.TaskList -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                block.items.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    if (item.isChecked) MaterialTheme.colorScheme.primary else Color.Transparent
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = if (item.isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                    shape = RoundedCornerShape(4.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (item.isChecked) {
                                                Icon(
                                                    imageVector = LucideIcons.Check,
                                                    contentDescription = "Checked",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                            }
                                        }
                                        Box(modifier = Modifier.weight(1f)) {
                                            RunwayInlineRichText(
                                                text = item.text,
                                                isStreaming = isStreaming,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
                                                ),
                                                color = if (item.isChecked) {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                                onOpenImage = { alt, url -> fullScreenImageTarget = alt to url }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        is RunwayRichBlock.HorizontalRule -> {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        is RunwayRichBlock.CodeBlock -> {
                            RunwaySyntaxCodeBlock(
                                language = block.language,
                                code = block.code,
                                isIncompleteStream = block.isIncompleteStream,
                                onCopiedToast = onCopiedToast,
                                onRunCodeBlock = onRunCodeBlock
                            )
                        }

                        is RunwayRichBlock.MermaidBlock -> {
                            RunwayMermaidDiagramCard(
                                source = block.source,
                                isIncompleteStream = block.isIncompleteStream,
                                onCopiedToast = onCopiedToast
                            )
                        }

                        is RunwayRichBlock.MathBlock -> {
                            RunwayMathBlockCard(
                                expression = block.expression,
                                isIncompleteStream = block.isIncompleteStream,
                                onCopiedToast = onCopiedToast
                            )
                        }

                        is RunwayRichBlock.TableBlock -> {
                            RunwayStickyMarkdownTable(
                                headers = block.headers,
                                rows = block.rows,
                                onCopiedToast = onCopiedToast
                            )
                        }

                        is RunwayRichBlock.ImageBlock -> {
                            RunwayReplyImageItem(
                                altText = block.altText,
                                imageUrl = block.imageUrl,
                                onClick = { fullScreenImageTarget = block.altText to block.imageUrl }
                            )
                        }
                    }
                }
            }
        }
    }

    fullScreenImageTarget?.let { (alt, url) ->
        RunwayFullScreenImageDialog(
            altText = alt,
            imageUrl = url,
            onDismiss = { fullScreenImageTarget = null },
            onToast = onCopiedToast
        )
    }
}

// ============================================================================
// HEADINGS, BLOCKQUOTES & INLINE MARKDOWN + INLINE LaTeX ($...$)
// ============================================================================

@Composable
private fun RunwayMarkdownHeading(level: Int, text: String) {
    val style = when (level) {
        1, 2 -> MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Medium,
            lineHeight = 26.sp
        )
        else -> MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.Medium,
            lineHeight = 22.sp
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (level <= 2) 4.dp else 2.dp)
    ) {
        RunwayInlineRichText(
            text = text,
            isStreaming = false,
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
            onOpenImage = { _, _ -> }
        )
        if (level == 1) {
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun RunwayMarkdownBlockquote(
    lines: List<String>,
    isStreaming: Boolean,
    onOpenImage: (String, String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            lines.forEach { quoteLine ->
                RunwayInlineRichText(
                    text = quoteLine,
                    isStreaming = isStreaming,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    onOpenImage = onOpenImage
                )
            }
        }
    }
}

private val inlineImageExtractRegex = Regex("!\\[([^\\]]*)]\\(([^)]+)\\)")
private val inlineLinkExtractRegex = Regex("\\[([^\\]]*)\\]\\(([^)]+)\\)")

private val IMAGE_FILE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp")

/** True when [url] points at an image file (query string and fragment ignored). */
internal fun isImageFileUrl(url: String): Boolean {
    val path = url.trim().substringBefore('?').substringBefore('#').lowercase()
    return IMAGE_FILE_EXTENSIONS.any { path.endsWith(it) }
}

@Composable
private fun RunwayInlineRichText(
    text: String,
    isStreaming: Boolean,
    style: TextStyle,
    color: Color,
    onOpenImage: (alt: String, url: String) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val inlineCodeBg = MaterialTheme.colorScheme.surface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary

    // Extract inline images if embedded inside a paragraph
    val inlineImages = remember(text) {
        inlineImageExtractRegex.findAll(text).map {
            it.groupValues[1] to it.groupValues[2].trim()
        }.toList()
    }
    val textWithoutImages = remember(text, inlineImages) {
        if (inlineImages.isEmpty()) text else inlineImageExtractRegex.replace(text, "").trim()
    }

    // Promote plain links that point at image files to image previews: models
    // often wrap a returned image URL as [name](url) instead of ![name](url),
    // which otherwise shows as a bare link with no picture (seen 2026-11-12
    // with a Composio-generated image). Non-image links stay clickable text.
    val promotedImages = remember(textWithoutImages) {
        inlineLinkExtractRegex.findAll(textWithoutImages)
            .filter { isImageFileUrl(it.groupValues[2]) }
            .map { it.groupValues[1] to it.groupValues[2].trim() }
            .toList()
    }
    val displayText = remember(textWithoutImages, promotedImages) {
        if (promotedImages.isEmpty()) {
            textWithoutImages
        } else {
            inlineLinkExtractRegex.replace(textWithoutImages) { match ->
                if (isImageFileUrl(match.groupValues[2])) "" else match.value
            }.trim()
        }
    }

    val annotated = remember(displayText, isStreaming, color, inlineCodeBg, onSurfaceVariant, accentColor) {
        buildInlineMarkdownAnnotatedString(
            raw = displayText,
            isStreaming = isStreaming,
            baseColor = color,
            codeBg = inlineCodeBg,
            mutedColor = onSurfaceVariant,
            accentColor = accentColor
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (annotated.text.isNotEmpty()) {
            val hasLinks = remember(annotated) {
                annotated.getStringAnnotations(tag = "URL", start = 0, end = annotated.length).isNotEmpty()
            }
            if (hasLinks) {
                ClickableText(
                    text = annotated,
                    style = style.copy(color = color),
                    onClick = { offset ->
                        annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                runCatching { uriHandler.openUri(annotation.item) }
                            }
                    }
                )
            } else {
                Text(
                    text = annotated,
                    style = style,
                    color = color
                )
            }
        }

        (inlineImages + promotedImages).forEach { (alt, url) ->
            RunwayReplyImageItem(
                altText = alt,
                imageUrl = url,
                onClick = { onOpenImage(alt, url) }
            )
        }
    }
}

private fun buildInlineMarkdownAnnotatedString(
    raw: String,
    isStreaming: Boolean,
    baseColor: Color,
    codeBg: Color,
    mutedColor: Color,
    accentColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val n = raw.length

        while (i < n) {
            // 1. Inline code: `...`
            if (raw[i] == '`') {
                val closeIdx = raw.indexOf('`', i + 1)
                if (closeIdx > i + 1) {
                    val codeSegment = raw.substring(i + 1, closeIdx)
                    withStyle(
                        SpanStyle(
                            fontFamily = MonospaceFontFamily,
                            fontSize = 13.sp,
                            color = accentColor,
                            background = codeBg
                        )
                    ) {
                        append(" $codeSegment ")
                    }
                    i = closeIdx + 1
                    continue
                } else if (isStreaming) {
                    // Stream-safe unclosed backtick: show as plain text
                    append(raw.substring(i))
                    break
                }
            }

            // 2. Inline LaTeX math: $...$ (avoid matching currency like "$10 and $20")
            if (raw[i] == '$' && (i + 1 < n && raw[i + 1] != '$' && !raw[i + 1].isWhitespace())) {
                val closeIdx = findClosingInlineMathDollar(raw, i + 1)
                if (closeIdx != -1) {
                    val rawLatex = raw.substring(i + 1, closeIdx)
                    if (LaTeXMathFormatter.looksLikeMath(rawLatex)) {
                        val rendered = LaTeXMathFormatter.formatToAnnotatedString(
                            latex = rawLatex,
                            mathColor = accentColor,
                            fallbackColor = mutedColor
                        )
                        withStyle(SpanStyle(background = codeBg.copy(alpha = 0.7f))) {
                            append(rendered)
                        }
                        i = closeIdx + 1
                        continue
                    }
                } else if (isStreaming && raw.substring(i + 1).contains('\\')) {
                    // Stream-safe unclosed inline formula at tail of stream: show raw text
                    append(raw.substring(i))
                    break
                }
            }

            // 3. Markdown Link: [label](url)
            if (raw[i] == '[') {
                val closeBracket = raw.indexOf(']', i + 1)
                if (closeBracket != -1 && closeBracket + 1 < n && raw[closeBracket + 1] == '(') {
                    val closeParen = raw.indexOf(')', closeBracket + 2)
                    if (closeParen != -1) {
                        val label = raw.substring(i + 1, closeBracket)
                        val url = raw.substring(closeBracket + 2, closeParen).trim()
                        pushStringAnnotation(tag = "URL", annotation = url)
                        withStyle(
                            SpanStyle(
                                color = accentColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium
                            )
                        ) {
                            append(label.ifEmpty { url })
                        }
                        pop()
                        i = closeParen + 1
                        continue
                    }
                }
            }

            // 4. Bold + Italic: ***...***
            if (raw.startsWith("***", i)) {
                val closeIdx = raw.indexOf("***", i + 3)
                if (closeIdx != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic)) {
                        append(raw.substring(i + 3, closeIdx))
                    }
                    i = closeIdx + 3
                    continue
                }
            }

            // 5. Bold: **...**
            if (raw.startsWith("**", i)) {
                val closeIdx = raw.indexOf("**", i + 2)
                if (closeIdx != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = baseColor)) {
                        append(raw.substring(i + 2, closeIdx))
                    }
                    i = closeIdx + 2
                    continue
                }
            }

            // 6. Strikethrough: ~~...~~
            if (raw.startsWith("~~", i)) {
                val closeIdx = raw.indexOf("~~", i + 2)
                if (closeIdx != -1) {
                    withStyle(
                        SpanStyle(
                            textDecoration = TextDecoration.LineThrough,
                            color = mutedColor
                        )
                    ) {
                        append(raw.substring(i + 2, closeIdx))
                    }
                    i = closeIdx + 2
                    continue
                }
            }

            // 7. Italic: *...*
            if (raw[i] == '*' && (i + 1 < n && raw[i + 1] != ' ')) {
                val closeIdx = raw.indexOf('*', i + 1)
                if (closeIdx > i + 1 && raw[closeIdx - 1] != ' ') {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(raw.substring(i + 1, closeIdx))
                    }
                    i = closeIdx + 1
                    continue
                }
            }

            // 8. Bare URL: https://... or http://...
            if (raw.startsWith("https://", i) || raw.startsWith("http://", i)) {
                var endUrl = i
                while (endUrl < n && !raw[endUrl].isWhitespace() && raw[endUrl] !in charArrayOf(')', ']', '>', '"', '\'')) {
                    endUrl++
                }
                val url = raw.substring(i, endUrl).trimEnd('.', ',', ';', ':')
                if (url.length > 8) {
                    pushStringAnnotation(tag = "URL", annotation = url)
                    withStyle(
                        SpanStyle(
                            color = accentColor,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append(url)
                    }
                    pop()
                    i += url.length
                    continue
                }
            }

            append(raw[i])
            i++
        }
    }
}

private fun findClosingInlineMathDollar(raw: String, startIndex: Int): Int {
    var idx = startIndex
    while (idx < raw.length) {
        if (raw[idx] == '\n') return -1
        if (raw[idx] == '$' && raw.getOrNull(idx - 1) != '\\') {
            if (idx > startIndex && !raw[idx - 1].isWhitespace()) {
                return idx
            }
            return -1
        }
        idx++
    }
    return -1
}

// ============================================================================
// CODE BLOCKS: SYNTAX HIGHLIGHTING, COPY ("Copied ✓"), LINE-WRAP, >30 LINE COLLAPSE
// ============================================================================

@Composable
fun RunwaySyntaxCodeBlock(
    language: String,
    code: String,
    isIncompleteStream: Boolean = false,
    onCopiedToast: (String) -> Unit = {},
    onRunCodeBlock: ((language: String, code: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var wrapLines by rememberSaveable { mutableStateOf(false) }
    var isCopied by remember { mutableStateOf(false) }
    val canRunCode = remember(language, isIncompleteStream, onRunCodeBlock) {
        !isIncompleteStream &&
            onRunCodeBlock != null &&
            com.example.data.mcp.JsSandboxExecutor.isSupportedRunnerLanguage(language)
    }

    val allLines = remember(code) { code.lines() }
    val totalLineCount = allLines.size
    val isCollapsible = totalLineCount > 30 && !isIncompleteStream
    var isExpanded by rememberSaveable(isCollapsible) { mutableStateOf(!isCollapsible) }

    val visibleCode = remember(code, isCollapsible, isExpanded) {
        if (isCollapsible && !isExpanded) {
            allLines.take(24).joinToString("\n")
        } else {
            code
        }
    }

    val highlightedCode = remember(visibleCode, language, isIncompleteStream) {
        if (isIncompleteStream && visibleCode.length > 4000) {
            AnnotatedString(visibleCode)
        } else {
            SyntaxHighlighter.highlight(visibleCode, language)
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(160))
            .testTag("rich_code_block")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top bar: language label + Run button (HTML/CSS/JS/Java) + Wrap toggle + Copy button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = language.ifBlank { "code" }.lowercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = MonospaceFontFamily,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isIncompleteStream) {
                        Text(
                            text = "streaming…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (canRunCode && onRunCodeBlock != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onRunCodeBlock(language, code) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("code_block_run_button")
                        ) {
                            Icon(
                                imageVector = LucideIcons.Play,
                                contentDescription = "Run code",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Run",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    // Line-wrap toggle
                    Text(
                        text = if (wrapLines) "Wrap: On" else "Wrap",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (wrapLines) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { wrapLines = !wrapLines }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("code_block_wrap_toggle")
                    )

                    // Copy button with 1.5s "Copied" state
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                clipboard.setText(AnnotatedString(code))
                                isCopied = true
                                onCopiedToast("Copied code")
                                scope.launch {
                                    delay(1500)
                                    isCopied = false
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("code_block_copy_button")
                    ) {
                        Icon(
                            imageVector = if (isCopied) LucideIcons.Check else LucideIcons.Copy,
                            contentDescription = "Copy code",
                            tint = if (isCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (isCopied) "Copied" else "Copy",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

            // Code body
            val bodyModifier = if (wrapLines) {
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            }

            Box(modifier = bodyModifier) {
                Text(
                    text = highlightedCode,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = MonospaceFontFamily,
                        lineHeight = 19.sp
                    ),
                    softWrap = wrapLines
                )
            }

            // Collapse / Expand bar for blocks > 30 lines
            if (isCollapsible) {
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .padding(vertical = 8.dp)
                        .testTag("code_block_expand_toggle")
                ) {
                    Text(
                        text = if (isExpanded) {
                            "Show less"
                        } else {
                            "Show more ($totalLineCount lines)"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private object SyntaxHighlighter {
    private val keywords = setOf(
        "fun", "val", "var", "class", "object", "interface", "package", "import",
        "return", "if", "else", "when", "for", "while", "do", "try", "catch", "finally",
        "throw", "null", "true", "false", "this", "super", "is", "in", "as", "suspend",
        "override", "private", "public", "protected", "internal", "data", "sealed", "enum",
        "const", "companion", "init", "by", "lazy", "where",
        "def", "lambda", "yield", "async", "await", "from", "with", "pass", "None", "True", "False",
        "function", "let", "export", "default", "typeof", "instanceof", "new", "void",
        "select", "from", "where", "insert", "into", "update", "delete", "join", "group", "order", "limit"
    )

    private val tokenRegex = Regex(
        """(//[^\n]*|#[^\n]*|/\*[\s\S]*?\*/)|("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`)|(@[A-Za-z_][A-Za-z0-9_]*)|\b([A-Z][A-Za-z0-9_]*)\b|\b([a-z_][A-Za-z0-9_]*)\b|\b(\d+(?:\.\d+)?)\b"""
    )

    private val commentColor = Color(0xFF6B7280)
    private val stringColor = Color(0xFF93C5FD)
    private val keywordColor = SoftIndigo
    private val typeColor = Color(0xFFC4B5FD)
    private val numberColor = Color(0xFFA5B4FC)

    fun highlight(code: String, language: String): AnnotatedString {
        if (code.isEmpty()) return AnnotatedString("")
        return buildAnnotatedString {
            var lastIdx = 0
            for (match in tokenRegex.findAll(code)) {
                if (match.range.first > lastIdx) {
                    append(code.substring(lastIdx, match.range.first))
                }
                val full = match.value
                when {
                    match.groups[1] != null -> {
                        withStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)) {
                            append(full)
                        }
                    }
                    match.groups[2] != null -> {
                        withStyle(SpanStyle(color = stringColor)) {
                            append(full)
                        }
                    }
                    match.groups[3] != null -> {
                        withStyle(SpanStyle(color = keywordColor)) {
                            append(full)
                        }
                    }
                    match.groups[4] != null -> {
                        if (full in keywords) {
                            withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Medium)) {
                                append(full)
                            }
                        } else {
                            withStyle(SpanStyle(color = typeColor)) {
                                append(full)
                            }
                        }
                    }
                    match.groups[5] != null -> {
                        if (full in keywords) {
                            withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Medium)) {
                                append(full)
                            }
                        } else {
                            append(full)
                        }
                    }
                    match.groups[6] != null -> {
                        withStyle(SpanStyle(color = numberColor)) {
                            append(full)
                        }
                    }
                    else -> append(full)
                }
                lastIdx = match.range.last + 1
            }
            if (lastIdx < code.length) {
                append(code.substring(lastIdx))
            }
        }
    }
}

// ============================================================================
// TABLES: STICKY HEADER, HORIZONTAL SCROLL, 1PX BORDERS, "COPY AS CSV"
// ============================================================================

@Composable
fun RunwayStickyMarkdownTable(
    headers: List<String>,
    rows: List<List<String>>,
    onCopiedToast: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copiedCsv by remember { mutableStateOf(false) }
    val hScrollState = rememberScrollState()
    val vScrollState = rememberScrollState()

    val csvString = remember(headers, rows) {
        buildString {
            appendLine(headers.joinToString(",") { escapeCsvCell(it) })
            rows.forEach { row ->
                appendLine(row.joinToString(",") { escapeCsvCell(it) })
            }
        }.trimEnd()
    }

    val columnWidths = remember(headers, rows) {
        headers.indices.map { colIdx ->
            val maxChars = maxOf(
                headers.getOrNull(colIdx)?.length ?: 6,
                rows.maxOfOrNull { it.getOrNull(colIdx)?.length ?: 0 } ?: 6
            )
            (maxChars * 8 + 28).coerceIn(96, 240).dp
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .testTag("rich_markdown_table")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Table top bar with row count + "Copy as CSV"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Table · ${rows.size} ${if (rows.size == 1) "row" else "rows"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            clipboard.setText(AnnotatedString(csvString))
                            copiedCsv = true
                            onCopiedToast("Copied as CSV")
                            scope.launch {
                                delay(1500)
                                copiedCsv = false
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("table_copy_csv_button")
                ) {
                    Icon(
                        imageVector = if (copiedCsv) LucideIcons.Check else LucideIcons.Copy,
                        contentDescription = "Copy as CSV",
                        tint = if (copiedCsv) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (copiedCsv) "Copied CSV" else "Copy as CSV",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (copiedCsv) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

            // Horizontally scrollable table with sticky header row
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(hScrollState)
            ) {
                // Sticky Header Row
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                ) {
                    headers.forEachIndexed { colIndex, headerText ->
                        val colWidth = columnWidths.getOrElse(colIndex) { 120.dp }
                        Box(
                            modifier = Modifier
                                .width(colWidth)
                                .border(0.5.dp, MaterialTheme.colorScheme.outline)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = headerText,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Scrollable Body Rows (capped height so sticky header stays visible on tall tables)
                val bodyModifier = if (rows.size > 8) {
                    Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(vScrollState)
                } else {
                    Modifier
                }

                Column(modifier = bodyModifier) {
                    rows.forEach { row ->
                        Row {
                            headers.indices.forEach { colIndex ->
                                val colWidth = columnWidths.getOrElse(colIndex) { 120.dp }
                                val cellText = row.getOrNull(colIndex).orEmpty()
                                Box(
                                    modifier = Modifier
                                        .width(colWidth)
                                        .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = cellText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun escapeCsvCell(value: String): String {
    val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n')
    if (!needsQuotes) return value
    return "\"${value.replace("\"", "\"\"")}\""
}

// ============================================================================
// LaTeX / KaTeX MATH RENDERER ($...$ INLINE AND $$...$$ BLOCK)
// ============================================================================

@Composable
fun RunwayMathBlockCard(
    expression: String,
    isIncompleteStream: Boolean = false,
    onCopiedToast: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }
    val mathAccentColor = MaterialTheme.colorScheme.primary

    val isValidLatex = remember(expression) {
        LaTeXMathFormatter.isBalancedAndValid(expression)
    }
    val formattedMath = remember(expression, isIncompleteStream, isValidLatex, mathAccentColor) {
        if (isIncompleteStream || !isValidLatex) {
            AnnotatedString(expression)
        } else {
            LaTeXMathFormatter.formatToAnnotatedString(
                latex = expression,
                mathColor = mathAccentColor,
                fallbackColor = Color.Unspecified
            )
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .testTag("rich_math_block")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (!isValidLatex && !isIncompleteStream) "LaTeX (raw)" else "Math",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            clipboard.setText(AnnotatedString(expression))
                            isCopied = true
                            onCopiedToast("Copied LaTeX")
                            scope.launch {
                                delay(1500)
                                isCopied = false
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (isCopied) LucideIcons.Check else LucideIcons.Copy,
                        contentDescription = "Copy formula",
                        tint = if (isCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (isCopied) "Copied" else "Copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formattedMath,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = if (isValidLatex && !isIncompleteStream) FontFamily.Serif else MonospaceFontFamily,
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

object LaTeXMathFormatter {

    private val symbolMap = mapOf(
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
        "\\epsilon" to "ϵ", "\\varepsilon" to "ε", "\\zeta" to "ζ", "\\eta" to "η",
        "\\theta" to "θ", "\\iota" to "ι", "\\kappa" to "κ", "\\lambda" to "λ",
        "\\mu" to "μ", "\\nu" to "ν", "\\xi" to "ξ", "\\pi" to "π",
        "\\rho" to "ρ", "\\sigma" to "σ", "\\tau" to "τ", "\\upsilon" to "υ",
        "\\phi" to "ϕ", "\\varphi" to "φ", "\\chi" to "χ", "\\psi" to "ψ", "\\omega" to "ω",
        "\\Gamma" to "Γ", "\\Delta" to "Δ", "\\Theta" to "Θ", "\\Lambda" to "Λ",
        "\\Xi" to "Ξ", "\\Pi" to "Π", "\\Sigma" to "Σ", "\\Phi" to "Φ",
        "\\Psi" to "Ψ", "\\Omega" to "Ω",
        "\\infty" to "∞", "\\pm" to "±", "\\mp" to "∓", "\\times" to "×",
        "\\div" to "÷", "\\cdot" to "·", "\\ast" to "∗", "\\star" to "⋆",
        "\\leq" to "≤", "\\le" to "≤", "\\geq" to "≥", "\\ge" to "≥",
        "\\neq" to "≠", "\\ne" to "≠", "\\approx" to "≈", "\\equiv" to "≡",
        "\\sim" to "∼", "\\propto" to "∝",
        "\\rightarrow" to "→", "\\to" to "→", "\\leftarrow" to "←",
        "\\Rightarrow" to "⇒", "\\Leftarrow" to "⇐", "\\leftrightarrow" to "↔",
        "\\Leftrightarrow" to "⇔", "\\mapsto" to "↦",
        "\\forall" to "∀", "\\exists" to "∃", "\\nexists" to "∄",
        "\\in" to "∈", "\\notin" to "∉", "\\subset" to "⊂", "\\subseteq" to "⊆",
        "\\cup" to "∪", "\\cap" to "∩", "\\emptyset" to "∅",
        "\\sum" to "∑", "\\prod" to "∏", "\\int" to "∫", "\\oint" to "∮",
        "\\partial" to "∂", "\\nabla" to "∇", "\\sqrt" to "√",
        "\\dots" to "…", "\\ldots" to "…", "\\cdots" to "⋯",
        "\\angle" to "∠", "\\perp" to "⊥", "\\parallel" to "∥",
        "\\mathbb{R}" to "ℝ", "\\mathbb{N}" to "ℕ", "\\mathbb{Z}" to "ℤ",
        "\\mathbb{Q}" to "ℚ", "\\mathbb{C}" to "ℂ"
    )

    fun looksLikeMath(input: String): Boolean {
        if (input.isBlank()) return false
        if (input.all { it.isDigit() || it == '.' || it == ',' }) return false
        return input.contains('\\') || input.contains('^') || input.contains('_') ||
            input.contains('=') || input.contains('+') || input.contains('-') ||
            input.contains('/') || input.length <= 24
    }

    fun isBalancedAndValid(latex: String): Boolean {
        if (latex.isBlank()) return false
        var depth = 0
        var i = 0
        while (i < latex.length) {
            when (latex[i]) {
                '\\' -> i++ // skip escaped char
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth < 0) return false
                }
            }
            i++
        }
        if (depth != 0) return false
        if (latex.trimEnd().endsWith("\\")) return false
        return true
    }

    fun formatToAnnotatedString(
        latex: String,
        mathColor: Color,
        fallbackColor: Color
    ): AnnotatedString {
        if (!isBalancedAndValid(latex)) {
            return buildAnnotatedString {
                if (fallbackColor != Color.Unspecified) {
                    withStyle(SpanStyle(fontFamily = MonospaceFontFamily, color = fallbackColor)) {
                        append(latex)
                    }
                } else {
                    append(latex)
                }
            }
        }

        return runCatching {
            val preprocessed = preprocessConstructs(latex)
            buildAnnotatedString {
                var i = 0
                while (i < preprocessed.length) {
                    val ch = preprocessed[i]
                    if (ch == '^' || ch == '_') {
                        val isSuper = ch == '^'
                        val shift = if (isSuper) BaselineShift.Superscript else BaselineShift.Subscript
                        i++
                        if (i < preprocessed.length) {
                            if (preprocessed[i] == '{') {
                                val closeBrace = findMatchingBrace(preprocessed, i)
                                if (closeBrace != -1) {
                                    val inner = preprocessed.substring(i + 1, closeBrace)
                                    withStyle(SpanStyle(baselineShift = shift, fontSize = 11.sp, color = mathColor)) {
                                        append(replaceSymbols(inner))
                                    }
                                    i = closeBrace + 1
                                    continue
                                }
                            } else {
                                withStyle(SpanStyle(baselineShift = shift, fontSize = 11.sp, color = mathColor)) {
                                    append(preprocessed[i].toString())
                                }
                                i++
                                continue
                            }
                        }
                    } else {
                        append(ch)
                        i++
                    }
                }
            }
        }.getOrElse {
            AnnotatedString(latex)
        }
    }

    private fun preprocessConstructs(input: String): String {
        var result = input.trim()
        // Matrix environments
        val matrixRegex = Regex("""\\begin\{(?:p|b|v|V)?matrix\}([\s\S]*?)\\end\{(?:p|b|v|V)?matrix\}""")
        result = matrixRegex.replace(result) { match ->
            val rows = match.groupValues[1].trim().split("\\\\").map { row ->
                row.trim().split("&").joinToString("  ") { it.trim() }
            }
            "[ " + rows.joinToString(" ; ") + " ]"
        }

        // \frac{a}{b} -> (a)/(b)
        val fracRegex = Regex("""\\frac\{([^{}]*)\}\{([^{}]*)\}""")
        var guard = 0
        while (fracRegex.containsMatchIn(result) && guard < 10) {
            result = fracRegex.replace(result) { m ->
                val num = m.groupValues[1].trim()
                val den = m.groupValues[2].trim()
                "($num)/($den)"
            }
            guard++
        }

        // \sqrt{a} -> √(a)
        val sqrtRegex = Regex("""\\sqrt\{([^{}]*)\}""")
        result = sqrtRegex.replace(result) { m ->
            "√(${m.groupValues[1].trim()})"
        }

        // \text{...} or \mathrm{...}
        val textCmdRegex = Regex("""\\(?:text|mathrm|mathbf|mathit)\{([^{}]*)\}""")
        result = textCmdRegex.replace(result) { m -> m.groupValues[1] }

        result = result.replace("\\left", "").replace("\\right", "")
        return replaceSymbols(result)
    }

    private fun replaceSymbols(input: String): String {
        var out = input
        for ((cmd, sym) in symbolMap.entries.sortedByDescending { it.key.length }) {
            out = out.replace(cmd, sym)
        }
        return out
    }

    private fun findMatchingBrace(s: String, openIdx: Int): Int {
        var depth = 0
        for (i in openIdx until s.length) {
            if (s[i] == '{') depth++
            else if (s[i] == '}') {
                depth--
                if (depth == 0) return i
            }
        }
        return -1
    }
}

// ============================================================================
// MERMAID DIAGRAMS: INTERACTIVE VISUAL CANVAS + DIAGRAM / SOURCE TOGGLE
// ============================================================================

private data class MermaidNode(
    val id: String,
    val label: String,
    val isDiamond: Boolean = false,
    val isRounded: Boolean = false
)

private data class MermaidEdge(
    val fromId: String,
    val toId: String,
    val label: String = "",
    val isDashed: Boolean = false
)

private data class MermaidGraphModel(
    val direction: String, // TD, TB, LR, RL
    val nodes: List<MermaidNode>,
    val edges: List<MermaidEdge>,
    val diagramType: String
)

private object MermaidParser {
    private val edgeRegex = Regex(
        """^([A-Za-z0-9_.\-]+)\s*(\[[^\]]*]|\([^)]*\)|\{[^}]*\})?\s*(-+>|-\.+->|==+>|--\|[^|]+\|-->|-+>>|--+>>)\s*(?:\|([^|]+)\|)?\s*([A-Za-z0-9_.\-]+)\s*(\[[^\]]*]|\([^)]*\)|\{[^}]*\})?$"""
    )
    private val seqMessageRegex = Regex(
        """^([A-Za-z0-9_.\-]+)\s*(-+>>|--+>>|->|-->)\s*([A-Za-z0-9_.\-]+)\s*:\s*(.+)$"""
    )
    private val standaloneNodeRegex = Regex(
        """^([A-Za-z0-9_.\-]+)\s*(\[[^\]]*]|\([^)]*\)|\{[^}]*\})$"""
    )
    private val pieItemRegex = Regex("""^"([^"]+)"\s*:\s*(\d+(?:\.\d+)?)$""")

    fun parse(rawSource: String): MermaidGraphModel? {
        val lines = rawSource.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("%%") }
        if (lines.isEmpty()) return null

        val header = lines.first()
        val lowerHeader = header.lowercase()
        val direction = when {
            lowerHeader.contains(" lr") -> "LR"
            lowerHeader.contains(" rl") -> "RL"
            else -> "TD"
        }

        val diagramType = when {
            lowerHeader.startsWith("flowchart") || lowerHeader.startsWith("graph") -> "Flowchart"
            lowerHeader.startsWith("sequencediagram") -> "Sequence"
            lowerHeader.startsWith("statediagram") -> "State"
            lowerHeader.startsWith("classdiagram") -> "Class"
            lowerHeader.startsWith("pie") -> "Pie"
            else -> return null
        }

        val nodeMap = linkedMapOf<String, MermaidNode>()
        val edges = mutableListOf<MermaidEdge>()

        fun upsertNode(id: String, rawDecl: String?) {
            val cleanId = id.trim()
            if (cleanId.isEmpty()) return
            if (rawDecl != null && rawDecl.length >= 2) {
                val isDiamond = rawDecl.startsWith("{") && rawDecl.endsWith("}")
                val isRounded = rawDecl.startsWith("(") && rawDecl.endsWith(")")
                val label = rawDecl.substring(1, rawDecl.length - 1).trim().removeSurrounding("\"")
                nodeMap[cleanId] = MermaidNode(
                    id = cleanId,
                    label = label.ifEmpty { cleanId },
                    isDiamond = isDiamond,
                    isRounded = isRounded
                )
            } else if (!nodeMap.containsKey(cleanId)) {
                nodeMap[cleanId] = MermaidNode(id = cleanId, label = cleanId)
            }
        }

        for (idx in 1 until lines.size) {
            val stmt = lines[idx].removeSuffix(";")
            if (stmt.startsWith("subgraph ") || stmt == "end" || stmt.startsWith("title ")) continue

            if (diagramType == "Pie") {
                val pieMatch = pieItemRegex.matchEntire(stmt)
                if (pieMatch != null) {
                    val label = pieMatch.groupValues[1]
                    val value = pieMatch.groupValues[2]
                    val nodeId = "slice_$idx"
                    nodeMap[nodeId] = MermaidNode(id = nodeId, label = "$label ($value)", isRounded = true)
                }
                continue
            }

            if (stmt.startsWith("participant ")) {
                val rest = stmt.removePrefix("participant ").trim()
                val parts = rest.split(" as ", ignoreCase = true)
                val id = parts.first().trim()
                val label = parts.getOrNull(1)?.trim() ?: id
                nodeMap[id] = MermaidNode(id = id, label = label, isRounded = true)
                continue
            }

            val seqMatch = seqMessageRegex.matchEntire(stmt)
            if (seqMatch != null) {
                val from = seqMatch.groupValues[1]
                val arrow = seqMatch.groupValues[2]
                val to = seqMatch.groupValues[3]
                val msg = seqMatch.groupValues[4].trim()
                upsertNode(from, null)
                upsertNode(to, null)
                edges.add(
                    MermaidEdge(
                        fromId = from,
                        toId = to,
                        label = msg,
                        isDashed = arrow.startsWith("--")
                    )
                )
                continue
            }

            val edgeMatch = edgeRegex.matchEntire(stmt)
            if (edgeMatch != null) {
                val fromId = edgeMatch.groupValues[1]
                val fromDecl = edgeMatch.groupValues[2].takeIf { it.isNotEmpty() }
                val arrow = edgeMatch.groupValues[3]
                val midLabel = edgeMatch.groupValues[4]
                val toId = edgeMatch.groupValues[5]
                val toDecl = edgeMatch.groupValues[6].takeIf { it.isNotEmpty() }

                val embeddedLabel = if (arrow.contains("|")) {
                    arrow.substringAfter("|").substringBefore("|")
                } else {
                    midLabel
                }

                upsertNode(fromId, fromDecl)
                upsertNode(toId, toDecl)
                edges.add(
                    MermaidEdge(
                        fromId = fromId,
                        toId = toId,
                        label = embeddedLabel.trim(),
                        isDashed = arrow.contains(".")
                    )
                )
                continue
            }

            val nodeMatch = standaloneNodeRegex.matchEntire(stmt)
            if (nodeMatch != null) {
                upsertNode(nodeMatch.groupValues[1], nodeMatch.groupValues[2])
            }
        }

        if (nodeMap.isEmpty()) return null
        return MermaidGraphModel(
            direction = direction,
            nodes = nodeMap.values.toList(),
            edges = edges,
            diagramType = diagramType
        )
    }
}

@Composable
fun RunwayMermaidDiagramCard(
    source: String,
    isIncompleteStream: Boolean = false,
    onCopiedToast: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val parsedModel = remember(source, isIncompleteStream) {
        if (isIncompleteStream) null else MermaidParser.parse(source)
    }
    var showSource by rememberSaveable { mutableStateOf(false) }

    // If still streaming or invalid Mermaid syntax, fall back gracefully to code block
    if (isIncompleteStream || parsedModel == null) {
        RunwaySyntaxCodeBlock(
            language = "mermaid",
            code = source,
            isIncompleteStream = isIncompleteStream,
            onCopiedToast = onCopiedToast,
            modifier = modifier
        )
        return
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .testTag("rich_mermaid_block")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top bar: Mermaid label + Diagram / Source toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mermaid · ${parsedModel.diagramType}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showSource) "Diagram" else "Source",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { showSource = !showSource }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .testTag("mermaid_view_toggle")
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

            if (showSource) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Text(
                        text = SyntaxHighlighter.highlight(source, "mermaid"),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = MonospaceFontFamily,
                            lineHeight = 19.sp
                        )
                    )
                }
            } else {
                RunwayMermaidVisualDiagram(model = parsedModel)
            }
        }
    }
}

@Composable
private fun RunwayMermaidVisualDiagram(model: MermaidGraphModel) {
    val isHorizontal = model.direction == "LR" || model.direction == "RL"
    val nodeSurface = MaterialTheme.colorScheme.background
    val outlineColor = MaterialTheme.colorScheme.outline
    val textColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isHorizontal) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                model.nodes.forEachIndexed { index, node ->
                    MermaidNodeChip(
                        node = node,
                        nodeSurface = nodeSurface,
                        outlineColor = outlineColor,
                        textColor = textColor
                    )
                    if (index < model.nodes.lastIndex) {
                        val edge = model.edges.find {
                            it.fromId == node.id && it.toId == model.nodes[index + 1].id
                        } ?: model.edges.getOrNull(index)
                        MermaidHorizontalArrow(label = edge?.label.orEmpty(), mutedColor = mutedColor)
                    }
                }
            }
        } else {
            model.nodes.forEachIndexed { index, node ->
                MermaidNodeChip(
                    node = node,
                    nodeSurface = nodeSurface,
                    outlineColor = outlineColor,
                    textColor = textColor
                )
                if (index < model.nodes.lastIndex) {
                    val edge = model.edges.find {
                        it.fromId == node.id && it.toId == model.nodes[index + 1].id
                    } ?: model.edges.getOrNull(index)
                    MermaidVerticalArrow(label = edge?.label.orEmpty(), mutedColor = mutedColor)
                }
            }
        }

        // Additional cross-branch edges summary if graph has multiple branches
        if (model.edges.size >= model.nodes.size && model.edges.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                model.edges.forEach { edge ->
                    val fromLabel = model.nodes.find { it.id == edge.fromId }?.label ?: edge.fromId
                    val toLabel = model.nodes.find { it.id == edge.toId }?.label ?: edge.toId
                    val edgeDesc = if (edge.label.isNotBlank()) {
                        "$fromLabel  —(${edge.label})→  $toLabel"
                    } else {
                        "$fromLabel  →  $toLabel"
                    }
                    Text(
                        text = edgeDesc,
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedColor
                    )
                }
            }
        }
    }
}

@Composable
private fun MermaidNodeChip(
    node: MermaidNode,
    nodeSurface: Color,
    outlineColor: Color,
    textColor: Color
) {
    val shape = when {
        node.isRounded -> RoundedCornerShape(50)
        node.isDiamond -> RoundedCornerShape(6.dp)
        else -> RoundedCornerShape(12.dp)
    }
    Surface(
        shape = shape,
        color = nodeSurface,
        border = BorderStroke(1.dp, if (node.isDiamond) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else outlineColor),
        modifier = Modifier.widthIn(min = 88.dp, max = 220.dp)
    ) {
        Text(
            text = node.label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun MermaidVerticalArrow(label: String, mutedColor: Color) {
    val accentColor = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Canvas(modifier = Modifier.size(width = 14.dp, height = 22.dp)) {
            val cx = size.width / 2f
            drawLine(
                color = accentColor,
                start = Offset(cx, 0f),
                end = Offset(cx, size.height - 4f),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
            val arrowPath = Path().apply {
                moveTo(cx - 4.5f, size.height - 7f)
                lineTo(cx, size.height)
                lineTo(cx + 4.5f, size.height - 7f)
            }
            drawPath(arrowPath, color = accentColor, style = Stroke(width = 3f, cap = StrokeCap.Round))
        }
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = mutedColor
            )
        }
    }
}

@Composable
private fun MermaidHorizontalArrow(label: String, mutedColor: Color) {
    val accentColor = MaterialTheme.colorScheme.primary
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = mutedColor
            )
        }
        Canvas(modifier = Modifier.size(width = 28.dp, height = 14.dp)) {
            val cy = size.height / 2f
            drawLine(
                color = accentColor,
                start = Offset(0f, cy),
                end = Offset(size.width - 4f, cy),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
            val arrowPath = Path().apply {
                moveTo(size.width - 7f, cy - 4.5f)
                lineTo(size.width, cy)
                lineTo(size.width - 7f, cy + 4.5f)
            }
            drawPath(arrowPath, color = accentColor, style = Stroke(width = 3f, cap = StrokeCap.Round))
        }
    }
}

// ============================================================================
// IMAGES IN REPLIES: ROUNDED THUMBNAIL + FULL-SCREEN VIEWER WITH SAVE & SHARE
// ============================================================================

@Composable
fun RunwayReplyImageItem(
    altText: String,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("reply_inline_image")
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = altText.ifBlank { "Reply image" },
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 220.dp)
        )
        if (altText.isNotBlank()) {
            Text(
                text = altText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun RunwayFullScreenImageDialog(
    altText: String,
    imageUrl: String,
    onDismiss: () -> Unit,
    onToast: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF20B0D10))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.05f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.25f
                            }
                        }
                    )
                }
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = altText.ifBlank { "Full screen image" },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .transformable(state = transformableState)
            )

            // Top bar with Close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = altText.ifBlank { "Image preview" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF14171C))
                ) {
                    Icon(
                        imageVector = LucideIcons.X,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Bottom action pill: Copy, Save, Share
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MinimalButton(
                    text = "Copy link",
                    icon = LucideIcons.Copy,
                    filled = false,
                    onClick = {
                        clipboard.setText(AnnotatedString(imageUrl))
                        onToast("Copied image URI")
                    }
                )
                MinimalButton(
                    text = "Save",
                    icon = LucideIcons.Download,
                    filled = true,
                    onClick = {
                        scope.launch {
                            val saved = saveReplyImageToDevice(context, imageUrl)
                            onToast(if (saved) "Saved image to gallery" else "Saved URI to clipboard")
                            if (!saved) {
                                clipboard.setText(AnnotatedString(imageUrl))
                            }
                        }
                    }
                )
                MinimalButton(
                    text = "Share",
                    icon = LucideIcons.Link,
                    filled = false,
                    onClick = {
                        shareReplyImage(context, imageUrl, altText)
                    }
                )
            }
        }
    }
}

private suspend fun saveReplyImageToDevice(context: Context, imageUrl: String): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val bitmap: Bitmap? = when {
                imageUrl.startsWith("data:image") -> {
                    val base64Part = imageUrl.substringAfter("base64,", "")
                    val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                imageUrl.startsWith("content://") || imageUrl.startsWith("file://") -> {
                    context.contentResolver.openInputStream(Uri.parse(imageUrl))?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }
                imageUrl.startsWith("http://") || imageUrl.startsWith("https://") -> {
                    URL(imageUrl).openStream().use {
                        BitmapFactory.decodeStream(it)
                    }
                }
                else -> {
                    val f = File(imageUrl)
                    if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
                }
            }
            if (bitmap == null) return@runCatching false

            val filename = "Runway_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Runway")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                true
            } else {
                val dir = File(context.cacheDir, "saved_images").apply { mkdirs() }
                val outFile = File(dir, filename)
                FileOutputStream(outFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                true
            }
        }.getOrDefault(false)
    }

private fun shareReplyImage(context: Context, imageUrl: String, altText: String) {
    runCatching {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, altText.ifBlank { "Runway Image" })
            putExtra(Intent.EXTRA_TEXT, imageUrl)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share image").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
