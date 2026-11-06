# SystemHelper Blocker

Android 本地 VPN 工具。启用后丢弃当前 Android 用户下
com.mobiletools.systemhelper 的 IPv4 和 IPv6 流量。手机上需要确认 VPN 授权。

项目意图和实现决策从 [Singularity 入口](.singularity/README.md) 查阅。

## 在 Android Studio 运行

1. 用 **File → Open** 打开本项目根目录（包含 `settings.gradle.kts`），等待 Gradle 同步。
   如果此前打开的是旧的 Java 模块，可在 Gradle 工具窗口选择 **Link Gradle Project**
   并指向根目录的 `settings.gradle.kts`。
2. 在 SDK Manager 安装 Android SDK Platform 36 和 NDK `30.0.16248370`。
   Gradle JDK 可选择 JDK 21。项目使用 Gradle Wrapper，首次同步会下载所需依赖。
3. 在工具栏选择 **app** 和已开启 USB 调试的手机，然后点击 **Run**。
   如果缺少运行配置，在 **Run → Edit Configurations** 添加 **Android App**，模块选择 **app**。

也可以运行 `./gradlew.bat :app:assembleDebug`，调试 APK 位于
`app/build/outputs/apk/debug/app-debug.apk`。若已有 `build/local.p12`，Gradle 调试
版本沿用该本地测试签名，可覆盖使用同一密钥安装的旧版本。

## PowerShell 构建

在 Windows PowerShell 7 运行 `./build.ps1`。需要 JDK 21、Python、Android SDK
和 Android NDK（默认 `30.0.16248370`），以及包含 Kotlin 2.4.0 编译器的
Gradle 9.7.1 安装目录（`-GradleHome`）。构建脚本直接调用本地编译器，
并使用 Android SDK Command-line Tools（latest）内的 D8（R8 9.1.29 或更高版本）。
SDK 路径可通过 `-Sdk` 指定，NDK 版本可通过 `-NdkVersion` 指定。
生成的 APK 位于 `build/SystemHelperBlocker.apk`，
包含 ARM64 和 x86_64 的原生抓包库。
build/local.p12 是本地测试签名密钥，同一安装的更新需要使用同一密钥。

## 代码结构

- [MainView.kt](src/MainView.kt) 和 [MonitorView.kt](src/MonitorView.kt) 负责界面和对话框。
  [UiTheme.kt](src/UiTheme.kt) 提供配色、圆角、文字与按钮等界面基础，界面全部由代码构建，
  不使用 XML 资源，并跟随系统深色模式。[RequestListView.kt](src/RequestListView.kt) 与
  [AppPicker.kt](src/AppPicker.kt) 负责请求列表行和应用选择对话框。
- Activity 负责 VPN 授权和服务调用。[MonitorSelection.kt](src/MonitorSelection.kt) 负责应用列表、包名验证和选择保存。
- [CaptureService.kt](src/CaptureService.kt) 和 [BlockService.kt](src/BlockService.kt) 负责 VPN 生命周期。[PcapStore.kt](src/PcapStore.kt) 负责 PCAP 文件的创建与发布。

## 应用通信监控

首页按应用分组显示最近 500 个出站包，可展开查看目标地址、协议、时间和大小。
放行应用的明文 HTTP/1.x 请求和响应会标记为「HTTP」，点按可直接查看首个匹配包中的
明文片段，最多 2 KB。跨 TCP 包的正文、压缩内容和 HTTPS 仍需按各自协议分析。
在首页打开「管理应用与拦截规则」，使用「查找并选择应用」按应用名称或包名搜索。
已选应用会排在列表前面。可以在不同搜索结果中连续勾选，点击「保存并抓包」。
手动添加包名也会开始抓包。首次需要完成 Android VPN 授权。
管理页可移除应用，并可按每个包名打开「丢弃」。关闭丢弃时抓包并尝试转发。
前台服务会在后台转发并记录通信。
转发库 [zdtun](third_party/zdtun/README.md) 已打包进 APK。选中列表保存在本应用，
运行时修改名单或规则会先保存当前 PCAP，再重建 VPN 应用新规则。
切换期间连接可能短暂中断或走系统默认网络。移除最后一个应用会停止抓包。
拦截发生在转发前，出站包仍会写入 PCAP。
若开启任一拦截规则，无法通过 Android 确认归属的包也会被拦截，并单独分组。
共享 UID 的所选应用合用一组，任一应用设置拦截时，该组应用都会被拦截。

可在界面或抓包通知中停止抓包。停止并保存后，在手机文件管理器打开
`Download/SystemHelperBlocker/SystemHelperBlocker-*.pcap` 查看或导出记录。
PCAP 保存 VPN 隧道两端看到的 IPv4/IPv6 包，包含时间戳、协议头和原始载荷。
明文可直接分析，HTTPS 等加密载荷保持密文。接收方向的 IP 包由转发引擎
从返回数据重建，因此包头和分段可能与物理网卡上看到的不同。Android 按 UID
应用 VPN 过滤，共享 UID 的应用可能一起被捕获。监控与原有拦截模式需分开运行。

目前已通过 APK 构建和签名校验。多应用过滤、屏幕关闭后的后台记录及真机联网行为待验证。

## 调查背景

项目用于限制 `com.mobiletools.systemhelper` 自身的网络通信。VPN 只将当前
Android 用户下该包的 IPv4 和 IPv6 流量导入无转发路径的隧道。目标包仍可在
本地运行和访问已有权限允许的数据。启用时可能替换当前 VPN，重启后需手动启用。

### SystemHelper 的线索

- 既有调查记录显示，ADB 曾观察到目标包约每 30 分钟在后台访问定位。原始日志
  和对应设备信息尚未归档到此目录。
- 既有 APK 分析记录的默认上报地址为 `http://dm.wo.com.cn:18080/register`，
  可被配置覆盖。设备实际连接的地址、是否发送定位及具体载荷仍待验证。
- 构建已验证。VPN 启用后的实际拦截效果仍待真机验证。

### 三星 APK 安装器的观察

在[关联对话《解释system helper监控》](chatgpt-conversation://6aed501c-f1d4-83ea-99d8-029d46cf986f)
中，用户报告使用三星原版系统，讨论中记录的型号为 Galaxy S25 Ultra（SM-S9380）。
用户确认，通过系统安装器安装第三方 APK 时，选择「略过」仍能完成安装，
接受协议后则观察到了第三方推广应用推荐。

对话截图显示，「软件包安装程序」提示三星安全下载服务会联网扫描未知来源应用，
并推荐三星应用商店中的同版本或更高版本。页面还列出个人信息的收集和使用，
以及标为选填项的第三方信息共享。截图与用户观察尚无法确定略过后是否仍有
联网扫描、推荐在本地还是服务端生成，或实际传输了哪些字段。目前也没有证据
将该安装器流程与 `com.mobiletools.systemhelper` 关联起来。

### 待验证

1. 在三星设备上核对当前 Android 用户及目标包记录。以下命令包含保留包记录，
   命中后还需确认当前用户的安装状态。空输出仅表示未找到名称匹配的记录。

   ```powershell
   adb shell am get-current-user
   adb shell pm list packages -u | Select-String 'systemhelper|mobiletools'
   ```

2. 在已确认存在目标包的设备上，记录目标包的实际网络目的地址，并比较
   VPN 启用前后的通信行为。按包归因，区分后台定位访问和网络发送。
3. 单独核对三星安装器在「略过」时是否联网，以及推荐和扫描由哪个组件发起。
   将包名、APK 哈希、安装记录、设备标识等可能传输的字段保留为待验证项。
