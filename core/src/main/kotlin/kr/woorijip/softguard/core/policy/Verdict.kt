package kr.woorijip.softguard.core.policy

import java.time.LocalDateTime

enum class BlockReason {
    /** 허용 시간대가 하나도 없어 항상 차단 */
    ALWAYS_BLOCKED,
    /** 허용 시간대 밖 */
    OUT_OF_HOURS,
}

sealed interface Verdict {
    /** 허용. until 은 이 허용이 끝나는 시각 (차단 목록에 없는 앱이면 null). */
    data class Allowed(val until: LocalDateTime?) : Verdict

    /** 차단. nextAvailableAt 은 다음 허용 시작 시각 (항상 차단이면 null). */
    data class Blocked(val reason: BlockReason, val nextAvailableAt: LocalDateTime?) : Verdict
}
