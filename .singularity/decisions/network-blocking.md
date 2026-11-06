# 网络拦截的实现选择

## D-BLOCK-001

Status: Proposed

Upstream: [S-BLOCK-001](../singularities/network-blocking.md#s-block-001)

Decision: 由 `VpnService` 将目标包的 IPv4 和 IPv6 流量导入没有转发路径的 TUN 接口。

Reason: Android VPN 的应用允许列表提供按应用分流，目标流量进入隧道后没有出站转发路径。

Evidence: [BlockService.onStartCommand](../../src/BlockService.kt) 已采用该实现。[项目说明](../../README.md#待验证) 记录了待做的真机验证。
