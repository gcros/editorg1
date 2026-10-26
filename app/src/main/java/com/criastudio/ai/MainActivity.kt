package com.criastudio.ai

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.effect.BitmapOverlay
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.concurrent.Executors

private val Ink = Color(0xFF101116)
private val Panel = Color(0xFF1B1D25)
private val Orange = Color(0xFFFF6B2C)
private val Muted = Color(0xFFA6A8B4)

@UnstableApi
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
        var selectedUri by remember { mutableStateOf<Uri?>(null) }
        var selectedName by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("Escolha um vídeo para começar.") }
        var exporting by remember { mutableStateOf(false) }
        var projectName by remember { mutableStateOf("Meu vídeo") }
        var ratio by remember { mutableStateOf("9:16") }
        var overlayText by remember { mutableStateOf("SEU TEXTO AQUI") }
        var textSize by remember { mutableFloatStateOf(54f) }
        var textY by remember { mutableFloatStateOf(0.72f) }
        var textColor by remember { mutableIntStateOf(android.graphics.Color.WHITE) }
        var durationMs by remember { mutableLongStateOf(0L) }
        var trimStartMs by remember { mutableLongStateOf(0L) }
        var trimEndMs by remember { mutableLongStateOf(0L) }

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                selectedUri = uri
                selectedName = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "Vídeo selecionado"
                durationMs = 0L
                trimStartMs = 0L
                trimEndMs = 0L
                status = "Vídeo selecionado. Confira o preview e ajuste sua edição."
            }
        }

        val player = remember(selectedUri) {
            selectedUri?.let { uri ->
                ExoPlayer.Builder(this).build().apply {
                    setMediaItem(MediaItem.fromUri(uri))
                    prepare()
                    playWhenReady = false
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) {
                                val d = duration.coerceAtLeast(0L)
                                durationMs = d
                                if (trimEndMs == 0L || trimEndMs > d) trimEndMs = d
                            }
                        }
                    })
                }
            }
        }
        DisposableEffect(player) { onDispose { player?.release() } }

        Column(
            Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState())
                .imePadding().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Orange, Color(0xFFFF9B52)))), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MovieFilter, null, tint = Color.White, modifier = Modifier.size(27.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("CRIA STUDIO AI", fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = .5.sp)
                    Text("Editor de Reels • Stories • Shorts", color = Muted, fontSize = 12.sp)
                }
                Icon(Icons.Rounded.AutoAwesome, null, tint = Orange, modifier = Modifier.size(25.dp))
            }

            Text(if (selectedUri == null) "Vamos criar algo incrível" else "Edite seu vídeo", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Escolha o vídeo, ajuste o corte, coloque seu gancho e exporte.", color = Muted, fontSize = 14.sp)

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 420.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                    if (player != null) {
                        AndroidView(
                            factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true; controllerAutoShow = true } },
                            update = { it.player = player },
                            modifier = Modifier.fillMaxWidth().aspectRatio(when (ratio) { "9:16" -> 9f/16f; "1:1" -> 1f; else -> 16f/9f }).heightIn(min = 220.dp, max = 420.dp)
                        )
                        if (overlayText.isNotBlank()) {
                            Text(overlayText, color = Color(textColor), fontSize = (textSize / 3.5f).sp, fontWeight = FontWeight.Black,
                                modifier = Modifier.align(if (textY < .4f) Alignment.TopCenter else if (textY > .65f) Alignment.BottomCenter else Alignment.Center)
                                    .padding(horizontal = 16.dp, vertical = 30.dp)
                                    .background(Color(0x55000000), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.VideoLibrary, null, tint = Orange, modifier = Modifier.size(48.dp))
                            Text("Seu preview aparece aqui", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Selecione um vídeo para começar", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
                Button(onClick = { picker.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Orange)) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (selectedUri == null) "Escolher vídeo da galeria" else "Trocar vídeo", fontWeight = FontWeight.Bold)
                }
                if (selectedUri != null) Text(selectedName, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            EditorCard("FORMATO DE SAÍDA", Icons.Rounded.Crop) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("9:16", "1:1", "16:9").forEach { value ->
                        FilterChip(selected = ratio == value, onClick = { ratio = value }, label = { Text(value) }, modifier = Modifier.weight(1f))
                    }
                }
                Text(when (ratio) { "9:16" -> "Vertical • Reels, Stories e TikTok"; "1:1" -> "Quadrado • Feed"; else -> "Horizontal • YouTube" }, color = Muted, fontSize = 12.sp)
            }

            EditorCard("CORTE DO VÍDEO", Icons.Rounded.ContentCut) {
                Text("Início: ${formatTime(trimStartMs)}", color = Color.White)
                Slider(value = trimStartMs.toFloat(), onValueChange = { trimStartMs = it.toLong().coerceAtMost((trimEndMs - 500).coerceAtLeast(0L)) }, valueRange = 0f..(durationMs.coerceAtLeast(1000L).toFloat()), enabled = durationMs > 0L)
                Text("Fim: ${formatTime(if (trimEndMs == 0L) durationMs else trimEndMs)}", color = Color.White)
                Slider(value = (if (trimEndMs == 0L) durationMs else trimEndMs).toFloat().coerceIn(0f, durationMs.coerceAtLeast(1000L).toFloat()), onValueChange = { trimEndMs = it.toLong().coerceAtLeast(trimStartMs + 500).coerceAtMost(durationMs) }, valueRange = 0f..durationMs.coerceAtLeast(1000L).toFloat(), enabled = durationMs > 0L)
                Text("Duração original: ${formatTime(durationMs)}", color = Muted, fontSize = 12.sp)
            }

            EditorCard("TEXTO SOBRE O VÍDEO", Icons.Rounded.Title) {
                OutlinedTextField(value = overlayText, onValueChange = { overlayText = it }, label = { Text("Gancho / legenda") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), minLines = 1, maxLines = 3)
                Text("Tamanho do texto", color = Color.White, fontWeight = FontWeight.Medium)
                Slider(value = textSize, onValueChange = { textSize = it }, valueRange = 28f..100f)
                Text("Posição vertical", color = Color.White, fontWeight = FontWeight.Medium)
                Slider(value = textY, onValueChange = { textY = it }, valueRange = .08f.. .92f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Cor:", color = Muted)
                    listOf(android.graphics.Color.WHITE, android.graphics.Color.YELLOW, android.graphics.Color.CYAN, android.graphics.Color.RED).forEach { color ->
                        Box(Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(Color(color)).then(if (textColor == color) Modifier.padding(3.dp).background(Orange, RoundedCornerShape(16.dp)) else Modifier).androidxClickable { textColor = color })
                    }
                }
                Text("O texto aparece no preview e será aplicado ao MP4 exportado.", color = Muted, fontSize = 12.sp)
            }

            EditorCard("NOME DO ARQUIVO", Icons.Rounded.DriveFileRenameOutline) {
                OutlinedTextField(value = projectName, onValueChange = { projectName = it }, label = { Text("Nome do projeto") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp))
            }

            Button(
                onClick = {
                    val uri = selectedUri
                    if (uri == null) status = "Escolha um vídeo primeiro."
                    else if (durationMs > 0L && trimEndMs <= trimStartMs) status = "O fim do corte precisa ser depois do início."
                    else if (!exporting) {
                        exporting = true
                        status = "Exportando MP4 com corte, proporção e texto…"
                        exportVideo(uri, projectName, ratio, trimStartMs, if (trimEndMs == 0L) durationMs else trimEndMs, overlayText, textSize, textColor, textY) { ok, msg ->
                            exporting = false
                            status = msg
                        }
                    }
                },
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange, disabledContainerColor = Color(0xFF6F493B))
            ) {
                if (exporting) CircularProgressIndicator(Modifier.size(21.dp), color = Color.White, strokeWidth = 2.dp) else Icon(Icons.Rounded.FileDownload, null)
                Spacer(Modifier.width(9.dp))
                Text(if (exporting) "Exportando…" else "Exportar MP4", fontWeight = FontWeight.Bold)
            }

            Surface(color = Panel, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Icon(if (exporting) Icons.Rounded.Sync else Icons.Rounded.Info, null, tint = Orange)
                    Text(status, color = Muted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
                }
            }
            Text("Cria Studio AI • Fase 1", color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }

    @Composable
    private fun EditorCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = Orange)
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            content()
        }
    }

    private fun Modifier.androidxClickable(onClick: () -> Unit): Modifier =
        this.then(androidx.compose.foundation.clickable(onClick = onClick))

    private fun formatTime(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) / 1000L)
        return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
    }

    private fun makeTextOverlay(text: String, ratio: String, size: Float, color: Int, yFraction: Float): Bitmap {
        val (width, height) = when (ratio) {
            "9:16" -> 1080 to 1920
            "1:1" -> 1080 to 1080
            else -> 1920 to 1080
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size * minOf(width / 1080f, height / 1920f)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            setShadowLayer(8f, 2f, 2f, android.graphics.Color.BLACK)
        }
        val maxWidth = width * .88f
        val words = text.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var line = ""
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                lines.add(line)
                line = word
            } else line = candidate
        }
        if (line.isNotBlank()) lines.add(line)
        val lineHeight = paint.textSize * 1.18f
        val startY = (height * yFraction - (lines.size - 1) * lineHeight / 2f).coerceIn(paint.textSize, height - paint.textSize)
        lines.forEachIndexed { index, value -> canvas.drawText(value, width / 2f, startY + index * lineHeight, paint) }
        return bitmap
    }

    private fun exportVideo(uri: Uri, projectTitle: String, ratio: String, startMs: Long, endMs: Long, text: String, textSize: Float, textColor: Int, textY: Float, done: (Boolean, String) -> Unit) {
        val output = File(cacheDir, "criastudio_${System.currentTimeMillis()}.mp4")
        try {
            val builder = MediaItem.Builder().setUri(uri)
            if (endMs > startMs && endMs > 0L) {
                builder.setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build())
            }
            val targetAspectRatio = when (ratio) { "9:16" -> 9f / 16f; "1:1" -> 1f; else -> 16f / 9f }
            val effects = mutableListOf<androidx.media3.common.Effect>()
            effects.add(Presentation.createForAspectRatio(targetAspectRatio, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
            if (text.isNotBlank()) {
                val bitmap = makeTextOverlay(text, ratio, textSize, textColor, textY)
                effects.add(OverlayEffect(listOf(BitmapOverlay.createStaticBitmapOverlay(bitmap))))
            }
            val edited = EditedMediaItem.Builder(builder.build()).setEffects(Effects(emptyList(), effects)).build()
            val transformer = Transformer.Builder(this).addListener(object : Transformer.Listener {
                override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                    try {
                        if (!output.exists() || output.length() == 0L) {
                            runOnUiThread { done(false, "A exportação terminou, mas o arquivo ficou vazio.") }
                            return
                        }
                        val safeTitle = projectTitle.trim().replace(Regex("[^A-Za-z0-9_-]"), "_").take(40).ifBlank { "Video" }
                        val values = ContentValues().apply {
                            put(MediaStore.Video.Media.DISPLAY_NAME, "CriaStudio_${safeTitle}_${System.currentTimeMillis()}.mp4")
                            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CriaStudio")
                        }
                        val dest = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                        if (dest == null) { runOnUiThread { done(false, "Não foi possível criar o arquivo na galeria.") }; return }
                        val target = contentResolver.openOutputStream(dest)
                        if (target == null) { contentResolver.delete(dest, null, null); runOnUiThread { done(false, "Não foi possível gravar na galeria.") }; return }
                        target.use { out -> output.inputStream().use { input -> input.copyTo(out) } }
                        runOnUiThread { done(true, "Pronto! Salvo em Filmes/CriaStudio.") }
                    } catch (e: Exception) {
                        runOnUiThread { done(false, "Falha ao salvar: ${e.localizedMessage ?: "erro desconhecido"}") }
                    } finally { output.delete() }
                }
                override fun onError(composition: androidx.media3.transformer.Composition, exportResult: ExportResult, exportException: ExportException) {
                    output.delete()
                    runOnUiThread { done(false, "Falha ao exportar: ${exportException.localizedMessage ?: "formato não suportado"}") }
                }
            }).build()
            transformer.start(edited, output.absolutePath)
        } catch (e: Exception) {
            output.delete()
            done(false, "Erro ao iniciar exportação: ${e.localizedMessage ?: "tente novamente"}")
        }
    }
}
