package ai.researchmate.mobile

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ScientificDocumentPackageTest {
    private fun validPackage(): String = """
        {
          "schema_version":"1.0",
          "document":{"title":"Steel Research","subtitle":"A test","language":"en","citation_style":"APA 7"},
          "manuscript":{"abstract":"Abstract text","keywords":["steel","ML"],"sections":[{"heading":"Introduction","level":1,"content":"Initial text"}]},
          "tables":[{"id":"t1","title":"Data","headers":["A","B"],"rows":[["1","2"]],"source":"Test source"}],
          "charts":[{"id":"c1","title":"Trend","type":"line","x_label":"Year","y_label":"Value","categories":["2020","2021"],"series":[{"name":"Yield","values":[1.0,2.0]}],"source":"Test source"}],
          "figures":[{"id":"fig1","caption":"Schematic","description":"A schematic","source":"AI schematic"}],
          "equations":[{"label":"Eq. 1","latex":"x=1","plain_text":"x equals one"}],
          "references":[{"text":"Author (2025). Example."}]
        }
    """.trimIndent()

    @Test fun markdownEditorPreservesStructuredAssets() {
        val base = validPackage()
        val updated = ScientificDocumentPackage.fromEditor(
            "Updated title", "Updated abstract", "steel, metallurgy, AI",
            "# Background\nText with **emphasis**.\n\n## Methods\nA second section.",
            base
        )
        val report = ScientificDocumentPackage.validate(updated)
        assertTrue(report.errors.joinToString(), report.valid)
        assertEquals(2, report.sectionCount)
        assertEquals(1, report.tableCount)
        assertEquals(1, report.chartCount)
        assertEquals(1, report.figureCount)
        assertEquals(1, report.referenceCount)
        val root = JSONObject(updated)
        assertEquals("Updated title", root.getJSONObject("document").getString("title"))
        assertEquals("Updated abstract", root.getJSONObject("manuscript").getString("abstract"))
        assertEquals("t1", root.getJSONArray("tables").getJSONObject(0).getString("id"))
    }

    @Test fun markdownHeadingsBecomeSeparateSections() {
        val updated = ScientificDocumentPackage.fromEditor(
            "Title", "Abstract", "steel",
            "# Intro\nFirst paragraph.\n\n## Methods\nSecond paragraph.",
            validPackage()
        )
        val sections = JSONObject(updated).getJSONObject("manuscript").getJSONArray("sections")
        assertEquals("Intro", sections.getJSONObject(0).getString("heading"))
        assertEquals(1, sections.getJSONObject(0).getInt("level"))
        assertTrue(sections.getJSONObject(0).getString("content").contains("First paragraph"))
        assertEquals("Methods", sections.getJSONObject(1).getString("heading"))
        assertEquals(2, sections.getJSONObject(1).getInt("level"))
    }

    @Test fun invalidTableIsRejectedWithoutLosingSourceText() {
        val broken = validPackage().replace("[\"1\",\"2\"]", "[\"1\"]")
        val report = ScientificDocumentPackage.validate(broken)
        assertFalse(report.valid)
        assertTrue(report.errors.any { it.contains("تعداد سلول‌ها") })
        assertTrue(broken.contains("Initial text"))
    }

    @Test fun fencedJsonAndTrailingCommaCanBeRepaired() {
        val raw = "\u0060\u0060\u0060json\n" + validPackage().replace("\"ML\"]", "\"ML\",]") + "\n\u0060\u0060\u0060"
        val repaired = ScientificDocumentPackage.repairFormatting(raw)
        assertTrue(ScientificDocumentPackage.validate(repaired).valid)
    }

    @Test fun realSteelMlReviewFixturePassesValidationAndHtmlRendering() {
        val input = javaClass.getResourceAsStream("/steel_ml_mini_review.json")
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("Sample scientific review fixture is missing")
        val report = ScientificDocumentPackage.validate(input)
        assertTrue(report.errors.joinToString(), report.valid)
        assertEquals(1, report.tableCount)
        assertEquals(1, report.chartCount)
        assertEquals(1, report.figureCount)
        assertEquals(7, report.referenceCount)

        val html = ScientificHtmlPackageBuilder.build(input)
        assertTrue(html.contains("<svg"))
        assertTrue(html.contains("CatBoost"))
        assertTrue(html.contains("منابع"))
        assertTrue(html.contains("جدول"))
        assertFalse(html.contains("<script"))
        assertTrue(html.contains("figure-placeholder"))
    }

    @Test fun markdownTablesRenderAsNativeHtmlAndRawHtmlIsEscaped() {
        val input = ScientificDocumentPackage.fromEditor(
            "Title", "Abstract", "steel",
            "# Results\n\n| Model | R2 |\n| --- | --- |\n| CatBoost | 0.818 |\n\n<script>alert(1)</script>",
            validPackage()
        )
        val html = ScientificHtmlPackageBuilder.build(input)
        assertTrue(html.contains("<th>Model</th>"))
        assertTrue(html.contains("<td>CatBoost</td>"))
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
    }

    @Test fun unsafeRemoteImageUriIsNotRenderedAsAnImage() {
        val packageWithRemoteImage = validPackage().replace(
            "\"source\":\"AI schematic\"",
            "\"source\":\"AI schematic\",\"image_uri\":\"https://example.com/pixel.png\""
        )
        val html = ScientificHtmlPackageBuilder.build(packageWithRemoteImage)
        assertFalse(html.contains("https://example.com/pixel.png"))
        assertTrue(html.contains("figure-placeholder"))
    }

    @Test fun attachingImageRequiresExistingFigureId() {
        val attached = ScientificDocumentPackage.attachImage(validPackage(), "fig1", "content://media/external/images/1")
        assertTrue(ScientificDocumentPackage.hasImage(attached, "fig1"))
        assertFalse(ScientificDocumentPackage.hasImage(attached, "missing"))
    }
}