package com.leaf.channeltool

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import channeltool.shared.generated.resources.Res
import channeltool.shared.generated.resources.channeltool_icon
import com.leaf.channeltool.model.ApkChannelInfo
import com.leaf.channeltool.model.ApkSignatureScheme
import com.leaf.channeltool.model.ChannelSource
import com.leaf.channeltool.model.PackagingItemResult
import com.leaf.channeltool.model.PackagingItemStatus
import com.leaf.channeltool.platform.createChannelToolService
import com.leaf.channeltool.platform.createDesktopBridge
import com.leaf.channeltool.platform.desktopFileDropTarget
import com.leaf.channeltool.ui.AppPage
import com.leaf.channeltool.ui.ChannelToolUiState
import com.leaf.channeltool.ui.ChannelToolViewModel
import com.leaf.channeltool.ui.InspectionUiState
import com.leaf.channeltool.ui.PackagingUiState
import org.jetbrains.compose.resources.painterResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.ConvertFile
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@Composable
fun App() {
    val viewModel = remember {
        ChannelToolViewModel(
            service = createChannelToolService(),
            desktopBridge = createDesktopBridge(),
        )
    }
    val state by viewModel.state.collectAsState()
    val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()

    MiuixTheme(colors = colors) {
        ChannelToolScreen(state = state, viewModel = viewModel)
    }
}

@Composable
private fun ChannelToolScreen(
    state: ChannelToolUiState,
    viewModel: ChannelToolViewModel,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    state.message?.let { message ->
        LaunchedEffect(message.id) {
            snackbarHostState.showSnackbar(message.text)
            viewModel.consumeMessage(message.id)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                selectedPage = state.page,
                isCurrentPageDefault = state.page == state.defaultPage,
                onSetDefaultPage = viewModel::setCurrentPageAsDefault,
            )
        },
        bottomBar = { BottomNavigation(state.page, viewModel::selectPage) },
        snackbarHost = {
            SnackbarHost(
                state = snackbarHostState,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .consumeWindowInsets(contentPadding),
        ) {
            when (state.page) {
                AppPage.Packaging -> PackagingPage(state.packaging, viewModel)
                AppPage.Inspection -> InspectionPage(state.inspection, viewModel)
            }
        }
    }
}

@Composable
private fun AppTopBar(
    selectedPage: AppPage,
    isCurrentPageDefault: Boolean,
    onSetDefaultPage: () -> Unit,
) {
    var showAppInfo by remember { mutableStateOf(false) }
    var showDefaultPagePrompt by remember { mutableStateOf(false) }

    Column {
        SmallTopAppBar(
            title = "ChannelTool",
            subtitle = "VasDolly Desktop",
            color = MiuixTheme.colorScheme.background,
            titleColor = MiuixTheme.colorScheme.onBackground,
            subtitleColor = MiuixTheme.colorScheme.onBackgroundVariant,
            navigationIcon = {
                IconButton(
                    onClick = { showAppInfo = true },
                    modifier = Modifier.offset(y = 4.dp),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Info,
                        contentDescription = "关于 ChannelTool",
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = { showDefaultPagePrompt = true },
                    modifier = Modifier.offset(y = 4.dp),
                    backgroundColor = if (isCurrentPageDefault) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        Color.Unspecified
                    },
                ) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Pin,
                        contentDescription = if (isCurrentPageDefault) {
                            "当前页已是默认页"
                        } else {
                            "将当前页设为默认页"
                        },
                        tint = if (isCurrentPageDefault) {
                            MiuixTheme.colorScheme.onPrimaryContainer
                        } else {
                            LocalContentColor.current
                        },
                    )
                }
            },
            defaultWindowInsetsPadding = false,
            titlePadding = 32.dp,
        )
        HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
    }

    OverlayDialog(
        show = showAppInfo,
        title = "关于",
        onDismissRequest = { showAppInfo = false },
        defaultWindowInsetsPadding = false,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(Res.drawable.channeltool_icon),
                contentDescription = "ChannelTool 应用图标",
                modifier = Modifier.size(104.dp),
            )
            Text(
                text = "ChannelTool",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = "版本 1.0.0",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = "基于 VasDolly 的桌面端渠道工具，支持批量生成渠道包和查询 APK 渠道信息。",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { showAppInfo = false },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("关闭")
            }
        }
    }

    OverlayDialog(
        show = showDefaultPagePrompt,
        title = if (isCurrentPageDefault) "默认启动页" else "设置默认页",
        summary = if (isCurrentPageDefault) {
            "“${selectedPage.title}”当前已是默认启动页面。"
        } else {
            "将“${selectedPage.title}”设为默认启动页面？"
        },
        onDismissRequest = { showDefaultPagePrompt = false },
        defaultWindowInsetsPadding = false,
    ) {
        if (isCurrentPageDefault) {
            Button(
                onClick = { showDefaultPagePrompt = false },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("知道了")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { showDefaultPagePrompt = false },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        showDefaultPagePrompt = false
                        onSetDefaultPage()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("设为默认页")
                }
            }
        }
    }
}

