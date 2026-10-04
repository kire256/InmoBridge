package com.droidforge.inmobridge.phone

import android.content.Context

/** Remembers every package that has posted a notification (for the Apps tab). */
object NotifSources {
    private const val PREFS = "sources"
    private const val KEY = "pkgs"

    fun observe(ctx: Context, pkg: String) {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = sp.getStringSet(KEY, emptySet()).orEmpty()
        if (pkg !in current) {
            sp.edit().putStringSet(KEY, current + pkg).apply()
        }
    }

    fun observed(ctx: Context): Set<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()).orEmpty()
}
