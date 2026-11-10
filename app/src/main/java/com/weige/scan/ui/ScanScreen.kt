package com.weige.scan.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.weige.scan.AppViewModel
import com.weige.scan.data.Files
import com.weige.scan.model.Target
import com.weige.scan.model.formatSize

private val DOC_MIMES = arrayOf(
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
)

@Composable
fun ScanScreen(vm: AppViewModel) {
    val ctx = LocalContext.current

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok -> vm.finishShot(ok) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris -> vm.addImages(uris) }

    val docLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) vm.pickDocument(uri, Files.displayName(ctx, uri))
    }

    fun shoot() {
        val f = vm.newShotTarget()
        if (f == null) {
            vm.note("无法创建临时文件")
            return
        }
        vm.setPendingShot(f)
        val uri = FileProvider.getUriForFile(ctx, "com.weige.scan.fileprovider", f)
        runCatching { cameraLauncher.launch(uri) }.onFailure {
            vm.finishShot(false)
            vm.note("没有可用的相机应用")
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(42)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("伟哥扫描王", style = MaterialTheme.typography.titleLarge)
                Text(
                    "拍一下，表格和正文都认得出",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // 主入口：拍照
        Surface(
            onClick = { shoot() },
            shape = RoundedCornerShape(22.dp),
            color = Color.Transparent,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(126.dp)
                    .background(Brush.linearGradient(listOf(Amber, AmberDeep)))
            ) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FFFFFF)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.CameraAlt, null,
                            Modifier.size(28.dp), tint = Color.White
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            "拍照扫描",
                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "可连续拍多页，自动合成一份文档",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xE6FFFFFF),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftAction(
                "从相册选",
                Icons.Rounded.PhotoLibrary,
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.weight(1f),
            )
            SoftAction(
                "选 PDF / Word",
                Icons.Rounded.FolderOpen,
                onClick = { docLauncher.launch(DOC_MIMES) },
                modifier = Modifier.weight(1f),
            )
        }

        // 已选内容
        if (vm.docFile != null || vm.images.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            AppCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (vm.docFile != null) "已选文件"
                        else "本次扫描 · ${vm.images.size} 页",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextLink("清空") { vm.clearPicked() }
                }
                Spacer(Modifier.height(10.dp))
                val doc = vm.docFile
                if (doc != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Description, null,
                                Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                doc.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                formatSize(doc.length()),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconBtn(Icons.Rounded.Close) { vm.clearPicked() }
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(vm.images) { index, file ->
                            Box {
                                AsyncImage(
                                    model = file,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(76.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                                // 小叉子单独可点：整张图当删除键太容易误触
                                Surface(
                                    onClick = { vm.removeImage(index) },
                                    shape = CircleShape,
                                    color = Color(0xB3000000),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(5.dp)
                                        .size(22.dp),
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.Close, "删除",
                                            Modifier.size(13.dp), tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            Surface(
                                onClick = { shoot() },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(76.dp),
                            ) {
                                Column(
                                    Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Icon(Icons.Rounded.CameraAlt, null, Modifier.size(20.dp))
                                    Spacer(Modifier.height(4.dp))
                                    Text("加一页", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("输出格式")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Target.entries.forEach { t ->
                TargetChip(t, vm.target == t, Modifier.weight(1f)) { vm.target = t }
            }
        }

        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = vm.pageRanges,
            onValueChange = { vm.pageRanges = it },
            label = { Text("页码范围（可选）") },
            placeholder = { Text("留空=全部，例如 1-5,8") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))
        PrimaryAction(
            text = if (vm.submitting) "正在提交…" else "开始转换",
            icon = Icons.Rounded.ArrowForward,
            enabled = !vm.submitting && (vm.docFile != null || vm.images.isNotEmpty()),
        ) { vm.submit() }

        Spacer(Modifier.height(12.dp))
        Text(
            "支持 JPG/PNG/PDF/Word，单文件 ≤ ${vm.limits.maxFileMb}MB，单次 ≤ ${vm.limits.maxPages} 页。" +
                "照片会自动压到约 200 DPI 再合成（足够 OCR），页面越少转换越快；" +
                "拍摄时四角对齐、光线均匀，识别准确率会明显更好。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TargetChip(
    target: Target,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.height(46.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                target.short,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun IconBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(32.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(16.dp))
        }
    }
}
