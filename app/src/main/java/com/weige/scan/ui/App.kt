package com.weige.scan.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.weige.scan.AppViewModel
import com.weige.scan.CrashLog
import com.weige.scan.Tab

@Composable
fun WeigeScanApp(vm: AppViewModel) {
    WeigeScanTheme(dark = vm.dark) {
        // 上次启动崩过的话，先把堆栈摊出来（可复制），别让用户只看到"已停止运行"
        val appCtx = LocalContext.current
        var crashText by remember { mutableStateOf(CrashLog.peek(appCtx)) }
        val pending = crashText
        if (pending != null) {
            CrashScreen(pending) {
                CrashLog.clear(appCtx)
                crashText = null
            }
            return@WeigeScanTheme
        }

        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(vm.message) {
            val m = vm.message
            if (m != null) {
                snackbar.showSnackbar(m)
                vm.consumeMessage()
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                vm.booting -> Splash()
                !vm.loggedIn -> LoginScreen(vm)
                else -> MainShell(vm)
            }

            // 预览是全屏浮层：层级压在 tab 之上，返回键优先关它
            vm.preview?.let { task ->
                PreviewScreen(vm, task)
                BackHandler { vm.closePreview() }
            }

            vm.busy?.let { BusyOverlay(it) }

            vm.unlockTarget?.let { TaskItem_unlock ->
                UnlockDialog(
                    task = TaskItem_unlock,
                    priceText = vm.priceText,
                    onDismiss = { vm.dismissUnlock() },
                    onConfirm = { vm.unlock(it) },
                )
                BackHandler { vm.dismissUnlock() }
            }

            SnackbarHost(
                snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(16.dp),
            )
            NotificationPermissionGate()
        }
    }
}

@Composable
private fun Splash() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(72)
            Spacer(Modifier.height(18.dp))
            Text("伟哥扫描王", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(
                Modifier.size(22.dp), strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun MainShell(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Box(Modifier.weight(1f)) {
            when (vm.tab) {
                Tab.SCAN -> ScanScreen(vm)
                Tab.TASKS -> TasksScreen(vm)
                Tab.ME -> ProfileScreen(vm)
            }
        }
        BottomBar(vm.tab) { vm.tab = it }
    }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarItem("扫描", Icons.Rounded.CameraAlt, current == Tab.SCAN) { onSelect(Tab.SCAN) }
                BarItem("任务", Icons.Rounded.Description, current == Tab.TASKS) { onSelect(Tab.TASKS) }
                BarItem("我的", Icons.Rounded.Person, current == Tab.ME) { onSelect(Tab.ME) }
            }
        }
    }
}

@Composable
private fun BarItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, label, Modifier.size(20.dp), tint = color)
            Spacer(Modifier.size(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

/** Android 13+ 通知权限：转换完成通知要靠它，进应用就问一次 */
@Composable
private fun NotificationPermissionGate() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val ctx = LocalContext.current
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (asked) return@LaunchedEffect
        asked = true
        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        // 首次进应用时 Activity 可能还没 RESUMED，个别 ROM 上会拒绝挂载请求；
        // 通知权限丢了只是收不到完成通知，不该连累启动，所以整体兜住。
        if (!granted) runCatching { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
}
