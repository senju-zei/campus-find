package ph.edu.bulsu.campusfind.ui.start

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.core.ServiceLocator
import ph.edu.bulsu.campusfind.databinding.FragmentStartBinding

/**
 * Start destination, shown behind the system splash. Signed out goes to Welcome; signed in and
 * verified goes to Feed; signed in but unverified goes to Login, which shows the verification notice.
 * Both actions pop this screen, so Back never returns here.
 */
class StartFragment : Fragment() {

    private var _binding: FragmentStartBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentStartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val user = ServiceLocator.authRepository.currentUser
        val action = when {
            user == null -> R.id.action_start_to_welcome
            user.isEmailVerified -> R.id.action_start_to_feed
            else -> R.id.action_start_to_login
        }
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
