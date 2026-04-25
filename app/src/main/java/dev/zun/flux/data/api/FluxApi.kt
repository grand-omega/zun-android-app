package dev.zun.flux.data.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * The v2 wire contract (all paths under `/api/v1/`).
 */
interface FluxApi {
    @GET("api/v1/health")
    suspend fun health(): HealthResponse

    // --- Prompts (per-user CRUD) ---

    @GET("api/v1/prompts")
    suspend fun listPrompts(): PromptListResponse

    @GET("api/v1/prompts/{id}")
    suspend fun getPrompt(
        @Path("id") id: Long,
    ): PromptDto

    @POST("api/v1/prompts")
    suspend fun createPrompt(
        @Body body: PromptDto,
    ): PromptDto

    @PATCH("api/v1/prompts/{id}")
    suspend fun updatePrompt(
        @Path("id") id: Long,
        @Body body: PromptDto,
    ): PromptDto

    @DELETE("api/v1/prompts/{id}")
    suspend fun deletePrompt(
        @Path("id") id: Long,
    )

    // --- Jobs ---

    /** JSON probe — server answers 200/202 if it already has the input, else 409 need_upload. */
    @POST("api/v1/jobs")
    suspend fun submitJobJson(
        @Body body: JobSubmitRequest,
    ): Response<JobCreatedResponse>

    /** Multipart fallback used after a 409 need_upload response. */
    @Multipart
    @POST("api/v1/jobs")
    suspend fun submitJobMultipart(
        @Part image: MultipartBody.Part,
        @Part("input_sha256") sha256: RequestBody,
        @Part("input_name") inputName: RequestBody? = null,
        @Part("prompt_id") promptId: RequestBody? = null,
        @Part("prompt_text") promptText: RequestBody? = null,
        @Part("workflow") workflow: RequestBody? = null,
    ): JobCreatedResponse

    @GET("api/v1/jobs/{id}")
    suspend fun getJob(
        @Path("id") id: String,
    ): JobStatusDto

    @GET("api/v1/jobs")
    suspend fun listJobs(
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("cursor") cursor: String? = null,
        @Query("input_id") inputId: Int? = null,
    ): JobListResponse

    @DELETE("api/v1/jobs/{id}")
    suspend fun deleteJob(
        @Path("id") id: String,
    )

    @POST("api/v1/jobs/{id}/restore")
    suspend fun restoreJob(
        @Path("id") id: String,
    )

    @POST("api/v1/jobs/{id}/cancel")
    suspend fun cancelJob(
        @Path("id") id: String,
    )

    // --- Inputs (addressable) ---

    @GET("api/v1/inputs/{id}")
    suspend fun getInput(
        @Path("id") id: Int,
    ): InputMetadata

    @Streaming
    @GET("api/v1/inputs/{id}/file")
    suspend fun downloadInputFile(
        @Path("id") id: Int,
    ): ResponseBody
}
