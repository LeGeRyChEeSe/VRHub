package com.vrhub.ui

import com.vrhub.data.InstallStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for status mapping - Story 1.13
 */
class StatusMappingTest {

    @Test
    fun testDataToTaskStatusMapping() {
        assertEquals(InstallTaskStatus.QUEUED, InstallStatus.QUEUED.toTaskStatus())
        assertEquals(InstallTaskStatus.DOWNLOADING, InstallStatus.DOWNLOADING.toTaskStatus())
        assertEquals(InstallTaskStatus.EXTRACTING, InstallStatus.EXTRACTING.toTaskStatus())
        assertEquals(InstallTaskStatus.INSTALLING, InstallStatus.INSTALLING.toTaskStatus())
        assertEquals(InstallTaskStatus.PENDING_INSTALL, InstallStatus.PENDING_INSTALL.toTaskStatus())
        assertEquals(InstallTaskStatus.PAUSED, InstallStatus.PAUSED.toTaskStatus())
        assertEquals(InstallTaskStatus.COMPLETED, InstallStatus.COMPLETED.toTaskStatus())
        assertEquals(InstallTaskStatus.FAILED, InstallStatus.FAILED.toTaskStatus())
        assertEquals(InstallTaskStatus.LOCAL_VERIFYING, InstallStatus.LOCAL_VERIFYING.toTaskStatus())
        assertEquals(InstallTaskStatus.SHELVED, InstallStatus.SHELVED.toTaskStatus())
    }

    @Test
    fun testTaskToDataStatusMapping() {
        assertEquals(InstallStatus.QUEUED, InstallTaskStatus.QUEUED.toDataStatus())
        assertEquals(InstallStatus.DOWNLOADING, InstallTaskStatus.DOWNLOADING.toDataStatus())
        assertEquals(InstallStatus.EXTRACTING, InstallTaskStatus.EXTRACTING.toDataStatus())
        assertEquals(InstallStatus.INSTALLING, InstallTaskStatus.INSTALLING.toDataStatus())
        assertEquals(InstallStatus.PENDING_INSTALL, InstallTaskStatus.PENDING_INSTALL.toDataStatus())
        assertEquals(InstallStatus.PAUSED, InstallTaskStatus.PAUSED.toDataStatus())
        assertEquals(InstallStatus.COMPLETED, InstallTaskStatus.COMPLETED.toDataStatus())
        assertEquals(InstallStatus.FAILED, InstallTaskStatus.FAILED.toDataStatus())
        assertEquals(InstallStatus.LOCAL_VERIFYING, InstallTaskStatus.LOCAL_VERIFYING.toDataStatus())
        assertEquals(InstallStatus.SHELVED, InstallTaskStatus.SHELVED.toDataStatus())
    }
}