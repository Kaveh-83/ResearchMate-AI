package ai.researchmate.mobile

import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val ResearchMateDark = darkColorScheme(
    primary = Color(0xFFB9A5FF),
    onPrimary = Color(0xFF26164F),
    primaryContainer = Color(0xFF30234D),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFF8BD6CD),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF174A45),
    onSecondaryContainer = Color(0xFFB4F2E9),
    tertiary = Color(0xFFFFC27A),
    onTertiary = Color(0xFF442B08),
    background = Color(0xFF0B0D13),
    onBackground = Color(0xFFE7E7F0),
    surface = Color(0xFF11151F),
    onSurface = Color(0xFFE7E7F0),
    surfaceVariant = Color(0xFF232938),
    onSurfaceVariant = Color(0xFFB7BDCC),
    surfaceContainerLowest = Color(0xFF090B10),
    surfaceContainerLow = Color(0xFF121722),
    surfaceContainer = Color(0xFF171D29),
    surfaceContainerHigh = Color(0xFF1D2533),
    surfaceContainerHighest = Color(0xFF293142),
    outline = Color(0xFF424B5D),
    outlineVariant = Color(0xFF30394A),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF5B2528),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = AndroidColor.rgb(11, 13, 19)
        window.navigationBarColor = AndroidColor.rgb(17, 21, 31)
        window.decorView.systemUiVisibility = 0
        setContent {
            MaterialTheme(colorScheme = ResearchMateDark) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        ResearchMateHome()
                    }
                }
            }
        }
    }
}

