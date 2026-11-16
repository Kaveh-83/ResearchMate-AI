package ai.researchmate.mobile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Offline Markdown -> HTML/CSS renderer.
 *
 * Android owns PDF pagination through its public PrintManager API. This avoids
 * calling package-private PrintDocumentAdapter callbacks directly.
 */
object ScientificHtmlPdfRenderer {
    private var activeWebView: WebView? = null
    private var activePrintJob: android.print.PrintJob? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var printPollCount = 0

    fun printLegacyPackage(
        context: Context,
        raw: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val activity = generateSequence(context) { current -> (current as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
        if (activity == null) {
            onComplete(false, "برای چاپ PDF باید برنامه در یک صفحه فعال باز باشد.")
            return
        }

        try {
            val report = ScientificDocumentPackage.validate(raw)
            require(report.valid) { "ساختار سند معتبر نیست؛ محتوای سند را بررسی کن." }
            val title = ScientificDocumentPackage.title(raw).ifBlank { "گزارش پژوهشی" }
            printHtml(activity, title, ScientificHtmlPackageBuilder.build(raw), onComplete)
        } catch (e: Exception) {
            onComplete(false, e.message ?: "محتوای سند قابل خواندن نیست.")
        }
    }

    fun printMarkdown(
        activity: Activity,
        markdown: String,
        title: String,
        onComplete: (Boolean, String?) -> Unit
    ) = printHtml(activity, title, markdownToHtml(markdown), onComplete)

    private fun printHtml(
        activity: Activity,
        title: String,
        bodyHtml: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { printHtml(activity, title, bodyHtml, onComplete) }
            return
        }

        try {
            val web = WebView(activity)
            activeWebView?.let { previous ->
                try { previous.stopLoading(); previous.destroy() } catch (_: Exception) { }
            }
            activeWebView = web

            web.settings.javaScriptEnabled = false
            web.settings.domStorageEnabled = false
            web.settings.allowFileAccess = false
            web.settings.allowContentAccess = true
            web.settings.blockNetworkLoads = true
            web.settings.allowFileAccessFromFileURLs = false
            web.settings.allowUniversalAccessFromFileURLs = false
            web.settings.databaseEnabled = false

            val started = AtomicBoolean(false)
            web.webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame && started.compareAndSet(false, true)) {
                        onComplete(false, "بارگذاری محتوای سند ناموفق بود: " + error.description)
                        releaseWebView()
                    }
                }

                override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                    if (started.compareAndSet(false, true)) onComplete(false, "فرایند نمایش سند متوقف شد؛ دوباره تلاش کن.")
                    releaseWebView()
                    return true
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    if (!started.compareAndSet(false, true)) return
                    try {
                        val manager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
                        val adapter = view.createPrintDocumentAdapter("ResearchMate")
                        val attributes = PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .setResolution(PrintAttributes.Resolution("researchmate", "ResearchMate", 300, 300))
                            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                            .build()
                        activePrintJob = manager.print(
                            title.take(80).ifBlank { "ResearchMate" },
                            adapter,
                            attributes
                        )
                        onComplete(true, null)
                        printPollCount = 0
                        watchPrintJob()
                    } catch (e: Exception) {
                        onComplete(false, e.message ?: "بازکردن پنجره چاپ PDF ناموفق بود.")
                        releaseWebView()
                    }
                }
            }
            web.loadDataWithBaseURL(
                null,
                html(title, bodyHtml),
                "text/html",
                "UTF-8",
                null
            )
        } catch (e: Exception) {
            onComplete(false, e.message ?: "آماده‌سازی سند برای PDF ناموفق بود.")
            releaseWebView()
        }
    }

    private fun watchPrintJob() {
        val job = activePrintJob ?: return
        if (job.isCompleted || job.isCancelled || job.isFailed || ++printPollCount >= 180) {
            releaseWebView()
            return
        }
        mainHandler.postDelayed({ watchPrintJob() }, 1000L)
    }

    private fun releaseWebView() {
        val web = activeWebView
        activeWebView = null
        activePrintJob = null
        try { web?.stopLoading(); web?.destroy() } catch (_: Exception) { }
    }

    private fun html(title: String, bodyHtml: String): String = """<!doctype html><html lang="fa" dir="rtl"><head><meta charset="utf-8">
<style>
@page{size:A4;margin:19mm 17mm 20mm}*{box-sizing:border-box}
body{font-family:sans-serif;color:#202938;font-size:11pt;line-height:1.85;overflow-wrap:anywhere}
h1{font-size:21pt;color:#192b49;border-bottom:2px solid #7962b8;padding-bottom:8px;break-after:avoid}
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
.subtitle{text-align:center;color:#596579;margin-bottom:18px}
.keywords{color:#45536b}
figure{margin:18px 0;break-inside:avoid}
figcaption{font-weight:bold;color:#243a5c;margin:8px 0}
.chart-svg{width:100%;height:auto;max-height:360px;overflow:visible}
.chart-svg text{font-size:12px;fill:#4c5668}
.legend{display:flex;flex-wrap:wrap;gap:14px;justify-content:center;font-size:9pt}
.legend span{display:inline-flex;align-items:center;gap:5px}
.legend i{display:inline-block;width:10px;height:10px;border-radius:2px}
.attached-figure{display:block;max-width:100%;max-height:270mm;object-fit:contain;margin:10px auto}
.figure-placeholder{padding:18px;border:1px dashed #aab3c2;background:#f5f7fa;border-radius:6px}
.equation{padding:12px 16px;margin:12px 0;background:#f1f4f8;border-radius:6px;break-inside:avoid}
.equation div{text-align:center;font-family:serif;font-size:12pt;padding-top:6px}
.source{font-size:8.5pt;color:#667085}
.references{padding-inline-start:26px}
.references li{margin:6px 0;overflow-wrap:anywhere}
</style></head><body><div class="doc-title">""" + escape(title.ifBlank { "گزارش پژوهشی" }) + """</div>""" + bodyHtml + """</body></html>"""

    private fun markdownToHtml(source: String): String {
        val out = StringBuilder()
        val paragraph = mutableListOf<String>()
        val table = mutableListOf<List<String>>()
        var list: String? = null
        fun flushP() {
            if (paragraph.isNotEmpty()) {
                out.append("<p>").append(paragraph.joinToString("<br>") { inline(it) }).append("</p>")
                paragraph.clear()
            }
        }
        fun closeList() { list?.let { out.append("</").append(it).append(">") }; list = null }
        fun flushTable() {
            if (table.isEmpty()) return
            out.append("<table><tbody>")
            table.forEachIndexed { i, row ->
                out.append("<tr>")
                row.forEach { cell ->
                    val tag = if (i == 0) "th" else "td"
                    out.append("<").append(tag).append(">").append(inline(cell.trim())).append("</").append(tag).append(">")
                }
                out.append("</tr>")
            }
            out.append("</tbody></table>")
            table.clear()
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
                out.append("<h").append(n).append(">").append(inline(heading.groupValues[2]))
                    .append("</h").append(n).append(">")
                return@forEach
            }
            if (line == "---" || line == "***") { flushP(); closeList(); out.append("<hr>"); return@forEach }
            if (line.startsWith(">")) {
                flushP(); closeList()
                out.append("<blockquote>").append(inline(line.drop(1).trim())).append("</blockquote>")
                return@forEach
            }
            val bullet = Regex("^[-*+]\\s+(.+)$").matchEntire(line)
            val numbered = Regex("^\\d+[.)]\\s+(.+)$").matchEntire(line)
            if (bullet != null || numbered != null) {
                flushP()
                val target = if (numbered != null) "ol" else "ul"
                if (list != target) { closeList(); list = target; out.append("<").append(target).append(">") }
                out.append("<li>").append(inline((bullet ?: numbered)!!.groupValues[1])).append("</li>")
                return@forEach
            }
            closeList()
            paragraph += line
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