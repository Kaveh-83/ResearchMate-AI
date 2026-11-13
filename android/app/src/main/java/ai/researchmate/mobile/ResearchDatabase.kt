package ai.researchmate.mobile

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ResearchPaper(
    val id: String = UUID.randomUUID().toString(),
    val openAlexId: String = "",
    val title: String,
    val authors: String = "",
    val year: Int? = null,
    val journal: String = "",
    val citationCount: Int = 0,
    val doi: String = "",
    val pdfUrl: String = "",
    val abstractText: String = "",
    val pdfUri: String = "",
    val notes: String = "",
    val tags: String = "",
    val addedAtMillis: Long = System.currentTimeMillis(),
)

data class EvidenceRecord(
    val id: String = UUID.randomUUID().toString(),
    val paperId: String,
    val variable: String,
    val value: String,
    val unit: String = "",
    val page: Int? = null,
    val excerpt: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
)

class ResearchDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE papers (
                id TEXT PRIMARY KEY NOT NULL,
                openalex_id TEXT NOT NULL DEFAULT '',
                title TEXT NOT NULL,
                authors TEXT NOT NULL DEFAULT '',
                year INTEGER,
                journal TEXT NOT NULL DEFAULT '',
                citation_count INTEGER NOT NULL DEFAULT 0,
                doi TEXT NOT NULL DEFAULT '',
                pdf_url TEXT NOT NULL DEFAULT '',
                abstract_text TEXT NOT NULL DEFAULT '',
                pdf_uri TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                tags TEXT NOT NULL DEFAULT '',
                added_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_papers_title ON papers(title)")
        db.execSQL("CREATE INDEX idx_papers_doi ON papers(doi)")
        db.execSQL("""
            CREATE TABLE projects (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                topic TEXT NOT NULL DEFAULT '',
                paper_context TEXT NOT NULL DEFAULT '',
                prompt TEXT NOT NULL DEFAULT '',
                updated_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE evidence (
                id TEXT PRIMARY KEY NOT NULL,
                paper_id TEXT NOT NULL,
                variable TEXT NOT NULL,
                value TEXT NOT NULL,
                unit TEXT NOT NULL DEFAULT '',
                page INTEGER,
                excerpt TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                FOREIGN KEY(paper_id) REFERENCES papers(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_evidence_paper ON evidence(paper_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Versioned migrations are added here; user records are never dropped.
    }

    fun migrateLegacyProjects(context: Context) {
        val prefs = context.getSharedPreferences("researchmate_local_projects", Context.MODE_PRIVATE)
        if (prefs.getBoolean("sqlite_migrated_v1", false)) return
        val raw = prefs.getString("projects_json", "[]") ?: "[]"
        val db = writableDatabase
        db.beginTransaction()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val values = ContentValues().apply {
                    put("id", item.optString("id", UUID.randomUUID().toString()))
                    put("title", item.optString("title", "پروژه بدون نام"))
                    put("topic", item.optString("topic", ""))
                    put("paper_context", item.optString("paperContext", ""))
                    put("prompt", item.optString("prompt", ""))
                    put("updated_at", item.optLong("updatedAtMillis", System.currentTimeMillis()))
                }
                db.insertWithOnConflict("projects", null, values, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
            prefs.edit().putBoolean("sqlite_migrated_v1", true).apply()
        } catch (_: Exception) {
            // Keep the original SharedPreferences payload untouched so migration can be retried.
        } finally {
            db.endTransaction()
        }
    }

    fun loadProjects(): List<ResearchProject> {
        val result = mutableListOf<ResearchProject>()
        readableDatabase.query("projects", null, null, null, null, null, "updated_at DESC").use { c ->
            while (c.moveToNext()) {
                result += ResearchProject(
                    id = c.string("id"),
                    title = c.string("title"),
                    topic = c.string("topic"),
                    paperContext = c.string("paper_context"),
                    prompt = c.string("prompt"),
                    updatedAtMillis = c.getLong(c.getColumnIndexOrThrow("updated_at")),
                )
            }
        }
        return result
    }

    fun upsertProject(project: ResearchProject) {
        val values = ContentValues().apply {
            put("id", project.id)
            put("title", project.title)
            put("topic", project.topic)
            put("paper_context", project.paperContext)
            put("prompt", project.prompt)
            put("updated_at", project.updatedAtMillis)
        }
        writableDatabase.insertWithOnConflict("projects", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteProject(id: String) {
        writableDatabase.delete("projects", "id=?", arrayOf(id))
    }

    fun allPapers(): List<ResearchPaper> {
        val result = mutableListOf<ResearchPaper>()
        readableDatabase.query("papers", null, null, null, null, null, "added_at DESC").use { c ->
            while (c.moveToNext()) result += c.toPaper()
        }
        return result
    }

    fun getPaper(id: String): ResearchPaper? {
        readableDatabase.query("papers", null, "id=?", arrayOf(id), null, null, null, "1").use { c ->
            return if (c.moveToFirst()) c.toPaper() else null
        }
    }

    fun upsertPaper(paper: ResearchPaper) {
        val values = ContentValues().apply {
            put("id", paper.id)
            put("openalex_id", paper.openAlexId)
            put("title", paper.title)
            put("authors", paper.authors)
            if (paper.year == null) putNull("year") else put("year", paper.year)
            put("journal", paper.journal)
            put("citation_count", paper.citationCount)
            put("doi", paper.doi)
            put("pdf_url", paper.pdfUrl)
            put("abstract_text", paper.abstractText)
            put("pdf_uri", paper.pdfUri)
            put("notes", paper.notes)
            put("tags", paper.tags)
            put("added_at", paper.addedAtMillis)
        }
        writableDatabase.insertWithOnConflict("papers", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updatePaperNotes(id: String, notes: String, tags: String) {
        val values = ContentValues().apply { put("notes", notes); put("tags", tags) }
        writableDatabase.update("papers", values, "id=?", arrayOf(id))
    }

    fun attachPdf(id: String, uri: String) {
        val values = ContentValues().apply { put("pdf_uri", uri) }
        writableDatabase.update("papers", values, "id=?", arrayOf(id))
    }

    fun deletePaper(id: String) {
        writableDatabase.delete("papers", "id=?", arrayOf(id))
    }

    fun addEvidence(item: EvidenceRecord) {
        val values = ContentValues().apply {
            put("id", item.id); put("paper_id", item.paperId); put("variable", item.variable)
            put("value", item.value); put("unit", item.unit)
            if (item.page == null) putNull("page") else put("page", item.page)
            put("excerpt", item.excerpt); put("created_at", item.createdAtMillis)
        }
        writableDatabase.insertWithOnConflict("evidence", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun evidenceForPaper(paperId: String): List<EvidenceRecord> {
        val result = mutableListOf<EvidenceRecord>()
        readableDatabase.query("evidence", null, "paper_id=?", arrayOf(paperId), null, null, "created_at DESC").use { c ->
            while (c.moveToNext()) result += c.toEvidence()
        }
        return result
    }

    fun allEvidence(): List<EvidenceRecord> {
        val result = mutableListOf<EvidenceRecord>()
        readableDatabase.query("evidence", null, null, null, null, null, "created_at DESC").use { c ->
            while (c.moveToNext()) result += c.toEvidence()
        }
        return result
    }

    fun deleteEvidence(id: String) {
        writableDatabase.delete("evidence", "id=?", arrayOf(id))
    }

    fun backupJson(): String {
        val root = JSONObject().put("schemaVersion", 1).put("exportedAt", System.currentTimeMillis())
        val papers = JSONArray()
        allPapers().forEach { p ->
            papers.put(JSONObject().put("id", p.id).put("openAlexId", p.openAlexId).put("title", p.title)
                .put("authors", p.authors).put("year", p.year ?: JSONObject.NULL).put("journal", p.journal)
                .put("citationCount", p.citationCount).put("doi", p.doi).put("pdfUrl", p.pdfUrl)
                .put("abstractText", p.abstractText).put("pdfUri", p.pdfUri).put("notes", p.notes)
                .put("tags", p.tags).put("addedAtMillis", p.addedAtMillis))
        }
        val projects = JSONArray()
        loadProjects().forEach { p ->
            projects.put(JSONObject().put("id", p.id).put("title", p.title).put("topic", p.topic)
                .put("paperContext", p.paperContext).put("prompt", p.prompt).put("updatedAtMillis", p.updatedAtMillis))
        }
        val evidence = JSONArray()
        allEvidence().forEach { e ->
            evidence.put(JSONObject().put("id", e.id).put("paperId", e.paperId).put("variable", e.variable)
                .put("value", e.value).put("unit", e.unit).put("page", e.page ?: JSONObject.NULL)
                .put("excerpt", e.excerpt).put("createdAtMillis", e.createdAtMillis))
        }
        root.put("papers", papers).put("projects", projects).put("evidence", evidence)
        return root.toString(2)
    }

    fun restoreJson(raw: String): Int {
        val root = JSONObject(raw)
        require(root.optInt("schemaVersion", -1) == 1) { "نسخه فایل پشتیبان پشتیبانی نمی‌شود." }
        val db = writableDatabase
        var restored = 0
        db.beginTransaction()
        try {
            val papers = root.optJSONArray("papers") ?: JSONArray()
            for (i in 0 until papers.length()) {
                val x = papers.optJSONObject(i) ?: continue
                val paper = ResearchPaper(
                    id = x.optString("id").ifBlank { UUID.randomUUID().toString() },
                    openAlexId = x.optString("openAlexId"), title = x.optString("title", "بدون عنوان"),
                    authors = x.optString("authors"), year = if (x.isNull("year")) null else x.optInt("year"),
                    journal = x.optString("journal"), citationCount = x.optInt("citationCount"),
                    doi = x.optString("doi"), pdfUrl = x.optString("pdfUrl"), abstractText = x.optString("abstractText"),
                    pdfUri = x.optString("pdfUri"), notes = x.optString("notes"), tags = x.optString("tags"),
                    addedAtMillis = x.optLong("addedAtMillis", System.currentTimeMillis())
                )
                val values = paper.toContentValues()
                db.insertWithOnConflict("papers", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                restored++
            }
            val projects = root.optJSONArray("projects") ?: JSONArray()
            for (i in 0 until projects.length()) {
                val x = projects.optJSONObject(i) ?: continue
                val project = ResearchProject(
                    id = x.optString("id").ifBlank { UUID.randomUUID().toString() },
                    title = x.optString("title", "پروژه بدون نام"), topic = x.optString("topic"),
                    paperContext = x.optString("paperContext"), prompt = x.optString("prompt"),
                    updatedAtMillis = x.optLong("updatedAtMillis", System.currentTimeMillis())
                )
                val v = ContentValues().apply {
                    put("id", project.id); put("title", project.title); put("topic", project.topic)
                    put("paper_context", project.paperContext); put("prompt", project.prompt); put("updated_at", project.updatedAtMillis)
                }
                db.insertWithOnConflict("projects", null, v, SQLiteDatabase.CONFLICT_REPLACE)
                restored++
            }
            val evidence = root.optJSONArray("evidence") ?: JSONArray()
            for (i in 0 until evidence.length()) {
                val x = evidence.optJSONObject(i) ?: continue
                val paperId = x.optString("paperId")
                if (db.query("papers", arrayOf("id"), "id=?", arrayOf(paperId), null, null, null).use { it.moveToFirst() }) {
                    val v = ContentValues().apply {
                        put("id", x.optString("id").ifBlank { UUID.randomUUID().toString() })
                        put("paper_id", paperId); put("variable", x.optString("variable")); put("value", x.optString("value"))
                        put("unit", x.optString("unit"))
                        if (x.isNull("page")) putNull("page") else put("page", x.optInt("page"))
                        put("excerpt", x.optString("excerpt")); put("created_at", x.optLong("createdAtMillis", System.currentTimeMillis()))
                    }
                    db.insertWithOnConflict("evidence", null, v, SQLiteDatabase.CONFLICT_REPLACE)
                    restored++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return restored
    }

    private fun Cursor.string(name: String): String = getString(getColumnIndexOrThrow(name)) ?: ""
    private fun Cursor.toPaper() = ResearchPaper(
        id=string("id"), openAlexId=string("openalex_id"), title=string("title"), authors=string("authors"),
        year=if (isNull(getColumnIndexOrThrow("year"))) null else getInt(getColumnIndexOrThrow("year")),
        journal=string("journal"), citationCount=getInt(getColumnIndexOrThrow("citation_count")), doi=string("doi"),
        pdfUrl=string("pdf_url"), abstractText=string("abstract_text"), pdfUri=string("pdf_uri"),
        notes=string("notes"), tags=string("tags"), addedAtMillis=getLong(getColumnIndexOrThrow("added_at"))
    )
    private fun Cursor.toEvidence() = EvidenceRecord(
        id=string("id"), paperId=string("paper_id"), variable=string("variable"), value=string("value"),
        unit=string("unit"), page=if (isNull(getColumnIndexOrThrow("page"))) null else getInt(getColumnIndexOrThrow("page")),
        excerpt=string("excerpt"), createdAtMillis=getLong(getColumnIndexOrThrow("created_at"))
    )
    private fun ResearchPaper.toContentValues() = ContentValues().apply {
        put("id", id); put("openalex_id", openAlexId); put("title", title); put("authors", authors)
        if (year == null) putNull("year") else put("year", year)
        put("journal", journal); put("citation_count", citationCount); put("doi", doi); put("pdf_url", pdfUrl)
        put("abstract_text", abstractText); put("pdf_uri", pdfUri); put("notes", notes); put("tags", tags); put("added_at", addedAtMillis)
    }

    companion object {
        private const val DB_NAME = "researchmate_workspace.db"
        private const val DB_VERSION = 1
        @Volatile private var instance: ResearchDatabase? = null
        fun get(context: Context): ResearchDatabase = instance ?: synchronized(this) {
            instance ?: ResearchDatabase(context).also { instance = it }
        }
    }
}