@Composable
private fun BottomNavigation(
    selectedPage: AppPage,
    onPageSelected: (AppPage) -> Unit,
) {
    NavigationBar(
        color = MiuixTheme.colorScheme.background,
        showDivider = true,
        defaultWindowInsetsPadding = false,
        mode = NavigationBarDisplayMode.IconAndText,
    ) {
        NavigationBarItem(
            selected = selectedPage == AppPage.Packaging,
            onClick = { onPageSelected(AppPage.Packaging) },
            icon = MiuixIcons.Regular.ConvertFile,
            label = AppPage.Packaging.title,
        )
        NavigationBarItem(
            selected = selectedPage == AppPage.Inspection,
            onClick = { onPageSelected(AppPage.Inspection) },
            icon = MiuixIcons.Regular.Search,
            label = AppPage.Inspection.title,
        )
    }
}

@Composable
private fun PackagingPage(
    state: PackagingUiState,
    viewModel: ChannelToolViewModel,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 32.dp,
            top = 24.dp,
            end = 32.dp,
            bottom = 48.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            PageTitle("批量打包", "${state.channels.size} 个有效渠道")
        }
        item {
            SectionTitle("基础 APK")
            Spacer(Modifier.height(8.dp))
            PathSelector(
                value = state.baseApkPath,
                placeholder = "尚未选择 APK",
                buttonLabel = "选择 APK",
                icon = { Icon(MiuixIcons.Regular.File, contentDescription = null) },
                enabled = !state.isRunning,
                onClick = viewModel::chooseBaseApk,
                onFilesDropped = viewModel::useDroppedBaseApk,
            )
        }
        item {
            SectionTitle("输出目录")
            Spacer(Modifier.height(8.dp))
            PathSelector(
                value = state.outputDirectory,
                placeholder = "选择基础 APK 后自动设置",
                buttonLabel = "选择目录",
                icon = { Icon(MiuixIcons.Regular.Folder, contentDescription = null) },
                enabled = !state.isRunning,
                onClick = viewModel::chooseOutputDirectory,
                onFilesDropped = viewModel::useDroppedOutputDirectory,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SectionTitle("渠道列表")
                    Text(
                        text = "每行一个渠道",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
                Button(
                    onClick = viewModel::importChannelFile,
                    enabled = !state.isRunning,
                ) {
                    Icon(MiuixIcons.Regular.Import, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("导入 TXT")
                }
            }
            Spacer(Modifier.height(10.dp))
            TextField(
                value = state.channelText,
                onValueChange = viewModel::updateChannelText,
                modifier = Modifier.fillMaxWidth().height(156.dp),
                label = "xiaomi\nhuawei\noppo\nvivo",
                useLabelAsPlaceholder = true,
                enabled = !state.isRunning,
                singleLine = false,
                minLines = 5,
                maxLines = 8,
            )
            if (state.channelIssues.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                state.channelIssues.take(3).forEach { issue ->
                    Text(
                        text = "第 ${issue.line} 行：${issue.message}",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
                if (state.channelIssues.size > 3) {
                    Text(
                        text = "另有 ${state.channelIssues.size - 3} 项错误",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.error,
                    )
                }
            }
        }
        item {
            OptionsBand(state, viewModel)
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    space = 12.dp,
                    alignment = Alignment.CenterHorizontally,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.isRunning) {
                    Button(
                        onClick = viewModel::cancelPackaging,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Icon(MiuixIcons.Basic.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("取消")
                    }
                } else {
                    Button(
                        onClick = viewModel::startPackaging,
                        modifier = Modifier.weight(1f),
                        enabled = state.canStart,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Icon(MiuixIcons.Regular.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("开始打包")
                    }
                }
                Button(
                    onClick = viewModel::openOutputDirectory,
                    modifier = Modifier.weight(1f),
                    enabled = state.outputDirectory.isNotBlank() && !state.isRunning,
                ) {
                    Icon(MiuixIcons.Regular.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("打开输出目录")
                }
            }
        }
        if (state.results.isNotEmpty()) {
            item {
                val progress = state.completedCount.toFloat() / state.results.size.toFloat()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle("处理结果")
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${state.completedCount} / ${state.results.size}",
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(state.results, key = { it.channel }) { result ->
                PackagingResultRow(result)
            }
        }
    }
}

@Composable
private fun OptionsBand(
    state: PackagingUiState,
    viewModel: ChannelToolViewModel,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        OptionSwitch(
            title = "快速模式",
            summary = "跳过生成后的强校验",
            checked = state.fastMode,
            enabled = !state.isRunning,
            onCheckedChange = viewModel::updateFastMode,
            modifier = Modifier.weight(1f),
        )
        OptionSwitch(
            title = "覆盖同名文件",
            summary = "成功生成后替换旧渠道包",
            checked = state.overwriteExisting,
            enabled = !state.isRunning,
            onCheckedChange = viewModel::updateOverwrite,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OptionSwitch(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.Medium)
            Text(
                text = summary,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun PackagingResultRow(result: PackagingItemResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusIndicator(result.status)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = result.channel, fontWeight = FontWeight.Medium)
            Text(
                text = result.message ?: result.targetPath,
                fontSize = 12.sp,
                color = if (result.status == PackagingItemStatus.Failed) {
                    MiuixTheme.colorScheme.error
                } else {
                    MiuixTheme.colorScheme.onSurfaceSecondary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = result.status.label,
            fontSize = 12.sp,
            color = result.status.color,
        )
    }
}

@Composable
private fun StatusIndicator(status: PackagingItemStatus) {
    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        when (status) {
            PackagingItemStatus.Processing -> CircularProgressIndicator(modifier = Modifier.size(18.dp))
            PackagingItemStatus.Generated -> Icon(
                MiuixIcons.Basic.Check,
                contentDescription = null,
                tint = status.color,
                modifier = Modifier.size(18.dp),
            )
            PackagingItemStatus.Failed -> Icon(
                MiuixIcons.Basic.Close,
                contentDescription = null,
                tint = status.color,
                modifier = Modifier.size(18.dp),
            )
            PackagingItemStatus.Pending,
            PackagingItemStatus.Skipped,
            -> Box(
                Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(status.color),
            )
        }
    }
}

@Composable
private fun InspectionPage(
    state: InspectionUiState,
    viewModel: ChannelToolViewModel,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 32.dp,
            top = 24.dp,
            end = 32.dp,
            bottom = 48.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { PageTitle("渠道查询", "单个 APK") }
        item {
            SectionTitle("APK 文件")
            Spacer(Modifier.height(8.dp))
            PathSelector(
                value = state.apkPath,
                placeholder = "尚未选择 APK",
                buttonLabel = "选择 APK",
                icon = { Icon(MiuixIcons.Regular.File, contentDescription = null) },
                enabled = !state.isLoading,
                onClick = viewModel::chooseInspectionApk,
                onFilesDropped = viewModel::useDroppedInspectionApk,
            )
        }
        if (state.isLoading) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("正在读取 APK")
                }
            }
        }
        state.error?.let { error ->
            item {
                ResultBand(containerColor = MiuixTheme.colorScheme.errorContainer) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("读取失败", fontWeight = FontWeight.SemiBold)
                        Text(error, color = MiuixTheme.colorScheme.onErrorContainer)
                    }
                    IconButton(onClick = viewModel::inspectAgain) {
                        Icon(MiuixIcons.Regular.Refresh, contentDescription = "重新读取")
                    }
                }
            }
        }
        state.info?.let { info ->
            item { ChannelResult(info, viewModel::copyEffectiveChannel) }
            item { ApkDetails(info) }
        }
    }
}

