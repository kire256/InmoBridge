package com.droidforge.inmobridge.phone

import com.droidforge.inmobridge.core.CardSpec
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiCompatProviderTest {

    private fun resp(content: String): JSONObject = JSONObject()
        .put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", content))))

    @Test
    fun `short reply maps to card lines`() {
        val card = OpenAiCompatProvider.formatResponse(resp("Tokyo: 22C, clear\nWind: light"))
        assertEquals("AI", card.title)
        assertEquals(listOf("Tokyo: 22C, clear", "Wind: light"), card.body)
        assertEquals(CardSpec.STYLE_AI, card.style)
    }

    @Test
    fun `long lines are chunked to hud width`() {
        val card = OpenAiCompatProvider.formatResponse(
            resp("This is a very long answer line that definitely exceeds the forty four character limit set for the HUD")
        )
        assertTrue(card.body.size in 2..4)
        assertTrue(card.body.all { it.length <= 44 })
    }

    @Test
    fun `more than four lines truncates`() {
        val card = OpenAiCompatProvider.formatResponse(resp("one\ntwo\nthree\nfour\nfive\nsix"))
        assertEquals(4, card.body.size)
    }

    @Test
    fun `empty content is a warn card`() {
        val card = OpenAiCompatProvider.formatResponse(resp("  "))
        assertEquals(CardSpec.STYLE_WARN, card.style)
    }
}
