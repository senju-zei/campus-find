package ph.edu.bulsu.campusfind.ui.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import ph.edu.bulsu.campusfind.R
import ph.edu.bulsu.campusfind.core.ServiceLocator
import ph.edu.bulsu.campusfind.databinding.FragmentFeedBinding
import ph.edu.bulsu.campusfind.model.ItemType
import ph.edu.bulsu.campusfind.ui.common.ItemAdapter
import ph.edu.bulsu.campusfind.ui.common.NavArgs
import ph.edu.bulsu.campusfind.ui.common.showResultMessages
import ph.edu.bulsu.campusfind.ui.common.collectWhenStarted
import ph.edu.bulsu.campusfind.ui.common.visibleIf

class FeedFragment : Fragment() {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FeedViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setUpTabs()
        setUpMenu()

        val adapter = ItemAdapter { item ->
            findNavController().navigate(R.id.action_feed_to_detail, bundleOf(NavArgs.ITEM_ID to item.id))
        }
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter
        binding.emptyAction.setOnClickListener { openPostItem() }

        collectWhenStarted(viewModel.state) { state ->
            binding.progress.visibleIf(state is FeedState.Loading)
            binding.error.visibleIf(state is FeedState.Failed)
            when (state) {
                FeedState.Loading -> binding.empty.visibleIf(false)
                is FeedState.Failed -> {
                    binding.empty.visibleIf(false)
                    adapter.submitList(emptyList())
                    binding.error.setText(state.message)
                }
                is FeedState.Loaded -> {
                    adapter.submitList(state.items)
                    binding.empty.visibleIf(state.items.isEmpty())
                    binding.emptyText.setText(
                        if (state.type == ItemType.FOUND) R.string.empty_found else R.string.empty_lost,
                    )
                }
            }
        }

        collectWhenStarted(viewModel.isStaff) { requireActivity().invalidateMenu() }

        // Post item hands back its confirmation here.
        showResultMessages()
    }

    private fun setUpTabs() {
        val tabs = binding.tabs
        tabs.addTab(tabs.newTab().setText(R.string.tab_found).setTag(ItemType.FOUND))
        tabs.addTab(tabs.newTab().setText(R.string.tab_lost).setTag(ItemType.LOST))
        tabs.getTabAt(if (viewModel.selectedType.value == ItemType.FOUND) 0 else 1)?.select()
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.select(tab.tag as ItemType)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun setUpMenu() {
        requireActivity().addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.menu_feed, menu)
                }

                override fun onPrepareMenu(menu: Menu) {
                    menu.findItem(R.id.action_staff_dashboard)?.isVisible = viewModel.isStaff.value
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
                    R.id.action_post_item -> {
                        openPostItem()
                        true
                    }
                    R.id.action_my_activity -> {
                        findNavController().navigate(R.id.action_feed_to_my_activity)
                        true
                    }
                    R.id.action_staff_dashboard -> {
                        findNavController().navigate(R.id.action_feed_to_staff)
                        true
                    }
                    else -> false
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )
    }

    /** Opens Post item with the type of the tab the user is on. */
    private fun openPostItem() {
        findNavController().navigate(
            R.id.action_feed_to_post,
            bundleOf(NavArgs.TYPE to viewModel.selectedType.value.name),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
