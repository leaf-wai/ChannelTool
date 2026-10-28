import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.bundling.Zip

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val channelToolName = "ChannelTool"
val channelToolVersion = providers.gradleProperty("channelToolVersion")
    .orElse(providers.environmentVariable("CHANNELTOOL_VERSION"))
    .orElse("1.0.0")
    .get()
val isWindowsHost = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
val windowsArchitecture = when (System.getProperty("os.arch").lowercase()) {
    "aarch64", "arm64" -> "arm64"
    "amd64", "x86_64", "x64" -> "x64"
    else -> System.getProperty("os.arch").lowercase()
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
            targetFormats(TargetFormat.Dmg)
            packageName = channelToolName
            packageVersion = channelToolVersion
            licenseFile.set(rootProject.file("LICENSE"))
            appResourcesRootDir.set(project.layout.projectDirectory.dir("src/main/distribution-resources"))

            macOS {
                iconFile.set(project.file("src/main/resources/channeltool-icon.icns"))
                minimumSystemVersion = "13.0"
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
    enabled = isWindowsHost

    from(layout.buildDirectory.dir("compose/binaries/main/app/$channelToolName")) {
        into("ChannelTool")
    }
    from(rootProject.file("LICENSE"))
    from(project.file("src/main/distribution-resources/common"))
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main/portable"))
    archiveFileName.set("$channelToolName-$channelToolVersion-windows-$windowsArchitecture.zip")
}

tasks.matching { it.name == "packageDistributionForCurrentOS" }.configureEach {
    if (isWindowsHost) dependsOn(packagePortableWindows)
}
