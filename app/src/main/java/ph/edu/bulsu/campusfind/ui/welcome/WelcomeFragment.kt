package ph.edu.bulsu.campusfind.ui.welcome

import android.graphics.drawable.Animatable
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.databinding.FragmentWelcomeBinding

/**
 * Landing page for signed-out users: logo, short welcome, Log in and Create account.
 * The logo fades in, the text and buttons slide up, then the eye glances and blinks.
 * All motion is skipped when the system has animations turned off.
 */
class WelcomeFragment : Fragment() {

    private var _binding: FragmentWelcomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentWelcomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.logInButton.setOnClickListener {
            findNavController().navigate(R.id.action_welcome_to_login)
        }
        binding.createAccountButton.setOnClickListener {
            findNavController().navigate(R.id.action_welcome_to_register)
        }
        if (savedInstanceState == null && animationsEnabled()) playIntro()
    }

    private fun playIntro() {
        val slide = resources.getDimension(R.dimen.welcome_slide)
        val ease = DecelerateInterpolator()

        binding.logo.apply {
            alpha = 0f
            scaleX = 0.85f
            scaleY = 0.85f
            animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(LOGO_MS).setInterpolator(ease)
                .withEndAction { (drawable as? Animatable)?.start() }
                .start()
        }

        val staggered = listOf(binding.title, binding.tagline, binding.body, binding.pilot, binding.actions)
        staggered.forEachIndexed { index, target ->
            target.alpha = 0f
            target.translationY = slide
            target.animate().alpha(1f).translationY(0f)
                .setStartDelay(TEXT_DELAY_MS + index * STAGGER_MS)
                .setDuration(TEXT_MS).setInterpolator(ease)
                .start()
        }
    }

    private fun animationsEnabled(): Boolean = Settings.Global.getFloat(
        requireContext().contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) != 0f

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val LOGO_MS = 500L
        const val TEXT_DELAY_MS = 250L
        const val STAGGER_MS = 90L
        const val TEXT_MS = 400L
    }
}
