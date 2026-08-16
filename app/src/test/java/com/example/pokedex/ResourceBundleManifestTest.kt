package com.example.pokedex

import com.example.pokedex.data.scanner.ResourceBundleManifest
import com.example.pokedex.data.scanner.ResourceFileEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class ResourceBundleManifestTest {
    @Test
    fun manifest_roundTrip_preservesBundleAndFileIntegrityMetadata() {
        val manifest = ResourceBundleManifest(
            version = "1",
            bundleUrl = "https://example.com/pokedex/pokedex-resources-v1.zip",
            bundleSizeBytes = 1234,
            bundleSha256 = "a".repeat(64),
            files = listOf(ResourceFileEntry("assets/pokemon/catalog.json", 100, "b".repeat(64))),
        )

        assertEquals(manifest, ResourceBundleManifest.fromJson(manifest.toJson().toString()))
    }
}
