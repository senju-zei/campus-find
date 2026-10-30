package ph.edu.bulsu.campusfind

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import ph.edu.bulsu.campusfind.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setUpSystemBars()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets()

        val navHost = supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
        navController = navHost.navController
        // Screens with no back arrow.
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.startFragment, R.id.loginFragment, R.id.feedFragment),
        )
        setSupportActionBar(binding.toolbar)
        binding.toolbar.overflowIcon = ContextCompat.getDrawable(this, R.drawable.ic_more_vert)
        setupActionBarWithNavController(navController, appBarConfiguration)
        // NavigationUI sets its own drawer arrow on each destination; this listener runs after
        // it and swaps in the Material Symbols arrow in textMuted.
        navController.addOnDestinationChangedListener { _, _, _ ->
            supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_arrow_back)
        }
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()

    /** Bars show the background color, with dark icons in light theme and light icons in dark. */
    private fun setUpSystemBars() {
        val background = ContextCompat.getColor(this, R.color.background)
        val style = if (resources.getBoolean(R.bool.light_system_bars)) {
            SystemBarStyle.light(background, background)
        } else {
            SystemBarStyle.dark(background)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = maxOf(bars.bottom, ime.bottom),
            )
            WindowInsetsCompat.CONSUMED
        }
    }
}
