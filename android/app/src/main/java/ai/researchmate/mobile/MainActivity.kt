package ai.researchmate.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.time.Year
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        ResearchMateHome()
                    }
                }
            }
        }
    }
}

@Composable
private fun ResearchMateHome() {
    var selectedSection by rememberSaveable { mutableStateOf("home") }
    var promptSeed by rememberSaveable { mutableStateOf("") }
    var paperContextSeed by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "ResearchMate AI",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "دستیار پژوهش علمی شما",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SectionButton("خانه", selectedSection == "home") { selectedSection = "home" }
            SectionButton("جست‌وجو", selectedSection == "search") { selectedSection = "search" }
            SectionButton("پرامپت", selectedSection == "prompt") {
                promptSeed = ""
                paperContextSeed = ""
                selectedSection = "prompt"
            }
            SectionButton("پروژه‌ها", selectedSection == "projects") { selectedSection = "projects" }
        }

        when (selectedSection) {
            "search" -> LiteratureSearchScreen(
                onCreatePrompt = { title, metadata ->
                    promptSeed = title
                    paperContextSeed = metadata
                    selectedSection = "prompt"
                },
            )
            "prompt" -> key(promptSeed + paperContextSeed) {
                PromptStudioScreen(
                    initialTopic = promptSeed,
                    initialPaperContext = paperContextSeed,
                )
            }
            "projects" -> PlaceholderPanel(
                title = "پروژه‌های پژوهشی",
                description = "مدیریت و ذخیره دائمی پروژه‌ها در فاز بعدی با پایگاه داده محلی اضافه می‌شود. این صفحه فعلاً محل نگهداری پروژه‌ها نیست.",
            )
            else -> HomeScreen(
                onSearch = { selectedSection = "search" },
                onPrompt = {
                    promptSeed = ""
                    paperContextSeed = ""
                    selectedSection = "prompt"
                },
                onProjects = { selectedSection = "projects" },
            )
        }
    }
}

