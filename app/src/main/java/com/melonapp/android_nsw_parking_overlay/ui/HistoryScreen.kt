package com.melonapp.android_nsw_parking_overlay.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.roundToInt

private data class MorningUnavailablePoint(
    val date: LocalDate,
    val epochMillis: Long,
    val minutesOfDay: Int,
    val inferred: Boolean
)

@Composable
fun HistoryScreen(
    viewModel: CarParkViewModel,
    uiState: CarParkUiState
) {
    var xZoom by rememberSaveable(uiState.historyTimespanPreset) {
        mutableFloatStateOf(1f)
    }
    var yZoom by rememberSaveable(uiState.historyTimespanPreset) {
        mutableFloatStateOf(1f)
    }
    var xPan by rememberSaveable(uiState.historyTimespanPreset) {
        mutableFloatStateOf(1f)
    }
    var yPan by rememberSaveable(uiState.historyTimespanPreset) {
        mutableFloatStateOf(0.5f)
    }

    val shortPresets = remember {
        listOf(
            HistoryTimespanPreset.FIFTEEN_MINUTES,
            HistoryTimespanPreset.ONE_HOUR,
            HistoryTimespanPreset.FOUR_HOURS,
            HistoryTimespanPreset.ONE_DAY
        )
    }
    val longPresets = remember {
        listOf(
            HistoryTimespanPreset.ONE_WEEK,
            HistoryTimespanPreset.ONE_MONTH,
            HistoryTimespanPreset.ONE_QUARTER,
            HistoryTimespanPreset.ONE_YEAR
        )
    }
    val windowLabel = remember(
        uiState.historyTimespanPreset,
        uiState.historyWindowEndEpochMillis
    ) {
        formatHistoryWindowLabel(
            preset = uiState.historyTimespanPreset,
            windowEndEpochMillis = uiState.historyWindowEndEpochMillis
        )
    }
    val canShiftBackward = remember(
        uiState.historyMinEpochMillis,
        uiState.historyWindowEndEpochMillis,
        uiState.historyTimespanPreset
    ) {
        val minEpoch = uiState.historyMinEpochMillis ?: return@remember false
        val windowStart = uiState.historyWindowEndEpochMillis - uiState.historyTimespanPreset.duration.toMillis()
        windowStart > minEpoch
    }
    val canShiftForward = remember(
        uiState.historyMaxEpochMillis,
        uiState.historyWindowEndEpochMillis
    ) {
        val maxEpoch = uiState.historyMaxEpochMillis ?: return@remember false
        uiState.historyWindowEndEpochMillis < maxEpoch
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("History", style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (uiState.historyTimespanPreset.isLongSpan) {
                        "Long-range view shows the first time each morning a car park effectively becomes unavailable."
                    } else {
                        "Short-range view tracks spaces left over time for the currently selected car parks."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            PresetRow(
                title = "Short range",
                presets = shortPresets,
                selectedPreset = uiState.historyTimespanPreset,
                onPresetSelected = viewModel::setHistoryTimespanPreset
            )
        }

        item {
            PresetRow(
                title = "Long range",
                presets = longPresets,
                selectedPreset = uiState.historyTimespanPreset,
                onPresetSelected = viewModel::setHistoryTimespanPreset
            )
        }

        item {
            WindowShiftRow(
                label = windowLabel,
                canShiftBackward = canShiftBackward,
                canShiftForward = canShiftForward,
                onShiftBackward = { viewModel.shiftHistoryWindow(direction = -1) },
                onShiftForward = { viewModel.shiftHistoryWindow(direction = 1) }
            )
        }

        item {
            if (uiState.selectedCarParks.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Select one or more car parks in the Selected tab to see their history here.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else if (uiState.historySeries.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No history has been recorded for the selected car parks in the ${uiState.historyTimespanPreset.label} window yet.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            if (uiState.historyTimespanPreset.isLongSpan) {
                                "First unavailable time each morning"
                            } else {
                                "Spaces left by date/time"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (uiState.historyTimespanPreset.isLongSpan) {
                            Text(
                                "`~HH:mm` means the app inferred the car park was effectively full even though the feed never reached 0.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        LegendRow(series = uiState.historySeries)
                        if (uiState.historyTimespanPreset.isLongSpan) {
                            LongSpanHistoryChart(
                                series = uiState.historySeries,
                                timespanPreset = uiState.historyTimespanPreset,
                                windowEndEpochMillis = uiState.historyWindowEndEpochMillis,
                                xZoom = xZoom,
                                yZoom = yZoom,
                                xPan = xPan,
                                yPan = yPan
                            )
                        } else {
                            ShortSpanHistoryChart(
                                series = uiState.historySeries,
                                timespanPreset = uiState.historyTimespanPreset,
                                windowEndEpochMillis = uiState.historyWindowEndEpochMillis,
                                xZoom = xZoom,
                                yZoom = yZoom,
                                xPan = xPan,
                                yPan = yPan
                            )
                        }
                    }
                }
            }
        }

        item {
            ZoomControls(
                label = if (uiState.historyTimespanPreset.isLongSpan) "Date span" else "Time span",
                zoom = xZoom,
                onZoomChange = {
                    xZoom = it.coerceIn(1f, 8f)
                    if (xZoom <= 1.01f) {
                        xPan = 1f
                    }
                },
                onReset = {
                    xZoom = 1f
                    xPan = 1f
                }
            )
        }

        item {
            ZoomControls(
                label = if (uiState.historyTimespanPreset.isLongSpan) "Morning time span" else "Space span",
                zoom = yZoom,
                onZoomChange = {
                    yZoom = it.coerceIn(1f, 8f)
                    if (yZoom <= 1.01f) {
                        yPan = 0.5f
                    }
                },
                onReset = {
                    yZoom = 1f
                    yPan = 0.5f
                }
            )
        }

        if (xZoom > 1.01f) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (uiState.historyTimespanPreset.isLongSpan) "Visible date window" else "Visible time window",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(value = xPan, onValueChange = { xPan = it })
                }
            }
        }

        if (yZoom > 1.01f) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (uiState.historyTimespanPreset.isLongSpan) "Visible morning time window" else "Visible space window",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(value = yPan, onValueChange = { yPan = it })
                }
            }
        }
    }
}

