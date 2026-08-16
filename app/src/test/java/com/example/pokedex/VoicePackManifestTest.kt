package com.example.pokedex

import com.example.pokedex.data.scanner.VoicePackId
import com.example.pokedex.data.scanner.VoicePackManifest
import com.example.pokedex.data.scanner.safeRelativePath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VoicePackManifestTest {
    @Test
    fun parsesAndSerializesManifest() {
        val json = """
            {"voiceId":"original","revision":1,"contentVersion":"v1","minAppVersion":2,
             "bundle":{"url":"https://example.test/original.zip","sizeBytes":10,"sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},
             "files":[{"path":"p0001_demo.aac","sizeBytes":10,"sha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}]}
        """.trimIndent()
        val manifest = VoicePackManifest.fromJson(json)
        assertEquals(VoicePackId.Original, manifest.voiceId)
        assertEquals(1L, manifest.revision)
        assertEquals("p0001_demo.aac", manifest.files.single().path)
        assertEquals(manifest.revision, VoicePackManifest.fromJson(manifest.toJson().toString()).revision)
    }

    @Test
    fun rejectsUnsafeArchivePaths() {
        assertThrows(IllegalArgumentException::class.java) { safeRelativePath("../escape.aac") }
        assertThrows(IllegalArgumentException::class.java) { safeRelativePath("/absolute.aac") }
        assertEquals("nested/file.aac", safeRelativePath("nested/file.aac"))
    }
}
