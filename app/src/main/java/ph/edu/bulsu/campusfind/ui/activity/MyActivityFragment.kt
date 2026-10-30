package ph.edu.bulsu.campusfind.ui.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ph.edu.bulsu.campusfind.domain.Settled
import ph.edu.bulsu.campusfind.model.ClaimStatus
import ph.edu.bulsu.campusfind.ui.common.Selection
import ph.edu.bulsu.campusfind.ui.common.TabCounts
import kotlinx.coroutines.flow.combine
import ph.edu.bulsu.campusfind.ui.common.SelectionBar
import ph.edu.bulsu.campusfind.ui.common.UiMessage
import ph.edu.bulsu.campusfind.ui.common.showMessage
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.databinding.FragmentMyActivityBinding
import ph.edu.bulsu.campusfind.model.ItemType
import ph.edu.bulsu.campusfind.model.Role
import ph.edu.bulsu.campusfind.ui.common.ItemAdapter
import ph.edu.bulsu.campusfind.ui.common.NavArgs
import ph.edu.bulsu.campusfind.ui.common.collectWhenStarted
import ph.edu.bulsu.campusfind.ui.common.showResultMessages
import ph.edu.bulsu.campusfind.ui.common.visibleIf

class MyActivityFragment : Fragment() {

    private var _binding: FragmentMyActivityBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MyActivityViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMyActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setUpTabs()
        val nav = findNavController()
        binding.logOutButton.setOnClickListener {
            viewModel.logOut()
            // Clears the back stack, so Back cannot return to signed-in screens.
            nav.navigate(R.id.action_global_welcome)
        }
        collectWhenStarted(viewModel.profile) { profile ->
            if (profile == null) return@collectWhenStarted
            binding.name.text = profile.name
            binding.email.text = profile.email
            binding.initials.text = profile.name.split(" ").filter { it.isNotBlank() }.take(2)
                .joinToString("") { it.first().uppercase() }
            binding.role.setText(
                when (profile.role) {
                    Role.STUDENT -> R.string.role_student
                    Role.CLERK -> R.string.role_clerk
                    Role.FACULTY -> R.string.role_faculty
                },
            )
        }
        collectWhenStarted(viewModel.counts) { (posts, claims) ->
            if (viewModel.isStaff.value) return@collectWhenStarted
            binding.counts.text = listOf(
                resources.getQuantityString(R.plurals.count_posts, posts, posts),
                resources.getQuantityString(R.plurals.count_claims, claims, claims),
            ).joinToString(" · ")
            TabCounts.set(binding.tabs.getTabAt(ActivityTab.POSTS.ordinal), R.string.tab_posts, posts)
            TabCounts.set(binding.tabs.getTabAt(ActivityTab.CLAIMS.ordinal), R.string.tab_claims, claims)
        }
        binding.turnInReminder.setOnClickListener { binding.tabs.getTabAt(ActivityTab.POSTS.ordinal)?.select() }
        collectWhenStarted(combine(viewModel.turnInReminder, viewModel.isStaff) { count, staff -> if (staff) 0 else count }) { count ->
            binding.turnInReminder.visibleIf(count > 0)
            if (count > 0) binding.turnInReminder.text = resources.getQuantityString(R.plurals.reminder_turn_in, count, count)
        }
        val postsAdapter = ItemAdapter { item ->
            nav.navigate(R.id.action_my_activity_to_detail, bundleOf(NavArgs.ITEM_ID to item.id))
        }
        // Claims tab: long-press selects finished claims to remove from this Profile only.
        lateinit var claimsAdapter: MyClaimAdapter
        val claimsSelection = Selection {
            claimsAdapter.notifyDataSetChanged()
            claimsSelectionBar?.update()
        }
        claimsAdapter = MyClaimAdapter(
            selection = claimsSelection,
            onOpen = { row -> nav.navigate(R.id.action_my_activity_to_detail, bundleOf(NavArgs.ITEM_ID to row.claim.itemId)) },
            onClaimAgain = { row -> nav.navigate(R.id.action_my_activity_to_claim, bundleOf(NavArgs.ITEM_ID to row.claim.itemId)) },
            onSelect = { row ->
                if (row.claim.itemId in claimsSelection || Settled.claimHideable(row.claim)) {
                    claimsSelection.toggle(row.claim.itemId)
                } else {
                    showMessage(UiMessage(R.string.msg_cannot_remove_pending))
                }
            },
        )
        claimsSelectionBar = SelectionBar(this, claimsSelection) {
            val ids = claimsSelection.snapshot()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_remove_claims_title)
                .setMessage(
                    buildString {
                        append(resources.getQuantityString(R.plurals.dialog_remove_claims_body, ids.size, ids.size))
                        val approvedSelected = claimsAdapter.currentList
                            .any { it.claim.itemId in ids && it.claim.status == ClaimStatus.APPROVED }
                        if (approvedSelected) append("\n\n").append(getString(R.string.dialog_remove_approved_warning))
                    },
                )
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_remove) { _, _ ->
                    viewModel.hideClaims(ids)
                    claimsSelectionBar?.finish()
                }
                .show()
        }
        binding.list.layoutManager = LinearLayoutManager(requireContext())

        binding.swipe.setColorSchemeResources(R.color.accent)
        binding.swipe.setProgressBackgroundColorSchemeResource(R.color.surface)
        binding.swipe.setOnRefreshListener { viewModel.refresh() }
        setUpStaffHistory(nav)

        collectWhenStarted(viewModel.state) { state ->
            // Staff see History instead of Posts and Claims.
            if (state !is ActivityState.Loading) binding.swipe.isRefreshing = false
            if (viewModel.isStaff.value) return@collectWhenStarted
            if (state !is ActivityState.Claims) claimsSelectionBar?.finish()
            binding.progress.visibleIf(state is ActivityState.Loading)
            binding.error.visibleIf(state is ActivityState.Failed)
            when (state) {
                ActivityState.Loading -> binding.empty.visibleIf(false)
                is ActivityState.Failed -> {
                    binding.empty.visibleIf(false)
                    binding.list.adapter = null
                    binding.error.setText(state.message)
                }
                is ActivityState.Posts -> {
                    if (binding.list.adapter !== postsAdapter) binding.list.adapter = postsAdapter
                    postsAdapter.submitList(state.items)
                    showEmpty(
                        state.items.isEmpty(), R.string.empty_my_posts, R.string.action_post_item,
                    ) { nav.navigate(R.id.action_my_activity_to_post, bundleOf(NavArgs.TYPE to ItemType.FOUND.name)) }
                }
                is ActivityState.Claims -> {
                    if (binding.list.adapter !== claimsAdapter) binding.list.adapter = claimsAdapter
                    claimsAdapter.submitList(state.rows)
                    showEmpty(state.rows.isEmpty(), R.string.empty_my_claims, R.string.action_browse_found) {
                        nav.popBackStack()
                    }
                }
            }
        }
        collectWhenStarted(viewModel.messages) { showMessage(it) }
        // The Claim screen ("Claim again") hands back its confirmation here.
        showResultMessages()
    }

    private var selectionBar: SelectionBar? = null
    private var claimsSelectionBar: SelectionBar? = null

    /** Staff Profile: History of reviewed claims with long-press delete, instead of the tabs. */
    private fun setUpStaffHistory(nav: NavController) {
        lateinit var adapter: HistoryAdapter
        val selection = Selection {
            adapter.notifyDataSetChanged()
            selectionBar?.update()
        }
        adapter = HistoryAdapter(
            selection = selection,
            onOpen = { row ->
                if (row.item != null) nav.navigate(R.id.action_my_activity_to_detail, bundleOf(NavArgs.ITEM_ID to row.claim.itemId))
            },
            onSelect = { row ->
                if (row.key in selection || Settled.recordDeletable(row.claim, row.item, System.currentTimeMillis())) {
                    selection.toggle(row.key)
                } else {
                    showMessage(UiMessage(R.string.msg_cannot_delete_yet))
                }
            },
        )
        selectionBar = SelectionBar(this, selection) {
            val keys = selection.snapshot()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_delete_title)
                .setMessage(
                    buildString {
                        append(resources.getQuantityString(R.plurals.dialog_delete_records_body, keys.size, keys.size))
                        val approvedSelected = adapter.currentList.any { it.key in keys && it.claim.status == ClaimStatus.APPROVED }
                        if (approvedSelected) append("\n\n").append(getString(R.string.dialog_delete_approved_warning))
                    },
                )
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    viewModel.deleteHistory(keys)
                    selectionBar?.finish()
                }
                .show()
        }
        collectWhenStarted(viewModel.history) { rows ->
            if (rows == null) return@collectWhenStarted
            binding.swipe.isRefreshing = false
            binding.tabs.visibleIf(false)
            binding.historyHint.visibleIf(true)
            binding.progress.visibleIf(false)
            binding.error.visibleIf(false)
            if (binding.list.adapter !== adapter) binding.list.adapter = adapter
            adapter.submitList(rows)
            binding.empty.visibleIf(rows.isEmpty())
            binding.emptyText.setText(R.string.empty_history)
            binding.emptyAction.visibleIf(false)
            val approved = rows.count { it.claim.status == ClaimStatus.APPROVED }
            val rejected = rows.size - approved
            binding.counts.text = listOf(
                resources.getQuantityString(R.plurals.count_approved, approved, approved),
                resources.getQuantityString(R.plurals.count_rejected, rejected, rejected),
            ).joinToString(" · ")
        }
    }

    private fun showEmpty(empty: Boolean, text: Int, action: Int, onAction: () -> Unit) {
        binding.empty.visibleIf(empty)
        binding.emptyText.setText(text)
        binding.emptyAction.setText(action)
        binding.emptyAction.setOnClickListener { onAction() }
    }

    private fun setUpTabs() {
        val tabs = binding.tabs
        tabs.addTab(tabs.newTab().setText(R.string.tab_posts).setTag(ActivityTab.POSTS))
        tabs.addTab(tabs.newTab().setText(R.string.tab_claims).setTag(ActivityTab.CLAIMS))
        tabs.getTabAt(viewModel.selectedTab.value.ordinal)?.select()
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = viewModel.select(tab.tag as ActivityTab)
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    override fun onDestroyView() {
        selectionBar?.finish()
        selectionBar = null
        claimsSelectionBar?.finish()
        claimsSelectionBar = null
        super.onDestroyView()
        _binding = null
    }
}
