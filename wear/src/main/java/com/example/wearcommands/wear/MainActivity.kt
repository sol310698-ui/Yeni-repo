package com.example.wearcommands.wear

import android.app.RemoteInput
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.input.RemoteInputIntentHelper
import com.example.wearcommands.shared.CommandBus
import com.example.wearcommands.shared.CommandProtocol
import com.example.wearcommands.shared.CommandSender
import com.example.wearcommands.shared.CryptoManager
import com.example.wearcommands.shared.PhotoBus
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearApp() }
    }
}

private val WearColors = Colors(
    primary = Color(0xFF8B6DF0),
    onPrimary = Color.White,
    surface = Color(0xFF16181F),
    onSurface = Color(0xFFECEAF6),
    background = Color.Black,
    onBackground = Color(0xFFECEAF6)
)

@Composable
private fun WearApp() {
    MaterialTheme(colors = WearColors) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val listState = rememberScalingLazyListState()
        val focusRequester = remember { FocusRequester() }

        var torchOn by remember { mutableStateOf(false) }
        var alarmOn by remember { mutableStateOf(false) }
        var audioOn by remember { mutableStateOf(false) }
        var videoOn by remember { mutableStateOf(false) }

        var photos by remember { mutableStateOf(PhotoStore.list(context)) }
        var galleryOpen by remember { mutableStateOf(false) }
        var viewerFile by remember { mutableStateOf<File?>(null) }
        var previewFile by remember { mutableStateOf<File?>(null) }
        var info by remember { mutableStateOf<String?>(null) }
        var settingsOpen by remember { mutableStateOf(false) }
        var lastRotary by remember { mutableStateOf(0L) }

        fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

        fun send(command: String) {
            scope.launch {
                runCatching { CommandSender.send(context.applicationContext, command) }
                    .onSuccess { if (it == 0) toast("Bagli telefon yok") }
                    .onFailure { toast("Hata: ${it.message}") }
            }
        }

        val messageLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val text = result.data?.let { RemoteInput.getResultsFromIntent(it) }
                ?.getCharSequence(KEY_MSG)?.toString()
            if (!text.isNullOrBlank()) send(CommandProtocol.build(CommandProtocol.CMD_MESSAGE, text))
        }
        val wipeLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val pin = result.data?.let { RemoteInput.getResultsFromIntent(it) }
                ?.getCharSequence(KEY_PIN)?.toString()
            if (!pin.isNullOrBlank()) send(CommandProtocol.build(CommandProtocol.CMD_WIPE, pin))
        }

        fun launchInput(key: String, label: String, launch: (android.content.Intent) -> Unit) {
            val remoteInput = RemoteInput.Builder(key).setLabel(label).build()
            val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
            RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
            launch(intent)
        }

        LaunchedEffect(Unit) {
            CommandBus.incoming.collect { command ->
                val (name, value) = CommandProtocol.split(command)
                when (name) {
                    CommandProtocol.RSP_LOCATION -> info = "Konum:\n$value"
                    CommandProtocol.RSP_ERROR -> toast("Hata: $value")
                    CommandProtocol.RSP_STATUS -> toast(value)
                    CommandProtocol.RSP_ALERT -> { vibrate(context); info = "⚠\n$value" }
                }
            }
        }
        LaunchedEffect(Unit) {
            PhotoBus.incoming.collect { jpeg ->
                val f = PhotoStore.save(context, jpeg)
                photos = PhotoStore.list(context)
                previewFile = f
            }
        }
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

            ScalingLazyColumn(
                state = listState,
                flingBehavior = ScalingLazyColumnDefaults.snapFlingBehavior(state = listState),
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .onRotaryScrollEvent { event ->
                        val now = System.currentTimeMillis()
                        if (now - lastRotary > 120) {
                            lastRotary = now
                            val step = if (event.verticalScrollPixels > 0) 1 else -1
                            val target = (listState.centerItemIndex + step).coerceIn(0, ITEM_COUNT - 1)
                            scope.launch { listState.animateScrollToItem(target) }
                        }
                        true
                    }
                    .focusRequester(focusRequester)
                    .focusable(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 60.dp, bottom = 60.dp)
            ) {
                item { PhotoHeader(count = photos.size) { galleryOpen = true } }
                item { WideChip("⚙ Ayarlar") { settingsOpen = true } }
                item {
                    CircleAction(if (torchOn) "Fener\nKAPAT" else "Fener\nAÇ") {
                        torchOn = !torchOn
                        send(if (torchOn) CommandProtocol.CMD_TORCH_ON else CommandProtocol.CMD_TORCH_OFF)
                    }
                }
                item {
                    CircleAction(if (alarmOn) "Alarm\nDURDUR" else "Alarm\nÇAL") {
                        alarmOn = !alarmOn
                        send(if (alarmOn) CommandProtocol.CMD_ALARM_ON else CommandProtocol.CMD_ALARM_OFF)
                    }
                }
                item { CircleAction("Kilitle") { send(CommandProtocol.CMD_LOCK) } }
                item { CircleAction("Konum") { send(CommandProtocol.CMD_LOCATION) } }
                item {
                    CircleAction("Ekrana\nmesaj") {
                        launchInput(KEY_MSG, "Mesaj") { messageLauncher.launch(it) }
                    }
                }
                item { CircleAction("Foto\nçek") { send(CommandProtocol.CMD_PHOTO) } }
                item {
                    CircleAction(if (audioOn) "Ses\nDURDUR" else "Ses\nkaydı") {
                        audioOn = !audioOn
                        send(if (audioOn) CommandProtocol.CMD_AUDIO_START else CommandProtocol.CMD_AUDIO_STOP)
                    }
                }
                item {
                    CircleAction(if (videoOn) "Video\nDURDUR" else "Video\nkaydı") {
                        videoOn = !videoOn
                        send(if (videoOn) CommandProtocol.CMD_VIDEO_START else CommandProtocol.CMD_VIDEO_STOP)
                    }
                }
                item {
                    CircleAction("Veri\nsil", danger = true) {
                        launchInput(KEY_PIN, "PIN") { wipeLauncher.launch(it) }
                    }
                }
            }

            // Yeni foto kucuk onizleme (dokun -> tam ekran)
            if (previewFile != null && viewerFile == null && !galleryOpen) {
                val f = previewFile!!
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xCC000000))
                        .clickable { previewFile = null },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PhotoThumb(f, size = 130.dp) { viewerFile = f; previewFile = null }
                        Spacer(Modifier.height(10.dp))
                        Text("Dokun: tam ekran", color = Color.White, fontSize = 13.sp)
                    }
                }
            }

            // Galeri (eski fotograflar)
            if (galleryOpen && viewerFile == null) {
                GalleryScreen(
                    photos = photos,
                    onOpen = { viewerFile = it },
                    onBack = { galleryOpen = false }
                )
            }

            // Ayarlar (eslesme QR)
            if (settingsOpen && viewerFile == null) {
                SettingsScreen(context) { settingsOpen = false }
            }

            // Bilgi / uyari katmani
            if (info != null && viewerFile == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xE6000000))
                        .clickable { info = null },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = info!!,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }

            // Tam ekran goruntuleyici (en ustte)
            viewerFile?.let { f ->
                val bmp = remember(f.path) { BitmapFactory.decodeFile(f.absolutePath) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable { viewerFile = null },
                    contentAlignment = Alignment.Center
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Foto",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}

/** Ust bolum: kayitli foto sayisi; dokununca galeri acilir. */
@Composable
private fun PhotoHeader(count: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF23202E))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "📷 Fotoğraflar ($count)",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}

