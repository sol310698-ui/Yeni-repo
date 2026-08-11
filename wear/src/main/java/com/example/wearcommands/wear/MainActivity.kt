package com.example.wearcommands.wear

import android.app.RemoteInput
import android.graphics.BitmapFactory
import android.os.Bundle
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
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
import com.example.wearcommands.shared.PhotoBus
import kotlinx.coroutines.launch

/**
 * Saat arayuzu (Jetpack Compose for Wear OS).
 *
 * Kavisli liste (ScalingLazyColumn): merkeze gelen buton buyuk bir daire
 * olarak one cikar, kenardakiler kuculur; Samsung kadrani (bezel) veya parmak
 * ile kaydirinca butonlar alttan uste akar.
 */
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

        var photo by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
        var info by remember { mutableStateOf<String?>(null) }

        fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

        fun send(command: String) {
            scope.launch {
                runCatching { CommandSender.send(context.applicationContext, command) }
                    .onSuccess { if (it == 0) toast("Bagli telefon yok") }
                    .onFailure { toast("Hata: ${it.message}") }
            }
        }

        // Metin girisi (mesaj) sonucu
        val messageLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val text = result.data?.let { RemoteInput.getResultsFromIntent(it) }
                ?.getCharSequence(KEY_MSG)?.toString()
            if (!text.isNullOrBlank()) send(CommandProtocol.build(CommandProtocol.CMD_MESSAGE, text))
        }
        // PIN girisi (veri silme) sonucu
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

        // Telefondan gelen durum/konum bilgisi
        LaunchedEffect(Unit) {
            CommandBus.incoming.collect { command ->
                val (name, value) = CommandProtocol.split(command)
                when (name) {
                    CommandProtocol.RSP_LOCATION -> info = "Konum:\n$value"
                    CommandProtocol.RSP_ERROR -> toast("Hata: $value")
                    CommandProtocol.RSP_STATUS -> toast(value)
                }
            }
        }
        // Telefondan gelen fotograf
        LaunchedEffect(Unit) {
            PhotoBus.incoming.collect { jpeg ->
                photo = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            }
        }
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

        var lastRotary by remember { mutableStateOf(0L) }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        ScalingLazyColumn(
            state = listState,
            // Parmakla kaydirinca en yakin buton ortaya oturur (snap).
            flingBehavior = ScalingLazyColumnDefaults.snapFlingBehavior(state = listState),
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onRotaryScrollEvent { event ->
                    // Kadran bir tik donunce bir sonraki buton tam ortaya gelir.
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

        // Fotograf onizleme (tam ekran; dokununca kapanir)
        photo?.let { bmp ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { photo = null },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Foto onizleme",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // Bilgi (konum) katmani
        info?.let { text ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE6000000))
                    .clickable { info = null },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(20.dp)
                )
            }
        }
        }
    }
}

/** Merkeze geldiginde buyuyen daire buton; uzerinde buyuk yazi. */
@Composable
private fun CircleAction(
    text: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
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

private const val KEY_MSG = "msg"
private const val KEY_PIN = "pin"

/** Listedeki komut butonu sayisi (rotary snap sinir kontrolu icin). */
private const val ITEM_COUNT = 9
