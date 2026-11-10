package com.weige.scan.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.weige.scan.AppViewModel
import com.weige.scan.data.Api
import com.weige.scan.data.UnauthorizedException
import com.weige.scan.model.TaskItem

/**
 * 带水印预览：服务端只回前 3 页的低清 PNG（/api/preview + /api/preview/img），
 * 完整文件要保存/分享才拿得到。这里故意用深色底，像在看纸质文档。
 */
@Composable
fun PreviewScreen(vm: AppViewModel, task: TaskItem) {
    val out = task.output
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(out?.outputId) {
        if (out == null) {
            error = "该任务还没有产物"
            loading = false
            return@LaunchedEffect
        }
        try {
            val info = Api.preview(out.outputId)
            val count = info.pages.coerceIn(0, 20)
            val list = ArrayList<Bitmap>()
            for (i in 1..count) {
                val bytes = Api.previewImage(info.ptoken, i)
                decodeCapped(bytes, 1700)?.let { list.add(it) }
            }
            if (list.isEmpty()) error = "预览图没生成出来，可以直接保存后查看"
            pages = list
        } catch (e: UnauthorizedException) {
            error = "登录已过期，请重新登录"
        } catch (e: Exception) {
            error = e.message ?: "预览加载失败"
        } finally {
            loading = false
        }
    }

    Surface(color = Color(0xFF0A0908), modifier = Modifier.fillMaxSize()) {
        // ⚠️ 预览是全屏浮层，盖在 MainShell 之上，拿不到它的 safeDrawingPadding，
        // 必须自己吃系统栏内边距，否则标题会被状态栏压住（真机截图暴露的问题）。
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(top = 4.dp)
        ) {
            // 顶栏
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = { vm.closePreview() },
                    shape = CircleShape,
                    color = Color(0x1FFFFFFF),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.ArrowBack, "返回", Modifier.size(18.dp), tint = Color.White)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        task.output?.name?.ifBlank { task.fileName } ?: task.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "带水印预览 · 前 ${pages.size} 页",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xB3FFFFFF),
                    )
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            Modifier.size(26.dp), strokeWidth = 2.dp, color = Amber
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "正在生成预览…",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xCCFFFFFF),
                        )
                    }

                    error != null || pages.isEmpty() -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp),
                    ) {
                        Text(
                            error ?: "暂无预览",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xCCFFFFFF),
                        )
                    }

                    else -> {
                        val pager = rememberPagerState(pageCount = { pages.size })
                        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                            Image(
                                bitmap = pages[page].asImageBitmap(),
                                contentDescription = "第 ${page + 1} 页",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0x99000000),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp),
                        ) {
                            Text(
                                "${pager.currentPage + 1} / ${pages.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }

            // 底部操作
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0x14FFFFFF))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (task.canDownload) {
                    ActionPill(
                        "保存到手机", Icons.Rounded.Download,
                        Modifier.weight(1f)
                    ) { vm.save(task) }
                    ActionPill(
                        "分享", Icons.Rounded.IosShare,
                        Modifier.weight(1f)
                    ) { vm.share(task) }
                } else {
                    ActionPill(
                        "授权码解锁后保存", Icons.Rounded.Lock,
                        Modifier.weight(1f)
                    ) { vm.askUnlock(task) }
                }
            }
        }
    }
}

@Composable
private fun ActionPill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Amber,
        contentColor = Color(0xFF2A1200),
        modifier = modifier.height(50.dp),
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun decodeCapped(bytes: ByteArray, maxEdge: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}
