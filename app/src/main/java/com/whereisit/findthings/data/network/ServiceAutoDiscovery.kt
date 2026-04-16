package com.whereisit.findthings.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.whereisit.findthings.data.repository.SessionRepository
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class ServiceAutoDiscovery(
    private val context: Context,
    private val sessionRepository: SessionRepository,
    private val lanDiscovery: LanDiscovery
) {
    private val tag = "WhereIsItDiscovery"
    private val healthClient = OkHttpClient.Builder()
        .connectTimeout(1200, TimeUnit.MILLISECONDS)
        .readTimeout(2000, TimeUnit.MILLISECONDS)
        .writeTimeout(2000, TimeUnit.MILLISECONDS)
        .callTimeout(3000, TimeUnit.MILLISECONDS)
        .build()

    suspend fun discover(): List<DiscoveredService> = coroutineScope {
        Log.i(tag, "Auto discovery begin")
        val found = LinkedHashMap<String, DiscoveredService>()
        val seen = linkedSetOf<String>()

        suspend fun tryAccept(baseUrl: String, name: String): Boolean {
            val normalized = normalizeBaseUrl(baseUrl) ?: return false
            if (!seen.add(normalized)) return false
            if (!checkHealth(normalized)) {
                Log.w(tag, "Health FAIL $normalized")
                return false
            }
            val uri = URI(normalized)
            val host = uri.host.orEmpty()
            val port = if (uri.port == -1) 3000 else uri.port
            found[normalized] = DiscoveredService(name = name, baseUrl = normalized, host = host, port = port)
            sessionRepository.saveLastSuccessBaseUrl(normalized)
            Log.i(tag, "Health OK $normalized source=$name")
            return true
        }

        val nsdCandidates = lanDiscovery.discoverWhereIsIt(
            timeoutMs = 10_000L,
            maxAttempts = 3,
            rawServiceType = "_whereisit._tcp"
        )
        for (service in nsdCandidates) {
            tryAccept(service.baseUrl, service.name.ifBlank { "NSD" })
        }
        if (found.isNotEmpty()) {
            Log.i(tag, "NSD success count=${found.size}")
            return@coroutineScope found.values.toList()
        }

        Log.w(tag, "NSD failed, fallback to UDP broadcast + subnet scan")

        val udpUrls = discoverByUdp(timeoutMs = 1800L)
        for (url in udpUrls) {
            tryAccept(url, "UDP")
        }
        if (found.isNotEmpty()) {
            Log.i(tag, "UDP fallback success count=${found.size}")
            return@coroutineScope found.values.toList()
        }

        val scanPorts = linkedSetOf(3000, 80, 8080, 5000)
        nsdCandidates.mapNotNull { runCatching { URI(it.baseUrl).port }.getOrNull() }
            .filter { it > 0 }
            .forEach { scanPorts += it }

        val hosts = getSubnetHosts(maxHosts = 512)
        if (hosts.isEmpty()) {
            Log.w(tag, "Subnet scan skipped: empty host list")
            return@coroutineScope emptyList()
        }
        Log.i(tag, "Subnet scan start: hosts=${hosts.size}, ports=${scanPorts.size}")

        val stop = AtomicBoolean(false)
        val semaphore = Semaphore(48)
        val jobs = mutableListOf<kotlinx.coroutines.Deferred<Unit>>()

        for (host in hosts) {
            for (port in scanPorts) {
                if (stop.get()) break
                val baseUrl = "http://$host:$port/"
                jobs += async(Dispatchers.IO) {
                    semaphore.withPermit {
                        if (!isActive || stop.get()) return@withPermit
                        val ok = checkHealth(baseUrl)
                        if (ok) {
                            synchronized(found) {
                                if (found.isEmpty()) {
                                    found[baseUrl] = DiscoveredService(
                                        name = "SubnetScan",
                                        baseUrl = baseUrl,
                                        host = host,
                                        port = port
                                    )
                                    stop.set(true)
                                    Log.i(tag, "Subnet scan hit $baseUrl")
                                }
                            }
                        }
                    }
                }
            }
            if (stop.get()) break
        }

        jobs.awaitAll()
        Log.i(tag, "Subnet scan done: found=${found.size}")
        found.values.toList()
    }

    private suspend fun discoverByUdp(timeoutMs: Long): List<String> = withContext(Dispatchers.IO) {
        val results = linkedSetOf<String>()
        val nonce = UUID.randomUUID().toString()
        val payload = JSONObject()
            .put("type", "whereisit-discover")
            .put("nonce", nonce)
            .toString()
            .toByteArray(Charsets.UTF_8)

        val socket = DatagramSocket(null)
        try {
            socket.reuseAddress = true
            socket.broadcast = true
            socket.soTimeout = 250
            socket.bind(InetSocketAddress(0))

            val endpoints = mutableListOf<InetAddress>()
            endpoints += InetAddress.getByName("255.255.255.255")
            getBroadcastAddress()?.let { endpoints += it }

            endpoints.distinctBy { it.hostAddress }.forEach { address ->
                runCatching {
                    val packet = DatagramPacket(payload, payload.size, address, 37020)
                    socket.send(packet)
                }
            }

            val deadline = System.currentTimeMillis() + timeoutMs
            val buffer = ByteArray(2048)
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                    val body = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val json = runCatching { JSONObject(body) }.getOrNull() ?: continue
                    if (!json.optString("type").equals("whereisit-offer", ignoreCase = true)) continue
                    if (json.optString("nonce") != nonce) continue
                    val url = json.optString("url")
                    if (url.isNotBlank()) {
                        results += url
                    } else {
                        val host = json.optString("host")
                        val port = json.optInt("port", 0)
                        if (host.isNotBlank() && port > 0) {
                            results += "http://$host:$port/"
                        }
                    }
                } catch (_: Exception) {
                    // keep receiving until timeout
                }
            }
        } finally {
            runCatching { socket.close() }
        }

        Log.i(tag, "UDP fallback candidates=${results.size}")
        results.toList()
    }

    private suspend fun checkHealth(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        repeat(2) { attempt ->
            val ok = runCatching {
                val normalized = baseUrl.trimEnd('/')
                val req = Request.Builder()
                    .url("$normalized/api/health")
                    .get()
                    .build()
                healthClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use false
                    val body = resp.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val status = when {
                        json.has("data") && json.opt("data") is JSONObject ->
                            (json.optJSONObject("data") ?: JSONObject()).optString("status")
                        else -> json.optString("status")
                    }
                    status.equals("ok", ignoreCase = true)
                }
            }.getOrDefault(false)
            if (ok) return@withContext true
            if (attempt == 0) Thread.sleep(200)
        }
        false
    }

    private fun getSubnetHosts(maxHosts: Int): List<String> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return emptyList()
        val network = cm.activeNetwork ?: return emptyList()
        val caps = cm.getNetworkCapabilities(network) ?: return emptyList()
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        ) {
            return emptyList()
        }
        val linkProps = cm.getLinkProperties(network) ?: return emptyList()
        val ipv4 = linkProps.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return emptyList()

        val ipInt = inet4ToInt(ipv4.address as Inet4Address)
        val mask = prefixToMask(ipv4.prefixLength)
        val networkInt = ipInt and mask
        val broadcastInt = networkInt or mask.inv()
        if (broadcastInt - networkInt <= 2) return emptyList()

        val selfIp = intToInet4(ipInt)
        val hosts = mutableListOf<String>()
        var current = networkInt + 1
        val end = broadcastInt - 1
        while (current <= end && hosts.size < maxHosts) {
            val host = intToInet4(current)
            if (host != selfIp) hosts += host
            current++
        }
        return hosts
    }

    private fun getBroadcastAddress(): InetAddress? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val network = cm.activeNetwork ?: return null
        val linkProps = cm.getLinkProperties(network) ?: return null
        val ipv4 = linkProps.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
        val ipInt = inet4ToInt(ipv4.address as Inet4Address)
        val mask = prefixToMask(ipv4.prefixLength)
        val broadcastInt = (ipInt and mask) or mask.inv()
        return runCatching { InetAddress.getByName(intToInet4(broadcastInt)) }.getOrNull()
    }

    private fun normalizeBaseUrl(raw: String): String? {
        val text = raw.trim()
        if (text.isBlank()) return null
        val withScheme = if (text.startsWith("http://") || text.startsWith("https://")) text else "http://$text"
        return runCatching {
            val uri = URI(withScheme)
            val host = uri.host ?: return null
            val scheme = uri.scheme ?: "http"
            val port = if (uri.port == -1) 3000 else uri.port
            "$scheme://$host:$port/"
        }.getOrNull()
    }

    private fun prefixToMask(prefixLength: Int): Int {
        if (prefixLength <= 0) return 0
        if (prefixLength >= 32) return -1
        return -1 shl (32 - prefixLength)
    }

    private fun inet4ToInt(address: Inet4Address): Int {
        val b = address.address
        return ((b[0].toInt() and 0xFF) shl 24) or
            ((b[1].toInt() and 0xFF) shl 16) or
            ((b[2].toInt() and 0xFF) shl 8) or
            (b[3].toInt() and 0xFF)
    }

    private fun intToInet4(value: Int): String {
        return "${(value ushr 24) and 0xFF}.${(value ushr 16) and 0xFF}.${(value ushr 8) and 0xFF}.${value and 0xFF}"
    }
}
