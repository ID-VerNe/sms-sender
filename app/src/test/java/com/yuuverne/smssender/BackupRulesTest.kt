package com.yuuverne.smssender

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class BackupRulesTest {

    @Test
    fun `legacy backup rules exclude secure prefs legacy prefs and work database`() {
        val root = parse("backup_rules.xml").documentElement

        assertExcludes(root, "sharedpref", "${ConfigManager.SECURE_PREFS_NAME}.xml")
        assertExcludes(root, "sharedpref", "${ConfigManager.PREFS_NAME}.xml")
        assertExcludes(root, "database", WORK_MANAGER_DATABASE)
    }

    @Test
    fun `cloud backup and device transfer exclude secure prefs legacy prefs and work database`() {
        val document = parse("data_extraction_rules.xml")
        val cloudBackup = document.getElementsByTagName("cloud-backup").item(0) as Element
        val deviceTransfer = document.getElementsByTagName("device-transfer").item(0) as Element

        assertExcludes(cloudBackup, "sharedpref", "${ConfigManager.SECURE_PREFS_NAME}.xml")
        assertExcludes(cloudBackup, "sharedpref", "${ConfigManager.PREFS_NAME}.xml")
        assertExcludes(cloudBackup, "database", WORK_MANAGER_DATABASE)

        assertExcludes(deviceTransfer, "sharedpref", "${ConfigManager.SECURE_PREFS_NAME}.xml")
        assertExcludes(deviceTransfer, "sharedpref", "${ConfigManager.PREFS_NAME}.xml")
        assertExcludes(deviceTransfer, "database", WORK_MANAGER_DATABASE)
    }

    private fun assertExcludes(parent: Element, domain: String, path: String) {
        assertTrue(
            "Expected <exclude domain=\"$domain\" path=\"$path\" />",
            hasExclude(parent.getElementsByTagName("exclude"), domain, path)
        )
    }

    private fun hasExclude(excludes: org.w3c.dom.NodeList, domain: String, path: String): Boolean =
        (0 until excludes.length).any { index ->
            val element = excludes.item(index) as Element
            element.getAttribute("domain") == domain && element.getAttribute("path") == path
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

    private companion object {
        const val WORK_MANAGER_DATABASE = "androidx.work.workdb"
    }
}
