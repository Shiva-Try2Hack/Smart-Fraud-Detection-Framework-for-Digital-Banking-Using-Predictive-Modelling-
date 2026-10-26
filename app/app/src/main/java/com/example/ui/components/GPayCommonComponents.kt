package com.example.ui.components

import com.example.R
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.repository.GPayRepository
import com.example.domain.model.ServerConnectionState
import com.example.domain.model.UserAccount
import com.example.ui.theme.GPayBlue
import com.example.ui.theme.GPayBorder
import com.example.ui.theme.GPayGreen
import com.example.ui.theme.GPayGreenAccent
import com.example.ui.theme.GPayGreenLight
import com.example.ui.theme.GPayOffWhite
import com.example.ui.theme.GPayPillFill
import com.example.ui.theme.GPayPrimaryText
import com.example.ui.theme.GPayRed
import com.example.ui.theme.GPayRedAccent
import com.example.ui.theme.GPayRedLight
import com.example.ui.theme.GPaySecondaryText
import com.example.ui.theme.GPayWhite
import com.example.ui.theme.GPayYellowAccent
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.avatarColorForName

/**
 * Authentic 4-Color Google "G" + "Pay" Emblem Badge
 */
@Composable
fun GooglePayLogoHeader(
    modifier: Modifier = Modifier,
    logoSize: Dp = 28.dp,
    isDarkBackground: Boolean = false
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        val logoRes = if (isDarkBackground) R.drawable.nexus_pay_logo_white else R.drawable.nexus_pay_logo_dark
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = logoRes),
            contentDescription = "NEXUS PAY Logo",
            modifier = Modifier.size(logoSize)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "NEXUS PAY",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = if (isDarkBackground) Color.White else GPayPrimaryText,
                letterSpacing = 0.8.sp
            )
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLine(
    color: Color,
    startThreshold: Float,
    start: Offset,
    end: Offset,
    strokeWidth: Float
) {
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Butt
    )
}

/**
 * Soft Pastel Landscape Horizon Illustration in authentic Google Pay India (Tez) style.
 */
@Composable
fun GPayHorizonBanner(
    isDarkMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val bgGradient = if (isDarkMode) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0F1B2F),
                    Color(0xFF132238),
                    Color(0xFF1A2A44)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFE8F0FE),
                    Color(0xFFF1F6FD),
                    Color(0xFFFFFFFF)
                )
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawRect(brush = bgGradient)

            // Dynamic futuristic Nexus energy curves
            val glowColor = if (isDarkMode) Color(0xFF1A73E8).copy(alpha = 0.25f) else Color(0xFF4285F4).copy(alpha = 0.12f)
            drawCircle(
                color = glowColor,
                radius = h * 0.75f,
                center = Offset(w * 0.85f, h * 0.25f)
            )
            drawCircle(
                color = if (isDarkMode) Color(0xFF00E676).copy(alpha = 0.12f) else Color(0xFF34A853).copy(alpha = 0.08f),
                radius = h * 0.5f,
                center = Offset(w * 0.15f, h * 0.35f)
            )

            // Bottom smooth fade into content
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        if (isDarkMode) com.example.ui.theme.GPayDarkBackground else GPayWhite
                    ),
                    startY = h * 0.65f,
                    endY = h
                )
            )
        }

        // Overlay with NEXUS PAY Watermark / Emblem
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp, top = 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(
                    id = if (isDarkMode) R.drawable.nexus_pay_logo_white else R.drawable.nexus_pay_logo_accent
                ),
                contentDescription = "NEXUS PAY Logo",
                alpha = if (isDarkMode) 0.65f else 0.85f,
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
            )
        }
    }
}

/**
 * Circular Contact Avatar in authentic Google Pay style.
 * Displays uploaded Base64 profile photo if set, otherwise a clean initial.
 */
