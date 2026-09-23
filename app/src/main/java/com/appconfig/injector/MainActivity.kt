package com.appconfig.injector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.appconfig.injector.ui.DpiStudioApp
import com.appconfig.injector.ui.theme.AppTheme

/**
 * 唯一入口。界面由 Compose + Miuix 绘制，业务逻辑仍是原来的 Java 实现。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logs.init(this)

        // Android 上 /tmp 不可写；LSPatch 内部用 KeyStore.getDefaultType()，内置的是 BKS 密钥库
        System.setProperty("java.io.tmpdir", cacheDir.absolutePath)
        java.security.Security.setProperty("keystore.type", "BKS")

        enableEdgeToEdge()

        setContent {
            AppTheme {
                DpiStudioApp(this)
            }
        }
    }

    override fun onPause() {
        super.onPause()
    }
}
