package com.whereisit.findthings.data.repository

sealed class AppError(message: String) : Exception(message) {
    class Validation(message: String) : AppError(message)
    class Unauthorized(message: String = "登录已过期，请重新登录") : AppError(message)
    class Network(message: String = "网络连接失败，请检查地址或网络") : AppError(message)
    class Business(message: String) : AppError(message)
    class Unknown(message: String = "发生未知错误") : AppError(message)
}
