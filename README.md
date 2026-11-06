# SystemHelper Blocker

Android 本地 VPN 工具。启用后丢弃当前 Android 用户下
com.mobiletools.systemhelper 的 IPv4 和 IPv6 流量。手机上需要确认 VPN 授权。

项目意图和实现决策从 [Singularity 入口](.singularity/README.md) 查阅。

## 构建

在 Windows PowerShell 7 运行 `./build.ps1`。需要 JDK 21、Python、Android SDK
和 Android NDK（默认 `30.0.16248370`）。SDK 路径可通过 `-Sdk` 指定，NDK 版本
可通过 `-NdkVersion` 指定。生成的 APK 位于 `build/SystemHelperBlocker.apk`，
包含 ARM64 和 x86_64 的原生抓包库。
build/local.p12 是本地测试签名密钥，同一安装的更新需要使用同一密钥。

## 应用通信监控

在首页打开「应用通信监控」，勾选一个或多个应用。没有启动图标的应用可按包名添加。
点击「开始抓包」并完成 Android VPN 授权后，本应用的前台服务会在后台转发并记录通信。
转发库 [zdtun](third_party/zdtun/README.md) 已打包进 APK。选中列表保存在本应用，
下次启动抓包时生效。

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
