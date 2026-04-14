package com.whereisit.findthings.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.whereisit.findthings.R
import com.whereisit.findthings.data.network.DiscoveredService
import com.whereisit.findthings.data.repository.ActiveEndpoint
import com.whereisit.findthings.ui.MainUiState

@Composable
fun LoginScreen(
    modifier: Modifier,
    paddingValues: PaddingValues,
    state: MainUiState,
    onInternalUrlChange: (String) -> Unit,
    onExternalUrlChange: (String) -> Unit,
    onEndpointChange: (ActiveEndpoint) -> Unit,
    onLogin: (String, String) -> Unit,
    onSwitchRetry: () -> Unit,
    onDiscoverServices: () -> Unit,
    onSelectService: (DiscoveredService) -> Unit,
    onDismissDiscovery: () -> Unit
) {
    var username by remember { mutableStateOf(state.lastLoginUsername) }
    var password by remember { mutableStateOf(state.lastLoginPassword) }

    Column(
        modifier = modifier
            .padding(paddingValues)
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.logo_app),
                contentDescription = null,
                modifier = Modifier.size(34.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text("找东西", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = state.internalUrl,
                    onValueChange = onInternalUrlChange,
                    label = { Text("内网 URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.externalUrl,
                    onValueChange = onExternalUrlChange,
                    label = { Text("外网 URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.endpoint == ActiveEndpoint.INTERNAL,
                        onClick = { onEndpointChange(ActiveEndpoint.INTERNAL) },
                        label = { Text("内网生效") }
                    )
                    FilterChip(
                        selected = state.endpoint == ActiveEndpoint.EXTERNAL,
                        onClick = { onEndpointChange(ActiveEndpoint.EXTERNAL) },
                        label = { Text("外网生效") }
                    )
                }

                OutlinedButton(
                    onClick = onDiscoverServices,
                    enabled = !state.isDiscoveringServices && !state.isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isDiscoveringServices) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(if (state.isDiscoveringServices) "正在扫描服务..." else "自动发现内网服务")
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                state.loginError?.let {
                    Text(it, color = MaterialTheme.colorScheme.tertiary)
                }

                Button(
                    onClick = { onLogin(username, password) },
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("登录")
                    }
                }

                if (state.canSwitchRetry) {
                    OutlinedButton(onClick = onSwitchRetry, modifier = Modifier.fillMaxWidth()) {
                        Text("切换地址并重试")
                    }
                }
            }
        }
    }

    if (state.showDiscoveryDialog && state.discoveredServices.isNotEmpty()) {
        DiscoveryDialog(
            services = state.discoveredServices,
            onSelect = onSelectService,
            onDismiss = onDismissDiscovery
        )
    }
}

@Composable
private fun DiscoveryDialog(
    services: List<DiscoveredService>,
    onSelect: (DiscoveredService) -> Unit,
    onDismiss: () -> Unit
) {
    val servicesState = rememberLazyListState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现内网服务") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .height(260.dp)
                    .prettyVerticalScrollbar(servicesState),
                state = servicesState,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(services, key = { it.baseUrl }) { service ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(service) }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(service.name, fontWeight = FontWeight.SemiBold)
                        Text(service.baseUrl, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("稍后") }
        }
    )
}
