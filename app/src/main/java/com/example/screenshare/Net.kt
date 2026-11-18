package com.example.screenshare

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

fun getLocalIpAddress(): String? {
    val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces() ?: return null)
    for (ni in interfaces) {
        if (!ni.isUp || ni.isLoopback) continue
        for (addr in Collections.list(ni.inetAddresses)) {
            if (!addr.isLoopbackAddress && addr is Inet4Address) {
                return addr.hostAddress
            }
        }
    }
    return null
}