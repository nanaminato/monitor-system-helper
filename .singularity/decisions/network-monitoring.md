# 应用通信监控的实现选择

## D-MONITOR-001

Status: Superseded by [D-MONITOR-002](#d-monitor-002).

Upstream: [S-MONITOR-001](../singularities/network-monitoring.md#s-monitor-001)

Decision: 由 [MonitorActivity](../../src/MonitorActivity.kt) 保存选择，通过
[PCAPdroid 控制接口](https://github.com/emanuele-f/PCAPdroid/blob/master/docs/app_api.md)
启动按应用过滤的本地 VPN，将原始通信包写入手机上的 PCAP 文件。

Reason: 监控需要双向转发。复用现有抓包引擎可以覆盖 IPv4、IPv6 和加密载荷，
代价是需要另装 PCAPdroid，抓包和原有拦截模式需分开运行。

Verification: APK 构建和签名已校验。真机抓包待验证。

## D-MONITOR-002

Upstream: [S-MONITOR-001](../singularities/network-monitoring.md#s-monitor-001)

Decision: [MonitorActivity](../../src/MonitorActivity.kt) 保存多应用选择。
[CaptureService](../../src/CaptureService.kt) 管理本应用的按应用 VPN 和文件生命周期。
[原生抓包循环](../../src/capture.c) 通过 APK 内的 zdtun 转发，双向记录隧道侧 IP 包。

Reason: 复用双栈转发库，同时满足单 APK 安装。PCAP 由本应用写入 MediaStore 下载目录。

Impact: 替换外部抓包控制接口，更新 VPN 服务声明、原生库构建和使用说明。
原有拦截服务继续承担丢弃目标流量的职责。

Verification: ARM64 与 x86_64 原生库已随 APK 构建并通过签名校验。
本机 ADB 服务无法启动，真机抓包和后台持续运行待验证。

## D-MONITOR-003

Upstream: [S-MONITOR-002](../singularities/network-monitoring.md#s-monitor-002)

Decision: 在出站包转发前解析五元组，通过 Android VPN 的连接归属查询映射到 UID。
管理页保存逐包名规则，抓包会话启动时按 UID 固定规则。共享 UID 使用更严格的规则。
存在拦截规则时，无法确认来源的包也丢弃，避免绕过拦截。此类包单独分组。

Reason: 按五元组短时缓存归属，应用目录在后台按需加载。首页分批刷新有界内存中的
最新包摘要，完整记录仍写入 PCAP。

Revision: 应用选择保存后通过 Android VPN 授权流程启动抓包。会话运行时收到新名单或规则，
先结束原会话并写完 PCAP，再启动最新配置。移除最后一项会结束会话。
