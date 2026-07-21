package com.rakshak.app.presentation.screen

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.rakshak.app.presentation.viewmodel.ScanViewModel
import com.rakshak.app.utils.Haptics
import com.rakshak.app.utils.rotate
import java.util.concurrent.Executors

/** Shared size/shape for the two comparison thumbnails. */
private val faceModifier = Modifier
    .size(120.dp)
    .clip(RoundedCornerShape(8.dp))

/** A comparison thumbnail with a caption saying which image it is. */
@Composable
private fun LabelledFace(label: String, image: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        image()
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** Live camera scan. Feeds frames to [ScanViewModel] and prompts on a match. */
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun ScanScreen(viewModel: ScanViewModel, onReported: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val reported by viewModel.reported.collectAsStateWithLifecycle()

    LaunchedEffect(reported) {
        if (reported) onReported()
    }

    // Buzz when a candidate appears — the volunteer is watching the crowd, not the screen.
    LaunchedEffect(pending) {
        if (pending != null) Haptics.vibrateMatch(context)
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .apply {
                            setAnalyzer(Executors.newSingleThreadExecutor()) { proxy ->
                                // Apply sensor rotation so faces are upright for ML Kit.
                                val upright = proxy.toBitmap().rotate(proxy.imageInfo.rotationDegrees)
                                viewModel.onFrame(upright)
                                proxy.close()
                            }
                        }

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )

        val match = pending
        if (match != null) {
            AlertDialog(
                onDismissRequest = viewModel::dismiss,
                title = { Text("Possible Match — ${match.alert.childName}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Side-by-side: the parent's photo next to the live capture, so the
                        // volunteer compares the two faces instead of trusting the score.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        ) {
                            LabelledFace(label = "Reported photo") {
                                AsyncImage(
                                    model = match.alert.imageUrl,
                                    contentDescription = "Photo of ${match.alert.childName} submitted by the parent",
                                    contentScale = ContentScale.Crop,
                                    modifier = faceModifier,
                                )
                            }
                            LabelledFace(label = "Camera now") {
                                Image(
                                    bitmap = match.faceCrop.asImageBitmap(),
                                    contentDescription = "Face just captured by the camera",
                                    contentScale = ContentScale.Crop,
                                    modifier = faceModifier,
                                )
                            }
                        }
                        Text(
                            "Confidence ${(match.confidence * 100).toInt()}%",
                            fontWeight = FontWeight.Bold,
                        )
                        Text("${match.alert.age} yrs · ${match.alert.gender}")
                        Text(match.alert.clothingDesc)
                        Text("Is this the child? Confirm to alert police with your location.")
                    }
                },
                confirmButton = { TextButton(onClick = viewModel::confirm) { Text("Confirm") } },
                dismissButton = { TextButton(onClick = viewModel::dismiss) { Text("Not a match") } },
            )
        }
    }
}
