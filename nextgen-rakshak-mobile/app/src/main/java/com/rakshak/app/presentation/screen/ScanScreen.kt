package com.rakshak.app.presentation.screen

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.rakshak.app.data.model.Alert
import com.rakshak.app.domain.matching.FaceBox
import com.rakshak.app.presentation.theme.RakshakExtendedColors
import com.rakshak.app.presentation.theme.RakshakExtras
import com.rakshak.app.presentation.theme.Spacing
import com.rakshak.app.presentation.theme.rememberWindowInfo
import com.rakshak.app.presentation.viewmodel.ScanViewModel
import com.rakshak.app.utils.Constants
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
    val queuedCount by viewModel.queuedCount.collectAsStateWithLifecycle()
    val submitting by viewModel.submitting.collectAsStateWithLifecycle()
    val reported by viewModel.reported.collectAsStateWithLifecycle()
    val scanningFor by viewModel.scanningFor.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val detectedFaces by viewModel.detectedFaces.collectAsStateWithLifecycle()
    val scanStatus by viewModel.scanStatus.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val readiness by viewModel.readiness.collectAsStateWithLifecycle()

    var showConfirmation by remember { mutableStateOf(false) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var torchOn by remember { mutableStateOf(false) }
    var lastReportedChildName by remember { mutableStateOf("Unknown") }
    var lastReportedLocation by remember { mutableStateOf("Unknown") }

    // One analyzer thread for the whole screen lifetime. Re-creating it per
    // camera rebind (the previous bug) leaked a thread on every recomposition.
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    LaunchedEffect(Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
        }, ContextCompat.getMainExecutor(context))
    }

    // Bind the camera ONCE per (provider, view, lens) change — never on every
    // recomposition. The scan status / face-box state updates several times a
    // second; binding from AndroidView's `update` tore the analysis pipeline
    // down faster than it could deliver a frame, so ML Kit never saw an image.
    LaunchedEffect(cameraProvider, previewView, lensFacing) {
        val provider = cameraProvider ?: return@LaunchedEffect
        val view = previewView ?: return@LaunchedEffect

        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(view.surfaceProvider)
        }
        val analysis = ImageAnalysis.Builder()
            // Only ever work on the newest frame. The pipeline is slower than the
            // camera, so a queue here would show the volunteer matches from where
            // they were pointing seconds ago.
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            // Pin the analysis stream to 720p instead of taking CameraX's default.
            // Resolution is the main lever on detector latency, and detector
            // latency is the scan rate: 1080p roughly halves the faces scanned per
            // second in a crowd, while below 720p a child a few metres away falls
            // under Constants.MIN_FACE_PX and is dropped by the quality gate.
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(Constants.ANALYSIS_WIDTH, Constants.ANALYSIS_HEIGHT),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        )
                    )
                    .build()
            )
            .build()
            .apply {
                setAnalyzer(analysisExecutor) { proxy ->
                    try {
                        // Cheap check first: converting the frame costs two
                        // full-resolution bitmap allocations, and they are pure
                        // waste while the pipeline is busy or a match dialog is up.
                        if (!viewModel.acceptsFrames()) return@setAnalyzer
                        val upright = proxy.toBitmap().rotate(proxy.imageInfo.rotationDegrees)
                        viewModel.onFrame(upright)
                    } catch (e: Throwable) {
                        android.util.Log.e("ScanScreen", "Analyzer error", e)
                    } finally {
                        proxy.close()
                    }
                }
            }

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis).also {
                if (it.cameraInfo.hasFlashUnit()) {
                    it.cameraControl.enableTorch(torchOn && lensFacing == CameraSelector.LENS_FACING_BACK)
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("ScanScreen", "Camera bind failure for lens $lensFacing", e)
        }
    }

    LaunchedEffect(reported) {
        if (reported) {
            showConfirmation = true
        }
    }

    LaunchedEffect(pending) {
        if (pending != null) {
            Haptics.vibrateMatch(context)
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

    // Popup shown the instant a face match is detected — side-by-side compare
    // with the alert's details and the similarity score, all in one dialog so
    // nothing is submitted until the volunteer confirms right here.
    val reviewMatch = pending
    if (reviewMatch != null) {
        BackHandler { viewModel.dismiss() }
        MatchPopupDialog(
            alert = reviewMatch.alert,
            faceCrop = reviewMatch.faceCrop,
            confidence = reviewMatch.confidence,
            framesFused = reviewMatch.framesFused,
            queuedCount = queuedCount,
            submitting = submitting,
            error = error,
            onReject = viewModel::dismiss,
            onConfirm = {
                lastReportedChildName = reviewMatch.alert.childName
                lastReportedLocation = reviewMatch.alert.lastSeen
                viewModel.confirm()
            },
        )
    }

    val windowInfo = rememberWindowInfo()
    val extras = RakshakExtras.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scanning...", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.endSession(); onReported() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Camera Preview. Binding is done in the LaunchedEffect above — this
            // only hands the PreviewView up so the effect can attach a surface.
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                update = { view ->
                    if (previewView !== view) previewView = view
                }
            )

            // Live face-tracking overlay, drawn straight over the preview.
            FaceOverlay(
                faces = detectedFaces,
                frameWidth = diagnostics.frameWidth,
                frameHeight = diagnostics.frameHeight,
                mirrored = lensFacing == CameraSelector.LENS_FACING_FRONT,
            )

            // Fallback guide frame if no faces in view
            if (detectedFaces.isEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(220.dp)
                        .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                )
            }

            // Status banner: what the scanner is doing, who it is looking for,
            // and how fast it is going. Deliberately a dark scrim with white text
            // regardless of app theme — it sits on a live video feed, not a
            // themed surface, the same way a camera app's own overlay would.
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(if (windowInfo.isLandscape) 0.7f else 1f)
                    .padding(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = Color.Black.copy(alpha = 0.72f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val indicatorColor = when {
                            readiness.modelMismatch -> MaterialTheme.colorScheme.error
                            detectedFaces.any { it.isMatch } -> extras.success
                            detectedFaces.isNotEmpty() -> MaterialTheme.colorScheme.tertiary
                            readiness.preparing -> extras.warning
                            else -> Color.LightGray
                        }
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(indicatorColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text(
                            text = scanStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            maxLines = 2,
                        )
                    }
                }

                // Naming the children makes the scan concrete: the volunteer is
                // looking for Aarav, not running "face recognition".
                if (scanningFor.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = Color.Black.copy(alpha = 0.55f),
                    ) {
                        Text(
                            text = "Looking for: " + scanningFor.joinToString(", "),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                        )
                    }
                }

                // Live throughput. A scan that has silently stalled looks exactly
                // like one that is running and finding nobody; this is what tells
                // the two apart, on a real phone, in the field.
                if (diagnostics.frameMillis > 0) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = "${diagnostics.frameMillis} ms/frame · " +
                            "${diagnostics.embedded}/${diagnostics.detected} face(s) scanned · " +
                            "${readiness.alertsReady}/${readiness.alertsTotal} alert(s) ready",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            // Controls — hidden while the match popup is showing. A phone
            // rotated to landscape gets a side rail instead of a bottom bar: a
            // bar pinned to the bottom of a landscape frame eats a much bigger
            // share of the (shorter) preview height than the same bar does in
            // portrait, exactly where the volunteer needs to see the crowd.
            if (pending == null) {
                if (windowInfo.isLandscape) {
                    ScanControlsRail(
                        torchOn = torchOn,
                        lensFacing = lensFacing,
                        onToggleTorch = {
                            val cam = camera ?: return@ScanControlsRail
                            if (cam.cameraInfo.hasFlashUnit()) {
                                torchOn = !torchOn
                                cam.cameraControl.enableTorch(torchOn)
                            }
                        },
                        onStop = { viewModel.endSession(); onReported() },
                        onSwitchCamera = {
                            torchOn = false
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                } else {
                    ScanControlsBar(
                        torchOn = torchOn,
                        lensFacing = lensFacing,
                        onToggleTorch = {
                            val cam = camera ?: return@ScanControlsBar
                            if (cam.cameraInfo.hasFlashUnit()) {
                                torchOn = !torchOn
                                cam.cameraControl.enableTorch(torchOn)
                            }
                        },
                        onStop = { viewModel.endSession(); onReported() },
                        onSwitchCamera = {
                            torchOn = false
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanControlsBar(
    torchOn: Boolean,
    lensFacing: Int,
    onToggleTorch: () -> Unit,
    onStop: () -> Unit,
    onSwitchCamera: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TorchControl(torchOn, onToggleTorch)
            StopScanControl(onStop)
            SwitchCameraControl(lensFacing, onSwitchCamera)
        }
        Spacer(modifier = Modifier.height(Spacing.lg))
        OfflineNotice()
    }
}

/** The landscape counterpart of [ScanControlsBar]: a vertical rail instead of a bottom bar. */
@Composable
private fun ScanControlsRail(
    torchOn: Boolean,
    lensFacing: Int,
    onToggleTorch: () -> Unit,
    onStop: () -> Unit,
    onSwitchCamera: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xl, Alignment.CenterVertically),
    ) {
        TorchControl(torchOn, onToggleTorch)
        StopScanControl(onStop)
        SwitchCameraControl(lensFacing, onSwitchCamera)
        Spacer(modifier = Modifier.height(Spacing.md))
        OfflineNotice()
    }
}

@Composable
private fun TorchControl(torchOn: Boolean, onToggle: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onToggle) {
            Icon(
                Icons.Filled.FlashOn,
                contentDescription = "Torch",
                tint = if (torchOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(28.dp)
            )
        }
        Text("Torch", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun StopScanControl(onStop: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onStop,
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(
                Icons.Filled.Stop,
                contentDescription = "Stop scanning",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text("Stop Scan", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SwitchCameraControl(lensFacing: Int, onSwitch: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onSwitch) {
            Icon(
                Icons.Filled.Cameraswitch,
                contentDescription = "Switch Camera",
                tint = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            if (lensFacing == CameraSelector.LENS_FACING_BACK) "Front Cam" else "Rear Cam",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun OfflineNotice() {
    val success = RakshakExtras.current.success
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Scanning will work offline",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(Spacing.xs))
        Icon(Icons.Filled.Wifi, contentDescription = null, tint = success, modifier = Modifier.size(16.dp))
    }
}

/**
 * Draws the live tracking boxes over the camera preview.
 *
 * The geometry here is not decoration. [FaceBox] coordinates are normalised
 * against the *analysis* frame, while what the volunteer sees is that frame
 * cropped to fill the screen ([PreviewView.ScaleType.FILL_CENTER]). Stretching
 * normalised coordinates straight onto the canvas — which is what this used to
 * do — leaves every box off the face by the difference between the frame's
 * aspect ratio and the screen's, which on a modern 20:9 phone is most of a head.
 * So the same fill-centre transform the preview applies is reproduced: scale by
 * the larger of the two ratios, then centre the overflow.
 *
 * [mirrored] handles the other half of it. PreviewView mirrors the front camera
 * so the volunteer sees themselves the right way round, but the analysis bitmap
 * is never mirrored — without flipping x, every box on the selfie lens tracks
 * the mirror image of the face instead of the face.
 */
@Composable
private fun FaceOverlay(
    faces: List<FaceBox>,
    frameWidth: Int,
    frameHeight: Int,
    mirrored: Boolean,
) {
    // Text on a Compose canvas goes through the native canvas; the paints are
    // remembered so the overlay does not allocate two objects per face per frame.
    val labelPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 34f
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }
    val labelBackdrop = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.argb(160, 0, 0, 0)
            isAntiAlias = true
        }
    }

    // Read outside the DrawScope lambda below: it is not @Composable, so
    // MaterialTheme.colorScheme cannot be resolved inside it.
    val matchColor = RakshakExtras.current.success
    val turnedAwayColor = RakshakExtras.current.warning
    val trackingColor = MaterialTheme.colorScheme.tertiary

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        if (frameWidth <= 0 || frameHeight <= 0) return@Canvas
        val canvasW = size.width
        val canvasH = size.height

        val scale = maxOf(canvasW / frameWidth, canvasH / frameHeight)
        val offsetX = (canvasW - frameWidth * scale) / 2f
        val offsetY = (canvasH - frameHeight * scale) / 2f

        fun toCanvasX(normalized: Float): Float =
            (if (mirrored) 1f - normalized else normalized) * frameWidth * scale + offsetX

        faces.forEach { face ->
            val xs = listOf(toCanvasX(face.left), toCanvasX(face.right))
            val boxLeft = xs.min()
            val boxRight = xs.max()
            val boxTop = face.top * frameHeight * scale + offsetY
            val boxBottom = face.bottom * frameHeight * scale + offsetY

            val boxColor = when {
                face.isMatch -> matchColor
                !face.isFrontal -> turnedAwayColor // turned away, not embedded
                else -> trackingColor
            }

            drawRoundRect(
                color = boxColor,
                topLeft = androidx.compose.ui.geometry.Offset(boxLeft, boxTop),
                size = androidx.compose.ui.geometry.Size(
                    (boxRight - boxLeft).coerceAtLeast(10f),
                    (boxBottom - boxTop).coerceAtLeast(10f),
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f),
                // A confirmed match is drawn heavier so it is unmistakable in a
                // frame that may hold a dozen ordinary tracking boxes.
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = if (face.isMatch) 10f else 5f,
                ),
            )

            // The per-face score is the single most useful thing on this screen:
            // it is the difference between "the scanner is running and this is not
            // the child" and "the scanner is doing nothing". A face that was gated
            // out has no score, and says why instead.
            val score = face.score
            val label = when {
                face.isMatch -> "MATCH " + (score ?: 0f).percent()
                score != null -> score.percent()
                !face.isFrontal -> "look up"
                else -> null
            } ?: return@forEach

            val textWidth = labelPaint.measureText(label)
            val labelTop = (boxTop - 46f).coerceAtLeast(0f)
            drawContext.canvas.nativeCanvas.apply {
                drawRoundRect(
                    boxLeft, labelTop, boxLeft + textWidth + 10f, labelTop + 42f,
                    8f, 8f, labelBackdrop,
                )
                drawText(label, boxLeft + 5f, labelTop + 31f, labelPaint)
            }
        }
    }
}

private fun Float.percent(): String = "${(this * 100).toInt()}%"

/**
 * Popup shown the moment a face match is detected on the live camera feed:
 * side-by-side compare of the alert's original photo against the face just
 * captured, the alert's details below, and the similarity score — so the
 * volunteer makes the final call from evidence, with the percentage surfaced
 * only here rather than on the live camera view.
 */
@Composable
private fun MatchPopupDialog(
    alert: Alert,
    faceCrop: android.graphics.Bitmap,
    confidence: Float,
    framesFused: Int,
    queuedCount: Int,
    submitting: Boolean,
    error: String?,
    onReject: () -> Unit,
    onConfirm: () -> Unit,
) {
    val extras = RakshakExtras.current
    val windowInfo = rememberWindowInfo()
    // A phone rotated to landscape has roughly a third less height to work
    // with. The photo pair alone (140dp tall, stacked above the score, the
    // details, and two 52dp buttons) does not fit, and this dialog cannot be
    // dismissed by tapping outside — a volunteer stuck mid-scan with an
    // unreachable Confirm button is exactly the failure this app exists to
    // avoid. Landscape gets a two-column split (evidence left, decision
    // right) instead of one long stack, and — as a safety net regardless of
    // orientation or font scale — the whole thing scrolls.
    val isLandscape = windowInfo.isLandscape

    Dialog(
        onDismissRequest = onReject,
        properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = if (isLandscape) 720.dp else 480.dp)
                .heightIn(max = (windowInfo.heightDp * 0.9f).dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = Spacing.xs,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.xl)
            ) {
                MatchDialogHeader(queuedCount, extras)
                Spacer(modifier = Modifier.height(Spacing.lg))

                if (isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            MatchPhotoPair(alert, faceCrop, photoHeight = 110.dp)
                            Spacer(modifier = Modifier.height(Spacing.lg))
                            MatchScoreRow(confidence, framesFused, extras)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            MatchDetails(alert)
                            if (error != null) {
                                Spacer(modifier = Modifier.height(Spacing.md))
                                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(Spacing.lg))
                            MatchDecisionPrompt()
                            Spacer(modifier = Modifier.height(Spacing.md))
                            MatchActionButtons(submitting, extras, onReject, onConfirm)
                        }
                    }
                } else {
                    MatchPhotoPair(alert, faceCrop, photoHeight = 140.dp)
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    MatchScoreRow(confidence, framesFused, extras)
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    MatchDetails(alert)
                    if (error != null) {
                        Spacer(modifier = Modifier.height(Spacing.md))
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    MatchDecisionPrompt()
                    Spacer(modifier = Modifier.height(Spacing.md))
                    MatchActionButtons(submitting, extras, onReject, onConfirm)
                }
            }
        }
    }
}

