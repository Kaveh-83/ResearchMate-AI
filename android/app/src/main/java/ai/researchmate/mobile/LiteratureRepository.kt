package ai.researchmate.mobile

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
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
)

data class LiteratureSearchResponse(
    val works: List<LiteratureWork>,
    val totalCount: Int,
)

object LiteratureRepository {
    suspend fun search(
        baseUrl: String,
        query: String,
        yearFrom: Int,
        yearTo: Int,
    ): LiteratureSearchResponse = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
        val endpoint = "$baseUrl/v1/literature/search?q=$encodedQuery&year_from=$yearFrom&year_to=$yearTo&limit=25"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("Accept", "application/json")
        }

        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(body)

            if (status !in 200..299) {
                val upstreamMessage = root.optJSONObject("error")?.optString("message")
                    ?.takeIf { !it.isNullOrBlank() }
                    ?: "سرور با خطای HTTP $status پاسخ داد."
                val message = when {
                    upstreamMessage.contains("OpenAlex anonymous rate limit", ignoreCase = true) ->
                        "سرویس جست‌وجو هنوز به کلید دسترسی OpenAlex متصل نشده است. مدیر برنامه باید تنظیمات سرور را تکمیل کند."
                    upstreamMessage.contains("OpenAlex rate limit reached", ignoreCase = true) ->
                        "سهمیه جست‌وجوی OpenAlex موقتاً محدود شده است. لطفاً کمی بعد دوباره تلاش کن."
                    upstreamMessage.contains("Search limit reached", ignoreCase = true) ->
                        "تعداد جست‌وجوها در یک دقیقه به سقف مجاز رسیده است. یک دقیقه بعد دوباره تلاش کن."
                    else -> upstreamMessage
                }
                throw IllegalStateException(message)
            }

            val results = root.optJSONArray("results")
            val works = buildList {
                if (results != null) {
                    for (index in 0 until results.length()) {
                        val item = results.optJSONObject(index) ?: continue
                        val authorships = item.optJSONArray("authorships")
                        val authors = buildList {
                            if (authorships != null) {
                                for (authorIndex in 0 until minOf(authorships.length(), 5)) {
                                    val author = authorships.optJSONObject(authorIndex)
                                        ?.optJSONObject("author")
                                        ?.optString("display_name")
                                        ?.takeIf { !it.isNullOrBlank() }
                                    if (author != null) add(author)
                                }
                            }
                        }.joinToString(", ")

                        val bestLocation = item.optJSONObject("best_oa_location")
                        val primaryLocation = item.optJSONObject("primary_location")
                        val source = primaryLocation?.optJSONObject("source")
                        val title = item.optString("title").ifBlank { "عنوان در فراداده موجود نیست" }

                        add(
                            LiteratureWork(
                                id = item.optString("id").ifBlank { "work-$index" },
                                title = title,
                                authors = authors,
                                year = item.optInt("publication_year").takeIf {
                                    !item.isNull("publication_year") && it > 0
                                },
                                journal = source?.optString("display_name").orEmpty(),
                                citationCount = item.optInt("cited_by_count", 0),
                                doi = item.optString("doi").orEmpty(),
                                pdfUrl = bestLocation?.optString("pdf_url").orEmpty(),
                            ),
                        )
                    }
                }
            }

            LiteratureSearchResponse(
                works = works,
                totalCount = root.optJSONObject("meta")?.optInt("count", works.size) ?: works.size,
            )
        } finally {
            connection.disconnect()
        }
    }
}
