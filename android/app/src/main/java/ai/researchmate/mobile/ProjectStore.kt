package ai.researchmate.mobile
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
data class ResearchProject(val id:String=UUID.randomUUID().toString(),val title:String,val topic:String,val paperContext:String,val prompt:String,val updatedAtMillis:Long=System.currentTimeMillis())
object ProjectStore{
 private const val PREFS="researchmate_local_projects";private const val KEY="projects_json"
 fun load(context:Context):List<ResearchProject>{
  val raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY,"[]")?:"[]"
  return try{val array=JSONArray(raw);buildList{for(i in 0 until array.length()){val x=array.optJSONObject(i)?:continue;add(ResearchProject(x.optString("id",UUID.randomUUID().toString()),x.optString("title","پروژه بدون نام"),x.optString("topic"),x.optString("paperContext"),x.optString("prompt"),x.optLong("updatedAtMillis",0L)))}}.sortedByDescending{it.updatedAtMillis}}catch(_:Exception){emptyList()}
 }
 fun upsert(context:Context,p:ResearchProject){persist(context,(load(context).filterNot{it.id==p.id}+p).sortedByDescending{it.updatedAtMillis})}
 fun delete(context:Context,id:String){persist(context,load(context).filterNot{it.id==id})}
 private fun persist(context:Context,items:List<ResearchProject>){val array=JSONArray();items.forEach{p->array.put(JSONObject().put("id",p.id).put("title",p.title).put("topic",p.topic).put("paperContext",p.paperContext).put("prompt",p.prompt).put("updatedAtMillis",p.updatedAtMillis))};context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY,array.toString()).apply()}
}
