package ai.researchmate.mobile

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Offline scientific-document PDF renderer.
 * All content is read as structured JSON; no AI-generated code is executed.
 */
object ScientificDocumentExport {
    fun writePdf(resolver: ContentResolver, uri: Uri, rawPackage: String) {
        val report = ScientificDocumentPackage.validate(rawPackage)
        require(report.valid) { report.errors.joinToString("\n") }

        val root = JSONObject(rawPackage)
        val documentMeta = root.optJSONObject("document") ?: JSONObject()
        val manuscript = root.optJSONObject("manuscript") ?: JSONObject()
        val pdf = PdfDocument()
        val flow = PdfFlow(pdf)

        try {
            flow.startPage()
            flow.addText(documentMeta.optString("title", "گزارش پژوهشی"), 22f, true, Color.rgb(24, 39, 64), 10f, Layout.Alignment.ALIGN_CENTER)
            val subtitle = documentMeta.optString("subtitle")
            if (subtitle.isNotBlank()) flow.addText(subtitle, 11f, false, Color.DKGRAY, 14f, Layout.Alignment.ALIGN_CENTER)
            flow.addRule()

            val abstractText = manuscript.optString("abstract")
            if (abstractText.isNotBlank()) {
                flow.addHeading("چکیده", 1)
                flow.addText(abstractText, 10.5f, false, Color.rgb(35, 39, 48), 8f)
            }
            val keywords = manuscript.optJSONArray("keywords") ?: JSONArray()
            if (keywords.length() > 0) {
                flow.addText("کلیدواژه‌ها: " + (0 until keywords.length()).joinToString("، ") { keywords.optString(it) },
                    9f, true, Color.rgb(72, 82, 101), 12f)
            }

            val sections = manuscript.optJSONArray("sections") ?: JSONArray()
            for (i in 0 until sections.length()) {
                val section = sections.optJSONObject(i) ?: continue
                flow.addHeading(section.optString("heading"), section.optInt("level", 1))
                flow.addMarkdownText(section.optString("content"))
            }

            val tables = root.optJSONArray("tables") ?: JSONArray()
            for (i in 0 until tables.length()) {
                val table = tables.optJSONObject(i) ?: continue
                flow.addTable(table, i + 1)
            }

            val charts = root.optJSONArray("charts") ?: JSONArray()
            for (i in 0 until charts.length()) {
                val chart = charts.optJSONObject(i) ?: continue
                flow.addChart(chart, i + 1)
            }

            val figures = root.optJSONArray("figures") ?: JSONArray()
            for (i in 0 until figures.length()) {
                val figure = figures.optJSONObject(i) ?: continue
                flow.addFigure(resolver, figure, i + 1)
            }

            val equations = root.optJSONArray("equations") ?: JSONArray()
            if (equations.length() > 0) flow.addHeading("معادلات", 1)
            for (i in 0 until equations.length()) {
                val equation = equations.optJSONObject(i) ?: continue
                val label = equation.optString("label").ifBlank { "معادله " + (i + 1) }
                val formula = equation.optString("plain_text").ifBlank { equation.optString("latex") }
                flow.addFormula(label, formula)
            }

            val references = root.optJSONArray("references") ?: JSONArray()
            if (references.length() > 0) {
                flow.addHeading("منابع", 1)
                for (i in 0 until references.length()) {
                    val item = references.opt(i)
                    val text = if (item is JSONObject) item.optString("text") else item.toString()
                    flow.addText((i + 1).toString() + ". " + text, 9f, false, Color.rgb(45, 48, 55), 5f)
                }
            }

            flow.finish()
            resolver.openOutputStream(uri)?.use { pdf.writeTo(it) }
                ?: error("امکان نوشتن فایل PDF وجود ندارد.")
        } finally {
            pdf.close()
        }
    }

    fun writeDocx(resolver: ContentResolver, uri: Uri, rawPackage: String) {
        val title = ScientificDocumentPackage.title(rawPackage)
        ResearchDocumentExport.writeDocx(resolver, uri, title, ScientificDocumentPackage.toMarkdown(rawPackage))
    }

    private class PdfFlow(private val document: PdfDocument) {
        private val pageWidth = 595
        private val pageHeight = 842
        private val margin = 46f
        private val contentWidth = pageWidth - margin * 2
        private val bottom = pageHeight - 48f
        private val textColor = Color.rgb(35, 39, 48)
        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = 0f

        fun startPage() {
            page?.let { finishCurrentPage() }
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = document.startPage(info)
            canvas = page!!.canvas
            canvas!!.drawColor(Color.WHITE)
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(102, 111, 127)
                textSize = 8f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            canvas!!.drawText("ResearchMate AI  |  Scientific Document", margin, 25f, headerPaint)
            y = 48f
        }

