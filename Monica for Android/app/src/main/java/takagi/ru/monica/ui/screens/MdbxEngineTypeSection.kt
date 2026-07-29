package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.MdbxEngineType

@Composable
fun MdbxEngineTypeSection(
    selectedEngine: MdbxEngineType,
    onEngineChange: (MdbxEngineType) -> Unit,
    remote: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("数据库引擎", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val engines = MdbxEngineType.entries
                engines.forEachIndexed { index, engine ->
                    SegmentedButton(
                        selected = selectedEngine == engine,
                        onClick = { onEngineChange(engine) },
                        shape = SegmentedButtonDefaults.itemShape(index, engines.size)
                    ) {
                        Text(if (engine == MdbxEngineType.KOTLIN_MDBX1) "MDBX 1" else "MDBX 2")
                    }
                }
            }
            Text(
                text = when {
                    selectedEngine == MdbxEngineType.RUST_MDBX2 && remote ->
                        "MDBX 2 使用增量同步；远端 .mdbx 仅作为加密 bootstrap"
                    selectedEngine == MdbxEngineType.RUST_MDBX2 ->
                        "MDBX 2 使用 Rust 引擎与本地加密存储"
                    else -> "MDBX 1 保持现有兼容格式与整文件同步"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
