package dev.quietly.ui.apps

import dev.quietly.data.db.dao.DayTotal
import dev.quietly.data.db.entity.AppOverrideEntity
import dev.quietly.data.db.entity.AppUsageEntity
import dev.quietly.domain.repository.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeRepo = FakeUsageRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `viewModel initializes and loads apps`() = runTest {
        val vm = AppsViewModel(fakeRepo)

        val app1 = AppUsageEntity("com.app.a", 100, "App A", 1000L, 5)
        val app2 = AppUsageEntity("com.app.b", 100, "App B", 2000L, 10)

        fakeRepo.emitApps(listOf(app1, app2))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.totalApps)
        assertEquals(15, state.totalOpens)
        assertEquals(3000L, state.totalTimeMs)
        // Default sort is TIME_DESC
        assertEquals(listOf("com.app.b", "com.app.a"), state.filtered.map { it.packageName })
    }

    @Test
    fun `setQuery filters apps by appLabel and packageName`() = runTest {
        val vm = AppsViewModel(fakeRepo)

        val app1 = AppUsageEntity("com.social.chat", 100, "Chat App", 1000L, 5)
        val app2 = AppUsageEntity("com.game.puzzle", 100, "Puzzle Game", 2000L, 10)

        fakeRepo.emitApps(listOf(app1, app2))
        testDispatcher.scheduler.advanceUntilIdle()

        // Filter by label
        vm.setQuery("Chat")
        assertEquals(1, vm.uiState.value.filtered.size)
        assertEquals("Chat App", vm.uiState.value.filtered.first().appLabel)

        // Filter by package name
        vm.setQuery("puzzle")
        assertEquals(1, vm.uiState.value.filtered.size)
        assertEquals("Puzzle Game", vm.uiState.value.filtered.first().appLabel)

        // Blank query restores all
        vm.setQuery("")
        assertEquals(2, vm.uiState.value.filtered.size)
    }

    @Test
    fun `setSort updates app list order`() = runTest {
        val vm = AppsViewModel(fakeRepo)

        val app1 = AppUsageEntity("com.a", 100, "Beta", 1000L, 20)
        val app2 = AppUsageEntity("com.b", 100, "Alpha", 5000L, 5)

        fakeRepo.emitApps(listOf(app1, app2))
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setSort(AppSort.NAME)
        assertEquals(listOf("Alpha", "Beta"), vm.uiState.value.filtered.map { it.appLabel })

        vm.setSort(AppSort.LAUNCHES)
        assertEquals(listOf("Beta", "Alpha"), vm.uiState.value.filtered.map { it.appLabel })
    }

    private class FakeUsageRepository : UsageRepository {
        private val flow = MutableSharedFlow<List<AppUsageEntity>>(replay = 1)

        suspend fun emitApps(apps: List<AppUsageEntity>) {
            flow.emit(apps)
        }

        override fun observeDay(day: Int): Flow<List<AppUsageEntity>> = flow

        override suspend fun syncToday() {}

        override suspend fun queryRange(fromDay: Int, toDay: Int): List<AppUsageEntity> = emptyList()

        override suspend fun dailyTotals(fromDay: Int, toDay: Int): List<DayTotal> = emptyList()

        override suspend fun historyForApp(pkg: String, limit: Int): List<AppUsageEntity> = emptyList()

        override suspend fun purgeOld(retentionDays: Int) {}

        override suspend fun query90DayAggregated(today: Int): List<AppUsageEntity> = emptyList()

        override suspend fun allPerDayRows90(today: Int): List<AppUsageEntity> = emptyList()

        override suspend fun getOverrides(): List<AppOverrideEntity> = emptyList()

        override suspend fun setOverride(entity: AppOverrideEntity) {}

        override suspend fun clearOverride(packageName: String) {}

        override suspend fun getAppInsights(): List<dev.quietly.domain.AppInsight> = emptyList()
    }
}