@Composable
private fun ResearchMateHome() {
    val context = LocalContext.current
    val db = remember { ResearchDatabase.get(context) }
    var page by rememberSaveable { mutableStateOf("home") }
    var previousPage by rememberSaveable { mutableStateOf("library") }
    var topic by rememberSaveable { mutableStateOf("") }
    var papersContext by rememberSaveable { mutableStateOf("") }
    var projectId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPaper by remember { mutableStateOf<ResearchPaper?>(null) }
    var projects by remember { mutableStateOf(ProjectStore.load(context)) }
    var library by remember { mutableStateOf(db.allPapers()) }
    var evidence by remember { mutableStateOf(emptyList<EvidenceRecord>()) }
    var pendingDocumentTitle by remember { mutableStateOf("ResearchMate AI") }
    var pendingDocumentText by remember { mutableStateOf("") }
    var pendingSelectedWorks by remember { mutableStateOf(emptyList<LiteratureWork>()) }

    fun reload() {
        projects = ProjectStore.load(context)
        library = db.allPapers()
        selectedPaper?.let { selectedPaper = db.getPaper(it.id) }
        evidence = selectedPaper?.let { db.evidenceForPaper(it.id) } ?: emptyList()
    }
    fun showPrompt(newTopic: String, newPapers: String) {
        topic = newTopic
        papersContext = newPapers
        projectId = null
        page = "prompt"
    }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            val name = getDocumentName(context, uri).ifBlank { "PDF واردشده" }
            db.upsertPaper(ResearchPaper(title = name.removeSuffix(".pdf"), pdfUri = uri.toString(), tags = "PDF واردشده"))
            reload()
            page = "library"
            Toast.makeText(context, "PDF به کتابخانه اضافه شد.", Toast.LENGTH_SHORT).show()
        }
    }

    val backupExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) writeText(context, uri, db.backupJson())
    }
    val csvExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) writeText(context, uri, ResearchExports.csv(db.allPapers()))
    }
    val bibExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) writeText(context, uri, ResearchExports.bibtex(db.allPapers()))
    }
    val markdownExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) writeText(context, uri, ResearchExports.markdown(db.allPapers(), db.allEvidence()))
    }

    val selectedRisExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-research-info-systems")) { uri ->
        if (uri != null) writeText(context, uri, ResearchExports.risWorks(pendingSelectedWorks))
    }
    val promptPdfExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            try {
                ResearchDocumentExport.writePdf(context.contentResolver, uri, pendingDocumentTitle, pendingDocumentText)
                Toast.makeText(context, "فایل PDF با موفقیت ذخیره شد.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "ساخت PDF ناموفق بود: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }
    val promptWordExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) { uri ->
        if (uri != null) {
            try {
                ResearchDocumentExport.writeDocx(context.contentResolver, uri, pendingDocumentTitle, pendingDocumentText)
                Toast.makeText(context, "فایل Word با موفقیت ذخیره شد.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "ساخت Word ناموفق بود: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }
    val backupImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("خواندن فایل پشتیبان ممکن نشد.")
                val count = db.restoreJson(raw)
                reload()
                Toast.makeText(context, count.toString() + " رکورد بازیابی شد.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "بازیابی ناموفق: " + (e.message ?: "فایل نامعتبر"), Toast.LENGTH_LONG).show()
            }
        }
    }

    val showBottomBar = page !in setOf("detail", "pdf")
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    val tabs = listOf(
                        Triple("home", "خانه", Icons.Default.Home),
                        Triple("search", "جست‌وجو", Icons.Default.Search),
                        Triple("library", "کتابخانه", Icons.Default.LibraryBooks),
                        Triple("compare", "مقایسه", Icons.Default.CompareArrows),
                        Triple("tools", "ابزارها", Icons.Default.Build)
                    )
                    tabs.forEach { item ->
                        NavigationBarItem(
                            selected = page == item.first || (item.first == "library" && page == "ideas"),
                            onClick = { page = item.first; if (item.first == "library") reload() },
                            icon = { Icon(item.third, contentDescription = item.second) },
                            label = { Text(item.second) },
                            alwaysShowLabel = true
                        )
                    }
                }
            }
        }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 15.dp, vertical = 9.dp)) {
            if (page !in setOf("prompt", "detail", "pdf")) {
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Science, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Column(Modifier.weight(1f).padding(start = 9.dp)) {
                        Text("ResearchMate AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("دستیار هوشمند پژوهش", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("v" + BuildConfig.VERSION_NAME, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
            AnimatedContent(page, label = "workspace-page", modifier = Modifier.weight(1f)) { target ->
                when (target) {
                    "search" -> AcademicSearchScreen(
                        onPrompt = { t, p -> showPrompt(t, p) },
                        onSave = { work ->
                            db.upsertPaper(work.toResearchPaper())
                            reload()
                            Toast.makeText(context, "مقاله به کتابخانه افزوده شد.", Toast.LENGTH_SHORT).show()
                        },
                        onExportSelected = { works ->
                            pendingSelectedWorks = works
                            selectedRisExport.launch("researchmate-selected-papers.ris")
                        }
                    )
                    "library" -> ResearchLibraryScreen(
                        papers = library,
                        onOpen = { selectedPaper = it; evidence = db.evidenceForPaper(it.id); previousPage = "library"; page = "detail" },
                        onImportPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                        onDelete = { db.deletePaper(it.id); reload() }
                    )
                    "compare" -> ComparePapersScreen(library, evidenceFor = { db.evidenceForPaper(it) })
                    "ideas" -> IdeaLabScreen(library) { generated -> showPrompt("تحلیل شکاف پژوهشی", generated) }
                    "tools" -> ResearchToolsScreen(
                        onBackup = { backupExport.launch("researchmate-backup.json") },
                        onRestore = { backupImport.launch(arrayOf("application/json", "text/json")) },
                        onCsv = { csvExport.launch("researchmate-library.csv") },
                        onBibtex = { bibExport.launch("researchmate-references.bib") },
                        onMarkdown = { markdownExport.launch("researchmate-research-notes.md") }
                    )
                    "projects" -> ProjectScreen(
                        projects,
                        onBack = { page = "home" },
                        open = { selected -> topic = selected.topic; papersContext = selected.paperContext; projectId = selected.id; page = "prompt" },
                        delete = { deleted -> ProjectStore.delete(context, deleted.id); reload() }
                    )
                    "prompt" -> key(topic + papersContext + projectId.orEmpty()) {
                        val old = projects.firstOrNull { it.id == projectId }
                        PromptStudioScreen(
                            old?.topic ?: topic, old?.paperContext ?: papersContext, old?.title.orEmpty(), old?.id,
                            onSaveProject = { ProjectStore.upsert(context, it); reload() },
                            onExportPdf = { documentTitle, documentText ->
                                pendingDocumentTitle = documentTitle
                                pendingDocumentText = documentText
                                promptPdfExport.launch("researchmate-${documentTitle.take(35).replace(Regex("[^\\p{L}\\p{N}]+"), "-")}.pdf")
                            },
                            onExportWord = { documentTitle, documentText ->
                                pendingDocumentTitle = documentTitle
                                pendingDocumentText = documentText
                                promptWordExport.launch("researchmate-${documentTitle.take(35).replace(Regex("[^\\p{L}\\p{N}]+"), "-")}.docx")
                            }
                        )
                    }
                    "detail" -> {
                        val p = selectedPaper
                        if (p == null) EmptyState(Icons.Default.Description, "مقاله انتخاب نشده", "به کتابخانه برگرد.")
                        else PaperDetailScreen(
                            paper = p,
                            evidence = evidence,
                            onSave = { notes, tags -> db.updatePaperNotes(p.id, notes, tags); reload(); Toast.makeText(context, "یادداشت ذخیره شد.", Toast.LENGTH_SHORT).show() },
                            onAddEvidence = { db.addEvidence(it); reload() },
                            onDeleteEvidence = { db.deleteEvidence(it.id); reload() },
                            onPreviewPdf = { page = "pdf" },
                            onBack = { page = previousPage; reload() }
                        )
                    }
                    "pdf" -> {
                        val p = selectedPaper
                        if (p == null) EmptyState(Icons.Default.PictureAsPdf, "PDF در دسترس نیست", "به کتابخانه برگرد.")
                        else PdfPreviewScreen(context, p, onBack = { page = "detail" })
                    }
                    "home" -> WorkspaceDashboard(
                        projectsCount = projects.size,
                        papersCount = library.size,
                        onSearch = { page = "search" },
                        onLibrary = { page = "library"; reload() },
                        onCompare = { page = "compare" },
                        onIdeas = { page = "ideas" },
                        onProjects = { page = "projects"; reload() },
                        onPrompt = { showPrompt("", "") }
                    )
                    else -> WorkspaceDashboard(projects.size, library.size, { page = "search" }, { page = "library" }, { page = "compare" }, { page = "ideas" }, { page = "projects" }, { showPrompt("", "") })
                }
            }
        }
    }
}

private fun LiteratureWork.toResearchPaper() = ResearchPaper(
    id = id,
    openAlexId = id,
    title = title,
    authors = authors,
    year = year,
    journal = journal,
    citationCount = citationCount,
    doi = doi,
    pdfUrl = pdfUrl,
    abstractText = abstractText,
)

private fun getDocumentName(context: android.content.Context, uri: Uri): String {
    var name = ""
    val cursor: Cursor? = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    cursor?.use { if (it.moveToFirst()) name = it.getString(0).orEmpty() }
    return name
}

private fun writeText(context: android.content.Context, uri: Uri, text: String) {
    try {
        context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) }
        Toast.makeText(context, "فایل ذخیره شد.", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "ذخیره فایل ناموفق بود: " + (e.message ?: ""), Toast.LENGTH_LONG).show()
    }
}

