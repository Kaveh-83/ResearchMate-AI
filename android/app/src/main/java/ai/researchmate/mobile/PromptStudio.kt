package ai.researchmate.mobile

import android.content.Context
import android.content.ClipData
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.SelectionContainer
import java.time.Year

private val outputs=listOf("مقاله پژوهشی اصیل","مقاله مروری روایتی","مرور نظام‌مند","فراتحلیل","پروپوزال پژوهشی","فصل پایان‌نامه","گزارش فنی/صنعتی","نقد و داوری مقاله","پوستر علمی","گزارش مرور ادبیات و نقشه پژوهش")

@Composable
fun PromptStudioScreen(initialTopic:String,initialPaperContext:String,initialProjectTitle:String,initialProjectId:String?,onSaveProject:(ResearchProject)->Unit,onExportPdf:(String,String)->Unit,onExportWord:(String,String)->Unit){
 val ctx=LocalContext.current;val now=Year.now().value
 var title by rememberSaveable(initialProjectId){mutableStateOf(initialProjectTitle)}
 var topic by rememberSaveable(initialProjectId,initialTopic){mutableStateOf(initialTopic)}
 var domain by rememberSaveable{mutableStateOf("علوم مواد و مهندسی متالورژی")}
 var goal by rememberSaveable{mutableStateOf("تولید خروجی علمی دقیق، مستند، انتقادی و قابل ارزیابی")}
 var output by rememberSaveable{mutableStateOf(outputs.first())}
 var level by rememberSaveable{mutableStateOf("تحصیلات تکمیلی؛ استاندارد مجله تخصصی")}
 var from by rememberSaveable{mutableStateOf("2020")};var to by rememberSaveable{mutableStateOf(now.toString())}
 var language by rememberSaveable{mutableStateOf("فارسی علمی؛ اصطلاحات انگلیسی در اولین کاربرد")}
 var citation by rememberSaveable{mutableStateOf("APA 7")}
 var audience by rememberSaveable{mutableStateOf("پژوهشگران و داوران مجلات تخصصی")}
 var journal by rememberSaveable{mutableStateOf("")};var length by rememberSaveable{mutableStateOf("6000 تا 8000 کلمه")}
 var corpus by rememberSaveable{mutableStateOf("مقالات داوری‌شده، منابع اولیه و دسترسی‌آزاد")}
 var constraints by rememberSaveable{mutableStateOf("هیچ داده، DOI، منبع یا نتیجه‌ای جعل نشود.")}
 var papers by rememberSaveable(initialProjectId,initialPaperContext){mutableStateOf(initialPaperContext)}
 var target by rememberSaveable(initialProjectId){mutableStateOf(90f)}
 var rounds by rememberSaveable(initialProjectId){mutableStateOf(5f)}\n var finalDraft by rememberSaveable(initialProjectId){mutableStateOf("")}
 var menu by remember{mutableStateOf(false)};var message by remember{mutableStateOf<String?>(null)}
 val a=from.toIntOrNull();val b=to.toIntOrNull();val valid=a!=null&&b!=null&&a in 1800..now&&b in 1800..now&&a<=b
 val promptText=remember(topic,domain,goal,output,level,from,to,language,citation,audience,journal,length,corpus,constraints,papers,target,rounds,valid){if(!valid)"" else buildPrompt(topic,domain,goal,output,level,a!!,b!!,language,citation,audience,journal,length,corpus,constraints,papers,target.toInt(),rounds.toInt())}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
  Text("استودیوی پیشرفته پرامپت",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
  Field("نام پروژه",title,{title=it});Field("موضوع یا سؤال اصلی *",topic,{topic=it},2);Field("حوزه علمی و زیرشاخه",domain,{domain=it});Field("هدف، مسئله و سؤال‌های کلیدی",goal,{goal=it},3)
  Text("نوع خروجی",fontWeight=FontWeight.SemiBold)
  Column{OutlinedButton({menu=true},Modifier.fillMaxWidth()){Text(output)};DropdownMenu(menu,{menu=false}){outputs.forEach{opt->DropdownMenuItem(text={Text(opt)},onClick={output=opt;menu=false})}}}
  Field("سطح علمی",level,{level=it},2);Field("مخاطب",audience,{audience=it});Field("مجله یا راهنمای نگارش هدف",journal,{journal=it});Field("حجم مورد انتظار",length,{length=it})
  Text("بازه سال منابع")
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Field("از سال",from,{from=it.filter(Char::isDigit).take(4)},modifier=Modifier.weight(1f));Field("تا سال",to,{to=it.filter(Char::isDigit).take(4)},modifier=Modifier.weight(1f))}
  if(!valid)Text("سال‌ها باید بین 1800 و "+now+" باشند و شروع از پایان بیشتر نباشد.",color=MaterialTheme.colorScheme.error)
  Field("زبان و سبک نگارش",language,{language=it},2);Field("سبک رفرنس‌دهی",citation,{citation=it});Field("دامنه منابع",corpus,{corpus=it},2);Field("محدودیت‌ها، بودجه، تجهیزات و ملاحظات ویژه",constraints,{constraints=it},3);Field("مقالات منتقل‌شده از جست‌وجو (قابل ویرایش)",papers,{papers=it},7)
  Card(Modifier.fillMaxWidth().animateContentSize(),shape=RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){
   Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
    Text("هدف کیفیت خروجی",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
    Text("امتیاز هدف: "+target.toInt()+" از 100",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
    LinearProgressIndicator(progress={target/100f},Modifier.fillMaxWidth().height(9.dp))
    Text(rank(target.toInt()),fontWeight=FontWeight.SemiBold)
    Slider(target,{target=it},valueRange=60f..98f,steps=37)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("60 قابل قبول",style=MaterialTheme.typography.labelSmall);Text("80 قوی",style=MaterialTheme.typography.labelSmall);Text("90 ممتاز",style=MaterialTheme.typography.labelSmall);Text("98 سخت‌گیرانه",style=MaterialTheme.typography.labelSmall)}
    Text("حداکثر دور بازبینی: "+rounds.toInt());Slider(rounds,{rounds=it},valueRange=1f..8f,steps=6)
    Text("در هر دور، مدل متن را امتیازدهی، اصلاح و دوباره ارزیابی می‌کند.",style=MaterialTheme.typography.bodySmall)
   }
  }
  Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(15.dp)){
   Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
    Text("پرامپت نهایی و چرخه‌ای",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
    Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
     Button(onClick={val cb=ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager;cb.setPrimaryClip(ClipData.newPlainText("ResearchMate prompt",promptText));Toast.makeText(ctx,"پرامپت کپی شد",Toast.LENGTH_SHORT).show()},enabled=topic.isNotBlank()&&valid,modifier=Modifier.weight(1f)){Text("کپی پرامپت")}
     OutlinedButton(onClick={if(title.isBlank()||topic.isBlank()||!valid)message="نام پروژه، موضوع و سال معتبر را کامل کن." else {onSaveProject(ResearchProject(id=initialProjectId?:java.util.UUID.randomUUID().toString(),title=title.trim(),topic=topic.trim(),paperContext=papers,prompt=promptText));message="پروژه ذخیره شد."}},enabled=title.isNotBlank()&&topic.isNotBlank()&&valid){Text("ذخیره پروژه")}
    }
    message?.let{Text(it,color=MaterialTheme.colorScheme.primary)}
   }
  }
  Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(15.dp)){SelectionContainer{Text(promptText.ifBlank{"موضوع و سال معتبر را وارد کن."},Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall,textAlign=TextAlign.Start)}}
  Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){
   Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
    Text("تحویل فایل مقاله",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
    Text("متن نهایی تولیدشده توسط ChatGPT یا مدل دیگر را در کادر زیر قرار بده؛ سپس فایل واقعی PDF یا Word را مستقیماً روی گوشی ذخیره کن.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(finalDraft,{finalDraft=it},Modifier.fillMaxWidth(),label={Text("متن کامل مقاله یا گزارش نهایی")},minLines=8)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
     Button(onClick={onExportPdf(title.ifBlank{topic},finalDraft)},enabled=finalDraft.isNotBlank(),modifier=Modifier.weight(1f)){Text("ساخت PDF")}
     OutlinedButton(onClick={onExportWord(title.ifBlank{topic},finalDraft)},enabled=finalDraft.isNotBlank(),modifier=Modifier.weight(1f)){Text("ساخت Word")}
    }
    Text("خروجی به‌صورت آفلاین روی دستگاه ساخته می‌شود؛ متن برای تبدیل فایل به سرور ارسال نمی‌شود.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.tertiary)
   }
  }
  Spacer(Modifier.height(12.dp))
 }
}
@Composable private fun Field(label:String,value:String,onChange:(String)->Unit,minLines:Int=1,modifier:Modifier=Modifier.fillMaxWidth()){OutlinedTextField(value,onChange,modifier,label={Text(label)},minLines=minLines,singleLine=minLines==1)}
private fun rank(s:Int)=when{s>=95->"A+ · ممتاز استثنایی";s>=90->"A · ممتاز و بسیار قوی";s>=80->"B · قوی";s>=70->"C · قابل قبول با اصلاح";else->"D · بازنگری اساسی"}
private fun buildPrompt(topic:String,domain:String,goal:String,output:String,level:String,from:Int,to:Int,language:String,citation:String,audience:String,journal:String,length:String,corpus:String,constraints:String,papers:String,target:Int,rounds:Int):String{
 val template="""
# RESEARCHMATE AI — SCIENTIFIC WRITING & ITERATIVE REVIEW

موضوع: {{TOPIC}}
حوزه: {{DOMAIN}}
هدف: {{GOAL}}
نوع خروجی: {{OUTPUT}}
سطح: {{LEVEL}}
مخاطب: {{AUDIENCE}}
بازه منابع: {{FROM}} تا {{TO}}
زبان: {{LANGUAGE}}
سبک استناد: {{CITATION}}
مجله هدف: {{JOURNAL}}
حجم: {{LENGTH}}
دامنه منابع: {{CORPUS}}
محدودیت‌ها: {{CONSTRAINTS}}
امتیاز هدف: {{TARGET}}/100 — {{RANK}}
حداکثر دور بازبینی: {{ROUNDS}}

## نقش و صداقت علمی
در نقش پژوهشگر ارشد، متخصص روش تحقیق، کتابدار علمی، نویسنده، ویراستار و داور سخت‌گیر عمل کن.
- مقاله، نویسنده، DOI، عدد، داده، نتیجه، نقل‌قول یا رتبه مجله جعل نکن.
- یافته منبع، تفسیر و فرضیه را جدا کن؛ هر ادعای علمی و عددی را ارجاع بده.
- اگر فقط چکیده/فراداده در دسترس است، صریح اعلام کن؛ وانمود نکن متن کامل خوانده شده.
- شواهد مخالف، نتایج منفی و محدودیت‌ها را حذف نکن.
- Q1 تا Q4 و ضریب تأثیر فقط با منبع معتبر و سال مربوط گزارش شود. تعداد استناد یا نمره این روبریک معادل رتبه مجله نیست.
- داده مفقود را «گزارش نشده» بنویس؛ حدس نزن؛ سرقت ادبی ممنوع.
- مشخصات کتاب‌شناختی و DOI را کنترل کن؛ جست‌وجوی انجام‌نشده را انجام‌شده جلوه نده.

## اصول مقاله‌نویسی
عنوان دقیق و متناسب با شواهد؛ چکیده ساختاریافته؛ 4 تا 8 کلیدواژه استاندارد.
مقدمه: زمینه، وضعیت دانش، مسئله، شکاف مستند، اهمیت و هدف/فرضیه.
مرور ادبیات: ترکیب موضوعی و انتقادی، نه فهرست مقاله‌ها؛ تحلیل توافق و تناقض.
روش: مواد، ترکیب، تجهیزات، شرایط، نمونه، کنترل، اندازه‌گیری و آمار بازتولیدپذیر؛ فقط داده واقعی.
نتایج: جداسازی مشاهده از تفسیر، واحد SI، ارقام معنادار، عدم‌قطعیت و شرایط آزمون.
بحث: تفسیر، مقایسه منصفانه، سازوکار محتمل، شواهد مخالف و محدودیت.
نتیجه‌گیری: پاسخ مستقیم به سؤال، بدون داده جدید یا تعمیم ناموجه.
جدول/شکل: شماره، عنوان خودبسنده، واحد، راهنمای علائم و ارجاع در متن؛ شکل/داده جعلی ممنوع.
نمادها و اختصارات را تعریف و یکدست کن؛ لحن دقیق و بی‌طرف باشد.
تعارض منافع، حمایت مالی، دسترس‌پذیری داده و اخلاق را در صورت ارتباط اعلام کن.
PRISMA و سایر راهنماها فقط برای طرح مرتبط.

## ساختار متناسب با نوع خروجی
- مقاله اصیل: Title, Abstract, Keywords, Introduction, Materials and Methods, Results, Discussion, Conclusion, Declarations, References.
- مرور روایتی: دامنه انتخاب، دسته‌بندی موضوعی، سنتز انتقادی و محدودیت پوشش.
- مرور نظام‌مند: سؤال، پروتکل، پایگاه و تاریخ جست‌وجو، رشته جست‌وجو، معیارهای ورود/خروج، غربالگری، استخراج، خطر سوگیری و سنتز.
- فراتحلیل: اندازه اثر، مدل، ناهمگنی، حساسیت و سوگیری انتشار، فقط با داده کافی.
- پروپوزال: مسئله، پیشینه، شکاف، نوآوری، اهداف، فرضیه، روش، متغیرها، زمان، بودجه و ریسک.
- فصل پایان‌نامه: ساختار دانشگاهی و پیوند منطقی فصل‌ها.
- گزارش صنعتی: مسئله عملیاتی، شرایط، روش، نتایج، هزینه، ریسک، مقیاس‌پذیری و توصیه.
- نقد مقاله: خلاصه منصفانه، قوت‌ها، ایرادهای روش‌شناختی و اصلاحات.
- پوستر: مسئله، هدف، روش، یافته‌ها، نتیجه و منابع کلیدی.
- نقشه پژوهش: خوشه‌ها، روندها، مطالعات محوری واقعی، شکاف‌ها و مسیر آینده.

## مقالات ورودی
{{PAPERS}}

## استخراج شواهد
برای هر مقاله: ارجاع کامل، DOI، نوع سند، سال، نشریه، تعداد استناد با تاریخ و پایگاه، هدف، نمونه/مواد، روش، شرایط آزمون، نتایج کمی، محدودیت و سطح دسترسی متن را استخراج کن. ماتریس مقایسه بساز و تفاوت شرایط آزمون را آشکار نگه دار. هر ادعا را به شاهد واقعی وصل کن.

## روبریک 100 امتیازی
اصالت و وضوح سؤال 15%؛ کیفیت شواهد 15%؛ روش‌شناسی و بازتولیدپذیری 15%؛ تحلیل انتقادی 15%؛ اعتبار نتایج و عدم‌قطعیت 10%؛ ساختار 10%؛ نگارش 8%؛ صحت ارجاع 7%؛ تناسب قالب 5%.
هر معیار را از 0 تا 10 نمره بده، وزن‌ها را اعمال کن و برای هر امتیاز شاهد و دلیل ارائه کن.
رده داخلی: 95–100 A+؛ 90–94 A؛ 80–89 B؛ 70–79 C؛ زیر 70 D. این رتبه، Q مجله یا تضمین پذیرش نیست.

## چرخه اجباری ارزیابی و بازنویسی
1. پیش‌نویس کامل را در قالب انتخاب‌شده تولید کن.
2. منابع و ارجاعات را از نظر واقعی‌بودن و تطبیق کتاب‌شناختی ممیزی کن.
3. در نقش داور مستقل، متن را نمره بده؛ محل ایراد و شدت را مشخص کن.
4. ایرادها را بر اساس اثر بر اعتبار علمی اولویت‌بندی کن.
5. متن را اصلاح کن؛ برای افزایش نمره داده یا منبع نساز.
6. دوباره با همان روبریک نمره بده و تغییر معیارها را گزارش کن.
7. مراحل 3 تا 6 را حداکثر {{ROUNDS}} دور تکرار کن یا وقتی به {{TARGET}} رسیدی و ایراد اساسی باقی نماند متوقف شو.
8. اگر به هدف نرسیدی، پس از دور آخر متوقف شو و نمره واقعی، فاصله تا هدف و ایرادهای باقی‌مانده را گزارش کن؛ رسیدن به هدف را جعل نکن.
9. نمره هر دور مستقل و انتقادی باشد.

## تحویل فایل — شرط اجباری
- خروجی نهایی را فقط به شکل متن خام یا پاسخ چت تحویل نده. باید یک فایل واقعی و قابل دریافت تولید کنی.
- فایل اصلی را با فرمت PDF و صفحه‌آرایی علمی آماده کن؛ در صورت امکان یک نسخه Word قابل ویرایش (.docx) نیز بساز.
- فایل باید شامل عنوان، نویسندگان/جای‌نگهدارهای مشخص، چکیده، کلیدواژه‌ها، متن کامل بخش‌بندی‌شده، جدول‌ها، ارجاع‌های درون‌متنی و فهرست منابع باشد.
- صفحه‌آرایی: اندازه A4، حاشیه استاندارد، فونت خوانا، تیترهای سلسله‌مراتبی، شماره صفحه، فاصله خطوط مناسب، جدول‌های شماره‌گذاری‌شده و منابع با تورفتگی آویزان در صورت پشتیبانی.
- معادلات را با نمادگذاری استاندارد و واحدهای SI بنویس؛ از شکستن معادلات و جدول‌ها در صفحه تا حد امکان جلوگیری کن.
- قبل از تحویل، فایل را بازبینی کن: صفحات خالی/بریده، متن ناقص، فونت ناخوانا، ارجاعات شکسته و جدول‌های خارج از صفحه نباید باقی بمانند.
- پس از ساخت، پیوند دانلود مستقیم فایل را ارائه کن و وجود فایل را بررسی کن. اگر محیط واقعاً امکان ساخت یا پیوست فایل ندارد، صریح بگو و هرگز وانمود نکن فایل ساخته شده است؛ در آن حالت متن کامل و آماده تبدیل را تحویل بده.
- متن کامل را هم در پاسخ کوتاه خلاصه کن، اما فایل PDF باید مرجع اصلی تحویل باشد. پذیرش مجله یا کیفیت قطعی را تضمین نکن.
"""
 return template
  .replace("{{TOPIC}}",topic).replace("{{DOMAIN}}",domain).replace("{{GOAL}}",goal)
  .replace("{{OUTPUT}}",output).replace("{{LEVEL}}",level).replace("{{AUDIENCE}}",audience)
  .replace("{{FROM}}",from.toString()).replace("{{TO}}",to.toString())
  .replace("{{LANGUAGE}}",language).replace("{{CITATION}}",citation).replace("{{JOURNAL}}",journal.ifBlank{"تعیین نشده"})
  .replace("{{LENGTH}}",length).replace("{{CORPUS}}",corpus).replace("{{CONSTRAINTS}}",constraints)
  .replace("{{TARGET}}",target.toString()).replace("{{RANK}}",rank(target)).replace("{{ROUNDS}}",rounds.toString())
  .replace("{{PAPERS}}",papers.ifBlank{"مقاله‌ای ضمیمه نشده؛ منابع واقعی را جست‌وجو و فهرست اولیه را مشخص کن."})
}
