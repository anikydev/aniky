package eu.kanade.tachiyomi.ui.anilist.detail

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.dto.ALStaffDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class StaffDetailScreenModel(
    private val staffId: Long,
    private val trackerManager: TrackerManager = Injekt.get(),
) : StateScreenModel<StaffDetailScreenModel.State>(State()) {

    data class State(
        val isLoading: Boolean = true,
        val details: ALStaffDetails? = null,
        val error: Boolean = false,
    )

    private var fetchJob: Job? = null

    init {
        fetch()
    }

    fun retry() = fetch()

    private fun fetch() {
        fetchJob?.cancel()
        mutableState.update { it.copy(isLoading = true, error = false) }
        fetchJob = screenModelScope.launch(Dispatchers.IO) {
            try {
                val details = trackerManager.aniList.getStaffDetails(staffId)
                mutableState.update { it.copy(isLoading = false, details = details, error = details == null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Staff detail failed" }
                mutableState.update { it.copy(isLoading = false, error = true) }
            }
        }
    }
}
