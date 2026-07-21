package com.rakshak.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.rakshak.app.di.ServiceLocator
import com.rakshak.app.networking.NotificationHelper
import com.rakshak.app.presentation.navigation.AppNavigation
import com.rakshak.app.presentation.screen.PermissionRationaleDialog
import com.rakshak.app.presentation.theme.RakshakTheme

class MainActivity : ComponentActivity() {

    // Start the offline mesh once the user responds to the permission prompt.
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            ServiceLocator.mesh(this).start()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.ensureChannel(this)

        val alreadyGranted = hasAllPermissions()
        // Nothing to ask for on later launches — bring the mesh up straight away.
        if (alreadyGranted) ServiceLocator.mesh(this).start()

        setContent {
            RakshakTheme {
                Surface {
                    // Explain why each permission is needed before the system prompt.
                    var showRationale by remember { mutableStateOf(!alreadyGranted) }
                    if (showRationale) {
                        PermissionRationaleDialog(
                            onContinue = {
                                showRationale = false
                                requestRuntimePermissions()
                            },
                        )
                    }
                    AppNavigation()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) ServiceLocator.mesh(this).stop()
    }

    private fun requiredPermissions(): List<String> = buildList {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun hasAllPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRuntimePermissions() {
        permissionLauncher.launch(requiredPermissions().toTypedArray())
    }
}
