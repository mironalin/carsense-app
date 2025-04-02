package com.carsense.core.model

/**
 * A generic wrapper class that holds data with its loading status.
 * @param T The type of data being wrapped
 */
sealed class Resource<out T> {
    /** Data is being loaded */
    object Loading : Resource<Nothing>()

    /** Data has been loaded successfully */
    data class Success<T>(val data: T) : Resource<T>()

    /** An error occurred while loading data */
    data class Error(val message: String, val throwable: Throwable? = null) : Resource<Nothing>()

    companion object {
        /** Create a Loading resource */
        fun <T> loading(): Resource<T> = Loading

        /** Create a Success resource */
        fun <T> success(data: T): Resource<T> = Success(data)

        /** Create an Error resource */
        fun <T> error(message: String, throwable: Throwable? = null): Resource<T> =
                Error(message, throwable)
    }

    /** Transform the wrapped data */
    fun <R> map(transform: (T) -> R): Resource<R> {
        return when (this) {
            is Loading -> Loading
            is Success -> Success(transform(data))
            is Error -> Error(message, throwable)
        }
    }

    /** Get the data or null if not in Success state */
    fun getOrNull(): T? =
            when (this) {
                is Success -> data
                else -> null
            }

    /** Execute code block if resource is in Success state */
    inline fun onSuccess(action: (T) -> Unit): Resource<T> {
        if (this is Success) action(data)
        return this
    }

    /** Execute code block if resource is in Error state */
    inline fun onError(action: (message: String, throwable: Throwable?) -> Unit): Resource<T> {
        if (this is Error) action(message, throwable)
        return this
    }

    /** Execute code block if resource is in Loading state */
    inline fun onLoading(action: () -> Unit): Resource<T> {
        if (this is Loading) action()
        return this
    }
}
