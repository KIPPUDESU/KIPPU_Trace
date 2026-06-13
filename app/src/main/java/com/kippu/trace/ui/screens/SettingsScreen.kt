package com.kippu.trace.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.kippu.trace.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kippu.trace.utils.LanguageMode
import com.kippu.trace.utils.LanguagePreferences
import com.kippu.trace.utils.ThemeMode
import com.kippu.trace.utils.ThemePreferences
import com.kippu.trace.utils.NowNodeStyle
import com.kippu.trace.utils.TimelineScaleMode
import com.kippu.trace.utils.TimelinePreferences
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
    val showDevelopingDialog = remember { mutableStateOf(false) }
    val developingFeatureName = remember { mutableStateOf("") }
    val showThemeDialog = remember { mutableStateOf(false) }
    val showLanguageDialog = remember { mutableStateOf(false) }
    val showTimelineDialog = remember { mutableStateOf(false) }
    val currentLanguageMode = remember { mutableStateOf(LanguagePreferences.getLanguageMode(context)) }
    val currentTimelineScaleMode = remember { mutableStateOf(TimelinePreferences.getScaleMode(context)) }
    val currentNowNodeStyle = remember { mutableStateOf(TimelinePreferences.getNowStyle(context)) }

    // 备份弹窗状态
    val showBackupDialog = remember { mutableStateOf(false) }
    val showImportConfirmDialog = remember { mutableStateOf(false) }
    val backupResultMessage = remember { mutableStateOf<String?>(null) }
    val backupResultIsError = remember { mutableStateOf(false) }
    val isBackupWorking = remember { mutableStateOf(false) }

    // 导出启动器：选择名称和位置
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

    // 导入启动器：选择 .zip 文件
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

    if (showDevelopingDialog.value) {
        AlertDialog(
            onDismissRequest = { showDevelopingDialog.value = false },
            title = { Text(stringResource(R.string.confirm)) },
            text = {
                val text = if (developingFeatureName.value == context.getString(R.string.about_app))
                    stringResource(R.string.about_app) + "\n" + stringResource(R.string.about_maintenance)
                else
                    stringResource(R.string.feature_developing, developingFeatureName.value)
                Text(text)
            },
            confirmButton = {
                TextButton(onClick = { showDevelopingDialog.value = false }) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }

    if (showThemeDialog.value) {
        AlertDialog(
            onDismissRequest = { showThemeDialog.value = false },
            title = { Text(stringResource(R.string.theme_mode)) },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        val isSelected = mode == themeMode
                        Surface(
                            onClick = {
                                onThemeModeChange(mode)
                                showThemeDialog.value = false
                            },
                            color = if (isSelected)
                                MaterialTheme.colorScheme.surfaceVariant
                            else
                                Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = ThemePreferences.themeModeLabel(mode, context),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        if (mode != ThemeMode.entries.last()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog.value = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    // 备份操作弹窗
    if (showBackupDialog.value) {
        AlertDialog(
            onDismissRequest = { showBackupDialog.value = false },
            title = { Text(stringResource(R.string.backup_restore)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        onClick = {
                            showBackupDialog.value = false
                            exportLauncher.launch("backup_${System.currentTimeMillis()}.zip")
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.backup_export),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = stringResource(R.string.save_as_zip),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Surface(
                        onClick = {
                            showBackupDialog.value = false
                            showImportConfirmDialog.value = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.backup_import),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = stringResource(R.string.restore_from_zip),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackupDialog.value = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 导入确认弹窗
    if (showImportConfirmDialog.value) {
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog.value = false },
            title = { Text(stringResource(R.string.confirm_import)) },
            text = {
                Text(stringResource(R.string.import_warning))
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirmDialog.value = false
                    importLauncher.launch(arrayOf("application/zip", "*/*"))
                }) {
                    Text(stringResource(R.string.confirm_import_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirmDialog.value = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 处理中弹窗
    if (isBackupWorking.value) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.please_wait)) },
            text = { Text(stringResource(R.string.processing)) },
            confirmButton = {}
        )
    }

    // 结果弹窗
    if (backupResultMessage.value != null) {
        AlertDialog(
            onDismissRequest = { backupResultMessage.value = null },
            title = {
                Text(if (backupResultIsError.value) stringResource(R.string.error) else stringResource(R.string.complete))
            },
            text = { Text(backupResultMessage.value!!) },
            confirmButton = {
                TextButton(onClick = { backupResultMessage.value = null }) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }

    if (showLanguageDialog.value) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog.value = false },
            title = { Text(stringResource(R.string.language_selection)) },
            text = {
                Column {
                    LanguageMode.entries.forEach { mode ->
                        val isSelected = mode == currentLanguageMode.value
                        Surface(
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
                            },
                            color = if (isSelected)
                                MaterialTheme.colorScheme.surfaceVariant
                            else
                                Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = LanguagePreferences.languageModeLabel(mode, context),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        if (mode != LanguageMode.entries.last()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog.value = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    // 时间线设置弹窗（合并刻度样式 + 节点样式）
    if (showTimelineDialog.value) {
        AlertDialog(
            onDismissRequest = { showTimelineDialog.value = false },
            title = { Text(stringResource(R.string.timeline_settings)) },
            text = {
                Column {
                    // 刻度样式
                    Text(
                        stringResource(R.string.timeline_scale_mode),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                    TimelineScaleMode.entries.forEach { mode ->
                        val isSelected = mode == currentTimelineScaleMode.value
                        Surface(
                            onClick = {
                                currentTimelineScaleMode.value = mode
                                TimelinePreferences.setScaleMode(context, mode)
                            },
                            color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    TimelinePreferences.scaleModeLabel(mode, context),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                if (isSelected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                            }
                        }
                        if (mode != TimelineScaleMode.entries.last()) {
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // 节点样式
                    Text(
                        stringResource(R.string.now_node_style),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                    NowNodeStyle.entries.forEach { style ->
                        val isSelected = style == currentNowNodeStyle.value
                        Surface(
                            onClick = {
                                currentNowNodeStyle.value = style
                                TimelinePreferences.setNowStyle(context, style)
                            },
                            color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    TimelinePreferences.nowStyleLabel(style, context),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                if (isSelected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                            }
                        }
                        if (style != NowNodeStyle.entries.last()) {
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimelineDialog.value = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

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
                        icon = Icons.Default.DarkMode,
                        subtitle = ThemePreferences.themeModeLabel(themeMode, context)
                    ) {
                        showThemeDialog.value = true
                    }
                    SettingsItem(
                        title = stringResource(R.string.language_selection),
                        icon = Icons.Default.Language,
                        subtitle = LanguagePreferences.languageModeLabel(currentLanguageMode.value, context)
                    ) {
                        showLanguageDialog.value = true
                    }
                    SettingsItem(
                        title = stringResource(R.string.timeline_settings),
                        icon = Icons.Default.Timeline,
                        subtitle = context.getString(R.string.timeline_settings_subtitle,
                            TimelinePreferences.scaleModeLabel(currentTimelineScaleMode.value, context),
                            TimelinePreferences.nowStyleLabel(currentNowNodeStyle.value, context))
                    ) {
                        showTimelineDialog.value = true
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.section_data)) {
                    SettingsItem(
                        title = stringResource(R.string.backup_restore),
                        icon = Icons.Default.Backup,
                        subtitle = stringResource(R.string.local_backup_subtitle)
                    ) {
                        showBackupDialog.value = true
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.section_about)) {
                    SettingsItem(
                        title = stringResource(R.string.about_app),
                        icon = Icons.Default.ChevronRight,
                        subtitle = "v$versionName Stable"
                    ) {
                        developingFeatureName.value = context.getString(R.string.about_app)
                        showDevelopingDialog.value = true
                    }
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
            Column {
                content()
            }
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
