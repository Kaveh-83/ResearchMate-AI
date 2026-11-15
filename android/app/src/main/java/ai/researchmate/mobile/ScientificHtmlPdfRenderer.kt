package ai.researchmate.mobile

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File
import java.io.FileInputStream

/** Offline Markdown-to-HTML/CSS-to-PDF renderer. */
object ScientificHtmlPdfRenderer {
    fun write(context: Context, destination: Uri, markdown: String, title: String, onComplete: (Boolean, String?) -> Unit) {
        val web = WebView(context)
        val temp = File(context.cacheDir, "rm-\${System.nanoTime()}.pdf")
        var done = false
        fun finish(ok: Boolean, error: String? = null) {
            if (done) return
            done = true
            try { web.stopLoading(); web.destroy() } catch (_: Exception) {}
            temp.delete()
            onComplete(ok, error)
        }
        try {
            web.settings.javaScriptEnabled = false
            web.settings.domStorageEnabled = false
            web.settings.allowFileAccess = false
            web.settings.allowContentAccess = true
            web.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    val adapter = view.createPrintDocumentAdapter("ResearchMate")
                    val attrs = PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setResolution(PrintAttributes.Resolution("rm", "ResearchMate", 300, 300))
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS).build()
                    adapter.onLayout(null, attrs, CancellationSignal(), object : PrintDocumentAdapter.LayoutResultCallback() {
                        override fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean) {
                            try {
                                val fd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE)
                                adapter.onWrite(arrayOf(PrintDocumentAdapter.PageRange.ALL_PAGES), fd, CancellationSignal(),
                                    object : PrintDocumentAdapter.WriteResultCallback() {
                                        override fun onWriteFinished(pages: Array<PrintDocumentAdapter.PageRange>) {
                                            try {
                                                FileInputStream(temp).use { input ->
                                                    context.contentResolver.openOutputStream(destination)?.use { input.copyTo(it) }
                                                        ?: error("امکان ذخیره PDF وجود ندارد.")
                                                }
                                                finish(true)
                                            } catch (e: Exception) { finish(false, e.message ?: "ذخیره PDF ناموفق بود.") }
                                        }
                                        override fun onWriteFailed(error: CharSequence?) { finish(false, error?.toString() ?: "تبدیل PDF ناموفق بود.") }
                                        override fun onWriteCancelled() { finish(false, "ساخت PDF لغو شد.") }
                                    })
                            } catch (e: Exception) { finish(false, e.message ?: "آماده‌سازی PDF ناموفق بود.") }
                        }
                        override fun onLayoutFailed(error: CharSequence?) { finish(false, error?.toString() ?: "صفحه‌آرایی ناموفق بود.") }
                        override fun onLayoutCancelled() { finish(false, "صفحه‌آرایی لغو شد.") }
                    }, Bundle())
                }
            }
            web.loadDataWithBaseURL("https://researchmate.local/", html(title, markdown), "text/html", "UTF-8", null)
        } catch (e: Exception) { finish(false, e.message ?: "ساخت PDF ناموفق بود.") }
    }

    /** Compatibility bridge used until the JSON editor is migrated to Markdown. */
    fun writeLegacyPackage(context: Context, destination: Uri, raw: String, onComplete: (Boolean, String?) -> Unit) {
        try {
            val report = ScientificDocumentPackage.validate(raw)
            require(report.valid) { "ساختار سند معتبر نیست؛ محتوای سند را بررسی کن." }
            write(context, destination, ScientificDocumentPackage.toMarkdown(raw), ScientificDocumentPackage.title(raw), onComplete)
        } catch (e: Exception) { onComplete(false, e.message ?: "محتوای سند قابل خواندن نیست.") }
    }

    private fun html(title: String, markdown: String): String = """<!doctype html><html lang="fa" dir="rtl"><head><meta charset="utf-8">
<style>
@page{size:A4;margin:19mm 17mm 20mm}*{box-sizing:border-box}
body{font-family:sans-serif;color:#202938;font-size:11pt;line-height:1.85;overflow-wrap:anywhere}
h1{font-size:21pt;color:#192b49;border-bottom:2px solid #7962b8;padding-bottom:8px}
h2{font-size:16pt;color:#243a5c;margin-top:24px;break-after:avoid}
h3{font-size:13pt;color:#354b6b;margin-top:18px;break-after:avoid}
p{margin:0 0 10px;white-space:pre-wrap}ul,ol{padding-inline-start:25px}
li{margin:3px 0}blockquote{border-right:3px solid #9b8acb;padding:6px 14px;background:#f6f4fb}
table{border-collapse:collapse;width:100%;margin:14px 0;font-size:9pt}
tr{break-inside:avoid}th,td{border:1px solid #cbd2dc;padding:6px 8px;text-align:start;vertical-align:top}
th{background:#edf0f6;color:#1e3454}thead{display:table-header-group}
code{font-family:monospace;background:#f1f3f6;padding:1px 4px}
hr{border:0;border-top:1px solid #d9dee7;margin:20px 0}
.doc-title{font-size:24pt;font-weight:bold;color:#192b49;margin-bottom:22px}
</style></head><body><div class="doc-title">\${escape(title.ifBlank { "گزارش پژوهشی" })}</div>\${markdownToHtml(markdown)}</body></html>"""

    private fun markdownToHtml(source: String): String {
        val out = StringBuilder()
        val paragraph = mutableListOf<String>()
        val table = mutableListOf<List<String>>()
        var list: String? = null
        fun flushP() { if (paragraph.isNotEmpty()) { out.append("<p>").append(paragraph.joinToString("<br>") { inline(it) }).append("</p>"); paragraph.clear() } }
        fun closeList() { list?.let { out.append("</").append(it).append(">") }; list = null }
        fun flushTable() {
            if (table.isEmpty()) return
            out.append("<table><tbody>")
            table.forEachIndexed { i, row ->
                out.append("<tr>")
                row.forEach { cell -> val tag = if (i == 0) "th" else "td"; out.append("<").append(tag).append(">").append(inline(cell.trim())).append("</").append(tag).append(">") }
                out.append("</tr>")
            }
            out.append("</tbody></table>"); table.clear()
        }
        source.replace("\r\n", "\n").lines().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank()) { flushP(); closeList(); flushTable(); return@forEach }
            if (line.startsWith("|") && line.endsWith("|")) {
                flushP(); closeList()
                val cells = line.trim('|').split("|")
                if (!cells.all { it.trim().matches(Regex(":?-{3,}:?")) }) table += cells
                return@forEach
            }
            flushTable()
            val heading = Regex("^(#{1,3})\\s+(.+)$").matchEntire(line)
            if (heading != null) {
                flushP(); closeList()
                val n = heading.groupValues[1].length
                out.append("<h").append(n).append(">").append(inline(heading.groupValues[2])).append("</h").append(n).append(">")
                return@forEach
            }
            if (line == "---" || line == "***") { flushP(); closeList(); out.append("<hr>"); return@forEach }
            if (line.startsWith(">")) { flushP(); closeList(); out.append("<blockquote>").append(inline(line.drop(1).trim())).append("</blockquote>"); return@forEach }
            val bullet = Regex("^[-*+]\\s+(.+)$").matchEntire(line)
            val numbered = Regex("^\\d+[.)]\\s+(.+)$").matchEntire(line)
            if (bullet != null || numbered != null) {
                flushP()
                val target = if (numbered != null) "ol" else "ul"
                if (list != target) { closeList(); list = target; out.append("<").append(target).append(">") }
                out.append("<li>").append(inline((bullet ?: numbered)!!.groupValues[1])).append("</li>")
                return@forEach
            }
            closeList(); paragraph += line
        }
        flushP(); closeList(); flushTable()
        return out.toString()
    }

    private fun inline(raw: String): String {
        var s = escape(raw)
        s = s.replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
        s = s.replace(Regex("\\*(.+?)\\*"), "<em>$1</em>")
        return s
    }
    private fun escape(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
}
