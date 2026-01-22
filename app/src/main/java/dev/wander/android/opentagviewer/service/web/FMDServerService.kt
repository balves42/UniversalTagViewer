package dev.wander.android.opentagviewer.service.web

import android.util.Base64
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.net.cronet.okhttptransport.CronetCallFactory
import dev.wander.android.opentagviewer.db.repo.model.UserSettings
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.chromium.net.CronetEngine
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.nio.charset.StandardCharsets

class FMDServerService(
    private val engine: CronetEngine,
    private val userSettings: UserSettings? = null
) {
    fun getHealth(
        fmdServerRootUrl: String? = userSettings?.fmdServerUrl,
        email: String? = userSettings?.fmdEmail,
        password: String? = userSettings?.fmdPassword
    ): Observable<FMDServerHealthData>? {
        val auth = buildBasicAuthHeader(email, password)
        return builder(fmdServerRootUrl)?.getHealth(auth)?.subscribeOn(Schedulers.io())
    }

    fun getGoogleDevices(): Observable<List<GoogleDeviceResponse>>? {
        val auth = buildBasicAuthHeader(userSettings?.fmdEmail, userSettings?.fmdPassword)
        return builder(userSettings?.fmdServerUrl)?.getGoogleDevices(auth)?.subscribeOn(Schedulers.io())
    }

    fun locateGoogleDevice(canonicId: String): Observable<GoogleLocateResponse>? {
        val auth = buildBasicAuthHeader(userSettings?.fmdEmail, userSettings?.fmdPassword)
        val googleLocateRequest = GoogleLocateRequest(canonicId);
        return builder(userSettings?.fmdServerUrl)?.locateGoogleDevice(auth, googleLocateRequest)?.subscribeOn(Schedulers.io())
    }

    private fun builder(
        fmdServerRootUrl: String?
    ): FMDServer? {
        if (fmdServerRootUrl != null){
            val retrofit = Retrofit.Builder()
                .baseUrl(ensureEndsWithSlash(fmdServerRootUrl))
                .addConverterFactory(JacksonConverterFactory.create())
                .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
                .callFactory(CronetCallFactory.newBuilder(engine).build())
                .build()
            val service = retrofit.create(FMDServer::class.java)
            return service
        } else {
            return null
        }
    }

    private fun ensureEndsWithSlash(url: String): String =
        if (url.endsWith("/")) url else "$url/"

    private fun buildBasicAuthHeader(email: String?, password: String?): String? {
        val e = email?.trim().orEmpty()
        val p = password?.trim().orEmpty()
        if (e.isEmpty() || p.isEmpty()) return null

        val raw = "$e:$p"
        val encoded = Base64.encodeToString(
            raw.toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP
        )
        return "Basic $encoded"
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class FMDServerHealthData(
        @field:JsonProperty("status")
        val status: String? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class GoogleDeviceResponse(
        @field:JsonProperty("name")
        val name: String? = null,

        @field:JsonProperty("canonic_id")
        val canonic_id: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class GoogleLocateRequest(
        @field:JsonProperty("canonic_id")
        val canonic_id: String
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class GoogleLocateResponse(

        @field:JsonProperty("ok")
        val ok: Boolean? = null,

        @field:JsonProperty("latitude")
        val latitude: Double? = null,

        @field:JsonProperty("longitude")
        val longitude: Double? = null,

        @field:JsonProperty("google_maps")
        val google_maps: String? = null,

        @field:JsonProperty("time")
        val time: Long? = null,
    )

    interface FMDServer {
        @GET("health")
        fun getHealth(@Header("Authorization") authorization: String?): Observable<FMDServerHealthData>

        @GET("google/devices")
        fun getGoogleDevices(@Header("Authorization") authorization: String?): Observable<List<GoogleDeviceResponse>>

        @POST("google/locate")
        fun locateGoogleDevice(@Header("Authorization") authorization: String?, @Body body: GoogleLocateRequest): Observable<GoogleLocateResponse>
    }

}
