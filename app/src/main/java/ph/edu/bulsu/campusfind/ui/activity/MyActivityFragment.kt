package ph.edu.bulsu.campusfind.ui.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.databinding.FragmentMyActivityBinding
import ph.edu.bulsu.campusfind.model.ItemType
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
        val postsAdapter = ItemAdapter { item ->
            nav.navigate(R.id.action_my_activity_to_detail, bundleOf(NavArgs.ITEM_ID to item.id))
        }
        val claimsAdapter = MyClaimAdapter(
            onOpen = { row -> nav.navigate(R.id.action_my_activity_to_detail, bundleOf(NavArgs.ITEM_ID to row.claim.itemId)) },
            onClaimAgain = { row -> nav.navigate(R.id.action_my_activity_to_claim, bundleOf(NavArgs.ITEM_ID to row.claim.itemId)) },
        )
        binding.list.layoutManager = LinearLayoutManager(requireContext())

        collectWhenStarted(viewModel.state) { state ->
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
        // The Claim screen ("Claim again") hands back its confirmation here.
        showResultMessages()
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
        super.onDestroyView()
        _binding = null
    }
}
