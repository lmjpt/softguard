package kr.woorijip.softguard.core.model

/**
 * 정책 전체. 블랙리스트 방식 (설계문서 §7.1):
 *   기본 전부 허용 + 차단 목록의 앱만 공통 허용 시간대에 연다.
 */
data class Policy(
    val blockedPackages: Set<String> = emptySet(),
    val allowWindows: List<AllowWindow> = emptyList(),
) {
    fun isBlocked(packageName: String): Boolean = packageName in blockedPackages

    fun withBlocked(packageName: String, blocked: Boolean): Policy =
        copy(blockedPackages = if (blocked) blockedPackages + packageName else blockedPackages - packageName)

    companion object {
        /** 아무것도 차단하지 않는 정책. 읽기 실패 시 폴백 (기기를 계속 쓸 수 있는 방향). */
        val EMPTY = Policy()
    }
}
