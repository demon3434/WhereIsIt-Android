package com.whereisit.findthings.ui

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whereisit.findthings.data.AppContainer
import com.whereisit.findthings.ui.screen.ItemHomeScreen
import com.whereisit.findthings.ui.screen.LoginScreen
import com.whereisit.findthings.ui.theme.FindThingsTheme

@Composable
fun FindThingsApp(container: AppContainer) {
    val vm: MainViewModel = viewModel(factory = MainViewModel.factory(container))
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeToast()
        }
    }

    FindThingsTheme(appTheme = state.appTheme) {
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { padding ->
            if (!state.isLoggedIn) {
                LoginScreen(
                    modifier = Modifier.fillMaxSize(),
                    paddingValues = padding,
                    state = state,
                    onInternalUrlChange = vm::setInternalUrl,
                    onExternalUrlChange = vm::setExternalUrl,
                    onEndpointChange = vm::setEndpoint,
                    onLogin = vm::login,
                    onSwitchRetry = vm::switchAndRetry,
                    onDiscoverServices = { vm.discoverLocalServices(autoPrompt = true) },
                    onSelectService = vm::applyDiscoveredService,
                    onDismissDiscovery = vm::dismissDiscoveryDialog
                )
            } else {
                ItemHomeScreen(
                    modifier = Modifier.fillMaxSize(),
                    paddingValues = padding,
                    state = state,
                    repository = container.itemRepository,
                    voiceSearchRepository = container.voiceSearchRepository,
                    onLogout = vm::logout,
                    onSaveServerSettings = vm::saveServerSettings,
                    onDiscoverServices = { vm.discoverLocalServices(autoPrompt = false) },
                    onApplyFilter = vm::applyFilter,
                    onApplyVoiceSearchResult = vm::applySmartVoiceSearchResult,
                    onRefresh = vm::pullToRefresh,
                    onCreateItem = vm::createItem,
                    onUpdateItem = vm::updateItem,
                    onDeleteItem = vm::deleteItem,
                    onOpenCreate = vm::openCreate,
                    onOpenEdit = vm::openEdit,
                    onCloseForm = vm::closeForm,
                    onSetDeleteImage = vm::toggleDeleteImage,
                    onPickImages = vm::setNewImages,
                    onChangeTheme = vm::changeTheme,
                    onChangePassword = vm::changePassword,
                    onChangePage = vm::goToPage
                )
            }
        }
    }
}
