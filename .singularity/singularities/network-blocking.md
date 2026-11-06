# 网络拦截

## S-BLOCK-001

Status: Candidate

Statement: 网络拦截只针对当前 Android 用户下的 `com.mobiletools.systemhelper`，覆盖该目标的 IPv4 和 IPv6 流量。

Reason: 拦截对象和流量范围决定工具对设备上其他应用的影响，调整范围需要明确的产品意图。

Evidence: 提交 `8e4f200` 中的 `BlockService.onStartCommand` 曾为独立模式配置单包允许列表及 IPv4、IPv6 默认路由。当前行为见 [S-MONITOR-002](network-monitoring.md#s-monitor-002)。
