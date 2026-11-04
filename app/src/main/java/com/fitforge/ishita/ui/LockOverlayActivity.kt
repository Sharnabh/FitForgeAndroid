package com.fitforge.ishita.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitforge.FitForgeThemeColors
import com.fitforge.MainActivity
import com.fitforge.ModernDarkColorScheme
import com.fitforge.ishita.data.AccountabilityBlockManager
import kotlinx.coroutines.delay

class LockOverlayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TARGET_NAME = "extra_target_name"
        const val EXTRA_IS_WEBSITE = "extra_is_website"
        const val EXTRA_REASON = "extra_reason"
        const val EXTRA_ID = "extra_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetName = intent.getStringExtra(EXTRA_TARGET_NAME) ?: "Restricted Target"
        val isWebsite = intent.getBooleanExtra(EXTRA_IS_WEBSITE, false)
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "A planned workout was not completed."
        val targetId = intent.getStringExtra(EXTRA_ID) ?: targetName

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(homeIntent)
                finish()
            }
        })

        setContent {
            MaterialTheme(colorScheme = ModernDarkColorScheme) {
                LockOverlayScreen(
                    targetName = targetName,
                    isWebsite = isWebsite,
                    reason = reason,
                    onOpenWorkout = {
                        val mainIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(mainIntent)
                        finish()
                    },
                    onGoHome = {
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(homeIntent)
                        finish()
                    },
                    onEmergencyOverride = {
                        AccountabilityBlockManager.get(applicationContext).unblock(targetId)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun LockOverlayScreen(
    targetName: String,
    isWebsite: Boolean,
    reason: String,
    onOpenWorkout: () -> Unit,
    onGoHome: () -> Unit,
    onEmergencyOverride: () -> Unit
) {
    var showOverrideConfirm by remember { mutableStateOf(false) }
    var overrideCountdown by remember { mutableIntStateOf(5) }

    LaunchedEffect(showOverrideConfirm) {
        if (showOverrideConfirm) {
            overrideCountdown = 5
            while (overrideCountdown > 0) {
                delay(1000)
                overrideCountdown--
            }
        }
    }

    Surface(
        color = FitForgeThemeColors.Background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Glowing Lock Icon
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(FitForgeThemeColors.Secondary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = FitForgeThemeColors.Secondary,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "FITFORGE ACCOUNTABILITY",
                color = FitForgeThemeColors.Secondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "${if (isWebsite) "Website" else "App"} Restricted",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = targetName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = FitForgeThemeColors.Primary
            )

            Spacer(Modifier.height(20.dp))

            // Reason Card
            Card(
                colors = CardDefaults.cardColors(containerColor = FitForgeThemeColors.SurfaceElevated),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = FitForgeThemeColors.Secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Why is this blocked?",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Main Action: Open Workout
            Button(
                onClick = onOpenWorkout,
                colors = ButtonDefaults.buttonColors(containerColor = FitForgeThemeColors.Primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = FitForgeThemeColors.OnPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "LOG OR COMPLETE WORKOUT",
                    color = FitForgeThemeColors.OnPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(12.dp))

            // Secondary Action: Go Home
            OutlinedButton(
                onClick = onGoHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Return to Home Screen")
            }

            Spacer(Modifier.height(16.dp))

            // Emergency Override Flow
            AnimatedVisibility(visible = !showOverrideConfirm) {
                TextButton(onClick = { showOverrideConfirm = true }) {
                    Text(
                        "Emergency Override",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            AnimatedVisibility(visible = showOverrideConfirm) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Override allows bypassing your commitment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onEmergencyOverride,
                        enabled = overrideCountdown == 0,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (overrideCountdown > 0) "Wait $overrideCountdown s..." else "CONFIRM OVERRIDE & UNBLOCK",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
