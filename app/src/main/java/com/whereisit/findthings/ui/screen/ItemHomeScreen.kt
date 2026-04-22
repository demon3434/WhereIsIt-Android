
package com.whereisit.findthings.ui.screen

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.whereisit.findthings.R
import com.whereisit.findthings.data.model.CategoryDto
import com.whereisit.findthings.data.model.HouseDto
import com.whereisit.findthings.data.model.ItemDto
import com.whereisit.findthings.data.model.ItemImageDto
import com.whereisit.findthings.data.model.RoomDto
import com.whereisit.findthings.data.model.TagDto
import com.whereisit.findthings.data.network.DiscoveredService
import com.whereisit.findthings.data.repository.ActiveEndpoint
import com.whereisit.findthings.data.repository.AppTheme
import com.whereisit.findthings.data.repository.ItemRepository
import com.whereisit.findthings.data.voice.VoiceSearchRepository
import com.whereisit.findthings.data.voice.model.VoiceFinalizeResponse
import com.whereisit.findthings.ui.ItemFormState
import com.whereisit.findthings.ui.MainUiState
import com.whereisit.findthings.ui.voice.VoiceSearchRoute
import java.io.File
import kotlin.math.roundToInt

private enum class SettingsPanel {
    MENU,
    SERVER,
    PASSWORD,
    THEME,
    LOGOUT
}

