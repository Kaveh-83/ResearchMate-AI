package ai.researchmate.mobile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val DOCUMENT_JSON_TEMPLATE = """{
  "schema_version": "1.0",
  "document": {"title": "عنوان سند علمی", "subtitle": "", "language": "fa", "citation_style": "APA 7"},
  "manuscript": {
    "abstract": "چکیده را اینجا قرار بده.",
    "keywords": ["کلیدواژه ۱", "کلیدواژه ۲"],
    "sections": [
      {"heading": "مقدمه", "level": 1, "content": "متن کامل مقدمه را اینجا قرار بده."},
      {"heading": "روش پژوهش", "level": 1, "content": "روش، مواد، متغیرها و شرایط را دقیق توضیح بده."},
      {"heading": "نتایج و بحث", "level": 1, "content": "فقط نتایج مستند و قابل ردیابی را وارد کن."},
      {"heading": "نتیجه‌گیری", "level": 1, "content": "پاسخ مستقیم به سؤال پژوهش."}
    ]
  },
  "tables": [], "charts": [], "figures": [], "equations": [], "references": []
}"""

@Composable
fun ScientificDocumentStudioScreen(
    onExportPdf: (String) -> Unit,
    onExportWord: (String) -> Unit,
    onExportMarkdown: (String, String) -> Unit,
    onExportPackage: (String) -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("scientific_document_studio", Context.MODE_PRIVATE) }
    val initialPackage = remember { preferences.getString("draft_package", DOCUMENT_JSON_TEMPLATE) ?: DOCUMENT_JSON_TEMPLATE }

    var basePackage by remember { mutableStateOf(initialPackage) }
    var title by rememberSaveable { mutableStateOf(ScientificDocumentPackage.editorTitle(initialPackage)) }
    var abstractText by rememberSaveable { mutableStateOf(ScientificDocumentPackage.editorAbstract(initialPackage)) }
    var keywordsText by rememberSaveable { mutableStateOf(ScientificDocumentPackage.editorKeywords(initialPackage)) }
    var markdown by rememberSaveable { mutableStateOf(ScientificDocumentPackage.editorMarkdown(initialPackage)) }
    var advancedJson by remember { mutableStateOf(initialPackage) }
    var advancedOpen by rememberSaveable { mutableStateOf(false) }
    var pendingImageId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val editorPackage = remember(title, abstractText, keywordsText, markdown, basePackage) {
        ScientificDocumentPackage.fromEditor(title, abstractText, keywordsText, markdown, basePackage)
    }
    val report = remember(editorPackage) { ScientificDocumentPackage.validate(editorPackage) }

    LaunchedEffect(editorPackage) {
        delay(450)
        preferences.edit().putString("draft_package", editorPackage).apply()
        basePackage = editorPackage
        advancedJson = editorPackage
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val targetId = pendingImageId
        if (uri != null && targetId != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                basePackage = ScientificDocumentPackage.attachImage(editorPackage, targetId, uri.toString())
                advancedJson = basePackage
                status = "تصویر به شکل " + targetId + " پیوست شد."
            } catch (e: Exception) {
                status = "اتصال تصویر ناموفق بود: " + (e.message ?: "")
            }
        }
        pendingImageId = null
    }

    val packagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val importedRaw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("خواندن فایل JSON ممکن نشد.")
                val importedReport = ScientificDocumentPackage.validate(importedRaw)
                require(importedReport.valid) { importedReport.errors.joinToString("\n") }
                basePackage = importedRaw
                title = ScientificDocumentPackage.editorTitle(importedRaw)
                abstractText = ScientificDocumentPackage.editorAbstract(importedRaw)
                keywordsText = ScientificDocumentPackage.editorKeywords(importedRaw)
                markdown = ScientificDocumentPackage.editorMarkdown(importedRaw)
                advancedJson = importedRaw
                status = "بسته علمی وارد شد و پیش‌نویس فعلی با محتوای آن جایگزین شد."
            } catch (e: Exception) {
                status = "ورود فایل ناموفق بود: " + (e.message ?: "فایل نامعتبر")
            }
        }
    }

    val figures = remember(editorPackage) { ScientificDocumentPackage.figureIds(editorPackage) }
    val missingImageIds = remember(editorPackage) {
        figures.filterNot { ScientificDocumentPackage.hasImage(editorPackage, it) }
    }
    val manuscriptMarkdown = remember(title, abstractText, keywordsText, markdown) {
        buildString {
            append("# ").append(title.trim().ifBlank { "گزارش پژوهشی" }).appendLine().appendLine()
            if (abstractText.isNotBlank()) append("## چکیده").appendLine().appendLine()
                .append(abstractText.trim()).appendLine().appendLine()
            if (keywordsText.isNotBlank()) append("**کلیدواژه‌ها:** ").append(keywordsText.trim()).appendLine().appendLine()
            append(markdown.trim())
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("استودیوی سند علمی", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "متن مقاله را مستقیماً با Markdown بنویس. جدول‌ها، نمودارها، تصاویر، معادلات و منابع به‌صورت داده‌های مستقل نگهداری می‌شوند. پیش‌نویس به‌طور خودکار روی دستگاه ذخیره می‌شود.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("شروع پژوهش با هوش مصنوعی", fontWeight = FontWeight.Bold)
                }
                Text(
                    "پرامپت پژوهش را کپی کن و از مدل بخواه متن مقاله را با تیترهای Markdown و داده‌های ساختاریافته جداگانه تولید کند. منابع و داده‌ها باید واقعی و قابل راستی‌آزمایی باشند.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("ResearchMate scientific prompt", ScientificDocumentPackage.prompt()))
                    Toast.makeText(context, "پرامپت استاندارد کپی شد.", Toast.LENGTH_LONG).show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.ContentCopy, null)
                    Spacer(Modifier.width(7.dp))
                    Text("کپی پرامپت پژوهش")
                }
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("۱. مشخصات و چکیده", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = title, onValueChange = { title = it; status = null },
                    modifier = Modifier.fillMaxWidth(), label = { Text("عنوان مقاله") }, singleLine = false
                )
                OutlinedTextField(
                    value = abstractText, onValueChange = { abstractText = it; status = null },
                    modifier = Modifier.fillMaxWidth(), label = { Text("چکیده") }, minLines = 4
                )
                OutlinedTextField(
                    value = keywordsText, onValueChange = { keywordsText = it; status = null },
                    modifier = Modifier.fillMaxWidth(), label = { Text("کلیدواژه‌ها (با ویرگول جدا کن)") }, minLines = 1
                )
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("۲. متن اصلی مقاله", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    AssistChip(onClick = { status = "پیش‌نویس به‌طور خودکار روی همین دستگاه ذخیره می‌شود." },
                        label = { Text("ذخیره خودکار") }, leadingIcon = { Icon(Icons.Default.CloudDone, null) })
                }
                Text(
                    "تیتر سطح ۱: # عنوان | تیتر سطح ۲: ## عنوان | متن عادی: پاراگراف | فهرست: - مورد | تأکید: **متن**",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = markdown,
                    onValueChange = { markdown = it; status = null },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 16,
                    label = { Text("متن Markdown مقاله") },
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                Text(markdown.length.toString() + " نویسه • " + report.sectionCount + " بخش",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("۳. اجزای علمی ساختاریافته", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "این اجزا از متن مقاله جدا نگهداری می‌شوند؛ بنابراین ویرایش متن اصلی باعث حذف جدول‌ها، نمودارها یا تصاویر نمی‌شود.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SummaryMetric("جدول", report.tableCount)
                    SummaryMetric("نمودار", report.chartCount)
                    SummaryMetric("شکل", report.figureCount)
                    SummaryMetric("معادله", equationCount(editorPackage))
                    SummaryMetric("منبع", report.referenceCount)
                }
                if (figures.isNotEmpty()) {
                    figures.forEach { id ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (ScientificDocumentPackage.hasImage(editorPackage, id)) Icons.Default.CheckCircle else Icons.Default.ImageNotSupported,
                                null,
                                tint = if (ScientificDocumentPackage.hasImage(editorPackage, id)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(id, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text(if (ScientificDocumentPackage.hasImage(editorPackage, id)) "تصویر پیوست" else "بدون تصویر",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val id = missingImageIds.firstOrNull()
                            if (id == null) status = "شکل بدون تصویر وجود ندارد."
                            else { pendingImageId = id; imagePicker.launch(arrayOf("image/png", "image/jpeg", "image/webp")) }
                        },
                        enabled = missingImageIds.isNotEmpty(), modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, null)
                        Spacer(Modifier.width(7.dp))
                        Text("پیوست تصویر برای شکل بعدی")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ورود/ویرایش اجزای پیشرفته", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { advancedOpen = !advancedOpen }) {
                        Text(if (advancedOpen) "بستن" else "باز کردن")
                        Icon(if (advancedOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    }
                }
                if (advancedOpen) {
                    OutlinedButton(
                        onClick = { packagePicker.launch(arrayOf("application/json", "text/json")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, null)
                        Spacer(Modifier.width(7.dp))
                        Text("انتخاب فایل بسته JSON از گوشی")
                    }
                    Text(
                        "برای انتقال یک بسته JSON قدیمی یا ویرایش مستقیم جدول‌ها، نمودارها، معادلات و منابع از این بخش استفاده کن. پس از واردکردن، متن مقاله به ویرایشگر Markdown منتقل می‌شود.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = advancedJson, onValueChange = { advancedJson = it },
                        modifier = Modifier.fillMaxWidth(), minLines = 10,
                        label = { Text("بسته ساختاریافته JSON") }, textStyle = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = {
                        val imported = ScientificDocumentPackage.validate(advancedJson)
                        if (!imported.valid) {
                            status = "ورود انجام نشد؛ JSON نامعتبر است: " + imported.errors.firstOrNull().orEmpty()
                        } else {
                            basePackage = advancedJson
                            title = ScientificDocumentPackage.editorTitle(advancedJson)
                            abstractText = ScientificDocumentPackage.editorAbstract(advancedJson)
                            keywordsText = ScientificDocumentPackage.editorKeywords(advancedJson)
                            markdown = ScientificDocumentPackage.editorMarkdown(advancedJson)
                            status = "بسته وارد شد؛ متن به Markdown منتقل شد و اجزای ساختاریافته حفظ شدند."
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.FileDownload, null)
                        Spacer(Modifier.width(7.dp))
                        Text("اعتبارسنجی و واردکردن بسته")
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("۴. کنترل کیفیت و خروجی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (report.valid) Icons.Default.CheckCircle else Icons.Default.Error,
                        null, tint = if (report.valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(7.dp))
                    Text(if (report.valid) "ساختار سند معتبر است." else "خطاهای ساختاری باید اصلاح شوند.",
                        fontWeight = FontWeight.SemiBold)
                }
                report.errors.forEach { Text("خطا: " + it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                report.warnings.forEach { Text("هشدار: " + it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall) }
                status?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = { onExportPdf(editorPackage) }, enabled = report.valid, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PictureAsPdf, null)
                    Spacer(Modifier.width(7.dp))
                    Text("ساخت PDF صفحه‌آرایی‌شده")
                }
                OutlinedButton(onClick = { onExportWord(editorPackage) }, enabled = report.valid, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Description, null)
                    Spacer(Modifier.width(7.dp))
                    Text("ساخت Word قابل ویرایش")
                }
                OutlinedButton(onClick = { onExportMarkdown(title, manuscriptMarkdown) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Article, null)
                    Spacer(Modifier.width(7.dp))
                    Text("خروجی Markdown مقاله")
                }
                OutlinedButton(onClick = { onExportPackage(editorPackage) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.DataObject, null)
                    Spacer(Modifier.width(7.dp))
                    Text("پشتیبان کامل بسته علمی JSON")
                }
                OutlinedButton(onClick = {
                    basePackage = DOCUMENT_JSON_TEMPLATE
                    title = ScientificDocumentPackage.editorTitle(DOCUMENT_JSON_TEMPLATE)
                    abstractText = ScientificDocumentPackage.editorAbstract(DOCUMENT_JSON_TEMPLATE)
                    keywordsText = ScientificDocumentPackage.editorKeywords(DOCUMENT_JSON_TEMPLATE)
                    markdown = ScientificDocumentPackage.editorMarkdown(DOCUMENT_JSON_TEMPLATE)
                    advancedJson = DOCUMENT_JSON_TEMPLATE
                    status = "قالب نمونه بازیابی شد."
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.RestartAlt, null)
                    Spacer(Modifier.width(7.dp))
                    Text("شروع سند جدید")
                }
            }
        }

        Text("راهنمای کوتاه Markdown", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        GuideCard("headings", "تیترها و ساختار", "از # برای تیتر اصلی و ## یا ### برای زیرعنوان استفاده کن. هر تیتر به یک بخش مستقل در سند تبدیل می‌شود.")
        GuideCard("lists", "فهرست‌ها و تأکید", "برای فهرست از - استفاده کن. برای تأکید از **متن برجسته** و برای تأکید ملایم از *متن مورب* استفاده کن.")
        GuideCard("structured", "جدول، نمودار، شکل و منابع", "اجزای علمی را در بخش ورود پیشرفته JSON وارد کن. مقادیر نمودار باید واقعی و دارای منبع باشند؛ منبع یا DOI را حدس نزن. تصاویر از حافظه گوشی به شناسه شکل متصل می‌شوند.")
        GuideCard("backup", "ذخیره و بازیابی", "متن و اجزای سند به‌طور خودکار در حافظه داخلی برنامه ذخیره می‌شوند. برای نگهداری نسخه خارج از گوشی، خروجی Markdown و Word را نیز ذخیره کن.")
        Spacer(Modifier.height(18.dp))
    }
}

private fun equationCount(raw: String): Int = try {
    val array = org.json.JSONObject(raw).optJSONArray("equations") ?: org.json.JSONArray()
    array.length()
} catch (_: Exception) { 0 }

@Composable
private fun SummaryMetric(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GuideCard(id: String, title: String, body: String) {
    var open by rememberSaveable(id) { mutableStateOf(id == "headings") }
    Card(Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.padding(12.dp)) {
            TextButton(onClick = { open = !open }, modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
            }
            if (open) Text(body, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}