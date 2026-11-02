package com.phaseslauncher.api

import android.content.Intent
import android.net.Uri
import com.phaseslauncher.AppContainer
import com.phaseslauncher.data.themes.Theme
import kotlinx.coroutines.flow.first

/**
 * How other apps switch phases: automation apps (Tasker, MacroDroid, Samsung Modes and Routines),
 * shortcuts and adb. A request names the phase by [EXTRA_PHASE] (its name, any case) or
 * [EXTRA_PHASE_ID]; links carry the same as query parameters: `phases://switch?phase=Work`.
 *
 * The switch is like picking the phase yourself: rules that apply right now don't undo it, and the
 * phase's wallpaper and sound apply. It isn't counted toward schedule suggestions, since the
 * automation app already runs on a schedule of its own.
 */
object PhaseApi {
    /** Broadcast action, handled by [SwitchPhaseReceiver] when "Other apps can switch phases" is on. */
    const val ACTION_SWITCH_PHASE = "com.phaseslauncher.action.SWITCH_PHASE"
    const val EXTRA_PHASE = "phase"
    const val EXTRA_PHASE_ID = "phase_id"

    const val LINK_SCHEME = "phases"
    const val LINK_HOST = "switch"

    /** What the request asks for: an id, a name, or nothing usable. */
    sealed interface Target {
        data class Id(val id: Long) : Target
        data class Name(val name: String) : Target
    }

    fun target(intent: Intent): Target? {
        val uri = intent.data?.takeIf { it.scheme == LINK_SCHEME && it.host == LINK_HOST }
        val id = uri?.getQueryParameter(EXTRA_PHASE_ID)?.toLongOrNull()
            ?: intent.getLongExtra(EXTRA_PHASE_ID, -1).takeIf { it >= 0 }
            // Tasker sends extras as strings unless told otherwise.
            ?: intent.getStringExtra(EXTRA_PHASE_ID)?.trim()?.toLongOrNull()
        if (id != null) return Target.Id(id)
        val name = uri?.getQueryParameter(EXTRA_PHASE) ?: intent.getStringExtra(EXTRA_PHASE)
        return name?.trim()?.takeIf { it.isNotEmpty() }?.let(Target::Name)
    }

    /** The phase [target] names: by id, else the first whose name matches ignoring case and spaces at the ends. */
    fun resolve(themes: List<Theme>, target: Target): Theme? = when (target) {
        is Target.Id -> themes.firstOrNull { it.id == target.id }
        is Target.Name -> themes.firstOrNull { it.name.trim().equals(target.name, ignoreCase = true) }
    }

    /** A link that switches to [theme], for shortcuts and for copying into automation apps. */
    fun link(theme: Theme): Uri =
        Uri.Builder().scheme(LINK_SCHEME).authority(LINK_HOST).appendQueryParameter(EXTRA_PHASE_ID, theme.id.toString()).build()

    sealed interface Result {
        data class Switched(val theme: Theme) : Result
        data object NoPhaseGiven : Result
        data class NotFound(val target: Target) : Result
    }

    suspend fun switch(container: AppContainer, intent: Intent): Result {
        val target = target(intent) ?: return Result.NoPhaseGiven
        val theme = resolve(container.themeRepository.themes.first(), target) ?: return Result.NotFound(target)
        container.settingsRepository.setActiveThemeId(theme.id)
        return Result.Switched(theme)
    }
}