@Composable
fun GPayContactAvatar(
    name: String,
    size: Dp = 52.dp,
    fontSizeSp: Int = 20,
    showVerifiedBadge: Boolean = false,
    profileImage: String? = null,
    modifier: Modifier = Modifier
) {
    val bgColor = remember(name) { avatarColorForName(name) }
    val initial = remember(name) {
        name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "G"
    }

    val imageBitmap = remember(profileImage) {
        profileImage?.let { b64 ->
            try {
                val clean = if (b64.contains(",")) b64.substringAfter(",") else b64
                val decoded = android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(decoded, 0, decoded.size)?.asImageBitmap()
            } catch (_: Exception) { null }
        }
    }

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(if (imageBitmap != null) Color.Transparent else bgColor)
                .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (imageBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = imageBitmap,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Text(
                    text = initial,
                    color = Color.White,
                    fontSize = fontSizeSp.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        if (showVerifiedBadge) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(1.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Verified Account",
                    tint = GPayGreenAccent,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Direct Connection Status Pill Badge:
 * - On HTTP 200: Green dot (#1E8E3E): "Connected to Bank Server • Online"
 * - On failure: Red dot (#D93025): "Offline — Tap to change IP"
 */
@Composable
fun ServerStatusBadge(
    serverState: ServerConnectionState,
    serverUrl: String,
    detailText: String,
    onClickConfigure: () -> Unit,
    onRetryPing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = serverState == ServerConnectionState.CONNECTED
    val isChecking = serverState == ServerConnectionState.CHECKING

    val containerColor = when (serverState) {
        ServerConnectionState.CONNECTED -> GPayGreenLight
        ServerConnectionState.CHECKING -> GPayPillFill
        ServerConnectionState.OFFLINE -> GPayRedLight
    }
    val dotAndTextColor = when (serverState) {
        ServerConnectionState.CONNECTED -> GPayGreen // #1E8E3E
        ServerConnectionState.CHECKING -> GPayBlue
        ServerConnectionState.OFFLINE -> GPayRed // #D93025
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClickConfigure)
            .testTag("server_status_badge"),
        color = containerColor,
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(
            width = 0.8.dp,
            color = dotAndTextColor.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = dotAndTextColor
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotAndTextColor)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = when (serverState) {
                        ServerConnectionState.CONNECTED -> "Connected to Bank Server • Online"
                        ServerConnectionState.CHECKING -> "Pinging Bank Server..."
                        ServerConnectionState.OFFLINE -> "Offline — Tap to change IP"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = dotAndTextColor
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = serverUrl,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = MonoFontFamily,
                        color = dotAndTextColor.copy(alpha = 0.82f),
                        fontSize = 10.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (isConnected) Icons.Default.Dns else Icons.Default.Refresh,
                contentDescription = "Configure or Retry Ping",
                tint = dotAndTextColor,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onRetryPing() }
            )
        }
    }
}

/**
 * Clean Google-styled Material 3 Dialog allowing the user to type their PC's current IP,
 * test connection with a live Ping button (`GET /`), and persist the working IP in SharedPreferences.
 */
@Composable
fun ServerConfigDialog(
    currentUrl: String,
    serverState: ServerConnectionState,
    statusDetail: String,
    isPinging: Boolean = false,
    onDismiss: () -> Unit,
    onLivePingTest: (String) -> Unit = {},
    onSaveAndTest: (String) -> Unit
) {
    var urlInput by remember(currentUrl) { mutableStateOf(currentUrl) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = GPayWhite,
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("server_config_dialog")
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = GPayBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Configure Bank Server IP",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = GPayPrimaryText
                            )
                        )
                        Text(
                            text = "Nexus Pay FDS Server API (Port 8000)",
                            style = MaterialTheme.typography.labelSmall.copy(color = GPaySecondaryText)
                        )
                    }
                }

                Text(
                    text = "Enter your PC's Wi-Fi IP address (e.g. 192.168.29.40) or use 10.0.2.2:8000 for Android Emulator. Saved automatically in SharedPreferences.",
                    style = MaterialTheme.typography.bodySmall.copy(color = GPaySecondaryText)
                )

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Server IP or Full URL") },
                    placeholder = { Text("192.168.29.40:8000") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_url_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GPayBlue,
                        unfocusedBorderColor = GPayBorder
                    )
                )

                // Quick Preset Chips for Physical Wi-Fi vs Emulator vs Localhost
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            urlInput = GPayRepository.DEFAULT_SERVER_URL
                            onLivePingTest(GPayRepository.DEFAULT_SERVER_URL)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "192.168.29.40",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = MonoFontFamily,
                                fontSize = 10.sp
                            )
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            urlInput = GPayRepository.EMULATOR_SERVER_URL
                            onLivePingTest(GPayRepository.EMULATOR_SERVER_URL)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "10.0.2.2 (Emu)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = MonoFontFamily,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                // Live Health-Check Status Card
                Surface(
                    color = when (serverState) {
                        ServerConnectionState.CONNECTED -> GPayGreenLight
                        ServerConnectionState.CHECKING -> GPayPillFill
                        ServerConnectionState.OFFLINE -> GPayRedLight
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isPinging || serverState == ServerConnectionState.CHECKING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = GPayBlue
                            )
                        } else {
                            Icon(
                                imageVector = if (serverState == ServerConnectionState.CONNECTED) {
                                    Icons.Default.CheckCircle
                                } else {
                                    Icons.Default.ErrorOutline
                                },
                                contentDescription = null,
                                tint = if (serverState == ServerConnectionState.CONNECTED) GPayGreen else GPayRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = statusDetail,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = GPayPrimaryText,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                // Live Ping Button + Save & Connect Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onLivePingTest(urlInput) },
                        enabled = !isPinging,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.testTag("live_ping_server_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = null,
                            tint = GPayBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Live Ping", color = GPayBlue)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) {
                            Text("Close", color = GPaySecondaryText)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = { onSaveAndTest(urlInput) },
                            colors = ButtonDefaults.buttonColors(containerColor = GPayBlue),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.testTag("save_server_url_button")
                        ) {
                            Text("Save & Connect")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Personal UPI QR Code Dialog showing the user's live UPI handle & deterministic QR matrix.
 */
@Composable
fun MyUpiQrDialog(
    user: UserAccount,
    onDismiss: () -> Unit,
    onCopiedMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val upiUri = remember(user) {
        "upi://pay?pa=${user.upiId}&pn=${UriEncode(user.fullName)}&cu=INR"
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = GPayWhite,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("my_upi_qr_dialog")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GooglePayLogoHeader(logoSize = 22.dp)
                Spacer(modifier = Modifier.height(16.dp))

                GPayContactAvatar(name = user.fullName, size = 56.dp, fontSizeSp = 22, showVerifiedBadge = true)
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = user.fullName,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = GPayPrimaryText
                    )
                )
                Text(
                    text = user.upiId,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = MonoFontFamily,
                        color = GPaySecondaryText
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(GPayWhite)
                        .border(1.5.dp, GPayBorder, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    DeterministicQrCanvas(seed = upiUri, modifier = Modifier.fillMaxSize())
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = GPayGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Scan with any NEXUS PAY / BHIM UPI app",
                        style = MaterialTheme.typography.bodySmall.copy(color = GPaySecondaryText)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            copyToClipboard(context, "UPI ID", user.upiId)
                            onCopiedMessage("Copied UPI ID: ${user.upiId}")
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy UPI")
                    }

                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Pay ${user.fullName} on NEXUS PAY\nUPI ID: ${user.upiId}\nQR Link: $upiUri"
                                )
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share UPI QR"))
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = GPayBlue),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share QR")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeterministicQrCanvas(seed: String, modifier: Modifier = Modifier) {
    val qrBitmap = remember(seed) {
        try {
            val writer = com.google.zxing.qrcode.QRCodeWriter()
            val bitMatrix = writer.encode(
                seed,
                com.google.zxing.BarcodeFormat.QR_CODE,
                512,
                512,
                mapOf(
                    com.google.zxing.EncodeHintType.MARGIN to 1,
                    com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M
                )
            )
            val w = bitMatrix.width
            val h = bitMatrix.height
            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            for (x in 0 until w) {
                for (y in 0 until h) {
                    bmp.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }

    if (qrBitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = qrBitmap.asImageBitmap(),
            contentDescription = "Scannable UPI QR Code",
            modifier = modifier
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = GPayBlue, modifier = Modifier.size(24.dp))
        }
    }
}

fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
}

private fun UriEncode(input: String): String =
    input.trim().replace(" ", "%20")
