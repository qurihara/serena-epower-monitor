package org.unryu.epowermonitor

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class EPowerCarAppService : CarAppService() {
    // サイドロード運用のため、接続元ホストの検証はすべて許可する
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = EPowerSession()
}

class EPowerSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = MonitorScreen(carContext)
}
