package ai.researchmate.mobile

import org.json.JSONArray
import org.json.JSONObject

data class ScientificPackageReport(
    val valid: Boolean,
    val title: String,
    val sectionCount: Int,
    val tableCount: Int,
    val chartCount: Int,
    val figureCount: Int,
    val referenceCount: Int,
    val missingImageIds: List<String>,
    val errors: List<String>,
    val warnings: List<String>,
)

object ScientificDocumentPackage {
    private const val VERSION = "1.0"

    fun validate(raw: String): ScientificPackageReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var title = ""
        var sections = 0
        var tables = 0
        var charts = 0
        var figures = 0
        var references = 0
        val missingImages = mutableListOf<String>()

        val root = try {
            parseRoot(raw)
        } catch (e: Exception) {
            val position = Regex("character\\s+(\\d+)", RegexOption.IGNORE_CASE)
                .find(e.message.orEmpty())?.groupValues?.getOrNull(1)
            val detail = if (position != null) " (محل تقریبی خطا: نویسه $position)" else ""
            return ScientificPackageReport(false, "", 0, 0, 0, 0, 0, emptyList(),
                listOf("قالب اطلاعات سند قابل خواندن نیست. احتمالاً بخشی از JSON ناقص یا دارای نشانه‌گذاری نادرست است.$detail برای اصلاح، از گزینه «اصلاح خودکار قالب» استفاده کن."), emptyList())
        }

        if (root.optString("schema_version") != VERSION) errors += "schema_version باید برابر 1.0 باشد."

        val document = root.optJSONObject("document")
        if (document == null) errors += "بخش document وجود ندارد."
        else {
            title = document.optString("title").trim()
            if (title.isBlank()) errors += "عنوان سند در document.title خالی است."
        }

        val manuscript = root.optJSONObject("manuscript")
        if (manuscript == null) {
            errors += "بخش manuscript وجود ندارد."
        } else {
            if (manuscript.optString("abstract").isBlank()) warnings += "چکیده ثبت نشده است."
            val sectionArray = manuscript.optJSONArray("sections") ?: JSONArray()
            sections = sectionArray.length()
            if (sections == 0) errors += "حداقل یک بخش در manuscript.sections لازم است."
            for (i in 0 until sectionArray.length()) {
                val section = sectionArray.optJSONObject(i)
                if (section == null) errors += "بخش شماره " + (i + 1) + " ساختار شیء ندارد."
                else {
                    if (section.optString("heading").isBlank()) errors += "عنوان بخش شماره " + (i + 1) + " خالی است."
                    if (section.optString("content").isBlank()) warnings += "متن بخش «" + section.optString("heading") + "» خالی است."
                }
            }
            val keywords = manuscript.optJSONArray("keywords") ?: JSONArray()
            if (keywords.length() == 0) warnings += "کلیدواژه‌ای ثبت نشده است."
        }

        val tableArray = root.optJSONArray("tables") ?: JSONArray()
        tables = tableArray.length()
        for (i in 0 until tableArray.length()) {
            val table = tableArray.optJSONObject(i)
            if (table == null) {
                errors += "جدول شماره " + (i + 1) + " ساختار شیء ندارد."
                continue
            }
            val headers = table.optJSONArray("headers") ?: JSONArray()
            val rows = table.optJSONArray("rows") ?: JSONArray()
            if (headers.length() == 0) errors += "جدول " + (i + 1) + ": حداقل یک سرستون لازم است."
            for (r in 0 until rows.length()) {
                val row = rows.optJSONArray(r)
                if (row == null || row.length() != headers.length()) {
                    errors += "جدول " + (i + 1) + "، ردیف " + (r + 1) + ": تعداد سلول‌ها باید با تعداد سرستون‌ها برابر باشد."
                }
            }
        }

        val chartArray = root.optJSONArray("charts") ?: JSONArray()
        charts = chartArray.length()
        for (i in 0 until chartArray.length()) {
            val chart = chartArray.optJSONObject(i)
            if (chart == null) {
                errors += "نمودار شماره " + (i + 1) + " ساختار شیء ندارد."
                continue
            }
            if (chart.optString("type") !in setOf("bar", "line")) errors += "نمودار " + (i + 1) + ": type باید bar یا line باشد."
            val categories = chart.optJSONArray("categories") ?: JSONArray()
            if (categories.length() < 2) errors += "نمودار " + (i + 1) + ": حداقل دو دسته یا نقطه لازم است."
            val series = chart.optJSONArray("series") ?: JSONArray()
            if (series.length() == 0) errors += "نمودار " + (i + 1) + ": حداقل یک سری داده لازم است."
            for (s in 0 until series.length()) {
                val item = series.optJSONObject(s)
                val values = item?.optJSONArray("values")
                if (values == null || values.length() != categories.length()) {
                    errors += "نمودار " + (i + 1) + ": تعداد مقادیر هر سری باید با دسته‌ها برابر باشد."
                    continue
                }
                for (v in 0 until values.length()) {
                    if (values.isNull(v) || values.optDouble(v, Double.NaN).isNaN()) {
                        errors += "نمودار " + (i + 1) + ": همه مقادیر باید عدد معتبر باشند."
                        break
                    }
                }
            }
            if (chart.optString("source").isBlank()) warnings += "منبع داده نمودار " + (i + 1) + " مشخص نشده است."
        }

