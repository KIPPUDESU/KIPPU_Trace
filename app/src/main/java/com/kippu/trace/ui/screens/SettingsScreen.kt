package com.kippu.trace.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kippu.trace.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kippu.trace.utils.LanguageMode
import com.kippu.trace.utils.LanguagePreferences
import com.kippu.trace.utils.ThemeMode
import com.kippu.trace.utils.ThemePreferences
import com.kippu.trace.viewmodel.EventViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            "1.0.0"
        }
    }
    val eventViewModel: EventViewModel = viewModel()
    val showThemeDialog = remember { mutableStateOf(false) }
    val showLanguageDialog = remember { mutableStateOf(false) }
    val currentLanguageMode = remember { mutableStateOf(LanguagePreferences.getLanguageMode(context)) }

    // 备份弹窗状态
    val showBackupDialog = remember { mutableStateOf(false) }
    val showImportConfirmDialog = remember { mutableStateOf(false) }
    val backupResultMessage = remember { mutableStateOf<String?>(null) }
    val backupResultIsError = remember { mutableStateOf(false) }
    val isBackupWorking = remember { mutableStateOf(false) }

    // 关于弹窗
    val showAboutDialog = remember { mutableStateOf(false) }

    // 导出启动器
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        uri?.let {
            isBackupWorking.value = true
            eventViewModel.exportBackup(it) { success, message ->
                isBackupWorking.value = false
                backupResultIsError.value = !success
                backupResultMessage.value = message
            }
        }
    }

    // 导入启动器
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            isBackupWorking.value = true
            eventViewModel.importBackup(it) { success, message ->
                isBackupWorking.value = false
                backupResultIsError.value = !success
                backupResultMessage.value = message
            }
        }
    }

    // ========== 主题选择 ==========
    if (showThemeDialog.value) {
        SettingsDialog(onDismiss = { showThemeDialog.value = false }) {
            DialogHeader(
                title = stringResource(R.string.theme_mode),
                subtitle = ThemePreferences.themeModeLabel(themeMode, context),
            )
            Spacer(modifier = Modifier.height(16.dp))

            ThemeOptionCard(
                icon = { MdiIcon(Icons.Default.Settings) },
                label = ThemePreferences.themeModeLabel(ThemeMode.SYSTEM, context),
                description = stringResource(R.string.follow_system),
                isSelected = themeMode == ThemeMode.SYSTEM,
                onClick = {
                    onThemeModeChange(ThemeMode.SYSTEM)
                    showThemeDialog.value = false
                }
            )
            Spacer(modifier = Modifier.height(10.dp))

            ThemeOptionCard(
                icon = { MdiIcon(Icons.Default.LightMode) },
                label = ThemePreferences.themeModeLabel(ThemeMode.LIGHT, context),
                description = stringResource(R.string.light_mode),
                isSelected = themeMode == ThemeMode.LIGHT,
                onClick = {
                    onThemeModeChange(ThemeMode.LIGHT)
                    showThemeDialog.value = false
                }
            )
            Spacer(modifier = Modifier.height(10.dp))

            ThemeOptionCard(
                icon = { MdiIcon(Icons.Default.DarkMode) },
                label = ThemePreferences.themeModeLabel(ThemeMode.DARK, context),
                description = stringResource(R.string.dark_mode),
                isSelected = themeMode == ThemeMode.DARK,
                onClick = {
                    onThemeModeChange(ThemeMode.DARK)
                    showThemeDialog.value = false
                }
            )
        }
    }

    // ========== 语言选择 ==========
    if (showLanguageDialog.value) {
        SettingsDialog(onDismiss = { showLanguageDialog.value = false }) {
            DialogHeader(
                title = stringResource(R.string.language_selection),
                subtitle = LanguagePreferences.languageModeLabel(currentLanguageMode.value, context),
            )
            Spacer(modifier = Modifier.height(16.dp))

            LanguageMode.entries.forEach { mode ->
                val isSelected = mode == currentLanguageMode.value
                ThemeOptionCard(
                    icon = { LanguageCharIcon(mode = mode) },
                    label = LanguagePreferences.languageModeLabel(mode, context),
                    description = null,
                    isSelected = isSelected,
                    onClick = {
                        currentLanguageMode.value = mode
                        LanguagePreferences.setLanguageMode(context, mode)
                        showLanguageDialog.value = false
                        val activity = context as? android.app.Activity
                        activity?.let {
                            it.finishAffinity()
                            it.startActivity(it.intent)
                            @Suppress("DEPRECATION")
                            it.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        }
                    }
                )
                if (mode != LanguageMode.entries.last()) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }

    // ========== 备份恢复 ==========
    if (showBackupDialog.value) {
        SettingsDialog(onDismiss = { showBackupDialog.value = false }) {
            DialogHeader(
                title = stringResource(R.string.backup_restore),
                subtitle = stringResource(R.string.local_backup_subtitle),
            )
            Spacer(modifier = Modifier.height(16.dp))

            BackupActionCard(
                icon = Icons.Default.CloudUpload,
                title = stringResource(R.string.backup_export),
                subtitle = stringResource(R.string.save_as_zip),
                onClick = {
                    showBackupDialog.value = false
                    exportLauncher.launch("backup_${System.currentTimeMillis()}.zip")
                }
            )
            Spacer(modifier = Modifier.height(10.dp))

            BackupActionCard(
                icon = Icons.Default.CloudDownload,
                title = stringResource(R.string.backup_import),
                subtitle = stringResource(R.string.restore_from_zip),
                onClick = {
                    showBackupDialog.value = false
                    showImportConfirmDialog.value = true
                }
            )
        }
    }

    // ========== 关于 ==========
    if (showAboutDialog.value) {
        SettingsDialog(onDismiss = { showAboutDialog.value = false }) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // 软件图标（从 assets 加载）
                val iconBitmap = remember {
                    try {
                        context.assets.open("icon1.png").use { inputStream ->
                            android.graphics.BitmapFactory.decodeStream(inputStream)
                        }?.asImageBitmap()
                    } catch (_: Exception) { null }
                }
                if (iconBitmap != null) {
                    Image(
                        painter = BitmapPainter(iconBitmap),
                        contentDescription = null,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(18.dp)),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "TimeTrace",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "v$versionName",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            AboutInfoRow(
                label = stringResource(R.string.section_about),
                value = "TimeTrace — ${stringResource(R.string.about_maintenance)}"
            )
            Spacer(modifier = Modifier.height(10.dp))
            AboutInfoRow(label = "License", value = "MIT License")
            Spacer(modifier = Modifier.height(10.dp))
            AboutInfoRow(label = "© 2026", value = "KIPPU")
            Spacer(modifier = Modifier.height(20.dp))

            TextButton(
                onClick = { showAboutDialog.value = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.close))
            }
        }
    }

    // ========== 导入确认弹窗 ==========
    if (showImportConfirmDialog.value) {
        SettingsDialog(onDismiss = { showImportConfirmDialog.value = false }) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.confirm_import),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.import_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { showImportConfirmDialog.value = false }) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = {
                    showImportConfirmDialog.value = false
                    importLauncher.launch(arrayOf("application/zip", "*/*"))
                }) {
                    Text(stringResource(R.string.confirm_import_button),
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    // ========== 处理中弹窗 ==========
    if (isBackupWorking.value) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 400.dp)
                    .fillMaxWidth(0.75f)
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.processing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // ========== 结果弹窗 ==========
    if (backupResultMessage.value != null) {
        Dialog(
            onDismissRequest = { backupResultMessage.value = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .widthIn(min = 320.dp, max = 420.dp)
                    .fillMaxWidth(0.85f)
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                ) {
                    val isError = backupResultIsError.value
                    val resultIcon = if (isError) Icons.Default.Info else Icons.Default.Check
                    val resultColor = if (isError)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary

                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = resultColor.copy(alpha = 0.12f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = resultIcon,
                                contentDescription = null,
                                tint = resultColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (isError) stringResource(R.string.error) else stringResource(R.string.complete),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = backupResultMessage.value!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { backupResultMessage.value = null }) {
                            Text(stringResource(R.string.confirm))
                        }
                    }
                }
            }
        }
    }

    // ---------- 主设置页面 ----------
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                SettingsSection(title = stringResource(R.string.section_general)) {
                    SettingsItem(
                        title = stringResource(R.string.theme_mode),
                        icon = Icons.Default.Contrast,
                        subtitle = ThemePreferences.themeModeLabel(themeMode, context)
                    ) { showThemeDialog.value = true }
                    SettingsItem(
                        title = stringResource(R.string.language_selection),
                        icon = Icons.Default.Language,
                        subtitle = LanguagePreferences.languageModeLabel(currentLanguageMode.value, context)
                    ) { showLanguageDialog.value = true }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.section_data)) {
                    SettingsItem(
                        title = stringResource(R.string.backup_restore),
                        icon = Icons.Default.Backup,
                        subtitle = stringResource(R.string.local_backup_subtitle)
                    ) { showBackupDialog.value = true }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.section_about)) {
                    SettingsItem(
                        title = stringResource(R.string.about_app),
                        icon = Icons.Default.Info,
                        subtitle = "v$versionName"
                    ) { showAboutDialog.value = true }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "TimeTrace",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            letterSpacing = 2.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "© 2026 KIPPU. Licensed under MIT.",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}

