package com.orienteer.app.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orienteer.app.presentation.components.PrimaryGreenButton
import com.orienteer.app.ui.components.OrienteerGhostButton
import com.orienteer.app.ui.theme.BrandForest
import com.orienteer.app.ui.theme.OrienteerCardShape
import com.orienteer.app.ui.theme.OrienteerSpacing

enum class NewFromRunMode {
    SameConstraints,
    EditCheckpoints,
    KnobsOnly
}

/**
 * History → New from this run… choices, then Go to Setup / Stay.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewFromRunSheet(
    onDismiss: () -> Unit,
    onChooseMode: (NewFromRunMode) -> Unit,
    pendingConfirm: NewFromRunMode?,
    onGoToSetup: () -> Unit,
    onStayInHistory: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OrienteerSpacing.md)
                .padding(bottom = OrienteerSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(OrienteerSpacing.sm)
        ) {
            Text(
                text = if (pendingConfirm == null) "New from this run" else "Applied",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (pendingConfirm == null) {
                Text(
                    text = "Reuse the distance and settings you used to generate — not a copy of every control on the course.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PrimaryGreenButton(
                    text = "Same constraints",
                    onClick = { onChooseMode(NewFromRunMode.SameConstraints) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Distance + min/max (+ prefs) and the locked pins you set before Generate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OrienteerGhostButton(
                    text = "Edit checkpoints",
                    onClick = { onChooseMode(NewFromRunMode.EditCheckpoints) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Open the picker with those locked pins so you can edit before activating.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OrienteerGhostButton(
                    text = "Knobs only",
                    onClick = { onChooseMode(NewFromRunMode.KnobsOnly) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Apply distance + min/max (+ prefs); clear locked pins. Keeps start/finish.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = when (pendingConfirm) {
                        NewFromRunMode.SameConstraints ->
                            "Constraints and locked pins are ready on Setup."
                        NewFromRunMode.KnobsOnly ->
                            "Distance and prefs applied; locked pins cleared."
                        NewFromRunMode.EditCheckpoints ->
                            "Open the picker to edit."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OrienteerSpacing.sm)
                ) {
                    // Equal 48dp height — PrimaryGreenButton is 56dp / Ghost is 44dp by default.
                    Button(
                        onClick = onStayInHistory,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = OrienteerCardShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Text(
                            "Stay in History",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Button(
                        onClick = onGoToSetup,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = OrienteerCardShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandForest,
                            contentColor = androidx.compose.ui.graphics.Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Text(
                            "Go to Setup",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
    }
}
