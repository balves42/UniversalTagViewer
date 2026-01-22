package dev.wander.android.opentagviewer.service.web

import android.util.Base64
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.net.cronet.okhttptransport.CronetCallFactory
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.chromium.net.CronetEngine
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import java.nio.charset.StandardCharsets

class FMDServerTesterService(private val engine: CronetEngine) {

    fun getIndex(
        fmdServerRootUrl: String,
        email: String?,
        password: String?
    ): Observable<FMDServerHealthData> {

        val retrofit = Retrofit.Builder()
            .baseUrl(ensureEndsWithSlash(fmdServerRootUrl))
            .addConverterFactory(JacksonConverterFactory.create())
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
            .callFactory(CronetCallFactory.newBuilder(engine).build())
            .build()

        val service = retrofit.create(FMDServer::class.java)
        val auth = buildBasicAuthHeader(email, password)

        return service.getHealth(auth)
            .subscribeOn(Schedulers.io())
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

    interface FMDServer {
        @GET("health")
        fun getHealth(@Header("Authorization") authorization: String?): Observable<FMDServerHealthData>
    }
}
