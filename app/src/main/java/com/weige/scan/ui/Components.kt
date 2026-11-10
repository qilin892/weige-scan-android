package com.weige.scan.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.weige.scan.R
import com.weige.scan.model.TaskItem

/** 品牌图标。
 *  ⚠️ 必须用 drawable 里的普通 PNG：mipmap 的 ic_launcher_round 在 API 26+ 解析到
 *  <adaptive-icon> XML，painterResource 解不了这种 XML，会直接抛异常导致「点开即闪退」。
 */
@Composable
fun BrandMark(size: Int = 56) {
    Image(
        painter = painterResource(R.drawable.brand_mark),
        contentDescription = "伟哥扫描王",
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.28f).dp)),
    )
}

@Composable
fun SectionLabel(text: String, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        trailing?.invoke()
    }
}

/** 主按钮：整块琥珀色，高度给足，手指不容易点空 */
@Composable
fun PrimaryAction(
    text: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (enabled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun SoftAction(
    text: String,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = tint,
        modifier = modifier.height(48.dp),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

/** 状态徽标 */
@Composable
fun StateBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.12f),
        contentColor = color,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

fun stateColor(task: TaskItem): Color = when {
    task.state == "failed" -> Color(0xFFD9363E)
    task.active -> Color(0xFFE07B00)
    task.output != null -> Color(0xFF2E9E5B)
    else -> Color(0xFF8A8F99)
}

@Composable
fun TaskCard(
    task: TaskItem,
    onPreview: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onUnlock: () -> Unit,
) {
    val color = stateColor(task)
    AppCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    task.fileName.ifBlank { "未命名文档" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    listOf(task.targetLabel, task.sizeText, task.createdAt)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (task.output != null && task.canDownload) {
                    Icon(
                        Icons.Rounded.Check, null,
                        Modifier.size(14.dp), tint = color
                    )
                    Spacer(Modifier.width(3.dp))
                }
                if (task.output != null && !task.canDownload) {
                    Icon(Icons.Rounded.Lock, null, Modifier.size(14.dp), tint = color)
                    Spacer(Modifier.width(3.dp))
                }
                StateBadge(task.stateText, color)
            }
        }

        if (task.active) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { (task.progress.coerceIn(0, 100) / 100f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    task.stageLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${task.progress}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (task.progress <= 35) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "云端正在生成文档；图片较多的扫描件通常要 1–5 分钟，进度不动是正常的，切到后台也会继续",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (task.state == "failed") {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.ErrorOutline, null,
                    Modifier.size(15.dp), tint = Color(0xFFD9363E)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    task.err.ifBlank { "转换失败，请重试" },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFD9363E),
                )
            }
        }

        if (task.output != null) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (task.canDownload) {
                    SoftAction("预览", onClick = onPreview, modifier = Modifier.weight(1f))
                    SoftAction("保存", Icons.Rounded.Download, onClick = onSave, modifier = Modifier.weight(1f))
                    SoftAction("分享", Icons.Rounded.IosShare, onClick = onShare, modifier = Modifier.weight(1f))
                } else {
                    SoftAction("预览", onClick = onPreview, modifier = Modifier.weight(1f))
                    Surface(
                        onClick = onUnlock,
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.weight(2f).height(48.dp),
                    ) {
                        Row(
                            Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Lock, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("授权码解锁", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyBlock(title: String, desc: String, action: @Composable (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_notify_scan), null,
                Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            desc,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) {
            Spacer(Modifier.height(8.dp))
            action()
        }
    }
}

@Composable
fun BusyOverlay(text: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(14.dp))
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun TextLink(text: String, color: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}
