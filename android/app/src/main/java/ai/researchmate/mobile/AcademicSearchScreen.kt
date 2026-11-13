package ai.researchmate.mobile

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class SearchProfileOption(val id: String, val label: String)

private val searchProfiles = listOf(
    SearchProfileOption("all", "همه رشته‌ها"),
    SearchProfileOption("materials", "مواد و متالورژی"),
    SearchProfileOption("engineering", "مهندسی"),
    SearchProfileOption("computer_science", "علوم کامپیوتر و هوش مصنوعی"),
    SearchProfileOption("medicine", "پزشکی"),
    SearchProfileOption("life_sciences", "زیست‌شناسی و علوم زیستی"),
    SearchProfileOption("physics", "فیزیک و ریاضی"),
    SearchProfileOption("chemistry", "شیمی"),
    SearchProfileOption("environment", "محیط زیست و علوم زمین"),
    SearchProfileOption("agriculture", "کشاورزی و علوم غذایی"),
    SearchProfileOption("social_sciences", "علوم اجتماعی و اقتصاد"),
    SearchProfileOption("humanities", "علوم انسانی و حقوق")
)

private data class SearchProviderOption(val id: String, val label: String)

private data class SavedAcademicSearch(
    val query: String, val yearFrom: String, val yearTo: String, val profile: String,
    val manualProviders: Boolean, val providers: Set<String>, val author: String, val journal: String,
    val publisher: String, val documentType: String, val accessMode: String,
    val minCitations: String, val sort: String, val savedAt: Long = System.currentTimeMillis(),
)

private fun loadSavedAcademicSearches(context: android.content.Context): List<SavedAcademicSearch> {
    return try {
        val raw = context.getSharedPreferences("researchmate_saved_searches", android.content.Context.MODE_PRIVATE)
            .getString("items", "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val x = array.optJSONObject(i) ?: continue
                val providers = x.optJSONArray("providers") ?: JSONArray()
                add(SavedAcademicSearch(
                    query = x.optString("query"), yearFrom = x.optString("yearFrom"), yearTo = x.optString("yearTo"),
                    profile = x.optString("profile", "all"), manualProviders = x.optBoolean("manualProviders"),
                    providers = buildSet { for (j in 0 until providers.length()) add(providers.optString(j)) },
                    author = x.optString("author"), journal = x.optString("journal"), publisher = x.optString("publisher"),
                    documentType = x.optString("documentType"), accessMode = x.optString("accessMode", "all"),
                    minCitations = x.optString("minCitations"), sort = x.optString("sort", "relevance"),
                    savedAt = x.optLong("savedAt", 0L),
                ))
            }
        }.filter { it.query.isNotBlank() }
    } catch (_: Exception) { emptyList() }
}

private fun persistSavedAcademicSearches(context: android.content.Context, items: List<SavedAcademicSearch>) {
    val array = JSONArray()
    items.take(20).forEach { item ->
        array.put(JSONObject().apply {
            put("query", item.query); put("yearFrom", item.yearFrom); put("yearTo", item.yearTo)
            put("profile", item.profile); put("manualProviders", item.manualProviders)
            put("providers", JSONArray(item.providers.toList()))
            put("author", item.author); put("journal", item.journal); put("publisher", item.publisher)
            put("documentType", item.documentType); put("accessMode", item.accessMode)
            put("minCitations", item.minCitations); put("sort", item.sort); put("savedAt", item.savedAt)
        })
    }
    context.getSharedPreferences("researchmate_saved_searches", android.content.Context.MODE_PRIVATE)
        .edit().putString("items", array.toString()).apply()
}

