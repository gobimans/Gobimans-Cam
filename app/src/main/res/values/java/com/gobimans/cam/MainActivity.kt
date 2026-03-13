package com.gobimans.cam

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gobimans.cam.ui.CameraScreen
import com.gobimans.cam.ui.CameraViewModel
import com.gobimans.cam.ui.theme.GobimansCamTheme

class MainActivity : ComponentActivity() {

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val granted = results.values.all { it }
            val vm = cameraViewModel ?: return@registerForActivityResult
            vm.onPermissionsResult(granted)
        }

    private var cameraViewModel: CameraViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requiredPermissions = buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT <= 32) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        }.toTypedArray()

        setContent {
            val vm: CameraViewModel = viewModel(factory = CameraViewModel.provideFactory(application))
            cameraViewModel = vm

            GobimansCamTheme {
                CameraScreen(
                    viewModel = vm,
                    activity = this@MainActivity,
                    requiredPermissions = requiredPermissions,
                    onRequestPermissions = {
                        permissionsLauncher.launch(requiredPermissions)
                    },
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
}
