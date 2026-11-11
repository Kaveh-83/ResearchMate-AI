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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionButton("خانه", selectedSection == "home") { selectedSection = "home" }
            SectionButton("جست‌وجو", selectedSection == "search") { selectedSection = "search" }
            SectionButton("پروژه‌ها", selectedSection == "projects") { selectedSection = "projects" }
        }

        when (selectedSection) {
            "search" -> LiteratureSearchScreen()
            "projects" -> PlaceholderPanel(
                title = "پروژه‌های پژوهشی",
                description = "پروژه‌ها و مقاله‌های ذخیره‌شده در حافظه محلی گوشی نگهداری خواهند شد.",
            )
            else -> LazyColumn(
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
                                text = "جست‌وجو، بررسی و سازمان‌دهی مقاله‌ها و ساخت گزارش‌های علمی در یک محیط یکپارچه.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                item {
                    FeatureCard(
                        title = "جست‌وجوی مقالات",
                        description = "جست‌وجوی زنده در منابع علمی و دریافت اطلاعات کتاب‌شناختی مقالات دسترسی‌آزاد.",
                        action = "شروع جست‌وجو",
                        onClick = { selectedSection = "search" },
                    )
                }
                item {
                    FeatureCard(
                        title = "سازنده پرامپت پژوهشی",
                        description = "ساخت دستورهای دقیق برای تحلیل مقاله، مقایسه مطالعات و نگارش مرور علمی.",
                        action = "در مرحله توسعه",
                        onClick = { },
                    )
                }
                item {
                    FeatureCard(
                        title = "کتابخانه و پروژه‌ها",
                        description = "نگهداری مقاله‌ها و نتایج روی دستگاه، همراه با دسترسی آفلاین.",
                        action = "مشاهده پروژه‌ها",
                        onClick = { selectedSection = "projects" },
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "نسخه آزمایشی اولیه · 0.1.0",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiteratureSearchScreen() {
    val apiBaseUrl = BuildConfig.API_BASE_URL
    var query by rememberSaveable { mutableStateOf("") }
    var yearFrom by rememberSaveable { mutableStateOf("2021") }
    var yearTo by rememberSaveable { mutableStateOf("2026") }
    var results by remember { mutableStateOf<List<LiteratureWork>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var resultCount by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("جست‌وجوی مقالات علمی", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            if (apiBaseUrl.isBlank()) {
                "جست‌وجوی آنلاین پس از فعال‌شدن سرویس مرکزی در دسترس قرار می‌گیرد. نیازی به تنظیم سرور توسط کاربر نیست."
            } else {
                "اتصال به سرویس پژوهشی مرکزی آماده است."
            },
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
                if (base.isBlank() || !base.startsWith("https://")) {
                    errorMessage = "سرویس آنلاین این نسخه هنوز پیکربندی نشده است. این بخش پس از راه‌اندازی سرور فعال می‌شود."
                } else if (query.trim().length < 2) {
                    errorMessage = "حداقل دو نویسه برای جست‌وجو وارد کن."
                } else if (yearFrom.length != 4 || yearTo.length != 4 || yearFrom.toInt() > yearTo.toInt()) {
                    errorMessage = "بازه سال انتشار معتبر نیست."
                } else {
                    isLoading = true
                    scope.launch {
                        try {
                            val response = LiteratureRepository.search(
                                baseUrl = base,
                                query = query.trim(),
                                yearFrom = yearFrom.toInt(),
                                yearTo = yearTo.toInt(),
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

        if (resultCount != null) {
            Text("تعداد نتایج در صفحه فعلی: ${results.size} · تعداد کل تقریبی: ${resultCount}")
        }
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(results, key = { it.id }) { work ->
                LiteratureWorkCard(work)
            }
        }
    }
}

@Composable
private fun LiteratureWorkCard(work: LiteratureWork) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(work.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                work.authors.ifBlank { "نویسندگان در فراداده موجود نیستند" },
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                listOfNotNull(work.year?.toString(), work.journal, "استناد: ${work.citationCount}").joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (work.doi.isNotBlank()) {
                Text("DOI: ${work.doi}", style = MaterialTheme.typography.bodySmall)
            }
            if (work.pdfUrl.isNotBlank()) {
                Text("نسخه دسترسی‌آزاد: ${work.pdfUrl}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SectionButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick) { Text(label) }
    else OutlinedButton(onClick = onClick) { Text(label) }
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
