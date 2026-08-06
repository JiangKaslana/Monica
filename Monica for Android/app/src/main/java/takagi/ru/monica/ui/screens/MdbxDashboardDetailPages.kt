package takagi.ru.monica.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.LocalMdbxDatabase
import takagi.ru.monica.repository.MdbxVaultDiagnostics

private data class MdbxHealthCheckPresentation(
    val title: String,
    val description: String,
    val value: String,
    val icon: ImageVector,
    val hasIssue: Boolean
)

@Composable
internal fun MdbxHealthDetailPage(
    database: LocalMdbxDatabase,
    diagnostics: MdbxVaultDiagnostics?,
    onRefreshDiagnostics: () -> Unit,
    onOpenMaintenance: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (diagnostics == null) {
            item {
                MdbxDetailHeroCard(
                    icon = Icons.Default.Security,
                    title = "正在检查数据库",
                    subtitle = "${database.name} 的完整性与结构状态正在读取",
                    warning = false
                )
            }
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            item {
                OutlinedButton(
                    onClick = onRefreshDiagnostics,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("重新检查")
                }
            }
        } else {
            val checks = diagnostics.healthCheckPresentations()
            val issueCount = diagnostics.healthIssueCount
            item {
                MdbxDetailHeroCard(
                    icon = if (issueCount > 0) Icons.Default.Warning else Icons.Default.CheckCircle,
                    title = if (issueCount > 0) "$issueCount 个问题需要处理" else "数据库健康正常",
                    subtitle = if (issueCount > 0) {
                        "下方按影响程度列出了 ${database.name} 的异常项目"
                    } else {
                        "${database.name} 的文件、完整性和引用关系均通过检查"
                    },
                    warning = issueCount > 0
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRefreshDiagnostics,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("重新检查")
                    }
                    FilledTonalButton(
                        onClick = onOpenMaintenance,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.ReportProblem, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("诊断维护")
                    }
                }
            }
            item { MdbxDetailSectionLabel("检查结果", "异常项目优先显示，正常项目保留用于核对") }
            checks.forEachIndexed { index, check ->
                item(key = "health-check-$index") {
                    MdbxHealthCheckCard(check)
                }
            }
            item {
                MdbxDetailInformationCard(
                    title = "数据库信息",
                    rows = listOf(
                        MdbxDetailInformationRow("同步状态", diagnostics.lastSyncStatus),
                        MdbxDetailInformationRow("格式版本", diagnostics.formatVersion ?: "未提供"),
                        MdbxDetailInformationRow("文件体积", formatBytes(diagnostics.fileSizeBytes)),
                        MdbxDetailInformationRow("当前客户端", diagnostics.currentDeviceId ?: "未提供"),
                        MdbxDetailInformationRow("文件位置", diagnostics.filePath ?: "未提供")
                    )
                )
            }
        }
    }
}

@Composable
internal fun MdbxAttachmentDetailPage(
    database: LocalMdbxDatabase,
    diagnostics: MdbxVaultDiagnostics?,
    onRefreshDiagnostics: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (diagnostics == null) {
            item {
                MdbxDetailHeroCard(
                    icon = Icons.Default.Storage,
                    title = "正在读取附件状态",
                    subtitle = "${database.name} 的附件索引与存储信息正在统计",
                    warning = false
                )
            }
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        } else {
            val mismatchCount = diagnostics.attachmentChunkMismatchCount
            item {
                MdbxDetailHeroCard(
                    icon = if (mismatchCount > 0) Icons.Default.Warning else Icons.Default.Storage,
                    title = when {
                        mismatchCount > 0 -> "$mismatchCount 个附件分片异常"
                        diagnostics.attachmentCount == 0 -> "当前没有附件"
                        else -> "附件存储正常"
                    },
                    subtitle = when {
                        mismatchCount > 0 -> "附件内容与分片索引存在差异，建议进入诊断维护后重新检查"
                        diagnostics.attachmentCount == 0 -> "${database.name} 尚未保存任何附件内容"
                        else -> "${database.name} 共保存 ${diagnostics.attachmentCount} 个附件"
                    },
                    warning = mismatchCount > 0
                )
            }
            item {
                OutlinedButton(
                    onClick = onRefreshDiagnostics,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("重新检查附件")
                }
            }
            item { MdbxDetailSectionLabel("存储概览", "区分数据库记录、外部引用和实际占用空间") }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MdbxDetailMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Storage,
                        label = "附件文件",
                        value = diagnostics.attachmentCount.toString()
                    )
                    MdbxDetailMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Folder,
                        label = "外部引用",
                        value = diagnostics.externalAttachmentCount.toString()
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MdbxDetailMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Info,
                        label = "原始体积",
                        value = formatBytes(diagnostics.originalAttachmentBytes)
                    )
                    MdbxDetailMetricCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Storage,
                        label = "实际占用",
                        value = formatBytes(diagnostics.storedAttachmentBytes)
                    )
                }
            }
            item {
                MdbxAttachmentIntegrityCard(
                    mismatchCount = mismatchCount,
                    attachmentCount = diagnostics.attachmentCount
                )
            }
            item {
                MdbxDetailInformationCard(
                    title = "存储说明",
                    rows = listOf(
                        MdbxDetailInformationRow(
                            "数据库附件",
                            "附件元数据和受保护内容由当前 MDBX 数据库管理"
                        ),
                        MdbxDetailInformationRow(
                            "外部引用",
                            if (diagnostics.externalAttachmentCount > 0) {
                                "${diagnostics.externalAttachmentCount} 个附件通过外部内容引用保存"
                            } else {
                                "没有使用外部内容引用"
                            }
                        ),
                        MdbxDetailInformationRow(
                            "分片状态",
                            if (mismatchCount > 0) "$mismatchCount 个分片需要检查" else "索引与附件内容一致"
                        )
                    )
                )
            }
        }
    }
}

