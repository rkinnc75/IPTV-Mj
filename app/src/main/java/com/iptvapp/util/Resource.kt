package com.iptvapp.util

import kotlin.coroutines.cancellation.CancellationException

sealed class Resource<out T> {
    object Loading : Resource<Nothing>()
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Resource<Nothing>()

    val isLoading get() = this is Loading
    val isSuccess get() = this is Success
    val isError get() = this is Error
}

suspend fun <T> safeApiCall(call: suspend () -> T): Resource<T> {
    return try {
        Resource.Success(call())
    } catch (e: CancellationException) {
        // Never convert cancellation into an error — it must propagate so
        // structured concurrency can tear the coroutine down.
        throw e
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Unknown error", e)
    }
}
