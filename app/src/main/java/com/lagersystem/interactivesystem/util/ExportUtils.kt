package com.lagersystem.interactivesystem.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.lagersystem.interactivesystem.data.item.Item
import com.lagersystem.interactivesystem.data.log.ActionType
import com.lagersystem.interactivesystem.data.log.LogEntry
import com.lagersystem.interactivesystem.data.warehouse.Warehouse
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ExportUtils {

    fun exportWarehousePdf(context: Context, warehouse: Warehouse, items: List<Item>, logs: List<LogEntry>) {
        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val left = 40f
        val tableWidth = 515f
        val columnWidths = floatArrayOf(90f, 143f, 112f, 52f, 55f, 63f)
        val headers = listOf("Inventar-\nnr.", "Name / Modell", "Serien-\nnr.", "Vorhan-\nden", "Verwen-\ndet", "Verschwun-\nden")
        val paint = Paint().apply {
            textSize = 12f
            color = Color.BLACK
        }
        val titlePaint = Paint().apply {
            textSize = 18f
            isFakeBoldText = true
            color = Color.BLACK
        }
        val headerPaint = Paint(paint).apply {
            textSize = 9f
            isFakeBoldText = true
        }
        val cellPaint = Paint(paint).apply { textSize = 9f }
        val gridPaint = Paint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
        }
        val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        var pageNumber = 0
        var page = pdfDocument.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, ++pageNumber).create()
        )
        var y = 0f

        fun drawPageHeading() {
            val canvas = page.canvas
            y = 40f
            canvas.drawText("Lagerbericht: ${warehouse.name}", left, y, titlePaint)
            y += 25f
            canvas.drawText("Datum: $date", left, y, paint)
            y += 25f
            val tableTop = y
            val headerHeight = 34f
            canvas.drawRect(left, tableTop, left + tableWidth, tableTop + headerHeight, gridPaint)
            var x = left
            headers.forEachIndexed { index, header ->
                val lines = header.split('\n')
                lines.forEachIndexed { lineIndex, line ->
                    canvas.drawText(line, x + 4f, tableTop + 12f + lineIndex * 11f, headerPaint)
                }
                x += columnWidths[index]
                canvas.drawLine(x, tableTop, x, tableTop + headerHeight, gridPaint)
            }
            canvas.drawLine(left, tableTop + headerHeight, left + tableWidth, tableTop + headerHeight, gridPaint)
            y = tableTop + headerHeight
        }

        fun startNextPage() {
            pdfDocument.finishPage(page)
            page = pdfDocument.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, ++pageNumber).create()
            )
            drawPageHeading()
        }

        fun fitText(text: String, maxWidth: Float): String {
            if (cellPaint.measureText(text) <= maxWidth) return text
            var result = text
            while (result.isNotEmpty() && cellPaint.measureText("$result…") > maxWidth) {
                result = result.dropLast(1)
            }
            return "$result…"
        }

        fun wrapText(text: String, maxWidth: Float): List<String> {
            if (text.isEmpty()) return emptyList()
            val lines = mutableListOf<String>()
            var remaining = text
            while (remaining.isNotEmpty()) {
                var end = remaining.length
                while (end > 1 && cellPaint.measureText(remaining.substring(0, end)) > maxWidth) {
                    end--
                }
                lines += remaining.substring(0, end)
                remaining = remaining.substring(end)
            }
            return lines
        }

        drawPageHeading()
        items.forEach { item ->
            val itemLogs = logs.filter { it.itemId == item.inventoryNumber }
            val lent = itemLogs.filter { it.action == ActionType.LEND }.sumOf { it.amount }
            val returned = itemLogs.filter { it.action == ActionType.RETURN }.sumOf { it.amount }
            val lostWhileLent = itemLogs.filter { it.action == ActionType.LOST_WHILE_LENT }.sumOf { it.amount }
            val borrowed = maxOf(0, lent - returned - lostWhileLent)
            val missing = maxOf(
                0,
                itemLogs.filter { it.action == ActionType.MISSING || it.action == ActionType.LOST_WHILE_LENT }
                    .sumOf { it.amount } -
                    itemLogs.filter { it.action == ActionType.FOUND }.sumOf { it.amount }
            )

            val serialLines = item.serialNumbers
                .lines()
                .filter { it.isNotBlank() }
                .flatMap { wrapText(it.trim(), columnWidths[2] - 8f) }
            val cells = listOf(
                item.inventoryNumber,
                listOf(item.name, item.model).filter { it.isNotBlank() }.joinToString(" "),
                "",
                item.stock.toString(),
                borrowed.toString(),
                missing.toString()
            )

            var serialLineIndex = 0
            var firstSegment = true
            do {
                if (y + 24f > pageHeight - 40f) startNextPage()
                val availableLines = ((pageHeight - 40f - y - 8f) / 11f).toInt().coerceAtLeast(1)
                val segmentLineCount = if (serialLines.isEmpty()) 0 else minOf(
                    availableLines,
                    serialLines.size - serialLineIndex
                )
                val rowHeight = maxOf(24f, segmentLineCount * 11f + 8f)
                val canvas = page.canvas
                canvas.drawRect(left, y, left + tableWidth, y + rowHeight, gridPaint)
                var x = left
                cells.forEachIndexed { index, value ->
                    if (firstSegment && index != 2) {
                        canvas.drawText(fitText(value, columnWidths[index] - 8f), x + 4f, y + 16f, cellPaint)
                    }
                    if (index == 2) {
                        repeat(segmentLineCount) { lineIndex ->
                            val serial = serialLines[serialLineIndex + lineIndex]
                            canvas.drawText(serial, x + 4f, y + 14f + lineIndex * 11f, cellPaint)
                        }
                    }
                    x += columnWidths[index]
                    canvas.drawLine(x, y, x, y + rowHeight, gridPaint)
                }
                canvas.drawLine(left, y + rowHeight, left + tableWidth, y + rowHeight, gridPaint)
                y += rowHeight
                serialLineIndex += segmentLineCount
                firstSegment = false
                if (serialLineIndex < serialLines.size) startNextPage()
            } while (serialLineIndex < serialLines.size)
        }

        pdfDocument.finishPage(page)

        val documentsDir = File(context.getExternalFilesDir(null), "Documents")
        if (!documentsDir.exists()) documentsDir.mkdirs()
        val sanitizedName = warehouse.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(documentsDir, "${sanitizedName}_export.pdf")
        
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        shareFile(context, file, "application/pdf")
    }

    fun exportWarehouseCsv(context: Context, warehouse: Warehouse, items: List<Item>, logs: List<LogEntry>) {
        val csvBuilder = StringBuilder().apply {
            // Header (Warehouse items only, user data excluded)
            append("Inventarnummer;Name;Modell;Merkmale;Seriennummern;Vorhanden;Verwendet;Verschwunden\n")
            
            items.forEach { item ->
                val itemLogs = logs.filter { it.itemId == item.inventoryNumber }
                val lent = itemLogs.filter { it.action == ActionType.LEND }.sumOf { it.amount }
                val returned = itemLogs.filter { it.action == ActionType.RETURN }.sumOf { it.amount }
                val lostWhileLent = itemLogs.filter { it.action == ActionType.LOST_WHILE_LENT }.sumOf { it.amount }
                val borrowed = maxOf(0, lent - returned - lostWhileLent)
                val missing = maxOf(
                    0,
                    itemLogs.filter { it.action == ActionType.MISSING || it.action == ActionType.LOST_WHILE_LENT }
                        .sumOf { it.amount } -
                        itemLogs.filter { it.action == ActionType.FOUND }.sumOf { it.amount }
                )

                append("\"${item.inventoryNumber}\";")
                append("\"${item.name}\";")
                append("\"${item.model}\";")
                append("\"${item.attributes}\";")
                append("\"${item.serialNumbers.replace("\"", "\"\"").replace("\n", ", ")}\";")
                append("${item.stock};")
                append("$borrowed;")
                append("$missing\n")
            }
        }

        val documentsDir = File(context.getExternalFilesDir(null), "Documents")
        if (!documentsDir.exists()) documentsDir.mkdirs()
        val sanitizedName = warehouse.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(documentsDir, "${sanitizedName}_export.csv")

        file.writeText(csvBuilder.toString(), Charsets.UTF_8)

        shareFile(context, file, "text/csv")
    }

    private fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Lager exportieren"))
    }
}
