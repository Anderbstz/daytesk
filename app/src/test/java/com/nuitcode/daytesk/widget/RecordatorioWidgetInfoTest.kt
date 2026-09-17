package com.nuitcode.daytesk.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Strict TDD — RED test written before the 1x2 footprint change.
 *
 * Contract (see sdd/recordatorios-ux/spec, domain `home-widget`):
 *   - Footprint is 1x2    -> targetCellWidth=2, targetCellHeight=1 (API 31+)
 *   - Pre-API-31 fallback -> minWidth/minHeight reserve 2 columns x 1 row
 *
 * The fallback uses the documented cells-to-dp mapping `(n * 70) - 30`:
 *   2 columns -> 110dp, 1 row -> 40dp.
 *
 * Implementation note: this module does not enable Robolectric's
 * `includeAndroidResources`, so `context.resources.getXml(...)` cannot resolve
 * app resources. The test therefore parses the real production
 * `recordatorio_widget_info.xml` straight off the source tree with the JDK XML
 * parser — same assertions, no build-config change.
 */
class RecordatorioWidgetInfoTest {

    @Test
    fun targetCells_describeTwoColumnsByOneRow() {
        val attrs = widgetInfoAttributes()

        assertEquals("2", attrs["android:targetCellWidth"])
        assertEquals("1", attrs["android:targetCellHeight"])
    }

    @Test
    fun preApi31Fallback_reservesTwoColumnsByOneRow() {
        val attrs = widgetInfoAttributes()

        // (2 * 70) - 30 = 110dp ; (1 * 70) - 30 = 40dp
        assertEquals("110dp", attrs["android:minWidth"])
        assertEquals("40dp", attrs["android:minHeight"])
    }

    private fun widgetInfoAttributes(): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(widgetInfoFile())
            .documentElement as Element

        val attrs = mutableMapOf<String, String>()
        for (index in 0 until root.attributes.length) {
            val attr = root.attributes.item(index)
            attrs[attr.nodeName] = attr.nodeValue.orEmpty()
        }
        return attrs
    }

    /** Resolves the XML from either the module or the repo root working dir. */
    private fun widgetInfoFile(): File {
        val relative = "src/main/res/xml/recordatorio_widget_info.xml"
        var dir: File? = File(System.getProperty("user.dir"))
        repeat(4) {
            val base = dir ?: return@repeat
            val candidates = listOf(File(base, relative), File(base, "app/$relative"))
            candidates.firstOrNull { it.isFile }?.let { return it }
            dir = base.parentFile
        }
        error("recordatorio_widget_info.xml not found from ${System.getProperty("user.dir")}")
    }
}
