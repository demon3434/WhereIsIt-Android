package com.whereisit.findthings.data.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import java.net.Inet6Address
import java.net.URI
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

data class DiscoveredService(
    val name: String,
    val baseUrl: String,
    val host: String,
    val port: Int
)

class LanDiscovery(private val context: Context) {
    private val tag = "WhereIsItDiscovery"
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun discoverWhereIsIt(
        timeoutMs: Long = 10_000L,
        maxAttempts: Int = 3,
        rawServiceType: String = "_whereisit._tcp"
    ): List<DiscoveredService> = suspendCancellableCoroutine { cont ->
        val manager = nsdManager
        if (manager == null) {
            Log.w(tag, "NSD manager unavailable")
            cont.resume(emptyList())
            return@suspendCancellableCoroutine
        }

        val serviceType = normalizeServiceType(rawServiceType)
        val results = LinkedHashMap<String, DiscoveredService>()
        val deliveredKeys = linkedSetOf<String>()

        var currentListener: NsdManager.DiscoveryListener? = null
        var timeoutRunnable: Runnable? = null
        var multicastLock: WifiManager.MulticastLock? = null
        var attempts = 0
        var finished = false

        fun releaseMulticastLock() {
            multicastLock?.let {
                if (it.isHeld) {
                    runCatching { it.release() }
                }
            }
            multicastLock = null
        }

        fun clearTimeout() {
            timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            timeoutRunnable = null
        }

        fun stopCurrentDiscovery() {
            currentListener?.let { listener ->
                runCatching { manager.stopServiceDiscovery(listener) }
            }
            currentListener = null
        }

        fun finish() {
            if (finished) return
            finished = true
            clearTimeout()
            stopCurrentDiscovery()
            releaseMulticastLock()
            Log.i(tag, "Discovery finish, candidates=${results.size}, attempts=$attempts")
            if (cont.isActive) {
                cont.resume(results.values.toList())
            }
        }

        @SuppressLint("MissingPermission")
        fun acquireMulticastLock() {
            if (multicastLock?.isHeld == true) return
            multicastLock = wifiManager?.createMulticastLock("whereisit-mdns-lock")?.apply {
                setReferenceCounted(true)
                runCatching { acquire() }
            }
            Log.d(tag, "MulticastLock acquired=${multicastLock?.isHeld == true}")
        }

        fun addResolvedCandidate(
            serviceName: String,
            host: String,
            port: Int,
            scheme: String = "http"
        ) {
            if (host.isBlank() || port <= 0) return
            val baseUrl = normalizeBaseUrl("$scheme://${hostForUrl(host)}:$port") ?: return
            val key = "${host.lowercase(Locale.US)}:$port"
            if (!deliveredKeys.add(key)) return
            results[key] = DiscoveredService(
                name = serviceName.ifBlank { "WhereIsIt" },
                baseUrl = baseUrl,
                host = host,
                port = port
            )
            Log.i(tag, "Candidate found: name=$serviceName host=$host port=$port url=$baseUrl")
        }

        fun resolveService(serviceInfo: NsdServiceInfo) {
            @Suppress("DEPRECATION")
            manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(tag, "Resolve failed: name=${serviceInfo.serviceName} code=$errorCode")
                }

                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    if (finished) return

                    val attrs = resolved.attributes.mapValues { (_, value) ->
                        runCatching { String(value, Charsets.UTF_8) }.getOrDefault("")
                    }

                    val serviceName = resolved.serviceName.ifBlank { "WhereIsIt" }
                    val txtUrl = attrs["url"].orEmpty()
                    if (txtUrl.isNotBlank()) {
                        val normalized = normalizeBaseUrl(txtUrl)
                        if (normalized != null) {
                            val uri = URI(normalized)
                            val host = uri.host.orEmpty()
                            val port = if (uri.port == -1) 3000 else uri.port
                            addResolvedCandidate(serviceName, host, port, uri.scheme ?: "http")
                        }
                    }

                    val srvHost = resolved.host?.hostAddress.orEmpty()
                    val srvHostName = resolved.host?.hostName.orEmpty()
                    val srvHostIsIpv6LinkLocal = (resolved.host as? Inet6Address)?.isLinkLocalAddress == true
                    val srvPort = resolved.port
                    val txtHost = attrs["host"].orEmpty()
                    val txtPort = attrs["mappedPort"]?.toIntOrNull() ?: 0

                    if (txtHost.isNotBlank() && txtPort > 0) addResolvedCandidate(serviceName, txtHost, txtPort)
                    if (srvHost.isNotBlank() && srvPort > 0 && !srvHostIsIpv6LinkLocal) {
                        addResolvedCandidate(serviceName, srvHost, srvPort)
                    }
                    if (srvHostName.isNotBlank() && srvPort > 0) addResolvedCandidate(serviceName, srvHostName, srvPort)
                    if (srvHost.isNotBlank() && srvPort > 0 && srvHostIsIpv6LinkLocal) {
                        // Keep link-local IPv6 as last-resort candidate.
                        addResolvedCandidate(serviceName, srvHost, srvPort)
                    }
                }
            })
        }

        fun startAttempt() {
            if (finished) return
            attempts += 1
            acquireMulticastLock()

            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(type: String) {
                    Log.i(tag, "Discovery started: type=$type attempt=$attempts")
                    timeoutRunnable = Runnable {
                        if (finished) return@Runnable
                        Log.w(tag, "Discovery timeout: attempt=$attempts candidates=${results.size}")
                        stopCurrentDiscovery()
                        if (results.isNotEmpty()) {
                            finish()
                        } else if (attempts < maxAttempts) {
                            startAttempt()
                        } else {
                            finish()
                        }
                    }
                    mainHandler.postDelayed(timeoutRunnable!!, timeoutMs)
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (!serviceInfo.serviceType.contains("_whereisit._tcp", ignoreCase = true)) return
                    Log.i(
                        tag,
                        "Service found: name=${serviceInfo.serviceName} type=${serviceInfo.serviceType}"
                    )
                    resolveService(serviceInfo)
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

                override fun onDiscoveryStopped(type: String) = Unit

                override fun onStartDiscoveryFailed(type: String, errorCode: Int) {
                    Log.e(tag, "Start discovery failed: type=$type code=$errorCode attempt=$attempts")
                    clearTimeout()
                    stopCurrentDiscovery()
                    if (finished) return
                    if (attempts < maxAttempts) {
                        startAttempt()
                    } else {
                        finish()
                    }
                }

                override fun onStopDiscoveryFailed(type: String, errorCode: Int) {
                    Log.w(tag, "Stop discovery failed: type=$type code=$errorCode")
                    clearTimeout()
                    stopCurrentDiscovery()
                }
            }

            currentListener = listener
            runCatching {
                manager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            }.onFailure {
                Log.e(tag, "discoverServices throw: ${it.message}", it)
                clearTimeout()
                stopCurrentDiscovery()
                if (attempts < maxAttempts) {
                    startAttempt()
                } else {
                    finish()
                }
            }
        }

        startAttempt()
        cont.invokeOnCancellation {
            finish()
        }
    }

    private fun normalizeServiceType(input: String): String {
        var value = input.trim().lowercase(Locale.US)
        if (value.isBlank()) return "_whereisit._tcp"
        value = value.removeSuffix(".")
        if (value.endsWith(".local")) value = value.removeSuffix(".local")
        value = value.removeSuffix(".")
        return if (value.endsWith("._tcp") || value.endsWith("._udp")) value else "_whereisit._tcp"
    }

    private fun normalizeBaseUrl(raw: String): String? {
        val text = raw.trim()
        if (text.isBlank()) return null
        val withScheme = if (text.startsWith("http://") || text.startsWith("https://")) text else "http://$text"
        return runCatching {
            val uri = URI(withScheme)
            val host = uri.host ?: return null
            val port = if (uri.port == -1) 3000 else uri.port
            val scheme = uri.scheme ?: "http"
            "$scheme://${hostForUrl(host)}:$port/"
        }.getOrNull()
    }

    private fun hostForUrl(rawHost: String): String {
        val host = rawHost.trim().substringBefore('%').removePrefix("[").removeSuffix("]")
        if (host.contains(":")) return "[$host]"
        return host
    }
}
