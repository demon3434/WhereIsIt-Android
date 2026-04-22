package com.whereisit.findthings.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whereisit.findthings.data.AppContainer
import com.whereisit.findthings.data.model.CategoryDto
import com.whereisit.findthings.data.model.HouseDto
import com.whereisit.findthings.data.model.ItemCreatePayload
import com.whereisit.findthings.data.model.ItemDto
import com.whereisit.findthings.data.model.RoomDto
import com.whereisit.findthings.data.model.TagDto
import com.whereisit.findthings.data.network.DiscoveredService
import com.whereisit.findthings.data.network.ServiceAutoDiscovery
import com.whereisit.findthings.data.repository.ActiveEndpoint
import com.whereisit.findthings.data.repository.AppError
import com.whereisit.findthings.data.repository.AppTheme
import com.whereisit.findthings.data.repository.ItemFilter
import com.whereisit.findthings.data.repository.ItemRepository
import com.whereisit.findthings.data.repository.PagedItems
import com.whereisit.findthings.data.repository.SessionRepository
import com.whereisit.findthings.data.voice.model.VoiceFinalizeResponse
import com.whereisit.findthings.ui.voice.buildVoiceSearchCandidates
import java.net.URI
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    private val sessionRepository: SessionRepository,
    private val itemRepository: ItemRepository,
    private val serviceAutoDiscovery: ServiceAutoDiscovery
) : ViewModel() {
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val session = sessionRepository.current()
            _state.update {
                it.copy(
                    internalUrl = session.internalUrl,
                    externalUrl = session.externalUrl,
                    endpoint = session.activeEndpoint,
                    appTheme = session.appTheme,
                    isLoggedIn = session.token.isNotBlank()
                )
            }
            if (session.token.isNotBlank()) {
                val base = session.activeBaseUrl()
                if (base.isNotBlank()) itemRepository.setRuntimeAuth(base, session.token)
                loadHome()
            } else {
                discoverLocalServices(autoPrompt = true)
            }
        }
    }

    fun setInternalUrl(value: String) = _state.update { it.copy(internalUrl = value) }
    fun setExternalUrl(value: String) = _state.update { it.copy(externalUrl = value) }
    fun setEndpoint(value: ActiveEndpoint) = _state.update { it.copy(endpoint = value) }

    fun saveServerSettings(internal: String, external: String, endpoint: ActiveEndpoint) {
        viewModelScope.launch {
            val currentSession = sessionRepository.current()
            val activeUrlChanged = when (currentSession.activeEndpoint) {
                ActiveEndpoint.INTERNAL -> hasMeaningfulUrlChange(currentSession.internalUrl, internal)
                ActiveEndpoint.EXTERNAL -> hasMeaningfulUrlChange(currentSession.externalUrl, external)
            }

            sessionRepository.saveServerSettings(internal, external, endpoint)

            if (currentSession.token.isNotBlank() && activeUrlChanged) {
                itemRepository.clearRuntimeAuth()
                sessionRepository.clearToken()
                _state.value = MainUiState(
                    internalUrl = internal,
                    externalUrl = external,
                    endpoint = endpoint,
                    appTheme = state.value.appTheme,
                    toastMessage = "当前生效服务器网址已修改，已自动退出登录，请重新登录"
                )
                return@launch
            }

            if (currentSession.token.isNotBlank()) {
                val session = sessionRepository.current()
                val base = session.activeBaseUrl()
                if (base.isNotBlank()) itemRepository.setRuntimeAuth(base, session.token)
            }

            _state.update {
                it.copy(
                    internalUrl = internal,
                    externalUrl = external,
                    endpoint = endpoint,
                    toastMessage = "服务器地址已保存"
                )
            }
        }
    }

    fun changeTheme(theme: AppTheme) {
        viewModelScope.launch {
            sessionRepository.saveTheme(theme)
            _state.update { it.copy(appTheme = theme, toastMessage = "主题已切换") }
        }
    }

    fun changePassword(newPassword: String) {
        viewModelScope.launch {
            if (newPassword.trim().length < 6) {
                _state.update { it.copy(toastMessage = "密码至少 6 位") }
                return@launch
            }
            runSafely {
                _state.update { it.copy(isBusy = true) }
                val resp = itemRepository.changePassword(newPassword.trim())
                _state.update { it.copy(isBusy = false, toastMessage = resp.message.ifBlank { "密码已修改" }) }
            }
        }
    }

    fun discoverLocalServices(autoPrompt: Boolean = false) {
        if (state.value.isDiscoveringServices) return
        viewModelScope.launch {
            _state.update { it.copy(isDiscoveringServices = true) }
            val discovered = serviceAutoDiscovery.discover()
            _state.update {
                it.copy(
                    isDiscoveringServices = false,
                    discoveredServices = discovered,
                    showDiscoveryDialog = autoPrompt && discovered.isNotEmpty() || it.showDiscoveryDialog
                )
            }
            if (discovered.isEmpty()) {
                _state.update { it.copy(toastMessage = "未发现可用服务") }
            }
        }
    }

    fun dismissDiscoveryDialog() {
        _state.update { it.copy(showDiscoveryDialog = false) }
    }

    fun applyDiscoveredService(service: DiscoveredService) {
        viewModelScope.launch {
            val endpoint = ActiveEndpoint.INTERNAL
            sessionRepository.saveServerSettings(service.baseUrl, state.value.externalUrl, endpoint)
            sessionRepository.saveLastSuccessBaseUrl(service.baseUrl)
            _state.update {
                it.copy(
                    internalUrl = service.baseUrl,
                    endpoint = endpoint,
                    showDiscoveryDialog = false,
                    toastMessage = "已选择：${service.baseUrl}"
                )
            }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isBusy = true,
                    loginError = null,
                    canSwitchRetry = false,
                    lastLoginUsername = username,
                    lastLoginPassword = password
                )
            }
            val active = normalizedActiveUrl() ?: run {
                _state.update { it.copy(isBusy = false, loginError = "请先填写生效服务器地址") }
                return@launch
            }
            try {
                sessionRepository.saveServerSettings(state.value.internalUrl, state.value.externalUrl, state.value.endpoint)
                val token = itemRepository.login(username.trim(), password, active).accessToken
                itemRepository.setRuntimeAuth(active, token)
                sessionRepository.setToken(token)
                sessionRepository.saveLastSuccessBaseUrl(active)
                _state.update { it.copy(isLoggedIn = true, isBusy = false, showDiscoveryDialog = false) }
                loadHome()
            } catch (e: AppError.Network) {
                _state.update {
                    it.copy(
                        isBusy = false,
                        loginError = e.message,
                        canSwitchRetry = fallbackUrl().isNotBlank()
                    )
                }
                discoverLocalServices(autoPrompt = true)
            } catch (e: AppError) {
                _state.update { it.copy(isBusy = false, loginError = e.message) }
            }
        }
    }

    fun switchAndRetry() {
        val username = state.value.lastLoginUsername
        val password = state.value.lastLoginPassword
        if (username.isBlank() || password.isBlank()) {
            _state.update { it.copy(loginError = "请重新输入用户名和密码") }
            return
        }
        viewModelScope.launch {
            sessionRepository.switchEndpoint()
            val session = sessionRepository.current()
            _state.update { it.copy(endpoint = session.activeEndpoint, canSwitchRetry = false) }
            login(username, password)
        }
    }

    fun logout() {
        viewModelScope.launch {
            itemRepository.clearRuntimeAuth()
            sessionRepository.clearToken()
            _state.value = MainUiState(
                internalUrl = state.value.internalUrl,
                externalUrl = state.value.externalUrl,
                endpoint = state.value.endpoint,
                appTheme = state.value.appTheme
            )
            discoverLocalServices(autoPrompt = true)
        }
    }

    fun consumeToast() = _state.update { it.copy(toastMessage = null) }

    fun applyFilter(keyword: String, houseId: Int?, roomId: Int?, categoryId: Int?, tagIds: Set<Int>) {
        _state.update {
            it.copy(
                filter = ItemFilter(keyword, houseId, roomId, categoryId, tagIds),
                currentPage = 1
            )
        }
        refreshItems(1)
    }

    fun goToPage(page: Int) {
        val safePage = page.coerceAtLeast(1)
        if (safePage == state.value.currentPage) return
        refreshItems(safePage)
    }

    fun applyVoiceSearchResult(result: VoiceFinalizeResponse) {
        val keyword = result.normalizedQuery?.takeIf { it.isNotBlank() } ?: result.finalText
        val items = result.items
        _state.update {
            it.copy(
                items = items,
                totalItems = items.size,
                currentPage = 1,
                pageSize = max(1, items.size),
                totalPages = 1,
                listScrollToTopSignal = it.listScrollToTopSignal + 1,
                toastMessage = if (keyword.isNotBlank()) "语音搜索：$keyword" else null
            )
        }
    }

    fun pullToRefresh() {
        refreshItems(page = state.value.currentPage, showRefreshIndicator = true)
    }

    fun applySmartVoiceSearchResult(result: VoiceFinalizeResponse) {
        val candidates = buildVoiceSearchCandidates(result)
        val preferredKeyword = candidates.firstOrNull().orEmpty()
        val directItems = result.items
        if (directItems.isNotEmpty()) {
            _state.update {
                it.copy(
                    items = directItems,
                    totalItems = directItems.size,
                    currentPage = 1,
                    pageSize = max(1, directItems.size),
                    totalPages = 1,
                    listScrollToTopSignal = it.listScrollToTopSignal + 1,
                    toastMessage = if (preferredKeyword.isNotBlank()) "语音搜索：$preferredKeyword" else null
                )
            }
            return
        }

        if (preferredKeyword.isBlank()) {
            _state.update { it.copy(toastMessage = "语音识别结果为空，请重试") }
            return
        }

        viewModelScope.launch {
            runSafely {
                _state.update {
                    it.copy(
                        isBusy = true,
                        currentPage = 1,
                        listScrollToTopSignal = it.listScrollToTopSignal + 1
                    )
                }

                val (matchedKeyword, matchedPage) = searchItemsByVoiceCandidates(candidates)
                val totalPages = max(1, (matchedPage.total + FIXED_PAGE_SIZE - 1) / FIXED_PAGE_SIZE)

                _state.update {
                    it.copy(
                        items = matchedPage.items,
                        totalItems = matchedPage.total,
                        currentPage = matchedPage.page.coerceAtLeast(1),
                        pageSize = FIXED_PAGE_SIZE,
                        totalPages = totalPages,
                        isBusy = false,
                        isRefreshingItems = false,
                        toastMessage = "语音搜索：$matchedKeyword"
                    )
                }
            }
        }
    }

    fun refreshItems(page: Int = state.value.currentPage, showRefreshIndicator: Boolean = false) {
        if (state.value.isBusy && !showRefreshIndicator) return
        viewModelScope.launch {
            runSafely {
                _state.update { it.copy(isBusy = true, isRefreshingItems = showRefreshIndicator) }
                loadItemsPage(page)
            }
        }
    }

    fun openCreate() {
        viewModelScope.launch {
            runSafely { loadMetaOnly() }
            _state.update {
                it.copy(
                    showForm = true,
                    editingItem = null,
                    form = ItemFormState(quantity = "1"),
                    newImageUris = emptyList(),
                    removeImageIds = emptySet()
                )
            }
        }
    }

    fun openEdit(item: ItemDto) {
        viewModelScope.launch {
            runSafely { loadMetaOnly() }
            _state.update {
                it.copy(
                    showForm = true,
                    editingItem = item,
                    form = ItemFormState(
                        name = item.name,
                        brand = item.brand,
                        quantity = item.quantity.toString(),
                        locationDetail = item.locationDetail,
                        houseId = item.houseId,
                        roomId = item.roomId,
                        categoryId = item.categoryId,
                        selectedTagIds = item.tags.map { tag -> tag.id }.toSet()
                    ),
                    removeImageIds = emptySet(),
                    newImageUris = emptyList()
                )
            }
        }
    }

    fun closeForm() = _state.update {
        it.copy(showForm = false, removeImageIds = emptySet(), newImageUris = emptyList())
    }

    fun setNewImages(uris: List<Uri>) = _state.update { it.copy(newImageUris = uris.take(9)) }

    fun toggleDeleteImage(imageId: Int, checked: Boolean) = _state.update {
        it.copy(removeImageIds = if (checked) it.removeImageIds + imageId else it.removeImageIds - imageId)
    }

    fun createItem(form: ItemFormState) {
        viewModelScope.launch {
            runSafely {
                _state.update { it.copy(isBusy = true) }
                itemRepository.createItem(buildPayload(form), state.value.newImageUris)
                _state.update {
                    it.copy(
                        showForm = false,
                        isBusy = false,
                        toastMessage = "物品已创建",
                        newImageUris = emptyList(),
                        removeImageIds = emptySet(),
                        currentPage = 1
                    )
                }
                _state.update { it.copy(isBusy = true) }
                loadItemsPage(1)
                _state.update { it.copy(listScrollToTopSignal = it.listScrollToTopSignal + 1) }
                loadMetaOnly()
            }
        }
    }

    fun updateItem(form: ItemFormState) {
        val itemId = state.value.editingItem?.id ?: return
        viewModelScope.launch {
            runSafely {
                _state.update { it.copy(isBusy = true) }
                itemRepository.updateItem(itemId, buildPayload(form), state.value.newImageUris, state.value.removeImageIds.toList())
                _state.update {
                    it.copy(
                        showForm = false,
                        isBusy = false,
                        toastMessage = "物品已更新",
                        newImageUris = emptyList(),
                        removeImageIds = emptySet()
                    )
                }
                refreshItems()
                loadMetaOnly()
            }
        }
    }

    fun deleteItem(item: ItemDto) {
        viewModelScope.launch {
            runSafely {
                _state.update { it.copy(isBusy = true) }
                itemRepository.deleteItem(item.id)
                _state.update { it.copy(isBusy = false, toastMessage = "已删除：${item.name}") }
                refreshItems()
            }
        }
    }

    private suspend fun loadHome() {
        runSafely {
            _state.update { it.copy(isBusy = true) }
            itemRepository.me()
            loadMetaOnly()
            loadItemsPage(1)
        }
    }

    private suspend fun loadItemsPage(page: Int) {
        val safePage = page.coerceAtLeast(1)
        val pageSize = FIXED_PAGE_SIZE
        val result = itemRepository.listItems(
            filter = state.value.filter,
            page = safePage,
            pageSize = pageSize
        )

        val totalPages = max(1, (result.total + pageSize - 1) / pageSize)

        if (safePage > 1 && result.items.isEmpty() && result.total > 0) {
            loadItemsPage(safePage - 1)
            return
        }

        _state.update {
            it.copy(
                items = result.items,
                totalItems = result.total,
                currentPage = result.page.coerceAtLeast(1),
                pageSize = pageSize,
                totalPages = totalPages,
                isBusy = false,
                isRefreshingItems = false
            )
        }
    }

    private suspend fun loadMetaOnly() {
        val pack = itemRepository.meta()
        _state.update {
            it.copy(
                houses = pack.houses.filter { house -> house.isActive },
                rooms = pack.rooms.filter { room -> room.isActive },
                categories = pack.categories.filter { category -> category.isActive },
                tags = pack.tags.filter { tag -> tag.isActive }
            )
        }
    }

    private suspend fun searchItemsByVoiceCandidates(candidates: List<String>): Pair<String, PagedItems> {
        var lastKeyword = candidates.first()
        var lastPage = itemRepository.listItems(
            filter = ItemFilter(keyword = lastKeyword),
            page = 1,
            pageSize = FIXED_PAGE_SIZE
        )
        if (lastPage.items.isNotEmpty()) {
            return lastKeyword to lastPage
        }

        for (candidate in candidates.drop(1)) {
            val page = itemRepository.listItems(
                filter = ItemFilter(keyword = candidate),
                page = 1,
                pageSize = FIXED_PAGE_SIZE
            )
            lastKeyword = candidate
            lastPage = page
            if (page.items.isNotEmpty()) {
                return candidate to page
            }
        }

        return lastKeyword to lastPage
    }

    private suspend fun runSafely(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: AppError.Unauthorized) {
            val current = state.value
            val isLoggedInNow = current.isLoggedIn
            val shouldScheduleLogout = isLoggedInNow && !current.pendingSessionExpireRedirect
            _state.update {
                it.copy(
                    isBusy = false,
                    isRefreshingItems = false,
                    loginError = e.message,
                    toastMessage = if (isLoggedInNow) "登录已过期，3秒后跳转到登录页" else e.message,
                    pendingSessionExpireRedirect = if (isLoggedInNow) true else it.pendingSessionExpireRedirect
                )
            }
            if (shouldScheduleLogout) {
                viewModelScope.launch {
                    delay(3000)
                    itemRepository.clearRuntimeAuth()
                    sessionRepository.clearToken()
                    val latest = state.value
                    _state.value = MainUiState(
                        internalUrl = latest.internalUrl,
                        externalUrl = latest.externalUrl,
                        endpoint = latest.endpoint,
                        appTheme = latest.appTheme
                    )
                    discoverLocalServices(autoPrompt = true)
                }
            }
        } catch (e: AppError) {
            _state.update { it.copy(isBusy = false, isRefreshingItems = false, toastMessage = e.message) }
        }
    }

    private fun buildPayload(form: ItemFormState): ItemCreatePayload {
        val name = form.name.trim()
        val location = form.locationDetail.trim()
        if (name.isBlank()) throw AppError.Validation("请填写物品名称")
        if (location.isBlank()) throw AppError.Validation("请填写具体位置")
        val quantity = form.quantity.toIntOrNull() ?: 0
        if (quantity < 1) throw AppError.Validation("数量必须大于等于 1")
        val houseId = form.houseId ?: throw AppError.Validation("请选择房屋")
        val roomId = form.roomId ?: throw AppError.Validation("请选择房间")
        val categoryId = form.categoryId ?: throw AppError.Validation("请选择分类")
        val tagIds = form.selectedTagIds.toList().sorted()
        val tagNames = form.customTagNames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sorted()
        return ItemCreatePayload(name, form.brand.trim(), quantity, categoryId, houseId, roomId, location, tagIds, tagNames)
    }

    private fun normalizedActiveUrl(): String? {
        val raw = when (state.value.endpoint) {
            ActiveEndpoint.INTERNAL -> state.value.internalUrl
            ActiveEndpoint.EXTERNAL -> state.value.externalUrl
        }.trim()
        return normalizeBaseUrl(raw)
    }

    private fun fallbackUrl(): String {
        val raw = when (state.value.endpoint) {
            ActiveEndpoint.INTERNAL -> state.value.externalUrl
            ActiveEndpoint.EXTERNAL -> state.value.internalUrl
        }.trim()
        return normalizeBaseUrl(raw) ?: ""
    }

    private fun hasMeaningfulUrlChange(oldValue: String, newValue: String): Boolean {
        val oldNormalized = normalizeBaseUrl(oldValue)
        val newNormalized = normalizeBaseUrl(newValue)
        return if (oldNormalized != null || newNormalized != null) {
            oldNormalized != newNormalized
        } else {
            oldValue.trim() != newValue.trim()
        }
    }

    private fun normalizeBaseUrl(raw: String): String? {
        if (raw.isBlank()) return null
        val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "http://$raw"
        return try {
            val uri = URI(withScheme)
            val scheme = uri.scheme ?: "http"
            val host = uri.host ?: return null
            val port = if (uri.port == -1) 3000 else uri.port
            "$scheme://$host:$port/"
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val FIXED_PAGE_SIZE = 10

        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(
                    sessionRepository = container.sessionRepository,
                    itemRepository = container.itemRepository,
                    serviceAutoDiscovery = container.serviceAutoDiscovery
                ) as T
            }
        }
    }
}

