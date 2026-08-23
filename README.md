# FC/NES 模拟器

NTSC / PAL / Dendy 一台机器。Windows 与 Android 两个 Host，核是同一份 Java 17。

- 手册：[`AGENTS.md`](AGENTS.md)
- 模块与不变量：[`docs/project-map.md`](docs/project-map.md)
- 下一会话从哪接：[`docs/handoff.md`](docs/handoff.md)
- 发布：https://github.com/LaVendergong/nes-emulator/releases/tag/v0.4.0

```
mvn -q compile
java -cp target/classes nes.selfcheck.SelfCheck
java -cp target/classes nes.host.Main roms\nova.nes
```

Host 只调用 `nes.console.Console`。仓库不收商业 ROM。
