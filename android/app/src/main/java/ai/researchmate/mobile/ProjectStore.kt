package ai.researchmate.mobile

import android.content.Context
import java.util.UUID

data class ResearchProject(val id: String = UUID.randomUUID().toString(), val title: String, val topic: String, val paperContext: String, val prompt: String, val updatedAtMillis: Long = System.currentTimeMillis())

/**
 * Backwards-compatible facade for the prompt studio.
 * Existing v0.5 projects are migrated from SharedPreferences to SQLite on first access.
 */
object ProjectStore {
    fun load(context: Context): List<ResearchProject> {
        val db = ResearchDatabase.get(context)
        db.migrateLegacyProjects(context)
        return db.loadProjects()
    }

    fun upsert(context: Context, project: ResearchProject) {
        val db = ResearchDatabase.get(context)
        db.migrateLegacyProjects(context)
        db.upsertProject(project)
    }

    fun delete(context: Context, id: String) {
        val db = ResearchDatabase.get(context)
        db.migrateLegacyProjects(context)
        db.deleteProject(id)
    }
}
