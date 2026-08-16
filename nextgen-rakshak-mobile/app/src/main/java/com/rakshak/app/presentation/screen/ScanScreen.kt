package com.rakshak.app.presentation.screen

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.rakshak.app.data.model.Alert
import com.rakshak.app.presentation.theme.AlertRed
import com.rakshak.app.presentation.theme.PrimaryBlue
import com.rakshak.app.presentation.theme.SafeGreen
import com.rakshak.app.presentation.viewmodel.ScanViewModel
import com.rakshak.app.utils.Haptics
import com.rakshak.app.utils.rotate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

@SuppressLint("UnsafeOptInUsageError")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(viewModel: ScanViewModel, onReported: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val reported by viewModel.reported.collectAsStateWithLifecycle()
    val scanningFor by viewModel.scanningFor.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var showConfirmation by remember { mutableStateOf(false) }
    var showReview by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var lastReportedChildName by remember { mutableStateOf("Unknown") }
    var lastReportedLocation by remember { mutableStateOf("Unknown") }

    LaunchedEffect(reported) {
        if (reported) {
            showConfirmation = true
            showReview = false
        }
    }

    LaunchedEffect(pending) {
        val match = pending
        if (match != null) {
            Haptics.vibrateMatch(context)
        } else {
            // Cleared by dismiss() or a successful confirm() — either way the
            // compare screen no longer has anything to show.
            showReview = false
        }
    }

    if (showConfirmation) {
        MatchConfirmationScreen(
            childName = lastReportedChildName,
            location = lastReportedLocation,
            onDone = onReported
        )
        return
    }

    // Full-screen side-by-side compare, entered only after the volunteer taps
    // "Confirm Match" on the live overlay below — nothing is submitted until
    // they explicitly confirm again from here.
    val reviewMatch = pending
    if (showReview && reviewMatch != null) {
        BackHandler { showReview = false }
        MatchReviewScreen(
            alert = reviewMatch.alert,
            faceCrop = reviewMatch.faceCrop,
            confidence = reviewMatch.confidence,
            error = error,
            onReject = {
                viewModel.dismiss()
                showReview = false
            },
            onConfirm = {
                lastReportedChildName = reviewMatch.alert.childName
                lastReportedLocation = reviewMatch.alert.lastSeen
                viewModel.confirm()
            },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scanning...", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.dismiss(); onReported() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Camera Preview
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
                                    val upright = proxy.toBitmap().rotate(proxy.imageInfo.rotationDegrees)
                                    viewModel.onFrame(upright)
                                    proxy.close()
                                }
                            }

                        provider.unbindAll()
                        camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
            )

            // Scanning Target Banner
            if (scanningFor.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.9f),
                ) {
                    Text(
                        text = "Point camera at people's face",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        fontWeight = FontWeight.Medium,
                        color = Color.DarkGray
                    )
                }
            }

            // Face Detection Box Overlay (Decorative)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(200.dp)
                    .border(3.dp, SafeGreen, RoundedCornerShape(16.dp))
            )

            // Bottom Controls — replaced by the match banner while a candidate is pending,
            // so the volunteer sees the percentage without the camera being covered.
            if (pending == null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = { onReported() },
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(PrimaryBlue, CircleShape)
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Stop", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    val cam = camera ?: return@IconButton
                                    if (cam.cameraInfo.hasFlashUnit()) {
                                        torchOn = !torchOn
                                        cam.cameraControl.enableTorch(torchOn)
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Filled.FlashOn,
                                    contentDescription = "Torch",
                                    tint = if (torchOn) PrimaryBlue else Color.Black,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Text("Torch", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Scanning will work offline", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Filled.Wifi, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Non-blocking match banner: the camera stays live behind it. Shows the
            // percentage as soon as a face is detected; nothing is submitted until
            // the volunteer taps through to the full compare screen and confirms there.
            val match = pending
            if (match != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    color = Color.White,
                    shadowElevation = 8.dp,
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                bitmap = match.faceCrop.asImageBitmap(),
                                contentDescription = "Detected face",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Possible match: ${match.alert.childName}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    "${(match.confidence * 100).toInt()}% similarity",
                                    color = PrimaryBlue,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = viewModel::dismiss,
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray)
                            ) {
                                Text("Not a Match", color = Color.DarkGray)
                            }
                            Button(
                                onClick = { showReview = true },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SafeGreen)
                            ) {
                                Text("Confirm Match")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-screen side-by-side compare: the alert's original photo next to the
 * face actually captured by the camera, with the alert's details below, so
 * the volunteer makes the final call from evidence rather than the banner's
 * bare percentage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchReviewScreen(
    alert: Alert,
    faceCrop: android.graphics.Bitmap,
    confidence: Float,
    error: String?,
    onReject: () -> Unit,
    onConfirm: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Confirm Sighting", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onReject) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Uploaded Alert Photo", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(6.dp))
                    AsyncImage(
                        model = alert.imageUrl,
                        contentDescription = "Uploaded alert photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)).background(Color.LightGray)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Detected Face", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(6.dp))
                    Image(
                        bitmap = faceCrop.asImageBitmap(),
                        contentDescription = "Detected face",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)).background(Color.LightGray)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Similarity Score", fontWeight = FontWeight.Medium)
                Text("${(confidence * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = PrimaryBlue)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                DetailRow("Child Name", alert.childName)
                DetailRow("Age / Gender", "${alert.age} yrs · ${alert.gender}")
                DetailRow("Clothing", alert.clothingDesc)
                DetailRow("Last Seen", alert.lastSeen)
            }

            if (error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(error, color = AlertRed, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                "Does this look like the missing child?",
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onReject,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text("Reject")
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SafeGreen)
                ) {
                    Text("Confirm Match")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchConfirmationScreen(childName: String, location: String, onDone: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Confirm Match", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Success",
                tint = SafeGreen,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text("Thank You!", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Your match has been submitted successfully.",
                textAlign = TextAlign.Center,
                color = Color.Gray
            )
            
            Spacer(modifier = Modifier.height(48.dp))

            val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            val currentTime = dateFormat.format(Date())

            Column(modifier = Modifier.fillMaxWidth()) {
                DetailRow("Child Name", childName)
                DetailRow("Matched At", currentTime)
                DetailRow("Location", location)
                DetailRow("Matched By", "You")
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text("Done", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = Color.Gray, fontSize = 14.sp)
        }
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.End)
    }
}
