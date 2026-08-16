package ru.maxalert.notifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun network(
    id: String,
    transport: NetworkRoute.Transport,
    internet: Boolean = true,
    validated: Boolean = true,
    vpn: Boolean = false,
) = NetworkRoute.Candidate(id, transport, internet, validated, vpn)

private val VPN = network("vpn", NetworkRoute.Transport.OTHER, vpn = true)
private val WIFI = network("wifi", NetworkRoute.Transport.WIFI)
private val MOBILE = network("mobile", NetworkRoute.Transport.CELLULAR)

class NetworkRouteNegativeCasesTest {

    @Test
    fun `nothing is pinned when the setting is off`() {
        assertNull(NetworkRoute.choose(listOf(VPN, WIFI), bypassVpn = false))
    }

    @Test
    fun `nothing is pinned when no VPN is running`() {
        // Pinning would only make the socket die on a Wi-Fi to mobile handover the system
        // would otherwise have carried for us.
        assertNull(NetworkRoute.choose(listOf(WIFI, MOBILE), bypassVpn = true))
    }

    @Test
    fun `a VPN with no physical network underneath leaves the choice to the system`() {
        assertNull(NetworkRoute.choose(listOf(VPN), bypassVpn = true))
    }

    @Test
    fun `a network without internet is never chosen`() {
        val useless = network("wifi", NetworkRoute.Transport.WIFI, internet = false)

        assertNull(NetworkRoute.choose(listOf(VPN, useless), bypassVpn = true))
    }

    @Test
    fun `the VPN itself is never chosen`() {
        val chosen = NetworkRoute.choose(listOf(VPN, MOBILE), bypassVpn = true)

        assertEquals(MOBILE, chosen)
    }
}

class NetworkRoutePositiveCasesTest {

    @Test
    fun `wi-fi wins over mobile when both are up`() {
        assertEquals(WIFI, NetworkRoute.choose(listOf(VPN, MOBILE, WIFI), bypassVpn = true))
    }

    @Test
    fun `a validated mobile network beats unvalidated wi-fi`() {
        val captive = network("wifi", NetworkRoute.Transport.WIFI, validated = false)

        assertEquals(MOBILE, NetworkRoute.choose(listOf(VPN, captive, MOBILE), bypassVpn = true))
    }

    @Test
    fun `an unvalidated network is still better than giving up`() {
        val unvalidated = network("mobile", NetworkRoute.Transport.CELLULAR, validated = false)

        assertEquals(unvalidated, NetworkRoute.choose(listOf(VPN, unvalidated), bypassVpn = true))
    }
}
