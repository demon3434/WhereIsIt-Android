package com.whereisit.findthings.data.network

import com.google.gson.JsonElement
import com.whereisit.findthings.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("/api/auth/login")
    suspend fun login(@Body request: LoginRequest): TokenResponse

    @GET("/api/health")
    suspend fun health(): HealthResponse

    @GET("/api/me")
    suspend fun me(@Header("Authorization") auth: String): UserMe

    @PUT("/api/me")
    suspend fun updateMe(
        @Header("Authorization") auth: String,
        @Body payload: ProfileUpdateRequest
    ): MessageResponse

    @GET("/api/houses")
    suspend fun houses(@Header("Authorization") auth: String): List<HouseDto>

    @GET("/api/rooms")
    suspend fun rooms(@Header("Authorization") auth: String): List<RoomDto>

    @GET("/api/categories")
    suspend fun categories(@Header("Authorization") auth: String): List<CategoryDto>

    @GET("/api/tags")
    suspend fun tags(@Header("Authorization") auth: String): List<TagDto>

    @GET("/api/items")
    suspend fun items(
        @Header("Authorization") auth: String,
        @Query("q") q: String? = null,
        @Query("category_id") categoryId: Int? = null,
        @Query("house_id") houseId: Int? = null,
        @Query("room_id") roomId: Int? = null,
        @Query("tag_id") tagId: Int? = null,
        @Query("page") page: Int? = null,
        @Query("page_size") pageSize: Int? = null,
        @Query("sort_by") sortBy: String? = null,
        @Query("sort_order") sortOrder: String? = null
    ): JsonElement

    @Multipart
    @POST("/api/items")
    suspend fun createItem(
        @Header("Authorization") auth: String,
        @Part("data") data: RequestBody,
        @Part files: List<MultipartBody.Part>
    ): ItemDto

    @Multipart
    @PUT("/api/items/{id}")
    suspend fun updateItem(
        @Header("Authorization") auth: String,
        @Path("id") id: Int,
        @Part("data") data: RequestBody,
        @Part files: List<MultipartBody.Part>
    ): ItemDto

    @DELETE("/api/items/{id}")
    suspend fun deleteItem(@Header("Authorization") auth: String, @Path("id") id: Int): MessageResponse

    @DELETE("/api/items/{itemId}/images/{imageId}")
    suspend fun deleteItemImage(
        @Header("Authorization") auth: String,
        @Path("itemId") itemId: Int,
        @Path("imageId") imageId: Int
    ): MessageResponse
}