private fun MdbxVaultDiagnostics.healthCheckPresentations(): List<MdbxHealthCheckPresentation> {
    val checks = listOf(
        MdbxHealthCheckPresentation(
            title = if (isReadable) "数据库文件可读取" else "数据库文件无法读取",
            description = if (isReadable) {
                "Monica 可以打开并读取当前数据库文件"
            } else {
                unavailableReason ?: "当前本地副本不可用，请检查文件位置与访问权限"
            },
            value = if (isReadable) "正常" else "需要处理",
            icon = if (isReadable) Icons.Default.CheckCircle else Icons.Default.CloudOff,
            hasIssue = !isReadable
        ),
        MdbxHealthCheckPresentation(
            title = if (integrityOk) "完整性检查通过" else "完整性检查未通过",
            description = integrityMessage?.takeIf { it.isNotBlank() }
                ?: if (integrityOk) "数据库结构与校验信息一致" else "数据库返回了完整性异常",
            value = if (integrityOk) "正常" else "需要处理",
            icon = Icons.Default.Security,
            hasIssue = !integrityOk
        ),
        MdbxHealthCheckPresentation(
            title = "提交父引用",
            description = if (danglingParentCount > 0) {
                "发现 $danglingParentCount 个提交引用了不存在的父提交，可能影响历史关系"
            } else {
                "所有提交都能找到对应的父提交"
            },
            value = if (danglingParentCount > 0) "$danglingParentCount 个异常" else "正常",
            icon = Icons.Default.History,
            hasIssue = danglingParentCount > 0
        ),
        MdbxHealthCheckPresentation(
            title = "分支头引用",
            description = if (danglingBranchHeadCount > 0) {
                "发现 $danglingBranchHeadCount 个分支指向不存在的提交"
            } else {
                "所有分支都指向有效提交"
            },
            value = if (danglingBranchHeadCount > 0) "$danglingBranchHeadCount 个异常" else "正常",
            icon = Icons.AutoMirrored.Filled.CallMerge,
            hasIssue = danglingBranchHeadCount > 0
        ),
        MdbxHealthCheckPresentation(
            title = "设备头引用",
            description = if (danglingDeviceHeadCount > 0) {
                "发现 $danglingDeviceHeadCount 个设备状态指向不存在的提交"
            } else {
                "所有设备状态都指向有效提交"
            },
            value = if (danglingDeviceHeadCount > 0) "$danglingDeviceHeadCount 个异常" else "正常",
            icon = Icons.Default.Storage,
            hasIssue = danglingDeviceHeadCount > 0
        ),
        MdbxHealthCheckPresentation(
            title = "附件分片",
            description = if (attachmentChunkMismatchCount > 0) {
                "发现 $attachmentChunkMismatchCount 个附件的分片索引与内容不一致"
            } else {
                "附件分片索引与内容一致"
            },
            value = if (attachmentChunkMismatchCount > 0) "$attachmentChunkMismatchCount 个异常" else "正常",
            icon = Icons.Default.Storage,
            hasIssue = attachmentChunkMismatchCount > 0
        )
    )
    return checks.sortedByDescending(MdbxHealthCheckPresentation::hasIssue)
}

@Composable
internal fun MdbxDetailHeroCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    warning: Boolean
) {
    val containerColor = if (warning) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (warning) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.medium,
                color = contentColor.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(26.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.82f)
                )
            }
        }
    }
}

@Composable
private fun MdbxDetailSectionLabel(title: String, subtitle: String) {
    Column(
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MdbxHealthCheckCard(check: MdbxHealthCheckPresentation) {
    val accentColor = if (check.hasIssue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val iconContainer = if (check.hasIssue) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.medium,
                color = iconContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(check.icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(21.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        check.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        check.value,
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    check.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MdbxDetailMetricCard(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    value: String
) {
    Card(
        modifier = modifier.heightIn(min = 92.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MdbxAttachmentIntegrityCard(
    mismatchCount: Int,
    attachmentCount: Int
) {
    val warning = mismatchCount > 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (warning) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (warning) Icons.Default.Warning else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (warning) "附件完整性需要处理" else "附件完整性正常",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (warning) {
                        "$mismatchCount 个分片异常，受影响内容需要通过诊断工具进一步核对"
                    } else if (attachmentCount == 0) {
                        "数据库当前没有附件，无需执行分片检查"
                    } else {
                        "$attachmentCount 个附件的分片索引与内容一致"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class MdbxDetailInformationRow(
    val label: String,
    val value: String
)

@Composable
private fun MdbxDetailInformationCard(
    title: String,
    rows: List<MdbxDetailInformationRow>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        row.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(76.dp)
                    )
                    Text(
                        row.value,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