@Composable
private fun MatchDialogHeader(queuedCount: Int, extras: RakshakExtendedColors) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Possible Match Found", style = MaterialTheme.typography.titleLarge)
        // Other faces in the same frame also crossed the threshold and are
        // waiting their turn — surfaced so the volunteer knows to stay put and
        // work through all of them, not just this one.
        if (queuedCount > 0) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = extras.warningContainer,
            ) {
                Text(
                    if (queuedCount == 1) "+1 more waiting" else "+$queuedCount more waiting",
                    color = extras.onWarningContainer,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun MatchPhotoPair(alert: Alert, faceCrop: android.graphics.Bitmap, photoHeight: androidx.compose.ui.unit.Dp) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Missing Child Photo",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            AsyncImage(
                // Prefer the mesh thumbnail bytes: on an offline phone imageUrl
                // cannot be fetched, and the thumbnail is the only way this
                // side-by-side compare shows a face.
                model = alert.thumbnail ?: alert.imageUrl,
                contentDescription = "Missing child photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(photoHeight)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Detected Face",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Image(
                bitmap = faceCrop.asImageBitmap(),
                contentDescription = "Detected face",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(photoHeight)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
        }
    }
}

@Composable
private fun MatchScoreRow(confidence: Float, framesFused: Int, extras: RakshakExtendedColors) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Similarity Score", style = MaterialTheme.typography.titleSmall)
            // How the score was reached is part of how much to trust it: one
            // strong frame and three agreeing frames are different kinds of
            // evidence, and the volunteer is the one being asked to make the call.
            Text(
                if (framesFused > 1) "averaged over $framesFused frames" else "single frame",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${(confidence * 100).toInt()}%",
                style = MaterialTheme.typography.headlineSmall,
                color = if (confidence >= Constants.STRONG_MATCH_THRESHOLD) extras.success else MaterialTheme.colorScheme.tertiary,
            )
            Text(
                if (confidence >= Constants.STRONG_MATCH_THRESHOLD) "strong" else "possible",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MatchDetails(alert: Alert) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DetailRow("Child Name", alert.childName)
        DetailRow("Age / Gender", "${alert.age} yrs · ${alert.gender}")
        DetailRow("Clothing", alert.clothingDesc)
        DetailRow("Last Seen", alert.lastSeen)
        if (alert.identifyingMarks.isNotBlank()) {
            DetailRow("Identifying Marks", alert.identifyingMarks)
        }
    }
}

