package com.yuuverne.smssender

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class BackupRulesTest {

    @Test
    fun `legacy backup rules exclude encrypted secrets`() {
        val document = parse("backup_rules.xml")
        val excludes = document.getElementsByTagName("exclude")

        assertTrue(
            (0 until excludes.length).any { index ->
                val element = excludes.item(index) as Element
                element.getAttribute("domain") == "sharedpref" &&
                    element.getAttribute("path") == ConfigManager.SECURE_PREFS_NAME + ".xml"
            }
        )
    }

    @Test
    fun `cloud backup and device transfer exclude encrypted secrets`() {
        val document = parse("data_extraction_rules.xml")
        val cloudBackup = document.getElementsByTagName("cloud-backup").item(0) as Element
        val deviceTransfer = document.getElementsByTagName("device-transfer").item(0) as Element

        assertTrue(hasSecurePrefsExclude(cloudBackup))
        assertTrue(hasSecurePrefsExclude(deviceTransfer))
    }

    private fun hasSecurePrefsExclude(parent: Element): Boolean {
        val excludes = parent.getElementsByTagName("exclude")
        val expectedPath = ConfigManager.SECURE_PREFS_NAME + ".xml"
        return (0 until excludes.length).any { index ->
            val element = excludes.item(index) as Element
            element.getAttribute("domain") == "sharedpref" && element.getAttribute("path") == expectedPath
        }
    }

    private fun parse(fileName: String) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(findResource(fileName))

    private fun findResource(fileName: String): File {
        val candidates = listOf(
            File("src/main/res/xml/$fileName"),
            File("app/src/main/res/xml/$fileName")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Cannot find backup resource $fileName from ${File("").absolutePath}")
    }
}
