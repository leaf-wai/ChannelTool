# ChannelTool

ChannelTool 是一个基于 Compose Multiplatform、[miuix](https://github.com/compose-miuix-ui/miuix) 和 [VasDolly](https://github.com/Tencent/VasDolly) 的桌面渠道包工具。

## 功能

- 从一个已签名 APK 批量生成多个渠道包
- 自动识别 V1、V2 和 V3 签名并选择对应写入方式
- 支持粘贴渠道列表或导入 UTF-8 TXT 文件
- 支持完整校验、快速模式、同名文件保护和任务取消
- 查询单个 APK 的有效渠道、V1/V2 渠道与签名方案
- 支持 macOS DMG 和 Windows MSI 分发

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

# 在 Windows 上生成 MSI
gradlew.bat :desktopApp:packageMsi
```

Compose Desktop 的原生安装包只能在对应操作系统上构建，因此 MSI 需要在 Windows 主机验证。

## 渠道文件

TXT 文件使用 UTF-8 编码，每行一个渠道。空行和完全重复的渠道会被忽略；为保证输出文件能在 macOS 与 Windows 上使用，渠道名不能包含控制字符、路径分隔符、Windows 文件名禁用字符或系统保留名称。

## 测试资产

`shared/src/jvmTest/resources/fixtures` 中的 APK 只包含测试载荷，由一次性测试证书生成，用于验证 VasDolly 的 V1/V2 写入和读取。私钥不包含在项目中。
