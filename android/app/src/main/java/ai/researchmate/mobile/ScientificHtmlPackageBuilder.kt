package ai.researchmate.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Converts the validated structured scientific package into safe, offline HTML fragments. */
object ScientificHtmlPackageBuilder {
    fun build(raw: String): String {
        val root = JSONObject(raw)
        val document = root.optJSONObject("document") ?: JSONObject()
        val manuscript = root.optJSONObject("manuscript") ?: JSONObject()
        return buildString {
            val subtitle = document.optString("subtitle")
            if (subtitle.isNotBlank()) append("<p class=\"subtitle\">").append(esc(subtitle)).append("</p>")
            val abstractText = manuscript.optString("abstract")
            if (abstractText.isNotBlank()) {
                append("<h2>چکیده</h2>").append(textBlock(abstractText))
            }
            val keywords = manuscript.optJSONArray("keywords") ?: JSONArray()
            if (keywords.length() > 0) {
                append("<p class=\"keywords\"><strong>کلیدواژه‌ها:</strong> ")
                append((0 until keywords.length()).joinToString("، ") { esc(keywords.optString(it)) })
                append("</p>")
            }
            val sections = manuscript.optJSONArray("sections") ?: JSONArray()
            for (i in 0 until sections.length()) {
                val section = sections.optJSONObject(i) ?: continue
                val level = section.optInt("level", 1).coerceIn(1, 3)
                append("<h").append(level).append(">").append(esc(section.optString("heading")))
                    .append("</h").append(level).append(">")
                append(textBlock(section.optString("content")))
            }
            val tables = root.optJSONArray("tables") ?: JSONArray()
            for (i in 0 until tables.length()) {
                val table = tables.optJSONObject(i) ?: continue
                val headers = table.optJSONArray("headers") ?: JSONArray()
                val rows = table.optJSONArray("rows") ?: JSONArray()
                append("<figure><figcaption>جدول ").append(i + 1).append(": ")
                    .append(esc(table.optString("title"))).append("</figcaption><table><thead><tr>")
                for (h in 0 until headers.length()) append("<th>").append(esc(headers.optString(h))).append("</th>")
                append("</tr></thead><tbody>")
                for (r in 0 until rows.length()) {
                    val row = rows.optJSONArray(r) ?: JSONArray()
                    append("<tr>")
                    for (col in 0 until headers.length()) append("<td>").append(esc(row.optString(col))).append("</td>")
                    append("</tr>")
                }
                append("</tbody></table>")
                source(this, table.optString("source"))
                append("</figure>")
            }
            val charts = root.optJSONArray("charts") ?: JSONArray()
            for (i in 0 until charts.length()) {
                val chart = charts.optJSONObject(i) ?: continue
                append("<figure><figcaption>نمودار ").append(i + 1).append(": ")
                    .append(esc(chart.optString("title"))).append("</figcaption>")
                append(chartSvg(chart))
                val x = chart.optString("x_label")
                val y = chart.optString("y_label")
                if (x.isNotBlank() || y.isNotBlank()) {
                    append("<p class=\"axis-labels\">")
                    if (x.isNotBlank()) append("محور افقی: ").append(esc(x))
                    if (x.isNotBlank() && y.isNotBlank()) append(" | ")
                    if (y.isNotBlank()) append("محور عمودی: ").append(esc(y))
                    append("</p>")
                }
                source(this, chart.optString("source"))
                append("</figure>")
            }
            val figures = root.optJSONArray("figures") ?: JSONArray()
            for (i in 0 until figures.length()) {
                val figure = figures.optJSONObject(i) ?: continue
                append("<figure><figcaption>شکل ").append(i + 1).append(": ")
                    .append(esc(figure.optString("caption"))).append("</figcaption>")
                val imageUri = figure.optString("image_uri")
                if (imageUri.isNotBlank()) {
                    append("<img class=\"attached-figure\" src=\"").append(esc(imageUri))
                        .append("\" alt=\"").append(esc(figure.optString("caption"))).append("\">")
                } else {
                    append("<div class=\"figure-placeholder\">")
                        .append(esc(figure.optString("description").ifBlank { "تصویر پیوست نشده است." }))
                        .append("</div>")
                }
                source(this, figure.optString("source"))
                append("</figure>")
            }
            val equations = root.optJSONArray("equations") ?: JSONArray()
            if (equations.length() > 0) append("<h2>معادلات</h2>")
            for (i in 0 until equations.length()) {
                val equation = equations.optJSONObject(i) ?: continue
                append("<div class=\"equation\"><strong>")
                    .append(esc(equation.optString("label").ifBlank { "معادله " + (i + 1) }))
                    .append("</strong><div dir=\"auto\">")
                    .append(esc(equation.optString("plain_text").ifBlank { equation.optString("latex") }))
                    .append("</div></div>")
            }
            val refs = root.optJSONArray("references") ?: JSONArray()
            if (refs.length() > 0) {
                append("<h2>منابع</h2><ol class=\"references\">")
                for (i in 0 until refs.length()) {
                    val item = refs.opt(i)
                    append("<li>").append(esc(if (item is JSONObject) item.optString("text") else item.toString()))
                        .append("</li>")
                }
                append("</ol>")
            }
        }
    }

    private fun source(out: StringBuilder, value: String) {
        if (value.isNotBlank()) out.append("<p class=\"source\">منبع: ").append(esc(value)).append("</p>")
    }

