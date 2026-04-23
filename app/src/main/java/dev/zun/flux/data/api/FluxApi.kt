package dev.zun.flux.data.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The wire contract the Rust server is expected to implement. Kept in tree from
 * Milestone 1 onward so the contract is visible; real calls start once
 * RealJobRepository replaces the fake.
 */
interface FluxApi {
    @GET("api/health")
    suspend fun health(): HealthResponse

    @GET("api/prompts")
    suspend fun listPrompts(): List<PromptDto>

    @Multipart
    @POST("api/jobs")
    suspend fun submitJob(
        @Part image: MultipartBody.Part,
        @Part("prompt_id") promptId: RequestBody,
    ): JobCreatedResponse

    @GET("api/jobs/{id}")
    suspend fun getJob(
        @Path("id") id: String,
    ): JobStatusDto

    @GET("api/jobs")
    suspend fun listJobs(
        @Query("status") status: String = "done",
        @Query("limit") limit: Int = 30,
        @Query("before") before: Long? = null,
    ): List<JobSummaryDto>

    @DELETE("api/jobs/{id}")
    suspend fun deleteJob(
        @Path("id") id: String,
    )
}
