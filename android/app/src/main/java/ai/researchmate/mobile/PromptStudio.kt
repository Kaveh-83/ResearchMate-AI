package ai.researchmate.mobile

import android.content.ClipData
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.selection.SelectionContainer

@Composable
fun PromptStudioScreen(
    initialTopic: String,
    initialPaperContext: String,
) {
    var topic by rememberSaveable(initialTopic) { mutableStateOf(initialTopic) }
    var domain by rememberSaveable { mutableStateOf("علوم مواد و مهندسی متالورژی") }
    var goal by rememberSaveable { mutableStateOf("تحلیل انتقادی شواهد، مقایسه نتایج و استخراج شکاف پژوهشی قابل اجرا") }
    var reviewType by rememberSaveable { mutableStateOf("مرور انتقادی و ترکیب شواهد") }
    var academicLevel by rememberSaveable { mutableStateOf("تحصیلات تکمیلی؛ در حد مقاله علمی قابل ارسال به مجله") }
    var years by rememberSaveable { mutableStateOf("2020 تا 2026، با استفاده از منابع بنیادی قدیمی‌تر در صورت ضرورت") }
    var outputLanguage by rememberSaveable { mutableStateOf("فارسی علمی؛ اصطلاحات تخصصی انگلیسی در اولین کاربرد داخل پرانتز") }
    var citationStyle by rememberSaveable { mutableStateOf("APA 7") }
    var corpus by rememberSaveable { mutableStateOf("مقالات داوری‌شده و دسترسی‌آزاد؛ ابتدا منابع ارائه‌شده و سپس منابع علمی قابل راستی‌آزمایی") }
    var constraints by rememberSaveable { mutableStateOf("هیچ داده، نتیجه، DOI، رتبه مجله یا منبعی ساخته نشود. محدودیت دسترسی به متن کامل صریح اعلام شود.") }
    var paperContext by rememberSaveable(initialPaperContext) { mutableStateOf(initialPaperContext) }

    val prompt = remember(
        topic, domain, goal, reviewType, academicLevel, years, outputLanguage,
        citationStyle, corpus, constraints, paperContext,
    ) {
        generateFullResearchPrompt(
            topic = topic,
            domain = domain,
            goal = goal,
            reviewType = reviewType,
            academicLevel = academicLevel,
            years = years,
            outputLanguage = outputLanguage,
            citationStyle = citationStyle,
            corpus = corpus,
            constraints = constraints,
            paperContext = paperContext,
        )
    }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "سازنده پرامپت پژوهشی",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "جزئیات پروژه را وارد کن. پرامپت خروجی شامل پروتکل جست‌وجو، غربالگری، تحلیل تک‌مقاله‌ای، مقایسه شواهد، شکاف پژوهشی و مرحله داوری انتقادی است.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PromptField("موضوع یا سؤال اصلی پژوهش *", topic, { topic = it }, minLines = 2)
        PromptField("حوزه علمی", domain, { domain = it })
        PromptField("هدف دقیق پژوهش", goal, { goal = it }, minLines = 2)
        PromptField("نوع خروجی", reviewType, { reviewType = it })
        PromptField("سطح علمی و مخاطب", academicLevel, { academicLevel = it })
        PromptField("بازه زمانی منابع", years, { years = it })
        PromptField("زبان خروجی و سبک نگارش", outputLanguage, { outputLanguage = it }, minLines = 2)
        PromptField("سبک رفرنس‌دهی", citationStyle, { citationStyle = it })
        PromptField("دامنه و نوع منابع", corpus, { corpus = it }, minLines = 2)
        PromptField("محدودیت‌ها و معیارهای ویژه", constraints, { constraints = it }, minLines = 3)
        PromptField("اطلاعات مقاله/مقالات منتخب (اختیاری)", paperContext, { paperContext = it }, minLines = 5)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("پرامپت آماده اجرا", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "پرامپت تولیدشده شامل دستور ضدساختگی، الزام ارجاع‌دهی برای ادعاها، جدول مشخصات هر مقاله، مقایسه روش‌ها و مرحله داوری انتقادی است.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("ResearchMate research prompt", prompt))
                            Toast.makeText(context, "پرامپت کامل کپی شد.", Toast.LENGTH_SHORT).show()
                        },
                        enabled = topic.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("کپی پرامپت کامل")
                    }
                    OutlinedButton(
                        onClick = {
                            topic = ""
                            paperContext = ""
                        },
                    ) {
                        Text("پاک‌کردن موضوع")
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            SelectionContainer {
                Text(
                    prompt,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Start,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PromptField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        minLines = minLines,
    )
}

private fun generateFullResearchPrompt(
    topic: String,
    domain: String,
    goal: String,
    reviewType: String,
    academicLevel: String,
    years: String,
    outputLanguage: String,
    citationStyle: String,
    corpus: String,
    constraints: String,
    paperContext: String,
): String {
    return """
# RESEARCHMATE AI — FULL SCIENTIFIC RESEARCH WORK ORDER

## 1. نقش و استاندارد عملکرد
در نقش یک تیم پژوهشی چندتخصصی شامل پژوهشگر ارشد حوزه، متخصص روش تحقیق، تحلیل‌گر شواهد، کتابدار علمی، ویراستار دانشگاهی و داور سخت‌گیر مجله عمل کن. هدف تولید خروجی مستند، بازتولیدپذیر، انتقادی و قابل ارزیابی است؛ نه صرفاً متنی روان و طولانی.

## 2. مشخصات پروژه
- موضوع/سؤال اصلی: $topic
- حوزه علمی: $domain
- هدف: $goal
- نوع خروجی: $reviewType
- سطح علمی/مخاطب: $academicLevel
- بازه زمانی منابع: $years
- زبان و سبک: $outputLanguage
- سبک استناد: $citationStyle
- دامنه منابع: $corpus
- محدودیت‌های ویژه: $constraints

## 3. اطلاعات مقالات ارائه‌شده
${paperContext.ifBlank { "فعلاً مقاله مشخصی ارائه نشده است. ابتدا منابع معتبر را جست‌وجو و فهرست اولیه را برای بررسی ارائه کن." }}

## 4. اصول غیرقابل نقض صحت علمی
1. هیچ مقاله، نویسنده، عنوان، سال، DOI، عدد، نتیجه آزمایش، نقل‌قول، نام مجله یا رتبه‌ای را جعل نکن.
2. اگر به متن کامل دسترسی نداری، صریحاً بگو تحلیل بر پایه فراداده یا چکیده است؛ وانمود نکن متن کامل خوانده شده است.
3. بین «یافته گزارش‌شده در منبع»، «تفسیر خودت» و «فرضیه پیشنهادی» تمایز روشن بگذار.
4. هر ادعای علمی، عددی، مقایسه‌ای یا تاریخی باید به منبع مشخص ارجاع داشته باشد.
5. اگر داده‌ای موجود نیست، عبارت «گزارش نشده/قابل راستی‌آزمایی نیست» را ثبت کن؛ مقدار جایگزین حدس نزن.
6. از تعمیم نتایج یک ماده، ترکیب، فرایند یا شرایط آزمون به شرایط دیگر بدون استدلال معتبر خودداری کن.
7. رتبه مجله (Q1 تا Q4)، ضریب تأثیر و شاخص‌های مشابه را فقط از منبع معتبر و مربوط به همان سال گزارش کن. تعداد استناد به‌تنهایی معیار کیفیت یا رتبه مجله نیست.
8. منابع متناقض را پنهان نکن؛ اختلاف نتایج و علت‌های محتمل آن را تحلیل کن.
9. از بازنویسی نزدیک به متن مقاله و سرقت ادبی خودداری کن. نقل‌قول مستقیم را محدود و مشخص کن.
10. محدودیت پوشش پایگاه‌ها، فیلتر دسترسی‌آزاد و سوگیری جست‌وجو را گزارش کن.

## 5. پروتکل پژوهش و جست‌وجو
الف) سؤال پژوهش را به اجزای مفهومی، متغیرها و زیرسؤال‌ها بشکن.
ب) مترادف‌ها، صورت‌های املایی، اصطلاحات تخصصی، کلیدواژه‌های قدیمی/جدید و عبارت‌های جست‌وجو را پیشنهاد بده.
ج) پایگاه‌ها و منابعی را که واقعاً جست‌وجو کرده‌ای با تاریخ جست‌وجو گزارش کن. جست‌وجوی انجام‌نشده را انجام‌شده معرفی نکن.
د) معیارهای ورود و خروج را پیش از غربالگری روشن کن: سال، زبان، نوع سند، موضوع، روش، دسترسی و ارتباط مستقیم.
هـ) رکوردهای تکراری را با DOI و سپس عنوان/نویسنده/سال شناسایی کن.
و) برای هر رکورد تصمیم «وارد»، «حذف» یا «نیازمند بررسی» و دلیل کوتاه ثبت کن.
ز) اگر جست‌وجوی واقعی در دسترس نیست، پروتکل و رشته‌های جست‌وجو را آماده کن اما نتایج فرضی نساز.

## 6. تحلیل مستقل هر مقاله
برای تک‌تک مقاله‌ها یک شناسنامه و تحلیل مستقل ارائه کن:
- ارجاع کامل کتاب‌شناختی مطابق $citationStyle
- عنوان اصلی، نویسندگان، سال، مجله/ناشر، نوع سند و DOI/URL
- تعداد استناد و تاریخ/پایگاه دریافت آن، فقط در صورت وجود داده قابل مشاهده
- رتبه مجله و ضریب تأثیر، فقط در صورت راستی‌آزمایی مستقل؛ در غیر این صورت «نامشخص»
- هدف و سؤال پژوهش
- ماده/ترکیب/نمونه، فرایند ساخت یا آماده‌سازی و شرایط آزمون
- روش‌ها، تجهیزات، مدل‌ها و روش تحلیل داده
- نتایج کمی با واحد، دامنه و شرایط دقیق
- مهم‌ترین شکل‌ها و جدول‌ها؛ فقط اگر واقعاً قابل مشاهده/استخراج بوده‌اند
- نقاط قوت، محدودیت‌ها، سوگیری‌ها و شکاف‌های خود مقاله
- میزان ارتباط با سؤال اصلی پژوهش و دلیل آن
- سطح دسترسی شواهد: متن کامل، چکیده، یا صرفاً فراداده

## 7. ماتریس مقایسه بین مقالات
یک جدول مقایسه‌ای بساز که در آن، هر ردیف یک مقاله و ستون‌ها شامل سال، نوع ماده/سامانه، روش ساخت، متغیرهای کلیدی، روش آزمون، شرایط آزمون، نتایج کمی، محدودیت‌ها و DOI باشد.
- فقط مقادیر واقعاً قابل مقایسه را کنار هم قرار بده.
- واحدها را یکسان‌سازی کن و تبدیل واحد را نشان بده.
- اگر شرایط آزمون متفاوت است، از رتبه‌بندی مستقیم نتایج خودداری کن.
- داده‌های گمشده را با «گزارش نشده» مشخص کن.

## 8. ترکیب انتقادی شواهد
- روندهای اصلی و نقاط توافق را شناسایی کن.
- اختلاف‌ها و تناقض‌ها را همراه با توضیح‌های محتمل بررسی کن.
- اثر روش ساخت، ترکیب، ریزساختار، شرایط آزمون و روش اندازه‌گیری را جداگانه تحلیل کن.
- قدرت شواهد را با توجه به تعداد مطالعات، کیفیت روش، قابلیت تکرار و سازگاری نتایج توصیف کن.
- از جمع‌بندی توصیفی صرف عبور کن و رابطه بین یافته‌ها را توضیح بده.

## 9. استخراج شکاف پژوهشی و ایده‌های آینده
برای هر شکاف پیشنهادی:
- شواهد پشتیبان و مقاله‌های مرتبط را ذکر کن.
- توضیح بده شکاف از چه نوعی است: متغیر بررسی‌نشده، تناقض نتایج، ضعف روش، کمبود داده، نبود مقیاس‌پذیری یا نبود اعتبارسنجی.
- میزان اطمینان به جدیدبودن را «کم/متوسط/بالا» همراه با دلیل اعلام کن؛ جدیدبودن را قطعی جلوه نده.
- امکان‌پذیری آزمایشگاهی/صنعتی، تجهیزات، هزینه نسبی، زمان و ریسک را ارزیابی کن.
- حداقل سه سؤال پژوهشی یا فرضیه قابل آزمون پیشنهاد بده.
- برای ایده‌های پیشنهادی، روش آزمون، متغیر مستقل/وابسته و معیار موفقیت را تعیین کن.

## 10. ساختار خروجی نهایی
1. عنوان دقیق و چکیده ساختاریافته
2. مسئله، اهمیت و اهداف
3. سؤال‌ها و روش جست‌وجو/انتخاب منابع
4. مبانی نظری و دسته‌بندی موضوعی
5. تحلیل مستقل مطالعات کلیدی
6. ماتریس مقایسه شواهد
7. ترکیب انتقادی نتایج
8. محدودیت‌ها و عدم‌قطعیت‌ها
9. شکاف‌های پژوهشی اولویت‌بندی‌شده
10. مسیرهای آینده و پیشنهاد روش اجرایی
11. نتیجه‌گیری
12. فهرست منابع کامل و قابل راستی‌آزمایی

## 11. استاندارد ارجاع‌دهی و کنترل منابع
- ارجاع درون‌متنی را در مجاورت ادعای پشتیبانی‌شده قرار بده.
- هر منبع در فهرست پایانی باید حداقل یک کاربرد واقعی در متن داشته باشد و هر ارجاع در متن باید در فهرست پایانی موجود باشد.
- DOI را در قالب پیوند پایدار https://doi.org/... نمایش بده، فقط اگر معتبر است.
- برای هر منبع، عنوان، نویسنده، سال، نام مجله و شناسه را با داده واقعی تطبیق بده.
- اگر شماره صفحه یا محل دقیق شاهد موجود نیست، آن را جعل نکن.
- از رفرنس‌های ثانویه برای ادعایی که منبع اولیه آن در دسترس است، بدون توضیح استفاده نکن.

## 12. مرحله داوری مستقل (Reviewer 2)
پس از تهیه پیش‌نویس، نقش داور سخت‌گیر را بگیر و جداگانه بررسی کن:
- آیا سؤال پژوهش دقیق و پاسخ‌پذیر است؟
- آیا راهبرد جست‌وجو و معیارهای انتخاب بازتولیدپذیرند؟
- آیا ادعاهای کلیدی واقعاً به منابع ارجاع دارند؟
- آیا مقایسه‌ها از نظر ماده، روش و شرایط آزمون معتبرند؟
- آیا محدودیت‌ها و شواهد مخالف دیده شده‌اند؟
- آیا شکاف‌ها از شواهد استخراج شده‌اند یا صرفاً حدس‌اند؟
- آیا نتیجه‌گیری از داده‌ها فراتر رفته است؟
در پایان، ایرادها را با سطح شدت «اساسی/متوسط/جزئی» فهرست و نسخه اصلاح‌شده را ارائه کن.

## 13. قالب نهایی و رفتار در برابر کمبود داده
خروجی را علمی، منظم، دارای تیتر و جدول و بدون تکرار تولید کن. هرجا شواهد کافی نیست، آن قسمت را محدود و عدم‌قطعیت را آشکار کن. اگر انجام یک مرحله به فایل، متن کامل، داده یا دسترسی بیرونی نیاز دارد، دقیقاً همان محدودیت را اعلام کن و مراحل انجام‌شده را از مراحل پیشنهادی جدا نگه دار.
""".trimIndent()
}
