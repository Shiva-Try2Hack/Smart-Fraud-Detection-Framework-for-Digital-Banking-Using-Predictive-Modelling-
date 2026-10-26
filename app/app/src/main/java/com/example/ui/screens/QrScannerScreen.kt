package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.domain.model.ParsedUpiPayload
import com.example.domain.model.UserAccount
import com.example.domain.util.PaymentValidators
import com.example.ui.components.GPayContactAvatar
import com.example.ui.theme.GPayBlue
import com.example.ui.theme.GPayBorder
import com.example.ui.theme.GPayGreenAccent
import com.example.ui.theme.GPayOffWhite
import com.example.ui.theme.GPayPillFill
import com.example.ui.theme.GPayPrimaryText
import com.example.ui.theme.GPayRedAccent
import com.example.ui.theme.GPaySecondaryText
import com.example.ui.theme.GPayWhite
import com.example.ui.theme.GPayYellowAccent
import com.example.ui.theme.MonoFontFamily
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@OptIn(ExperimentalGetImage::class)
@Composable
fun QrScannerScreen(
    currentUser: UserAccount?,
    liveAccounts: List<UserAccount>,
    onQrDetected: (ParsedUpiPayload) -> Unit,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var torchEnabled by remember { mutableStateOf(false) }
    var activeCamera by remember { mutableStateOf<Camera?>(null) }
    var manualQrInput by remember { mutableStateOf("") }
    var scanError by remember { mutableStateOf<String?>(null) }
    var alreadyTriggered by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    DisposableEffect(Unit) {
        onDispose {
            barcodeScanner.close()
        }
    }

    // Zero-permission Photo Picker for scanning QR from Gallery
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val image = InputImage.fromFilePath(context, uri)
                barcodeScanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val rawValue = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                        if (rawValue != null) {
                            val parsed = PaymentValidators.parseUpiQrString(rawValue)
                            if (parsed != null) {
                                onQrDetected(parsed)
                            } else {
                                scanError = "Unsupported QR format: $rawValue"
                            }
                        } else {
                            scanError = "No QR code found in the selected image"
                        }
                    }
                    .addOnFailureListener {
                        scanError = "Failed to decode QR from image"
                    }
            } catch (e: Exception) {
                scanError = "Could not read selected image"
            }
        }
    }

    val otherLiveAccounts = remember(liveAccounts, currentUser) {
        liveAccounts.filter { acc ->
            acc.upiId != currentUser?.upiId && acc.id != currentUser?.id
        }
    }

    // Animated Laser Line Progress (0f .. 1f)
    val infiniteTransition = rememberInfiniteTransition(label = "qr_laser_transition")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "qr_laser_line"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101216))
            .testTag("qr_scanner_screen")
    ) {
        // 1. Live CameraX Preview + ML Kit QR Analyzer
        if (hasCameraPermission) {
            val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
            DisposableEffect(Unit) {
                onDispose { analysisExecutor.shutdown() }
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        runCatching {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setTargetResolution(Size(1280, 720))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null && !alreadyTriggered) {
                                    val inputImage = InputImage.fromMediaImage(
                                        mediaImage,
                                        imageProxy.imageInfo.rotationDegrees
                                    )
                                    barcodeScanner.process(inputImage)
                                        .addOnSuccessListener { barcodes ->
                                            for (barcode in barcodes) {
                                                val raw = barcode.rawValue ?: continue
                                                val parsed = PaymentValidators.parseUpiQrString(raw)
                                                if (parsed != null && !alreadyTriggered) {
                                                    alreadyTriggered = true
                                                    onQrDetected(parsed)
                                                    break
                                                }
                                            }
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }

                            cameraProvider.unbindAll()
                            val cam = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                            activeCamera = cam
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )
        }

        // 2. Viewfinder Brackets + Animated Laser Line Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 140.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.size(270.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val cornerLen = 48.dp.toPx()
                    val strokeW = 5.dp.toPx()

                    // Google 4-color corner brackets (Blue, Red, Yellow, Green)
                    // Top-left (Google Blue)
                    drawLine(
                        color = GPayBlue,
                        start = Offset(0f, cornerLen),
                        end = Offset(0f, 0f),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = GPayBlue,
                        start = Offset(0f, 0f),
                        end = Offset(cornerLen, 0f),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )

                    // Top-right (Google Red)
                    drawLine(
                        color = GPayRedAccent,
                        start = Offset(w - cornerLen, 0f),
                        end = Offset(w, 0f),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = GPayRedAccent,
                        start = Offset(w, 0f),
                        end = Offset(w, cornerLen),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )

                    // Bottom-left (Google Yellow)
                    drawLine(
                        color = GPayYellowAccent,
                        start = Offset(0f, h - cornerLen),
                        end = Offset(0f, h),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = GPayYellowAccent,
                        start = Offset(0f, h),
                        end = Offset(cornerLen, h),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )

                    // Bottom-right (Google Green)
                    drawLine(
                        color = GPayGreenAccent,
                        start = Offset(w - cornerLen, h),
                        end = Offset(w, h),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = GPayGreenAccent,
                        start = Offset(w, h - cornerLen),
                        end = Offset(w, h),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )

                    // Animated Laser Line
                    val laserY = h * laserProgress
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                GPayBlue,
                                Color(0xFF8AB4F8),
                                GPayBlue,
                                Color.Transparent
                            )
                        ),
                        start = Offset(14f, laserY),
                        end = Offset(w - 14f, laserY),
                        strokeWidth = 3.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // 3. Top Controls Bar (Close, Flashlight, Gallery Picker)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .testTag("qr_scanner_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close scanner",
                    tint = Color.White
                )
            }

            Text(
                text = "Scan any QR code",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Flashlight Toggle
                IconButton(
                    onClick = {
                        val next = !torchEnabled
                        torchEnabled = next
                        activeCamera?.cameraControl?.enableTorch(next)
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .testTag("qr_torch_button")
                ) {
                    Icon(
                        imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Toggle flashlight",
                        tint = if (torchEnabled) GPayYellowAccent else Color.White
                    )
                }

                // Upload from Gallery
                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .testTag("qr_gallery_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Upload QR from gallery",
                        tint = Color.White
                    )
                }
            }
        }

        // 4. Bottom Google Pay Sheet: Live QR Detection + UPI URI Parser
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = GPayWhite
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = GPayBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Point camera at any UPI / Nexus Pay QR",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = GPayPrimaryText
                            )
                        )
                    }
                    Surface(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(50),
                        color = GPayPillFill
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = GPayBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Upload from gallery",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = GPayBlue,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }

                if (otherLiveAccounts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "LIVE SERVER QR CODES (TAP TO SCAN INSTANTLY)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = GPaySecondaryText,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(otherLiveAccounts) { account ->
                            val qrUri = "upi://pay?pa=${account.upiId}&pn=${account.fullName.replace(" ", "%20")}&cu=INR"
                            Surface(
                                onClick = {
                                    PaymentValidators.parseUpiQrString(qrUri)?.let { onQrDetected(it) }
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = GPayOffWhite,
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, GPayBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    GPayContactAvatar(name = account.fullName, size = 30.dp, fontSizeSp = 13)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = account.fullName,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = GPayPrimaryText,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = account.upiId,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = MonoFontFamily,
                                                color = GPayBlue,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Direct UPI QR string parser field (`upi://pay?pa=...&pn=...&am=...`)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualQrInput,
                        onValueChange = {
                            manualQrInput = it
                            scanError = null
                        },
                        placeholder = {
                            Text(
                                "upi://pay?pa=user@okbank&pn=Name&am=250",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily)
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("qr_manual_uri_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GPayBlue,
                            unfocusedBorderColor = GPayBorder
                        )
                    )
                    Button(
                        onClick = {
                            val parsed = PaymentValidators.parseUpiQrString(manualQrInput)
                            if (parsed != null) {
                                onQrDetected(parsed)
                            } else {
                                scanError = "Enter a valid UPI QR URI (upi://pay?pa=...) or UPI ID"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GPayBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("qr_parse_pay_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pay")
                    }
                }

                if (!scanError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = scanError!!,
                        style = MaterialTheme.typography.bodySmall.copy(color = GPayRedAccent),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
