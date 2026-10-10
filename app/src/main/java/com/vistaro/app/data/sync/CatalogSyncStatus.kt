package com.vistaro.app.data.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button as TvButton
import java.text.DateFormat
import java.util.Date

@Composable
fun CatalogSyncStatus(manager: CatalogSyncManager, isTv: Boolean, modifier: Modifier = Modifier) {
    val works by manager.observeWork().collectAsState(initial = emptyList())
    val lastSuccess by manager.observeLastSuccess().collectAsState(initial = null)
    val lastError by manager.observeLastError().collectAsState(initial = null)
    val active = works
        .filter { work ->
            work.state == androidx.work.WorkInfo.State.RUNNING ||
                work.state == androidx.work.WorkInfo.State.BLOCKED ||
                (work.state == androidx.work.WorkInfo.State.ENQUEUED && CatalogSyncManager.PERIODIC_WORK_TAG !in work.tags)
        }
        .firstOrNull { it.state == androidx.work.WorkInfo.State.RUNNING }
        ?: works.firstOrNull { it.state == androidx.work.WorkInfo.State.ENQUEUED && CatalogSyncManager.PERIODIC_WORK_TAG !in it.tags }
    val successParts = lastSuccess?.value?.split('|').orEmpty()
    val lastSuccessAt = successParts.getOrNull(0)?.toLongOrNull()
    val lastErrorText = lastError?.value.orEmpty()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Catalog Sync", fontWeight = FontWeight.SemiBold, fontSize = if (isTv) 20.sp else 17.sp)
        if (active != null) {
            val progress = active.progress
            val message = progress.getString(CatalogSyncWorker.KEY_PROGRESS_MESSAGE)
                ?: if (active.state == androidx.work.WorkInfo.State.BLOCKED) "Waiting for another sync to finish" else "Waiting for a network connection"
            val current = progress.getInt(CatalogSyncWorker.KEY_PROGRESS_CURRENT, 0)
            val total = progress.getInt(CatalogSyncWorker.KEY_PROGRESS_TOTAL, 0)
            Text(
                if (total > 0) "$message · $current of $total" else message,
                color = MaterialTheme.colorScheme.primary,
                fontSize = if (isTv) 16.sp else 14.sp,
            )
        } else if (lastSuccessAt != null) {
            val whenText = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(lastSuccessAt))
            val counts = if (successParts.size >= 3) {
                val pending = successParts.getOrNull(3)?.toIntOrNull() ?: 0
                " · ${successParts[1]} movies · ${successParts[2]} TV shows${if (pending > 0) " · $pending metadata queued" else ""}"
            } else ""
            Text("Last successful sync: $whenText$counts", color = Color(0xFFB8C6D4), fontSize = if (isTv) 15.sp else 13.sp)
        } else {
            Text("Catalog has not been synced yet.", color = Color(0xFFB8C6D4), fontSize = if (isTv) 15.sp else 13.sp)
        }

        if (lastErrorText.isNotBlank()) {
            Text(lastErrorText, color = Color(0xFFFFB4A9), fontSize = if (isTv) 15.sp else 13.sp)
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            if (isTv) {
                TvButton(onClick = { manager.enqueueManualSync() }, enabled = active == null) { Text(if (lastErrorText.isNotBlank()) "Retry" else "Sync Catalog") }
            } else {
                if (lastErrorText.isNotBlank()) {
                    OutlinedButton(onClick = { manager.enqueueManualSync() }, enabled = active == null) { Text("Retry") }
                } else {
                    Button(onClick = { manager.enqueueManualSync() }, enabled = active == null) { Text("Sync Catalog") }
                }
            }
        }
        Text("Automatic sync runs about once a day when internet is available.", color = Color(0xFF9CADBD), fontSize = if (isTv) 13.sp else 12.sp)
    }
}
