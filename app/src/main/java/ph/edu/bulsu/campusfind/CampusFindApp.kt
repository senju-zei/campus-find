package ph.edu.bulsu.campusfind

import android.app.Application
import ph.edu.bulsu.campusfind.core.ServiceLocator

class CampusFindApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Must run before any Firebase call so USE_EMULATOR builds point at 10.0.2.2.
        ServiceLocator.init()
    }
}
