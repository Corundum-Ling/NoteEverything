// ============================================================
// InstalledAppFetcher.kt — 已安装应用查询 + 搜索联想
// ============================================================
// 使用 PackageManager 获取本机已安装应用列表，
// 为添加软件的 Auto 模式提供搜索联想数据。
//
// 数据流：
//   InstalledAppFetcher.getInstalledApps() → List<InstalledApp>
//   → 按输入文字过滤 → 排除已添加的包名 → 显示联想下拉
//
// 学习要点：
// - PackageManager 查询是同步的，在 IO 线程执行
// - queryIntentActivities 比 getInstalledApplications 更轻量
// - 按使用频率排序（最近使用的 App 排在前面）

package com.corunling.noteeverything.data.auto

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 一条已安装应用信息。
 */
data class InstalledApp(
    val packageName: String,
    val appName: String,
    val isGame: Boolean = false   // 是否游戏类（供后续自动分类参考）
)

/**
 * 已安装应用查询工具。
 * 查询本机所有可启动的 App，用于搜索联想。
 */
class InstalledAppFetcher(private val context: Context) {

    private var cachedApps: List<InstalledApp>? = null

    /**
     * 获取当前设备上所有已安装的可启动应用列表。
     * 结果按应用名称排序。
     * 内部有缓存，调用 getFilteredApps 时如缓存存在则不重复查询。
     */
    suspend fun getInstalledApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        cachedApps?.let { return@withContext it }

        val pm = context.packageManager

        // 源1：有桌面图标的应用（CATEGORY_LAUNCHER）
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val launcherPackages = pm.queryIntentActivities(mainIntent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()

        // 源2：全部已安装应用
        val allInstalled = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }

        val apps = allInstalled
            .filter { info ->
                // 必须有非空 label 且不是系统核心进程
                info.enabled && info.loadLabel(pm)?.let { it.isNotEmpty() } == true &&
                (info.packageName in launcherPackages ||  // 有桌面图标
                 info.packageName.startsWith("com.") ||    // 标准第三方包名
                 info.packageName.startsWith("org."))
            }
            .distinctBy { it.packageName }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    appName = info.loadLabel(pm).toString()
                )
            }
            .sortedBy { it.appName }

        cachedApps = apps
        apps
    }

    /**
     * 清空缓存（添加完软件后调用，下次打开时刷新列表）。
     */
    fun invalidateCache() {
        cachedApps = null
    }

    /**
     * 按输入文字搜索联想。
     * 匹配名称或包名，忽略大小写，排除已添加的包名。
     *
     * @param query 输入文字
     * @param excludePackages 要排除的包名列表（已添加的软件）
     * @param limit 返回结果上限，默认 10
     */
    suspend fun searchApps(
        query: String,
        excludePackages: Set<String> = emptySet(),
        limit: Int = 10
    ): List<InstalledApp> = withContext(Dispatchers.IO) {
        if (query.length < 2) return@withContext emptyList()

        val allApps = getInstalledApps()
        val lowerQuery = query.lowercase()

        val results = allApps
            .filter { app ->
                app.packageName !in excludePackages &&
                (app.appName.lowercase().contains(lowerQuery) ||
                 app.packageName.lowercase().contains(lowerQuery))
            }
            .take(limit)

        // 兜底：如果正常搜索没结果，且输入看起来像包名（含 .），
        // 直接用 getPackageInfo 验证是否是已安装应用
        if (results.isEmpty() && query.contains(".")) {
            try {
                val pm = context.packageManager
                @Suppress("DEPRECATION")
                val pkgInfo = pm.getPackageInfo(query, 0)
                if (pkgInfo != null && pkgInfo.applicationInfo != null && query !in excludePackages) {
                    val appName = pkgInfo.applicationInfo.loadLabel(pm).toString()
                    return@withContext listOf(
                        InstalledApp(packageName = query, appName = appName)
                    )
                }
            } catch (_: Exception) {
                // 包名不存在或无权访问
            }
        }

        results
    }
}
