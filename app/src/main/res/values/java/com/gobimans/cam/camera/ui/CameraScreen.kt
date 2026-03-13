package com.gobimans.cam.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.gobimans.cam.ui.theme.GobimansCamTheme

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    activity: Activity,
    requiredPermissions: Array<String>,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceHolder by remember { mutableStateOf<SurfaceHolder?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onResume(surfaceHolder)
                Lifecycle.Event.ON_PAUSE -> viewModel.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when {
        !uiState.rawSupported || uiState.isBlocked -> {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Device does not support RAW_SENSOR capture.", color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onOpenSettings) { Text("Open settings", color = Color.White) }
                }
            }
        }
        !uiState.hasPermissions -> {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Gobimans Cam needs camera and storage access", color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRequestPermissions) { Text("Grant permissions") }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onOpenSettings) { Text("Open settings", color = Color.White) }
                }
            }
        }
        else -> {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(h: SurfaceHolder) { surfaceHolder = h; viewModel.onPreviewReady(h) }
                                override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, h: Int) { surfaceHolder = h; viewModel.onPreviewReady(h) }
                                override fun surfaceDestroyed(h: SurfaceHolder) {}
                            })
                        }
                    }
                )
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("RAW DNG", color = Color.White)
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Manual", color = Color.White)
                        Spacer(Modifier.weight(1f))
                        Switch(checked = uiState.manualMode, onCheckedChange = { viewModel.setManualEnabled(!uiState.manualMode) })
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clickable { activity.startActivity(Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)) }, contentAlignment = Alignment.Center) { Text("G", color = Color.White) }
                        Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.size(72.dp).background(Color.White, CircleShape).clickable(enabled = !uiState.isCapturing) { viewModel.capture() },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(Modifier.size(if (uiState.isCapturing) 40.dp else 64.dp).background(Color(0xFFE50914), CircleShape))
                            }
                        }
                        Text(uiState.lenses.getOrNull(uiState.currentLensIndex)?.label ?: "1x", color = Color.White, modifier = Modifier.clickable { viewModel.nextLens() })
                    }
                }
            }
        }
    }
}
