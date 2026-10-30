package ph.edu.bulsu.campusfind.ui.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.core.ServiceLocator
import ph.edu.bulsu.campusfind.databinding.FragmentFeedBinding

/** Step 4 placeholder: proves sign-in works and offers Log out. Step 5 builds the real Feed. */
class FeedFragment : Fragment() {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val email = ServiceLocator.authRepository.currentUser?.email.orEmpty()
        binding.signedInText.text = getString(R.string.feed_placeholder, email)

        requireActivity().addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.menu_feed, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
                    R.id.action_log_out -> {
                        ServiceLocator.authRepository.logOut()
                        // Clears the back stack, so Back from Login does not return to Feed.
                        findNavController().navigate(R.id.action_global_login)
                        true
                    }
                    else -> false
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