private data class ImagePreviewState(
    val item: ItemDto,
    val initialIndex: Int
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun ItemHomeScreen(
    modifier: Modifier,
    paddingValues: PaddingValues,
    state: MainUiState,
    repository: ItemRepository,
    voiceSearchRepository: VoiceSearchRepository,
    onLogout: () -> Unit,
    onSaveServerSettings: (String, String, ActiveEndpoint) -> Unit,
    onDiscoverServices: () -> Unit,
    onApplyFilter: (String, Int?, Int?, Int?, Set<Int>) -> Unit,
    onApplyVoiceSearchResult: (VoiceFinalizeResponse) -> Unit,
    onRefresh: () -> Unit,
    onCreateItem: (ItemFormState) -> Unit,
    onUpdateItem: (ItemFormState) -> Unit,
    onDeleteItem: (ItemDto) -> Unit,
    onOpenCreate: () -> Unit,
    onOpenEdit: (ItemDto) -> Unit,
    onCloseForm: () -> Unit,
    onSetDeleteImage: (Int, Boolean) -> Unit,
    onPickImages: (List<Uri>) -> Unit,
    onChangeTheme: (AppTheme) -> Unit,
    onChangePassword: (String) -> Unit,
    onChangePage: (Int) -> Unit
) {
    val context = LocalContext.current
    var settingsPanel by remember { mutableStateOf<SettingsPanel?>(null) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showVoiceSearch by remember { mutableStateOf(false) }
    var detailItem by remember { mutableStateOf<ItemDto?>(null) }
    var imagePreview by remember { mutableStateOf<ImagePreviewState?>(null) }
    var fullscreenPreview by remember { mutableStateOf<ImagePreviewState?>(null) }
    var pendingDeleteItem by remember { mutableStateOf<ItemDto?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    var keyword by remember(state.filter.keyword) { mutableStateOf(state.filter.keyword) }
    var houseId by remember(state.filter.houseId) { mutableStateOf(state.filter.houseId) }
    var roomId by remember(state.filter.roomId) { mutableStateOf(state.filter.roomId) }
    var categoryId by remember(state.filter.categoryId) { mutableStateOf(state.filter.categoryId) }
    var tagIds by remember(state.filter.tagIds) { mutableStateOf(state.filter.tagIds) }
    val recentTagIds = remember(state.items) {
        state.items
            .asSequence()
            .flatMap { item -> item.tags.map { tag -> tag.id } }
            .distinct()
            .toList()
    }
    val itemListState = rememberLazyListState()
    var handledScrollSignal by remember { mutableIntStateOf(0) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshingItems,
        onRefresh = onRefresh
    )
    val itemListScrollbarWidth by animateDpAsState(
        targetValue = if (itemListState.isScrollInProgress) 3.dp else 1.dp,
        label = "itemListScrollbarWidth"
    )
    val itemListScrollbarThumbColor by animateColorAsState(
        targetValue = if (itemListState.isScrollInProgress) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
        },
        label = "itemListScrollbarThumbColor"
    )
    val itemListScrollbarTrackColor by animateColorAsState(
        targetValue = if (itemListState.isScrollInProgress) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
        },
        label = "itemListScrollbarTrackColor"
    )

    LaunchedEffect(state.listScrollToTopSignal, state.currentPage, state.items.firstOrNull()?.id) {
        if (state.listScrollToTopSignal > handledScrollSignal && state.currentPage == 1) {
            itemListState.scrollToItem(0)
            handledScrollSignal = state.listScrollToTopSignal
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        onPickImages(uris)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val captured = pendingCameraUri
        pendingCameraUri = null
        if (success && captured != null) {
            val merged = (state.newImageUris + captured).distinct().take(9)
            onPickImages(merged)
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(context, "未授予相机权限，无法拍照", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val uri = createTempImageUri(context)
        if (uri == null) {
            Toast.makeText(context, "无法创建拍照文件，请稍后重试", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_app),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("找东西", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    Row {
                        IconButton(onClick = { showSearchDialog = true }) {
                            Icon(Icons.Default.Search, null)
                        }
                        IconButton(onClick = { showVoiceSearch = true }) {
                            Icon(painter = painterResource(id = R.drawable.ic_mic_24), contentDescription = "语音搜索")
                        }
                        IconButton(onClick = onOpenCreate) {
                            Icon(Icons.Default.Add, null)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { settingsPanel = SettingsPanel.MENU }) {
                        Icon(Icons.Default.Settings, null)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(bottom = paddingValues.calculateBottomPadding())
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.isBusy && !state.isRefreshingItems) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            if (state.isRefreshingItems) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("正在刷新...")
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .pullRefresh(pullRefreshState)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = itemListState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp)
                ) {
                    items(state.items, key = { it.id }) { item ->
                        ItemCard(
                            item = item,
                            state = state,
                            repository = repository,
                            onOpenEdit = onOpenEdit,
                            onDeleteItem = { pendingDeleteItem = item },
                            onOpenDetail = { detailItem = item },
                            onOpenImageViewer = { index -> imagePreview = ImagePreviewState(item, index) }
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(3.dp)
                        .prettyVerticalScrollbar(
                            state = itemListState,
                            width = itemListScrollbarWidth,
                            thumbColor = itemListScrollbarThumbColor,
                            trackColor = itemListScrollbarTrackColor
                        )
                )

                PullRefreshIndicator(
                    refreshing = state.isRefreshingItems,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            PaginationRow(
                currentPage = state.currentPage,
                totalPages = state.totalPages,
                totalItems = state.totalItems,
                onPickPage = onChangePage,
                onPrev = { if (state.currentPage > 1) onChangePage(state.currentPage - 1) },
                onNext = { if (state.currentPage < state.totalPages) onChangePage(state.currentPage + 1) }
            )
        }
    }

    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("搜索物品") },
            text = {
                FilterCard(
                    houses = state.houses,
                    rooms = state.rooms,
                    categories = state.categories,
                    tags = state.tags,
                    recentTagIds = recentTagIds,
                    keyword = keyword,
                    houseId = houseId,
                    roomId = roomId,
                    categoryId = categoryId,
                    tagIds = tagIds,
                    onKeyword = { keyword = it },
                    onHouse = {
                        houseId = it
                        roomId = null
                    },
                    onRoom = { roomId = it },
                    onCategory = { categoryId = it },
                    onTagIds = { tagIds = it },
                    onSearch = {
                        onApplyFilter(keyword, houseId, roomId, categoryId, tagIds)
                        showSearchDialog = false
                    },
                    onReset = {
                        keyword = ""
                        houseId = null
                        roomId = null
                        categoryId = null
                        tagIds = emptySet()
                        onApplyFilter("", null, null, null, emptySet())
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showSearchDialog = false }) { Text("关闭") }
            }
        )
    }

    if (showVoiceSearch) {
        VoiceSearchRoute(
            repository = voiceSearchRepository,
            onDismiss = { showVoiceSearch = false },
            onCompleted = { result ->
                onApplyVoiceSearchResult(result)
                showVoiceSearch = false
            }
        )
    }

    detailItem?.let { item ->
        ItemDetailDialog(
            item = item,
            state = state,
            repository = repository,
            onDismiss = { detailItem = null },
            onOpenImageViewer = { fullscreenPreview = ImagePreviewState(item, it) }
        )
    }

    imagePreview?.let { preview ->
        ItemImageViewerDialog(
            item = preview.item,
            initialIndex = preview.initialIndex,
            state = state,
            repository = repository,
            onOpenFullscreen = { idx -> fullscreenPreview = ImagePreviewState(preview.item, idx) },
            onDismiss = { imagePreview = null }
        )
    }

    fullscreenPreview?.let { preview ->
        ItemImageFullscreenDialog(
            item = preview.item,
            initialIndex = preview.initialIndex,
            state = state,
            repository = repository,
            onDismiss = { fullscreenPreview = null }
        )
    }

    when (settingsPanel) {
        SettingsPanel.MENU -> SettingsMenuDialog(
            onDismiss = { settingsPanel = null },
            onServer = { settingsPanel = SettingsPanel.SERVER },
            onPassword = { settingsPanel = SettingsPanel.PASSWORD },
            onTheme = { settingsPanel = SettingsPanel.THEME },
            onLogout = { settingsPanel = SettingsPanel.LOGOUT }
        )

        SettingsPanel.SERVER -> ServerSettingsDialog(
            state = state,
            onDismiss = { settingsPanel = SettingsPanel.MENU },
            onDiscoverServices = onDiscoverServices,
            onSave = { internal, external, endpoint ->
                onSaveServerSettings(internal, external, endpoint)
                settingsPanel = null
                onRefresh()
            }
        )

        SettingsPanel.PASSWORD -> ChangePasswordDialog(
            onDismiss = { settingsPanel = SettingsPanel.MENU },
            onSubmit = {
                onChangePassword(it)
                settingsPanel = null
            }
        )

        SettingsPanel.THEME -> ThemePickerDialog(
            currentTheme = state.appTheme,
            onDismiss = { settingsPanel = SettingsPanel.MENU },
            onSubmit = {
                onChangeTheme(it)
                settingsPanel = null
            }
        )

        SettingsPanel.LOGOUT -> LogoutConfirmDialog(
            onDismiss = { settingsPanel = SettingsPanel.MENU },
            onConfirm = {
                settingsPanel = null
                onLogout()
            }
        )

        null -> Unit
    }

    if (state.showForm) {
        ItemFormDialog(
            state = state,
            repository = repository,
            onDismiss = onCloseForm,
            onPickImages = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onChangeNewImages = onPickImages,
            onTakePhoto = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    val uri = createTempImageUri(context)
                    if (uri == null) {
                        Toast.makeText(context, "无法创建拍照文件，请稍后重试", Toast.LENGTH_SHORT).show()
                    } else {
                        pendingCameraUri = uri
                        cameraLauncher.launch(uri)
                    }
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onSubmit = { form ->
                if (state.editingItem == null) onCreateItem(form) else onUpdateItem(form)
            },
            onSetDeleteImage = onSetDeleteImage
        )
    }

    pendingDeleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDeleteItem = null },
            title = { Text("删除物品") },
            text = { Text("确认删除“${item.name}”吗？") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteItem = null
                    onDeleteItem(item)
                }) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteItem = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun FilterCard(
    houses: List<HouseDto>,
    rooms: List<RoomDto>,
    categories: List<CategoryDto>,
    tags: List<TagDto>,
    recentTagIds: List<Int>,
    keyword: String,
    houseId: Int?,
    roomId: Int?,
    categoryId: Int?,
    tagIds: Set<Int>,
    onKeyword: (String) -> Unit,
    onHouse: (Int?) -> Unit,
    onRoom: (Int?) -> Unit,
    onCategory: (Int?) -> Unit,
    onTagIds: (Set<Int>) -> Unit,
    onSearch: () -> Unit,
    onReset: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = keyword,
                onValueChange = onKeyword,
                label = { Text("关键字") },
                placeholder = { Text("输入名称或品牌") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            SelectField("房屋", houses.map { it.id to it.name }, houseId, onHouse)
            SelectField("房间", rooms.filter { houseId == null || it.houseId == houseId }.map { it.id to it.path }, roomId, onRoom)
            SelectField("分类", categories.map { it.id to it.name }, categoryId, onCategory)
            MultiTagSelectorField(
                label = "标签",
                tags = tags,
                recentTagIds = recentTagIds,
                selectedTagIds = tagIds,
                onChange = onTagIds,
                allowClear = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSearch, modifier = Modifier.weight(1f)) { Text("查询") }
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("重置") }
            }
        }
    }
}

