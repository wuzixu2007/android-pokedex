package com.example.pokedex.presentation.scanner

import com.example.pokedex.data.scanner.*
import com.example.pokedex.domain.scanner.*

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.pokedex.ui.theme.ScannerBorder
import com.example.pokedex.ui.theme.ScannerGraphiteLight
import com.example.pokedex.ui.theme.ScannerOutline
import com.example.pokedex.ui.theme.ScannerPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun PokemonGalleryPage(pokemon: PokemonRecord, store: PokemonPhotoStore, onBack: () -> Unit) {
    var refresh by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<PokemonPhoto?>(null) }
    var deleteTarget by remember { mutableStateOf<PokemonPhoto?>(null) }
    val scope = rememberCoroutineScope()
    val photos by produceState<List<PokemonPhoto>>(emptyList(), pokemon.key, refresh) {
        value = withContext(Dispatchers.IO) { store.list(pokemon.key) }
    }
    Box(Modifier.fillMaxSize().padding(10.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(ScannerPanel).border(4.dp, Color.Black, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(12.dp)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${pokemon.nameZh} 的图库", fontWeight = FontWeight.Black, fontSize = 17.sp, color = ScannerOutline)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("返回") }
            }
            if (photos.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("还没有识别照片", color = ScannerGraphiteLight) }
            } else {
                LazyVerticalGrid(columns = GridCells.Adaptive(120.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                    items(photos, key = { it.id }) { photo -> GalleryPhotoCard(photo) { selected = photo } }
                }
            }
        }
    }
    selected?.let { photo -> PhotoPreviewDialog(photo, onDismiss = { selected = null }, onDelete = { selected = null; deleteTarget = photo }) }
    deleteTarget?.let { photo ->
        Dialog(onDismissRequest = { deleteTarget = null }) {
            Column(Modifier.fillMaxWidth().background(Color.White, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(20.dp)) {
                Text("删除照片？", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text("删除后无法恢复。", color = Color.DarkGray, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { deleteTarget = null }) { Text("取消") }
                    Button(onClick = { scope.launch { store.delete(photo); deleteTarget = null; refresh++ } }) { Text("删除") }
                }
            }
        }
    }
}

@Composable
private fun GalleryPhotoCard(photo: PokemonPhoto, onClick: () -> Unit) {
    val bitmap = remember(photo.file.path) { BitmapFactory.decodeFile(photo.file.path) }
    Column(Modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().size(120.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).border(3.dp, Color.Black, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))) {
            bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Text(formatPhotoTime(photo.createdAt), color = ScannerGraphiteLight, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun PhotoPreviewDialog(photo: PokemonPhoto, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val bitmap = remember(photo.file.path) { BitmapFactory.decodeFile(photo.file.path) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(ScannerPanel, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).border(4.dp, ScannerBorder, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(16.dp)) {
            bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().height(380.dp), contentScale = ContentScale.Fit) }
            Text(formatPhotoTime(photo.createdAt), color = ScannerGraphiteLight, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("关闭") }
                Button(onClick = onDelete) { Text("删除") }
            }
        }
    }
}

private fun formatPhotoTime(time: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))
