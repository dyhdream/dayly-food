package com.daylyfood.app.ui

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daylyfood.app.data.ai.AiClient
import com.daylyfood.app.data.ai.AiKeyStore
import com.daylyfood.app.data.ai.AiSettings
import com.daylyfood.app.data.db.AppDatabase
import com.daylyfood.app.data.db.FoodEntity
import com.daylyfood.app.data.db.ServingEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * PLAN §13 红线 / §10 验收：没配 provider、断网时，搜索 → 选份量 → 记录 → 当日汇总必须照样走通。
 *
 * 这里跑的是生产代码（SearchViewModel + Room DAO），只把网络换掉：谁要是哪天把 AI 调用塞进
 * 记录路径，这两条用例会先炸，而不是等用户断网才发现。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NoProviderRecordFlowTest {

    private lateinit var database: AppDatabase
    private lateinit var aiSettings: AiSettings
    private lateinit var search: SearchViewModel

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        // Inline executors keep Room on the calling thread: no sleeps, no waiting on a pool.
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        aiSettings = AiSettings(context, AiKeyStore(context))
        search = SearchViewModel(database.foodDao(), database.diaryDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `no provider - search, pick a portion, record and read the day`() = runTest {
        assertFalse("前提：一个 provider 都没配", aiSettings.config.value.isConfigured)
        assertFalse("没配置时 AI 入口必须不可用", aiSettings.config.value.canCall)

        val ai = AiClient(aiSettings)
        val reply = ai.testConnection()
        assertTrue("没配置 provider 时 AI 调用必须是可捕获的失败，而不是崩溃或挂住", reply.isFailure)
        assertTrue(
            "失败原因要指回设置页（接口地址/模型名），实际：${reply.exceptionOrNull()?.message}",
            reply.exceptionOrNull()?.message.orEmpty().contains("接口地址"),
        )

        seedRice()
        val totals = searchPickRecordAndSummarize()

        assertEquals(1, totals.entries)
        assertEquals(RICE_KCAL100 * PORTION_GRAMS / 100.0, totals.kcal, 0.001)
        assertEquals(2.6 * PORTION_GRAMS / 100.0, totals.protein, 0.001)
        assertEquals(0.3 * PORTION_GRAMS / 100.0, totals.fat, 0.001)
        assertEquals(25.9 * PORTION_GRAMS / 100.0, totals.carb, 0.001)
    }

    @Test
    fun `offline - the same flow records the same numbers`() = runTest {
        val attempts = AtomicInteger()
        val previous = ProxySelector.getDefault()
        ProxySelector.setDefault(OfflineProxy(attempts))
        try {
            // Positive control: without this the test would pass even if it blocked nothing.
            val probe = runCatching {
                URL("http://example.com/").openConnection().getInputStream().use { it.readBytes() }
            }
            assertTrue("断网模拟必须真的挡住 HTTP", probe.isFailure)
            assertTrue("断网模拟必须拦到这次探测", attempts.get() > 0)
            attempts.set(0)

            seedRice()
            val totals = searchPickRecordAndSummarize()

            assertEquals("断网时结果必须与无 provider 时一致", 1, totals.entries)
            assertEquals(RICE_KCAL100 * PORTION_GRAMS / 100.0, totals.kcal, 0.001)
            assertEquals(2.6 * PORTION_GRAMS / 100.0, totals.protein, 0.001)
            assertEquals(0.3 * PORTION_GRAMS / 100.0, totals.fat, 0.001)
            assertEquals(25.9 * PORTION_GRAMS / 100.0, totals.carb, 0.001)
            assertEquals("本地记录路径不得发起任何网络请求", 0, attempts.get())

            val reply = AiClient(aiSettings).testConnection()
            assertTrue("断网时 AI 入口必须是可捕获的失败", reply.isFailure)
            assertEquals("断网时 AI 入口也不得尝试联网", 0, attempts.get())
        } finally {
            ProxySelector.setDefault(previous)
        }
    }

    /** 生产路径：搜索 → 选中 → 取份量 → 记录，返回首页汇总用的当日数据。 */
    private suspend fun searchPickRecordAndSummarize(): DayTotals {
        search.setQuery(QUERY)
        settle()

        val hit = search.results.value.firstOrNull { it.name == RICE }
        assertNotNull("搜索「$QUERY」必须命中本地食物库，实际：${search.results.value.map { it.name }}", hit)
        search.select(hit!!)
        settle()

        val portions = search.servings.value
        assertTrue("选中食物后必须拿到份量选项", portions.isNotEmpty())
        assertEquals("默认份量应排在最前", PORTION_GRAMS, portions.first().grams, 0.001)

        var recorded = false
        search.record(MEAL, portions.first().grams, DAY) { recorded = true }
        settle()
        assertTrue("记录按钮在任何配置下都必须可用", recorded)

        val entries = database.diaryDao().list(DAY)
        assertEquals("当日汇总的数据源必须只有这一条记录", 1, entries.size)
        return DayTotals(
            entries = entries.size,
            kcal = entries.sumOf { it.kcal },
            protein = entries.sumOf { it.protein },
            fat = entries.sumOf { it.fat },
            carb = entries.sumOf { it.carb },
        )
    }

    private suspend fun seedRice() {
        database.foodDao().insertFoods(
            listOf(
                FoodEntity(
                    id = "F101",
                    name = RICE,
                    kind = "INGREDIENT",
                    basis = "PER_100G",
                    category = "主食",
                    brand = null,
                    baseUnit = "g",
                    kcal100 = RICE_KCAL100,
                    protein100 = 2.6,
                    fat100 = 0.3,
                    carb100 = 25.9,
                    fiber100 = null,
                    sodium100 = null,
                    source = "local",
                    sourceUrl = null,
                    confidence = "high",
                    verified = true,
                    notes = null,
                ),
            ),
        )
        database.foodDao().insertServings(
            listOf(
                ServingEntity(id = "S101", foodId = "F101", label = "一碗", grams = PORTION_GRAMS, isDefault = true, sort = 1),
                ServingEntity(id = "S102", foodId = "F101", label = "大碗", grams = 250.0, isDefault = false, sort = 2),
            ),
        )
    }

    /** ViewModel 的协程落在主线程 Looper 上；把它跑干净是确定的，sleep 不是。 */
    private fun settle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private data class DayTotals(
        val entries: Int,
        val kcal: Double,
        val protein: Double,
        val fat: Double,
        val carb: Double,
    )

    /** 断网 = 任何 HTTP 连接在建立前就失败，顺便数一下有人偷偷联网没有。 */
    private class OfflineProxy(private val attempts: AtomicInteger) : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> {
            attempts.incrementAndGet()
            throw IllegalStateException("测试模拟断网：不允许建立 HTTP 连接")
        }

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
    }

    private companion object {
        const val DAY = 20_000L
        const val MEAL = "lunch"
        const val QUERY = "米饭"
        const val RICE = "米饭(熟)"
        const val RICE_KCAL100 = 116.0
        const val PORTION_GRAMS = 150.0
    }
}