        val figureArray = root.optJSONArray("figures") ?: JSONArray()
        figures = figureArray.length()
        val seenFigureIds = mutableSetOf<String>()
        for (i in 0 until figureArray.length()) {
            val figure = figureArray.optJSONObject(i)
            if (figure == null) {
                errors += "شکل شماره " + (i + 1) + " ساختار شیء ندارد."
                continue
            }
            val id = figure.optString("id").trim()
            if (id.isBlank()) errors += "شکل شماره " + (i + 1) + ": شناسه یکتا لازم است."
            else if (!seenFigureIds.add(id)) errors += "شناسه شکل تکراری است: " + id + "."
            if (figure.optString("image_uri").isBlank()) {
                missingImages += id
                warnings += "برای شکل «" + id + "» هنوز تصویر پیوست نشده است؛ در PDF فقط شرح شکل می‌آید."
            }
            if (figure.optString("caption").isBlank()) warnings += "شرح شکل «" + id + "» خالی است."
            if (figure.optString("source").isBlank()) warnings += "منبع یا وضعیت حقوقی شکل «" + id + "» مشخص نیست."
        }

        val referenceArray = root.optJSONArray("references") ?: JSONArray()
        references = referenceArray.length()
        if (references == 0) warnings += "فهرست منابع خالی است؛ پیش از استفاده علمی منابع واقعی را اضافه کن."
        for (i in 0 until referenceArray.length()) {
            val ref = referenceArray.opt(i)
            val text = if (ref is JSONObject) ref.optString("text") else ref.toString()
            if (text.isBlank()) warnings += "مدخل منبع شماره " + (i + 1) + " خالی است."
        }

        if (charts == 0 && tables == 0 && figures == 0) warnings += "سند هیچ جدول، نمودار یا شکلی ندارد."

