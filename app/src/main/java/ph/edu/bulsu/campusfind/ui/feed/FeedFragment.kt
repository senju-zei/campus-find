package ph.edu.bulsu.campusfind.ui.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.combine
import ph.edu.bulsu.campusfind.ui.common.TabCounts
import ph.edu.bulsu.campusfind.core.Constants
import androidx.core.view.MenuItemCompat
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
        binding.swipe.setColorSchemeResources(R.color.accent)
        binding.swipe.setProgressBackgroundColorSchemeResource(R.color.surface)
        binding.swipe.setOnRefreshListener { viewModel.refresh() }
        binding.searchInput.doAfterTextChanged { viewModel.search(it?.toString().orEmpty()) }
        setUpCategoryChips()

        collectWhenStarted(viewModel.state) { state ->
            binding.progress.visibleIf(state is FeedState.Loading && !binding.swipe.isRefreshing)
            if (state !is FeedState.Loading) binding.swipe.isRefreshing = false
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
                        when {
                            state.filtered -> R.string.empty_search
                            state.type == ItemType.FOUND -> R.string.empty_found
                            else -> R.string.empty_lost
                        },
                    )
                    binding.emptyAction.visibleIf(!state.filtered)
                }
            }
        }

        collectWhenStarted(viewModel.isStaff) { requireActivity().invalidateMenu() }
        collectWhenStarted(viewModel.sessionEnded) { findNavController().navigate(R.id.action_global_welcome) }
        collectWhenStarted(viewModel.tabCounts) { (found, lost) ->
            TabCounts.set(binding.tabs.getTabAt(0), R.string.tab_found, found)
            TabCounts.set(binding.tabs.getTabAt(1), R.string.tab_lost, lost)
        }
        collectWhenStarted(combine(viewModel.isStaff, viewModel.pendingClaims) { staff, count -> if (staff) count else 0 }) {
            showPendingBadge(it)
        }

        // Post item hands back its confirmation here.
        showResultMessages()
    }

    /** "All" plus one chip per category; filters the list on the phone. */
    private fun setUpCategoryChips() {
        val context = requireContext()
        val labels = listOf(getString(R.string.filter_all)) + resources.getStringArray(R.array.categories)
        val values = listOf<String?>(null) + Constants.CATEGORIES
        labels.forEachIndexed { index, label ->
            val chip = Chip(context).apply {
                id = View.generateViewId()
                text = label
                isCheckable = true
                isCheckedIconVisible = false
                shapeAppearanceModel = shapeAppearanceModel.withCornerSize(resources.getDimension(R.dimen.radius_control))
                chipBackgroundColor = ContextCompat.getColorStateList(context, R.color.toggle_background)
                chipStrokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.border))
                chipStrokeWidth = resources.getDimension(R.dimen.border_width)
                setTextColor(ContextCompat.getColor(context, R.color.text))
                isChecked = values[index] == viewModel.category.value
                setOnCheckedChangeListener { _, checked -> if (checked) viewModel.filterCategory(values[index]) }
            }
            binding.categoryGroup.addView(chip)
        }
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
                    // The menu views were rebuilt, so the badge is attached again.
                    showPendingBadge(if (viewModel.isStaff.value) viewModel.pendingClaims.value else 0)
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

    private val toolbar: MaterialToolbar get() = requireActivity().findViewById(R.id.toolbar)
    private var pendingBadge: BadgeDrawable? = null

    /**
     * Number badge on the Staff dashboard icon. Posted so it attaches after the menu is rebuilt
     * (invalidateMenu recreates the icon views).
     */
    @androidx.annotation.OptIn(markerClass = [ExperimentalBadgeUtils::class])
    private fun showPendingBadge(count: Int) {
        val bar = toolbar
        bar.post {
            if (_binding == null) return@post
            pendingBadge?.let { BadgeUtils.detachBadgeDrawable(it, bar, R.id.action_staff_dashboard) }
            pendingBadge = null
            // Null until the menu is first built; onPrepareMenu calls this again then.
            val item = bar.menu.findItem(R.id.action_staff_dashboard) ?: return@post
            MenuItemCompat.setContentDescription(
                item,
                if (count > 0) resources.getQuantityString(R.plurals.cd_staff_dashboard_pending, count, count) else getString(R.string.cd_staff_dashboard),
            )
            if (count <= 0 || !item.isVisible) return@post
            val context = requireContext()
            pendingBadge = BadgeDrawable.create(context).apply {
                number = count
                maxCharacterCount = 3
                backgroundColor = ContextCompat.getColor(context, R.color.accent)
                badgeTextColor = ContextCompat.getColor(context, R.color.on_accent)
                // Screen readers say "3 claims to review", not Material's default "new notifications".
                setContentDescriptionQuantityStringsResource(R.plurals.badge_pending_claims)
            }.also { BadgeUtils.attachBadgeDrawable(it, bar, R.id.action_staff_dashboard) }
        }
    }

    /** Opens Post item with the type of the tab the user is on. */
    private fun openPostItem() {
        findNavController().navigate(
            R.id.action_feed_to_post,
            bundleOf(NavArgs.TYPE to viewModel.selectedType.value.name),
        )
    }

    @androidx.annotation.OptIn(markerClass = [ExperimentalBadgeUtils::class])
    override fun onDestroyView() {
        pendingBadge?.let { BadgeUtils.detachBadgeDrawable(it, toolbar, R.id.action_staff_dashboard) }
        pendingBadge = null
        super.onDestroyView()
        _binding = null
    }
}
