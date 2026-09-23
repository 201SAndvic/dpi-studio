# 签名与发版说明

本项目的签名材料**不进入版本控制**，通过 `local.properties` 注入。
没有配置签名时，`assembleRelease` 会自动回退成 Debug 签名，所以任何人 clone 下来
都能直接编译出可安装的 APK。

---

## 一、生成本地签名密钥

需要 JDK 的 `keytool`（JDK 安装目录下的 `bin/keytool.exe`）。

```powershell
keytool -genkeypair -v `
  -keystore app\keystore\dpi-studio.jks `
  -alias dpistudio `
  -keyalg RSA -keysize 2048 -validity 36500 `
  -storetype JKS
```

执行过程中会要求输入两次密码，以及姓名、组织等信息，随便填即可
（这些信息只影响证书外观，不影响功能）。

> `dpi-studio.jks` 已经被 `.gitignore` 排除，**不要**把它提交到仓库。
> **一旦丢失，你将无法再给自己的应用签名升级**，请务必备份。

---

## 二、配置 `local.properties`

在项目根目录的 `local.properties` 中追加四行（可参考 `local.properties.example`）：

```properties
sdk.dir=C:/Users/你的用户名/AppData/Local/Android/Sdk

release.storeFile=app/keystore/dpi-studio.jks
release.storePassword=你的密钥库密码
release.keyAlias=dpistudio
release.keyPassword=你的密钥密码
```

- `release.storeFile` 是相对项目根目录的路径。
- 这四行不写全，或者文件不存在时，Release 构建会静默回退到 Debug 签名。

---

## 三、打包

```bash
./gradlew assembleRelease
```

产物位于 `app/build/outputs/apk/release/`。

可以用下面的命令确认签名信息：

```bash
"$ANDROID_HOME/build-tools/<版本>/apksigner.bat" verify --print-certs app/build/outputs/apk/release/*.apk
```

---

## 四、发布到 GitHub Releases

1. 在 GitHub 仓库页面点右侧的 **Releases → Draft a new release**。
2. **Choose a tag** 填 `v1.0`（与 `versionName` 保持一致，前缀 `v`）。
3. 标题填 `DPI 工坊 1.0`，说明写这次改了什么（可以直接抄 [CHANGELOG.md](CHANGELOG.md)）。
4. 把 `app/build/outputs/apk/release/` 里的 APK 拖进附件区。
5. 点 **Publish release**。

> 建议**每个版本都保留旧 APK**。用户的设备上可能装着旧版，需要能随时回滚。

---

## 五、关于覆盖安装

安卓只允许「同一个包名 + 同一个签名」的应用互相覆盖升级。

- 本项目的 `applicationId` 是 `com.appconfig.injector`，从 0.1 到现在一直没变，
  因此旧版 DPI 工坊 / 应用配置注入器可以直接覆盖安装并保留数据。
- **如果换了签名密钥**，已安装的旧版必须先卸载才能装新版，且数据会丢失。
  所以请一直使用同一个 keystore。
