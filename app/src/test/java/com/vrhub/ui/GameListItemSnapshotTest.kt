package com.vrhub.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.vrhub.ui.theme.VRHubTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GameListItemSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @Before
    fun setUp() {
        // Coil crossfade launches coroutines on Dispatchers.Main, unavailable in JVM tests.
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun state(
        name: String,
        installStatus: InstallStatus,
        queueStatus: InstallTaskStatus?,
        size: String?,
        isFavorite: Boolean = false
    ) = GameItemState(
        name = name,
        version = "1.0.0",
        packageName = "com.test.game",
        releaseName = name.lowercase().replace(' ', '-'),
        iconFile = null,
        installStatus = installStatus,
        queueStatus = queueStatus,
        size = size,
        isFavorite = isFavorite
    )

    @Test
    fun snapshotNotInstalledWithMetadataLoading() {
        paparazzi.snapshot {
            VRHubTheme {
                GameListItem(
                    game = state("Beat Saber", InstallStatus.NOT_INSTALLED, null, null),
                    onInstallClick = {},
                    onUninstallClick = {},
                    onDownloadOnlyClick = {}
                )
            }
        }
    }

    @Test
    fun snapshotInstalledFavoriteWithSize() {
        paparazzi.snapshot {
            VRHubTheme {
                GameListItem(
                    game = state("Superhot VR", InstallStatus.INSTALLED, null, "3.2 GB", isFavorite = true),
                    onInstallClick = {},
                    onUninstallClick = {},
                    onDownloadOnlyClick = {}
                )
            }
        }
    }

    @Test
    fun snapshotQueuedPausedFirstInQueue() {
        paparazzi.snapshot {
            VRHubTheme {
                GameListItem(
                    game = state(
                        "Pistol Whip",
                        InstallStatus.NOT_INSTALLED,
                        InstallTaskStatus.PAUSED,
                        "1.8 GB"
                    ).copy(isFirstInQueue = true, isDownloaded = true),
                    onInstallClick = {},
                    onUninstallClick = {},
                    onDownloadOnlyClick = {}
                )
            }
        }
    }
}