@Composable
private fun PresetRow(
    title: String,
    presets: List<HistoryTimespanPreset>,
    selectedPreset: HistoryTimespanPreset,
    onPresetSelected: (HistoryTimespanPreset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(presets) { preset ->
                val selected = preset == selectedPreset
                if (selected) {
                    Button(onClick = { onPresetSelected(preset) }) {
                        Text(preset.label)
                    }
                } else {
                    OutlinedButton(onClick = { onPresetSelected(preset) }) {
                        Text(preset.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun WindowShiftRow(
    label: String,
    canShiftBackward: Boolean,
    canShiftForward: Boolean,
    onShiftBackward: () -> Unit,
    onShiftForward: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = onShiftBackward, enabled = canShiftBackward) {
            Text("<")
        }
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium
        )
        OutlinedButton(onClick = onShiftForward, enabled = canShiftForward) {
            Text(">")
        }
    }
}

@Composable
private fun ZoomControls(
    label: String,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text("${zoom.format(1)}x", style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onZoomChange(zoom / 1.5f) }) {
                Text("-")
            }
            OutlinedButton(onClick = { onReset() }) {
                Text("Reset")
            }
            OutlinedButton(onClick = { onZoomChange(zoom * 1.5f) }) {
                Text("+")
            }
        }
    }
}