private sealed class PdfRenderState {
    data object Loading : PdfRenderState()
    data class Ready(val bitmap: Bitmap, val pageCount: Int) : PdfRenderState()
    data class Failed(val message: String) : PdfRenderState()
}

@Composable
private fun PdfPreviewScreen(context: android.content.Context, paper: ResearchPaper, onBack: () -> Unit) {
    val state by produceState<PdfRenderState>(initialValue = PdfRenderState.Loading, key1 = paper.pdfUri) {
        value = withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(paper.pdfUri)
                val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("فایل PDF قابل بازشدن نیست.")
                val renderer = PdfRenderer(descriptor)
                try {
                    if (renderer.pageCount == 0) error("فایل PDF صفحه‌ای ندارد.")
                    val page = renderer.openPage(0)
                    try {
                        val factor = minOf(2.0f, 1500f / maxOf(page.width, 1))
                        val bitmap = Bitmap.createBitmap((page.width * factor).toInt().coerceAtLeast(1),
                            (page.height * factor).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        PdfRenderState.Ready(bitmap, renderer.pageCount)
                    } finally { page.close() }
                } finally { renderer.close() }
            } catch (e: Exception) {
                PdfRenderState.Failed(e.message ?: "خواندن PDF ناموفق بود.")
            }
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, null); Text("بازگشت به مقاله") }
            Spacer(Modifier.weight(1f))
            Text("پیش‌نمایش PDF", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        when (val current = state) {
            PdfRenderState.Loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is PdfRenderState.Failed -> EmptyState(Icons.Default.ErrorOutline, "پیش‌نمایش ممکن نشد", current.message)
            is PdfRenderState.Ready -> {
                Text("صفحه اول از " + current.pageCount + " صفحه — برای مشاهده کامل فایل از برنامه PDF دستگاه استفاده کن.", style = MaterialTheme.typography.bodySmall)
                Image(current.bitmap.asImageBitmap(), contentDescription = "PDF page 1", modifier = Modifier.fillMaxWidth().weight(1f), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                OutlinedButton(onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(paper.pdfUri), "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) { Toast.makeText(context, "برنامه نمایش PDF پیدا نشد.", Toast.LENGTH_SHORT).show() }
                }, modifier = Modifier.fillMaxWidth()) { Text("بازکردن فایل کامل با برنامه PDF") }
            }
        }
    }
}

