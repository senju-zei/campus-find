package ph.edu.bulsu.campusfind.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.core.ServiceLocator
import ph.edu.bulsu.campusfind.databinding.FragmentLoginBinding
import ph.edu.bulsu.campusfind.ui.common.UiMessage
import ph.edu.bulsu.campusfind.ui.common.check
import ph.edu.bulsu.campusfind.ui.common.collectWhenStarted
import ph.edu.bulsu.campusfind.ui.common.showMessage
import ph.edu.bulsu.campusfind.ui.common.text
import ph.edu.bulsu.campusfind.ui.common.visibleIf

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.emailInput.doAfterTextChanged { binding.emailLayout.error = null }
        binding.passwordInput.doAfterTextChanged { binding.passwordLayout.error = null }
        binding.passwordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }

        binding.logInButton.setOnClickListener { submit() }
        binding.goToRegisterButton.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }
        binding.iHaveVerifiedButton.setOnClickListener { viewModel.checkVerified() }
        binding.resendButton.setOnClickListener { viewModel.resendVerification() }
        binding.useAnotherAccountButton.setOnClickListener {
            ServiceLocator.authRepository.logOut()
            viewModel.syncPendingVerification()
        }

        collectWhenStarted(viewModel.state) { state ->
            binding.progress.visibility = if (state.loading) View.VISIBLE else View.INVISIBLE
            binding.logInButton.isEnabled = !state.loading
            binding.goToRegisterButton.isEnabled = !state.loading
            binding.iHaveVerifiedButton.isEnabled = !state.loading
            binding.resendButton.isEnabled = !state.loading
            binding.useAnotherAccountButton.isEnabled = !state.loading
            binding.verifyNotice.visibleIf(state.pendingVerificationEmail != null)
            binding.verifyEmail.text = state.pendingVerificationEmail.orEmpty()
        }
        collectWhenStarted(viewModel.events) { event ->
            when (event) {
                LoginEvent.GoToFeed -> findNavController().navigate(R.id.action_login_to_feed)
                is LoginEvent.Show -> showMessage(event.message)
            }
        }

        // Register hands back its confirmation here after creating the account.
        val handle = findNavController().currentBackStackEntry?.savedStateHandle
        handle?.getLiveData<UiMessage>(KEY_REGISTER_MESSAGE)?.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                handle.remove<UiMessage>(KEY_REGISTER_MESSAGE)
                viewModel.syncPendingVerification()
                showMessage(message)
            }
        }
    }

    private fun submit() {
        val email = binding.emailLayout.text()
        val password = binding.passwordLayout.text()
        val emailOk = binding.emailLayout.check(AuthFormRules.loginEmail(email))
        val passwordOk = binding.passwordLayout.check(AuthFormRules.loginPassword(password))
        if (emailOk && passwordOk) viewModel.logIn(email, password)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val KEY_REGISTER_MESSAGE = "register_message"
    }
}
