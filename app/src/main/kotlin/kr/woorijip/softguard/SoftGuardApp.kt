package kr.woorijip.softguard

import android.app.Application
import android.content.Context
import kr.woorijip.softguard.service.LogPruneWorker
import kr.woorijip.softguard.service.StatusNotifier

class SoftGuardApp : Application() {

    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
        StatusNotifier.ensureChannel(this)
        LogPruneWorker.ensureScheduled(this)
        graph.diag.log("앱 프로세스 시작")
    }

    companion object {
        fun graph(context: Context): Graph = (context.applicationContext as SoftGuardApp).graph
    }
}
