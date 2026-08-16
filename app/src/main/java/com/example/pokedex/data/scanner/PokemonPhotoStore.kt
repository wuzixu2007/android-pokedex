package com.example.pokedex.data.scanner

import com.example.pokedex.domain.scanner.*

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class PokemonPhoto(val id: String, val pokemonKey: String, val file: File, val createdAt: Long)

class PokemonPhotoStore(context: Context) {
    private val root = File(context.applicationContext.filesDir, "pokemon-gallery")

    suspend fun save(pokemonKey: String, jpeg: ByteArray): PokemonPhoto = withContext(Dispatchers.IO) {
        require(pokemonKey.isNotBlank() && jpeg.isNotEmpty())
        root.mkdirs()
        val id = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()
        val image = File(root, "$id.jpg")
        writeAtomic(image, jpeg)
        writeAtomic(File(root, "$id.json"), JSONObject().put("id", id).put("pokemonKey", pokemonKey).put("createdAt", createdAt).toString().toByteArray())
        PokemonPhoto(id, pokemonKey, image, createdAt)
    }

    suspend fun list(pokemonKey: String): List<PokemonPhoto> = withContext(Dispatchers.IO) {
        root.listFiles { file -> file.extension == "json" }?.mapNotNull { file -> runCatching {
            val json = JSONObject(file.readText())
            val id = json.getString("id")
            val image = File(root, "$id.jpg")
            if (json.getString("pokemonKey") == pokemonKey && image.isFile && image.length() > 0) PokemonPhoto(id, pokemonKey, image, json.optLong("createdAt")) else null
        }.getOrNull() }?.sortedByDescending { it.createdAt }.orEmpty()
    }

    suspend fun delete(photo: PokemonPhoto): Boolean = withContext(Dispatchers.IO) {
        if (photo.file.parentFile?.canonicalFile != root.canonicalFile) return@withContext false
        val removed = photo.file.delete()
        File(root, "${photo.id}.json").delete()
        removed
    }

    private fun writeAtomic(destination: File, bytes: ByteArray) {
        val partial = File(destination.parentFile, "${destination.name}.part")
        FileOutputStream(partial).use { it.write(bytes); it.fd.sync() }
        if (!partial.renameTo(destination)) {
            destination.delete()
            check(partial.renameTo(destination))
        }
    }
}
