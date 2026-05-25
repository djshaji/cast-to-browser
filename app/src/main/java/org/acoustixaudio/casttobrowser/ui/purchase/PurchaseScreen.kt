package org.acoustixaudio.casttobrowser.ui.purchase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PurchaseScreen(
    isBillingReady: Boolean,
    priceLabel: String,
    onPurchaseClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Unlock Pro",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "You have cast 2 items. Upgrade to Pro to keep casting without limits.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (priceLabel.isNotEmpty()) {
                    Text(
                        text = priceLabel,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (!isBillingReady) {
                    Text(
                        text = "Connecting to Google Play Billing...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onPurchaseClick,
                enabled = isBillingReady,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Buy Pro")
            }
        },
        dismissButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Later")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "One-time in-app purchase",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        }
    )
}