private val searchProviders = listOf(
    SearchProviderOption("openalex", "OpenAlex"),
    SearchProviderOption("crossref", "Crossref"),
    SearchProviderOption("semantic_scholar", "Semantic Scholar"),
    SearchProviderOption("europe_pmc", "Europe PMC"),
    SearchProviderOption("pubmed", "PubMed"),
    SearchProviderOption("arxiv", "arXiv")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademicSearchScreen(onPrompt: (String, String) -> Unit, onSave: (LiteratureWork) -> Unit, onExportSelected: (List<LiteratureWork>) -> Unit) {
    val context = LocalContext.current
    val baseUrl = BuildConfig.API_BASE_URL
    val now = java.time.Year.now().value
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var fromYear by rememberSaveable { mutableStateOf((now - 5).toString()) }
    var toYear by rememberSaveable { mutableStateOf(now.toString()) }
    var profile by rememberSaveable { mutableStateOf("all") }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var manualProviders by rememberSaveable { mutableStateOf(false) }
    var selectedProviders by remember { mutableStateOf(setOf<String>()) }
    var author by rememberSaveable { mutableStateOf("") }
    var journal by rememberSaveable { mutableStateOf("") }
    var publisher by rememberSaveable { mutableStateOf("") }
    var documentType by rememberSaveable { mutableStateOf("") }
    var minCitations by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("relevance") }
    var accessMode by rememberSaveable { mutableStateOf("all") }
    var resultTab by rememberSaveable { mutableStateOf("all") }

    var results by remember { mutableStateOf<List<LiteratureWork>>(emptyList()) }
    var providerStatuses by remember { mutableStateOf<List<ProviderSearchStatus>>(emptyList()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var searchSnapshot by remember { mutableStateOf<AcademicSearchRequest?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var total by remember { mutableIntStateOf(0) }
    var providerTotal by remember { mutableIntStateOf(0) }
    var nextPage by remember { mutableStateOf<Int?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var profileMenu by remember { mutableStateOf(false) }
    var savedSearches by remember { mutableStateOf(loadSavedAcademicSearches(context)) }
    var showSavedSearches by remember { mutableStateOf(false) }

    fun runSearch(append: Boolean = false) {
        val start = fromYear.toIntOrNull()
        val end = toYear.toIntOrNull()
        when {
            !baseUrl.startsWith("https://") -> error = "نشانی سرویس جست‌وجو تنظیم نشده است."
            query.trim().length < 2 -> error = "حداقل دو نویسه برای جست‌وجو وارد کن."
            start == null || end == null || start < 1500 || end > now + 1 || start > end ->
                error = "بازه زمانی معتبر نیست."
            manualProviders && selectedProviders.isEmpty() ->
                error = "حداقل یک پایگاه جست‌وجو انتخاب کن یا حالت انتخاب خودکار را فعال کن."
            else -> {
                error = null
                loading = true
                val requestedPage = if (append) (nextPage ?: page + 1) else 1
                if (!append) {
                    results = emptyList()
                    selected = emptySet()
                    searchSnapshot = null
                    providerStatuses = emptyList()
                    total = 0
                    providerTotal = 0
                    nextPage = null
                    page = 1
                }
                scope.launch {
                    try {
                        val request = if (append) {
                            (searchSnapshot ?: error("جزئیات جست‌وجوی قبلی در دسترس نیست.")).copy(page = requestedPage)
                        } else {
                            AcademicSearchRequest(
                                query = query.trim(), yearFrom = start, yearTo = end, profile = profile,
                                providers = if (manualProviders) selectedProviders else emptySet(),
                                author = author.trim(), journal = journal.trim(), publisher = publisher.trim(),
                                documentType = documentType.trim(), accessMode = accessMode,
                                minCitations = minCitations.toIntOrNull(), sort = sort,
                                page = requestedPage, pageSize = 20
                            ).also { searchSnapshot = it }
                        }
                        val response = LiteratureRepository.searchAdvanced(baseUrl, request)
                        results = if (append) (results + response.works).distinctBy { it.id } else response.works
                        providerStatuses = response.providerStatuses
                        total = response.totalCount
                        providerTotal = response.providerTotal
                        nextPage = response.nextPage
                        page = requestedPage
                        if (results.isEmpty() && providerStatuses.none { it.status == "success" || it.status == "empty" }) {
                            error = "منابع انتخاب‌شده پاسخ ندادند. وضعیت هر پایگاه را پایین‌تر بررسی کن."
                        } else if (results.isEmpty()) {
                            error = "با این عبارت و فیلترها نتیجه‌ای پیدا نشد. عبارت یا فیلترها را تغییر بده."
                        }
                    } catch (e: Exception) {
                        error = e.message ?: "ارتباط با سرویس جست‌وجو ناموفق بود."
                    } finally {
                        loading = false
                    }
                }
            }
        }
    }

    val visibleResults = results.filter { work ->
        when (resultTab) {
            "open" -> work.accessStatus == "open"
            "restricted" -> work.accessStatus == "restricted"
            "unknown" -> work.accessStatus == "unknown"
            else -> true
        }
    }

    fun selectAllSearchResults() {
        if (loading || results.isEmpty()) return
        val snapshot = searchSnapshot
        if (snapshot == null) {
            error = "برای ادامه جست‌وجو، ابتدا همین جست‌وجو را دوباره اجرا کن."
            return
        }
        val firstMissingPage = nextPage
        if (firstMissingPage == null) {
            selected = results.map { it.id }.toSet()
            error = if (results.size < total) {
                "همه رکوردهای قابل‌بازیابی انتخاب شدند؛ اما تعداد اعلام‌شده از سوی منابع بیشتر است و صفحه دیگری از API در دسترس نیست."
            } else null
            return
        }
        loading = true
        error = null
        scope.launch {
            val accumulated = results.toMutableList()
            var cursor: Int? = firstMissingPage
            var fetchedPages = 0
            var lastProviderStatuses = providerStatuses
            try {
                while (cursor != null && fetchedPages < 30) {
                    kotlinx.coroutines.delay(300)
                    val request = snapshot.copy(page = cursor)

                    val response = LiteratureRepository.searchAdvanced(baseUrl, request)
                    accumulated.addAll(response.works)
                    cursor = response.nextPage
                    lastProviderStatuses = response.providerStatuses
                    fetchedPages++
                }
                val unique = accumulated.distinctBy { it.id }
                results = unique
                providerStatuses = lastProviderStatuses
                selected = unique.map { it.id }.toSet()
                if (cursor != null) {
                    error = "به سقف ایمن ۳۰ صفحه رسیدیم. ${unique.size} رکورد بازیابی‌شده انتخاب شد؛ ادامه خودکار متوقف شد."
                } else if (unique.size < total) {
                    error = "همه صفحه‌های قابل‌دریافت بررسی شدند، اما فقط ${unique.size} رکورد از ${total} رکورد اعلام‌شده بازیابی شد. رکوردهای موجود انتخاب شدند."
                } else {
                    error = null
                }
            } catch (e: Exception) {
                val unique = accumulated.distinctBy { it.id }
                results = unique
                selected = unique.map { it.id }.toSet()
                error = "بازیابی کامل نشد؛ ${unique.size} رکورد دریافت‌شده انتخاب شد. علت: ${e.message ?: "خطای ارتباط"}"
            } finally {
                loading = false
            }
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("جست‌وجوی علمی", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("جست‌وجوی چندمنبعه در رشته‌های مختلف", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AssistChip(onClick = { advanced = !advanced }, label = { Text("فیلترهای بیشتر") },
                leadingIcon = { Icon(Icons.Default.Tune, null) })
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(bottom = 14.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("موضوع، سؤال پژوهشی، DOI یا کلیدواژه") },
                    placeholder = { Text("مثلاً high entropy alloy coatings") },
                    minLines = 2,
                    trailingIcon = { Icon(Icons.Default.Search, null) }
                )
            }
            item {
                ExposedDropdownMenuBox(expanded = profileMenu, onExpandedChange = { profileMenu = !profileMenu }) {
                    OutlinedTextField(
                        value = searchProfiles.firstOrNull { it.id == profile }?.label ?: "همه رشته‌ها",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        label = { Text("حوزه پژوهش") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileMenu) }
                    )
                    ExposedDropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                        searchProfiles.forEach { option ->
                            DropdownMenuItem(text = { Text(option.label) }, onClick = {
                                profile = option.id
                                profileMenu = false
                            })
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(fromYear, { fromYear = it.filter(Char::isDigit).take(4) },
                        Modifier.weight(1f), label = { Text("از سال") }, singleLine = true)
                    OutlinedTextField(toYear, { toYear = it.filter(Char::isDigit).take(4) },
                        Modifier.weight(1f), label = { Text("تا سال") }, singleLine = true)
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("پایگاه‌های جست‌وجو", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        manualProviders = !manualProviders
                        if (!manualProviders) selectedProviders = emptySet()
                    }) { Text(if (manualProviders) "انتخاب خودکار حوزه" else "انتخاب دستی") }
                }
                if (manualProviders) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        searchProviders.chunked(2).forEach { chunk ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                chunk.forEach { provider ->
                                    FilterChip(
                                        selected = provider.id in selectedProviders,
                                        onClick = {
                                            selectedProviders = if (provider.id in selectedProviders)
                                                selectedProviders - provider.id else selectedProviders + provider.id
                                        },
                                        label = { Text(provider.label) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (chunk.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    Text("منابع به‌صورت خودکار بر اساس حوزه انتخابی فعال می‌شوند.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("وضعیت دسترسی متن کامل", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(accessMode == "all", { accessMode = "all" }, label = { Text("همه") })
                            FilterChip(accessMode == "open", { accessMode = "open" }, label = { Text("آزاد") })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(accessMode == "restricted", { accessMode = "restricted" }, label = { Text("بدون نسخه آزاد") })
                            FilterChip(accessMode == "unknown", { accessMode = "unknown" }, label = { Text("نامشخص") })
                        }
                        Text("نبودن لینک آزاد، به‌تنهایی اثبات نمی‌کند مقاله پولی است؛ ممکن است وضعیت دسترسی نامشخص باشد.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (advanced) {
                item {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("فیلترهای تخصصی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            OutlinedTextField(author, { author = it }, Modifier.fillMaxWidth(),
                                label = { Text("نام نویسنده") }, singleLine = true)
                            OutlinedTextField(journal, { journal = it }, Modifier.fillMaxWidth(),
                                label = { Text("نام مجله یا نشریه") }, singleLine = true)
                            OutlinedTextField(publisher, { publisher = it }, Modifier.fillMaxWidth(),
                                label = { Text("ناشر") }, singleLine = true)
                            OutlinedTextField(documentType, { documentType = it }, Modifier.fillMaxWidth(),
                                label = { Text("نوع سند (article / review / preprint)") }, singleLine = true)
                            OutlinedTextField(minCitations, { minCitations = it.filter(Char::isDigit).take(6) },
                                Modifier.fillMaxWidth(), label = { Text("حداقل تعداد استناد") }, singleLine = true)
                            Text("مرتب‌سازی", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(sort == "relevance", { sort = "relevance" }, label = { Text("ارتباط") })
                                FilterChip(sort == "newest", { sort = "newest" }, label = { Text("جدیدترین") })
                                FilterChip(sort == "citations", { sort = "citations" }, label = { Text("استناد بیشتر") })
                            }
                        }
                    }
                }
            }
            item {
                Button(onClick = { runSearch(false) }, modifier = Modifier.fillMaxWidth(), enabled = !loading) {
                    if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Search, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (loading) "در حال جست‌وجو در چند منبع..." else "جست‌وجوی علمی")
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = {
                        if (query.trim().length < 2) {
                            error = "برای ذخیره، ابتدا عبارت جست‌وجو را وارد کن."
                        } else {
                            val item = SavedAcademicSearch(
                                query = query.trim(), yearFrom = fromYear, yearTo = toYear, profile = profile,
                                manualProviders = manualProviders, providers = selectedProviders,
                                author = author, journal = journal, publisher = publisher,
                                documentType = documentType, accessMode = accessMode,
                                minCitations = minCitations, sort = sort
                            )
                            savedSearches = listOf(item) + savedSearches.filterNot {
                                it.query.equals(item.query, true) && it.profile == item.profile
                            }
                            persistSavedAcademicSearches(context, savedSearches)
                            error = null
                        }
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.BookmarkAdd, null)
                        Spacer(Modifier.width(5.dp))
                        Text("ذخیره جست‌وجو")
                    }
                    OutlinedButton(onClick = { showSavedSearches = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.History, null)
                        Spacer(Modifier.width(5.dp))
                        Text("ذخیره‌شده‌ها (${savedSearches.size})")
                    }
                }
            }

            if (providerStatuses.isNotEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("وضعیت پایگاه‌ها", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            providerStatuses.forEach { status ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val statusText = when (status.status) {
                                        "success" -> "پاسخ: " + status.resultCount
                                        "empty" -> "بدون نتیجه"
                                        "throttled" -> "محدودیت موقت"
                                        "not_requested" -> "انتخاب نشده"
                                        else -> "در دسترس نیست"
                                    }
                                    val color = when (status.status) {
                                        "success" -> MaterialTheme.colorScheme.primary
                                        "empty" -> MaterialTheme.colorScheme.onSurfaceVariant
                                        else -> MaterialTheme.colorScheme.error
                                    }
                                    Text(status.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    Text(statusText, color = color, style = MaterialTheme.typography.labelSmall)
                                }
                                if (status.message.isNotBlank()) {
                                    Text(status.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            if (results.isNotEmpty()) {
                item {
                    Text("نتایج یکتا: $total  •  رکوردهای دریافتی از پایگاه‌ها: $providerTotal",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            OutlinedButton(onClick = { selected = selected + visibleResults.map { it.id } }) {
                                Text("انتخاب قابل‌مشاهده")
                            }
                            OutlinedButton(onClick = { selected = results.map { it.id }.toSet() }) {
                                Text("انتخاب بارگذاری‌شده")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { selectAllSearchResults() }, enabled = !loading && results.isNotEmpty()) {
                                Text(if (loading) "در حال دریافت..." else "دریافت و انتخاب همه صفحه‌ها")
                            }
                            TextButton(onClick = { selected = emptySet() }) { Text("پاک‌کردن انتخاب") }
                        }
                        Text("بارگذاری‌شده: ${results.size} از ${total} نتیجه یکتا • صفحه فعلی: $page",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            FilterChip(resultTab == "all", { resultTab = "all" }, label = { Text("همه (${results.size})") })
                            FilterChip(resultTab == "open", { resultTab = "open" },
                                label = { Text("آزاد (${results.count { it.accessStatus == "open" }})") })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            FilterChip(resultTab == "restricted", { resultTab = "restricted" },
                                label = { Text("بدون نسخه آزاد (${results.count { it.accessStatus == "restricted" }})") })
                            FilterChip(resultTab == "unknown", { resultTab = "unknown" },
                                label = { Text("نامشخص (${results.count { it.accessStatus == "unknown" }})") })
                        }
                    }
                }
            }
            if (selected.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("${selected.size} مقاله انتخاب شده")
                            Button(onClick = {
                                val chosen = results.filter { it.id in selected }
                                onPrompt(if (chosen.size == 1) chosen.first().title else query,
                                    chosen.joinToString("\n\n---\n\n") { it.promptText() })
                            }, modifier = Modifier.fillMaxWidth()) { Text("انتقال به استودیوی نگارش") }
                            OutlinedButton(onClick = {
                                results.filter { it.id in selected }.forEach(onSave)
                                selected = emptySet()
                            }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره مقاله‌های انتخاب‌شده") }
                            OutlinedButton(onClick = {
                                onExportSelected(results.filter { it.id in selected })
                            }, modifier = Modifier.fillMaxWidth()) { Text("خروجی منابع انتخاب‌شده (RIS)") }
                            TextButton(onClick = { selected = emptySet() }, modifier = Modifier.fillMaxWidth()) { Text("لغو انتخاب") }
                        }
                    }
                }
            }
            if (error != null) {
                item { Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }

            items(visibleResults, key = { it.id }) { work ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(work.id in selected, {
                                selected = if (work.id in selected) selected - work.id else selected + work.id
                            })
                            Text("انتخاب", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.weight(1f))
                            AssistChip(
                                onClick = { resultTab = work.accessStatus },
                                label = {
                                    Text(when (work.accessStatus) {
                                        "open" -> "دسترسی آزاد گزارش شده"
                                        "restricted" -> "نسخه آزاد در منبع یافت نشد"
                                        else -> "وضعیت دسترسی نامشخص"
                                    })
                                }
                            )
                        }
                        Text(work.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (work.authors.isNotBlank()) Text(work.authors, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(listOfNotNull(work.year?.toString(), work.journal.takeIf { it.isNotBlank() },
                            work.publicationType.takeIf { it.isNotBlank() }).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall)
                        if (work.publisher.isNotBlank()) Text("ناشر: " + work.publisher, style = MaterialTheme.typography.labelSmall)
                        if (work.citationCounts.isNotEmpty()) {
                            Text("استناد به تفکیک منبع: " + work.citationCounts.entries.joinToString(" | ") { "${it.key}: ${it.value}" },
                                style = MaterialTheme.typography.labelSmall)
                        } else {
                            Text("تعداد استناد در منابع متصل ثبت نشده است.", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (work.journalHIndex != null) {
                            Text("H-index مجله در OpenAlex: ${work.journalHIndex}" +
                                (work.journalTwoYearCitedness?.let { " • میانگین استناد دوساله: %.2f".format(java.util.Locale.US, it) } ?: ""),
                                style = MaterialTheme.typography.labelSmall)
                        }
                        if (work.isRetracted == true) {
                            Text("هشدار: برای این رکورد وضعیت پس‌گرفتن گزارش شده است.", color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        if (work.fieldOfStudy.isNotEmpty()) {
                            Text("حوزه‌ها: " + work.fieldOfStudy.take(3).joinToString("، "), style = MaterialTheme.typography.labelSmall)
                        }
                        if (work.abstractText.isNotBlank()) Text(work.abstractText, style = MaterialTheme.typography.bodySmall, maxLines = 5)
                        Text("منابع: " + work.sources.joinToString(", "), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary)
                        Text("DOI: " + work.doi.ifBlank { "ثبت نشده" }, style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (work.landingUrl.isNotBlank()) {
                                OutlinedButton(onClick = { openExternal(context, work.landingUrl) }, modifier = Modifier.weight(1f)) {
                                    Text("صفحه مقاله")
                                }
                            }
                            if (work.pdfUrl.isNotBlank()) {
                                Button(onClick = { openExternal(context, work.pdfUrl) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.PictureAsPdf, null)
                                    Spacer(Modifier.width(5.dp))
                                    Text("نسخه آزاد")
                                }
                            } else {
                                TextButton(onClick = { onSave(work) }) {
                                    Icon(Icons.Default.BookmarkAdd, null)
                                    Text("ذخیره")
                                }
                            }
                        }
                        Button(onClick = { onPrompt(work.title, work.promptText()) }, modifier = Modifier.fillMaxWidth()) {
                            Text("ارسال به استودیوی نگارش")
                        }
                    }
                }
            }
            if (nextPage != null) {
                item {
                    OutlinedButton(onClick = { runSearch(true) }, modifier = Modifier.fillMaxWidth(), enabled = !loading) {
                        if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.ExpandMore, null)
                        Spacer(Modifier.width(6.dp))
                        Text("دریافت صفحه بعد")
                    }
                }
            }
        }
    }

    if (showSavedSearches) {
        AlertDialog(
            onDismissRequest = { showSavedSearches = false },
            title = { Text("جست‌وجوهای ذخیره‌شده") },
            text = {
                if (savedSearches.isEmpty()) Text("هنوز جست‌وجویی ذخیره نشده است.")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 420.dp)) {
                    items(savedSearches) { item ->
                        Card {
                            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.query, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${item.yearFrom}–${item.yearTo} · ${searchProfiles.firstOrNull { it.id == item.profile }?.label ?: item.profile}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = {
                                    query = item.query; fromYear = item.yearFrom; toYear = item.yearTo
                                    profile = item.profile; manualProviders = item.manualProviders
                                    selectedProviders = item.providers; author = item.author; journal = item.journal
                                    publisher = item.publisher; documentType = item.documentType
                                    accessMode = item.accessMode; minCitations = item.minCitations; sort = item.sort
                                    advanced = true; showSavedSearches = false; results = emptyList()
                                    error = "فیلترها بارگذاری شدند؛ برای اجرای دوباره، دکمه جست‌وجو را بزن."
                                }) { Text("بارگذاری") }
                                IconButton(onClick = {
                                    savedSearches = savedSearches.filterNot { it.savedAt == item.savedAt && it.query == item.query }
                                    persistSavedAcademicSearches(context, savedSearches)
                                }) { Icon(Icons.Default.DeleteOutline, contentDescription = "حذف") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSavedSearches = false }) { Text("بستن") } }
        )
    }
}

private fun openExternal(context: android.content.Context, value: String) {
    try {
        val uri = Uri.parse(value)
        if (uri.scheme !in setOf("https", "http")) return
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: Exception) {
        android.widget.Toast.makeText(context, "بازکردن پیوند ممکن نشد.", android.widget.Toast.LENGTH_SHORT).show()
    }
}
