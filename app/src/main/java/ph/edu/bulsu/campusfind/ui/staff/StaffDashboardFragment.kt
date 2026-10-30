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
import ph.edu.bulsu.campusfind.domain.Settled
import ph.edu.bulsu.campusfind.ui.common.ItemAdapter
import ph.edu.bulsu.campusfind.ui.common.Selection
import ph.edu.bulsu.campusfind.ui.common.SelectionBar
import ph.edu.bulsu.campusfind.ui.common.TabCounts
import ph.edu.bulsu.campusfind.ui.common.UiMessage
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
        // Settled tab: tap opens, long-press selects. Returned items warn about the pickup code.
        lateinit var settledAdapter: ItemAdapter
        val selection = Selection {
            settledAdapter.notifyDataSetChanged()
            selectionBar?.update()
        }
        settledAdapter = ItemAdapter(
            onClick = { item -> nav.navigate(R.id.action_staff_to_detail, bundleOf(NavArgs.ITEM_ID to item.id)) },
            selection = selection,
            onSelect = { item ->
                if (item.id in selection || Settled.deletable(item, System.currentTimeMillis())) {
                    selection.toggle(item.id)
                } else {
                    showMessage(UiMessage(R.string.msg_cannot_delete_yet))
                }
            },
        )
        selectionBar = SelectionBar(this, selection) {
            val ids = selection.snapshot()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_delete_title)
                .setMessage(
                    buildString {
                        append(resources.getQuantityString(R.plurals.dialog_delete_items_body, ids.size, ids.size))
                        val returnedSelected = settledAdapter.currentList.any { it.id in ids && Settled.deleteNeedsWarning(it) }
                        if (returnedSelected) append("\n\n").append(getString(R.string.dialog_delete_returned_warning))
                    },
                )
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    viewModel.deleteItems(ids)
                    selectionBar?.finish()
                }
                .show()
        }
        binding.list.layoutManager = LinearLayoutManager(requireContext())

        collectWhenStarted(viewModel.state) { state ->
            binding.progress.visibleIf(state is StaffState.Loading)
            binding.error.visibleIf(state is StaffState.Failed)
            binding.hint.visibleIf(state is StaffState.Claims && state.rows.isNotEmpty() || state is StaffState.Settled)
            binding.hint.setText(if (state is StaffState.Settled) R.string.hint_settled else R.string.hint_tap_review)
            if (state !is StaffState.Settled) selectionBar?.finish()
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
                    if (binding.list.adapter !== settledAdapter) binding.list.adapter = settledAdapter
                    settledAdapter.submitList(state.items)
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
        collectWhenStarted(viewModel.messages) { showMessage(it) }
        collectWhenStarted(viewModel.tabCounts) { (pending, turnIn, settled) ->
            TabCounts.set(binding.tabs.getTabAt(StaffTab.CLAIMS.ordinal), R.string.tab_staff_claims, pending)
            TabCounts.set(binding.tabs.getTabAt(StaffTab.TURN_IN.ordinal), R.string.tab_staff_turn_in, turnIn)
            TabCounts.set(binding.tabs.getTabAt(StaffTab.SETTLED.ordinal), R.string.tab_staff_settled, settled)
        }

        // Review claim hands back "Claim approved" or "Claim rejected" here.
        showResultMessages()
    }

    private var selectionBar: SelectionBar? = null

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
        selectionBar?.finish()
        selectionBar = null
        super.onDestroyView()
        _binding = null
    }
}
