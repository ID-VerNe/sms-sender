package com.yuuverne.smssender

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsPackageFilterTest {

    @Test
    fun googleMessagesPackageIsAccepted() {
        assertTrue(isSmsPackage("com.google.android.apps.messaging", defaultSmsPackage = null))
    }

    @Test
    fun samsungMessagesPackageIsAccepted() {
        assertTrue(isSmsPackage("com.samsung.android.messaging", defaultSmsPackage = null))
    }

    @Test
    fun xiaomiAndRedmiMessagesPackageIsAccepted() {
        assertTrue(isSmsPackage("com.android.mms", defaultSmsPackage = null))
    }

    @Test
    fun vivoMessagesPackageIsAccepted() {
        assertTrue(isSmsPackage("com.bbk.message", defaultSmsPackage = null))
    }

    @Test
    fun otherKnownOemPackagesAreAccepted() {
        assertTrue(isSmsPackage("com.android.messaging", defaultSmsPackage = null))
        assertTrue(isSmsPackage("com.coloros.mms", defaultSmsPackage = null))
        assertTrue(isSmsPackage("com.oneplus.mms", defaultSmsPackage = null))
    }

    @Test
    fun systemDefaultSmsPackageIsAcceptedEvenWhenUnknown() {
        assertTrue(isSmsPackage("com.vendor.sms", defaultSmsPackage = "com.vendor.sms"))
        assertFalse(isSmsPackage("com.vendor.sms", defaultSmsPackage = null))
    }

    @Test
    fun nonSmsPackagesAreRejected() {
        val nonSmsPackages = listOf(
            "com.whatsapp",
            "com.tencent.mm",
            "org.thoughtcrime.securesms",
            "com.google.android.talk",
            "com.example.messaging",
            "com.example.mms",
            "com.samsung.android.messaging.fake"
        )

        nonSmsPackages.forEach { packageName ->
            assertFalse(packageName, isSmsPackage(packageName, defaultSmsPackage = null))
        }
    }

    @Test
    fun blankPackageIsRejected() {
        assertFalse(isSmsPackage("", defaultSmsPackage = null))
        assertFalse(isSmsPackage("   ", defaultSmsPackage = "   "))
    }
}
