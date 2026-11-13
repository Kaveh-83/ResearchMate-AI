package ai.researchmate.mobile

import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class LiteratureWork(
    val id: String,
    val title: String,
    val authors: String,
    val year: Int?,
    val journal: String,
    val citationCount: Int,
    val doi: String,
    val pdfUrl: String,
    val abstractText: String,
    val publicationType: String,
    val publisher: String = "",
    val landingUrl: String = "",
    val accessStatus: String = "unknown",
    val citationCounts: Map<String, Int> = emptyMap(),
    val sources: List<String> = emptyList(),
    val journalHIndex: Int? = null,
    val journalTwoYearCitedness: Double? = null,
    val issn: List<String> = emptyList(),
    val fieldOfStudy: List<String> = emptyList(),
    val peerReviewStatus: String = "unknown",
    val isRetracted: Boolean? = null,
)

data class ProviderSearchStatus(
    val provider: String,
    val label: String,
    val status: String,
    val resultCount: Int,
    val message: String,
    val elapsedMs: Long,
)

data class LiteratureSearchResponse(
    val works: List<LiteratureWork>,
    val totalCount: Int,
    val providerTotal: Int,
    val nextPage: Int?,
    val providerStatuses: List<ProviderSearchStatus>,
)

data class AcademicSearchRequest(
    val query: String,
    val yearFrom: Int,
    val yearTo: Int,
    val profile: String = "all",
    val providers: Set<String> = emptySet(),
    val author: String = "",
    val journal: String = "",
    val publisher: String = "",
    val documentType: String = "",
    val accessMode: String = "all",
    val minCitations: Int? = null,
    val sort: String = "relevance",
    val page: Int = 1,
    val pageSize: Int = 20,
)

object LiteratureRepository {
    suspend fun searchAdvanced(baseUrl: String, request: AcademicSearchRequest): LiteratureSearchResponse =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val endpoint = URL(baseUrl.trimEnd('/') + "/v2/academic-search")
            val connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 18_000
                readTimeout = 35_000
                doOutput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            val body = JSONObject().apply {
                put("query", request.query)
                put("yearFrom", request.yearFrom)
                put("yearTo", request.yearTo)
                put("profile", request.profile)
                put("providers", JSONArray(request.providers.toList()))
                put("author", request.author)
                put("journal", request.journal)
                put("publisher", request.publisher)
                put("documentType", request.documentType)
                put("accessMode", request.accessMode)
                if (request.minCitations != null) put("minCitations", request.minCitations)
                put("sort", request.sort)
                put("page", request.page)
                put("pageSize", request.pageSize)
            }.toString()

