package com.example.wearcommands.mobile

import android.content.Context
import android.hardware.camera2.CameraManager

/** Arka kamera flasini (fener) acar/kapatir. Kamera izni gerektirmez. */
object TorchController {

    private fun cameraManager(context: Context): CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    /** Flasi olan ilk kameranin id'sini bulur. */
    private fun torchCameraId(cm: CameraManager): String? {
        for (id in cm.cameraIdList) {
            val hasFlash = cm.getCameraCharacteristics(id)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE)
            if (hasFlash == true) return id
        }
        return null
    }

    fun setTorch(context: Context, on: Boolean): Boolean {
        val cm = cameraManager(context)
        val id = torchCameraId(cm) ?: return false
        return runCatching { cm.setTorchMode(id, on); true }.getOrDefault(false)
    }
}
