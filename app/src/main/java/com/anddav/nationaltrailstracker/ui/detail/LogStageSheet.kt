package com.anddav.nationaltrailstracker.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.anddav.nationaltrailstracker.data.local.StageLogEntity
import com.anddav.nationaltrailstracker.domain.Stage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHEET_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogStageSheet(
    stage: Stage,
    existingLog: StageLogEntity?,
    onDismiss: () -> Unit,
    onSave: (dateWalked: LocalDate, steps: Int?, notes: String?, actualMiles: Double?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var dateWalked by remember { mutableStateOf(existingLog?.dateWalked ?: LocalDate.now()) }
    var stepsText by remember { mutableStateOf(existingLog?.steps?.toString().orEmpty()) }
    var actualMilesText by remember { mutableStateOf(existingLog?.actualMiles?.toString().orEmpty()) }
    var notesText by remember { mutableStateOf(existingLog?.notes.orEmpty()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
        ) {
            Text(
                text = "Stage ${stage.index + 1}: ${stage.fromLandmark.name} → ${stage.toLandmark.name}",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.padding(top = 16.dp))

            OutlinedTextField(
                value = SHEET_DATE_FORMAT.format(dateWalked),
                onValueChange = {},
                readOnly = true,
                label = { Text("Date walked") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                trailingIcon = { TextButton(onClick = { showDatePicker = true }) { Text("Change") } },
            )

            OutlinedTextField(
                value = stepsText,
                onValueChange = { input -> if (input.all(Char::isDigit)) stepsText = input },
                label = { Text("Steps (optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )

            OutlinedTextField(
                value = actualMilesText,
                onValueChange = { input -> if (input.matches(Regex("^\\d*\\.?\\d*$"))) actualMilesText = input },
                label = { Text("Actual miles walked (optional)") },
                supportingText = { Text("Set this if you went further than planned - ${"%.1f".format(stage.miles)} mi was the plan for this stage") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )

            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                minLines = 2,
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                if (onDelete != null) {
                    OutlinedButton(onClick = { showDeleteConfirm = true }) { Text("Delete") }
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        onSave(
                            dateWalked,
                            stepsText.toIntOrNull(),
                            notesText.ifBlank { null },
                            actualMilesText.toDoubleOrNull(),
                        )
                    },
                ) { Text("Save") }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateWalked.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        dateWalked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = datePickerState) }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this log?") },
            text = { Text("This removes the date, steps and notes you logged for this stage.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete?.invoke()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }
}
