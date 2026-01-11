package com.trimsytrack.law

import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface BackendLawApi {
    @POST("lawGet")
    suspend fun lawGet(
        @Header("Authorization") authorization: String,
        @Body body: JsonObject,
    ): LawGetResponse

    @POST("lawQuizGet")
    suspend fun lawQuizGet(
        @Header("Authorization") authorization: String,
        @Body body: JsonObject,
    ): LawQuizGetResponse

    @POST("lawQuizSubmit")
    suspend fun lawQuizSubmit(
        @Header("Authorization") authorization: String,
        @Body body: LawQuizSubmitBody,
    ): LawQuizSubmitResponse

    @POST("lawAccept")
    suspend fun lawAccept(
        @Header("Authorization") authorization: String,
        @Body body: LawAcceptBody,
    ): LawAcceptResponse

    @POST("lawContractGet")
    suspend fun lawContractGet(
        @Header("Authorization") authorization: String,
        @Body body: LawContractGetBody,
    ): LawContractGetResponse
}
