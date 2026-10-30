package ph.edu.bulsu.campusfind.ui.staff

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.databinding.FragmentStaffDashboardBinding
import ph.edu.bulsu.campusfind.ui.common.ItemAdapter
import ph.edu.bulsu.campusfind.ui.common.NavArgs
import ph.edu.bulsu.campusfind.ui.common.collectWhenStarted
import ph.edu.bulsu.campusfind.ui.common.showMessage
import ph.edu.bulsu.campusfind.ui.common.showResultMessages
import ph.edu.bulsu.campusfind.ui.common.visibleIf

/** Claims to review (oldest first, beside the hidden detail) and found items to turn in. */
class StaffDashboardFragment : Fragment() {

    private var _binding: FragmentStaffDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StaffDashboardViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentStaffDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setUpTabs()
        val nav = findNavController()
        val claimsAdapter = StaffClaimAdapter { row ->
            nav.navigate(
                R.id.action_staff_to_review,
                bundleOf(NavArgs.ITEM_ID to row.claim.itemId, NavArgs.CLAIMANT to row.claim.claimant),
            )
        }
        // Opens Item detail, where staff see "Mark turned in".
        val turnInAdapter = ItemAdapter { item ->
            nav.navigate(R.id.action_staff_to_detail, bundleOf(NavArgs.ITEM_ID to item.id))
        }
        binding.list.layoutManager = LinearLayoutManager(requireContext())

        collectWhenStarted(viewModel.state) { state ->
            binding.progress.visibleIf(state is StaffState.Loading)
            binding.error.visibleIf(state is StaffState.Failed)
            binding.hint.visibleIf(state is StaffState.Claims && state.rows.isNotEmpty() || state is StaffState.Settled)
            binding.hint.setText(if (state is StaffState.Settled) R.string.hint_settled else R.string.hint_tap_review)
            binding.deleteOldButton.visibleIf(state is StaffState.Settled && state.deletable.isNotEmpty())
            when (state) {
                StaffState.Loading -> binding.empty.visibleIf(false)
                is StaffState.Failed -> {
                    binding.empty.visibleIf(false)
                    binding.list.adapter = null
                    binding.error.setText(state.message)
                }
                is StaffState.Claims -> {
                    if (binding.list.adapter !== claimsAdapter) binding.list.adapter = claimsAdapter
                    claimsAdapter.submitList(state.rows)
                    binding.empty.visibleIf(state.rows.isEmpty())
                    binding.emptyText.setText(R.string.empty_staff_claims)
                }
                is StaffState.Settled -> {
                    if (binding.list.adapter !== turnInAdapter) binding.list.adapter = turnInAdapter
                    turnInAdapter.submitList(state.items)
                    binding.empty.visibleIf(state.items.isEmpty())
                    binding.emptyText.setText(R.string.empty_staff_settled)
                }
                is StaffState.TurnIn -> {
                    if (binding.list.adapter !== turnInAdapter) binding.list.adapter = turnInAdapter
                    turnInAdapter.submitList(state.items)
                    binding.empty.visibleIf(state.items.isEmpty())
                    binding.emptyText.setText(R.string.empty_staff_turn_in)
                }
            }
        }
        binding.deleteOldButton.setOnClickListener {
            val count = (viewModel.state.value as? StaffState.Settled)?.deletable?.size ?: 0
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_delete_title)
                .setMessage(resources.getQuantityString(R.plurals.dialog_delete_old_body, count, count))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.deleteOld() }
                .show()
        }
        collectWhenStarted(viewModel.messages) { showMessage(it) }

        // Review claim hands back "Claim approved" or "Claim rejected" here.
        showResultMessages()
    }

    private fun setUpTabs() {
        val tabs = binding.tabs
        tabs.addTab(tabs.newTab().setText(R.string.tab_staff_claims).setTag(StaffTab.CLAIMS))
        tabs.addTab(tabs.newTab().setText(R.string.tab_staff_turn_in).setTag(StaffTab.TURN_IN))
        tabs.addTab(tabs.newTab().setText(R.string.tab_staff_settled).setTag(StaffTab.SETTLED))
        tabs.getTabAt(viewModel.selectedTab.value.ordinal)?.select()
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = viewModel.select(tab.tag as StaffTab)
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
