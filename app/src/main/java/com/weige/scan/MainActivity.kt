package com.weige.scan

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.weige.scan.ui.WeigeScanApp

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent { WeigeScanApp(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** 从完成通知点进来：直接打开那条产物的预览 */
    private fun handleIntent(i: Intent?) {
        val outputId = i?.getStringExtra(EXTRA_OUTPUT_ID) ?: return
        val name = i.getStringExtra(EXTRA_OUTPUT_NAME).orEmpty()
        vm.openOutput(outputId, name)
        i.removeExtra(EXTRA_OUTPUT_ID)
    }

    override fun onResume() {
        super.onResume()
        vm.onAppForeground()
    }

    companion object {
        const val EXTRA_OUTPUT_ID = "extra_output_id"
        const val EXTRA_OUTPUT_NAME = "extra_output_name"
    }
}
