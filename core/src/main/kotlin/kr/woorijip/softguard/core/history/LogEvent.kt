package kr.woorijip.softguard.core.history

/** 이력 한 줄의 종류 (설계문서 §8.4). 앱 실행 외에 제어 상태 변화도 같은 표에 남긴다. */
enum class LogType {
    /** 앱 실행 (허용/차단 모두) */
    APP_LAUNCH,
    /** 접근성 서비스 시작 */
    GUARD_STARTED,
    /** 접근성 서비스 종료 (inferred 면 추정) */
    GUARD_STOPPED,
    /** 기기 부팅 */
    DEVICE_BOOT,
}

/** DB 와 무관한 이력 한 줄. :app 의 Room 엔티티가 이 형태로 바꿔 넘긴다. */
data class LogEvent(
    val id: Long,
    val type: LogType,
    val occurredAt: Long,
    val packageName: String? = null,
    val appLabel: String? = null,
    val blocked: Boolean = false,
    val blockReason: String? = null,
    val inferred: Boolean = false,
)
