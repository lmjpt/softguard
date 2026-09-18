package kr.woorijip.softguard.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.inputmethod.InputMethodManager

/**
 * 설치 앱 목록과 "기록 대상인가" 판별 (설계문서 §8.2).
 * 결과는 캐시하고 PACKAGE_ADDED/REMOVED 때 invalidate() 로 비운다.
 */
class AppCatalog(private val context: Context) {

    data class AppInfo(val packageName: String, val label: String, val isSystem: Boolean)

    private val pm: PackageManager = context.packageManager
    val selfPackage: String = context.packageName

    @Volatile private var appsCache: Map<String, AppInfo>? = null
    @Volatile private var launcherCache: String? = null
    @Volatile private var imeCache: Set<String>? = null

    /**
     * 전면에 잠깐 올라오지만 "다른 앱으로 갔다"고 볼 수 없는 것들.
     * 알림 창·권한 대화상자·입력기가 올라와도 차단 상태를 바꾸지 않는다 (오버레이가 사라져 차단 앱이 드러나는 것을 막는다).
     */
    private val transientPackages = setOf(
        "com.android.systemui",
        "android",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.samsung.android.permissioncontroller",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
    )

    /** 차단 목록에 넣을 수 없는 앱 — 넣으면 기기를 되살릴 수 없거나 앱 자체가 동작하지 않는다 (§7.1 안전장치). */
    fun isExcludedFromBlockList(pkg: String): Boolean =
        pkg == selfPackage || pkg == defaultLauncher() || pkg == "com.android.systemui" || pkg == "com.android.settings"

    /** 런처에서 실행할 수 있는 앱 전체 (자기 자신 제외), 이름순. */
    fun apps(): List<AppInfo> = appsMap().values.sortedBy { it.label.lowercase() }

    private fun appsMap(): Map<String, AppInfo> {
        appsCache?.let { return it }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        val map = HashMap<String, AppInfo>()
        for (ri in resolved) {
            val ai = ri.activityInfo?.applicationInfo ?: continue
            val pkg = ai.packageName
            if (pkg == selfPackage || map.containsKey(pkg)) continue
            val label = try { ai.loadLabel(pm).toString() } catch (_: Exception) { pkg }
            val system = (ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            map[pkg] = AppInfo(pkg, label, system)
        }
        appsCache = map
        return map
    }

    fun defaultLauncher(): String? {
        launcherCache?.let { return it }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val ri = pm.resolveActivity(home, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY))
        val pkg = ri?.activityInfo?.packageName
        // 기본 홈이 아직 없으면 (android 이 돌아옴) 캐시하지 않는다
        if (pkg != null && pkg != "android") launcherCache = pkg
        return pkg
    }

    private fun imePackages(): Set<String> {
        imeCache?.let { return it }
        val imm = context.getSystemService(InputMethodManager::class.java)
        val set = try { imm.enabledInputMethodList.map { it.packageName }.toSet() } catch (_: Exception) { emptySet() }
        imeCache = set
        return set
    }

    /** 이력에 남길 앱인가: 런처에서 실행 가능하고, 자기 자신·기본 홈이 아닌 것. */
    fun isLoggable(pkg: String): Boolean =
        pkg != selfPackage && pkg != defaultLauncher() && appsMap().containsKey(pkg)

    /** 판정·오버레이 상태를 건드리지 않고 지나갈 창인가. */
    fun isTransient(pkg: String): Boolean = pkg in transientPackages || pkg in imePackages()

    fun isLauncher(pkg: String): Boolean = pkg == defaultLauncher()

    fun labelOf(pkg: String): String =
        appsMap()[pkg]?.label ?: try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))).toString()
        } catch (_: Exception) {
            pkg
        }

    fun iconOf(pkg: String): Drawable? = try { pm.getApplicationIcon(pkg) } catch (_: Exception) { null }

    fun isInstalled(pkg: String): Boolean = try {
        pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0)); true
    } catch (_: Exception) {
        false
    }

    fun invalidate() {
        appsCache = null
        launcherCache = null
        imeCache = null
    }

    /** 차단 목록 선정 가이드 (설계문서 §7.1). 설치된 것만 화면에 추천으로 보인다. */
    val recommended: List<Pair<String, String>> = listOf(
        "com.android.vending" to "Play 스토어 — 새 앱을 설치해 우회하는 길을 막아요",
        "com.sec.android.app.samsungapps" to "갤럭시 스토어 — 새 앱을 설치해 우회하는 길을 막아요",
        "com.sec.android.app.sbrowser" to "삼성 인터넷 — 브라우저로 같은 서비스에 들어가는 길을 막아요",
        "com.android.chrome" to "Chrome — 브라우저로 같은 서비스에 들어가는 길을 막아요",
    )
}