@Composable
private fun ChannelResult(
    info: ApkChannelInfo,
    onCopy: () -> Unit,
) {
    val hasChannel = !info.effectiveChannel.isNullOrEmpty()
    ResultBand(
        containerColor = if (hasChannel) {
            MiuixTheme.colorScheme.primary
        } else {
            MiuixTheme.colorScheme.error
        },
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (hasChannel) "有效渠道" else "查询结果",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = info.effectiveChannel?.ifEmpty { "未写入渠道信息" } ?: "未写入渠道信息",
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (info.effectiveSource != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (info.effectiveSource == ChannelSource.SigningBlock) {
                        "来源：APK Signing Block"
                    } else {
                        "来源：V1 META-INF"
                    },
                    fontSize = 12.sp,
                    color = if (hasChannel) MiuixTheme.colorScheme.onPrimaryVariant else MiuixTheme.colorScheme.onPrimary,
                )
            }
        }
        if (hasChannel) {
            IconButton(onClick = onCopy) {
                Icon(MiuixIcons.Regular.Copy, contentDescription = "复制渠道名", tint = MiuixTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun ApkDetails(info: ApkChannelInfo) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle("APK 信息")
        Spacer(Modifier.height(12.dp))
        DetailRow(
            label = "签名方案",
            value = info.signatures
                .sortedBy(ApkSignatureScheme::ordinal)
                .joinToString(" + ") { it.name }
                .ifEmpty { "未检测到" },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
        DetailRow("Signing Block 渠道", info.signingBlockChannel ?: "无")
        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
        DetailRow("V1 渠道", info.v1Channel ?: "无")
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MiuixTheme.colorScheme.onBackgroundVariant, modifier = Modifier.width(180.dp))
        Text(value, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ResultBand(
    containerColor: Color,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun PathSelector(
    value: String,
    placeholder: String,
    buttonLabel: String,
    icon: @Composable () -> Unit,
    enabled: Boolean,
    onClick: () -> Unit,
    onFilesDropped: (List<String>) -> Unit,
) {
    var isDragActive by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .desktopFileDropTarget(
                enabled = enabled,
                onFilesDropped = onFilesDropped,
                onDragActiveChange = { isDragActive = it },
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = value,
            onValueChange = {},
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .then(
                    if (isDragActive) {
                        Modifier.border(
                            width = 2.dp,
                            color = MiuixTheme.colorScheme.primary,
                            shape = RoundedCornerShape(TextFieldDefaults.CornerRadius),
                        )
                    } else {
                        Modifier
                    },
                ),
            label = placeholder,
            useLabelAsPlaceholder = true,
            readOnly = true,
            singleLine = true,
        )
        Button(
            onClick = onClick,
            modifier = Modifier.height(56.dp),
            enabled = enabled,
        ) {
            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) { icon() }
            Spacer(Modifier.width(8.dp))
            Text(buttonLabel)
        }
    }
}

@Composable
private fun PageTitle(title: String, meta: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
            text = title,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = meta,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            modifier = Modifier.padding(bottom = 3.dp),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = MiuixTheme.colorScheme.onBackground,
    )
}

private val PackagingItemStatus.label: String
    get() = when (this) {
        PackagingItemStatus.Pending -> "等待"
        PackagingItemStatus.Processing -> "处理中"
        PackagingItemStatus.Generated -> "成功"
        PackagingItemStatus.Skipped -> "已跳过"
        PackagingItemStatus.Failed -> "失败"
    }

private val PackagingItemStatus.color: Color
    @Composable get() = when (this) {
        PackagingItemStatus.Pending -> MiuixTheme.colorScheme.onSurfaceSecondary
        PackagingItemStatus.Processing -> MiuixTheme.colorScheme.primary
        PackagingItemStatus.Generated -> Color(0xFF2E8B57)
        PackagingItemStatus.Skipped -> Color(0xFFB06A00)
        PackagingItemStatus.Failed -> MiuixTheme.colorScheme.error
    }
