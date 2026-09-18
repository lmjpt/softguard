package kr.woorijip.softguard.core.storage

import kotlinx.serialization.json.Json
import kr.woorijip.softguard.core.model.AllowWindow
import kr.woorijip.softguard.core.model.Policy
import kr.woorijip.softguard.core.model.TimeOfDay
import java.time.DayOfWeek

sealed interface DecodeResult {
    data class Ok(val policy: Policy) : DecodeResult
    data class Failed(val message: String) : DecodeResult
}

/**
 * 정책 파일 포맷 (설계문서 §7.2 "저장 포맷 규칙").
 *   - version 3 만 읽는다. 화이트리스트 시절(v1·v2) 파일은 거부한다
 *   - defaultPolicy 는 "ALLOW_ALL" 이어야 한다
 *   - 모르는 키는 거부한다
 *   - 출력은 결정적이다 (집합을 정렬)
 *   - 읽기 실패는 예외가 아니라 Failed 로 돌려준다. 호출 쪽은 아무것도 차단하지 않는 정책으로 폴백한다
 */
object PolicyCodec {
    const val VERSION = 3
    const val DEFAULT_POLICY = "ALLOW_ALL"

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    private val DAY_CODES: Map<String, DayOfWeek> = DayOfWeek.entries.associateBy { it.name.substring(0, 3) }

    private fun code(day: DayOfWeek): String = day.name.substring(0, 3)

    fun encode(policy: Policy): String {
        val windows = policy.allowWindows
            .filter { it.isValid }
            .map { w ->
                AllowWindowDto(
                    days = w.days.sortedBy { it.value }.map(::code),
                    from = w.from.toString(),
                    to = w.to.toString(),
                )
            }
            .sortedWith(
                compareBy<AllowWindowDto>({ DAY_CODES[it.days.first()]!!.value }, { it.from }, { it.to }, { it.days.size })
            )
        val dto = PolicyDto(
            version = VERSION,
            defaultPolicy = DEFAULT_POLICY,
            blockedPackages = policy.blockedPackages.sorted(),
            allowWindows = windows,
        )
        return json.encodeToString(PolicyDto.serializer(), dto)
    }

    fun decode(text: String): DecodeResult {
        val dto = try {
            json.decodeFromString(PolicyDto.serializer(), text)
        } catch (e: Exception) {
            val first = e.message?.lineSequence()?.firstOrNull()?.take(160) ?: e.javaClass.simpleName
            return DecodeResult.Failed("정책 파일을 읽을 수 없어요: $first")
        }
        if (dto.version != VERSION) return DecodeResult.Failed("지원하지 않는 정책 버전이에요: ${dto.version}")
        if (dto.defaultPolicy != DEFAULT_POLICY) {
            return DecodeResult.Failed("기본 정책이 $DEFAULT_POLICY 이 아니에요: ${dto.defaultPolicy}")
        }
        val windows = ArrayList<AllowWindow>(dto.allowWindows.size)
        dto.allowWindows.forEachIndexed { i, w ->
            val n = i + 1
            val days = HashSet<DayOfWeek>()
            for (d in w.days) {
                days += DAY_CODES[d.trim().uppercase()]
                    ?: return DecodeResult.Failed("${n}번째 시간대의 요일을 모르겠어요: \"$d\"")
            }
            val from = TimeOfDay.parse(w.from)
                ?: return DecodeResult.Failed("${n}번째 시간대의 시작 시각이 잘못됐어요: \"${w.from}\"")
            val to = TimeOfDay.parse(w.to)
                ?: return DecodeResult.Failed("${n}번째 시간대의 종료 시각이 잘못됐어요: \"${w.to}\"")
            val window = AllowWindow(days, from, to)
            window.validate()?.let { return DecodeResult.Failed("${n}번째 시간대: $it") }
            windows += window
        }
        return DecodeResult.Ok(Policy(dto.blockedPackages.map { it.trim() }.filter { it.isNotEmpty() }.toSet(), windows))
    }
}
