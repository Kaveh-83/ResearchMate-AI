package ai.researchmate.mobile

import java.util.Locale

object ResearchExports {
    fun csv(papers: List<ResearchPaper>): String {
        val header = listOf("title", "authors", "year", "journal", "doi", "citation_count", "pdf_url", "notes", "tags")
        val rows = papers.map { p ->
            listOf(p.title, p.authors, p.year?.toString().orEmpty(), p.journal, p.doi,
                p.citationCount.toString(), p.pdfUrl, p.notes, p.tags)
        }
        return "\uFEFF" + (listOf(header) + rows).joinToString("\n") { row -> row.joinToString(",") { csvCell(it) } }
    }

    fun bibtex(papers: List<ResearchPaper>): String = papers.joinToString("\n\n") { p ->
        val key = bibKey(p)
        val type = if (p.journal.isBlank()) "misc" else "article"
        buildString {
            append("@").append(type).append("{").append(key).appendLine(",")
            append("  title = {").append(bibEscape(p.title)).appendLine("},")
            if (p.authors.isNotBlank()) append("  author = {").append(bibEscape(p.authors.replace(", ", " and "))).appendLine("},")
            if (p.year != null) append("  year = {").append(p.year).appendLine("},")
            if (p.journal.isNotBlank()) append("  journal = {").append(bibEscape(p.journal)).appendLine("},")
            if (p.doi.isNotBlank()) append("  doi = {").append(p.doi).appendLine("},")
            if (p.pdfUrl.isNotBlank()) append("  url = {").append(p.pdfUrl).appendLine("},")
            append("}")
        }
    }

    fun risWorks(works: List<LiteratureWork>): String = works.joinToString("\n\n") { work ->
        buildString {
            appendLine("TY  - JOUR")
            appendLine("TI  - " + work.title)
            if (work.authors.isNotBlank()) work.authors.split(Regex(";|\\\\s+and\\\\s+")).forEach { appendLine("AU  - " + it.trim()) }
            work.year?.let { appendLine("PY  - $it") }
            if (work.journal.isNotBlank()) appendLine("JO  - " + work.journal)
            if (work.doi.isNotBlank()) appendLine("DO  - " + work.doi)
            if (work.landingUrl.isNotBlank()) appendLine("UR  - " + work.landingUrl)
            if (work.abstractText.isNotBlank()) appendLine("AB  - " + work.abstractText.replace("\\n", " "))
            appendLine("ER  -")
        }.trimEnd()
    }

    fun markdown(papers: List<ResearchPaper>, evidence: List<EvidenceRecord>): String = buildString {
        appendLine("# ResearchMate AI — Research Library")
        appendLine()
        appendLine("تعداد رکوردها: " + papers.size)
        appendLine()
        papers.forEachIndexed { index, p ->
            appendLine("## " + (index + 1) + ". " + p.title)
            appendLine()
            appendLine("- نویسندگان: " + p.authors.ifBlank { "ثبت نشده" })
            appendLine("- سال: " + (p.year?.toString() ?: "ثبت نشده"))
            appendLine("- نشریه: " + p.journal.ifBlank { "ثبت نشده" })
            appendLine("- DOI: " + p.doi.ifBlank { "ثبت نشده" })
            appendLine("- استنادهای ثبت‌شده: " + p.citationCount)
            if (p.tags.isNotBlank()) appendLine("- برچسب‌ها: " + p.tags)
            if (p.notes.isNotBlank()) {
                appendLine()
                appendLine("### یادداشت")
                appendLine(p.notes)
            }
            val paperEvidence = evidence.filter { it.paperId == p.id }
            if (paperEvidence.isNotEmpty()) {
                appendLine()
                appendLine("### شواهد ثبت‌شده")
                paperEvidence.forEach { e ->
                    appendLine("- " + e.variable + ": " + e.value + " " + e.unit +
                        (e.page?.let { " (صفحه " + it + ")" } ?: "") +
                        (e.excerpt.takeIf { it.isNotBlank() }?.let { " — " + it } ?: ""))
                }
            }
            appendLine()
            appendLine("---")
            appendLine()
        }
        appendLine("> این گزارش فقط داده‌های ثبت‌شده در کتابخانه محلی را بازتاب می‌دهد و ممیزی مستقل منابع انجام نداده است.")
    }

    private fun csvCell(value: String): String = "\"" + value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\""

    private fun bibKey(p: ResearchPaper): String {
        val base = p.doi.substringAfterLast("/", "").ifBlank {
            p.title.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }.take(3).joinToString("")
        }
        return base.ifBlank { "researchmate" } + (p.year?.toString() ?: "")
    }

    private fun bibEscape(value: String): String = value
        .replace("&", "\\&")
        .replace("%", "\\%")
        .replace("_", "\\_")
        .replace("#", "\\#")
        .replace("$", "\\$")
        .replace("{", "\\{")
        .replace("}", "\\}")
}
