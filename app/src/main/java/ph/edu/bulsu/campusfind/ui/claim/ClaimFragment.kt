package ph.edu.bulsu.campusfind.ui.claim

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.core.Constants
import ph.edu.bulsu.campusfind.databinding.FragmentClaimBinding
import ph.edu.bulsu.campusfind.domain.TextRules
import ph.edu.bulsu.campusfind.model.ClaimDraft
import ph.edu.bulsu.campusfind.ui.common.ItemUi
import ph.edu.bulsu.campusfind.ui.common.UiMessage
import ph.edu.bulsu.campusfind.ui.common.check
import ph.edu.bulsu.campusfind.ui.common.collectWhenStarted
import ph.edu.bulsu.campusfind.ui.common.popWithMessage
import ph.edu.bulsu.campusfind.ui.common.showMessage
import ph.edu.bulsu.campusfind.ui.common.text

class ClaimFragment : Fragment() {

    private var _binding: FragmentClaimBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ClaimViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentClaimBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        listOf(binding.detailsLayout, binding.whenWhereLayout, binding.contactLayout).forEach { layout ->
            layout.editText?.doAfterTextChanged { layout.error = null }
        }
        binding.submitButton.setOnClickListener { submit() }

        collectWhenStarted(viewModel.item) { item ->
            if (item != null) {
                binding.itemTitle.text = item.title
                ItemUi.loadPicture(binding.picture, item, R.dimen.badge_size_row)
            }
        }
        collectWhenStarted(viewModel.submitting) { submitting ->
            binding.submitButton.isEnabled = !submitting
            binding.progress.visibility = if (submitting) View.VISIBLE else View.INVISIBLE
        }
        collectWhenStarted(viewModel.events) { event ->
            when (event) {
                ClaimEvent.Submitted -> popWithMessage(UiMessage(R.string.msg_claim_submitted))
                is ClaimEvent.Show -> showMessage(event.message)
            }
        }
    }

    private fun submit() {
        val details = binding.detailsLayout.text()
        val detailsError = when {
            details.trim().length < Constants.CLAIM_DETAILS_MIN -> R.string.error_claim_short
            !TextRules.isValidClaimDetails(details) -> R.string.error_claim_long
            else -> null
        }
        val checks = listOf(
            binding.detailsLayout.check(detailsError),
            binding.whenWhereLayout.check(
                if (TextRules.fitsMax(binding.whenWhereLayout.text(), Constants.CLAIM_OPTIONAL_MAX)) null else R.string.error_optional_long,
            ),
            binding.contactLayout.check(
                if (TextRules.fitsMax(binding.contactLayout.text(), Constants.CLAIM_OPTIONAL_MAX)) null else R.string.error_optional_long,
            ),
        )
        if (checks.any { !it }) return
        viewModel.submit(
            ClaimDraft(
                secretDetails = details,
                whenWhere = binding.whenWhereLayout.text(),
                contactNote = binding.contactLayout.text(),
            ),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
