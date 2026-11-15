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

private const val DOCUMENT_JSON_TEMPLATE = """{
  "schema_version": "1.0",
  "document": {
    "title": "عنوان سند علمی",
    "subtitle": "",
    "language": "fa",
    "citation_style": "APA 7"
  },
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
  "tables": [],
  "charts": [],
  "figures": [],
  "equations": [],
  "references": []
}"""

@Composable
fun ScientificDocumentStudioScreen(
    onExportPdf: (String) -> Unit,
    onExportWord: (String) -> Unit
) {
    val context = LocalContext.current
    var raw by rememberSaveable { mutableStateOf(DOCUMENT_JSON_TEMPLATE) }
    var report by remember { mutableStateOf(ScientificDocumentPackage.validate(DOCUMENT_JSON_TEMPLATE)) }
    var pendingImageId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val targetId = pendingImageId
        if (uri != null && targetId != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            try {
                raw = ScientificDocumentPackage.attachImage(raw, targetId, uri.toString())
                report = ScientificDocumentPackage.validate(raw)
                status = "تصویر به شناسه " + targetId + " متصل شد."
            } catch (e: Exception) {
                status = "اتصال تصویر ناموفق بود: " + (e.message ?: "")
            }
        }
        pendingImageId = null
    }

    val figures = remember(raw) { ScientificDocumentPackage.figureIds(raw) }
    val missingImageIds = remember(raw) { figures.filterNot { ScientificDocumentPackage.hasImage(raw, it) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("استودیوی ساخت سند علمی", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "بسته ساختاریافته‌ای را که هوش مصنوعی تولید کرده وارد کن؛ برنامه متن، جدول‌ها، نمودارها و تصاویر پیوست‌شده را به یک PDF چندصفحه‌ای تبدیل می‌کند.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("گام اول: ساخت بسته با هوش مصنوعی", fontWeight = FontWeight.Bold)
                }
                Text(
                    "پرامپت استاندارد ResearchMate را کپی کن، آن را به هوش مصنوعی بده و خروجی JSON را بدون تغییر ساختار در کادر پایین قرار بده.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("ResearchMate scientific package prompt", ScientificDocumentPackage.prompt()))
                    Toast.makeText(context, "پرامپت ساخت بسته علمی کپی شد.", Toast.LENGTH_LONG).show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.ContentCopy, null)
                    Spacer(Modifier.width(7.dp))
                    Text("کپی پرامپت استاندارد برای هوش مصنوعی")
                }
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("گام دوم: ورود بسته JSON", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("کل خروجی JSON را جایگزین متن نمونه کن. کد اجرایی یا اسکریپت را اینجا وارد نکن؛ برنامه فقط داده ساختاریافته را می‌پذیرد.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = raw,
                    onValueChange = { raw = it; report = ScientificDocumentPackage.validate(it); status = null },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 16,
                    label = { Text("بسته علمی JSON") },
                    textStyle = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        raw = DOCUMENT_JSON_TEMPLATE
                        report = ScientificDocumentPackage.validate(raw)
                        status = "قالب نمونه بازیابی شد."
                    }, modifier = Modifier.weight(1f)) { Text("بازنشانی نمونه") }
                    OutlinedButton(onClick = {
                        try {
                            raw = ScientificDocumentPackage.repairFormatting(raw)
                            report = ScientificDocumentPackage.validate(raw)
                            status = if (report.valid) "قالب JSON اصلاح و تأیید شد؛ متن مقاله حفظ شده است."
                                else "قالب‌بندی اصلاح شد، اما چند ایراد محتوایی/ساختاری باقی مانده است."
                        } catch (_: Exception) {
                            report = ScientificDocumentPackage.validate(raw)
                            status = "اصلاح خودکار ممکن نشد؛ متن اصلی حفظ شده است. خروجی JSON را دوباره از هوش مصنوعی دریافت کن."
                        }
                    }, modifier = Modifier.weight(1f)) { Text("اصلاح خودکار قالب") }
                    Button(onClick = {
                        report = ScientificDocumentPackage.validate(raw)
                        status = if (report.valid) "ساختار بسته معتبر است." else "خطاهای ساختاری را برطرف کن."
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.FactCheck, null)
                        Spacer(Modifier.width(5.dp))
                        Text("اعتبارسنجی")
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(7.dp))
                    Text("گام سوم: پیوست تصاویر", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text("برای هر شکل، ابتدا AI باید شناسه‌ای مانند fig1 در آرایه figures بسازد. سپس تصویر PNG/JPG واقعی یا شماتیک مجاز را از گوشی انتخاب کن تا به اولین شناسه بدون تصویر متصل شود.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (figures.isEmpty()) {
                    Text("در بسته فعلی شکلی تعریف نشده است. برای افزودن تصویر، یک شیء در آرایه figures تعریف کن.",
                        style = MaterialTheme.typography.labelMedium)
                } else {
                    figures.forEach { id ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (ScientificDocumentPackage.hasImage(raw, id)) Icons.Default.CheckCircle else Icons.Default.ImageNotSupported,
                                null,
                                tint = if (ScientificDocumentPackage.hasImage(raw, id)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(id, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text(
                                if (ScientificDocumentPackage.hasImage(raw, id)) "تصویر متصل" else "بدون تصویر",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        val id = missingImageIds.firstOrNull()
                        if (id == null) status = "شکل بدون تصویر وجود ندارد."
                        else {
                            pendingImageId = id
                            imagePicker.launch(arrayOf("image/png", "image/jpeg", "image/webp"))
                        }
                    },
                    enabled = missingImageIds.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, null)
                    Spacer(Modifier.width(7.dp))
                    Text("انتخاب تصویر برای شکل بعدی")
                }
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AssignmentTurnedIn, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(7.dp))
                    Text("گام چهارم: کنترل ساختار و خروجی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SummaryMetric("بخش‌ها", report.sectionCount)
                    SummaryMetric("جدول‌ها", report.tableCount)
                    SummaryMetric("نمودارها", report.chartCount)
                    SummaryMetric("شکل‌ها", report.figureCount)
                    SummaryMetric("منابع", report.referenceCount)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (report.valid) Icons.Default.CheckCircle else Icons.Default.Error,
                        null,
                        tint = if (report.valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        if (report.valid) "ساختار اصلی بسته معتبر است." else "برای ساخت PDF ابتدا خطاهای ساختاری را اصلاح کن.",
                        fontWeight = FontWeight.SemiBold
                    )
                }
                report.errors.forEach { Text("خطا: " + it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                report.warnings.forEach { Text("هشدار: " + it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall) }
                status?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = { onExportPdf(raw) }, enabled = report.valid, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PictureAsPdf, null)
                    Spacer(Modifier.width(7.dp))
                    Text("ساخت PDF صفحه‌آرایی‌شده با نمودار و تصویر")
                }
                OutlinedButton(onClick = { onExportWord(raw) }, enabled = report.valid, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Description, null)
                    Spacer(Modifier.width(7.dp))
                    Text("ساخت نسخه Word قابل ویرایش")
                }
                Text(
                    "نکته: Word در این نسخه متن، تیترها و داده‌های جدول/نمودار را به شکل متنی نگه می‌دارد؛ درج واقعی تصاویر و چارت‌های برداری در DOCX در نسخه بعدی تکمیل می‌شود. PDF خروجی اصلی این استودیو است.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text("راهنمای کار با هر بخش", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        GuideCard("start", "۱) شروع کار با هوش مصنوعی",
            "روی «کپی پرامپت استاندارد» بزن. موضوع، نوع مقاله، زبان، بازه منابع و مقالات واقعی را در ادامه همان پرامپت برای مدل مشخص کن. از مدل بخواه فقط JSON نهایی برگرداند. اگر مدل خروجی را با برچسب JSON در ابتدا و انتهای آن ارائه کرد، آن برچسب‌ها را حذف کن.")
        GuideCard("manuscript", "۲) متن مقاله و بخش‌ها",
            "در manuscript.abstract چکیده را قرار بده؛ در keywords کلیدواژه‌ها را بنویس؛ و در sections برای هر بخش یک شیء بساز. heading عنوان، level سطح تیتر از ۱ تا ۳ و content متن کامل است. برای مقاله اصیل معمولاً مقدمه، مواد و روش‌ها، نتایج و بحث و نتیجه‌گیری لازم است.")
        GuideCard("tables", "۳) جدول‌ها",
            "هر جدول باید id، title، headers و rows داشته باشد. تعداد مقدارهای هر ردیف باید دقیقاً برابر تعداد سرستون‌ها باشد. واحد را در عنوان ستون یا متن توضیح بده و source را با منبع واقعی پر کن. داده مفقود را خالی یا «گزارش نشده» ثبت کن.")
        GuideCard("charts", "۴) نمودارها",
            "type فقط bar یا line است. categories برچسب‌های محور افقی و values اعداد متناظر در هر سری هستند. تعداد values باید با categories برابر باشد. x_label و y_label همراه واحد و source الزامی‌اند. نمودار فقط باید بر پایه داده واقعی/مستند ساخته شود؛ داده فرضی را وارد نکن.")
        GuideCard("figures", "۵) تصاویر، میکروگراف‌ها و شکل‌های شماتیک",
            "در figures برای هر شکل id یکتا، caption، description و source بنویس. سپس تصویر واقعی را از گوشی متصل کن. منبع و مجوز تصویر را ثبت کن. تصویر تولیدی AI را «شماتیک/غیرتجربی» بنویس؛ آن را به‌جای میکروگراف یا نتیجه آزمایش جا نزن.")
        GuideCard("equations", "۶) معادلات",
            "در equations فیلد latex برای نمادگذاری و plain_text برای نسخه خوانا قرار بده. PDF فعلی نسخه plain_text را در یک کادر فرمول نمایش می‌دهد؛ نمایش پیشرفته ریاضی و چینش حرفه‌ای LaTeX در نسخه بعدی توسعه می‌یابد.")
        GuideCard("references", "۷) منابع و استنادها",
            "هر منبع در references یک شیء با text کامل است. DOI، نام نویسنده، سال و عنوان را با صفحه ناشر یا پایگاه معتبر کنترل کن. مدل نباید منبع یا DOI را حدس بزند. منابع ناقص را قبل از انتشار تکمیل کن.")
        GuideCard("qa", "۸) اعتبارسنجی و ساخت فایل",
            "ابتدا اعتبارسنجی را اجرا کن و همه خطاهای قرمز را برطرف کن. هشدارها لزوماً مانع ساخت نیستند اما باید بررسی شوند. بعد تصاویر را متصل کن و PDF بساز. فایل را باز کن و ترتیب صفحه‌ها، فونت، نمودارها، شرح شکل‌ها، واحدها و منابع را بررسی کن.")
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun SummaryMetric(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GuideCard(id: String, title: String, body: String) {
    var open by rememberSaveable(id) { mutableStateOf(id == "start") }
    Card(Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.padding(12.dp)) {
            TextButton(onClick = { open = !open }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
            }
            if (open) Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
