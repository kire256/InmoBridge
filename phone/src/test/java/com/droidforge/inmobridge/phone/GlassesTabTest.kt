package com.droidforge.inmobridge.phone

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the Glasses tab: the tools card (test notification + APK install)
 * must exist in the content column of the main screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class GlassesTabTest {

    private fun findAll(root: View, out: ArrayList<View>) {
        out.add(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) findAll(root.getChildAt(i), out)
        }
    }

    @Test
    fun glassesTabShowsToolsCard() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.get()
        controller.create().visible()

        // walk the decor to find the ScrollView (content host)
        val all = ArrayList<View>()
        findAll(activity.window.decorView, all)
        val scrolls = all.filterIsInstance<ScrollView>()
        assertTrue("no ScrollView in hierarchy", scrolls.isNotEmpty())
        val content = scrolls[0]
        assertTrue("content column has no children", content.childCount > 0)

        val texts = ArrayList<String>()
        all.clear(); findAll(content, all)
        all.forEach { v ->
            (v as? android.widget.TextView)?.let { texts.add(it.text.toString()) }
        }
        assertTrue(
            "test notification button missing; texts=$texts",
            texts.any { it.contains("Send test notification", ignoreCase = true) },
        )
        assertTrue(
            "APK install button missing",
            texts.any { it.contains("Pick APK", ignoreCase = true) },
        )
    }
}
