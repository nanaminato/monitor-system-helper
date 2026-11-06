package local.helperblock

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Bundle

class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var screen: MainView
    private var revision = -1L
    private val refresh = object : Runnable {
        override fun run() {
            screen.showStatus(CaptureService.status)
            RequestFeed.snapshot(revision)?.let { (version, packets) ->
                revision = version
                screen.showPackets(packets)
            }
            main.postDelayed(this, 750)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        screen = MainView(this) { startActivity(Intent(this, MonitorActivity::class.java)) }
        setContentView(screen)
    }

    override fun onStart() {
        super.onStart()
        refresh.run()
    }

    override fun onStop() {
        main.removeCallbacks(refresh)
        super.onStop()
    }
}