// ==================== 可复用组件 ====================

@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column { content() }
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ==================== 居中弹窗基底 ====================

@Composable
private fun SettingsDialog(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier
                .widthIn(min = 320.dp, max = 480.dp)
                .fillMaxWidth(0.88f)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun DialogHeader(
    title: String,
    subtitle: String?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

// ==================== 选项卡片 ====================

@Composable
private fun ThemeOptionCard(
    icon: @Composable () -> Unit,
    label: String,
    description: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                icon()
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    )
                    if (description != null) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (isSelected) {
                Surface(
                    modifier = Modifier.size(26.dp),
                    shape = RoundedCornerShape(7.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MdiIcon(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(24.dp),
        shape = RoundedCornerShape(6.dp),
        color = Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * 对应语言的文字字符图标，颜色全部统一
 */
@Composable
private fun LanguageCharIcon(mode: LanguageMode) {
    when (mode) {
        LanguageMode.SYSTEM -> MdiIcon(Icons.Default.Settings)
        LanguageMode.CHINESE -> CharBox("中")
        LanguageMode.ENGLISH -> CharBox("A")
        LanguageMode.JAPANESE -> CharBox("あ")
    }
}

@Composable
private fun CharBox(char: String) {
    Surface(
        modifier = Modifier.size(24.dp),
        shape = RoundedCornerShape(6.dp),
        color = Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = char,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            )
        }
    }
}

@Composable
private fun BackupActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun AboutInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.widthIn(min = 72.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Normal
            )
        )
    }
}
