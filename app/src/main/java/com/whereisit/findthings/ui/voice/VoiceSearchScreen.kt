package com.whereisit.findthings.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whereisit.findthings.R
import com.whereisit.findthings.data.voice.VoiceSearchRepository
import com.whereisit.findthings.data.voice.model.VoiceFinalizeResponse
import kotlinx.coroutines.delay

private const val MAX_VOICE_RECORD_SECONDS = 6

@Composable
fun VoiceSearchRoute(
    repository: VoiceSearchRepository,
    onDismiss: () -> Unit,
    onCompleted: (VoiceFinalizeResponse) -> Unit
) {
    val vm: VoiceSearchViewModel = viewModel(factory = VoiceSearchViewModel.factory(repository))
    val uiState by vm.uiState.collectAsState()

    LaunchedEffect(vm) {
        vm.completedResult.collect { result ->
            onCompleted(result)
            onDismiss()
        }
    }

    VoiceSearchScreen(
        uiState = uiState,
        onClose = {
            vm.cancelListening()
            onDismiss()
        },
        onStartListening = vm::startListening,
        onStopListening = vm::stopAndFinalize,
        onRetry = vm::reset
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun VoiceSearchScreen(
    uiState: VoiceSearchUiState,
    onClose: () -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isPressingMic by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableStateOf(MAX_VOICE_RECORD_SECONDS) }

    LaunchedEffect(isPressingMic, uiState) {
        if (!isPressingMic || uiState is VoiceSearchUiState.Finalizing) {
            remainingSeconds = MAX_VOICE_RECORD_SECONDS
            return@LaunchedEffect
        }

        remainingSeconds = MAX_VOICE_RECORD_SECONDS
        while (isPressingMic && remainingSeconds > 0) {
            delay(1_000)
            if (!isPressingMic || uiState is VoiceSearchUiState.Finalizing) {
                return@LaunchedEffect
            }
            remainingSeconds -= 1
        }

        if (isPressingMic && remainingSeconds == 0) {
            isPressingMic = false
            onStopListening()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) onRetry()
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text("语音搜索") },
                        navigationIcon = {
                            IconButton(onClick = onClose) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                            }
                        }
                    )
                }
            ) { innerPadding ->
                val displayText = when (uiState) {
                    VoiceSearchUiState.Idle -> "按住并说出物品名称或品牌"
                    is VoiceSearchUiState.Listening -> uiState.partialText.ifBlank { "正在聆听，请继续说话" }
                    is VoiceSearchUiState.Finalizing -> uiState.lastText.ifBlank { "正在识别并搜索..." }
                    is VoiceSearchUiState.Error -> uiState.lastText.orEmpty().ifBlank { "语音识别失败，请重试" }
                }
                val statusText = when (uiState) {
                    VoiceSearchUiState.Idle -> if (hasPermission) "按住下方麦克风开始说话" else "需要麦克风权限才能进行语音搜索"
                    is VoiceSearchUiState.Listening -> "松开后开始搜索"
                    is VoiceSearchUiState.Finalizing -> "正在识别并搜索..."
                    is VoiceSearchUiState.Error -> uiState.message
                }
                val hintText = if (isPressingMic) {
                    "倒计时：$remainingSeconds 秒"
                } else {
                    "您最多可以录制6秒的语音"
                }
                val isBusy = uiState is VoiceSearchUiState.Finalizing
                val micActive = isPressingMic || uiState is VoiceSearchUiState.Listening

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "按住并说出物品名称或品牌",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = displayText,
                                    style = MaterialTheme.typography.headlineSmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = statusText,
                            color = if (uiState is VoiceSearchUiState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (hasPermission) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = hintText,
                                color = if (isPressingMic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (!hasPermission) {
                            Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                                Text("授权麦克风")
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(124.dp)
                                    .background(
                                        color = if (micActive) {
                                            MaterialTheme.colorScheme.errorContainer
                                        } else {
                                            MaterialTheme.colorScheme.primaryContainer
                                        },
                                        shape = CircleShape
                                    )
                                    .pointerInteropFilter { event ->
                                        if (!hasPermission || isBusy) return@pointerInteropFilter false
                                        when (event.actionMasked) {
                                            MotionEvent.ACTION_DOWN -> {
                                                isPressingMic = true
                                                onStartListening()
                                                true
                                            }

                                            MotionEvent.ACTION_UP,
                                            MotionEvent.ACTION_CANCEL -> {
                                                val shouldStop = isPressingMic
                                                isPressingMic = false
                                                if (shouldStop) {
                                                    onStopListening()
                                                }
                                                true
                                            }

                                            else -> true
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_mic_24),
                                    contentDescription = "长按说话",
                                    modifier = Modifier.size(48.dp),
                                    tint = if (micActive) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (hasPermission) "长按说话，松开搜索" else "请先授予麦克风权限",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
