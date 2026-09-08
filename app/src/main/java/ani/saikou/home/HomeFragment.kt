package ani.saikou.home

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.drawable.Animatable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LayoutAnimationController
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.saikou.MainActivity
import ani.saikou.Refresh
import ani.saikou.bottomBar
import ani.saikou.connections.anilist.Anilist
import ani.saikou.connections.anilist.AnilistHomeViewModel
import ani.saikou.connections.anilist.getUserId
import ani.saikou.databinding.FragmentHomeBinding
import ani.saikou.loadData
import ani.saikou.loadImage
import ani.saikou.media.Media
import ani.saikou.media.MediaAdaptor
import ani.saikou.media.user.ListActivity
import ani.saikou.navBarHeight
import ani.saikou.setSafeOnClickListener
import ani.saikou.setSlideIn
import ani.saikou.setSlideUp
import ani.saikou.settings.SettingsDialogFragment
import ani.saikou.settings.UserInterfaceSettings
import ani.saikou.snackString
import ani.saikou.statusBarHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    val model: AnilistHomeViewModel by activityViewModels()

    private var currentHomeLayoutShow: List<Boolean>? = null

    private companion object {
        const val IDX_ANIME_CONTINUE = 0
        const val IDX_ANIME_FAV = 1
        const val IDX_ANIME_PLANNED = 2
        const val IDX_MANGA_CONTINUE = 3
        const val IDX_MANGA_FAV = 4
        const val IDX_MANGA_PLANNED = 5
        const val IDX_RECOMMENDED = 6

        val PRIORITY_INDICES = setOf(IDX_ANIME_CONTINUE, IDX_MANGA_CONTINUE)


    }

    private data class SectionConfig(
        val index: Int,
        val mode: LiveData<ArrayList<Media>>,
        val container: View,
        val recyclerView: RecyclerView,
        val progress: View,
        val empty: View,
        val title: View,
        val fetch: suspend () -> Unit
    )

    private fun getSectionsList(): List<SectionConfig> {
        return listOf(
            SectionConfig(
                IDX_ANIME_CONTINUE,
                model.getAnimeContinue(),
                binding.homeContinueWatchingContainer,
                binding.homeWatchingRecyclerView,
                binding.homeWatchingProgressBar,
                binding.homeWatchingEmpty,
                binding.homeContinueWatch,
                fetch = { model.setAnimeContinue() }
            ),
            SectionConfig(
                IDX_ANIME_FAV,
                model.getAnimeFav(),
                binding.homeFavAnimeContainer,
                binding.homeFavAnimeRecyclerView,
                binding.homeFavAnimeProgressBar,
                binding.homeFavAnimeEmpty,
                binding.homeFavAnime,
                fetch = { model.setAnimeFav() }
            ),
            SectionConfig(
                IDX_ANIME_PLANNED,
                model.getAnimePlanned(),
                binding.homePlannedAnimeContainer,
                binding.homePlannedAnimeRecyclerView,
                binding.homePlannedAnimeProgressBar,
                binding.homePlannedAnimeEmpty,
                binding.homePlannedAnime,
                fetch = { model.setAnimePlanned() }
            ),
            SectionConfig(
                IDX_MANGA_CONTINUE,
                model.getMangaContinue(),
                binding.homeContinueReadingContainer,
                binding.homeReadingRecyclerView,
                binding.homeReadingProgressBar,
                binding.homeReadingEmpty,
                binding.homeContinueRead,
                fetch = { model.setMangaContinue() }
            ),
            SectionConfig(
                IDX_MANGA_FAV,
                model.getMangaFav(),
                binding.homeFavMangaContainer,
                binding.homeFavMangaRecyclerView,
                binding.homeFavMangaProgressBar,
                binding.homeFavMangaEmpty,
                binding.homeFavManga,
                fetch = { model.setMangaFav() }
            ),
            SectionConfig(
                IDX_MANGA_PLANNED,
                model.getMangaPlanned(),
                binding.homePlannedMangaContainer,
                binding.homePlannedMangaRecyclerView,
                binding.homePlannedMangaProgressBar,
                binding.homePlannedMangaEmpty,
                binding.homePlannedManga,
                fetch = { model.setMangaPlanned() }
            ),
            SectionConfig(
                IDX_RECOMMENDED,
                model.getRecommendation(),
                binding.homeRecommendedContainer,
                binding.homeRecommendedRecyclerView,
                binding.homeRecommendedProgressBar,
                binding.homeRecommendedEmpty,
                binding.homeRecommended,
                fetch = { model.setRecommendation() }
            )
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val scope = lifecycleScope
        var uiSettings = loadData<UserInterfaceSettings>("ui_settings") ?: UserInterfaceSettings()
        currentHomeLayoutShow = uiSettings.homeLayoutShow.toList()

        fun loadHeaderUI() {
            if (activity != null && _binding != null) {
                lifecycleScope.launch(Dispatchers.Main) {
                    binding.homeUserName.text = Anilist.username
                    binding.homeUserEpisodesWatched.text = Anilist.episodesWatched.toString()
                    binding.homeUserChaptersRead.text = Anilist.chapterRead.toString()
                    binding.homeUserAvatar.loadImage(Anilist.avatar)
                    if (!uiSettings.bannerAnimations) binding.homeUserBg.pause()
                    binding.homeUserBg.loadImage(Anilist.bg)
                    binding.homeUserDataProgressBar.visibility = View.GONE

                    binding.homeAnimeList.setOnClickListener {
                        ContextCompat.startActivity(
                            requireActivity(), Intent(requireActivity(), ListActivity::class.java)
                                .putExtra("anime", true)
                                .putExtra("userId", Anilist.userid)
                                .putExtra("username", Anilist.username), null
                        )
                    }
                    binding.homeMangaList.setOnClickListener {
                        ContextCompat.startActivity(
                            requireActivity(), Intent(requireActivity(), ListActivity::class.java)
                                .putExtra("anime", false)
                                .putExtra("userId", Anilist.userid)
                                .putExtra("username", Anilist.username), null
                        )
                    }

                    binding.homeUserAvatarContainer.startAnimation(setSlideUp(uiSettings))
                    binding.homeUserDataContainer.visibility = View.VISIBLE
                    binding.homeUserDataContainer.layoutAnimation =
                        LayoutAnimationController(setSlideUp(uiSettings), 0.25f)
                    binding.homeAnimeList.visibility = View.VISIBLE
                    binding.homeMangaList.visibility = View.VISIBLE
                    binding.homeListContainer.layoutAnimation =
                        LayoutAnimationController(setSlideIn(uiSettings), 0.25f)
                }
            }
        }

        binding.homeUserAvatarContainer.setSafeOnClickListener {
            SettingsDialogFragment().show(parentFragmentManager, "dialog")
        }

        binding.homeContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = navBarHeight
        }
        binding.homeUserBg.updateLayoutParams { height += statusBarHeight }
        binding.homeTopContainer.updatePadding(top = statusBarHeight)

        var reached = false
        val duration = (uiSettings.animationSpeed * 200).toLong()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding.homeScroll.setOnScrollChangeListener { _, _, _, _, _ ->
                if (!binding.homeScroll.canScrollVertically(1)) {
                    reached = true
                    bottomBar.animate().translationZ(0f).setDuration(duration).start()
                    ObjectAnimator.ofFloat(bottomBar, "elevation", 4f, 0f).setDuration(duration)
                        .start()
                } else {
                    if (reached) {
                        bottomBar.animate().translationZ(12f).setDuration(duration).start()
                        ObjectAnimator.ofFloat(bottomBar, "elevation", 0f, 4f).setDuration(duration)
                            .start()
                    }
                }
            }
        }
        var height = statusBarHeight
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val displayCutout = activity?.window?.decorView?.rootWindowInsets?.displayCutout
            if (displayCutout != null && displayCutout.boundingRects.isNotEmpty()) {
                height = max(
                    statusBarHeight,
                    min(
                        displayCutout.boundingRects[0].width(),
                        displayCutout.boundingRects[0].height()
                    )
                )
            }
        }
        binding.homeRefresh.setSlingshotDistance(height + 128)
        binding.homeRefresh.setProgressViewEndTarget(false, height + 128)
        binding.homeRefresh.setOnRefreshListener {
            Refresh.activity[1]!!.postValue(true)
        }

        binding.homeUserDataProgressBar.visibility = View.VISIBLE
        binding.homeUserDataContainer.visibility = View.GONE
        if (model.loaded) {
            loadHeaderUI()
        }

        model.getListImages().observe(viewLifecycleOwner) {
            if (it.isNotEmpty()) {
                binding.homeAnimeListImage.loadImage(it[0] ?: "https://bit.ly/31bsIHq")
                binding.homeMangaListImage.loadImage(it[1] ?: "https://bit.ly/2ZGfcuG")
            }
        }

        val sections = getSectionsList()

        applyInitialLayoutStructure(sections, uiSettings)
        sections.forEach { s ->
            initRecyclerViewObserver(
                s.mode, s.container, s.recyclerView, s.progress, s.empty, s.title, uiSettings
            )
        }

        binding.homeWatchingBrowseButton.setOnClickListener {
            (activity as? MainActivity)?.navigateToTab(0)
        }
        binding.homeReadingBrowseButton.setOnClickListener {
            (activity as? MainActivity)?.navigateToTab(2)
        }
        binding.homePlannedAnimeBrowseButton.setOnClickListener {
            (activity as? MainActivity)?.navigateToTab(0)
        }
        binding.homePlannedMangaBrowseButton.setOnClickListener {
            (activity as? MainActivity)?.navigateToTab(2)
        }

        binding.homeUserAvatarContainer.startAnimation(setSlideUp(uiSettings))

        model.empty.observe(viewLifecycleOwner) {
            binding.homeSaikouContainer.visibility = if (it == true) View.VISIBLE else View.GONE
            (binding.homeSaikouIcon.drawable as Animatable).start()
            binding.homeSaikouContainer.startAnimation(setSlideUp(uiSettings))
            binding.homeSaikouIcon.setSafeOnClickListener {
                (binding.homeSaikouIcon.drawable as Animatable).start()
            }
        }

        val live = Refresh.activity.getOrPut(1) { MutableLiveData(false) }
        live.observe(viewLifecycleOwner) { isRefreshing ->
            if (isRefreshing) {
                scope.launch {
                    uiSettings = loadData<UserInterfaceSettings>("ui_settings") ?: UserInterfaceSettings()
                    currentHomeLayoutShow = uiSettings.homeLayoutShow.toList()

                    applyInitialLayoutStructure(sections, uiSettings)

                    try {
                        withContext(Dispatchers.IO) {
                            if (getUserId(requireContext())) {
                                withContext(Dispatchers.Main) { loadHeaderUI() }
                            }
                            model.loaded = true
                            model.setListImages()

                            val enabled = sections.filter { uiSettings.homeLayoutShow.getOrElse(it.index) { false } }
                            val isEmpty = enabled.isEmpty()

                            val priority = enabled.filter { it.index in PRIORITY_INDICES }
                            val secondary = enabled.filterNot { it.index in PRIORITY_INDICES }

                            val priorityJobs = priority.map { launch { it.fetch() } }
                            priorityJobs.joinAll()

                            val secondaryJobs = secondary.map { launch { it.fetch() } }
                            secondaryJobs.joinAll()

                            withContext(Dispatchers.Main) {
                                model.empty.postValue(isEmpty)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            snackString("Failed to load AniList data.")
                            binding.homeSaikouContainer.visibility = View.VISIBLE
                            binding.homeUserDataProgressBar.visibility = View.GONE
                        }
                    } finally {
                        live.postValue(false)
                        _binding?.homeRefresh?.isRefreshing = false
                    }
                }
            }
        }
    }

    private fun applyInitialLayoutStructure(
        sections: List<SectionConfig>,
        uiSettings: UserInterfaceSettings
    ) {
        sections.forEach { s ->
            val isEnabled = uiSettings.homeLayoutShow.getOrElse(s.index) { false }
            if (isEnabled) {
                s.container.visibility = View.VISIBLE
                if (s.mode.value == null) {

                    s.progress.visibility = View.VISIBLE
                    s.recyclerView.visibility = View.GONE
                    s.empty.visibility = View.GONE
                    s.title.visibility = View.INVISIBLE
                }
            } else {
                s.container.visibility = View.GONE
            }
        }
    }


    private fun initRecyclerViewObserver(
        mode: LiveData<ArrayList<Media>>,
        container: View,
        recyclerView: RecyclerView,
        progress: View,
        empty: View,
        title: View,
        uiSettings: UserInterfaceSettings
    ) {

        mode.observe(viewLifecycleOwner) { items ->
            if (items != null) {
                lifecycleScope.launch {

                    withContext(Dispatchers.Main) {
                        if (items.isNotEmpty()) {
                            recyclerView.adapter = MediaAdaptor(0, items, requireActivity())
                            recyclerView.layoutManager = LinearLayoutManager(
                                requireContext(),
                                LinearLayoutManager.HORIZONTAL,
                                false
                            )
                            recyclerView.visibility = View.VISIBLE
                            empty.visibility = View.GONE
                            recyclerView.layoutAnimation =
                                LayoutAnimationController(setSlideIn(uiSettings), 0.25f)
                        } else {
                            recyclerView.visibility = View.GONE
                            empty.visibility = View.VISIBLE
                        }
                        title.visibility = View.VISIBLE
                        title.startAnimation(setSlideUp(uiSettings))
                        progress.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!model.loaded) {
            Refresh.activity[1]!!.postValue(true)
        } else {
            val updatedSettings = loadData<UserInterfaceSettings>("ui_settings") ?: UserInterfaceSettings()

            val layoutChanged = currentHomeLayoutShow?.let { previous ->
                updatedSettings.homeLayoutShow.zip(previous).any { (newVal, oldVal) -> newVal != oldVal }
            } ?: false

            if (layoutChanged) {
                val previousLayout = currentHomeLayoutShow
                currentHomeLayoutShow = updatedSettings.homeLayoutShow.toList()

                handleLayoutChangeAndFetch(updatedSettings, previousLayout)
            } else {
                applyInitialLayoutStructure(getSectionsList(), updatedSettings)
            }
        }
    }

    private fun handleLayoutChangeAndFetch(
        uiSettings: UserInterfaceSettings,
        previousLayout: List<Boolean>?
    ) {
        val sections = getSectionsList()

        applyInitialLayoutStructure(sections, uiSettings)

        lifecycleScope.launch(Dispatchers.IO) {
            val fetchJobs = sections.mapNotNull { s ->
                val isNowEnabled = uiSettings.homeLayoutShow.getOrElse(s.index) { false }
                val wasEnabled = previousLayout?.getOrElse(s.index) { false } ?: false

                if (isNowEnabled && (!wasEnabled || s.mode.value == null)) {
                    launch { s.fetch() }
                } else null
            }

            fetchJobs.joinAll()
        }
    }
}