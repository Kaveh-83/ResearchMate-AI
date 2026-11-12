package ai.researchmate.mobile
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.time.Year
import java.util.Locale
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  setContent { MaterialTheme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { Surface(Modifier.fillMaxSize()) { ResearchMateHome() } } } }
 }
}
@Composable private fun ResearchMateHome() {
 val ctx=LocalContext.current
 var page by rememberSaveable { mutableStateOf("home") }
 var topic by rememberSaveable { mutableStateOf("") }
 var papers by rememberSaveable { mutableStateOf("") }
 var projectId by rememberSaveable { mutableStateOf<String?>(null) }
 var projects by remember { mutableStateOf(ProjectStore.load(ctx)) }
 fun reload(){projects=ProjectStore.load(ctx)}
 Column(Modifier.fillMaxSize().padding(15.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
   Icon(Icons.Default.Science,null,tint=MaterialTheme.colorScheme.primary)
   Column(Modifier.weight(1f).padding(start=10.dp)){Text("ResearchMate AI",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("دستیار پژوهش علمی",style=MaterialTheme.typography.bodySmall)}
   Text("0.5.0",color=MaterialTheme.colorScheme.primary)
  }
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){
   Nav("خانه",page=="home"){page="home"}
   Nav("جست‌وجو",page=="search"){page="search"}
   Nav("پرامپت",page=="prompt"){topic="";papers="";projectId=null;page="prompt"}
   Nav("پروژه‌ها",page=="projects"){reload();page="projects"}
  }
  AnimatedContent(page,label="page"){target->
   when(target){
    "search"->SearchScreen(
     onPrompt={t,p->topic=t;papers=p;projectId=null;page="prompt"},
     onSave={p->
      val old=projects.firstOrNull{it.title=="مقالات ذخیره‌شده"}
      ProjectStore.upsert(ctx,ResearchProject(id=old?.id?:java.util.UUID.randomUUID().toString(),title="مقالات ذخیره‌شده",topic="کتابخانه مقالات",paperContext=(old?.paperContext.orEmpty()+"\n\n---\n\n"+p).trim(),prompt=""))
      reload()
     })
    "prompt"->key(topic+papers+projectId.orEmpty()){
     val old=projects.firstOrNull{it.id==projectId}
     PromptStudioScreen(old?.topic?:topic,old?.paperContext?:papers,old?.title.orEmpty(),old?.id){ProjectStore.upsert(ctx,it);reload()}
    }
    "projects"->ProjectScreen(projects,{topic=it.topic;papers=it.paperContext;projectId=it.id;page="prompt"},{ProjectStore.delete(ctx,it.id);reload()})
    else->HomeScreen(projects.size,{page="search"},{topic="";papers="";projectId=null;page="prompt"},{reload();page="projects"})
   }
  }
 }
}
@Composable private fun HomeScreen(count:Int,search:()->Unit,prompt:()->Unit,projects:()->Unit){
 LazyColumn(verticalArrangement=Arrangement.spacedBy(11.dp)){
  item{Card(Modifier.fillMaxWidth().animateContentSize(),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){
   Row(Modifier.padding(17.dp),verticalAlignment=Alignment.CenterVertically){
    Icon(Icons.Default.AutoAwesome,null,tint=MaterialTheme.colorScheme.primary)
    Column(Modifier.padding(start=12.dp)){Text("از مقاله تا دست‌نوشته علمی",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text("مقالات را انتخاب کن، پروژه بساز و کیفیت خروجی را ارزیابی کن.")}
   }
  }}
  item{HomeCard(Icons.Default.Search,"جست‌وجوی مقالات","انتخاب چند مقاله و انتقال هم‌زمان به پرامپت.","شروع جست‌وجو",search)}
  item{HomeCard(Icons.Default.AutoAwesome,"استودیوی پرامپت","انتخاب نوع خروجی، امتیاز هدف و چرخه بازنویسی.","ساخت پرامپت",prompt)}
  item{HomeCard(Icons.Default.FolderOpen,"پروژه‌ها و کتابخانه",count.toString()+" پروژه ذخیره‌شده روی همین گوشی.","بازکردن پروژه‌ها",projects)}
  item{Text("ذخیره‌سازی محلی است؛ حذف داده برنامه یا حذف برنامه ممکن است پروژه‌ها را پاک کند.",style=MaterialTheme.typography.labelSmall)}
 }
}
@Composable private fun SearchScreen(onPrompt:(String,String)->Unit,onSave:(String)->Unit){
 val base=BuildConfig.API_BASE_URL;val now=Year.now().value
 var query by rememberSaveable{mutableStateOf("")}
 var from by rememberSaveable{mutableStateOf("2021")}
 var to by rememberSaveable{mutableStateOf(now.toString())}
 var results by remember{mutableStateOf<List<LiteratureWork>>(emptyList())}
 var selected by remember{mutableStateOf(setOf<String>())}
 var loading by remember{mutableStateOf(false)}
 var error by remember{mutableStateOf<String?>(null)}
 var total by remember{mutableStateOf<Int?>(null)}
 val scope=rememberCoroutineScope()
 Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)){
  Text("جست‌وجوی مقالات",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
  OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("موضوع یا کلیدواژه")},minLines=2)
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
   OutlinedTextField(from,{from=it.filter(Char::isDigit).take(4)},Modifier.weight(1f),label={Text("از سال")},singleLine=true)
   OutlinedTextField(to,{to=it.filter(Char::isDigit).take(4)},Modifier.weight(1f),label={Text("تا سال")},singleLine=true)
  }
  Button(onClick={
   error=null;results=emptyList();selected=emptySet();total=null
   val a=from.toIntOrNull();val b=to.toIntOrNull()
   when{
    !base.startsWith("https://")->error="سرویس آنلاین پیکربندی نشده است."
    query.trim().length<2->error="حداقل دو نویسه وارد کن."
    a==null||b==null||a<1800||b>now||a>b->error="بازه سال معتبر نیست؛ سال آینده پذیرفته نمی‌شود."
    else->{loading=true;scope.launch{try{val r=LiteratureRepository.search(base.trimEnd('/'),query.trim(),a,b);results=r.works;total=r.totalCount;if(results.isEmpty())error="مقاله‌ای پیدا نشد."}catch(e:Exception){error=e.message?:"خطای ارتباط"}finally{loading=false}}}
   }
  },Modifier.fillMaxWidth(),enabled=!loading){if(loading)CircularProgressIndicator(Modifier.height(18.dp),strokeWidth=2.dp)else Text("جست‌وجو")}
  AnimatedVisibility(selected.isNotEmpty()){
   Card(Modifier.fillMaxWidth().animateContentSize(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer)){
    Column(Modifier.padding(11.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
     Text(selected.size.toString()+" مقاله انتخاب شده")
     Button(onClick={val chosen=results.filter{it.id in selected};onPrompt(if(chosen.size==1)chosen.first().title else query,chosen.joinToString("\n\n---\n\n"){it.promptText()})},Modifier.fillMaxWidth()){Text("انتقال به پرامپت")}
    }
   }
  }
  if(total!=null)Text("نمایش "+results.size+" نتیجه از "+total)
  error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){
   items(results,key={it.id}){w->
    val age=w.year?.let{(now-it+1).coerceAtLeast(1)}?:1
    val rate=w.citationCount.toDouble()/age
    val level=when{w.citationCount==0->"بدون استناد";rate<1->"کم";rate<5->"متوسط";rate<20->"بالا";else->"بسیار بالا"}
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){
     Column(Modifier.padding(11.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
      Row(verticalAlignment=Alignment.CenterVertically){Checkbox(w.id in selected,{selected=if(w.id in selected)selected-w.id else selected+w.id});Text("انتخاب برای پرامپت")}
      Text(w.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
      Text(w.authors,style=MaterialTheme.typography.bodySmall)
      Text(listOfNotNull(w.year?.toString(),w.journal,w.publicationType).joinToString(" · "),style=MaterialTheme.typography.labelSmall)
      Text("استناد: "+w.citationCount+" · میانگین خام سالانه: "+String.format(Locale.US,"%.1f",rate))
      Text("رده استنادی تقریبی: "+level,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold)
      if(w.doi.isNotBlank())Text("DOI: "+w.doi,style=MaterialTheme.typography.bodySmall)
      Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
       OutlinedButton({onPrompt(w.title,w.promptText())},Modifier.weight(1f)){Text("ارسال به پرامپت")}
       OutlinedButton({onSave(w.promptText())},Modifier.weight(1f)){Text("ذخیره")}
      }
     }
    }
   }
  }
 }
}
private fun LiteratureWork.promptText():String{
 val age=year?.let{(Year.now().value-it+1).coerceAtLeast(1)}?:1
 val rate=citationCount.toDouble()/age
 return buildString{
  appendLine("عنوان: "+title)
  appendLine("نویسندگان: "+authors.ifBlank{"نامشخص"})
  appendLine("سال: "+(year?.toString()?:"نامشخص"))
  appendLine("نشریه: "+journal.ifBlank{"نامشخص"})
  appendLine("نوع سند: "+publicationType)
  appendLine("استنادهای OpenAlex: "+citationCount)
  appendLine("میانگین خام استناد سالانه: "+String.format(Locale.US,"%.1f",rate))
  appendLine("DOI: "+doi.ifBlank{"ثبت نشده"})
  appendLine("نسخه دسترسی‌آزاد: "+pdfUrl.ifBlank{"ثبت نشده"})
  appendLine("چکیده: "+abstractText.ifBlank{"در فراداده موجود نیست"})
  appendLine("توجه: تعداد استناد نه کیفیت مقاله است و نه رتبه Q مجله.")
 }
}
@Composable private fun Nav(label:String,active:Boolean,action:()->Unit){if(active)Button(action,contentPadding=PaddingValues(horizontal=8.dp,vertical=6.dp)){Text(label)}else OutlinedButton(action,contentPadding=PaddingValues(horizontal=8.dp,vertical=6.dp)){Text(label)}}
@Composable private fun HomeCard(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,desc:String,button:String,action:()->Unit){
 Card(Modifier.fillMaxWidth().animateContentSize(),shape=RoundedCornerShape(16.dp),elevation=CardDefaults.cardElevation(2.dp)){
  Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
   Icon(icon,null,tint=MaterialTheme.colorScheme.primary)
   Column(Modifier.weight(1f).padding(start=10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
    Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Text(desc,style=MaterialTheme.typography.bodySmall);OutlinedButton(action){Text(button)}
   }
  }
 }
}
@Composable private fun ProjectScreen(projects:List<ResearchProject>,open:(ResearchProject)->Unit,delete:(ResearchProject)->Unit){
 Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)){
  Text("پروژه‌ها و کتابخانه",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
  Text("ذخیره‌سازی محلی روی همین گوشی",style=MaterialTheme.typography.bodySmall)
  if(projects.isEmpty())Text("هنوز پروژه‌ای ذخیره نشده است.")
  else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){
   items(projects,key={it.id}){p->Card(Modifier.fillMaxWidth()){
    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
     Text(p.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Text(p.topic)
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({open(p)},Modifier.weight(1f)){Text("بازکردن")};OutlinedButton({delete(p)}){Icon(Icons.Default.DeleteOutline,"حذف")}}
    }
   }}
  }
 }
}
