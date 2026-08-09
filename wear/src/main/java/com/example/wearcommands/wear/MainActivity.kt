package com.example.wearcommands.wear

import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.wearcommands.shared.CommandBus
import com.example.wearcommands.shared.CommandProtocol
import com.example.wearcommands.shared.CommandSender
import com.example.wearcommands.shared.PhotoBus
import com.example.wearcommands.wear.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * Saat uygulamasi. Telefona uzaktan yonetim komutlari gonderir; telefondan
 * gelen durum/konum bilgisini ve fotograf onizlemesini gosterir.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var torchOn = false
    private var alarmOn = false
    private var audioOn = false
    private var videoOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.torchButton.setOnClickListener {
            torchOn = !torchOn
            send(if (torchOn) CommandProtocol.CMD_TORCH_ON else CommandProtocol.CMD_TORCH_OFF)
            binding.torchButton.setText(if (torchOn) R.string.btn_torch_off else R.string.btn_torch_on)
        }
        binding.alarmButton.setOnClickListener {
            alarmOn = !alarmOn
            send(if (alarmOn) CommandProtocol.CMD_ALARM_ON else CommandProtocol.CMD_ALARM_OFF)
            binding.alarmButton.setText(if (alarmOn) R.string.btn_alarm_off else R.string.btn_alarm_on)
        }
        binding.lockButton.setOnClickListener { send(CommandProtocol.CMD_LOCK) }
        binding.locationButton.setOnClickListener { send(CommandProtocol.CMD_LOCATION) }
        binding.messageButton.setOnClickListener { showMessageDialog() }
        binding.photoButton.setOnClickListener { send(CommandProtocol.CMD_PHOTO) }
        binding.audioButton.setOnClickListener {
            audioOn = !audioOn
            send(if (audioOn) CommandProtocol.CMD_AUDIO_START else CommandProtocol.CMD_AUDIO_STOP)
            binding.audioButton.setText(if (audioOn) R.string.btn_audio_off else R.string.btn_audio_on)
        }
        binding.videoButton.setOnClickListener {
            videoOn = !videoOn
            send(if (videoOn) CommandProtocol.CMD_VIDEO_START else CommandProtocol.CMD_VIDEO_STOP)
            binding.videoButton.setText(if (videoOn) R.string.btn_video_off else R.string.btn_video_on)
        }
        binding.wipeButton.setOnClickListener { showWipeDialog() }

        observeIncoming()
        observePhoto()
    }

    private fun observeIncoming() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CommandBus.incoming.collect { command ->
                    val (name, value) = CommandProtocol.split(command)
                    val text = when (name) {
                        CommandProtocol.RSP_LOCATION -> "Konum: $value"
                        CommandProtocol.RSP_STATUS -> value
                        CommandProtocol.RSP_ERROR -> "Hata: $value"
                        else -> "Gelen: $command"
                    }
                    binding.lastCommandText.text = text
                }
            }
        }
    }

    private fun observePhoto() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PhotoBus.incoming.collect { jpeg ->
                    val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                    if (bmp != null) {
                        binding.previewImage.setImageBitmap(bmp)
                        binding.previewImage.visibility = View.VISIBLE
                        binding.lastCommandText.text = getString(R.string.photo_ready)
                    }
                }
            }
        }
    }

    private fun send(command: String) {
        lifecycleScope.launch {
            runCatching { CommandSender.send(applicationContext, command) }
                .onSuccess { count ->
                    binding.lastCommandText.text =
                        if (count == 0) getString(R.string.no_phone) else "Gonderildi: ${label(command)}"
                }
                .onFailure { Toast.makeText(this@MainActivity, "Hata: ${it.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun label(command: String): String = CommandProtocol.split(command).first

    private fun showMessageDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.msg_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.btn_message)
            .setView(input)
            .setPositiveButton(R.string.send) { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) send(CommandProtocol.build(CommandProtocol.CMD_MESSAGE, text))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showWipeDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = getString(R.string.pin_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.wipe_confirm_title)
            .setMessage(R.string.wipe_confirm_msg)
            .setView(input)
            .setPositiveButton(R.string.wipe_confirm_yes) { _, _ ->
                val pin = input.text.toString().trim()
                if (pin.isNotEmpty()) send(CommandProtocol.build(CommandProtocol.CMD_WIPE, pin))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
