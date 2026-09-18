package kr.woorijip.softguard.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 진단용 메모리 로그 (설계문서 §14.2). adb 를 붙일 수 없어 logcat 대신 앱 안에서 본다.
 * 메모리에만 있어 프로세스가 죽으면 사라진다. 판정 근거로 쓰지 말고 참고만 할 것.
 */
class DetectionLog(private val capacity: Int = 300) {
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    private val fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

    fun log(message: String) {
        val line = "${LocalDateTime.now().format(fmt)}  $message"
        _lines.update { (listOf(line) + it).take(capacity) }
    }

    fun clear() {
        _lines.value = emptyList()
    }
}
