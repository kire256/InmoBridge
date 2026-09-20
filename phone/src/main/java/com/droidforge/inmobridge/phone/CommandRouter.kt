package com.droidforge.inmobridge.phone

import com.droidforge.inmobridge.core.BridgeMessage
import com.droidforge.inmobridge.core.CardSpec
import com.droidforge.inmobridge.core.Envelope
import com.droidforge.inmobridge.core.IntentRouter
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Routes intents from the glasses: local deterministic actions run instantly,
 * open-ended prompts go to the pluggable AI provider. Pure JVM — testable
 * without Android.
 */
class CommandRouter(
    private val aiProvider: AiProvider,
    private val now: () -> Long = System::currentTimeMillis,
    private val timeZone: java.util.TimeZone = java.util.TimeZone.getDefault(),
) {

    interface AiProvider {
        /** Blocking call; run on a worker thread. Returns a HUD-ready card. */
        fun ask(prompt: String): CardSpec
    }

    private val router = IntentRouter()

    fun handle(env: Envelope, send: (Envelope) -> Unit) {
        if (env.type != BridgeMessage.TYPE_INTENT) return
        val text = env.payload.optString("text")
        when (val r = router.route(text)) {
            is IntentRouter.Routing.Local -> send(localReply(env.id, r))
            is IntentRouter.Routing.Ai -> {
                Thread {
                    val card = runCatching { aiProvider.ask(r.prompt) }
                    .getOrElse {
                        CardSpec("AI error", listOf(it.message ?: "unavailable"), style = CardSpec.STYLE_WARN)
                    }
                    send(BridgeMessage.reply(env.id, card, env.id + 1_000_000))
                }.start()
            }
            IntentRouter.Routing.NotAddressed -> Unit
        }
    }

    private fun localReply(replyTo: Long, r: IntentRouter.Routing.Local): Envelope {
        val card = when (r.intentId) {
            "what_time" -> {
                val fmt = SimpleDateFormat("h:mm a")
                fmt.timeZone = timeZone
                CardSpec("Time", listOf(fmt.format(Date(now()))))
            }
            "status_battery" -> CardSpec("Battery", listOf("Phone: open phone app"))
            else -> CardSpec(r.intentId.replace('_', ' '), listOf("Executing on phone…"))
        }
        return BridgeMessage.reply(replyTo, card, replyTo + 1_000_000)
    }
}
