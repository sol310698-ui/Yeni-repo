package com.example.wearcommands.mobile

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.wearcommands.mobile.databinding.ActivityMainBinding
import com.example.wearcommands.shared.CommandBus
import com.example.wearcommands.shared.CommandProtocol
import com.example.wearcommands.shared.CommandSender
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Telefon uygulamasi. Kurulum (izinler, cihaz yoneticisi, PIN, pil) ve gelen
 * komut logunu yonetir; ayrica test icin saate komut gonderebilir.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val log = StringBuilder()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val denied = result.filterValues { !it }.keys
            if (denied.isEmpty()) toast("Tum izinler verildi")
            else toast("Verilmeyen izin: ${denied.size}")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.permissionsButton.setOnClickListener { requestAllPermissions() }
        binding.adminButton.setOnClickListener { requestDeviceAdmin() }
        binding.batteryButton.setOnClickListener { openBatterySettings() }
        binding.pinButton.setOnClickListener { showPinDialog() }

        binding.pingButton.setOnClickListener { send(CommandProtocol.CMD_PING) }
        binding.vibrateButton.setOnClickListener { send(CommandProtocol.CMD_VIBRATE) }
        binding.customButton.setOnClickListener {
            val text = binding.commandInput.text.toString().trim().uppercase(Locale.getDefault())
            if (text.isEmpty()) toast("Once bir komut yaz") else send(text)
        }

        observeIncoming()
        refreshConnectedNodes()

        // Sarj cikisini yakalamak icin kalici koruma servisini baslat.
        runCatching { MonitorService.start(this) }
    }

    private fun observeIncoming() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CommandBus.incoming.collect { command -> appendLog("⬇️ GELEN: $command") }
            }
        }
    }

    private fun send(command: String) {
        lifecycleScope.launch {
            runCatching { CommandSender.send(applicationContext, command) }
                .onSuccess { count ->
                    if (count == 0) appendLog("⚠️ GONDERILEMEDI (saat yok): $command")
                    else appendLog("⬆️ GONDERILDI ($count): $command")
                }
                .onFailure { appendLog("❌ HATA: ${it.message}") }
        }
    }

    private fun requestAllPermissions() {
        val perms = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun requestDeviceAdmin() {
        if (AdminHelper.isActive(this)) { toast("Cihaz yoneticisi zaten etkin"); return }
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, PhoneAdminReceiver.component(this@MainActivity))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Uzaktan kilitleme ve veri silme icin gereklidir."
            )
        }
        runCatching { startActivity(intent) }.onFailure { toast("Acilamadi: ${it.message}") }
    }

    private fun openBatterySettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        runCatching { startActivity(intent) }.onFailure {
            runCatching {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")))
            }
        }
    }

    private fun showPinDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Yeni PIN"
        }
        AlertDialog.Builder(this)
            .setTitle("Veri silme PIN'i")
            .setMessage("Saatten veri silme icin sorulacak PIN.")
            .setView(input)
            .setPositiveButton("Kaydet") { _, _ ->
                val pin = input.text.toString().trim()
                if (pin.length < 4) toast("En az 4 haneli olmali")
                else { PinStore.setPin(this, pin); toast("PIN kaydedildi") }
            }
            .setNegativeButton("Vazgec", null)
            .show()
    }

    private fun refreshConnectedNodes() {
        lifecycleScope.launch {
            runCatching { Wearable.getNodeClient(applicationContext).connectedNodes.await() }
                .onSuccess { nodes ->
                    binding.statusText.text = if (nodes.isEmpty()) "Bagli saat yok."
                    else "Bagli saat(ler): " + nodes.joinToString { it.displayName }
                }
                .onFailure { binding.statusText.text = "Durum okunamadi: ${it.message}" }
        }
    }

    private fun appendLog(line: String) {
        log.insert(0, "[${timeFormat.format(Date())}] $line\n")
        binding.logText.text = log.toString()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onResume() {
        super.onResume()
        refreshConnectedNodes()
    }
}