@Composable
private fun HomeScreen(
    onSearch: () -> Unit,
    onPrompt: () -> Unit,
    onProjects: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "فضای پژوهش هوشمند",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "از کشف مقاله تا طراحی پروتکل پژوهش و آماده‌سازی دستور تحلیل علمی.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item {
            FeatureCard(
                title = "جست‌وجوی مقالات",
                description = "جست‌وجوی آنلاین در OpenAlex و مشاهده DOI، تعداد استناد، نوع مقاله و رده استنادی تقریبی.",
                action = "شروع جست‌وجو",
                onClick = onSearch,
            )
        }
        item {
            FeatureCard(
                title = "سازنده پرامپت پژوهشی",
                description = "تولید دستور کامل برای تحلیل مقاله، مرور انتقادی، مقایسه مطالعات، استخراج شکاف پژوهشی و نگارش گزارش.",
                action = "ساخت پرامپت",
                onClick = onPrompt,
            )
        }
        item {
            FeatureCard(
                title = "پروژه‌ها و کتابخانه",
                description = "زیرساخت ذخیره دائمی، واردکردن PDF و کتابخانه آفلاین هنوز در حال توسعه است.",
                action = "مشاهده وضعیت",
                onClick = onProjects,
            )
        }
        item {
            Text(
                text = "نسخه آزمایشی · 0.4.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LiteratureSearchScreen(
    onCreatePrompt: (String, String) -> Unit,
) {
    val apiBaseUrl = BuildConfig.API_BASE_URL
    var query by rememberSaveable { mutableStateOf("") }
    var yearFrom by rememberSaveable { mutableStateOf("2021") }
    var yearTo by rememberSaveable { mutableStateOf(Year.now().value.toString()) }
    var results by remember { mutableStateOf<List<LiteratureWork>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var resultCount by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "جست‌وجوی مقالات علمی",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "منبع فراداده: OpenAlex · فقط مقاله‌ها و مقالات مروری دسترسی‌آزاد",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("موضوع یا کلیدواژه پژوهش") },
            placeholder = { Text("مثلاً high entropy alloy corrosion coating") },
            minLines = 2,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = yearFrom,
                onValueChange = { yearFrom = it.filter(Char::isDigit).take(4) },
                modifier = Modifier.weight(1f),
                label = { Text("از سال") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            OutlinedTextField(
                value = yearTo,
                onValueChange = { yearTo = it.filter(Char::isDigit).take(4) },
                modifier = Modifier.weight(1f),
                label = { Text("تا سال") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        }
        Button(
            onClick = {
                errorMessage = null
                results = emptyList()
                resultCount = null
                val base = apiBaseUrl.trim().trimEnd('/')
                val from = yearFrom.toIntOrNull()
                val to = yearTo.toIntOrNull()
                when {
                    base.isBlank() || !base.startsWith("https://") ->
                        errorMessage = "سرویس آنلاین این نسخه پیکربندی نشده است."
                    query.trim().length < 2 ->
                        errorMessage = "حداقل دو نویسه برای جست‌وجو وارد کن."
                    from == null || to == null || from > to ->
                        errorMessage = "بازه سال انتشار معتبر نیست."
                    else -> {
                        isLoading = true
                        scope.launch {
                            try {
                                val response = LiteratureRepository.search(
                                    baseUrl = base,
                                    query = query.trim(),
                                    yearFrom = from,
                                    yearTo = to,
                                )
                                results = response.works
                                resultCount = response.totalCount
                                if (response.works.isEmpty()) {
                                    errorMessage = "مقاله‌ای مطابق این جست‌وجو پیدا نشد."
                                }
                            } catch (error: Exception) {
                                errorMessage = error.message ?: "خطای نامشخص در ارتباط با سرور."
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text("جست‌وجو در مقالات")
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = "توضیح سطح: تعداد استناد و استناد سالانه از داده‌های OpenAlex نمایش داده می‌شوند. رده «کم تا بسیار بالا» فقط شاخص استنادی تقریبی است؛ کیفیت علمی یا رتبه مجله Q1 تا Q4 را اثبات نمی‌کند.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (resultCount != null) {
            Text("نتایج این صفحه: ${results.size} · تعداد کل اعلام‌شده: $resultCount")
        }
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(results, key = { it.id }) { work ->
                LiteratureWorkCard(work = work) { title, metadata ->
                    onCreatePrompt(title, metadata)
                }
            }
        }
    }
}

@Composable
private fun LiteratureWorkCard(
    work: LiteratureWork,
    onCreatePrompt: (String, String) -> Unit,
) {
    val currentYear = Year.now().value
    val ageYears = work.year?.let { (currentYear - it + 1).coerceAtLeast(1) } ?: 1
    val citationsPerYear = work.citationCount.toDouble() / ageYears
    val citationLevel = when {
        work.citationCount == 0 && ageYears <= 2 -> "تازه / بدون استناد ثبت‌شده"
        work.citationCount == 0 -> "بدون استناد ثبت‌شده"
        citationsPerYear < 1.0 -> "کم"
        citationsPerYear < 5.0 -> "متوسط"
        citationsPerYear < 20.0 -> "بالا"
        else -> "بسیار بالا"
    }
    val typeLabel = when (work.publicationType.lowercase(Locale.ROOT)) {
        "article" -> "مقاله پژوهشی"
        "review" -> "مقاله مروری"
        "preprint" -> "پیش‌چاپ"
        else -> work.publicationType.ifBlank { "نوع سند نامشخص" }
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(work.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                work.authors.ifBlank { "نویسندگان در فراداده موجود نیستند" },
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                listOfNotNull(work.year?.toString(), work.journal, typeLabel).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "استنادهای ثبت‌شده: ${work.citationCount}  |  میانگین تقریبی: ${String.format(Locale.US, "%.1f", citationsPerYear)} استناد در سال",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "سطح استنادی تقریبی: $citationLevel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            if (work.doi.isNotBlank()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text("DOI: ${work.doi}", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (work.abstractText.isNotBlank()) {
                Text(
                    "چکیده: ${work.abstractText.take(380)}${if (work.abstractText.length > 380) "…" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    "چکیده در فراداده این رکورد موجود نیست.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (work.pdfUrl.isNotBlank()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text("نسخه دسترسی‌آزاد: ${work.pdfUrl}", style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(
                onClick = {
                    val metadata = buildString {
                        appendLine("عنوان: ${work.title}")
                        appendLine("نویسندگان: ${work.authors}")
                        appendLine("سال انتشار: ${work.year ?: "نامشخص"}")
                        appendLine("نشریه: ${work.journal.ifBlank { "نامشخص" }}")
                        appendLine("نوع سند: $typeLabel")
                        appendLine("تعداد استناد ثبت‌شده در OpenAlex: ${work.citationCount}")
                        appendLine("استناد تقریبی سالانه: ${String.format(Locale.US, "%.1f", citationsPerYear)}")
                        appendLine("رده استنادی تقریبی: $citationLevel")
                        appendLine("DOI: ${work.doi.ifBlank { "ثبت نشده" }}")
                        appendLine("لینک نسخه دسترسی‌آزاد: ${work.pdfUrl.ifBlank { "ثبت نشده" }}")
                        appendLine("چکیده: ${work.abstractText.ifBlank { "در دسترس نیست" }}")
                    }
                    onCreatePrompt(work.title, metadata)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("ساخت پرامپت تحلیل این مقاله")
            }
        }
    }
}

@Composable
private fun SectionButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(label)
        }
    }
}

@Composable
private fun FeatureCard(title: String, description: String, action: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun PlaceholderPanel(title: String, description: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
