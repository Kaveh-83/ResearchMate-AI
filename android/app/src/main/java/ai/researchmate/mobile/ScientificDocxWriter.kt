package ai.researchmate.mobile

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Offline WordprocessingML writer: native Word tables plus embedded PNG figures and charts. */
object ScientificDocxWriter {
    private data class Media(val name: String, val bytes: ByteArray, val relId: String)
    private val media = mutableListOf<Media>()
    private val rels = StringBuilder()
    private var relNumber = 1
    private var drawingNumber = 1

    fun write(resolver: ContentResolver, uri: Uri, raw: String) {
        val report = ScientificDocumentPackage.validate(raw)
        require(report.valid) { report.errors.joinToString("\n") }
        media.clear(); rels.clear(); relNumber = 1; drawingNumber = 1
        val root = JSONObject(raw)
        val doc = root.optJSONObject("document") ?: JSONObject()
        val manuscript = root.optJSONObject("manuscript") ?: JSONObject()
        val body = StringBuilder()
        body.append(paragraph(doc.optString("title").ifBlank { "گزارش پژوهشی" }, "Title", true))
        val subtitle = doc.optString("subtitle")
        if (subtitle.isNotBlank()) body.append(paragraph(subtitle, "Subtitle"))
        val abstractText = manuscript.optString("abstract")
        if (abstractText.isNotBlank()) {
            body.append(paragraph("چکیده", "Heading1"))
            appendMarkdown(body, abstractText)
        }
        val keywords = manuscript.optJSONArray("keywords") ?: JSONArray()
        if (keywords.length() > 0) body.append(paragraph("کلیدواژه‌ها: " + (0 until keywords.length()).joinToString("، ") { keywords.optString(it) }))

        val sections = manuscript.optJSONArray("sections") ?: JSONArray()
        for (i in 0 until sections.length()) {
            val section = sections.optJSONObject(i) ?: continue
            body.append(paragraph(section.optString("heading"), "Heading" + section.optInt("level", 1).coerceIn(1, 3)))
            appendMarkdown(body, section.optString("content"))
        }
        val tables = root.optJSONArray("tables") ?: JSONArray()
        for (i in 0 until tables.length()) {
            val table = tables.optJSONObject(i) ?: continue
            body.append(paragraph("جدول " + (i + 1) + ". " + table.optString("title"), "Caption", true))
            body.append(tableXml(table))
            if (table.optString("source").isNotBlank()) body.append(paragraph("منبع: " + table.optString("source"), "Source"))
        }
        val charts = root.optJSONArray("charts") ?: JSONArray()
        for (i in 0 until charts.length()) {
            val chart = charts.optJSONObject(i) ?: continue
            body.append(paragraph("نمودار " + (i + 1) + ". " + chart.optString("title"), "Caption", true))
            body.append(imageParagraph(addMedia("chart" + (i + 1) + ".png", chartPng(chart))))
            body.append(tableXmlForChart(chart))
            val x = chart.optString("x_label"); val y = chart.optString("y_label")
            if (x.isNotBlank() || y.isNotBlank()) body.append(paragraph("محور افقی: " + x + " | محور عمودی: " + y, "Source"))
            if (chart.optString("source").isNotBlank()) body.append(paragraph("منبع داده: " + chart.optString("source"), "Source"))
        }
        val figures = root.optJSONArray("figures") ?: JSONArray()
        for (i in 0 until figures.length()) {
            val figure = figures.optJSONObject(i) ?: continue
            body.append(paragraph("شکل " + (i + 1) + ". " + figure.optString("caption"), "Caption", true))
            val image = loadImage(resolver, figure.optString("image_uri"))
            if (image != null) body.append(imageParagraph(addMedia("figure" + (i + 1) + ".png", image)))
            else body.append(paragraph("[تصویر پیوست نشده یا قابل خواندن نیست] " + figure.optString("description"), "Quote"))
            if (figure.optString("source").isNotBlank()) body.append(paragraph("منبع/مجوز: " + figure.optString("source"), "Source"))
        }
        val equations = root.optJSONArray("equations") ?: JSONArray()
        if (equations.length() > 0) body.append(paragraph("معادلات", "Heading1"))
        for (i in 0 until equations.length()) {
            val equation = equations.optJSONObject(i) ?: continue
            body.append(paragraph(equation.optString("label").ifBlank { "معادله " + (i + 1) }, "Caption", true))
            body.append(paragraph(equation.optString("plain_text").ifBlank { equation.optString("latex") }, "Equation", false, true))
        }
        val references = root.optJSONArray("references") ?: JSONArray()
        if (references.length() > 0) {
            body.append(paragraph("منابع", "Heading1"))
            for (i in 0 until references.length()) {
                val item = references.opt(i)
                val value = if (item is JSONObject) item.optString("text") else item.toString()
                body.append(paragraph((i + 1).toString() + ". " + value, "Reference"))
            }
        }

        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
<w:body>""" + body.toString() + """<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="567" w:footer="567"/></w:sectPr></w:body></w:document>"""
        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""
        val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
        val documentRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rIdOffice" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""" + rels.toString() + "</Relationships>"
        resolver.openOutputStream(uri)?.use { output ->
            ZipOutputStream(output).use { zip ->
                put(zip, "[Content_Types].xml", contentTypes)
                put(zip, "_rels/.rels", rootRels)
                put(zip, "word/document.xml", documentXml)
                put(zip, "word/_rels/document.xml.rels", documentRels)
                put(zip, "word/styles.xml", stylesXml())
                media.forEach { item ->
                    zip.putNextEntry(ZipEntry("word/media/" + item.name))
                    zip.write(item.bytes); zip.closeEntry()
                }
            }
        } ?: error("امکان نوشتن فایل Word وجود ندارد.")
    }

