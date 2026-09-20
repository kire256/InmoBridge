package com.droidforge.inmobridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentRouterTest {

    private val router = IntentRouter()

    @Test
    fun `wake word with comma routes local intent`() {
        val r = router.route("Inmo, navigate home")
        assertEquals(IntentRouter.Routing.Local("navigate_home"), r)
    }

    @Test
    fun `bare wake word is not addressed`() {
        assertEquals(IntentRouter.Routing.NotAddressed, router.route("inmo"))
    }

    @Test
    fun `no wake word is not addressed`() {
        assertEquals(IntentRouter.Routing.NotAddressed, router.route("navigate home"))
        assertEquals(IntentRouter.Routing.NotAddressed, router.route("what's the weather in tokyo"))
    }

    @Test
    fun `open ended goes to ai`() {
        val r = router.route("inmo, what's the weather in tokyo")
        assertTrue(r is IntentRouter.Routing.Ai)
        assertEquals("what's the weather in tokyo", (r as IntentRouter.Routing.Ai).prompt)
    }

    @Test
    fun `prefix variant of local intent still matches`() {
        val r = router.route("inmo open email now please")
        assertEquals(IntentRouter.Routing.Local("open_email"), r)
    }

    @Test
    fun `time variants match`() {
        assertEquals(IntentRouter.Routing.Local("what_time"), router.route("inmo what time is it"))
    }
}