/** Galeri ekrani: kayitli fotograflar; dokununca tam ekran. */
@Composable
private fun GalleryScreen(photos: List<File>, onOpen: (File) -> Unit, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 30.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF2A2740))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Text("‹ Geri", color = Color.White, fontSize = 14.sp) }

            if (photos.isEmpty()) {
                Text("Henüz fotoğraf yok", color = Color(0xFF9A96A8), fontSize = 14.sp)
            } else {
                photos.forEach { f -> PhotoThumb(f, size = 150.dp) { onOpen(f) } }
            }
        }
    }
}

/** Kucuk kare fotograf onizleme. */
@Composable
private fun PhotoThumb(file: File, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val bmp = remember(file.path) { BitmapFactory.decodeFile(file.absolutePath) }
    Box(
        modifier = Modifier
            .fillMaxWidth(0.7f)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF16181F))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Foto",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

/** Merkeze geldiginde buyuyen daire buton. */
@Composable
private fun CircleAction(text: String, danger: Boolean = false, onClick: () -> Unit) {
    val bg = if (danger) Color(0xFFFF5C6C) else MaterialTheme.colors.primary
    Box(
        modifier = Modifier
            .fillMaxWidth(0.86f)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            modifier = Modifier.padding(16.dp)
        )
    }
}

/** Genis yuvarlak kucuk buton (ust bolum / ayar butonlari). */
@Composable
private fun WideChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF23202E))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/** Ayarlar / eslesme ekrani: gizli anahtar QR olarak gosterilir. */
@Composable
private fun SettingsScreen(context: Context, onBack: () -> Unit) {
    // Sadece ayarlari acmak sifrelemeyi baslatmaz; anahtar "Etkinlestir"
    // denilene kadar aktif edilmez (yoksa eslesmeden komutlar kirilir).
    var activeSecret by remember { mutableStateOf(CryptoManager.getSecret(context)) }
    var secret by remember { mutableStateOf(activeSecret ?: CryptoManager.generateSecret()) }
    val active = activeSecret != null && activeSecret == secret
    val qr = remember(secret) { QrUtil.encode(secret, 360) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 26.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Eşleştirme", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(
                text = if (active) "Şifreleme: AKTİF" else "Şifreleme: PASİF",
                color = if (active) Color(0xFF3ED598) else Color(0xFFFFB020),
                fontSize = 12.sp
            )
            qr?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Eslesme QR",
                    modifier = Modifier.fillMaxWidth(0.72f).aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                )
            }
            Text(
                "1) Telefonda QR'ı tara  2) Etkinleştir",
                color = Color.White,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            if (!active) {
                WideChip("Etkinleştir") {
                    CryptoManager.setSecret(context, secret)
                    activeSecret = secret
                }
            }
            WideChip("Yeni anahtar") { secret = CryptoManager.generateSecret() }
            if (activeSecret != null) {
                WideChip("Şifrelemeyi kapat") {
                    CryptoManager.clear(context)
                    activeSecret = null
                    secret = CryptoManager.generateSecret()
                }
            }
            WideChip("‹ Geri", onBack)
        }
    }
}

private fun vibrate(context: Context) {
    val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
}

private const val KEY_MSG = "msg"
private const val KEY_PIN = "pin"

/** Listedeki oge sayisi (foto + ayarlar + 9 komut) — rotary snap siniri. */
private const val ITEM_COUNT = 11
