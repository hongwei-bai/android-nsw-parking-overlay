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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(
    viewModel: CarParkViewModel,
    uiState: CarParkUiState
) {
    val pointsFingerprint = remember(uiState.historySeries) {
        uiState.historySeries.sumOf { it.points.size }
    }
    var xZoom by rememberSaveable(uiState.historyTimespanPreset, pointsFingerprint) {
        mutableFloatStateOf(1f)
    }
    var yZoom by rememberSaveable(uiState.historyTimespanPreset, pointsFingerprint) {
        mutableFloatStateOf(1f)
    }
    var xPan by rememberSaveable(uiState.historyTimespanPreset, pointsFingerprint) {
        mutableFloatStateOf(1f)
    }
    var yPan by rememberSaveable(uiState.historyTimespanPreset, pointsFingerprint) {
        mutableFloatStateOf(0.5f)
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
                    "Track spaces left over time for the currently selected car parks.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(HistoryTimespanPreset.entries) { preset ->
                    val selected = preset == uiState.historyTimespanPreset
                    if (selected) {
                        Button(onClick = { viewModel.setHistoryTimespanPreset(preset) }) {
                            Text(preset.label)
                        }
                    } else {
                        OutlinedButton(onClick = { viewModel.setHistoryTimespanPreset(preset) }) {
                            Text(preset.label)
                        }
                    }
                }
            }
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
                            "Spaces left by date/time",
                            style = MaterialTheme.typography.titleMedium
                        )
                        LegendRow(series = uiState.historySeries)
                        HistoryChart(
                            series = uiState.historySeries,
                            timespanPreset = uiState.historyTimespanPreset,
                            xZoom = xZoom,
                            yZoom = yZoom,
                            xPan = xPan,
                            yPan = yPan
                        )
                    }
                }
            }
        }

        item {
            ZoomControls(
                label = "Time span",
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
                label = "Space span",
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
                    Text("Visible time window", style = MaterialTheme.typography.titleSmall)
                    Slider(value = xPan, onValueChange = { xPan = it })
                }
            }
        }

        if (yZoom > 1.01f) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Visible space window", style = MaterialTheme.typography.titleSmall)
                    Slider(value = yPan, onValueChange = { yPan = it })
                }
            }
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
private fun HistoryChart(
    series: List<HistorySeries>,
    timespanPreset: HistoryTimespanPreset,
    xZoom: Float,
    yZoom: Float,
    xPan: Float,
    yPan: Float
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val tickFormatter = remember(timespanPreset) {
        when {
            timespanPreset.duration.toHours() <= 24 -> DateTimeFormatter.ofPattern("dd MMM\nHH:mm")
            timespanPreset.duration.toDays() <= 7 -> DateTimeFormatter.ofPattern("dd MMM\nHH:mm")
            else -> DateTimeFormatter.ofPattern("dd MMM\nyyyy")
        }
    }

    val allPoints = remember(series) { series.flatMap { it.points } }
    val nowMillis = remember { Instant.now().toEpochMilli() }
    val baseXEnd = maxOf(nowMillis, allPoints.maxOfOrNull { it.epochMillis } ?: nowMillis)
    val baseXStart = baseXEnd - timespanPreset.duration.toMillis()
    val baseXSpan = (baseXEnd - baseXStart).coerceAtLeast(1L)
    val visibleXSpan = (baseXSpan / xZoom).toLong().coerceAtLeast(1L)
    val xOffset = ((baseXSpan - visibleXSpan) * xPan).toLong()
    val visibleXStart = (baseXStart + xOffset).coerceIn(baseXStart, baseXEnd - visibleXSpan)
    val visibleXEnd = visibleXStart + visibleXSpan

    val minY = allPoints.minOfOrNull { it.spacesLeft }?.toFloat() ?: 0f
    val maxY = allPoints.maxOfOrNull { it.spacesLeft }?.toFloat() ?: 10f
    val rawYSpan = (maxY - minY).coerceAtLeast(6f)
    val yPadding = maxOf(4f, rawYSpan * 0.15f)
    val baseYMin = (minY - yPadding).coerceAtLeast(0f)
    val baseYMax = maxY + yPadding
    val baseYSpan = (baseYMax - baseYMin).coerceAtLeast(1f)
    val visibleYSpan = (baseYSpan / yZoom).coerceAtLeast(1f)
    val maxYOffset = (baseYSpan - visibleYSpan).coerceAtLeast(0f)
    val visibleYMin = baseYMin + maxYOffset * yPan.coerceIn(0f, 1f)
    val visibleYMax = visibleYMin + visibleYSpan

    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Y axis: spaces left", style = MaterialTheme.typography.bodySmall)
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

            if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

            val axisPaint = Paint().apply {
                isAntiAlias = true
                color = textColor.toArgb()
                textSize = 12.sp.toPx()
                textAlign = Paint.Align.RIGHT
            }
            val dataLabelPaint = Paint().apply {
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

                val value = visibleYMax - visibleYSpan * fraction
                drawContext.canvas.nativeCanvas.drawText(
                    value.roundToInt().toString(),
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
                val epoch = visibleXStart + (visibleXSpan * fraction).toLong()
                val label = tickFormatter.format(Instant.ofEpochMilli(epoch).atZone(zoneId))
                label.split("\n").forEachIndexed { lineIndex, line ->
                    drawContext.canvas.nativeCanvas.drawText(
                        line,
                        x,
                        topPadding + chartHeight + 18.dp.toPx() + (lineIndex * 14.dp.toPx()),
                        dataLabelPaint
                    )
                }
            }

            drawRect(
                color = gridColor,
                topLeft = Offset(leftPadding, topPadding),
                size = Size(chartWidth, chartHeight),
                style = Stroke(width = 1.dp.toPx())
            )

            series.forEach { seriesItem ->
                val visiblePoints = seriesItem.points.filter {
                    it.epochMillis in visibleXStart..visibleXEnd &&
                        it.spacesLeft.toFloat() in visibleYMin..visibleYMax
                }
                if (visiblePoints.isEmpty()) return@forEach

                val pointColor = Color(seriesItem.colorArgb)
                val path = Path()

                visiblePoints.forEachIndexed { index, point ->
                    val pointOffset = Offset(
                        x = leftPadding + ((point.epochMillis - visibleXStart).toFloat() / visibleXSpan) * chartWidth,
                        y = topPadding + (1f - ((point.spacesLeft - visibleYMin) / visibleYSpan)) * chartHeight
                    )
                    if (index == 0) {
                        path.moveTo(pointOffset.x, pointOffset.y)
                    } else {
                        path.lineTo(pointOffset.x, pointOffset.y)
                    }
                }

                drawPath(
                    path = path,
                    color = pointColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                val labelStep = ceil(visiblePoints.size / 12f).toInt().coerceAtLeast(1)
                visiblePoints.forEachIndexed { index, point ->
                    val pointOffset = Offset(
                        x = leftPadding + ((point.epochMillis - visibleXStart).toFloat() / visibleXSpan) * chartWidth,
                        y = topPadding + (1f - ((point.spacesLeft - visibleYMin) / visibleYSpan)) * chartHeight
                    )
                    drawCircle(
                        color = pointColor,
                        radius = 4.dp.toPx(),
                        center = pointOffset
                    )
                    if (index % labelStep == 0 || index == visiblePoints.lastIndex) {
                        drawContext.canvas.nativeCanvas.drawText(
                            point.spacesLeft.toString(),
                            pointOffset.x,
                            pointOffset.y - 8.dp.toPx(),
                            dataLabelPaint
                        )
                    }
                }
            }
        }
        Text("X axis: date / time", style = MaterialTheme.typography.bodySmall)
    }
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
