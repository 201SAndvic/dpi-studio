import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---------------------------------------------------------------------------
// 签名材料从 local.properties 读取（做法见 RELEASE_SIGNING.md），不进版本控制。
// 没有配置、或者指向的密钥库文件不存在时，release 会自动回退成 debug 签名，
// 这样任何人 clone 下来都能直接编译出可安装的 APK。
// ---------------------------------------------------------------------------
val signingProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val releaseStorePath: String? = signingProps.getProperty("release.storeFile")?.takeIf { it.isNotBlank() }
val hasReleaseSigning: Boolean = releaseStorePath != null && rootProject.file(releaseStorePath).exists()

android {
    namespace = "com.appconfig.injector"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.appconfig.injector"
        minSdk = 26
        targetSdk = 37

        // 版本规则见根目录 README / gradle.properties：
        //   大改 → 0.2、0.3 …（第二位 +1，并开一个新的小改序列）
        //   小改 → 0.11、0.12 …（小改序列里递增）
        //   永不到 1.0
        versionCode = 18
        versionName = "1.01"

        multiDexEnabled = true
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStorePath!!)
                storePassword = signingProps.getProperty("release.storePassword")
                keyAlias = signingProps.getProperty("release.keyAlias")
                keyPassword = signingProps.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            // 没有配置正式签名时用 debug 签名，保证本地与 CI 都能编译出可安装的包。
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        encoding = "UTF-8"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/MANIFEST.MF",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/DEPENDENCIES",
                "META-INF/*.SF",
                "META-INF/*.RSA",
                "META-INF/*.DSA",
                "META-INF/maven/**",
                "META-INF/proguard/**",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            )
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    // LSPatch 引擎（去掉 assets 的纯类 jar）。运行期需要的 keystore / dex / so
    // 已经放在 app/src/main/assets 下。
    implementation(files("libs/lspatch-classes.jar"))

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    // Miuix：小米 HyperOS 风格的 Compose UI 组件库
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.4")
}

// LSPatch 引擎 jar 自带 Guava / errorprone 注解 / jsr305，这里排掉重复类。
configurations.all {
    exclude(group = "com.google.guava", module = "listenablefuture")
    exclude(group = "com.google.errorprone")
    exclude(group = "com.google.code.findbugs", module = "jsr305")
}
