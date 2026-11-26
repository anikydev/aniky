package eu.kanade.tachiyomi.ui.anilist.detail

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.dto.ALBrowseItem
import eu.kanade.tachiyomi.data.track.anilist.dto.ALStudioDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class StudioDetailScreenModel(
    private val initialStudioId: Long?,
    private val initialStudioName: String?,
    private val trackerManager: TrackerManager = Injekt.get(),
) : StateScreenModel<StudioDetailScreenModel.State>(State()) {

    enum class StudioSort(val apiSort: String) {
        POPULARITY("POPULARITY_DESC"),
        NEWEST("START_DATE_DESC"),
        SCORE("SCORE_DESC"),
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val isLoadingMore: Boolean = false,
        val error: Boolean = false,
        val details: ALStudioDetails? = null,
        val items: List<ALBrowseItem> = emptyList(),
        val sort: StudioSort = StudioSort.POPULARITY,
        val currentPage: Int = 1,
        val hasNextPage: Boolean = false,
    )

    private var fetchJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        fetch(resetItems = true)
    }

    fun retry() = fetch(resetItems = true)

    fun setSort(sort: StudioSort) {
        if (state.value.sort == sort) return
        mutableState.update { it.copy(sort = sort) }
        fetch(resetItems = false)
    }

    fun loadMore() {
        val snapshot = state.value
        if (snapshot.isLoading || snapshot.isLoadingMore || !snapshot.hasNextPage) return
        val resolvedId = snapshot.details?.id?.takeIf { it > 0L } ?: initialStudioId
        loadMoreJob?.cancel()
        mutableState.update { it.copy(isLoadingMore = true) }
        loadMoreJob = screenModelScope.launch(Dispatchers.IO) {
            try {
                val nextPage = snapshot.currentPage + 1
                val result = trackerManager.aniList.getStudioDetails(
                    studioId = resolvedId,
                    studioName = initialStudioName,
                    sort = snapshot.sort.apiSort,
                    page = nextPage,
                )
                if (result != null) {
                    val newNodes = result.media?.nodes.orEmpty()
                    val pageInfo = result.media?.pageInfo
                    mutableState.update { current ->
                        current.copy(
                            isLoadingMore = false,
                            items = (current.items + newNodes).distinctBy { it.id },
                            currentPage = pageInfo?.currentPage ?: nextPage,
                            hasNextPage = pageInfo?.hasNextPage ?: false,
                        )
                    }
                } else {
                    mutableState.update { it.copy(isLoadingMore = false, hasNextPage = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Studio loadMore failed" }
                mutableState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    private fun fetch(resetItems: Boolean) {
        fetchJob?.cancel()
        loadMoreJob?.cancel()
        val resolvedId = state.value.details?.id?.takeIf { it > 0L } ?: initialStudioId
        val activeSort = state.value.sort
        mutableState.update {
            it.copy(
                isLoading = resetItems || it.details == null,
                isLoadingMore = !resetItems && it.details != null,
                error = false,
            )
        }
        fetchJob = screenModelScope.launch(Dispatchers.IO) {
            try {
                val details = trackerManager.aniList.getStudioDetails(
                    studioId = resolvedId,
                    studioName = initialStudioName,
                    sort = activeSort.apiSort,
                    page = 1,
                )
                val nodes = details?.media?.nodes.orEmpty()
                val pageInfo = details?.media?.pageInfo
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        details = details ?: it.details,
                        items = if (details != null) nodes else it.items,
                        currentPage = pageInfo?.currentPage ?: 1,
                        hasNextPage = pageInfo?.hasNextPage ?: false,
                        error = details == null && it.details == null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Studio detail failed" }
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = it.details == null,
                    )
                }
            }
        }
    }
}
