package com.yuuverne.smssender

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.yuuverne.smssender.ui.theme.SmsSenderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.yuuverne.smssender.database.AppDatabase
import com.yuuverne.smssender.database.ForwardingRule
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List

class MainActivity : ComponentActivity() {
// ... 保持 onCreate 不变
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val config = remember { ConfigManager(context) }
    var selectedTab by remember { mutableIntStateOf(0) }

    // ... 权限请求逻辑保持不变

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(when(selectedTab) {
                        0 -> "SmsSender"
                        1 -> "转发规则"
                        else -> "设置"
                    })
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, "状态") },
                    label = { Text("状态") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.List, "规则") },
                    label = { Text("规则") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, "设置") },
                    label = { Text("设置") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> StatusPanel(config)
                1 -> RulesPanel()
                2 -> SettingsPanel(config)
            }
        }
    }
}

@Composable
fun RulesPanel() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val rules by db.ruleDao().getAllRulesFlow().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var newKeyword by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        if (rules.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无自定义转发规则", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rules) { rule ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("关键字: ${rule.keyword}", fontWeight = FontWeight.Bold)
                                Text("转发至: ${rule.targetEmail}", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = rule.isEnabled,
                                onCheckedChange = { 
                                    scope.launch { db.ruleDao().updateRule(rule.copy(isEnabled = it)) }
                                }
                            )
                            IconButton(onClick = {
                                scope.launch { db.ruleDao().deleteRule(rule) }
                            }) {
                                Icon(Icons.Default.Delete, "删除", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }

        LargeFloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(Icons.Default.Add, "添加规则")
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("新增转发规则") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newKeyword,
                            onValueChange = { newKeyword = it },
                            label = { Text("匹配关键字") },
                            placeholder = { Text("例如: Google") }
                        )
                        OutlinedTextField(
                            value = newEmail,
                            onValueChange = { newEmail = it },
                            label = { Text("目标邮箱") },
                            placeholder = { Text("example@mail.com") }
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (newKeyword.isNotBlank() && newEmail.isNotBlank()) {
                            scope.launch {
                                db.ruleDao().insertRule(ForwardingRule(keyword = newKeyword, targetEmail = newEmail))
                                showAddDialog = false
                                newKeyword = ""
                                newEmail = ""
                            }
                        }
                    }) { Text("添加") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) { Text("取消") }
                }
            )
        }
    }
}

@Composable
fun StatusPanel(config: ConfigManager) {
    val context = LocalContext.current
    val isRunning = config.isServiceEnabled && isNotificationListenerEnabled(context)
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = if (isRunning) Color(0xFFE8F5E9) else Color(0xFFFBE9E7),
            modifier = Modifier.size(200.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    if (isRunning) "运行中" else "未就绪",
                    color = if (isRunning) Color(0xFF2E7D32) else Color(0xFFC62828),
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text("转发至: ${config.receiverEmail.ifEmpty { "未配置" }}", style = MaterialTheme.typography.bodyLarge)
        if (!isNotificationListenerEnabled(context)) {
            Text("请在设置中开启‘通知监听’", color = Color.Red, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun SettingsPanel(config: ConfigManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var smtpHost by remember { mutableStateOf(config.smtpHost) }
    var smtpPort by remember { mutableStateOf(config.smtpPort) }
    var smtpEmail by remember { mutableStateOf(config.smtpEmail) }
    var smtpPassword by remember { mutableStateOf(config.smtpPassword) }
    var receiverEmail by remember { mutableStateOf(config.receiverEmail) }
    var isEnabled by remember { mutableStateOf(config.isServiceEnabled) }
    var useSsl by remember { mutableStateOf(config.useSsl) }
    var useLlm by remember { mutableStateOf(config.useLlm) }
    var llmApiUrl by remember { mutableStateOf(config.llmApiUrl) }
    var llmApiKey by remember { mutableStateOf(config.llmApiKey) }
    var llmModelId by remember { mutableStateOf(config.llmModelId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("SMTP 设置", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = smtpHost, onValueChange = { smtpHost = it; config.smtpHost = it }, label = { Text("SMTP 服务器") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = smtpPort, onValueChange = { smtpPort = it; config.smtpPort = it }, label = { Text("端口") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = smtpEmail, onValueChange = { smtpEmail = it; config.smtpEmail = it }, label = { Text("发件邮箱") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = smtpPassword, onValueChange = { smtpPassword = it; config.smtpPassword = it }, label = { Text("授权码") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = useSsl, onCheckedChange = { useSsl = it; config.useSsl = it })
            Text("使用 SSL")
        }

        HorizontalDivider()
        Text("接收设置", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = receiverEmail, onValueChange = { receiverEmail = it; config.receiverEmail = it }, label = { Text("接收邮箱") }, modifier = Modifier.fillMaxWidth())

        HorizontalDivider()
        Text("AI 智能提取 (验证码识别)", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = useLlm, onCheckedChange = { useLlm = it; config.useLlm = it })
            Spacer(modifier = Modifier.width(8.dp))
            Text("启用 LLM 提取")
        }
        if (useLlm) {
            OutlinedTextField(value = llmApiUrl, onValueChange = { llmApiUrl = it; config.llmApiUrl = it }, label = { Text("API URL") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = llmApiKey, onValueChange = { llmApiKey = it; config.llmApiKey = it }, label = { Text("API Key") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = llmModelId, onValueChange = { llmModelId = it; config.llmModelId = it }, label = { Text("Model ID") }, modifier = Modifier.fillMaxWidth())
        }
        
        HorizontalDivider()
        Text("系统保活", style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("忽略电池优化")
            Switch(checked = isIgnoringBatteryOptimizations(context), onCheckedChange = { requestIgnoreBatteryOptimizations(context) })
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("开启通知监听")
            Switch(checked = isNotificationListenerEnabled(context), onCheckedChange = { 
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            })
        }

        HorizontalDivider()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("开启转发总开关", fontWeight = FontWeight.Bold)
            Switch(checked = isEnabled, onCheckedChange = { 
                isEnabled = it
                config.isServiceEnabled = it
                toggleService(context, it)
            })
        }

        Button(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    MailSender.sendEmail(
                        config = config,
                        subject = "短信转发器 - 测试邮件",
                        content = "配置验证成功！\n\n——来自SmsSender",
                        onSuccess = { scope.launch { Toast.makeText(context, "测试邮件已发送", Toast.LENGTH_SHORT).show() } },
                        onFailure = { e -> scope.launch { Toast.makeText(context, "发送失败: ${e.message}", Toast.LENGTH_LONG).show() } }
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("发送测试邮件")
        }
    }
}

fun toggleService(context: android.content.Context, enable: Boolean) {
    val intent = Intent(context, ForwardingService::class.java)
    if (enable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    } else {
        context.stopService(intent)
    }
}

fun isNotificationListenerEnabled(context: android.content.Context): Boolean {
    val packageNames = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return packageNames != null && packageNames.contains(context.packageName)
}

fun isIgnoringBatteryOptimizations(context: android.content.Context): Boolean {
    val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

fun requestIgnoreBatteryOptimizations(context: android.content.Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = Uri.parse("package:${context.packageName}")
        context.startActivity(intent)
    } catch (e: Exception) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}