package com.criastudio.ai

import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import java.io.File

private val Ink = Color(0xFF101116)
private val Panel = Color(0xFF1B1D25)
private val Orange = Color(0xFFFF6B2C)
private val Muted = Color(0xFFA6A8B4)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Orange, background = Ink, surface = Panel)) { StudioApp() } }
    }

    @Composable
    private fun StudioApp() {
        var selectedUri by remember { mutableStateOf<Uri?>(null) }
        var status by remember { mutableStateOf("Escolha um vídeo para começar") }
        var exporting by remember { mutableStateOf(false) }
        var titleText by remember { mutableStateOf("Seu próximo Reel começa aqui") }
        var ratio by remember { mutableStateOf("9:16") }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) { selectedUri = uri; status = "Vídeo selecionado. Pronto para exportar." }
        }
        Column(Modifier.fillMaxSize().background(Ink).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(Orange, Color(0xFFFF9B52)))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MovieFilter, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("CRIA STUDIO", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 1.sp)
                    Text("IA criativa • vídeo e publicações", color = Muted, fontSize = 12.sp)
                }
                Icon(Icons.Rounded.AutoAwesome, null, tint = Orange, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text("Transforme ideias em conteúdo", color = Color.White, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
            Text("Edite, crie e exporte sem complicação.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(18.dp))
            Column(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF34233B), Color(0xFF182B35), Color(0xFF24212B)))).clickable { picker.launch("video/*") }.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Rounded.AddCircle, null, tint = Orange, modifier = Modifier.size(46.dp))
                Spacer(Modifier.height(8.dp))
                Text(if (selectedUri == null) "Adicionar vídeo" else "Vídeo selecionado", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Toque para escolher da galeria", color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(18.dp))
            Text("Formato da publicação", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("9:16", "1:1", "16:9").forEach { value ->
                    FilterChip(selected = ratio == value, onClick = { ratio = value }, label = { Text(value) })
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = titleText, onValueChange = { titleText = it }, label = { Text("Texto/gancho para o vídeo") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                val uri = selectedUri
                if (uri == null) { status = "Primeiro, adicione um vídeo da galeria."; return@Button }
                exporting = true; status = "Exportando vídeo…"; exportVideo(uri) { ok, msg ->
                    exporting = false; status = msg
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                }
            }, enabled = selectedUri != null && !exporting, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Orange)) {
                if (exporting) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Icon(Icons.Rounded.FileDownload, null)
                Spacer(Modifier.width(8.dp)); Text(if (exporting) "Exportando…" else "Exportar vídeo MP4", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Text(status, color = Muted, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
                BottomItem(Icons.Rounded.Home, "Início", true); BottomItem(Icons.Rounded.VideoLibrary, "Projetos", false)
                BottomItem(Icons.Rounded.AutoAwesome, "IA", false); BottomItem(Icons.Rounded.Person, "Perfil", false)
            }
        }
    }

    private fun exportVideo(uri: Uri, done: (Boolean, String) -> Unit) {
        val output = File(cacheDir, "criastudio_${System.currentTimeMillis()}.mp4")
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(uri)).build()
        val transformer = Transformer.Builder(this).addListener(object : Transformer.Listener {
            override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, "CriaStudio_${System.currentTimeMillis()}.mp4")
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CriaStudio")
                    }
                    val dest = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    if (dest == null) { runOnUiThread { done(false, "Não foi possível salvar o vídeo.") }; return }
                    contentResolver.openOutputStream(dest)?.use { out -> output.inputStream().use { it.copyTo(out) } }
                    runOnUiThread { done(true, "Vídeo salvo em Filmes/CriaStudio.") }
                } catch (e: Exception) { runOnUiThread { done(false, "Falha ao salvar: ${e.message}") } }
            }
            override fun onError(composition: androidx.media3.transformer.Composition, exportResult: ExportResult, exportException: ExportException) {
                runOnUiThread { done(false, "Erro ao exportar: ${exportException.message}") }
            }
        }).build()
        transformer.start(edited, output.absolutePath)
    }
}

@Composable
private fun BottomItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = if (active) Orange else Muted)
        Text(label, color = if (active) Orange else Muted, fontSize = 10.sp)
    }
}
