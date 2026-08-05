import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.bundling.Zip

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val channelToolName = "ChannelTool"
val channelToolVersion = "1.0.0"
val isWindowsHost = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

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
            targetFormats(TargetFormat.Dmg)
            packageName = channelToolName
            packageVersion = channelToolVersion

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

val packagePortableWindows by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Builds a portable Windows ZIP containing ChannelTool.exe and its runtime."
    dependsOn("createDistributable")
    onlyIf("Portable Windows distributions can only be built on Windows") { isWindowsHost }

    from(layout.buildDirectory.dir("compose/binaries/main/app/$channelToolName")) {
        into(channelToolName)
    }
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main/portable"))
    archiveFileName.set("$channelToolName-$channelToolVersion-windows.zip")
}

tasks.matching { it.name == "packageDistributionForCurrentOS" }.configureEach {
    if (isWindowsHost) dependsOn(packagePortableWindows)
}
