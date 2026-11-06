# zdtun

Copyright © 2018–2021 Emanuele Faranda. Licensed under LGPL-3.0-or-later.
See [COPYING](COPYING) and [GPL-3.0.txt](GPL-3.0.txt). The vendored headers retain
their own copyright and license notices.

Source: https://github.com/emanuele-f/zdtun/tree/80b7941d4e685d8354346820878eaec0e35b6b1e

Local change: the socket-open callback returns a status so the library can
close a socket before use if Android cannot exempt it from the VPN.

The complete source used for `libzdtun.so` is in this directory. The project
build script compiles it as a separate shared library. To use a compatible
modified library, rebuild with the edited sources or replace the matching
`lib/<abi>/libzdtun.so` inside the APK and align and re-sign the package.