    private fun textBlock(raw: String): String = buildString {
        var list: String? = null
        fun closeList() { list?.let { append("</").append(it).append(">") }; list = null }
        raw.replace("\r\n", "\n").lines().forEach { lineRaw ->
            val line = lineRaw.trim()
            if (line.isBlank()) { closeList(); return@forEach }
            val heading = Regex("^(#{1,3})\\s+(.+)$").matchEntire(line)
            if (heading != null) {
                closeList()
                val n = heading.groupValues[1].length + 1
                append("<h").append(n.coerceAtMost(4)).append(">").append(inline(heading.groupValues[2]))
                    .append("</h").append(n.coerceAtMost(4)).append(">")
                return@forEach
            }
            val bullet = Regex("^[-*+]\\s+(.+)$").matchEntire(line)
            val numbered = Regex("^\\d+[.)]\\s+(.+)$").matchEntire(line)
            if (bullet != null || numbered != null) {
                val target = if (numbered != null) "ol" else "ul"
                if (list != target) { closeList(); list = target; append("<").append(target).append(">") }
                append("<li>").append(inline((bullet ?: numbered)!!.groupValues[1])).append("</li>")
                return@forEach
            }
            closeList()
            if (line.startsWith(">")) append("<blockquote>").append(inline(line.drop(1).trim())).append("</blockquote>")
            else append("<p>").append(inline(line)).append("</p>")
        }
        closeList()
    }

    private fun inline(raw: String): String {
        var s = esc(raw)
        s = s.replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
        s = s.replace(Regex("\\*(.+?)\\*"), "<em>$1</em>")
        return s
    }

    private fun chartSvg(chart: JSONObject): String {
        val categories = chart.optJSONArray("categories") ?: JSONArray()
        val series = chart.optJSONArray("series") ?: JSONArray()
        val values = (0 until series.length()).map { s ->
            val arr = series.optJSONObject(s)?.optJSONArray("values") ?: JSONArray()
            (0 until arr.length()).map { arr.optDouble(it, Double.NaN) }
        }
        val flat = values.flatten().filter { it.isFinite() }
        if (categories.length() == 0 || flat.isEmpty()) return "<p>داده کافی برای رسم نمودار وجود ندارد.</p>"
        val minV = min(0.0, flat.minOrNull() ?: 0.0)
        val maxV = max(1.0, flat.maxOrNull() ?: 1.0)
        val range = (maxV - minV).takeIf { it > 0.0 } ?: 1.0
        val left = 70.0; val top = 25.0; val width = 520.0; val height = 245.0
        val group = width / categories.length()
        val colors = listOf("#5876B7", "#329487", "#C98642", "#9866AD")
        return buildString {
            append("<svg class=\"chart-svg\" viewBox=\"0 0 640 350\" role=\"img\" aria-label=\"")
                .append(esc(chart.optString("title"))).append("\">")
            for (step in 0..4) {
                val y = top + height * step / 4.0
                val v = maxV - range * step / 4.0
                append("<line x1=\"").append(left).append("\" y1=\"").append(y)
                    .append("\" x2=\"").append(left + width).append("\" y2=\"").append(y)
                    .append("\" stroke=\"#DDE2EA\"/>")
                append("<text x=\"60\" y=\"").append(y + 4).append("\" text-anchor=\"end\">")
                    .append(String.format(Locale.US, "%.3g", v)).append("</text>")
            }
            append("<line x1=\"").append(left).append("\" y1=\"").append(top)
                .append("\" x2=\"").append(left).append("\" y2=\"").append(top + height).append("\" stroke=\"#687184\"/>")
            append("<line x1=\"").append(left).append("\" y1=\"").append(top + height)
                .append("\" x2=\"").append(left + width).append("\" y2=\"").append(top + height).append("\" stroke=\"#687184\"/>")
            if (chart.optString("type") == "line") {
                values.forEachIndexed { s, points ->
                    val coords = points.mapIndexedNotNull { i, v ->
                        if (i >= categories.length() || !v.isFinite()) null
                        else (left + group * (i + 0.5)) to (top + (maxV - v) / range * height)
                    }
                    if (coords.size > 1) append("<polyline fill=\"none\" stroke=\"").append(colors[s % colors.size])
                        .append("\" stroke-width=\"3\" points=\"")
                        .append(coords.joinToString(" ") { it.first.toString() + "," + it.second.toString() }).append("\"/>")
                    coords.forEach { (x, y) -> append("<circle cx=\"").append(x).append("\" cy=\"").append(y)
                        .append("\" r=\"4\" fill=\"").append(colors[s % colors.size]).append("\"/>") }
                }
            } else {
                values.forEachIndexed { s, points ->
                    points.forEachIndexed { i, v ->
                        if (i < categories.length() && v.isFinite()) {
                            val barW = group * 0.72 / values.size.coerceAtLeast(1)
                            val x = left + group * i + group * 0.14 + s * barW
                            val y = top + (maxV - v) / range * height
                            val zeroY = top + (maxV - 0.0) / range * height
                            append("<rect x=\"").append(x).append("\" y=\"").append(min(y, zeroY))
                                .append("\" width=\"").append(barW * 0.82).append("\" height=\"")
                                .append(abs(zeroY - y)).append("\" fill=\"").append(colors[s % colors.size]).append("\"/>")
                        }
                    }
                }
            }
            for (i in 0 until categories.length()) {
                append("<text x=\"").append(left + group * (i + 0.5)).append("\" y=\"").append(top + height + 24)
                    .append("\" text-anchor=\"middle\">").append(esc(categories.optString(i).take(18))).append("</text>")
            }
            append("</svg><div class=\"legend\">")
            for (s in 0 until series.length()) {
                append("<span><i style=\"background:").append(colors[s % colors.size]).append("\"></i>")
                    .append(esc(series.optJSONObject(s)?.optString("name").orEmpty())).append("</span>")
            }
            append("</div>")
        }
    }

    private fun esc(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
}