@Composable
private fun SelectField(
    label: String,
    options: List<Pair<Int, String>>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    required: Boolean = false,
    allowClear: Boolean = true
) {
    var showPicker by remember { mutableStateOf(false) }
    val selectedText = options.firstOrNull { it.first == selected }?.second ?: if (allowClear) "全部" else "请选择"
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (required) {
            RequiredFieldLabel(label = label, style = MaterialTheme.typography.labelMedium)
        } else {
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedText, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(6.dp))
            Text("▼")
        }
    }

    if (showPicker) {
        val pickerListState = rememberLazyListState()
        val pickerScrollbarWidth by animateDpAsState(
            targetValue = if (pickerListState.isScrollInProgress) 2.dp else 1.dp,
            label = "pickerScrollbarWidth"
        )
        val pickerScrollbarThumbColor by animateColorAsState(
            targetValue = if (pickerListState.isScrollInProgress) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
            },
            label = "pickerScrollbarThumbColor"
        )
        val pickerScrollbarTrackColor by animateColorAsState(
            targetValue = if (pickerListState.isScrollInProgress) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            },
            label = "pickerScrollbarTrackColor"
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("选择$label") },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .height(320.dp)
                        .prettyVerticalScrollbar(
                            state = pickerListState,
                            width = pickerScrollbarWidth,
                            thumbColor = pickerScrollbarThumbColor,
                            trackColor = pickerScrollbarTrackColor
                        ),
                    state = pickerListState,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (allowClear) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelect(null)
                                        showPicker = false
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected == null,
                                    onClick = {
                                        onSelect(null)
                                        showPicker = false
                                    }
                                )
                                Text("全部")
                            }
                        }
                    }
                    if (options.isEmpty()) {
                        item { Text("暂无可选项", modifier = Modifier.padding(8.dp)) }
                    } else {
                        items(options, key = { it.first }) { (id, text) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelect(id)
                                        showPicker = false
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected == id,
                                    onClick = {
                                        onSelect(id)
                                        showPicker = false
                                    }
                                )
                                Text(text)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun RequiredFieldLabel(label: String, style: TextStyle) {
    Text(
        text = buildAnnotatedString {
            append(label)
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) {
                append("*")
            }
        },
        style = style
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiTagSelectorField(
    label: String,
    tags: List<TagDto>,
    recentTagIds: List<Int>,
    selectedTagIds: Set<Int>,
    onChange: (Set<Int>) -> Unit,
    allowClear: Boolean
) {
    var showPicker by remember { mutableStateOf(false) }
    val selectedNames = tags.filter { it.id in selectedTagIds }.map { it.name }
    val summary = when {
        selectedNames.isEmpty() -> if (allowClear) "全部" else "未选择"
        selectedNames.size <= 2 -> selectedNames.joinToString("、")
        else -> "已选 ${selectedNames.size} 个"
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text(summary, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(6.dp))
            Text("▼")
        }
    }

    if (showPicker) {
        var query by remember { mutableStateOf("") }
        var localSelection by remember(selectedTagIds) { mutableStateOf(selectedTagIds) }
        val activeTags = tags.filter { it.isActive }
        val activeById = activeTags.associateBy { it.id }
        val recentOrderedTags = recentTagIds.mapNotNull { activeById[it] }.distinctBy { it.id }
        val fallbackTags = activeTags
            .filterNot { it.id in recentOrderedTags.map { tag -> tag.id }.toSet() }
            .sortedBy { it.name }
        val defaultOrderedTags = recentOrderedTags + fallbackTags
        val queryValue = query.trim()
        val candidateTags = if (queryValue.isBlank()) {
            defaultOrderedTags.take(10)
        } else {
            activeTags.filter { it.name.contains(queryValue, ignoreCase = true) }.sortedBy { it.name }
        }
        val pickerScrollState = rememberScrollState()
        val candidateListState = rememberLazyListState()
        val pickerScrollbarWidth by animateDpAsState(
            targetValue = if (pickerScrollState.isScrollInProgress) 2.dp else 1.dp,
            label = "pickerTagScrollbarWidth"
        )
        val pickerScrollbarThumbColor by animateColorAsState(
            targetValue = if (pickerScrollState.isScrollInProgress) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
            },
            label = "pickerTagScrollbarThumbColor"
        )
        val pickerScrollbarTrackColor by animateColorAsState(
            targetValue = if (pickerScrollState.isScrollInProgress) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            },
            label = "pickerTagScrollbarTrackColor"
        )
        val candidateScrollbarWidth by animateDpAsState(
            targetValue = if (candidateListState.isScrollInProgress) 2.dp else 1.dp,
            label = "candidateTagScrollbarWidth"
        )
        val candidateScrollbarThumbColor by animateColorAsState(
            targetValue = if (candidateListState.isScrollInProgress) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
            },
            label = "candidateTagScrollbarThumbColor"
        )
        val candidateScrollbarTrackColor by animateColorAsState(
            targetValue = if (candidateListState.isScrollInProgress) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            },
            label = "candidateTagScrollbarTrackColor"
        )

        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("选择$label") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(pickerScrollState)
                            .padding(end = 0.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("搜索标签") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (allowClear) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { localSelection = emptySet() }) { Text("清空标签") }
                            }
                        }
                        Text("可勾选标签", style = MaterialTheme.typography.labelMedium)
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .prettyVerticalScrollbar(
                                    state = candidateListState,
                                    width = candidateScrollbarWidth,
                                    thumbColor = candidateScrollbarThumbColor,
                                    trackColor = candidateScrollbarTrackColor
                                ),
                            state = candidateListState,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (candidateTags.isEmpty()) {
                                item {
                                    Text("没有匹配标签", modifier = Modifier.padding(8.dp))
                                }
                            } else {
                                items(candidateTags, key = { it.id }) { tag ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                localSelection = if (tag.id in localSelection) {
                                                    localSelection - tag.id
                                                } else {
                                                    localSelection + tag.id
                                                }
                                            },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = tag.id in localSelection,
                                            onCheckedChange = { checked ->
                                                localSelection = if (checked) localSelection + tag.id else localSelection - tag.id
                                            }
                                        )
                                        Text(tag.name)
                                    }
                                }
                            }
                        }
                        val selectedTagNames = activeTags.filter { it.id in localSelection }.map { it.name }
                        if (selectedTagNames.isNotEmpty()) {
                            Text("已选标签", style = MaterialTheme.typography.labelMedium)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                selectedTagNames.forEach { tagName ->
                                    InputChip(
                                        selected = true,
                                        onClick = {
                                            val existing = tags.firstOrNull { it.name == tagName }
                                            if (existing != null) {
                                                localSelection = localSelection - existing.id
                                            }
                                        },
                                        label = { Text(tagName) },
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "移除标签"
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(3.dp)
                            .offset(x = 22.dp)
                            .prettyVerticalScrollbar(
                                state = pickerScrollState,
                                width = pickerScrollbarWidth,
                                thumbColor = pickerScrollbarThumbColor,
                                trackColor = pickerScrollbarTrackColor,
                                maxThumbRatio = 0.42f
                            )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onChange(localSelection)
                    showPicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemCard(
    item: ItemDto,
    state: MainUiState,
    repository: ItemRepository,
    onOpenEdit: (ItemDto) -> Unit,
    onDeleteItem: (ItemDto) -> Unit,
    onOpenDetail: () -> Unit,
    onOpenImageViewer: (Int) -> Unit
) {
    val imageBase = if (state.endpoint == ActiveEndpoint.INTERNAL) state.internalUrl else state.externalUrl
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ItemThumbnail(
                item = item,
                imageBase = imageBase,
                repository = repository,
                onClick = { onOpenImageViewer(0) }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenDetail() },
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = { onOpenEdit(item) }) { Icon(Icons.Default.Edit, null) }
                    IconButton(onClick = { onDeleteItem(item) }) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                    }
                }
                Text("分类：${item.categoryName ?: "-"}")
                Text("房间：${item.roomPath ?: "-"}")
                Text("位置：${item.locationDetail.ifBlank { "-" }}")
                Text("更新日期：${formatDate(item.updatedAt)}")
                if (item.tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        item.tags.forEach { tag ->
                            AssistChip(onClick = {}, label = { Text(tag.name) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditableTagField(
    tags: List<TagDto>,
    recentTagIds: List<Int>,
    selectedTagIds: Set<Int>,
    customTagNames: Set<String>,
    onSelectedTagIdsChange: (Set<Int>) -> Unit,
    onCustomTagNamesChange: (Set<String>) -> Unit
) {
    var input by remember { mutableStateOf("") }
    val activeTags = tags.filter { it.isActive }
    val activeById = activeTags.associateBy { it.id }
    val recentOrderedTags = recentTagIds.mapNotNull { activeById[it] }.distinctBy { it.id }
    val fallbackTags = activeTags.filterNot { it.id in recentOrderedTags.map { tag -> tag.id }.toSet() }
        .sortedBy { it.name }
    val defaultOrderedTags = recentOrderedTags + fallbackTags
    val selectedExisting = activeTags.filter { it.id in selectedTagIds }
    val normalizedCustom = customTagNames.map { it.trim() }.filter { it.isNotBlank() }.toSet()
    val query = input.trim()
    val matchedSuggestions = if (query.isBlank()) {
        defaultOrderedTags
    } else {
        activeTags.filter { it.name.contains(query, ignoreCase = true) }
    }
    val suggestions = if (query.isBlank()) matchedSuggestions.take(5) else matchedSuggestions
    val suggestionListState = rememberLazyListState()
    val suggestionScrollbarWidth by animateDpAsState(
        targetValue = if (suggestionListState.isScrollInProgress) 2.dp else 1.dp,
        label = "suggestionScrollbarWidth"
    )
    val suggestionScrollbarThumbColor by animateColorAsState(
        targetValue = if (suggestionListState.isScrollInProgress) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
        },
        label = "suggestionScrollbarThumbColor"
    )
    val suggestionScrollbarTrackColor by animateColorAsState(
        targetValue = if (suggestionListState.isScrollInProgress) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
        },
        label = "suggestionScrollbarTrackColor"
    )

    fun addCustomFromInput() {
        val name = input.trim()
        if (name.isBlank()) return
        if (selectedExisting.any { it.name.equals(name, ignoreCase = true) }) {
            input = ""
            return
        }
        onCustomTagNamesChange(normalizedCustom + name)
        input = ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("标签", style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("搜索或新增标签") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { addCustomFromInput() }) { Text("新增标签") }
            OutlinedButton(
                onClick = {
                    onSelectedTagIdsChange(emptySet())
                    onCustomTagNamesChange(emptySet())
                    input = ""
                }
            ) { Text("清空标签") }
        }
        if (suggestions.isNotEmpty()) {
            Text("可勾选标签", style = MaterialTheme.typography.labelMedium)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .prettyVerticalScrollbar(
                        state = suggestionListState,
                        width = suggestionScrollbarWidth,
                        thumbColor = suggestionScrollbarThumbColor,
                        trackColor = suggestionScrollbarTrackColor
                    ),
                state = suggestionListState,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(suggestions, key = { it.id }) { tag ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectedTagIdsChange(selectedTagIds + tag.id) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = tag.id in selectedTagIds,
                            onCheckedChange = { checked ->
                                onSelectedTagIdsChange(if (checked) selectedTagIds + tag.id else selectedTagIds - tag.id)
                            }
                        )
                        Text(tag.name)
                    }
                }
            }
        }

        val allTagNames = selectedExisting.map { it.name } + normalizedCustom.toList()
        if (allTagNames.isNotEmpty()) {
            Text("已选标签", style = MaterialTheme.typography.labelMedium)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                allTagNames.forEach { tagName ->
                    InputChip(
                        selected = true,
                        onClick = {
                            val existing = selectedExisting.firstOrNull { it.name == tagName }
                            if (existing != null) {
                                onSelectedTagIdsChange(selectedTagIds - existing.id)
                            } else {
                                onCustomTagNamesChange(normalizedCustom - tagName)
                            }
                        },
                        label = { Text(tagName) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "移除标签") }
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemThumbnail(
    item: ItemDto,
    imageBase: String,
    repository: ItemRepository,
    onClick: () -> Unit
) {
    val firstImage = item.images.firstOrNull()
    val modifier = Modifier
        .width(96.dp)
        .height(96.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .clickable(enabled = firstImage != null, onClick = onClick)

    if (firstImage == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("无图片", style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    val base = if (imageBase.endsWith('/')) imageBase else "$imageBase/"
    AsyncImage(
        model = repository.fullImageUrl(base, firstImage.url),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

@Composable
private fun PaginationRow(
    currentPage: Int,
    totalPages: Int,
    totalItems: Int,
    onPickPage: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val safeTotalPages = totalPages.coerceAtLeast(1)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        val visiblePageCount = when {
            maxWidth < 340.dp -> 3
            maxWidth < 420.dp -> 4
            else -> 5
        }
        val useArrowNav = maxWidth < 420.dp

        val startPage = (currentPage - visiblePageCount / 2)
            .coerceAtLeast(1)
            .coerceAtMost((safeTotalPages - visiblePageCount + 1).coerceAtLeast(1))
        val endPage = (startPage + visiblePageCount - 1).coerceAtMost(safeTotalPages)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (useArrowNav) {
                    IconButton(onClick = onPrev, enabled = currentPage > 1) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "上一页")
                    }
                } else {
                    TextButton(onClick = onPrev, enabled = currentPage > 1) {
                        Text("前页")
                    }
                }

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center
                ) {
                    for (page in startPage..endPage) {
                        val selected = page == currentPage
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                )
                                .clickable { onPickPage(page) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = page.toString(),
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                if (useArrowNav) {
                    IconButton(onClick = onNext, enabled = currentPage < safeTotalPages) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "下一页")
                    }
                } else {
                    TextButton(onClick = onNext, enabled = currentPage < safeTotalPages) {
                        Text("后页")
                    }
                }
            }

            Text(
                text = "第 ${currentPage.coerceAtMost(safeTotalPages)}/${safeTotalPages} 页 · 共 ${totalItems} 条",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun ItemImageViewerDialog(
    item: ItemDto,
    initialIndex: Int,
    state: MainUiState,
    repository: ItemRepository,
    onOpenFullscreen: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val images = item.images
    if (images.isEmpty()) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${item.name} 图片浏览") },
        text = {
            ThumbnailImageBrowser(
                images = images,
                imageBase = if (state.endpoint == ActiveEndpoint.INTERNAL) state.internalUrl else state.externalUrl,
                repository = repository,
                initialIndex = initialIndex,
                imageHeight = 330.dp,
                onOpenFullscreen = onOpenFullscreen
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun ItemDetailDialog(
    item: ItemDto,
    state: MainUiState,
    repository: ItemRepository,
    onDismiss: () -> Unit,
    onOpenImageViewer: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("物品详情") },
        text = {
            Column(
                modifier = Modifier
                    .height(520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("名称：${item.name}")
                Text("品牌：${item.brand.ifBlank { "-" }}")
                Text("数量：${item.quantity}")
                Text("分类：${item.categoryName ?: "-"}")
                Text("标签：${item.tags.joinToString("、") { it.name }.ifBlank { "-" }}")
                Text("房间：${item.roomPath ?: "-"}")
                Text("位置：${item.locationDetail}")
                Text("录入人：${item.ownerDisplayName}")
                Text("更新日期：${formatDate(item.updatedAt)}")

                if (item.images.isNotEmpty()) {
                    Text("照片浏览")
                    ThumbnailImageBrowser(
                        images = item.images,
                        imageBase = if (state.endpoint == ActiveEndpoint.INTERNAL) state.internalUrl else state.externalUrl,
                        repository = repository,
                        initialIndex = 0,
                        imageHeight = 220.dp,
                        onOpenFullscreen = onOpenImageViewer
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun ThumbnailImageBrowser(
    images: List<ItemImageDto>,
    imageBase: String,
    repository: ItemRepository,
    initialIndex: Int,
    imageHeight: Dp,
    onOpenFullscreen: (Int) -> Unit
) {
    var selectedIndex by remember(images, initialIndex) {
        mutableIntStateOf(initialIndex.coerceIn(0, images.lastIndex))
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val selected = images[selectedIndex]
        val base = if (imageBase.endsWith('/')) imageBase else "$imageBase/"
        AsyncImage(
            model = repository.fullImageUrl(base, selected.url),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onOpenFullscreen(selectedIndex) }
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButton(
                onClick = { selectedIndex = (selectedIndex - 1).coerceAtLeast(0) },
                enabled = selectedIndex > 0
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "上一张")
            }
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(images, key = { _, img -> img.id }) { index, img ->
                    val thumbBorder = if (index == selectedIndex) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    }
                    AsyncImage(
                        model = repository.fullImageUrl(base, img.url),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .border(2.dp, thumbBorder, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { selectedIndex = index }
                    )
                }
            }
            IconButton(
                onClick = { selectedIndex = (selectedIndex + 1).coerceAtMost(images.lastIndex) },
                enabled = selectedIndex < images.lastIndex
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = "下一张")
            }
        }
        Text(
            text = "${selectedIndex + 1} / ${images.size}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ItemImageFullscreenDialog(
    item: ItemDto,
    initialIndex: Int,
    state: MainUiState,
    repository: ItemRepository,
    onDismiss: () -> Unit
) {
    val images = item.images
    if (images.isEmpty()) return
    val base = if (state.endpoint == ActiveEndpoint.INTERNAL) state.internalUrl else state.externalUrl
    val normalizedBase = if (base.endsWith('/')) base else "$base/"
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, images.lastIndex),
        pageCount = { images.size }
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
        ) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${pagerState.currentPage + 1}/${images.size}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    ZoomableAsyncImage(
                        model = repository.fullImageUrl(normalizedBase, images[page].url),
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomableAsyncImage(
    model: String,
    modifier: Modifier = Modifier
) {
    var scale by remember(model) { mutableFloatStateOf(1f) }
    var offsetX by remember(model) { mutableFloatStateOf(0f) }
    var offsetY by remember(model) { mutableFloatStateOf(0f) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        if (scale > 1f) {
            offsetX += panChange.x
            offsetY += panChange.y
        } else {
            offsetX = 0f
            offsetY = 0f
        }
    }

    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .transformable(state = transformState)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .offset {
                IntOffset(offsetX.roundToInt(), offsetY.roundToInt())
            }
    )
}

@Composable
private fun SettingsMenuDialog(
    onDismiss: () -> Unit,
    onServer: () -> Unit,
    onPassword: () -> Unit,
    onTheme: () -> Unit,
    onLogout: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onServer, modifier = Modifier.fillMaxWidth()) { Text("修改服务器信息") }
                Button(onClick = onPassword, modifier = Modifier.fillMaxWidth()) { Text("修改密码") }
                Button(onClick = onTheme, modifier = Modifier.fillMaxWidth()) { Text("切换主题") }
                OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("退出登录") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun ServerSettingsDialog(
    state: MainUiState,
    onDismiss: () -> Unit,
    onDiscoverServices: () -> Unit,
    onSave: (String, String, ActiveEndpoint) -> Unit
) {
    var internal by remember(state.internalUrl) { mutableStateOf(state.internalUrl) }
    var external by remember(state.externalUrl) { mutableStateOf(state.externalUrl) }
    var endpoint by remember(state.endpoint) { mutableStateOf(state.endpoint) }
    var waitingForDiscovery by remember { mutableStateOf(false) }
    var showDiscoveryUrlDialog by remember { mutableStateOf(false) }

    LaunchedEffect(waitingForDiscovery, state.isDiscoveringServices, state.discoveredServices) {
        if (waitingForDiscovery && !state.isDiscoveringServices) {
            waitingForDiscovery = false
            if (state.discoveredServices.isNotEmpty()) {
                showDiscoveryUrlDialog = true
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改服务器信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(internal, { internal = it }, label = { Text("内网 URL") }, singleLine = true)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = {
                            waitingForDiscovery = true
                            onDiscoverServices()
                        },
                        enabled = !state.isDiscoveringServices,
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        if (state.isDiscoveringServices) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                        }
                        Text(if (state.isDiscoveringServices) "正在发现内网 URL..." else "内网发现")
                    }
                }
                OutlinedTextField(external, { external = it }, label = { Text("外网 URL") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = endpoint == ActiveEndpoint.INTERNAL,
                        onClick = { endpoint = ActiveEndpoint.INTERNAL },
                        label = { Text("内网生效") }
                    )
                    FilterChip(
                        selected = endpoint == ActiveEndpoint.EXTERNAL,
                        onClick = { endpoint = ActiveEndpoint.EXTERNAL },
                        label = { Text("外网生效") }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(internal, external, endpoint) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (showDiscoveryUrlDialog) {
        DiscoveryUrlDialog(
            services = state.discoveredServices,
            onSelect = { service ->
                internal = service.baseUrl
                showDiscoveryUrlDialog = false
            },
            onDismiss = { showDiscoveryUrlDialog = false }
        )
    }
}

@Composable
private fun DiscoveryUrlDialog(
    services: List<DiscoveredService>,
    onSelect: (DiscoveredService) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("请选择内网 URL") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .height(240.dp)
                    .prettyVerticalScrollbar(listState),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(services, key = { it.baseUrl }) { service ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(service) }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(service.baseUrl, fontWeight = FontWeight.SemiBold)
                        if (service.name.isNotBlank()) {
                            Text(service.name, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("新密码") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("确认密码") },
                    singleLine = true
                )
                if (confirm.isNotBlank() && confirm != password) {
                    Text("两次输入不一致", color = MaterialTheme.colorScheme.tertiary)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(password) },
                enabled = password.length >= 6 && password == confirm
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ThemePickerDialog(
    currentTheme: AppTheme,
    onDismiss: () -> Unit,
    onSubmit: (AppTheme) -> Unit
) {
    var selected by remember(currentTheme) { mutableStateOf(currentTheme) }
    val options = listOf(
        AppTheme.SAND to "沙砾米白",
        AppTheme.MINT to "薄荷绿",
        AppTheme.SKY to "晴空蓝",
        AppTheme.PEACH to "桃杏暖"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("切换主题") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { (theme, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = theme },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == theme, onClick = { selected = theme })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(selected) }) { Text("应用") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun LogoutConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("退出登录") },
        text = { Text("确认退出当前账号吗？") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确认") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ItemFormDialog(
    state: MainUiState,
    repository: ItemRepository,
    onDismiss: () -> Unit,
    onPickImages: () -> Unit,
    onChangeNewImages: (List<Uri>) -> Unit,
    onTakePhoto: () -> Unit,
    onSubmit: (ItemFormState) -> Unit,
    onSetDeleteImage: (Int, Boolean) -> Unit
) {
    var form by remember(state.form, state.editingItem?.id) { mutableStateOf(state.form) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var pendingDeleteImageId by remember { mutableStateOf<Int?>(null) }
    var showUpdateConfirm by remember { mutableStateOf(false) }
    val imageBase = if (state.endpoint == ActiveEndpoint.INTERNAL) state.internalUrl else state.externalUrl
    val formScrollState = rememberScrollState()
    val formScrollbarWidth by animateDpAsState(
        targetValue = if (formScrollState.isScrollInProgress) 3.dp else 1.dp,
        label = "formScrollbarWidth"
    )
    val formScrollbarThumbColor by animateColorAsState(
        targetValue = if (formScrollState.isScrollInProgress) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
        },
        label = "formScrollbarThumbColor"
    )
    val formScrollbarTrackColor by animateColorAsState(
        targetValue = if (formScrollState.isScrollInProgress) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
        },
        label = "formScrollbarTrackColor"
    )
    val recentTagIds = remember(state.items) {
        state.items
            .sortedByDescending { it.updatedAt }
            .flatMap { item -> item.tags.map { tag -> tag.id } }
            .distinct()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.editingItem == null) "新增物品" else "编辑物品") },
        confirmButton = {
            TextButton(onClick = {
                if (state.editingItem == null) {
                    onSubmit(form)
                } else {
                    showUpdateConfirm = true
                }
            }) {
                Text(if (state.editingItem == null) "新增" else "保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        text = {
            Box(
                modifier = Modifier
                    .height(500.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(formScrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        form.name,
                        { form = form.copy(name = it) },
                        label = { RequiredFieldLabel("名称", style = MaterialTheme.typography.bodySmall) }
                    )
                    OutlinedTextField(form.brand, { form = form.copy(brand = it) }, label = { Text("品牌") })
                    OutlinedTextField(
                        form.quantity,
                        { form = form.copy(quantity = it.filter(Char::isDigit)) },
                        label = { RequiredFieldLabel("数量", style = MaterialTheme.typography.bodySmall) }
                    )
                    SelectField(
                        label = "分类",
                        options = state.categories.map { it.id to it.name },
                        selected = form.categoryId,
                        onSelect = { form = form.copy(categoryId = it) },
                        required = true,
                        allowClear = false
                    )
                    SelectField(
                        label = "房屋",
                        options = state.houses.map { it.id to it.name },
                        selected = form.houseId,
                        onSelect = { form = form.copy(houseId = it, roomId = null) },
                        required = true,
                        allowClear = false
                    )
                    SelectField(
                        label = "房间",
                        options = state.rooms.filter { form.houseId == null || it.houseId == form.houseId }.map { it.id to it.path },
                        selected = form.roomId,
                        onSelect = { form = form.copy(roomId = it) },
                        required = true,
                        allowClear = false
                    )
                    OutlinedTextField(
                        form.locationDetail,
                        { form = form.copy(locationDetail = it) },
                        label = { RequiredFieldLabel("具体位置", style = MaterialTheme.typography.bodySmall) }
                    )

                    EditableTagField(
                        tags = state.tags,
                        recentTagIds = recentTagIds,
                        selectedTagIds = form.selectedTagIds,
                        customTagNames = form.customTagNames,
                        onSelectedTagIdsChange = { form = form.copy(selectedTagIds = it) },
                        onCustomTagNamesChange = { form = form.copy(customTagNames = it) }
                    )

                    if (state.editingItem?.images?.isNotEmpty() == true) {
                        Text("现有图片")
                        state.editingItem.images.forEach { image ->
                            if (image.id !in state.removeImageIds) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val base = if (imageBase.endsWith('/')) imageBase else "$imageBase/"
                                    AsyncImage(
                                        model = repository.fullImageUrl(base, image.url),
                                        contentDescription = null,
                                        modifier = Modifier.size(70.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(Modifier.weight(1f))
                                    IconButton(onClick = { pendingDeleteImageId = image.id }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "删除图片",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { showSourceDialog = true }) {
                            Text("拍照/上传照片")
                        }
                        Text("已选 ${state.newImageUris.size} 张")
                    }
                    state.newImageUris.forEachIndexed { index, uri ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier.size(70.dp),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = {
                                onChangeNewImages(state.newImageUris.filterIndexed { i, _ -> i != index })
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "删除图片",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = 24.dp)
                        .fillMaxHeight()
                        .width(3.dp)
                        .prettyVerticalScrollbar(
                            state = formScrollState,
                            width = formScrollbarWidth,
                            thumbColor = formScrollbarThumbColor,
                            trackColor = formScrollbarTrackColor
                        )
                )
            }
        }
    )

    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text("选择图片来源") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showSourceDialog = false
                            onTakePhoto()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("拍照") }
                    OutlinedButton(
                        onClick = {
                            showSourceDialog = false
                            onPickImages()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("从相册选择") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSourceDialog = false }) { Text("关闭") }
            }
        )
    }

    if (pendingDeleteImageId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteImageId = null },
            title = { Text("删除图片") },
            text = { Text("确认删除这张现有图片吗？保存后生效。") },
            confirmButton = {
                TextButton(onClick = {
                    onSetDeleteImage(pendingDeleteImageId!!, true)
                    pendingDeleteImageId = null
                }) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteImageId = null }) { Text("取消") }
            }
        )
    }

    if (showUpdateConfirm) {
        AlertDialog(
            onDismissRequest = { showUpdateConfirm = false },
            title = { Text("确认保存") },
            text = { Text("确认提交本次修改（含图片变更）吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showUpdateConfirm = false
                    onSubmit(form)
                }) { Text("确认保存") }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateConfirm = false }) { Text("取消") }
            }
        )
    }
}

private fun formatDate(raw: String): String {
    return if (raw.length >= 10) raw.substring(0, 10) else raw
}

private fun createTempImageUri(context: Context): Uri? {
    return runCatching {
        val folder = File(context.cacheDir, "camera").apply {
            if (!exists() && !mkdirs()) {
                error("Failed to create cache camera folder")
            }
        }
        val file = File.createTempFile("capture_", ".jpg", folder)
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }.getOrNull()
}

fun Modifier.prettyVerticalScrollbar(
    state: LazyListState,
    width: Dp = 5.dp,
    minThumbHeight: Dp = 28.dp,
    thumbColor: Color = Color(0x802D7FF9),
    trackColor: Color = Color(0x1F2D7FF9),
    endPadding: Dp = 0.dp
): Modifier = drawWithContent {
    drawContent()
    val layoutInfo = state.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    if (layoutInfo.totalItemsCount <= 0 || visibleItems.isEmpty()) return@drawWithContent

    val viewportSize = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
    if (viewportSize <= 0f) return@drawWithContent

    val avgItemSize = visibleItems.map { it.size }.average().toFloat().coerceAtLeast(1f)
    val estimatedContentSize = avgItemSize * layoutInfo.totalItemsCount
    if (estimatedContentSize <= viewportSize) return@drawWithContent

    val first = visibleItems.first()
    val currentScroll = (first.index * avgItemSize) + (layoutInfo.viewportStartOffset - first.offset)
    val maxScroll = (estimatedContentSize - viewportSize).coerceAtLeast(1f)

    val widthPx = width.toPx()
    val endPaddingPx = endPadding.toPx()
    val minThumbHeightPx = minThumbHeight.toPx()
    val thumbHeight = ((viewportSize / estimatedContentSize) * size.height).coerceIn(minThumbHeightPx, size.height)
    val travel = (size.height - thumbHeight).coerceAtLeast(0f)
    val thumbTop = (currentScroll / maxScroll * travel).coerceIn(0f, travel)
    val x = (size.width - widthPx - endPaddingPx).coerceAtLeast(0f)
    val radius = CornerRadius(widthPx / 2f, widthPx / 2f)

    drawRoundRect(
        color = trackColor,
        topLeft = Offset(x, 0f),
        size = Size(widthPx, size.height),
        cornerRadius = radius
    )
    drawRoundRect(
        color = thumbColor,
        topLeft = Offset(x, thumbTop),
        size = Size(widthPx, thumbHeight),
        cornerRadius = radius
    )
}

fun Modifier.prettyVerticalScrollbar(
    state: ScrollState,
    width: Dp = 5.dp,
    minThumbHeight: Dp = 28.dp,
    thumbColor: Color = Color(0x802D7FF9),
    trackColor: Color = Color(0x1F2D7FF9),
    endPadding: Dp = 0.dp,
    maxThumbRatio: Float = 1f
): Modifier = drawWithContent {
    drawContent()
    val maxScroll = state.maxValue.toFloat()
    if (maxScroll <= 0f || size.height <= 0f) return@drawWithContent

    val viewportHeight = size.height
    val contentHeight = viewportHeight + maxScroll
    val widthPx = width.toPx()
    val endPaddingPx = endPadding.toPx()
    val minThumbHeightPx = minThumbHeight.toPx()
    val clampedMaxRatio = maxThumbRatio.coerceIn(0.1f, 1f)
    val maxThumbHeightPx = (viewportHeight * clampedMaxRatio).coerceAtLeast(minThumbHeightPx)
    val thumbHeight = ((viewportHeight / contentHeight) * viewportHeight).coerceIn(minThumbHeightPx, maxThumbHeightPx)
    val travel = (viewportHeight - thumbHeight).coerceAtLeast(0f)
    val thumbTop = (state.value / maxScroll * travel).coerceIn(0f, travel)
    val x = (size.width - widthPx - endPaddingPx).coerceAtLeast(0f)
    val radius = CornerRadius(widthPx / 2f, widthPx / 2f)

    drawRoundRect(
        color = trackColor,
        topLeft = Offset(x, 0f),
        size = Size(widthPx, viewportHeight),
        cornerRadius = radius
    )
    drawRoundRect(
        color = thumbColor,
        topLeft = Offset(x, thumbTop),
        size = Size(widthPx, thumbHeight),
        cornerRadius = radius
    )
}


