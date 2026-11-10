package com.weige.scan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.weige.scan.AppViewModel
import com.weige.scan.Tab

@Composable
fun TasksScreen(vm: AppViewModel) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val finished = tasks.count { it.finished }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("任务", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    "${tasks.size}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (finished > 0) {
                TextLink("清除已完成", color = MaterialTheme.colorScheme.onSurfaceVariant) {
                    vm.clearFinished()
                }
            }
            Surface(
                onClick = { vm.refresh() },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(34.dp),
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Rounded.Refresh, "刷新", Modifier.size(17.dp))
                }
            }
        }

        if (tasks.isEmpty()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyBlock(
                    "还没有任务",
                    "回到「扫描」拍一张，几十秒就能拿到 Word",
                ) {
                    TextLink("去扫描") { vm.tab = Tab.SCAN }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(tasks, key = { it.id.ifBlank { it.hashCode().toString() } }) { task ->
                    TaskCard(
                        task = task,
                        onPreview = { vm.openPreview(task) },
                        onSave = { vm.save(task) },
                        onShare = { vm.share(task) },
                        onUnlock = { vm.askUnlock(task) },
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "转换依赖云端解析，排队高峰会慢一些；产物在服务端保留 24 小时，请及时保存。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
        }
    }
}
