package com.voltai.doai.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.voltai.doai.di.ServiceLocator
import com.voltai.doai.presentation.navigation.VoltAINavigation
import com.voltai.doai.presentation.theme.VoltTheme
import com.voltai.doai.service.InactivityKeepAlive

class MainActivity : ComponentActivity() {

    private var storageAccessGranted by mutableStateOf(false)
    private var storageRequestStarted = false

    private lateinit var inactivityKeepAlive: InactivityKeepAlive

    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.values.all { it }
            if (granted) {
                markStorageAccessGranted()
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inactivityKeepAlive = InactivityKeepAlive(ServiceLocator.qwenClient, lifecycleScope)
        setContent {
            VoltTheme {
                VoltAINavigation(storageAccessGranted = storageAccessGranted)
            }
        }
        requestStorageAccess()
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Environment.isExternalStorageManager()
        ) {
            markStorageAccessGranted()
        }
    }

    override fun onStart() {
        super.onStart()
        if (::inactivityKeepAlive.isInitialized) {
            inactivityKeepAlive.start()
        }
    }

    override fun onStop() {
        if (::inactivityKeepAlive.isInitialized) {
            inactivityKeepAlive.stop()
        }
        super.onStop()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (::inactivityKeepAlive.isInitialized) {
            inactivityKeepAlive.onUserActivity()
        }
    }

    private fun requestStorageAccess() {
        if (hasStorageAccess()) {
            markStorageAccessGranted()
            return
        }
        if (storageRequestStarted) return

        storageRequestStarted = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            storagePermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    private fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
        }

    private fun markStorageAccessGranted() {
        if (!storageAccessGranted) {
            storageAccessGranted = true
            requestNotificationPermission()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
