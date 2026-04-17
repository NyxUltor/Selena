package com.k.selena

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.k.selena.core.SelenaForegroundService

class MainActivity : ComponentActivity() {
    private val recordAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Log.i(TAG, "RECORD_AUDIO granted")
                startSelenaService()
            } else {
                Log.w(TAG, "RECORD_AUDIO denied, service will stay alive but voice capture is limited")
            }
            moveTaskToBack(true)
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainActivity launched, checking RECORD_AUDIO permission")
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.i(TAG, "RECORD_AUDIO already granted")
                startSelenaService()
                moveTaskToBack(true)
                finish()
            }
            else -> {
                Log.i(TAG, "Requesting RECORD_AUDIO permission")
                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startSelenaService() {
        val serviceIntent = SelenaForegroundService.createIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
