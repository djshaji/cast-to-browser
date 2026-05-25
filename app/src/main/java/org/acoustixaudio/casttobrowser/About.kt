package org.acoustixaudio.casttobrowser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.acoustixaudio.casttobrowser.ui.theme.CastToBrowserTheme

class About : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CastToBrowserTheme {
                AboutScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About Cast to Browser") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // App Name & Version
            Image(
                painter = painterResource(id = R.drawable.dev),
                contentDescription = "App Icon",
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Cast to Browser",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Version 1.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = "Transform your Android device into a versatile media server and remote control. Cast local videos and images directly to any web browser on the same Wi-Fi network.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Author Section
            Text(
                text = "Developer",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "djshaji",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Links
            LinkButton(
                label = "GitHub Repository",
                url = "https://github.com/djshaji/cast-to-browser"
            )

            LinkButton(
                label = "Website",
                url = "https://shaji.in"
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Features
            Text(
                text = "Key Features",
                style = MaterialTheme.typography.titleMedium
            )

            FeatureItem("Embedded Ktor Media Server with Partial Content streaming")
            FeatureItem("Real-time WebSocket Remote for instant synchronization")
            FeatureItem("Media Discovery & Gallery with high-quality thumbnails (Coil)")
            FeatureItem("Material 3 Adaptive UI for phones and tablets")
            FeatureItem("Share Support for instant media casting")
            FeatureItem("Dynamic Port Selection for networking robustness")
            FeatureItem("Edge-to-Edge Experience with immersive system bars")

            Spacer(modifier = Modifier.height(16.dp))

            // License
            Text(
                text = "Licensed under MIT License",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun LinkButton(label: String, url: String) {
    val context = LocalContext.current
    Button(
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        colors = ButtonDefaults.filledTonalButtonColors()
    ) {
        Text(label)
    }
}

@Composable
fun FeatureItem(text: String) {
    Text(
        text = "• $text",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Left,
        modifier = Modifier.fillMaxWidth()
    )
}