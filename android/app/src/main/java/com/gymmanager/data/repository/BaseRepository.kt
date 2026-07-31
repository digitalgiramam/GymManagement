package com.gymmanager.data.repository

import com.google.gson.Gson
import com.gymmanager.data.model.ApiError
import com.gymmanager.utils.NetworkResult
import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Shared safe-call wrapper for all repository classes.
 * Converts Retrofit [Response] into [NetworkResult], parsing error bodies
 * from the API's standard  { "error": "..." }  JSON shape.
 */
abstract class BaseRepository {

    private val gson = Gson()

    protected suspend fun <T> safeApiCall(call: suspend () -> Response<T>): NetworkResult<T> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                val body = response.body()
                // 204 No Content — body is null but still a success
                @Suppress("UNCHECKED_CAST")
                if (body != null) {
                    NetworkResult.Success(body)
                } else {
                    NetworkResult.Success(Unit as T)
                }
            } else {
                val errorMessage = parseErrorBody(response) ?: "Unknown error (${response.code()})"
                NetworkResult.Error(errorMessage, response.code())
            }
        } catch (e: SocketTimeoutException) {
            NetworkResult.Error("Server is warming up — please try again in a few seconds.")
        } catch (e: ConnectException) {
            NetworkResult.Error("Could not reach server. Please check your connection and retry.")
        } catch (e: UnknownHostException) {
            NetworkResult.Error("No internet connection. Please check your network.")
        } catch (e: Exception) {
            NetworkResult.Error(e.localizedMessage ?: "Network error. Check your connection.")
        }
    }

    private fun <T> parseErrorBody(response: Response<T>): String? {
        return try {
            val errorJson = response.errorBody()?.string()
            if (errorJson.isNullOrBlank()) return null
            gson.fromJson(errorJson, ApiError::class.java).error
        } catch (_: Exception) {
            null
        }
    }
}
