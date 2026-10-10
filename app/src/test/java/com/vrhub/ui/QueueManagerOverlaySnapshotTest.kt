package com.vrhub.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.vrhub.QueueManagerOverlay
import com.vrhub.ui.theme.VRHubTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class QueueManagerOverlaySnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun task(
        releaseName: String,
        gameName: String,
        status: InstallTaskStatus,
        progress: Float = 0f
    ) = InstallTaskState(
        releaseName = releaseName,
        gameName = gameName,
        packageName = "com.test.$releaseName",
        status = status,
        progress = progress
    )

    @Test
    fun snapshotQueueWithActiveDownloadAndPausedTasks() {
        val queue = listOf(
            task("beat-saber", "Beat Saber", InstallTaskStatus.DOWNLOADING, 0.5f),
            task("superhot-vr", "Superhot VR", InstallTaskStatus.PAUSED, 0.25f),
            task("pistol-whip", "Pistol Whip", InstallTaskStatus.QUEUED)
        )
        paparazzi.snapshot {
            VRHubTheme {
                QueueManagerOverlay(
                    queue = queue,
                    viewedReleaseName = null,
                    onTaskClick = {},
                    onCancel = {},
                    onPause = {},
                    onResume = {},
                    onPromote = {},
                    onClose = {}
                )
            }
        }
    }

    @Test
    fun snapshotQueueCancelButtonsVisible() {
        paparazzi.snapshot {
            VRHubTheme {
                QueueManagerOverlay(
                    queue = listOf(
                        task("beat-saber", "Beat Saber", InstallTaskStatus.DOWNLOADING, 0.5f),
                        task("superhot-vr", "Superhot VR", InstallTaskStatus.QUEUED)
                    ),
                    viewedReleaseName = null,
                    onTaskClick = {},
                    onCancel = {},
                    onPause = {},
                    onResume = {},
                    onPromote = {},
                    onClose = {}
                )
            }
        }
    }

    @Test
    fun snapshotQueueFirstItemPromoteHidden() {
        paparazzi.snapshot {
            VRHubTheme {
                QueueManagerOverlay(
                    queue = listOf(task("beat-saber", "Beat Saber", InstallTaskStatus.DOWNLOADING, 0.75f)),
                    viewedReleaseName = "beat-saber",
                    onTaskClick = {},
                    onCancel = {},
                    onPause = {},
                    onResume = {},
                    onPromote = {},
                    onClose = {}
                )
            }
        }
    }

    @Test
    fun snapshotQueueEmptyState() {
        paparazzi.snapshot {
            VRHubTheme {
                QueueManagerOverlay(
                    queue = emptyList(),
                    viewedReleaseName = null,
                    onTaskClick = {},
                    onCancel = {},
                    onPause = {},
                    onResume = {},
                    onPromote = {},
                    onClose = {}
                )
            }
        }
    }
}