@Composable
private fun MatchDecisionPrompt() {
    Text(
        "Does this look like the missing child?",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun MatchActionButtons(
    submitting: Boolean,
    extras: RakshakExtendedColors,
    onReject: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Button(
            onClick = onReject,
            enabled = !submitting,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = MaterialTheme.shapes.extraLarge,
            contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xs),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                disabledContentColor = MaterialTheme.colorScheme.onError.copy(alpha = 0.7f),
            )
        ) {
            Text(
                "Reject",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Button(
            onClick = onConfirm,
            // Disabled rather than just visually busy: a tap queued up while
            // submitting used to fire a second reportMatch() once this one
            // returned, relaying the same sighting twice.
            enabled = !submitting,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = MaterialTheme.shapes.extraLarge,
            contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xs),
            colors = ButtonDefaults.buttonColors(
                containerColor = extras.success,
                contentColor = extras.onSuccess,
                disabledContainerColor = extras.success.copy(alpha = 0.6f),
                disabledContentColor = extras.onSuccess,
            )
        ) {
            if (submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = extras.onSuccess,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    "Reporting...",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    "Confirm Match",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchConfirmationScreen(childName: String, location: String, onDone: () -> Unit) {
    val success = RakshakExtras.current.success
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Confirm Match", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Success",
                tint = success,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(Spacing.xl))
            Text("Thank You!", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                "Your match has been submitted successfully.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(48.dp))

            val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
            val currentTime = remember { dateFormat.format(Date()) }

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
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text("Done", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
