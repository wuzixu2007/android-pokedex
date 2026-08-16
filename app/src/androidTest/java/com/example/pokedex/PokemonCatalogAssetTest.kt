/* Packaged catalog integrity tests. / APK 图鉴资源完整性测试。 */
package com.example.pokedex

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pokedex.data.scanner.PokemonCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PokemonCatalogAssetTest {
    @Test
    fun generatedCatalogContainsSpeechDataForEveryRecord() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = PokemonCatalog.load(context)

        assertEquals(1318, catalog.records.size)
        assertTrue(catalog.records.all { it.attributeLabel.isNotBlank() })
        assertTrue(catalog.records.all { it.profile.isNotBlank() })
        assertEquals(
            "恶属性和一般属性宝可梦",
            catalog.findExact("小拉达-阿罗拉的样子")?.attributeLabel,
        )
    }
}
