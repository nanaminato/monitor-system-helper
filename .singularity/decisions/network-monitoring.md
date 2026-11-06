# 应用通信监控的实现选择

## D-MONITOR-001

Status: Superseded by [D-MONITOR-002](#d-monitor-002).

Upstream: [S-MONITOR-001](../singularities/network-monitoring.md#s-monitor-001)

Decision: 由 [MonitorActivity](../../src/MonitorActivity.java) 保存选择，通过
[PCAPdroid 控制接口](https://github.com/emanuele-f/PCAPdroid/blob/master/docs/app_api.md)
启动按应用过滤的本地 VPN，将原始通信包写入手机上的 PCAP 文件。

Reason: 监控需要双向转发。复用现有抓包引擎可以覆盖 IPv4、IPv6 和加密载荷，
代价是需要另装 PCAPdroid，抓包和原有拦截模式需分开运行。

Verification: APK 构建和签名已校验。真机抓包待验证。

## D-MONITOR-002

Upstream: [S-MONITOR-001](../singularities/network-monitoring.md#s-monitor-001)

Decision: [MonitorActivity](../../src/MonitorActivity.java) 保存多应用选择。
[CaptureService](../../src/CaptureService.java) 管理本应用的按应用 VPN 和文件生命周期。
[原生抓包循环](../../src/capture.c) 通过 APK 内的 zdtun 转发，双向记录隧道侧 IP 包。

Reason: 复用双栈转发库，同时满足单 APK 安装。PCAP 由本应用写入 MediaStore 下载目录。

Impact: 替换外部抓包控制接口，更新 VPN 服务声明、原生库构建和使用说明。
原有拦截服务继续承担丢弃目标流量的职责。

Verification: ARM64 与 x86_64 原生库已随 APK 构建并通过签名校验。
本机 ADB 服务无法启动，真机抓包和后台持续运行待验证。
