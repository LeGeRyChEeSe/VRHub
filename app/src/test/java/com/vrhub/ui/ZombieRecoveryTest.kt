package com.vrhub.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Startup zombie recovery (MainViewModel.resumeActiveDownloadObservations / handleZombieTaskRecovery).
 *
 * Covers two confirmed bugs:
 * 1. The race: a task left in a "running" status while its WorkManager download work is still
 *    alive must NOT be reset to QUEUED, otherwise the queue processor enqueues a second worker for
 *    the same release.
 * 2. LOCAL_VERIFYING was a dead end: no startup branch handled it, so a row killed during the
 *    fast-track verification stayed "verifying…" forever. It must now go through the zombie path.
 */
class ZombieRecoveryTest {

    // --- Which statuses are picked up by the recovery filter ---

    @Test
    fun `zombie filter includes statuses left running after an app kill`() {
        assertEquals(true, isZombieTaskStatus(InstallTaskStatus.EXTRACTING))
        assertEquals(true, isZombieTaskStatus(InstallTaskStatus.INSTALLING))
    }

    @Test
    fun `LOCAL_VERIFYING is recovered instead of spinning forever`() {
        assertEquals(true, isZombieTaskStatus(InstallTaskStatus.LOCAL_VERIFYING))
    }

    @Test
    fun `statuses with their own startup handling are not zombies`() {
        assertEquals(false, isZombieTaskStatus(InstallTaskStatus.QUEUED))
        assertEquals(false, isZombieTaskStatus(InstallTaskStatus.DOWNLOADING))
        assertEquals(false, isZombieTaskStatus(InstallTaskStatus.PENDING_INSTALL))
        assertEquals(false, isZombieTaskStatus(InstallTaskStatus.PAUSED))
        assertEquals(false, isZombieTaskStatus(InstallTaskStatus.SHELVED))
        assertEquals(false, isZombieTaskStatus(InstallTaskStatus.COMPLETED))
        assertEquals(false, isZombieTaskStatus(InstallTaskStatus.FAILED))
    }

    // --- Reset decision: active work must block the reset ---

    @Test
    fun `task with active download work is NOT reset to QUEUED`() {
        // RUNNING / ENQUEUED / BLOCKED all mean "work still pending" (see DownloadWorkActiveTest)
        assertEquals(false, shouldResetZombieToQueued(downloadWorkActive = true))
    }

    @Test
    fun `task whose work genuinely finished IS reset to QUEUED`() {
        // No work left in WorkManager (SUCCEEDED / FAILED / CANCELLED / none): the existing
        // reset-to-QUEUED behaviour is correct and must be preserved.
        assertEquals(true, shouldResetZombieToQueued(downloadWorkActive = false))
    }

    @Test
    fun `recovery decision table`() {
        val cases = listOf(
            // status, work active?, expected reset to QUEUED
            Triple(InstallTaskStatus.EXTRACTING, true, false),
            Triple(InstallTaskStatus.INSTALLING, true, false),
            Triple(InstallTaskStatus.LOCAL_VERIFYING, true, false),
            Triple(InstallTaskStatus.EXTRACTING, false, true),
            Triple(InstallTaskStatus.INSTALLING, false, true),
            Triple(InstallTaskStatus.LOCAL_VERIFYING, false, true)
        )
        for ((status, workActive, expectedReset) in cases) {
            assertEquals("filter must pick up $status", true, isZombieTaskStatus(status))
            val reset = shouldResetZombieToQueued(workActive)
            assertEquals("reset decision for $status (workActive=$workActive)", expectedReset, reset)
        }
    }
}
