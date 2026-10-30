package ph.edu.bulsu.campusfind.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import ph.edu.bulsu.campusfind.databinding.FragmentRegisterBinding
import ph.edu.bulsu.campusfind.ui.common.check
import ph.edu.bulsu.campusfind.ui.common.collectWhenStarted
import ph.edu.bulsu.campusfind.ui.common.showMessage
import ph.edu.bulsu.campusfind.ui.common.text

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RegisterViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.nameInput.doAfterTextChanged { binding.nameLayout.error = null }
        binding.emailInput.doAfterTextChanged { binding.emailLayout.error = null }
        binding.passwordInput.doAfterTextChanged { binding.passwordLayout.error = null }

        binding.createAccountButton.setOnClickListener { submit() }
        binding.goToLoginButton.setOnClickListener { findNavController().popBackStack() }

        collectWhenStarted(viewModel.loading) { loading ->
            binding.progress.visibility = if (loading) View.VISIBLE else View.INVISIBLE
            binding.createAccountButton.isEnabled = !loading
            binding.goToLoginButton.isEnabled = !loading
        }
        collectWhenStarted(viewModel.events) { event ->
            when (event) {
                is RegisterEvent.GoToLogin -> {
                    val nav = findNavController()
                    nav.previousBackStackEntry?.savedStateHandle
                        ?.set(LoginFragment.KEY_REGISTER_MESSAGE, event.message)
                    nav.popBackStack()
                }
                is RegisterEvent.Show -> showMessage(event.message)
            }
        }
    }

    private fun submit() {
        val name = binding.nameLayout.text()
        val email = binding.emailLayout.text()
        val password = binding.passwordLayout.text()
        val nameOk = binding.nameLayout.check(AuthFormRules.name(name))
        val emailOk = binding.emailLayout.check(AuthFormRules.registerEmail(email))
        val passwordOk = binding.passwordLayout.check(AuthFormRules.registerPassword(password))
        if (nameOk && emailOk && passwordOk) viewModel.register(name, email, password)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
