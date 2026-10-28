# ChannelTool

ChannelTool 是一个基于 Compose Multiplatform、[miuix](https://github.com/compose-miuix-ui/miuix) 和 [VasDolly](https://github.com/Tencent/VasDolly) 的桌面渠道包工具。

## 功能

- 从一个已签名 APK 批量生成多个渠道包
- 自动识别 V1、V2 和 V3 签名并选择对应写入方式
- 支持粘贴渠道列表或导入 UTF-8 TXT 文件
- 支持完整校验、快速模式、同名文件保护和任务取消
- 查询单个 APK 的有效渠道、V1/V2 渠道与签名方案
- 支持 macOS DMG 和 Windows 绿色免安装 ZIP 分发

渠道包默认输出到基础 APK 同级的 `channel-output` 目录，命名格式为：

```text
基础包名_渠道名.apk
```

## 开发

项目使用 JDK 21 Gradle Toolchain。

```shell
# 运行桌面应用
./gradlew :desktopApp:run

# 执行单元测试和 VasDolly 集成测试
./gradlew :shared:jvmTest

# 在 macOS 上生成 DMG
./gradlew :desktopApp:packageDmg

# 在 Windows 上生成绿色免安装包
gradlew.bat :desktopApp:packagePortableWindows
```

Windows 产物位于 `desktopApp/build/compose/binaries/main/portable/ChannelTool-版本-windows-架构.zip`，解压后直接运行其中的 `ChannelTool/ChannelTool.exe`。Compose Desktop 的原生运行时只能在对应操作系统上构建，因此绿色版 ZIP 仍需在对应架构的 Windows 主机生成和验证。

## GitHub Actions 分发

`.github/workflows/release.yml` 会执行测试并分别生成：

- `ChannelTool-版本.dmg`
- `ChannelTool-版本-windows-x64.zip`
- `ChannelTool-版本-windows-arm64.zip`

在 GitHub 的 Actions 页面手动运行 `Build and release` 时，填写 `MAJOR.MINOR.PATCH` 格式的版本号，三个产物会作为 Workflow Artifacts 保存 14 天。

推送版本标签会自动创建 GitHub Release：

```shell
git tag v1.0.0
git push origin v1.0.0
```

Windows ARM64 构建使用 GitHub 的标准 `windows-11-arm` Runner，macOS 构建使用 ARM64 `macos-14` Runner。当前 macOS 构建未配置 Apple Developer ID 签名和公证，下载后可能触发 Gatekeeper 提示；正式对外分发时应在仓库 Secrets 中配置 Apple 签名凭据并增加公证步骤。

## 渠道文件

TXT 文件使用 UTF-8 编码，每行一个渠道。空行和完全重复的渠道会被忽略；为保证输出文件能在 macOS 与 Windows 上使用，渠道名不能包含控制字符、路径分隔符、Windows 文件名禁用字符或系统保留名称。

## 测试资产

`shared/src/jvmTest/resources/fixtures` 中的 APK 只包含测试载荷，由一次性测试证书生成，用于验证 VasDolly 的 V1/V2 写入和读取。私钥不包含在项目中。

## 许可证

ChannelTool 自身代码采用 MIT License，详见 [LICENSE](LICENSE)。发行包还包含第三方开源组件；其许可声明见 [THIRD_PARTY_NOTICES.txt](desktopApp/src/main/distribution-resources/THIRD_PARTY_NOTICES.txt)。第三方组件继续适用各自的许可证。