data class MainUiState(
    val isBusy: Boolean = false,
    val isLoggedIn: Boolean = false,
    val internalUrl: String = "",
    val externalUrl: String = "",
    val endpoint: ActiveEndpoint = ActiveEndpoint.INTERNAL,
    val appTheme: AppTheme = AppTheme.SAND,
    val loginError: String? = null,
    val canSwitchRetry: Boolean = false,
    val lastLoginUsername: String = "",
    val lastLoginPassword: String = "",
    val toastMessage: String? = null,
    val houses: List<HouseDto> = emptyList(),
    val rooms: List<RoomDto> = emptyList(),
    val categories: List<CategoryDto> = emptyList(),
    val tags: List<TagDto> = emptyList(),
    val items: List<ItemDto> = emptyList(),
    val filter: ItemFilter = ItemFilter(),
    val currentPage: Int = 1,
    val pageSize: Int = 10,
    val totalItems: Int = 0,
    val totalPages: Int = 1,
    val showForm: Boolean = false,
    val editingItem: ItemDto? = null,
    val form: ItemFormState = ItemFormState(quantity = "1"),
    val newImageUris: List<Uri> = emptyList(),
    val removeImageIds: Set<Int> = emptySet(),
    val isRefreshingItems: Boolean = false,
    val listScrollToTopSignal: Int = 0,
    val isDiscoveringServices: Boolean = false,
    val discoveredServices: List<DiscoveredService> = emptyList(),
    val showDiscoveryDialog: Boolean = false,
    val pendingSessionExpireRedirect: Boolean = false
)

data class ItemFormState(
    val name: String = "",
    val brand: String = "",
    val quantity: String = "1",
    val locationDetail: String = "",
    val houseId: Int? = null,
    val roomId: Int? = null,
    val categoryId: Int? = null,
    val selectedTagIds: Set<Int> = emptySet(),
    val customTagNames: Set<String> = emptySet()
)