@Composable
private fun SearchScreen(onPrompt: (String, String) -> Unit, onSave: (LiteratureWork) -> Unit) {
    val base = BuildConfig.API_BASE_URL
    val now = java.time.Year.now().value
    var query by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf("2021") }
    var to by rememberSaveable { mutableStateOf(now.toString()) }
    var results by remember { mutableStateOf<List<LiteratureWork>>(emptyList()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var total by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("جست‌وجوی مقالات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("موضوع یا کلیدواژه") }, minLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(from, { from = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), label = { Text("از سال") }, singleLine = true)
            OutlinedTextField(to, { to = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), label = { Text("تا سال") }, singleLine = true)
        }
        Button(onClick = {
            error = null; results = emptyList(); selected = emptySet(); total = null
            val a = from.toIntOrNull(); val b = to.toIntOrNull()
            when {
                !base.startsWith("https://") -> error = "سرویس آنلاین پیکربندی نشده است."
                query.trim().length < 2 -> error = "حداقل دو نویسه وارد کن."
                a == null || b == null || a < 1800 || b > now || a > b -> error = "بازه سال معتبر نیست."
                else -> {
                    loading = true
                    scope.launch {
                        try {
                            val r = LiteratureRepository.search(base.trimEnd('/'), query.trim(), a, b)
                            results = r.works; total = r.totalCount
                            if (results.isEmpty()) error = "مقاله‌ای پیدا نشد."
                        } catch (e: Exception) { error = e.message ?: "خطای ارتباط" }
                        finally { loading = false }
                    }
                }
            }
        }, Modifier.fillMaxWidth(), enabled = !loading) {
            if (loading) CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp) else Text("جست‌وجو در دسترسی‌آزادها")
        }
        if (selected.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(selected.size.toString() + " مقاله انتخاب شده")
                    Button(onClick = {
                        val chosen = results.filter { it.id in selected }
                        onPrompt(if (chosen.size == 1) chosen.first().title else query, chosen.joinToString("\n\n---\n\n") { it.promptText() })
                    }, Modifier.fillMaxWidth()) { Text("انتقال مقاله‌ها به استودیوی نگارش") }
                    OutlinedButton(onClick = { results.filter { it.id in selected }.forEach(onSave); selected = emptySet() }, Modifier.fillMaxWidth()) { Text("ذخیره همه در کتابخانه") }
                }
            }
        }
        if (total != null) Text("نمایش " + results.size + " نتیجه از " + total)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results, key = { it.id }) { work ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(work.id in selected, { selected = if (work.id in selected) selected - work.id else selected + work.id })
                            Text("انتخاب مقاله")
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { onSave(work) }) { Icon(Icons.Default.BookmarkAdd, null); Text("ذخیره") }
                        }
                        Text(work.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(work.authors, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(listOfNotNull(work.year?.toString(), work.journal, work.publicationType).joinToString(" · "), style = MaterialTheme.typography.labelSmall)
                        Text("استناد: " + work.citationCount + " — DOI: " + work.doi.ifBlank { "ثبت نشده" }, style = MaterialTheme.typography.labelSmall)
                        Button(onClick = { onPrompt(work.title, work.promptText()) }, modifier = Modifier.fillMaxWidth()) { Text("بررسی در استودیوی نگارش") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectScreen(projects: List<ResearchProject>, onBack: () -> Unit, open: (ResearchProject) -> Unit, delete: (ResearchProject) -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Icon(Icons.Default.ArrowForward, null); Text("بازگشت") }
            Spacer(Modifier.weight(1f))
            Text(projects.size.toString() + " پروژه", style = MaterialTheme.typography.labelMedium)
        }
        if (projects.isEmpty()) EmptyState(Icons.Default.FolderOpen, "پروژه‌ای ثبت نشده", "از استودیوی نگارش یک پروژه ذخیره کن.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(projects, key = { it.id }) { project ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(project.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(project.topic, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { open(project) }, Modifier.weight(1f)) { Text("بازکردن در استودیو") }
                            OutlinedButton(onClick = { delete(project) }) { Icon(Icons.Default.DeleteOutline, "حذف") }
                        }
                    }
                }
            }
        }
    }
}
