package ai.researchmate.mobile

import android.content.ContentResolver
import android.net.Uri
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Offline document generation. PDF is rendered with Android's bidi text layout;
 * DOCX is a minimal, standards-based WordprocessingML package.
 */
object ResearchDocumentExport {
    fun writePdf(resolver: ContentResolver, uri: Uri, title: String, markdown: String) {
        val document = PdfDocument()
        try {
            val pageWidth = 595
            val pageHeight = 842
            val margin = 46f
            val contentWidth = (pageWidth - margin * 2).toInt()

            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(28, 39, 58)
                textSize = 19f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }
            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(35, 39, 48)
                textSize = 10.5f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.GRAY
                textSize = 8f
            }

            val normalized = markdown
                .replace(Regex("^\\s*[-*]\\s+", RegexOption.MULTILINE), "• ")
                .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
                .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
                .replace(Regex("(?m)^\\s*---+\\s*$"), "────────────────────────────────")
            val titleLayout = StaticLayout.Builder.obtain(title.ifBlank { "گزارش پژوهشی" }, 0,
                title.ifBlank { "گزارش پژوهشی" }.length, titlePaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setIncludePad(true)
                .build()
            val bodyLayout = StaticLayout.Builder.obtain(normalized, 0, normalized.length, bodyPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setLineSpacing(3f, 1.12f)
                .setIncludePad(true)
                .build()

            val top = 44f + titleLayout.height + 22f
            val availableHeight = pageHeight - top.toInt() - 42
            val pageCount = maxOf(1, kotlin.math.ceil(bodyLayout.height.toDouble() / availableHeight).toInt())

            for (index in 0 until pageCount) {
                val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = document.startPage(info)
                val canvas = page.canvas
                canvas.drawColor(android.graphics.Color.WHITE)
                canvas.drawText("ResearchMate AI", margin, 25f, footerPaint)
                if (index == 0) {
                    canvas.save()
                    canvas.translate(margin, 42f)
                    titleLayout.draw(canvas)
                    canvas.restore()
                }
                canvas.save()
                canvas.clipRect(margin, top, pageWidth - margin, pageHeight - 36f)
                canvas.translate(margin, top - index * availableHeight)
                bodyLayout.draw(canvas)
                canvas.restore()
                val pageLabel = "ResearchMate AI  •  ${index + 1} / $pageCount"
                val pageLabelWidth = footerPaint.measureText(pageLabel)
                canvas.drawText(pageLabel, pageWidth - margin - pageLabelWidth, pageHeight - 18f, footerPaint)
                document.finishPage(page)
            }
            resolver.openOutputStream(uri)?.use { document.writeTo(it) }
                ?: error("امکان نوشتن فایل PDF وجود ندارد.")
        } finally {
            document.close()
        }
    }

    fun writeDocx(resolver: ContentResolver, uri: Uri, title: String, markdown: String) {
        val paragraphs = buildList {
            if (title.isNotBlank()) add(paragraphXml(title, "Title"))
            markdown.replace("\r\n", "\n").split("\n").forEach { raw ->
                val line = raw.trimEnd()
                if (line.isBlank()) {
                    add("<w:p/>")
                } else {
                    val heading = Regex("^(#{1,3})\\s+(.*)$").matchEntire(line)
                    when {
                        heading != null -> add(paragraphXml(heading.groupValues[2], "Heading${heading.groupValues[1].length}"))
                        line.startsWith("- ") || line.startsWith("* ") -> add(paragraphXml("• " + line.drop(2), null))
                        line.startsWith("> ") -> add(paragraphXml(line.drop(2), "Quote"))
                        else -> add(paragraphXml(line.replace(Regex("\\*\\*(.*?)\\*\\*"), "$1"), null))
                    }
                }
            }
        }.joinToString("")
        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:body>$paragraphs<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr></w:body></w:document>"""
        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""
        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
        resolver.openOutputStream(uri)?.use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("[Content_Types].xml")); zip.write(contentTypes.toByteArray(Charsets.UTF_8)); zip.closeEntry()
                zip.putNextEntry(ZipEntry("_rels/.rels")); zip.write(rels.toByteArray(Charsets.UTF_8)); zip.closeEntry()
                zip.putNextEntry(ZipEntry("word/document.xml")); zip.write(documentXml.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
        } ?: error("امکان نوشتن فایل Word وجود ندارد.")
    }

    private fun paragraphXml(text: String, style: String?): String {
        val pStyle = style?.let { "<w:pStyle w:val=\"$it\"/>" }.orEmpty()
        val escaped = xmlEscape(text)
        return """<w:p><w:pPr><w:bidi/>$pStyle</w:pPr><w:r><w:rPr><w:rtl/><w:rFonts w:ascii="Arial" w:hAnsi="Arial" w:cs="Arial"/></w:rPr><w:t xml:space="preserve">$escaped</w:t></w:r></w:p>"""
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
}
