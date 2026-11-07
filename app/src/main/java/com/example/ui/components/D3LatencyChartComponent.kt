package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.provider.LatencyBenchmarkSample
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToLong

enum class D3ChartDisplayMode {
    TIME_SERIES,
    PROVIDER_BARS
}

private val ChartSeriesPalette = listOf(
    EmeraldConnected,
    Color(0xFF8FAE8B),
    AmberWarning,
    Color(0xFF5F7D5A),
    CoralError,
    Color(0xFFC9A86A)
)

private data class ProviderLatencySummary(
    val providerName: String,
    val providerId: String,
    val modelId: String,
    val latestMs: Long,
    val avgMs: Long,
    val minMs: Long
)

/**
 * High-precision native Compose Canvas telemetry visualization replacing the legacy WebView renderer
 * so Chromium renderer crashes (`aw_browser_terminator.cc`) can never occur.
 * Plots multi-line response latency time series and provider speed rankings in real time.
 */
@Composable
fun D3LatencyChartWebView(
    samples: List<LatencyBenchmarkSample>,
    fastestProviderName: String?,
    fastestLatencyMs: Long?,
    modifier: Modifier = Modifier,
    onSelectProviderModel: (providerId: String, modelId: String) -> Unit = { _, _ -> }
) {
    var displayMode by rememberSaveable { mutableStateOf(D3ChartDisplayMode.TIME_SERIES) }
    var selectedSample by remember(samples) { mutableStateOf<LatencyBenchmarkSample?>(samples.lastOrNull()) }
    val hiddenProviders = remember { mutableStateMapOf<String, Boolean>() }

    val groupedByProvider = remember(samples) {
        samples.groupBy { it.providerName }
    }
    val providerNames = remember(groupedByProvider) {
        groupedByProvider.keys.toList()
    }
    val providerSummaries = remember(groupedByProvider) {
        groupedByProvider.map { (provName, list) ->
            val sorted = list.sortedBy { it.timestampMs }
            val latest = sorted.last()
            val avg = sorted.map { it.latencyMs }.average().roundToLong()
            val min = sorted.minOf { it.latencyMs }
            ProviderLatencySummary(
                providerName = provName,
                providerId = latest.providerId,
                modelId = latest.modelId,
                latestMs = latest.latencyMs,
                avgMs = avg,
                minMs = min
            )
        }.sortedBy { it.latestMs }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("d3_latency_chart_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Chart Subheader: Mode selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Real-Time Response Latency Plot",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = displayMode == D3ChartDisplayMode.TIME_SERIES,
                        onClick = { displayMode = D3ChartDisplayMode.TIME_SERIES },
                        leadingIcon = {
                            Icon(
                                Icons.Default.ShowChart,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        label = { Text("Time Series", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("d3_tab_time_series")
                    )
                    FilterChip(
                        selected = displayMode == D3ChartDisplayMode.PROVIDER_BARS,
                        onClick = { displayMode = D3ChartDisplayMode.PROVIDER_BARS },
                        leadingIcon = {
                            Icon(
                                Icons.Default.BarChart,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        label = { Text("Ranking", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("d3_tab_ranking")
                    )
                }
            }

            // Native Compose Canvas Telemetry Plot
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top telemetry badge row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = EmeraldConnected.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, EmeraldConnected.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "🏆 FASTEST: ${fastestProviderName ?: "Measuring…"} (${fastestLatencyMs ?: 0L}ms)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = EmeraldConnected,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = if (displayMode == D3ChartDisplayMode.TIME_SERIES) {
                            "LATENCY (ms) vs TIME"
                        } else {
                            "SPEED RANKING (ms)"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (samples.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No latency telemetry recorded yet. Tap 'Run Probe' to benchmark.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (displayMode == D3ChartDisplayMode.TIME_SERIES) {
                    NativeLatencyTimeSeriesCanvas(
                        groupedByProvider = groupedByProvider,
                        providerNames = providerNames,
                        hiddenProviders = hiddenProviders,
                        selectedSample = selectedSample,
                        onSelectSample = { sample ->
                            selectedSample = sample
                            onSelectProviderModel(sample.providerId, sample.modelId)
                        }
                    )

                    // Selected point tooltip bar
                    selectedSample?.let { sel ->
                        val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                .clickable { onSelectProviderModel(sel.providerId, sel.modelId) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${sel.providerName} • ${sel.modelId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Sampled at ${timeFormat.format(Date(sel.timestampMs))} • Tap to activate model",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${sel.latencyMs}ms",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Interactive legend chips to toggle individual provider lines
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        providerNames.forEachIndexed { idx, provName ->
                            val seriesColor = ChartSeriesPalette[idx % ChartSeriesPalette.size]
                            val isHidden = hiddenProviders[provName] == true
                            Surface(
                                color = if (isHidden) MaterialTheme.colorScheme.surface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isHidden) MaterialTheme.colorScheme.outline.copy(alpha = 0.4f) else seriesColor.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier.clickable {
                                    hiddenProviders[provName] = !isHidden
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else seriesColor)
                                    )
                                    Text(
                                        text = provName,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                } else {
                    NativeLatencyBarRankingView(
                        summaries = providerSummaries,
                        onSelectProviderModel = onSelectProviderModel
                    )
                }
            }

            // Footer note
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Compose Canvas telemetry • Tap data points or bars to switch model",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (fastestLatencyMs != null && fastestLatencyMs > 0) {
                    Text(
                        text = "Lowest: ${fastestLatencyMs}ms",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = EmeraldConnected,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun NativeLatencyTimeSeriesCanvas(
    groupedByProvider: Map<String, List<LatencyBenchmarkSample>>,
    providerNames: List<String>,
    hiddenProviders: Map<String, Boolean>,
    selectedSample: LatencyBenchmarkSample?,
    onSelectSample: (LatencyBenchmarkSample) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val chartGridColor = MaterialTheme.colorScheme.outline
    val chartPointCoreColor = MaterialTheme.colorScheme.background
    val axisTextStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 9.sp,
        fontFamily = JetBrainsMonoFontFamily
    )

    val visibleSamples = remember(groupedByProvider, hiddenProviders.toMap()) {
        groupedByProvider
            .filterKeys { hiddenProviders[it] != true }
            .values
            .flatten()
    }

    val minTimestamp = remember(visibleSamples) {
        visibleSamples.minOfOrNull { it.timestampMs } ?: 0L
    }
    val maxTimestamp = remember(visibleSamples) {
        val rawMax = visibleSamples.maxOfOrNull { it.timestampMs } ?: 1L
        if (rawMax <= minTimestamp) minTimestamp + 60_000L else rawMax
    }
    val maxLatency = remember(visibleSamples) {
        val peak = visibleSamples.maxOfOrNull { it.latencyMs } ?: 500L
        max(100L, (peak * 1.15).roundToLong())
    }

    // Store plotted screen coordinates for tap hit-testing
    val plottedPoints = remember { mutableListOf<Pair<Offset, LatencyBenchmarkSample>>() }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .pointerInput(visibleSamples) {
                detectTapGestures { tapOffset ->
                    val nearest = plottedPoints.minByOrNull { (pt, _) ->
                        hypot(pt.x - tapOffset.x, pt.y - tapOffset.y)
                    }
                    if (nearest != null && hypot(nearest.first.x - tapOffset.x, nearest.first.y - tapOffset.y) <= 64f) {
                        onSelectSample(nearest.second)
                    }
                }
            }
    ) {
        plottedPoints.clear()
        val leftPad = 44.dp.toPx()
        val rightPad = 12.dp.toPx()
        val topPad = 10.dp.toPx()
        val bottomPad = 22.dp.toPx()

        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        // Draw horizontal grid lines and Y-axis ms labels
        val gridSteps = 4
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        for (i in 0..gridSteps) {
            val frac = i.toFloat() / gridSteps
            val y = topPad + plotH * (1f - frac)
            val msValue = (maxLatency * frac).roundToLong()

            drawLine(
                color = chartGridColor.copy(alpha = 0.55f),
                start = Offset(leftPad, y),
                end = Offset(leftPad + plotW, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = if (i > 0) dashEffect else null
            )

            val labelLayout = textMeasurer.measure("${msValue}ms", axisTextStyle)
            drawText(
                textLayoutResult = labelLayout,
                topLeft = Offset(
                    x = (leftPad - labelLayout.size.width - 6.dp.toPx()).coerceAtLeast(0f),
                    y = y - labelLayout.size.height / 2f
                )
            )
        }

        // Draw each provider series
        providerNames.forEachIndexed { idx, provName ->
            if (hiddenProviders[provName] == true) return@forEachIndexed
            val pts = groupedByProvider[provName]?.sortedBy { it.timestampMs }.orEmpty()
            if (pts.isEmpty()) return@forEachIndexed

            val color = ChartSeriesPalette[idx % ChartSeriesPalette.size]
            val coords = pts.map { sample ->
                val xRatio = if (maxTimestamp > minTimestamp) {
                    (sample.timestampMs - minTimestamp).toFloat() / (maxTimestamp - minTimestamp).toFloat()
                } else {
                    0.5f
                }
                val yRatio = (sample.latencyMs.toFloat() / maxLatency.toFloat()).coerceIn(0f, 1f)
                val offset = Offset(
                    x = leftPad + xRatio * plotW,
                    y = topPad + plotH * (1f - yRatio)
                )
                plottedPoints.add(offset to sample)
                offset
            }

            if (coords.size >= 2) {
                val strokePath = Path().apply {
                    moveTo(coords.first().x, coords.first().y)
                    for (i in 0 until coords.size - 1) {
                        val p0 = coords[i]
                        val p1 = coords[i + 1]
                        val ctrlX = (p0.x + p1.x) / 2f
                        cubicTo(ctrlX, p0.y, ctrlX, p1.y, p1.x, p1.y)
                    }
                }

                val fillPath = Path().apply {
                    addPath(strokePath)
                    lineTo(coords.last().x, topPad + plotH)
                    lineTo(coords.first().x, topPad + plotH)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(color.copy(alpha = 0.26f), Color.Transparent),
                        startY = topPad,
                        endY = topPad + plotH
                    )
                )

                drawPath(
                    path = strokePath,
                    color = color,
                    style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Data point nodes
            coords.forEachIndexed { ptIdx, pt ->
                val sample = pts[ptIdx]
                val isSelected = selectedSample?.timestampMs == sample.timestampMs &&
                    selectedSample.providerId == sample.providerId
                if (isSelected) {
                    drawCircle(
                        color = color.copy(alpha = 0.3f),
                        radius = 8.dp.toPx(),
                        center = pt
                    )
                }
                drawCircle(
                    color = chartPointCoreColor,
                    radius = 4.5.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = color,
                    radius = 3.2.dp.toPx(),
                    center = pt
                )
            }
        }
    }
}

@Composable
private fun NativeLatencyBarRankingView(
    summaries: List<ProviderLatencySummary>,
    onSelectProviderModel: (providerId: String, modelId: String) -> Unit
) {
    val chartTrackColor = MaterialTheme.colorScheme.background
    val maxMs = remember(summaries) {
        max(100L, summaries.maxOfOrNull { it.latestMs } ?: 500L)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        summaries.forEachIndexed { index, item ->
            val barColor = when (index) {
                0 -> EmeraldConnected
                1 -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.primary
            }
            val fraction = (item.latestMs.toFloat() / (maxMs * 1.15f)).coerceIn(0.08f, 1f)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onSelectProviderModel(item.providerId, item.modelId) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${if (index == 0) "🏆 " else "#${index + 1} "}${item.providerName} (${item.modelId})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${item.latestMs}ms (avg ${item.avgMs}ms)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = barColor
                    )
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                ) {
                    drawRoundRect(
                        color = chartTrackColor,
                        size = size,
                        cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                    )
                    drawRoundRect(
                        color = barColor,
                        size = Size(width = size.width * fraction, height = size.height),
                        cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                    )
                }
            }
        }
    }
}