    private fun appendMarkdown(out: StringBuilder, markdown: String) {
        val lines = markdown.replace("\r\n", "\n").lines()
        var tableRows = mutableListOf<List<String>>()
        fun flushTable() {
            if (tableRows.isEmpty()) return
            val rows = tableRows.filterNot { row -> row.all { it.trim().matches(Regex(":?-{3,}:?")) } }
            if (rows.isNotEmpty()) {
                out.append("""<w:tbl><w:tblPr><w:bidiVisual/><w:tblW w:w="0" w:type="auto"/></w:tblPr>""")
                rows.forEachIndexed { ri, row ->
                    out.append("<w:tr>")
                    row.forEach { cell -> out.append("<w:tc>").append(paragraph(cell.trim(), null, ri == 0)).append("</w:tc>") }
                    out.append("</w:tr>")
                }
                out.append("</w:tbl>")
            }
            tableRows = mutableListOf()
        }
        lines.forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("|") && line.endsWith("|")) tableRows.add(line.trim('|').split("|"))
            else {
                flushTable()
                if (line.isBlank()) return@forEach
                val heading = Regex("^(#{1,3})\\s+(.+)$").matchEntire(line)
                when {
                    heading != null -> out.append(paragraph(heading.groupValues[2], "Heading" + heading.groupValues[1].length))
                    line.startsWith("- ") || line.startsWith("* ") -> out.append(paragraph("• " + line.drop(2)))
                    line.startsWith(">") -> out.append(paragraph(line.drop(1).trim(), "Quote"))
                    else -> out.append(paragraph(line))
                }
            }
        }
        flushTable()
    }

    private fun paragraph(text: String, style: String? = null, bold: Boolean = false, centered: Boolean = false): String {
        val pStyle = if (style == null) "" else "<w:pStyle w:val=\"" + style + "\"/>"
        val align = if (centered) """<w:jc w:val="center"/>""" else ""
        val emphasis = if (bold) "<w:b/><w:bCs/>" else ""
        return """<w:p><w:pPr><w:bidi/>""" + pStyle + align + """</w:pPr><w:r><w:rPr><w:rtl/>""" + emphasis + """<w:rFonts w:ascii="Arial" w:hAnsi="Arial" w:cs="Arial"/></w:rPr><w:t xml:space="preserve">""" + xml(text) + "</w:t></w:r></w:p>"
    }

    private fun tableXml(table: JSONObject): String {
        val headers = table.optJSONArray("headers") ?: JSONArray()
        val rows = table.optJSONArray("rows") ?: JSONArray()
        val columns = headers.length().coerceAtLeast(1)
        return buildString {
            append("""<w:tbl><w:tblPr><w:bidiVisual/><w:tblW w:w="0" w:type="auto"/><w:tblBorders>""")
            listOf("top", "left", "bottom", "right", "insideH", "insideV").forEach {
                append("""<w:""").append(it).append(" w:val=\"single\" w:sz=\"5\" w:color=\"B8C2D0\"/>")
            }
            append("</w:tblBorders></w:tblPr><w:tblGrid>")
            repeat(columns) { append("<w:gridCol w:w=\"").append(9000 / columns).append("\"/>") }
            append("</w:tblGrid><w:tr>")
            for (c in 0 until columns) append(tableCell(headers.optString(c), true))
            append("</w:tr>")
            for (r in 0 until rows.length()) {
                val row = rows.optJSONArray(r) ?: JSONArray()
                append("<w:tr>")
                for (c in 0 until columns) append(tableCell(row.optString(c), false))
                append("</w:tr>")
            }
            append("</w:tbl>")
        }
    }

    private fun tableCell(value: String, header: Boolean): String {
        val shade = if (header) """<w:shd w:fill="E9EDF4"/>""" else ""
        return """<w:tc><w:tcPr><w:tcW w:w="1800" w:type="dxa"/>""" + shade + "</w:tcPr>" + paragraph(value, null, header) + "</w:tc>"
    }

    private fun tableXmlForChart(chart: JSONObject): String {
        val categories = chart.optJSONArray("categories") ?: JSONArray()
        val series = chart.optJSONArray("series") ?: JSONArray()
        return buildString {
            append(paragraph("داده‌های نمودار", "Caption", true))
            append("""<w:tbl><w:tblPr><w:bidiVisual/><w:tblW w:w="0" w:type="auto"/></w:tblPr><w:tr>""")
            append(tableCell("دسته", true))
            for (s in 0 until series.length()) append(tableCell(series.optJSONObject(s)?.optString("name").orEmpty(), true))
            append("</w:tr>")
            for (i in 0 until categories.length()) {
                append("<w:tr>").append(tableCell(categories.optString(i), false))
                for (s in 0 until series.length()) {
                    val values = series.optJSONObject(s)?.optJSONArray("values") ?: JSONArray()
                    append(tableCell(if (i < values.length()) values.optString(i) else "—", false))
                }
                append("</w:tr>")
            }
            append("</w:tbl>")
        }
    }

    private fun addMedia(name: String, bytes: ByteArray): Media {
        val id = "rId" + relNumber++
        rels.append("""<Relationship Id="""").append("\"").append(id)
            .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/")
            .append(name).append("\"/>")
        return Media(name, bytes, id).also { media.add(it) }
    }

    private fun imageParagraph(item: Media): String {
        val id = drawingNumber++
        val width = 5_800_000L
        val height = 3_000_000L
        return """<w:p><w:pPr><w:bidi/><w:jc w:val="center"/></w:pPr><w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx=""" +
            width + """ cy=""" + height + """/><wp:docPr id=""" + id + """ name=""" + xml(item.name) + """/><wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect="1"/></wp:cNvGraphicFramePr><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id=""" +
            id + """ name=""" + xml(item.name) + """/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed=""" + item.relId + """/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx=""" +
            width + """ cy=""" + height + """/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"""
    }

    private fun loadImage(resolver: ContentResolver, text: String): ByteArray? {
        val uri = try { Uri.parse(text) } catch (_: Exception) { return null }
        if (uri.scheme != "content") return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try { resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } } catch (_: Exception) { return null }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 1600)
        val bitmap = try {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        } catch (_: Exception) { null } ?: return null
        return try {
            ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }
        } finally { bitmap.recycle() }
    }

    private fun chartPng(chart: JSONObject): ByteArray {
        val w = 1200; val h = 650
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(30, 45, 68); textSize = 30f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        canvas.drawText(chart.optString("title").take(70), 60f, 48f, titlePaint)
        val categories = chart.optJSONArray("categories") ?: JSONArray()
        val series = chart.optJSONArray("series") ?: JSONArray()
        val values = (0 until series.length()).map { s ->
            val arr = series.optJSONObject(s)?.optJSONArray("values") ?: JSONArray()
            (0 until arr.length()).map { arr.optDouble(it, 0.0) }
        }
        val flat = values.flatten()
        val minV = minOf(0.0, flat.minOrNull() ?: 0.0)
        val maxV = maxOf(1.0, flat.maxOrNull() ?: 1.0)
        val range = (maxV - minV).takeIf { it > 0 } ?: 1.0
        val left = 105f; val right = 1160f; val top = 90f; val bottom = 500f
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(225, 230, 237); strokeWidth = 2f }
        val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.DKGRAY; strokeWidth = 2f; textSize = 22f }
        for (step in 0..5) {
            val y = top + (bottom - top) * step / 5f
            canvas.drawLine(left, y, right, y, grid)
            canvas.drawText(String.format(Locale.US, "%.3g", maxV - range * step / 5.0), 8f, y + 7f, axis)
        }
        canvas.drawLine(left, top, left, bottom, axis); canvas.drawLine(left, bottom, right, bottom, axis)
        val colors = intArrayOf(android.graphics.Color.rgb(78,112,177), android.graphics.Color.rgb(44,145,132), android.graphics.Color.rgb(204,132,61), android.graphics.Color.rgb(151,101,174))
        val count = categories.length().coerceAtLeast(1); val group = (right - left) / count
        if (chart.optString("type") == "line") {
            values.forEachIndexed { s, row ->
                val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors[s % colors.size]; strokeWidth = 6f; style = Paint.Style.STROKE }
                val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors[s % colors.size] }
                var previous: Pair<Float,Float>? = null
                row.forEachIndexed { i,v -> if (i < count) {
                    val point = (left + group * (i + .5f)) to (top + ((maxV-v)/range).toFloat()*(bottom-top))
                    previous?.let { canvas.drawLine(it.first,it.second,point.first,point.second,line) }
                    canvas.drawCircle(point.first,point.second,8f,dot); previous=point
                } }
            }
        } else {
            val barW = group * .72f / values.size.coerceAtLeast(1)
            val zeroY = top + ((maxV - 0.0) / range).toFloat() * (bottom-top)
            values.forEachIndexed { s,row -> row.forEachIndexed { i,v -> if(i<count) {
                val x=left+group*i+group*.14f+s*barW; val y=top+((maxV-v)/range).toFloat()*(bottom-top)
                canvas.drawRect(x,minOf(y,zeroY),x+barW*.82f,maxOf(y,zeroY),Paint().apply{color=colors[s%colors.size]})
            } } }
        }
        val labels = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.DKGRAY; textSize = 20f }
        for(i in 0 until categories.length()) canvas.drawText(categories.optString(i).take(12),left+group*i+4f,bottom+30f,labels)
        var legendX=left
        for(s in 0 until series.length()) {
            canvas.drawRect(legendX,570f,legendX+18f,588f,Paint().apply{color=colors[s%colors.size]})
            val name=series.optJSONObject(s)?.optString("name").orEmpty().take(25)
            canvas.drawText(name,legendX+25f,586f,labels); legendX+=65f+labels.measureText(name)
        }
        return ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG,100,out); bitmap.recycle(); out.toByteArray() }
    }

    private fun stylesXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial" w:cs="Arial"/><w:sz w:val="22"/><w:szCs w:val="22"/></w:rPr></w:rPrDefault></w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:pPr><w:bidi/><w:spacing w:after="140" w:line="300" w:lineRule="auto"/></w:pPr></w:style>
