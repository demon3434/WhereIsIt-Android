package com.whereisit.findthings.data.model

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val username: String,
    val password: String
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String
)

data class MessageResponse(
    val message: String
)

data class UserMe(
    val id: Int,
    val username: String,
    @SerializedName("full_name") val fullName: String,
    val nickname: String,
    val role: String,
    @SerializedName("available_house_ids") val availableHouseIds: List<Int> = emptyList(),
    @SerializedName("default_house_id") val defaultHouseId: Int? = null
)

data class ProfileUpdateRequest(
    val nickname: String = "",
    @SerializedName("full_name") val fullName: String = "",
    @SerializedName("default_house_id") val defaultHouseId: Int? = null,
    val password: String? = null
)

data class HouseDto(
    val id: Int,
    val name: String,
    @SerializedName("is_active") val isActive: Boolean
)

data class RoomDto(
    val id: Int,
    val name: String,
    val path: String,
    @SerializedName("house_id") val houseId: Int?,
    @SerializedName("is_active") val isActive: Boolean
)

data class CategoryDto(
    val id: Int,
    val name: String,
    @SerializedName("is_active") val isActive: Boolean
)

data class TagDto(
    val id: Int,
    val name: String,
    @SerializedName("is_active") val isActive: Boolean
)

data class ItemImageDto(
    val id: Int,
    val url: String,
    @SerializedName("display_order") val displayOrder: Int = 0
)

data class ItemDto(
    val id: Int,
    val name: String,
    val brand: String,
    val quantity: Int,
    @SerializedName("location_detail") val locationDetail: String,
    @SerializedName("category_id") val categoryId: Int?,
    @SerializedName("category_name") val categoryName: String?,
    @SerializedName("house_id") val houseId: Int?,
    @SerializedName("house_name") val houseName: String?,
    @SerializedName("room_id") val roomId: Int?,
    @SerializedName("room_path") val roomPath: String?,
    val tags: List<TagDto>,
    val images: List<ItemImageDto>,
    @SerializedName("owner_display_name") val ownerDisplayName: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)

data class ItemCreatePayload(
    val name: String,
    val brand: String,
    val quantity: Int,
    @SerializedName("category_id") val categoryId: Int,
    @SerializedName("house_id") val houseId: Int,
    @SerializedName("room_id") val roomId: Int,
    @SerializedName("location_detail") val locationDetail: String,
    @SerializedName("tag_ids") val tagIds: List<Int>,
    @SerializedName("tag_names") val tagNames: List<String>,
    @SerializedName("image_orders") val imageOrders: List<ItemImageOrderPayload> = emptyList()
)

data class ItemImageOrderPayload(
    @SerializedName("image_id") val imageId: Int? = null,
    @SerializedName("file_key") val fileKey: String? = null,
    @SerializedName("display_order") val displayOrder: Int
)
