package eu.kanade.tachiyomi.ui.browse.seasonal

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.dto.ALCharacterDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CharacterDetailScreenModel(
    private val characterId: Long,
    private val trackerManager: TrackerManager = Injekt.get(),
) : StateScreenModel<CharacterDetailScreenModel.State>(State()) {

    data class State(
        val isLoading: Boolean = true,
        val character: ALCharacterDetails? = null,
        val error: Boolean = false,
    )

    private var fetchJob: Job? = null

    init {
        fetchCharacter()
    }

    fun retry() {
        fetchCharacter()
    }

    private fun fetchCharacter() {
        // Cancel any in-flight fetch so a stale response can never overwrite a fresh one.
        fetchJob?.cancel()
        mutableState.update { it.copy(isLoading = true, error = false) }
        fetchJob = screenModelScope.launch(Dispatchers.IO) {
            try {
                val character = trackerManager.aniList.getCharacterDetails(characterId)
                mutableState.update {
                    it.copy(isLoading = false, character = character, error = character == null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, throwable = e) { "AniList character detail: failed to fetch" }
                mutableState.update { it.copy(isLoading = false, error = true) }
            }
        }
    }
}
