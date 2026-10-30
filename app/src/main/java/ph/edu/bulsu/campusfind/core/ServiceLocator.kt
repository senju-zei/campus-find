package ph.edu.bulsu.campusfind.core

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import okhttp3.OkHttpClient
import ph.edu.bulsu.campusfind.BuildConfig
import ph.edu.bulsu.campusfind.data.AuthRepository
import ph.edu.bulsu.campusfind.data.BookLookupRepository
import ph.edu.bulsu.campusfind.data.ClaimRepository
import ph.edu.bulsu.campusfind.data.ItemRepository
import ph.edu.bulsu.campusfind.data.UserRepository
import java.util.concurrent.TimeUnit

/** Simple manual wiring. [init] runs once from CampusFindApp before anything uses Firebase. */
object ServiceLocator {

    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
        if (BuildConfig.USE_EMULATOR) {
            FirebaseAuth.getInstance()
                .useEmulator(BuildConfig.EMULATOR_HOST, BuildConfig.AUTH_EMULATOR_PORT)
            FirebaseDatabase.getInstance()
                .useEmulator(BuildConfig.EMULATOR_HOST, BuildConfig.DATABASE_EMULATOR_PORT)
        }
    }

    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private val databaseRoot by lazy { FirebaseDatabase.getInstance().reference }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val userRepository by lazy { UserRepository(databaseRoot) }
    val authRepository by lazy { AuthRepository(auth, userRepository) }
    val itemRepository by lazy { ItemRepository(databaseRoot) }
    val claimRepository by lazy { ClaimRepository(databaseRoot) }
    val bookLookupRepository by lazy { BookLookupRepository(httpClient) }
}
