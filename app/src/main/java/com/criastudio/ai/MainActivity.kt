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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay
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
        var currentPositionMs by remember { mutableLongStateOf(0L) }
        var selectedTool by remember { mutableStateOf("Formato") }

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

        LaunchedEffect(player) {
            while (player != null) {
                currentPositionMs = player.currentPosition.coerceAtLeast(0L)
                delay(100)
            }
        }

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


            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf("Formato" to Icons.Rounded.Crop, "Corte" to Icons.Rounded.ContentCut, "Texto" to Icons.Rounded.Title, "Nome" to Icons.Rounded.DriveFileRenameOutline).forEach { (label, icon) ->
                    val active = selectedTool == label
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (active) Orange.copy(alpha = .16f) else Color.Transparent).clickable { selectedTool = label }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(icon, null, tint = if (active) Orange else Muted, modifier = Modifier.size(18.dp))
                        Text(label, color = if (active) Orange else Muted, fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
            if (selectedTool == "Formato") EditorCard("FORMATO DE SAÍDA", Icons.Rounded.Crop) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("9:16", "1:1", "16:9").forEach { value ->
                        FilterChip(selected = ratio == value, onClick = { ratio = value }, label = { Text(value) }, modifier = Modifier.weight(1f))
                    }
                }
                Text(when (ratio) { "9:16" -> "Vertical • Reels, Stories e TikTok"; "1:1" -> "Quadrado • Feed"; else -> "Horizontal • YouTube" }, color = Muted, fontSize = 12.sp)
            }

            if (selectedTool == "Corte") EditorCard("TIMELINE • CORTE", Icons.Rounded.ContentCut) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("POSIÇÃO ATUAL", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(formatTime(currentPositionMs), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("TRECHO SELECIONADO", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(formatTime((effectiveTrimEnd(trimEndMs, durationMs) - trimStartMs).coerceAtLeast(0L)), color = Orange, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
                VideoTimeline(
                    durationMs = durationMs,
                    trimStartMs = trimStartMs,
                    trimEndMs = effectiveTrimEnd(trimEndMs, durationMs),
                    playheadMs = currentPositionMs,
                    onSeek = { position ->
                        player?.seekTo(position.coerceIn(0L, durationMs.coerceAtLeast(0L)))
                        currentPositionMs = position
                    },
                    onTrimStart = { value ->
                        val end = effectiveTrimEnd(trimEndMs, durationMs)
                        trimStartMs = value.coerceIn(0L, (end - 500L).coerceAtLeast(0L))
                        player?.seekTo(trimStartMs)
                        currentPositionMs = trimStartMs
                    },
                    onTrimEnd = { value ->
                        val start = trimStartMs
                        trimEndMs = value.coerceIn((start + 500L).coerceAtMost(durationMs), durationMs.coerceAtLeast(500L))
                        player?.seekTo(trimEndMs.coerceAtLeast(0L))
                        currentPositionMs = trimEndMs
                    }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("INÍCIO  ${formatTime(trimStartMs)}", color = Muted, fontSize = 12.sp)
                    Text("FIM  ${formatTime(effectiveTrimEnd(trimEndMs, durationMs))}", color = Muted, fontSize = 12.sp)
                }
                Text("Arraste as bordas laranja para cortar ou toque na faixa para avançar.", color = Muted, fontSize = 11.sp)
                Text("Duração total: ${formatTime(durationMs)}", color = Muted, fontSize = 12.sp)
            }

            if (selectedTool == "Texto") EditorCard("TEXTO SOBRE O VÍDEO", Icons.Rounded.Title) {
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

            if (selectedTool == "Nome") EditorCard("NOME DO ARQUIVO", Icons.Rounded.DriveFileRenameOutline) {
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

    private fun effectiveTrimEnd(trimEnd: Long, duration: Long): Long =
        if (duration <= 0L) 0L else if (trimEnd <= 0L) duration else trimEnd.coerceIn(0L, duration)

    @Composable
    private fun VideoTimeline(
        durationMs: Long,
        trimStartMs: Long,
        trimEndMs: Long,
        playheadMs: Long,
        onSeek: (Long) -> Unit,
        onTrimStart: (Long) -> Unit,
        onTrimEnd: (Long) -> Unit
    ) {
        var dragTarget by remember { mutableStateOf("playhead") }
        val total = durationMs.coerceAtLeast(1L)
        val start = trimStartMs.coerceIn(0L, total)
        val end = trimEndMs.coerceIn((start + 500L).coerceAtMost(total), total)
        val playhead = playheadMs.coerceIn(0L, total)

        Canvas(
            Modifier.fillMaxWidth().height(76.dp)
                .pointerInput(durationMs, trimStartMs, trimEndMs) {
                    detectTapGestures { tap ->
                        if (durationMs > 0L) {
                            val ratio = (tap.x / size.width).coerceIn(0f, 1f)
                            onSeek((ratio * durationMs).toLong())
                        }
                    }
                }
                .pointerInput(durationMs, trimStartMs, trimEndMs) {
                    detectDragGestures(
                        onDragStart = { point ->
                            val w = size.width.coerceAtLeast(1)
                            val startX = (start.toFloat() / total * w)
                            val endX = (end.toFloat() / total * w)
                            val x = point.x
                            dragTarget = when {
                                kotlin.math.abs(x - startX) <= kotlin.math.abs(x - endX) &&
                                    kotlin.math.abs(x - startX) < 42.dp.toPx() -> "start"
                                kotlin.math.abs(x - endX) < 42.dp.toPx() -> "end"
                                else -> "playhead"
                            }
                            val ratio = (x / w).coerceIn(0f, 1f)
                            when (dragTarget) {
                                "start" -> onTrimStart((ratio * durationMs).toLong())
                                "end" -> onTrimEnd((ratio * durationMs).toLong())
                                else -> onSeek((ratio * durationMs).toLong())
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (durationMs > 0L) {
                                val ratio = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                val position = (ratio * durationMs).toLong()
                                when (dragTarget) {
                                    "start" -> onTrimStart(position)
                                    "end" -> onTrimEnd(position)
                                    else -> onSeek(position)
                                }
                            }
                        }
                    )
                }
        ) {
            val w = size.width
            val centerY = size.height * .53f
            val trackHeight = 16.dp.toPx()
            val startX = (start.toFloat() / total * w).coerceIn(0f, w)
            val endX = (end.toFloat() / total * w).coerceIn(startX, w)
            val headX = (playhead.toFloat() / total * w).coerceIn(0f, w)
            drawRoundRect(
                color = Color(0xFF343640),
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = androidx.compose.ui.geometry.Size(w, trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f)
            )
            drawRoundRect(
                color = Orange.copy(alpha = .28f),
                topLeft = Offset(startX, centerY - trackHeight / 2f),
                size = androidx.compose.ui.geometry.Size((endX - startX).coerceAtLeast(0f), trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f)
            )
            drawLine(Orange, Offset(startX, centerY - 17.dp.toPx()), Offset(startX, centerY + 17.dp.toPx()), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            drawLine(Orange, Offset(endX, centerY - 17.dp.toPx()), Offset(endX, centerY + 17.dp.toPx()), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(Orange, radius = 7.dp.toPx(), center = Offset(startX, centerY))
            drawCircle(Orange, radius = 7.dp.toPx(), center = Offset(endX, centerY))
            drawLine(Color.White, Offset(headX, 4.dp.toPx()), Offset(headX, size.height - 5.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(headX, 5.dp.toPx()))
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
        this.clickable(onClick = onClick)

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