            try {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val root = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
                if (status !in 200..299) {
                    val message = root.optJSONObject("error")?.optString("message")
                        ?.takeIf { !it.isNullOrBlank() }
                        ?: "سرور جست‌وجو با خطای HTTP $status پاسخ داد."
                    throw IllegalStateException(message)
                }

                val worksJson = root.optJSONArray("works") ?: JSONArray()
                val works = buildList {
                    for (index in 0 until worksJson.length()) {
                        val item = worksJson.optJSONObject(index) ?: continue
                        val citationObject = item.optJSONObject("citationCounts") ?: JSONObject()
                        val citations = buildMap {
                            val iterator = citationObject.keys()
                            while (iterator.hasNext()) {
                                val key = iterator.next()
                                put(key, citationObject.optInt(key, 0))
                            }
                        }
                        val metric = item.optJSONObject("journalMetrics")
                        val sources = item.optJSONArray("sources").toStringList()
                        val issn = item.optJSONArray("issn").toStringList()
                        val fields = item.optJSONArray("fieldOfStudy").toStringList()
                        val yearValue = if (item.isNull("year")) null else item.optInt("year").takeIf { it > 0 }
                        val title = item.optString("title").trim()
                        if (title.isBlank()) continue
                        add(
                            LiteratureWork(
                                id = item.optString("id").ifBlank { "result-$index" },
                                title = title,
                                authors = item.optString("authors"),
                                year = yearValue,
                                journal = item.optString("journal"),
                                citationCount = citations.values.maxOrNull() ?: 0,
                                doi = item.optString("doi"),
                                pdfUrl = item.optString("pdfUrl"),
                                abstractText = item.optString("abstractText"),
                                publicationType = item.optString("publicationType"),
                                publisher = item.optString("publisher"),
                                landingUrl = item.optString("landingUrl"),
                                accessStatus = item.optString("accessStatus", "unknown"),
                                citationCounts = citations,
                                sources = sources,
                                journalHIndex = metric?.let { if (it.isNull("openAlexHIndex")) null else it.optInt("openAlexHIndex") },
                                journalTwoYearCitedness = metric?.let { if (it.isNull("openAlexTwoYearMeanCitedness")) null else it.optDouble("openAlexTwoYearMeanCitedness") },
                                issn = issn,
                                fieldOfStudy = fields,
                                peerReviewStatus = item.optString("peerReviewStatus", "unknown"),
                                isRetracted = if (item.isNull("isRetracted")) null else item.optBoolean("isRetracted"),
                            )
                        )
                    }
                }

                val statusJson = root.optJSONArray("providerStatus") ?: JSONArray()
                val statuses = buildList {
                    for (index in 0 until statusJson.length()) {
                        val item = statusJson.optJSONObject(index) ?: continue
                        add(
                            ProviderSearchStatus(
                                provider = item.optString("provider"),
                                label = item.optString("label"),
                                status = item.optString("status"),
                                resultCount = item.optInt("resultCount"),
                                message = item.optString("message"),
                                elapsedMs = item.optLong("elapsedMs"),
                            )
                        )
                    }
                }

                LiteratureSearchResponse(
                    works = works,
                    totalCount = root.optInt("totalUnique", works.size),
                    providerTotal = root.optInt("providerTotal", 0),
                    nextPage = if (root.isNull("nextPage")) null else root.optInt("nextPage"),
                    providerStatuses = statuses,
                )
            } finally {
                connection.disconnect()
            }
        }

    // Compatibility method for older integrations and tests.
    suspend fun search(baseUrl: String, query: String, yearFrom: Int, yearTo: Int): LiteratureSearchResponse {
        return searchAdvanced(
            baseUrl,
            AcademicSearchRequest(query, yearFrom, yearTo, profile = "all", providers = setOf("openalex"))
        )
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val value = optString(index).trim()
            if (value.isNotBlank() && value != "null") add(value)
        }
    }
}

fun LiteratureWork.promptText(): String {
    val age = year?.let { (java.time.Year.now().value - it + 1).coerceAtLeast(1) } ?: 1
    val annualRate = citationCount.toDouble() / age
    return buildString {
        appendLine("عنوان: $title")
        appendLine("نویسندگان: " + authors.ifBlank { "نامشخص" })
        appendLine("سال: " + (year?.toString() ?: "نامشخص"))
        appendLine("نشریه: " + journal.ifBlank { "نامشخص" })
        appendLine("ناشر: " + publisher.ifBlank { "نامشخص" })
        appendLine("نوع سند: $publicationType")
        appendLine("منابع فراداده: " + sources.joinToString(", ").ifBlank { "نامشخص" })
        appendLine("استنادهای گزارش‌شده به تفکیک منبع: " + citationCounts.entries.joinToString("، ") { "${it.key}: ${it.value}" }.ifBlank { "ثبت نشده" })
        appendLine("میانگین خام استناد سالانه (بر اساس بیشینه شمارش منبع، نه مجموع منابع): " + String.format(Locale.US, "%.1f", annualRate))
        appendLine("وضعیت دسترسی: $accessStatus")
        appendLine("DOI: " + doi.ifBlank { "ثبت نشده" })
        appendLine("لینک صفحه مقاله: " + landingUrl.ifBlank { "ثبت نشده" })
        appendLine("نسخه دسترسی‌آزاد: " + pdfUrl.ifBlank { "در فراداده منابع پیدا نشد" })
        appendLine("چکیده: " + abstractText.ifBlank { "در فراداده موجود نیست" })
        appendLine("توجه: تعداد استناد نه کیفیت مقاله است و نه رتبه Q مجله. وضعیت دسترسی نامشخص نیز به معنی قطعی paywall نیست.")
    }
}
