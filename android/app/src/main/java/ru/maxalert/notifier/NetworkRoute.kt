package ru.maxalert.notifier

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Which network our own socket goes out through when a VPN is running.
 *
 * MAX refuses to send the SMS code to a foreign address, so a VPN breaks the login and can
 * break the watching connection with it. Android lets an app pin its own socket to a chosen
 * network instead of the system default, and the system default is the VPN. So when a VPN is
 * up we resolve and connect on the physical network -- the phone keeps its VPN for everything
 * else, and only this app's traffic to MAX goes around it.
 *
 * Two honest limits, both reported rather than hidden:
 * - a VPN in "always-on / block connections without VPN" mode (lockdown) makes Android drop
 *   traffic on any other network. The bypass then fails and we fall back to the default route,
 *   which is what would have happened anyway.
 * - the notification source never uses this: those messages come from the MAX app itself.
 *
 * The choosing is pure so it can be tested without a device; only [connect] touches Android.
 */
object NetworkRoute {

    enum class Transport { WIFI, CELLULAR, OTHER }

    data class Candidate(
        val id: String,
        val transport: Transport,
        val internet: Boolean,
        val validated: Boolean,
        val vpn: Boolean,
    )

    /**
     * The network to pin the socket to, or null to leave the choice to the system.
     *
     * Null when there is no VPN at all: pinning would then change nothing except making the
     * socket die on a Wi-Fi/mobile handover that the system would otherwise have carried.
     */
    fun choose(candidates: List<Candidate>, bypassVpn: Boolean): Candidate? {
        if (!bypassVpn) return null
        if (candidates.none { it.vpn }) return null
        val usable = candidates.filter { it.internet && !it.vpn }
        return usable.sortedWith(
            compareByDescending<Candidate> { it.validated }
                .thenBy { rank(it.transport) }
                .thenBy { it.id }
        ).firstOrNull()
    }

    private fun rank(transport: Transport): Int = when (transport) {
        Transport.WIFI -> 0
        Transport.CELLULAR -> 1
        Transport.OTHER -> 2
    }

    /** What the last connection actually did, for the screen to report instead of promise. */
    @Volatile
    var lastRoute: String? = null
        private set

    fun connect(context: Context, bypassVpn: Boolean, host: String, port: Int, timeoutMs: Int): Socket {
        val network = pick(context, bypassVpn)
        if (network != null) {
            val bypassed = runCatching {
                // Resolved on the same network: the VPN's DNS may answer differently, or not
                // at all, and a socket pinned to Wi-Fi must not chase a VPN-only address.
                val address = network.getAllByName(host).first()
                network.socketFactory.createSocket().apply {
                    connect(InetSocketAddress(address, port), timeoutMs)
                }
            }.getOrNull()
            if (bypassed != null) {
                lastRoute = "в обход VPN"
                return bypassed
            }
            lastRoute = "через VPN (обойти не удалось)"
        } else {
            lastRoute = if (vpnActive(context)) "через VPN" else "напрямую"
        }
        return Socket().apply { connect(InetSocketAddress(host, port), timeoutMs) }
    }

    fun vpnActive(context: Context): Boolean = candidates(context).any { it.vpn }

    private fun pick(context: Context, bypassVpn: Boolean): Network? {
        val manager = manager(context) ?: return null
        val chosen = choose(candidates(context), bypassVpn) ?: return null
        return networks(manager).firstOrNull { network -> network.toString() == chosen.id }
    }

    private fun candidates(context: Context): List<Candidate> {
        val manager = manager(context) ?: return emptyList()
        return networks(manager).mapNotNull { network ->
            val caps = manager.getNetworkCapabilities(network) ?: return@mapNotNull null
            Candidate(
                id = network.toString(),
                transport = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.WIFI
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.CELLULAR
                    else -> Transport.OTHER
                },
                internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
            )
        }
    }

    // The callback API would need an established watch to answer the same question; a
    // connection attempt needs the answer now, once, and this is the only call that gives it.
    @Suppress("DEPRECATION")
    private fun networks(manager: ConnectivityManager): List<Network> = manager.allNetworks.toList()

    private fun manager(context: Context): ConnectivityManager? =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
}
