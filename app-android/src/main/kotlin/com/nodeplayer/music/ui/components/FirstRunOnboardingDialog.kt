package com.nodeplayer.music.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun FirstRunOnboardingDialog(
    onDismiss: () -> Unit,
    onRequestPermissions: () -> Unit,
    onStartScan: () -> Unit,
    accentColor: Color = Color(0xFF6C5CE7)
) {
    var currentStep by remember { mutableIntStateOf(1) }

    Dialog(
        onDismissRequest = { /* Force explicit completion or skip */ },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF14161F).copy(alpha = 0.95f),
            tonalElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Step Indicator Pills
                Row(
                    modifier = Modifier.padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 1..3) {
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (i == currentStep) 24.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == currentStep) accentColor else Color.White.copy(alpha = 0.2f)
                                )
                        )
                    }
                }

                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "OnboardingContent"
                ) { step ->
                    when (step) {
                        1 -> OnboardingStepCard(
                            icon = Icons.Rounded.GraphicEq,
                            accentColor = accentColor,
                            title = "Audiophile Fidelity",
                            description = "Node Player delivers pure 32-bit floating-point audio processing, truthful bit-perfect signal path inspection, and algorithmic Freeverb acoustics without artificial coloring.",
                            actionLabel = "Grant Permissions",
                            onAction = {
                                onRequestPermissions()
                                currentStep = 2
                            }
                        )
                        2 -> OnboardingStepCard(
                            icon = Icons.Rounded.FolderOpen,
                            accentColor = accentColor,
                            title = "Select Your Folders",
                            description = "Node automatically indexes your device's Music and Download directories. You can enable or disable individual folders at any time without deleting your library metadata.",
                            actionLabel = "Continue to Setup",
                            onAction = { currentStep = 3 }
                        )
                        3 -> OnboardingStepCard(
                            icon = Icons.Rounded.CheckCircle,
                            accentColor = accentColor,
                            title = "You're All Set",
                            description = "Let's perform an initial high-speed library scan to organize your songs, extract genuine FLAC/WAV/AAC metadata, and prepare your audio workspace.",
                            actionLabel = "Start Listening",
                            onAction = {
                                onStartScan()
                                onDismiss()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Skip or dismiss text
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.5f))
                ) {
                    Text(text = "Skip Tour", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun OnboardingStepCard(
    icon: ImageVector,
    accentColor: Color,
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(36.dp)
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            textAlign = TextAlign.Center
        )

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White.copy(alpha = 0.72f),
                lineHeight = 22.sp
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onAction,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}