        return ScientificPackageReport(
            valid = errors.isEmpty(), title = title, sectionCount = sections,
            tableCount = tables, chartCount = charts, figureCount = figures,
            referenceCount = references, missingImageIds = missingImages,
            errors = errors.distinct(), warnings = warnings.distinct()
        )
    }

    private fun parseRoot(raw: String): JSONObject {
        val cleaned = cleanJsonEnvelope(raw)
        try { return JSONObject(cleaned) } catch (_: Exception) { }
        val withoutTrailingCommas = cleaned.replace(Regex(",\\s*([}\\]])"), "$1")
        return JSONObject(withoutTrailingCommas)
    }

    fun repairFormatting(raw: String): String {
        val cleaned = cleanJsonEnvelope(raw)
        val root = try {
            JSONObject(cleaned)
        } catch (_: Exception) {
            JSONObject(cleaned.replace(Regex(",\\s*([}\\]])"), "$1"))
        }
        return root.toString(2)
    }

    private fun cleanJsonEnvelope(raw: String): String {
        var value = raw.trim().removePrefix("\uFEFF")
        value = value.replace(Regex("^\\s*```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*```\\s*$"), "")
            .trim()
        val start = value.indexOf('{')
        val end = value.lastIndexOf('}')
        if (start >= 0 && end > start) value = value.substring(start, end + 1)
        return value
    }

    fun figureIds(raw: String): List<String> = try {
        val array = JSONObject(raw).optJSONArray("figures") ?: JSONArray()
        (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }
        }
    } catch (_: Exception) { emptyList() }

    fun hasImage(raw: String, id: String): Boolean = try {
        val array = JSONObject(raw).optJSONArray("figures") ?: JSONArray()
        (0 until array.length()).any { i ->
            val item = array.optJSONObject(i)
            item?.optString("id") == id && item.optString("image_uri").isNotBlank()
        }
    } catch (_: Exception) { false }

    fun attachImage(raw: String, id: String, uri: String): String {
        val root = JSONObject(raw)
        val figures = root.optJSONArray("figures") ?: JSONArray()
        var found = false
        for (i in 0 until figures.length()) {
            val item = figures.optJSONObject(i) ?: continue
            if (item.optString("id") == id) {
                item.put("image_uri", uri)
                found = true
                break
            }
        }
        require(found) { "شناسه شکل در بسته پیدا نشد." }
        root.put("figures", figures)
        return root.toString(2)
    }

    fun title(raw: String): String = try {
        JSONObject(raw).optJSONObject("document")?.optString("title")?.takeIf { it.isNotBlank() } ?: "گزارش پژوهشی"
    } catch (_: Exception) { "گزارش پژوهشی" }

    fun toMarkdown(raw: String): String {
        val root = JSONObject(raw)
        val document = root.optJSONObject("document") ?: JSONObject()
        val manuscript = root.optJSONObject("manuscript") ?: JSONObject()
        return buildString {
            val subtitle = document.optString("subtitle")
            if (subtitle.isNotBlank()) appendLine(subtitle)
            val abstractText = manuscript.optString("abstract")
            if (abstractText.isNotBlank()) {
                appendLine()
                appendLine("## چکیده")
                appendLine(abstractText)
            }
            val keywords = manuscript.optJSONArray("keywords") ?: JSONArray()
            if (keywords.length() > 0) {
                appendLine()
                appendLine("**کلیدواژه‌ها:** " + (0 until keywords.length()).joinToString("، ") { keywords.optString(it) })
            }
            val sections = manuscript.optJSONArray("sections") ?: JSONArray()
            for (i in 0 until sections.length()) {
                val section = sections.optJSONObject(i) ?: continue
                appendLine()
                appendLine("#".repeat(section.optInt("level", 1).coerceIn(1, 3)) + " " + section.optString("heading"))
                appendLine(section.optString("content"))
            }
            val tables = root.optJSONArray("tables") ?: JSONArray()
            for (i in 0 until tables.length()) {
                val table = tables.optJSONObject(i) ?: continue
                appendLine()
                appendLine("**جدول: " + table.optString("title") + "**")
                val headers = table.optJSONArray("headers") ?: JSONArray()
                appendLine("| " + (0 until headers.length()).joinToString(" | ") { headers.optString(it) } + " |")
                appendLine("| " + (0 until headers.length()).joinToString(" | ") { "---" } + " |")
                val rows = table.optJSONArray("rows") ?: JSONArray()
                for (r in 0 until rows.length()) {
                    val row = rows.optJSONArray(r) ?: JSONArray()
                    appendLine("| " + (0 until row.length()).joinToString(" | ") { row.optString(it) } + " |")
                }
                val source = table.optString("source")
                if (source.isNotBlank()) appendLine("منبع: " + source)
            }
            val charts = root.optJSONArray("charts") ?: JSONArray()
            for (i in 0 until charts.length()) {
                val chart = charts.optJSONObject(i) ?: continue
                appendLine()
                appendLine("**نمودار: " + chart.optString("title") + "**")
                appendLine("نوع: " + chart.optString("type"))
                appendLine("دسته‌ها: " + jsonArrayText(chart.optJSONArray("categories")))
                val series = chart.optJSONArray("series") ?: JSONArray()
                for (s in 0 until series.length()) {
                    val item = series.optJSONObject(s) ?: continue
                    appendLine(item.optString("name") + ": " + jsonArrayText(item.optJSONArray("values")))
                }
                val source = chart.optString("source")
                if (source.isNotBlank()) appendLine("منبع داده: " + source)
            }
            val figures = root.optJSONArray("figures") ?: JSONArray()
            for (i in 0 until figures.length()) {
                val figure = figures.optJSONObject(i) ?: continue
                appendLine()
                appendLine("**شکل " + (i + 1) + ": " + figure.optString("caption") + "**")
                val description = figure.optString("description")
                if (description.isNotBlank()) appendLine(description)
                val source = figure.optString("source")
                if (source.isNotBlank()) appendLine("منبع/مجوز: " + source)
            }
            val equations = root.optJSONArray("equations") ?: JSONArray()
            for (i in 0 until equations.length()) {
                val equation = equations.optJSONObject(i) ?: continue
                appendLine()
                appendLine("**" + equation.optString("label", "معادله") + "**")
                appendLine(equation.optString("plain_text").ifBlank { equation.optString("latex") })
            }
            val refs = root.optJSONArray("references") ?: JSONArray()
            if (refs.length() > 0) {
                appendLine()
                appendLine("## منابع")
                for (i in 0 until refs.length()) {
                    val ref = refs.opt(i)
                    appendLine((i + 1).toString() + ". " + if (ref is JSONObject) ref.optString("text") else ref.toString())
                }
            }
        }
    }

    fun prompt(): String = """
نقش تو: نویسنده و طراح حرفه‌ای اسناد علمی. خروجی را مطابق قرارداد JSON زیر تولید کن تا برنامه ResearchMate AI آن را به PDF صفحه‌آرایی‌شده تبدیل کند.

قوانین حیاتی:
- فقط JSON معتبر و قابل parse برگردان؛ از Markdown code fence و توضیح بیرون JSON استفاده نکن.
- هیچ مقاله، DOI، منبع، داده، نتیجه یا تصویر تجربی را جعل نکن. اطلاعات ناموجود را خالی بگذار یا صریحاً «گزارش نشده» بنویس.
- نمودار فقط از داده عددی واقعی و دارای منبع ساخته شود. داده نمونه/فرضی را به‌عنوان نتیجه علمی نمایش نده.
- اگر شکل یا تصویر واقعی در اختیار نداری، شکل را با description توصیف کن؛ تصویر تجربی، میکروگراف یا نمودار ساختگی نساز.
- در source شکل مشخص کن: منبع و مجوز، یا «تولیدشده با هوش مصنوعی؛ شماتیک/غیرتجربی»، یا «نیازمند تصویر از نویسنده».
- جدول‌ها را با ستون‌ها و ردیف‌های هم‌طول ارائه کن. واحدها را در سرستون یا متن مشخص کن.
- فرمول را در plain_text به صورت قابل خواندن هم بنویس؛ LaTeX صرف به تنهایی کافی نیست.
- متن کامل و ساختاریافته باشد؛ عنوان، چکیده، کلیدواژه، بخش‌های مقاله، جدول‌ها، نمودارها، شکل‌ها، معادلات و منابع را در فیلد مناسب بگذار.
- در references هر منبع را با مشخصات کتاب‌شناختی واقعی و DOI/URL در صورت راستی‌آزمایی قرار بده.
- هر نمودار باید title، type، categories، series، x_label، y_label و source داشته باشد. type فقط bar یا line است.
- برای هر شکل id یکتا، caption، description و source تعیین کن. تصویر را بعداً کاربر در برنامه به آن id متصل می‌کند.
- هیچ کد اجرایی، اسکریپت، HTML یا دستور سیستم‌عامل تولید نکن؛ فقط داده ساختاریافته.

قالب دقیق:
{
  "schema_version": "1.0",
  "document": {"title": "عنوان سند", "subtitle": "زیرعنوان اختیاری", "language": "fa", "citation_style": "APA 7"},
  "manuscript": {"abstract": "چکیده کامل", "keywords": ["کلیدواژه ۱", "کلیدواژه ۲"], "sections": [{"heading": "مقدمه", "level": 1, "content": "متن کامل بخش"}]},
  "tables": [{"id": "table1", "title": "عنوان جدول", "headers": ["ستون ۱", "ستون ۲"], "rows": [["مقدار ۱", "مقدار ۲"]], "source": "منبع واقعی"}],
  "charts": [{"id": "chart1", "title": "عنوان نمودار", "type": "line", "x_label": "محور افقی", "y_label": "محور عمودی و واحد", "categories": ["گروه ۱", "گروه ۲"], "series": [{"name": "سری داده", "values": [1, 2]}], "source": "منبع و محل داده"}],
  "figures": [{"id": "fig1", "caption": "شرح شکل", "description": "توضیح علمی و راهنمای تصویر", "source": "منبع/مجوز یا وضعیت شماتیک"}],
  "equations": [{"label": "معادله ۱", "latex": "E = mc^2", "plain_text": "E برابر m ضربدر c به توان دو"}],
  "references": [{"text": "ارجاع کامل و راستی‌آزمایی‌شده همراه DOI یا URL"}]
}

در JSON نهایی:
- تمام بخش‌های مقاله را متناسب با نوع خروجی و موضوع کامل کن.
- برای داده‌های عددی واحد و شرایط آزمون را ذکر کن.
- اگر داده واقعی برای نمودار وجود ندارد، آرایه charts را خالی بگذار؛ داده فرضی تولید نکن.
- اگر تصویر لازم است اما موجود نیست، فقط شرح شکل را ثبت کن تا کاربر تصویر معتبر را متصل کند.
""".trimIndent()

    private fun jsonArrayText(array: JSONArray?): String =
        if (array == null) "" else (0 until array.length()).joinToString("، ") { array.optString(it) }
}