        fun finish() {
            if (page == null) startPage()
            finishCurrentPage()
        }

        private fun finishCurrentPage() {
            val currentCanvas = canvas ?: return
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GRAY
                textSize = 8f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            val label = "ResearchMate AI  •  $pageNumber"
            val labelWidth = footerPaint.measureText(label)
            currentCanvas.drawLine(margin, pageHeight - 34f, pageWidth - margin, pageHeight - 34f, Paint().apply {
                color = Color.rgb(220, 224, 230)
                strokeWidth = 0.6f
            })
            currentCanvas.drawText(label, pageWidth - margin - labelWidth, pageHeight - 18f, footerPaint)
            page?.let { document.finishPage(it) }
            page = null
            canvas = null
        }

        fun addRule() {
            ensureSpace(14f)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(190, 198, 210); strokeWidth = 1f }
            canvas?.drawLine(margin, y, pageWidth - margin, y, p)
            y += 14f
        }

        fun addHeading(text: String, level: Int) {
            val size = when (level.coerceIn(1, 3)) { 1 -> 15f; 2 -> 12.5f; else -> 11f }
            ensureSpace(size * 2.8f)
            if (level <= 1) {
                val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(111, 82, 184); strokeWidth = 2f }
                canvas?.drawLine(margin, y + 2f, margin + 25f, y + 2f, line)
                y += 7f
            }
            addText(text, size, true, Color.rgb(33, 48, 74), 6f)
        }

        fun addMarkdownText(raw: String) {
            val normalized = raw.replace("\r\n", "\n")
            val parts = normalized.split(Regex("\n{2,}"))
            for (part in parts) {
                val clean = part.trim()
                    .replace(Regex("^\s*[-*]\s+", RegexOption.MULTILINE), "• ")
                    .replace(Regex("\*\*(.*?)\*\*"), "$1")
                    .replace(Regex("(?m)^>\s?"), "  ")
                if (clean.isNotBlank()) addText(clean, 10.2f, false, textColor, 8f)
            }
        }

        fun addText(
            text: String,
            size: Float,
            bold: Boolean,
            color: Int,
            gap: Float = 5f,
            alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
        ) {
            val safe = text.trim()
            if (safe.isBlank()) return
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textSize = size
                typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            }
            val layout = StaticLayout.Builder.obtain(safe, 0, safe.length, paint, contentWidth.toInt())
                .setAlignment(alignment)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setLineSpacing(2f, 1.13f)
                .setIncludePad(true)
                .build()
            drawLayoutAcrossPages(layout)
            y += gap
        }

        private fun drawLayoutAcrossPages(layout: StaticLayout) {
            var offset = 0
            while (offset < layout.height) {
                if (page == null) startPage()
                val space = (bottom - y).toInt()
                if (space < 24) {
                    startPage()
                    continue
                }
                val chunk = min(layout.height - offset, space)
                val c = canvas ?: break
                c.save()
                c.clipRect(margin, y, pageWidth - margin, bottom)
                c.translate(margin, y - offset)
                layout.draw(c)
                c.restore()
                y += chunk
                offset += chunk
                if (offset < layout.height) startPage()
            }
        }

        private fun ensureSpace(height: Float) {
            if (page == null || y + height > bottom) startPage()
        }

        fun addTable(table: JSONObject, index: Int) {
            val title = table.optString("title").ifBlank { "جدول " + index }
            addText("جدول " + index + ". " + title, 10.5f, true, Color.rgb(36, 54, 81), 5f)
            val headers = table.optJSONArray("headers") ?: JSONArray()
            val rows = table.optJSONArray("rows") ?: JSONArray()
            val columns = headers.length().coerceAtLeast(1)
            val cellWidth = contentWidth / columns
            val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor
                textSize = 8.3f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(26, 43, 68)
                textSize = 8.3f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }
            drawTableRow((0 until columns).map { headers.optString(it) }, cellWidth, headerPaint, true)
            for (r in 0 until rows.length()) {
                val row = rows.optJSONArray(r) ?: JSONArray()
                drawTableRow((0 until columns).map { row.optString(it) }, cellWidth, cellPaint, false)
            }
            val source = table.optString("source")
            if (source.isNotBlank()) addText("منبع جدول: " + source, 8f, false, Color.GRAY, 10f)
            else y += 10f
        }

        private fun drawTableRow(values: List<String>, cellWidth: Float, paint: TextPaint, header: Boolean) {
            val layouts = values.map { value ->
                val text = value.ifBlank { "—" }
                StaticLayout.Builder.obtain(text, 0, text.length, paint, max(30, cellWidth.toInt() - 12))
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setTextDirection(TextDirectionHeuristics.RTL)
                    .setIncludePad(true)
                    .build()
            }
            val rowHeight = max(28f, (layouts.maxOfOrNull { it.height } ?: 16).toFloat() + 12f)
            ensureSpace(rowHeight + 2f)
            val c = canvas ?: return
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (header) Color.rgb(231, 235, 243) else Color.WHITE
                style = Paint.Style.FILL
            }
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(196, 203, 213)
                style = Paint.Style.STROKE
                strokeWidth = 0.6f
            }
            for (col in values.indices) {
                val left = margin + col * cellWidth
                val rect = RectF(left, y, left + cellWidth, y + rowHeight)
                c.drawRect(rect, bg)
                c.drawRect(rect, border)
                c.save()
                c.clipRect(rect.left + 4f, rect.top + 3f, rect.right - 4f, rect.bottom - 3f)
                c.translate(left + 6f, y + (rowHeight - layouts[col].height) / 2f)
                layouts[col].draw(c)
                c.restore()
            }
            y += rowHeight
        }

        fun addChart(chart: JSONObject, index: Int) {
            addText("نمودار " + index + ". " + chart.optString("title", "نمودار"), 10.5f, true, Color.rgb(36, 54, 81), 4f)
            val categories = chart.optJSONArray("categories") ?: JSONArray()
            val series = chart.optJSONArray("series") ?: JSONArray()
            val values = mutableListOf<List<Double>>()
            for (s in 0 until series.length()) {
                val item = series.optJSONObject(s) ?: JSONObject()
                val arr = item.optJSONArray("values") ?: JSONArray()
                values += (0 until arr.length()).map { arr.optDouble(it, 0.0) }
            }
            val flat = values.flatten()
            val minValue = min(0.0, flat.minOrNull() ?: 0.0)
            val maxValue = max(0.0, flat.maxOrNull() ?: 1.0)
            val range = if (maxValue == minValue) 1.0 else maxValue - minValue
            val chartHeight = 218f
            ensureSpace(chartHeight + 18f)
            val c = canvas ?: return
            val left = margin + 42f
            val right = pageWidth - margin - 12f
            val top = y + 12f
            val bottomY = top + 150f
            val plotWidth = right - left
            val plotHeight = bottomY - top
            val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(104, 112, 124)
                strokeWidth = 1f
                textSize = 8f
            }
            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(228, 232, 238)
                strokeWidth = 0.7f
            }
            val palette = intArrayOf(
                Color.rgb(91, 117, 181), Color.rgb(48, 145, 132),
                Color.rgb(204, 132, 61), Color.rgb(151, 101, 174)
            )
            for (step in 0..4) {
                val gy = top + plotHeight * step / 4f
                c.drawLine(left, gy, right, gy, gridPaint)
                val v = maxValue - range * step / 4.0
                c.drawText(String.format(Locale.US, "%.2g", v), margin, gy + 3f, axisPaint)
            }
            c.drawLine(left, top, left, bottomY, axisPaint)
            c.drawLine(left, bottomY, right, bottomY, axisPaint)
            val count = categories.length().coerceAtLeast(1)
            val groupWidth = plotWidth / count
            if (chart.optString("type") == "line") {
                for (s in values.indices) {
                    val color = palette[s % palette.size]
                    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = color
                        strokeWidth = 2.2f
                        style = Paint.Style.STROKE
                    }
                    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
                    val points = values[s]
                    for (i in points.indices) {
                        val x = left + groupWidth * (i + 0.5f)
                        val yy = top + ((maxValue - points[i]) / range).toFloat() * plotHeight
                        if (i > 0) {
                            val px = left + groupWidth * (i - 0.5f)
                            val py = top + ((maxValue - points[i - 1]) / range).toFloat() * plotHeight
                            c.drawLine(px, py, x, yy, line)
                        }
                        c.drawCircle(x, yy, 3.2f, dot)
                    }
                }
            } else {
                val seriesCount = values.size.coerceAtLeast(1)
                val barWidth = (groupWidth * 0.72f / seriesCount).coerceAtLeast(2f)
                val zeroY = top + ((maxValue - 0.0) / range).toFloat() * plotHeight
                for (s in values.indices) {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette[s % palette.size] }
                    for (i in values[s].indices) {
                        val x = left + groupWidth * i + groupWidth * 0.14f + s * barWidth
                        val yy = top + ((maxValue - values[s][i]) / range).toFloat() * plotHeight
                        c.drawRect(x, min(yy, zeroY), x + barWidth * 0.82f, max(yy, zeroY), p)
                    }
                }
            }
            val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 7.5f }
            for (i in 0 until categories.length()) {
                val label = categories.optString(i).take(13)
                val layout = StaticLayout.Builder.obtain(label, 0, label.length, labelPaint, groupWidth.toInt().coerceAtLeast(20))
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setTextDirection(TextDirectionHeuristics.RTL)
                    .setIncludePad(false).build()
                c.save()
                c.translate(left + groupWidth * i, bottomY + 3f)
                layout.draw(c)
                c.restore()
            }
            var legendX = left
            val legendY = bottomY + 42f
            for (s in 0 until series.length()) {
                val item = series.optJSONObject(s) ?: JSONObject()
                val label = item.optString("name").take(22)
                val color = palette[s % palette.size]
                c.drawRect(legendX, legendY - 7f, legendX + 8f, legendY + 1f, Paint().apply { this.color = color })
                axisPaint.color = Color.DKGRAY
                c.drawText(label, legendX + 12f, legendY, axisPaint)
                legendX += 18f + axisPaint.measureText(label)
            }
            y = bottomY + 55f
            val xLabel = chart.optString("x_label")
            val yLabel = chart.optString("y_label")
            if (xLabel.isNotBlank()) addText("محور افقی: " + xLabel, 8f, false, Color.GRAY, 2f)
            if (yLabel.isNotBlank()) addText("محور عمودی: " + yLabel, 8f, false, Color.GRAY, 2f)
            val source = chart.optString("source")
            if (source.isNotBlank()) addText("منبع داده: " + source, 8f, false, Color.GRAY, 9f)
        }

        fun addFigure(resolver: ContentResolver, figure: JSONObject, index: Int) {
            val caption = figure.optString("caption").ifBlank { "شکل " + index }
            addText("شکل " + index + ". " + caption, 10.5f, true, Color.rgb(36, 54, 81), 4f)
            val imageUri = figure.optString("image_uri")
            if (imageUri.isNotBlank()) {
                val bitmap = try {
                    resolver.openInputStream(Uri.parse(imageUri))?.use { BitmapFactory.decodeStream(it) }
                } catch (_: Exception) { null }
                if (bitmap != null) {
                    val ratio = min(contentWidth / bitmap.width.toFloat(), 300f / bitmap.height.toFloat())
                    val w = bitmap.width * ratio
                    val h = bitmap.height * ratio
                    ensureSpace(h + 14f)
                    val left = (pageWidth - w) / 2f
                    canvas?.drawBitmap(bitmap, null, RectF(left, y, left + w, y + h), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    y += h + 10f
                    bitmap.recycle()
                } else {
                    addText("[تصویر پیوست‌شده قابل خواندن نیست]", 9f, true, Color.RED, 4f)
                }
            } else {
                val description = figure.optString("description").ifBlank { "تصویر برای این شکل پیوست نشده است." }
                ensureSpace(76f)
                val rect = RectF(margin, y, pageWidth - margin, y + 62f)
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(244, 246, 249); style = Paint.Style.FILL }
                val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(188, 196, 208); style = Paint.Style.STROKE; strokeWidth = 1f }
                canvas?.drawRoundRect(rect, 8f, 8f, fill)
                canvas?.drawRoundRect(rect, 8f, 8f, border)
                y += 7f
                addText(description, 8.8f, false, Color.rgb(90, 96, 107), 7f)
            }
            val source = figure.optString("source")
            if (source.isNotBlank()) addText("منبع/مجوز: " + source, 8f, false, Color.GRAY, 8f)
        }

        fun addFormula(label: String, formula: String) {
            addText(label, 9.5f, true, Color.rgb(40, 55, 80), 3f)
            val safe = formula.ifBlank { "فرمول متنی ارائه نشده است." }
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(34, 48, 69)
                textSize = 11f
                typeface = Typeface.create("serif", Typeface.NORMAL)
            }
            val layout = StaticLayout.Builder.obtain(safe, 0, safe.length, paint, contentWidth.toInt() - 20)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setIncludePad(true)
                .build()
            ensureSpace(layout.height + 16f)
            val rect = RectF(margin, y, pageWidth - margin, y + layout.height + 14f)
            canvas?.drawRoundRect(rect, 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(240, 243, 248) })
            canvas?.save()
            canvas?.translate(margin + 10f, y + 7f)
            layout.draw(canvas)
            canvas?.restore()
            y = rect.bottom + 12f
        }
    }
}
