package kr.woorijip.softguard.core.storage

import kotlinx.serialization.Serializable

/**
 * 저장 전용 DTO. 도메인 모델(Policy)과 분리해 파일 포맷을 고정한다.
 * 필드를 바꾸면 version 을 올리고 PolicyCodec 의 검증도 함께 고쳐야 한다.
 */
@Serializable
internal data class PolicyDto(
    val version: Int,
    val defaultPolicy: String,
    val blockedPackages: List<String>,
    val allowWindows: List<AllowWindowDto>,
)

@Serializable
internal data class AllowWindowDto(
    val days: List<String>,
    val from: String,
    val to: String,
)
