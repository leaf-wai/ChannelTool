import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.leaf.channeltool.MainKt"
        jvmArgs += listOf("-Duser.language=zh", "-Duser.country=CN")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "ChannelTool"
            packageVersion = "1.0.0"

            macOS {
                iconFile.set(project.file("src/main/resources/channeltool-icon.icns"))
                infoPlist {
                    extraKeysRawXml = """
                        <key>CFBundleLocalizations</key>
                        <array>
                          <string>zh-Hans</string>
                        </array>
                    """.trimIndent()
                }
            }
            windows {
                iconFile.set(project.file("src/main/resources/channeltool-icon.ico"))
            }
        }
    }
}
