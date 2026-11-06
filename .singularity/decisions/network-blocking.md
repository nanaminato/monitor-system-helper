# 网络拦截的实现选择

## D-BLOCK-001

Status: Superseded by [D-MONITOR-003](network-monitoring.md#d-monitor-003).

Upstream: [S-BLOCK-001](../singularities/network-blocking.md#s-block-001)

Decision: 由 `VpnService` 将目标包的 IPv4 和 IPv6 流量导入没有转发路径的 TUN 接口。

Reason: Android VPN 的应用允许列表提供按应用分流，目标流量进入隧道后没有出站转发路径。

Evidence: 提交 `8e4f200` 中的 `BlockService.onStartCommand` 采用了该实现。
