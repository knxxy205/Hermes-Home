package com.example

import com.example.data.model.ServerConfig
import com.example.data.sftp.SftpManager
import com.example.ui.files.FilesViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FilesViewModelTest {
    @Test fun latestQueryRefreshAndNavigationKeepTheRightListing() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val manager = SftpManager()
        val vm = FilesViewModel(manager)
        val config = ServerConfig()
        try {
            vm.navigateTo("/home/admin", config)
            withTimeout(5_000) { vm.uiState.first { !it.isLoading } }
            vm.updateSearchQuery("compose")
            vm.updateSearchQuery("")
            assertFalse(vm.uiState.value.isLoading)
            assertFalse(vm.uiState.value.isSearching)
            assertNull(vm.uiState.value.errorMessage)
            assertTrue(vm.uiState.value.files.any { it.name == "docker-compose.yml" })
            vm.updateSearchQuery("compose")
            vm.updateSearchQuery("Caddyfile")
            val found = withTimeout(5_000) { vm.uiState.first { !it.isSearching } }
            assertEquals(listOf("/home/admin/docker/Caddyfile"), found.files.map { it.path })
            vm.loadFiles(config = config)
            val refreshed = withTimeout(5_000) { vm.uiState.first { !it.isSearching } }
            assertEquals(found.files, refreshed.files)
            vm.updateSearchQuery("compose")
            vm.navigateTo("/home/admin/docker", config)
            val navigated = withTimeout(5_000) { vm.uiState.first { !it.isLoading && !it.isSearching } }
            assertEquals("", navigated.searchQuery)
            assertTrue(navigated.files.any { it.path == "/home/admin/docker/Caddyfile" })
            vm.updateSearchQuery("Caddyfile")
            vm.stopSearch()
            delay(400)
            assertFalse(vm.uiState.value.isSearching)
            assertTrue(vm.uiState.value.searchNotice!!.contains("dihentikan"))
            vm.updateSearchQuery("")
            withTimeout(5_000) { vm.uiState.first { !it.isLoading } }
            assertTrue(vm.uiState.value.files.any { it.name == "hermes" })
        } finally {
            manager.close()
            Dispatchers.resetMain()
        }
    }
}
