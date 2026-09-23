# 保留内置模块与 LSPatch 引擎用到的类
-keep class org.lsposed.patch.** { *; }
-keep class com.appconfig.injector.** { *; }
-dontwarn org.lsposed.**
-dontwarn com.google.**