<w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:pPr><w:jc w:val="center"/><w:bidi/><w:spacing w:after="280"/></w:pPr><w:rPr><w:b/><w:sz w:val="34"/><w:color w:val="203858"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Subtitle"><w:name w:val="Subtitle"/><w:basedOn w:val="Normal"/><w:pPr><w:jc w:val="center"/><w:bidi/></w:pPr><w:rPr><w:color w:val="68758A"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:pPr><w:bidi/><w:keepNext/></w:pPr><w:rPr><w:b/><w:sz w:val="30"/><w:color w:val="203858"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/><w:pPr><w:bidi/><w:keepNext/></w:pPr><w:rPr><w:b/><w:sz w:val="26"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading3"><w:name w:val="heading 3"/><w:basedOn w:val="Normal"/><w:pPr><w:bidi/><w:keepNext/></w:pPr><w:rPr><w:b/><w:sz w:val="23"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Caption"><w:name w:val="Caption"/><w:basedOn w:val="Normal"/><w:rPr><w:b/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Source"><w:name w:val="Source"/><w:basedOn w:val="Normal"/><w:rPr><w:color w:val="68758A"/><w:sz w:val="18"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Quote"><w:name w:val="Quote"/><w:basedOn w:val="Normal"/><w:rPr><w:i/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Equation"><w:name w:val="Equation"/><w:basedOn w:val="Normal"/><w:pPr><w:jc w:val="center"/><w:bidi/></w:pPr><w:rPr><w:rFonts w:ascii="Cambria Math" w:hAnsi="Cambria Math"/><w:sz w:val="26"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Reference"><w:name w:val="Reference"/><w:basedOn w:val="Normal"/><w:pPr><w:bidi/><w:ind w:right="360" w:hanging="360"/></w:pPr><w:rPr><w:sz w:val="20"/></w:rPr></w:style>
</w:styles>"""

    private fun put(zip: ZipOutputStream, name: String, value: String) {
        zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray(Charsets.UTF_8)); zip.closeEntry()
    }
    private fun xml(value: String): String = value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
}