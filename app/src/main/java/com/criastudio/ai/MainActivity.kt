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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Presentation
import java.io.File

private val Ink = Color(0xFF101116)
private val Panel = Color(0xFF1B1D25)
private val Orange = Color(0xFFFF6B2C)
private val Muted = Color(0xFFA6A8B4)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(16, 17, 22)
        window.navigationBarColor = android.graphics.Color.rgb(16, 17, 22)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Orange, background = Ink, surface = Panel)) {
                StudioApp()
            }
        }
    }

    @Composable
    private fun StudioApp() {
        val context = LocalContext.current
        var selectedUri by remember { mutableStateOf<Uri?>(null) }
        var selectedName by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("Escolha um vídeo para começar.") }
        var exporting by remember { mutableStateOf(false) }
        var titleText by remember { mutableStateOf("Meu novo vídeo") }
        var ratio by remember { mutableStateOf("9:16") }

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                selectedUri = uri
                selectedName = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "Vídeo selecionado"
                status = "Vídeo carregado. Agora você pode exportar."
            }
        }

        Column(
            Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState())
                .imePadding().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Orange, Color(0xFFFF9B52))),),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MovieFilter, null, tint = Color.White, modifier = Modifier.size(27.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("CRIA STUDIO", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 1.sp)
                    Text("Editor de vídeo", color = Muted, fontSize = 12.sp)
                }
                Icon(Icons.Rounded.AutoAwesome, null, tint = Orange, modifier = Modifier.size(25.dp))
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (selectedUri == null) "Vamos criar seu vídeo" else "Vídeo pronto para editar", color = Color.White, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
                Text(if (selectedUri == null) "Escolha um arquivo da galeria para começar." else "Seu arquivo foi selecionado. Ajuste as opções abaixo e exporte.", color = Muted, fontSize = 14.sp)
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF34233B), Color(0xFF182B35), Color(0xFF24212B))))
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x33202028)), contentAlignment = Alignment.Center) {
                    Icon(if (selectedUri == null) Icons.Rounded.VideoLibrary else Icons.Rounded.CheckCircle,
                        null, tint = Orange, modifier = Modifier.size(54.dp))
                }
                Text(if (selectedUri == null) "Nenhum vídeo selecionado" else "Vídeo selecionado ✓",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                if (selectedUri != null) {
                    Text(selectedName, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = { picker.launch(arrayOf("video/*")) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange)
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (selectedUri == null) "Escolher vídeo da galeria" else "Trocar vídeo", fontWeight = FontWeight.Bold)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("FORMATO DO VÍDEO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("9:16", "1:1", "16:9").forEach { value ->
                        FilterChip(
                            selected = ratio == value,
                            onClick = { ratio = value },
                            label = { Text(value) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text("Formato selecionado: $ratio", color = Muted, fontSize = 12.sp)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("TEXTO DO PROJETO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Nome do vídeo") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            }

            Button(
                onClick = {
                    val uri = selectedUri
                    if (uri == null) {
                        status = "Toque em “Escolher vídeo da galeria” primeiro."
                        Toast.makeText(this@MainActivity, status, Toast.LENGTH_SHORT).show()
                    } else if (!exporting) {
                        exporting = true
                        status = "Exportando vídeo… não feche o aplicativo."
                        exportVideo(uri, titleText, ratio) { _, msg ->
                            exporting = false
                            status = msg
                            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange, disabledContainerColor = Color(0xFF6F493B))
            ) {
                if (exporting) CircularProgressIndicator(Modifier.size(21.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(Icons.Rounded.FileDownload, null)
                Spacer(Modifier.width(9.dp))
                Text(if (exporting) "Exportando vídeo…" else "Exportar vídeo MP4", fontWeight = FontWeight.Bold)
            }

            Surface(color = Panel, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Icon(if (exporting) Icons.Rounded.Sync else Icons.Rounded.Info, null, tint = Orange)
                    Text(status, color = Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
                }
            }
            Text("Cria Studio AI • versão inicial", color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }

    private fun exportVideo(uri: Uri, projectTitle: String, ratio: String, done: (Boolean, String) -> Unit) {
        val output = File(cacheDir, "criastudio_${System.currentTimeMillis()}.mp4")
        val targetAspectRatio = when (ratio) {
            "9:16" -> 9f / 16f
            "1:1" -> 1f
            else -> 16f / 9f
        }
        val presentation = Presentation.createForAspectRatio(
            targetAspectRatio,
            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
        )
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(uri))
            .setEffects(Effects(emptyList(), listOf(presentation)))
            .build()
        try {
            val transformer = Transformer.Builder(this).addListener(object : Transformer.Listener {
                override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                    try {
                        if (!output.exists() || output.length() == 0L) {
                            runOnUiThread { done(false, "A exportação terminou, mas o arquivo ficou vazio. Tente outro vídeo.") }
                            return
                        }
                        val safeTitle = projectTitle.trim().replace(Regex("[^A-Za-z0-9_-]"), "_").take(40).ifBlank { "Video" }
                        val values = ContentValues().apply {
                            put(MediaStore.Video.Media.DISPLAY_NAME, "CriaStudio_${safeTitle}_${System.currentTimeMillis()}.mp4")
                            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CriaStudio")
                        }
                        val dest = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                        if (dest == null) {
                            runOnUiThread { done(false, "Não foi possível criar o arquivo na galeria.") }
                            return
                        }
                        val target = contentResolver.openOutputStream(dest)
                        if (target == null) {
                            contentResolver.delete(dest, null, null)
                            runOnUiThread { done(false, "Não foi possível gravar na galeria.") }
                            return
                        }
                        target.use { out -> output.inputStream().use { input -> input.copyTo(out) } }
                        runOnUiThread { done(true, "Pronto! Salvo em Filmes/CriaStudio.") }
                    } catch (e: Exception) {
                        runOnUiThread { done(false, "Falha ao salvar vídeo: ${e.localizedMessage ?: "erro desconhecido"}") }
                    } finally {
                        output.delete()
                    }
                }

                override fun onError(composition: androidx.media3.transformer.Composition, exportResult: ExportResult, exportException: ExportException) {
                    output.delete()
                    runOnUiThread { done(false, "Não foi possível exportar este vídeo: ${exportException.localizedMessage ?: "formato não suportado"}") }
                }
            }).build()
            transformer.start(edited, output.absolutePath)
        } catch (e: Exception) {
            output.delete()
            done(false, "Erro ao iniciar exportação: ${e.localizedMessage ?: "tente novamente"}")
        }
    }
}
