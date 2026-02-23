package dev.eknath.GymJournal.util

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null,
    val meta: ApiMeta? = null
) {
    companion object {
        fun <T> ok(data: T, meta: ApiMeta? = null) =
            ApiResponse(success = true, data = data, meta = meta)

        fun <T> error(code: String, message: String) =
            ApiResponse<T>(success = false, error = ApiError(code, message))
    }
}

data class ApiError(val code: String, val message: String)

data class ApiMeta(val page: Int? = null, val pageSize: Int? = null, val total: Long? = null)