@Composable
private fun LegendRow(series: List<HistorySeries>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(series) { seriesItem ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color(seriesItem.colorArgb), CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    seriesItem.carParkName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ShortSpanHistoryChart(
    series: List<HistorySeries>,
    timespanPreset: HistoryTimespanPreset,
    windowEndEpochMillis: Long,
    xZoom: Float,
    yZoom: Float,
    xPan: Float,
    yPan: Float
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val tickFormatter = remember(timespanPreset) {
        when (timespanPreset) {
            HistoryTimespanPreset.FIFTEEN_MINUTES,
            HistoryTimespanPreset.ONE_HOUR,
            HistoryTimespanPreset.FOUR_HOURS -> DateTimeFormatter.ofPattern("HH:mm")
            else -> DateTimeFormatter.ofPattern("dd MMM\nHH:mm")
        }
    }
    val allPoints = remember(series) { series.flatMap { it.points } }
    val baseXEnd = windowEndEpochMillis
    val baseXStart = baseXEnd - timespanPreset.duration.toMillis()
    val visibleXRange = rememberVisibleLongRange(baseXStart, baseXEnd, xZoom, xPan)
    val yRange = rememberShortYRange(allPoints, yZoom, yPan)

    ChartFrame(
        yAxisLabel = "Y axis: spaces left",
        xAxisLabel = "X axis: date / time",
        emptyMessage = "No points are visible in the current zoom window."
    ) { leftPadding, topPadding, chartWidth, chartHeight, gridColor, textColor ->
        drawLinearGrid(
            leftPadding = leftPadding,
            topPadding = topPadding,
            chartWidth = chartWidth,
            chartHeight = chartHeight,
            yLabelProvider = { fraction ->
                (yRange.endInclusive - yRange.span * fraction).roundToInt().toString()
            },
            xLabelProvider = { fraction ->
                val epoch = visibleXRange.start + (visibleXRange.span * fraction).toLong()
                tickFormatter.format(Instant.ofEpochMilli(epoch).atZone(zoneId))
            },
            gridColor = gridColor,
            textColor = textColor
        )

        series.forEach { seriesItem ->
            val visiblePoints = seriesItem.points.filter {
                it.epochMillis in visibleXRange.start..visibleXRange.end &&
                    it.spacesLeft.toFloat() in yRange.start..yRange.endInclusive
            }
            drawHistoryLine(
                points = visiblePoints,
                color = Color(seriesItem.colorArgb),
                textColor = textColor,
                leftPadding = leftPadding,
                topPadding = topPadding,
                chartWidth = chartWidth,
                chartHeight = chartHeight,
                xRangeStart = visibleXRange.start,
                xRangeSpan = visibleXRange.span,
                yRangeStart = yRange.start,
                yRangeSpan = yRange.span,
                pointLabelProvider = { it.spacesLeft.toString() }
            )
        }
    }
}

@Composable
private fun LongSpanHistoryChart(
    series: List<HistorySeries>,
    timespanPreset: HistoryTimespanPreset,
    windowEndEpochMillis: Long,
    xZoom: Float,
    yZoom: Float,
    xPan: Float,
    yPan: Float
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val dailySeries = remember(series) {
        series.associateWith { historySeries ->
            historySeries.toMorningUnavailablePoints(
                zoneId = zoneId,
                useSmartInference = historySeries.smartUnavailableDetectionEnabled
            )
        }
    }
    val allPoints = remember(dailySeries) { dailySeries.values.flatten() }
    val baseXEnd = windowEndEpochMillis
    val baseXStart = baseXEnd - timespanPreset.duration.toMillis()
    val visibleXRange = rememberVisibleLongRange(baseXStart, baseXEnd, xZoom, xPan)
    val yRange = rememberMorningYRange(allPoints, yZoom, yPan)
    val tickFormatter = remember(timespanPreset) {
        when (timespanPreset) {
            HistoryTimespanPreset.ONE_YEAR -> DateTimeFormatter.ofPattern("MMM yy")
            else -> DateTimeFormatter.ofPattern("dd MMM")
        }
    }

    val hasRenderablePoints = allPoints.any {
        it.epochMillis in visibleXRange.start..visibleXRange.end &&
            it.minutesOfDay.toFloat() in yRange.start..yRange.endInclusive
    }

    if (!hasRenderablePoints) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Y axis: morning unavailable time", style = MaterialTheme.typography.bodySmall)
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "No unavailable morning could be inferred in this zoom window yet.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text("X axis: date", style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    ChartFrame(
        yAxisLabel = "Y axis: morning unavailable time",
        xAxisLabel = "X axis: date",
        emptyMessage = "No unavailable morning could be inferred in this zoom window yet."
    ) { leftPadding, topPadding, chartWidth, chartHeight, gridColor, textColor ->
        drawLinearGrid(
            leftPadding = leftPadding,
            topPadding = topPadding,
            chartWidth = chartWidth,
            chartHeight = chartHeight,
            yLabelProvider = { fraction ->
                formatMinutesOfDay((yRange.endInclusive - yRange.span * fraction).roundToInt())
            },
            xLabelProvider = { fraction ->
                val epoch = visibleXRange.start + (visibleXRange.span * fraction).toLong()
                tickFormatter.format(Instant.ofEpochMilli(epoch).atZone(zoneId))
            },
            gridColor = gridColor,
            textColor = textColor
        )

        series.forEach { historySeries ->
            val visiblePoints = dailySeries[historySeries].orEmpty().filter {
                it.epochMillis in visibleXRange.start..visibleXRange.end &&
                    it.minutesOfDay.toFloat() in yRange.start..yRange.endInclusive
            }
            drawHistoryLine(
                points = visiblePoints,
                color = Color(historySeries.colorArgb),
                textColor = textColor,
                leftPadding = leftPadding,
                topPadding = topPadding,
                chartWidth = chartWidth,
                chartHeight = chartHeight,
                xRangeStart = visibleXRange.start,
                xRangeSpan = visibleXRange.span,
                yRangeStart = yRange.start,
                yRangeSpan = yRange.span,
                xValueProvider = { it.epochMillis },
                yValueProvider = { it.minutesOfDay.toFloat() },
                pointLabelProvider = { point ->
                    if (point.inferred) "~${formatMinutesOfDay(point.minutesOfDay)}" else formatMinutesOfDay(point.minutesOfDay)
                }
            )
        }
    }
}

@Composable
private fun ChartFrame(
    yAxisLabel: String,
    xAxisLabel: String,
    emptyMessage: String,
    drawChart: androidx.compose.ui.graphics.drawscope.DrawScope.(
        leftPadding: Float,
        topPadding: Float,
        chartWidth: Float,
        chartHeight: Float,
        gridColor: Color,
        textColor: Color
    ) -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(yAxisLabel, style = MaterialTheme.typography.bodySmall)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
        ) {
            val leftPadding = 76.dp.toPx()
            val topPadding = 18.dp.toPx()
            val rightPadding = 18.dp.toPx()
            val bottomPadding = 54.dp.toPx()
            val chartWidth = size.width - leftPadding - rightPadding
            val chartHeight = size.height - topPadding - bottomPadding

            if (chartWidth <= 0f || chartHeight <= 0f) {
                val paint = Paint().apply {
                    isAntiAlias = true
                    color = textColor.toArgb()
                    textSize = 12.sp.toPx()
                    textAlign = Paint.Align.CENTER
                }
                drawContext.canvas.nativeCanvas.drawText(
                    emptyMessage,
                    size.width / 2f,
                    size.height / 2f,
                    paint
                )
                return@Canvas
            }

            drawChart(leftPadding, topPadding, chartWidth, chartHeight, gridColor, textColor)
        }
        Text(xAxisLabel, style = MaterialTheme.typography.bodySmall)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLinearGrid(
    leftPadding: Float,
    topPadding: Float,
    chartWidth: Float,
    chartHeight: Float,
    yLabelProvider: (Float) -> String,
    xLabelProvider: (Float) -> String,
    gridColor: Color,
    textColor: Color
) {
    val axisPaint = Paint().apply {
        isAntiAlias = true
        color = textColor.toArgb()
        textSize = 12.sp.toPx()
        textAlign = Paint.Align.RIGHT
    }
    val xLabelPaint = Paint().apply {
        isAntiAlias = true
        color = textColor.toArgb()
        textSize = 11.sp.toPx()
        textAlign = Paint.Align.CENTER
    }

    repeat(5) { index ->
        val fraction = index / 4f
        val y = topPadding + chartHeight * fraction
        drawLine(
            color = gridColor,
            start = Offset(leftPadding, y),
            end = Offset(leftPadding + chartWidth, y),
            strokeWidth = 1.dp.toPx()
        )
        drawContext.canvas.nativeCanvas.drawText(
            yLabelProvider(fraction),
            leftPadding - 12.dp.toPx(),
            y + 4.dp.toPx(),
            axisPaint
        )
    }

    repeat(5) { index ->
        val fraction = index / 4f
        val x = leftPadding + chartWidth * fraction
        drawLine(
            color = gridColor,
            start = Offset(x, topPadding),
            end = Offset(x, topPadding + chartHeight),
            strokeWidth = 1.dp.toPx()
        )
        xLabelProvider(fraction).split("\n").forEachIndexed { lineIndex, line ->
            drawContext.canvas.nativeCanvas.drawText(
                line,
                x,
                topPadding + chartHeight + 18.dp.toPx() + (lineIndex * 14.dp.toPx()),
                xLabelPaint
            )
        }
    }

    drawRect(
        color = gridColor,
        topLeft = Offset(leftPadding, topPadding),
        size = Size(chartWidth, chartHeight),
        style = Stroke(width = 1.dp.toPx())
    )
}

private fun <T> androidx.compose.ui.graphics.drawscope.DrawScope.drawHistoryLine(
    points: List<T>,
    color: Color,
    textColor: Color,
    leftPadding: Float,
    topPadding: Float,
    chartWidth: Float,
    chartHeight: Float,
    xRangeStart: Long,
    xRangeSpan: Long,
    yRangeStart: Float,
    yRangeSpan: Float,
    xValueProvider: (T) -> Long = { (it as HistoryPoint).epochMillis },
    yValueProvider: (T) -> Float = { (it as HistoryPoint).spacesLeft.toFloat() },
    pointLabelProvider: (T) -> String
) {
    if (points.isEmpty() || xRangeSpan <= 0L || yRangeSpan <= 0f) return

    val labelPaint = Paint().apply {
        isAntiAlias = true
        this.color = textColor.toArgb()
        textSize = 11.sp.toPx()
        textAlign = Paint.Align.CENTER
    }

    val path = Path()
    points.forEachIndexed { index, point ->
        val offset = Offset(
            x = leftPadding + ((xValueProvider(point) - xRangeStart).toFloat() / xRangeSpan) * chartWidth,
            y = topPadding + (1f - ((yValueProvider(point) - yRangeStart) / yRangeSpan)) * chartHeight
        )
        if (index == 0) {
            path.moveTo(offset.x, offset.y)
        } else {
            path.lineTo(offset.x, offset.y)
        }
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
    )

    val labelStep = ceil(points.size / 12f).toInt().coerceAtLeast(1)
    points.forEachIndexed { index, point ->
        val offset = Offset(
            x = leftPadding + ((xValueProvider(point) - xRangeStart).toFloat() / xRangeSpan) * chartWidth,
            y = topPadding + (1f - ((yValueProvider(point) - yRangeStart) / yRangeSpan)) * chartHeight
        )
        drawCircle(
            color = color,
            radius = 4.dp.toPx(),
            center = offset
        )
        if (index % labelStep == 0 || index == points.lastIndex) {
            drawContext.canvas.nativeCanvas.drawText(
                pointLabelProvider(point),
                offset.x,
                offset.y - 8.dp.toPx(),
                labelPaint
            )
        }
    }
}

private data class VisibleLongRange(
    val start: Long,
    val end: Long,
    val span: Long
)

private data class VisibleFloatRange(
    val start: Float,
    val endInclusive: Float,
    val span: Float
)

private fun rememberVisibleLongRange(
    baseStart: Long,
    baseEnd: Long,
    zoom: Float,
    pan: Float
): VisibleLongRange {
    val baseSpan = (baseEnd - baseStart).coerceAtLeast(1L)
    val effectiveZoom = zoom.coerceAtLeast(1f)
    val visibleSpan = (baseSpan / effectiveZoom).toLong().coerceAtLeast(1L)
    val maxOffset = (baseSpan - visibleSpan).coerceAtLeast(0L)
    val offset = (maxOffset * pan.coerceIn(0f, 1f)).toLong()
    val start = (baseStart + offset).coerceIn(baseStart, baseEnd - visibleSpan)
    return VisibleLongRange(
        start = start,
        end = start + visibleSpan,
        span = visibleSpan
    )
}

private fun rememberShortYRange(
    allPoints: List<HistoryPoint>,
    zoom: Float,
    pan: Float
): VisibleFloatRange {
    val minY = allPoints.minOfOrNull { it.spacesLeft }?.toFloat() ?: 0f
    val maxY = allPoints.maxOfOrNull { it.spacesLeft }?.toFloat() ?: 10f
    val rawYSpan = (maxY - minY).coerceAtLeast(6f)
    val yPadding = maxOf(4f, rawYSpan * 0.15f)
    return visibleFloatRange(
        baseStart = (minY - yPadding).coerceAtLeast(0f),
        baseEnd = maxY + yPadding,
        zoom = zoom,
        pan = pan
    )
}

private fun rememberMorningYRange(
    allPoints: List<MorningUnavailablePoint>,
    zoom: Float,
    pan: Float
): VisibleFloatRange {
    val minMinutes = minOf(5 * 60f, allPoints.minOfOrNull { it.minutesOfDay }?.toFloat() ?: 5 * 60f)
    val maxMinutes = maxOf(13 * 60f, allPoints.maxOfOrNull { it.minutesOfDay }?.toFloat() ?: 13 * 60f)
    return visibleFloatRange(
        baseStart = minMinutes,
        baseEnd = maxMinutes,
        zoom = zoom,
        pan = pan
    )
}

private fun visibleFloatRange(
    baseStart: Float,
    baseEnd: Float,
    zoom: Float,
    pan: Float
): VisibleFloatRange {
    val baseSpan = (baseEnd - baseStart).coerceAtLeast(1f)
    val effectiveZoom = zoom.coerceAtLeast(1f)
    val visibleSpan = (baseSpan / effectiveZoom).coerceAtLeast(1f)
    val maxOffset = (baseSpan - visibleSpan).coerceAtLeast(0f)
    val start = baseStart + maxOffset * pan.coerceIn(0f, 1f)
    return VisibleFloatRange(
        start = start,
        endInclusive = start + visibleSpan,
        span = visibleSpan
    )
}

private fun inferMorningUnavailablePoint(
    date: LocalDate,
    points: List<HistoryPoint>,
    zoneId: ZoneId,
    useSmartInference: Boolean
): MorningUnavailablePoint? {
    val zeroPoint = points.firstOrNull { it.spacesLeft <= 0 }
    if (zeroPoint != null) {
        return zeroPoint.toMorningUnavailablePoint(date, zoneId, inferred = false)
    }
    if (!useSmartInference) return null

    val lowThreshold = 2
    points.forEachIndexed { index, point ->
        if (point.spacesLeft > lowThreshold) return@forEachIndexed
        val nextPoints = points.drop(index).takeWhile {
            it.epochMillis - point.epochMillis <= 90 * 60 * 1000L
        }
        val staysNearlyEmpty = nextPoints.size >= 2 && nextPoints.all { it.spacesLeft <= lowThreshold + 1 }
        val noStrongRecovery = points.drop(index).none { it.spacesLeft > lowThreshold + 2 }
        if (staysNearlyEmpty || noStrongRecovery) {
            return point.toMorningUnavailablePoint(date, zoneId, inferred = true)
        }
    }

    val dayMinimum = points.minOfOrNull { it.spacesLeft } ?: return null
    if (dayMinimum > 5) return null
    val firstMinimumIndex = points.indexOfFirst { it.spacesLeft == dayMinimum }
    if (firstMinimumIndex < 0) return null

    val candidate = points[firstMinimumIndex]
    val laterPoints = points.drop(firstMinimumIndex)
    val flatNearMinimum = laterPoints.size >= 3 && laterPoints.take(4).all {
        (it.spacesLeft - dayMinimum).absoluteValue <= 1
    }
    val inMorning = Instant.ofEpochMilli(candidate.epochMillis)
        .atZone(zoneId)
        .toLocalTime()
        .hour <= 12

    return if (flatNearMinimum && inMorning) {
        candidate.toMorningUnavailablePoint(date, zoneId, inferred = true)
    } else {
        null
    }
}

private fun HistorySeries.toMorningUnavailablePoints(
    zoneId: ZoneId,
    useSmartInference: Boolean
): List<MorningUnavailablePoint> {
    return points
        .groupBy { Instant.ofEpochMilli(it.epochMillis).atZone(zoneId).toLocalDate() }
        .mapNotNull { (date, dailyPoints) ->
            inferMorningUnavailablePoint(
                date = date,
                points = dailyPoints.sortedBy { it.epochMillis },
                zoneId = zoneId,
                useSmartInference = useSmartInference
            )
        }
        .sortedBy { it.epochMillis }
}

private fun formatHistoryWindowLabel(
    preset: HistoryTimespanPreset,
    windowEndEpochMillis: Long
): String {
    val zoneId = ZoneId.systemDefault()
    val end = Instant.ofEpochMilli(windowEndEpochMillis).atZone(zoneId)
    val start = end.minus(preset.duration)
    return if (preset.isLongSpan) {
        val formatter = DateTimeFormatter.ofPattern("d MMM")
        "${formatter.format(start)} - ${formatter.format(end)}"
    } else {
        val formatter = DateTimeFormatter.ofPattern("H:mm")
        val startDay = DateTimeFormatter.ofPattern("d MMM").format(start)
        val endDay = DateTimeFormatter.ofPattern("d MMM").format(end)
        if (start.toLocalDate() == end.toLocalDate()) {
            "${formatter.format(start)} - ${formatter.format(end)}"
        } else {
            "$startDay ${formatter.format(start)} - $endDay ${formatter.format(end)}"
        }
    }
}

private fun HistoryPoint.toMorningUnavailablePoint(
    date: LocalDate,
    zoneId: ZoneId,
    inferred: Boolean
): MorningUnavailablePoint {
    val localDateTime = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDateTime()
    return MorningUnavailablePoint(
        date = date,
        epochMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
        minutesOfDay = localDateTime.hour * 60 + localDateTime.minute,
        inferred = inferred
    )
}

private fun formatMinutesOfDay(totalMinutes: Int): String {
    val safeMinutes = totalMinutes.coerceIn(0, 23 * 60 + 59)
    val hours = safeMinutes / 60
    val minutes = safeMinutes % 60
    return "%02d:%02d".format(hours, minutes)
}

private fun Float.format(decimals: Int): String {
    return "%.${decimals}f".format(this)
}

private fun Color.toArgb(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).roundToInt(),
        (red * 255).roundToInt(),
        (green * 255).roundToInt(),
        (blue * 255).roundToInt()
    )
}
