package com.nodeplayer.common.result

sealed interface NodeResult<out T> {
    data class Success<out T>(val data: T) : NodeResult<T>
    data class Failure(val error: Throwable, val message: String = error.message ?: "Unknown error") : NodeResult<Nothing>

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Failure -> throw error
    }

    fun <R> map(transform: (T) -> R): NodeResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    companion object {
        inline fun <T> runCatching(block: () -> T): NodeResult<T> {
            return try {
                Success(block())
            } catch (t: Throwable) {
                Failure(t)
            }
        }
    }
}
