package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.domain.model.UserAccount
import com.example.ui.theme.*

val PROFILE_AVATAR_COLORS = listOf(
    Color(0xFF1A73E8), // Google/Nexus Blue
    Color(0xFF1E8E3E), // Green
    Color(0xFF9334E6), // Deep Purple
    Color(0xFFE8710A), // Warm Orange
    Color(0xFF007B83), // Teal
    Color(0xFFC5221F), // Crimson Red
    Color(0xFFE91E63), // Pink
    Color(0xFF3F51B5)  // Indigo
)

@Composable
fun EditProfileDialog(
    user: UserAccount,
    onDismiss: () -> Unit,
    onSave: (fullName: String, profession: String, address: String, avatarId: Int) -> Unit
) {
    var fullName by remember { mutableStateOf(user.fullName) }
    var profession by remember { mutableStateOf(user.profession ?: "") }
    var address by remember { mutableStateOf(user.address ?: "") }
    var selectedAvatarId by remember { mutableStateOf(user.avatarId.coerceIn(0, PROFILE_AVATAR_COLORS.lastIndex)) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = GPayWhite,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Edit Profile & Account",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = GPayPrimaryText
                    )
                )
                Text(
                    text = "Customize your visible cardholder details",
                    style = MaterialTheme.typography.bodySmall.copy(color = GPaySecondaryText)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Avatar color selector
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(PROFILE_AVATAR_COLORS[selectedAvatarId]),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = fullName.take(1).uppercase().ifBlank { "U" },
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Choose Profile Avatar Color",
                    style = MaterialTheme.typography.labelSmall.copy(color = GPaySecondaryText)
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(PROFILE_AVATAR_COLORS) { index, color ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selectedAvatarId == index) 3.dp else 1.dp,
                                    color = if (selectedAvatarId == index) GPayPrimaryText else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedAvatarId = index }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Legal Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = GPayBlue) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = profession,
                    onValueChange = { profession = it },
                    label = { Text("Profession / Title") },
                    placeholder = { Text("e.g. Software Engineer, Merchant") },
                    leadingIcon = { Icon(Icons.Default.Work, contentDescription = null, tint = GPayBlue) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Billing / Resident Address") },
                    placeholder = { Text("e.g. Mumbai, Maharashtra") },
                    leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, tint = GPayBlue) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            onSave(fullName.trim(), profession.trim(), address.trim(), selectedAvatarId)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = GPayBlue),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Save Details")
                    }
                }
            }
        }
    }
}
