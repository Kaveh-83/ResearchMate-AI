package ai.researchmate.mobile

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun WorkspaceDashboard(projectsCount: Int, papersCount: Int, onSearch: () -> Unit, onLibrary: () -> Unit,
    onCompare: () -> Unit, onIdeas: () -> Unit, onProjects: () -> Unit, onPrompt: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("RESEARCH INTELLIGENCE WORKSPACE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("از جست‌وجو تا شواهد و ایده پژوهشی", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("محیط یکپارچه برای کشف مقاله، سازمان‌دهی دانش و ساخت خروجی علمی.")
                    Button(onClick = onSearch, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("شروع پژوهش جدید")
                    }
                }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile("مقاله‌های من", papersCount.toString(), Icons.Default.LibraryBooks, Modifier.weight(1f))
            MetricTile("پروژه‌ها", projectsCount.toString(), Icons.Default.Folder, Modifier.weight(1f))
        } }
        item { ActionCard(Icons.Default.CollectionsBookmark, "کتابخانه پژوهش", "مقاله‌ها، PDFها، برچسب‌ها، یادداشت‌ها و شواهد.", "بازکردن کتابخانه", onLibrary) }
        item { ActionCard(Icons.Default.CompareArrows, "مقایسه مقاله‌ها", "مشخصات و شواهد چند مقاله را در یک ماتریس ببین.", "شروع مقایسه", onCompare) }
        item { ActionCard(Icons.Default.Lightbulb, "آزمایشگاه ایده", "نشانه‌های کم‌پوشش را در مجموعه مقاله‌های خودت بررسی کن.", "تحلیل مجموعه", onIdeas) }
        item { ActionCard(Icons.Default.EditNote, "استودیوی نگارش علمی", "پرامپت برای مرور، پروپوزال، نقد، گزارش و پوستر.", "بازکردن استودیو", onPrompt) }
        item { ActionCard(Icons.Default.FolderOpen, "مدیریت پروژه‌ها", "پروژه‌ها و مسیرهای پژوهشی ذخیره‌شده روی دستگاه.", "مدیریت پروژه‌ها", onProjects) }
        item { Text("پروژه‌ها، یادداشت‌ها و کتابخانه روی دستگاه نگهداری می‌شوند. جست‌وجوی مقاله به اینترنت نیاز دارد.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun MetricTile(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String, actionLabel: String, action: () -> Unit) {
    Card(Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = action, contentPadding = PaddingValues(0.dp)) { Text(actionLabel) }
            }
            Icon(Icons.Default.ChevronLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ResearchLibraryScreen(papers: List<ResearchPaper>, onOpen: (ResearchPaper) -> Unit, onImportPdf: () -> Unit, onDelete: (ResearchPaper) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = papers.filter {
        val q = query.trim().lowercase(Locale.ROOT)
        q.isBlank() || listOf(it.title, it.authors, it.journal, it.doi, it.abstractText, it.notes, it.tags).any { s -> s.lowercase(Locale.ROOT).contains(q) }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("کتابخانه پژوهش", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(papers.size.toString() + " رکورد ذخیره‌شده", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onImportPdf) { Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(6.dp)); Text("افزودن PDF") }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("جست‌وجو در عنوان، نویسنده، DOI و یادداشت") }, leadingIcon = { Icon(Icons.Default.Search, null) })
        if (papers.isEmpty()) EmptyState(Icons.Default.LibraryBooks, "کتابخانه هنوز خالی است", "از جست‌وجو مقاله ذخیره کن یا PDF وارد کن.")
        else if (filtered.isEmpty()) EmptyState(Icons.Default.SearchOff, "موردی پیدا نشد", "عبارت جست‌وجو را تغییر بده.")
        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
            items(filtered, key = { it.id }) { paper ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(paper.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(paper.authors.ifBlank { "نویسندگان ثبت نشده‌اند" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(listOfNotNull(paper.year?.toString(), paper.journal.takeIf { it.isNotBlank() }).joinToString(" · "), style = MaterialTheme.typography.labelSmall)
                        if (paper.tags.isNotBlank()) Text("برچسب: " + paper.tags, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (paper.pdfUri.isNotBlank()) "PDF متصل" else if (paper.pdfUrl.isNotBlank()) "لینک دسترسی آزاد" else "فراداده",
                                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { onDelete(paper) }) { Icon(Icons.Default.DeleteOutline, null); Text("حذف") }
                            Button(onClick = { onOpen(paper) }) { Text("جزئیات") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PaperDetailScreen(paper: ResearchPaper, evidence: List<EvidenceRecord>, onSave: (String, String) -> Unit,
    onAddEvidence: (EvidenceRecord) -> Unit, onDeleteEvidence: (EvidenceRecord) -> Unit, onPreviewPdf: () -> Unit, onBack: () -> Unit) {
    var notes by remember(paper.id) { mutableStateOf(paper.notes) }
    var tags by remember(paper.id) { mutableStateOf(paper.tags) }
    var variable by rememberSaveable(paper.id) { mutableStateOf("") }
    var value by rememberSaveable(paper.id) { mutableStateOf("") }
    var unit by rememberSaveable(paper.id) { mutableStateOf("") }
    var page by rememberSaveable(paper.id) { mutableStateOf("") }
    var excerpt by rememberSaveable(paper.id) { mutableStateOf("") }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, null); Text("بازگشت") }
            Spacer(Modifier.weight(1f))
            Text("پرونده مقاله", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(paper.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(paper.authors, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(listOfNotNull(paper.year?.toString(), paper.journal, paper.doi).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelSmall)
                        Text("تعداد استناد ثبت‌شده: " + paper.citationCount)
                        Text(paper.abstractText.ifBlank { "چکیده در فراداده موجود نیست." }, style = MaterialTheme.typography.bodyMedium)
                        if (paper.pdfUri.isNotBlank()) Button(onClick = onPreviewPdf, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(7.dp)); Text("پیش‌نمایش PDF")
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("یادداشت و برچسب‌ها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("یادداشت شخصی") }, minLines = 3)
                        OutlinedTextField(tags, { tags = it }, Modifier.fillMaxWidth(), label = { Text("برچسب‌ها (با ویرگول جدا کن)") }, singleLine = true)
                        Button(onClick = { onSave(notes, tags) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Save, null); Spacer(Modifier.width(7.dp)); Text("ذخیره یادداشت")
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ثبت دستی شواهد", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("مقادیر را از متن مقاله وارد کن تا در ماتریس پژوهش قابل استفاده باشند.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(variable, { variable = it }, Modifier.fillMaxWidth(), label = { Text("متغیر (مثلاً دمای عملیات)") }, singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value, { value = it }, Modifier.weight(1f), label = { Text("مقدار") }, singleLine = true)
                            OutlinedTextField(unit, { unit = it }, Modifier.weight(1f), label = { Text("واحد") }, singleLine = true)
                        }
                        OutlinedTextField(page, { page = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), label = { Text("شماره صفحه (اختیاری)") }, singleLine = true)
                        OutlinedTextField(excerpt, { excerpt = it }, Modifier.fillMaxWidth(), label = { Text("عبارت یا توضیح شاهد") }, minLines = 2)
                        Button(onClick = {
                            onAddEvidence(EvidenceRecord(paperId = paper.id, variable = variable.trim(), value = value.trim(),
                                unit = unit.trim(), page = page.toIntOrNull(), excerpt = excerpt.trim()))
                            variable = ""; value = ""; unit = ""; page = ""; excerpt = ""
                        }, enabled = variable.isNotBlank() && value.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("ثبت شاهد")
                        }
                    }
                }
            }
            item { Text("شواهد ثبت‌شده (" + evidence.size + ")", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(evidence, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.variable, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { onDeleteEvidence(item) }) { Icon(Icons.Default.DeleteOutline, null); Text("حذف") }
                        }
                        Text(listOf(item.value, item.unit).filter { it.isNotBlank() }.joinToString(" "))
                        Text(listOfNotNull(item.page?.let { "صفحه " + it }, item.excerpt.takeIf { it.isNotBlank() }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun ComparePapersScreen(papers: List<ResearchPaper>, evidenceFor: (String) -> List<EvidenceRecord>) {
    var selected by remember { mutableStateOf(emptySet<String>()) }
    val chosen = papers.filter { it.id in selected }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("مقایسه مقاله‌ها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("حداکثر چهار مقاله را انتخاب کن. داده ناموجود با «ثبت نشده» مشخص می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (papers.isEmpty()) EmptyState(Icons.Default.CompareArrows, "مقاله‌ای برای مقایسه نیست", "ابتدا مقاله‌ها را در کتابخانه ذخیره کن.")
        else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(papers, key = { it.id }) { p ->
                    Card {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(p.id in selected, {
                                selected = if (it && selected.size < 4) selected + p.id else if (!it) selected - p.id else selected
                            })
                            Column(Modifier.weight(1f)) {
                                Text(p.title, fontWeight = FontWeight.SemiBold)
                                Text(p.year?.toString() ?: "سال ثبت نشده", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            if (chosen.size >= 2) {
                Text("ماتریس مقایسه (" + chosen.size + " مقاله)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    val rows: List<Pair<String, (ResearchPaper) -> String>> = listOf(
                        "سال انتشار" to { it.year?.toString() ?: "ثبت نشده" },
                        "نشریه" to { it.journal.ifBlank { "ثبت نشده" } },
                        "نویسندگان" to { it.authors.ifBlank { "ثبت نشده" } },
                        "استناد" to { it.citationCount.toString() },
                        "DOI" to { it.doi.ifBlank { "ثبت نشده" } },
                        "یادداشت/روش/نتیجه" to { it.notes.ifBlank { "هنوز استخراج نشده" } },
                        "برچسب‌ها" to { it.tags.ifBlank { "ثبت نشده" } }
                    )
                    items(rows) { row ->
                        Card {
                            Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(row.first, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                chosen.forEach { p ->
                                    Text(p.title.take(60) + " — " + row.second(p), style = MaterialTheme.typography.bodySmall)
                                }
                                if (row.first == "یادداشت/روش/نتیجه") chosen.forEach { p ->
                                    val ev = evidenceFor(p.id)
                                    if (ev.isNotEmpty()) Text("شواهد: " + ev.joinToString { it.variable + "=" + it.value + it.unit }, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            } else Text("برای دیدن ماتریس، دست‌کم دو مقاله انتخاب کن.", style = MaterialTheme.typography.labelMedium)
        }
    }
}

data class GapSignal(val term: String, val documentCount: Int)

private fun findGapSignals(papers: List<ResearchPaper>): List<GapSignal> {
    val stopWords = setOf("this", "that", "with", "from", "were", "been", "using", "study", "based", "results",
        "method", "methods", "their", "about", "these", "those", "برای", "است", "های", "در", "از", "به", "با",
        "این", "آن", "یک", "شود", "شده", "مقاله", "پژوهش")
    val counts = mutableMapOf<String, Int>()
    papers.forEach { paper ->
        val terms = Regex("[\\\\p{L}]{4,}").findAll(paper.title + " " + paper.abstractText + " " + paper.notes)
            .map { it.value.lowercase(Locale.ROOT) }.filter { it !in stopWords }.toSet()
        terms.forEach { term -> counts[term] = (counts[term] ?: 0) + 1 }
    }
    return counts.filterValues { it <= 2 }.map { GapSignal(it.key, it.value) }
        .sortedWith(compareBy<GapSignal> { it.documentCount }.thenByDescending { it.term.length }).take(12)
}

@Composable
fun IdeaLabScreen(papers: List<ResearchPaper>, onUsePrompt: (String) -> Unit) {
    var topic by rememberSaveable { mutableStateOf("") }
    val signals = remember(papers) { findGapSignals(papers) }
    val sourcePack = papers.joinToString("\\n\\n---\\n\\n") {
        "عنوان: " + it.title + "\\nسال: " + (it.year?.toString() ?: "نامشخص") + "\\nچکیده: " + it.abstractText +
            "\\nیادداشت پژوهشگر: " + it.notes + "\\nDOI: " + it.doi
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("آزمایشگاه ایده پژوهشی", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("تحلیل محلی و مقدماتی مقاله‌های ذخیره‌شده؛ بدون ارسال اسناد به سرور.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(topic, { topic = it }, Modifier.fillMaxWidth(), label = { Text("حوزه یا سؤال پژوهش") }, minLines = 2)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("سیگنال‌های کم‌تکرار در مجموعه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (papers.isEmpty()) Text("ابتدا چند مقاله در کتابخانه ذخیره کن.")
                else if (signals.isEmpty()) Text("با داده فعلی سیگنال کم‌تکراری پیدا نشد.")
                else signals.forEach { signal ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(signal.term, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        AssistChip(onClick = { topic = if (topic.isBlank()) signal.term else topic + "، " + signal.term },
                            label = { Text(signal.documentCount.toString() + " مقاله") })
                    }
                }
                Text("این سیگنال‌ها فقط بر پایه فراوانی واژه‌ها هستند؛ مترادف‌ها و زمینه علمی را نمی‌فهمند و اثبات شکاف پژوهشی نیستند.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        Button(onClick = {
            val prompt = "# Evidence-grounded research gap exploration\\nموضوع: " + topic +
                "\\nتعداد رکوردهای محلی: " + papers.size + "\\nسیگنال‌های اولیه: " + signals.joinToString { it.term } +
                "\\n\\nمنابع واقعی را جست‌وجو و DOI را بررسی کن. مطالعات را بر اساس مواد، ترکیب، فرایند، ریزساختار، شرایط آزمون، نتایج و محدودیت‌ها مقایسه کن. تناقض‌ها و متغیرهای بررسی‌نشده را جدا کن. برای هر شکاف شواهد موافق و مخالف، اطمینان، امکان‌پذیری، هزینه و ارزش صنعتی را گزارش کن. کم‌تکراری واژه به‌تنهایی شکاف نیست. جامعیت ادبیات تضمین نشده است.\\n\\n" + sourcePack
            onUsePrompt(prompt)
        }, enabled = papers.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(7.dp)); Text("ساخت پرامپت تحلیل عمیق شکاف")
        }
        Text(papers.size.toString() + " مقاله در تحلیل محلی", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ResearchToolsScreen(onBackup: () -> Unit, onRestore: () -> Unit, onCsv: () -> Unit, onBibtex: () -> Unit, onMarkdown: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Text("ابزارها و خروجی‌ها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("فایل‌ها روی دستگاه ساخته می‌شوند و در محل انتخابی تو ذخیره خواهند شد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { ActionCard(Icons.Default.Backup, "پشتیبان کامل", "JSON شامل پروژه‌ها، مقاله‌ها، یادداشت‌ها و شواهد. فایل PDF درون پشتیبان قرار نمی‌گیرد.", "ساخت پشتیبان", onBackup) }
        item { ActionCard(Icons.Default.Restore, "بازیابی پشتیبان", "اطلاعات دارای شناسه یکسان به‌روزرسانی می‌شوند و سایر داده‌ها حفظ می‌شوند.", "انتخاب فایل پشتیبان", onRestore) }
        item { ActionCard(Icons.Default.TableChart, "خروجی CSV", "فراداده و یادداشت‌ها برای Excel یا نرم‌افزارهای آماری.", "ذخیره CSV", onCsv) }
        item { ActionCard(Icons.Default.FormatQuote, "خروجی BibTeX", "مدخل کتاب‌شناختی برای نرم‌افزارهای مدیریت منابع.", "ذخیره BibTeX", onBibtex) }
        item { ActionCard(Icons.Default.Description, "گزارش Markdown", "فهرست منابع، یادداشت‌ها، برچسب‌ها و شواهد ثبت‌شده.", "ساخت گزارش", onMarkdown) }
        item {
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("مرزهای نسخه 0.6", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("تحلیل معنایی کامل، استخراج خودکار متن و جدول از PDF، همگام‌سازی ابری و اجرای مستقیم مدل هوش مصنوعی هنوز فعال نیستند. خروجی‌های آزمایشگاه ایده نیز پیشنهادهای مقدماتی‌اند.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 35.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
