package dev.wander.android.opentagviewer.service.web

import android.util.Base64
import android.util.Log
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.net.cronet.okhttptransport.CronetCallFactory
import dev.wander.android.opentagviewer.data.model.BeaconLocationReport
import dev.wander.android.opentagviewer.db.repo.model.UserSettings
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import okhttp3.OkHttpClient
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

    private val locateCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<BeaconLocationReport>>>()
    private val cacheMS = 60_000L

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

    fun getGoogleDevicesAsBeaconLocationReport(): Observable<Map<String, List<BeaconLocationReport>>>? {
        return getGoogleDevices()
            ?.subscribeOn(Schedulers.io())
            ?.doOnSubscribe { Log.d("FMD", "SUBSCRIBED getGoogleDevicesAsBeaconLocationReport") }
            ?.doOnNext { list -> Log.d("FMD", "Devices recebidos: ${list.size}") }
            ?.flatMapIterable { it }
            ?.doOnNext { d -> Log.d("FMD", "Vai localizar canonic_id=${d.canonicId}") }
            ?.flatMap({ device ->
                val id = device.canonicId?.trim().orEmpty()
                if (id.isBlank()) return@flatMap Observable.empty()

                locateWithCache(id, device.name)
                    .doOnSubscribe { Log.d("FMD", "PIPE locate($id) subscribed") }
                    .doOnNext { pair ->
                        // Mantém o mesmo “RESP locate(...) ok lat lon”
                        val reports = pair.second
                        val last = reports.lastOrNull()
                        Log.d("FMD", "RESP locate($id) ok=${last != null} " + "lat=${last?.latitude} lon=${last?.longitude} reports=${reports.size}")
                    }
                    .onErrorResumeNext { e: Throwable ->
                        Log.e("FMD", "ERRO locate($id)", e)
                        Observable.empty() // não devolve lista vazia
                    }
            }, 3) // maxConcurrency
            ?.toList()
            ?.map { pairs ->
                val out = LinkedHashMap<String, MutableList<BeaconLocationReport>>()
                for ((key, reports) in pairs) {
                    if (key.isBlank()) continue
                    out.getOrPut(key) { mutableListOf() }.addAll(reports)
                }
                val finalMap = out.mapValues { it.value.toList() }
                Log.d("FMD", "Reports agregados: ${finalMap.size} devices com reports")
                finalMap
            }
            ?.toObservable()
    }

    private fun locateWithCache(id: String, deviceName: String?): Observable<Pair<String, List<BeaconLocationReport>>> {
        val now = System.currentTimeMillis()
        val cached = locateCache[id]

        if (cached != null && now - cached.first <= cacheMS) {
            Log.d("FMD", "CACHE HIT locate($id) age=${now - cached.first}ms")
            return Observable.just(Pair(id, cached.second))
        }

        Log.d("FMD", "CACHE MISS locate($id) → CALL locateGoogleDevice($id)")

        return withTimeoutAndRetry(locateGoogleDevice(id)!!
            .doOnSubscribe { Log.d("FMD", "CALL locateGoogleDevice($id)") }, id)
            .doOnNext { resp ->
                // Mantém log no formato antigo
                Log.d("FMD", "RESP locate($id) ok=${resp.ok} lat=${resp.latitude} lon=${resp.longitude}")
            }
            .map { locate -> buildReportsFromLocate(locate, deviceName) }
            .doOnNext { reports ->
                locateCache[id] = Pair(now, reports)
                Log.d("FMD", "CACHE STORE locate($id) reports=${reports.size}")
            }
            .map { reports -> Pair(id, reports) }
            .onErrorReturn { e ->
                Log.e("FMD", "ERRO locate($id)", e)

                // fallback para cache “stale” se existir
                val fb = locateCache[id]
                if (fb != null) {
                    Log.w("FMD", "CACHE FALLBACK locate($id) age=${now - fb.first}ms")
                    Pair(id, fb.second)
                } else {
                    Pair(id, emptyList())
                }
            }
    }

    private fun <T : Any> withTimeoutAndRetry(src: Observable<T>, id: String): Observable<T> {
        return src
            .timeout(25, java.util.concurrent.TimeUnit.SECONDS)
            .retryWhen { errors ->
                errors
                    .zipWith(Observable.range(1, 3)) { err, attempt -> Pair(err, attempt) }
                    .flatMap { (err, attempt) ->
                        val isTimeout = err is java.net.SocketTimeoutException
                        if (!isTimeout) Observable.error(err)
                        else {
                            val delaySec = (1 shl (attempt - 1)).toLong() // 1,2,4
                            Log.w("FMD", "Retry locate($id) attempt=$attempt in ${delaySec}s due to timeout")
                            Observable.timer(delaySec, java.util.concurrent.TimeUnit.SECONDS).map { attempt }
                        }
                    }
            }
    }

    //TODO: OLD way of making requests
    /*
    fun getGoogleDevicesAsBeaconLocationReport(): Observable<Map<String, List<BeaconLocationReport>>> {
        return getGoogleDevices()!!
            .subscribeOn(Schedulers.io())
            .doOnSubscribe { android.util.Log.d("FMD", "SUBSCRIBED getGoogleDevicesAsBeaconLocationReport") }
            .doOnNext { list -> android.util.Log.d("FMD", "Devices recebidos: ${list.size}") }
            .flatMapIterable { devices -> devices }
            .doOnNext { d -> android.util.Log.d("FMD", "Vai localizar canonic_id=${d.canonicId}") }

            // ConcatMap makes sure that 1 locate is made each time (slow but easy to debug)
            .concatMap { device ->
                val id = device.canonicId?.trim().orEmpty()
                //TODO: Slow requests due to this
                if (id.isEmpty()) {
                    Observable.just(Pair(id, emptyList()))
                } else {
                    locateGoogleDevice(id)!!
                        .doOnSubscribe { android.util.Log.d("FMD", "CALL locateGoogleDevice($id)") }
                        .doOnNext { resp -> android.util.Log.d("FMD", "RESP locate($id) ok=${resp.ok} lat=${resp.latitude} lon=${resp.longitude}") }
                        .map { locate -> Pair(id, buildReportsFromLocate(locate, device.name)) }
                        .onErrorReturn { e ->
                            android.util.Log.e("FMD", "ERRO locate($id)", e)
                            Pair(id, emptyList())
                        }
                }
            }
            .toList()
            .map { pairs ->
                val out = LinkedHashMap<String, MutableList<BeaconLocationReport>>()
                for (pair in pairs) {
                    val key = pair.first
                    if (key.isBlank()) continue
                    out.getOrPut(key) { mutableListOf() }.addAll(pair.second)
                }
                val finalMap = LinkedHashMap<String, List<BeaconLocationReport>>()
                for ((k, v) in out) finalMap[k] = v.toList()
                finalMap as Map<String, List<BeaconLocationReport>>
            }
            .toObservable()
    }*/


    private fun buildReportsFromLocate(locate: GoogleLocateResponse?, name: String?): List<BeaconLocationReport> {
        val lat: Double? = locate?.latitude
        val lon: Double? = locate?.longitude
        if (lat == null || lon == null) return emptyList()

        val ok: Boolean = (locate.ok == true)
        val timeMs: Long = parseTimeToEpochMs(locate.time)

        val report = BeaconLocationReport(
            timeMs,
            name ?: "Google FMD",
            timeMs,
            if (ok) 3L else 0L,
            lat,
            lon,
            50L,
            if (ok) 1L else 0L
        )

        return listOf(report)
    }

    private fun parseTimeToEpochMs(time: String?): Long {
        val t = time?.trim().orEmpty()
        if (t.isEmpty()) return 0L

        val asLong = t.toLongOrNull()
        if (asLong != null) {
            return if (asLong in 1L..9_999_999_999L) asLong * 1000L else asLong
        }

        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC") // ou TimeZone.getDefault()
            val date = sdf.parse(t)
            date?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
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
                .client(OkHttpClient())
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
        val canonicId: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class GoogleLocateRequest(
        @field:JsonProperty("canonic_id")
        val canonicId: String
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
        val googleMaps: String? = null,

        @field:JsonProperty("time")
        val time: String? = null,
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
