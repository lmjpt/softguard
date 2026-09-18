package kr.woorijip.softguard

import android.app.Application
import kr.woorijip.softguard.data.AppCatalog
import kr.woorijip.softguard.data.DetectionLog
import kr.woorijip.softguard.data.EventLogger
import kr.woorijip.softguard.data.GuardPrefs
import kr.woorijip.softguard.data.PinStore
import kr.woorijip.softguard.data.PolicyRepository
import kr.woorijip.softguard.data.db.SoftGuardDatabase
import java.time.ZoneId

/**
 * 앱 전체가 공유하는 객체 묶음. DI 라이브러리 대신 손으로 조립한다.
 * 화면·서비스·리시버 모두 SoftGuardApp.graph(context) 로 얻는다.
 */
class Graph(app: Application) {
    val zone: ZoneId = ZoneId.systemDefault()
    val diag = DetectionLog()
    val prefs = GuardPrefs(app)
    val pinStore = PinStore(app)
    val appCatalog = AppCatalog(app)
    val policies = PolicyRepository(app, diag)
    val db: SoftGuardDatabase = SoftGuardDatabase.get(app)
    val logger = EventLogger(db.eventLogDao(), prefs, diag, zone)
}
