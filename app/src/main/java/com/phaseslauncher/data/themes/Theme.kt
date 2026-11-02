package com.phaseslauncher.data.themes

import android.content.pm.ApplicationInfo
import android.content.res.Resources
import androidx.annotation.StringRes
import com.phaseslauncher.R
import com.phaseslauncher.data.Labeled
import com.phaseslauncher.data.apps.AppInfo
import com.phaseslauncher.data.focus.ThemeFocus
import com.phaseslauncher.data.rules.AutoEndAction
import com.phaseslauncher.data.rules.RuleMatch
import com.phaseslauncher.data.rules.RuleSetting
import com.phaseslauncher.data.rules.ThemeAutomation
import com.phaseslauncher.data.rules.ThemeRule
import java.time.DayOfWeek

/** Icons a theme can use. Stored by name; the UI maps each to a Material icon. */
enum class ThemeIconKey {
    Gaming, Work, Social, Music, Home, Fitness, Travel, Study,
    Shopping, Photo, Code, Movie, Food, Night, Star, Heart;

    companion object {
        fun fromName(name: String): ThemeIconKey = entries.firstOrNull { it.name == name } ?: Star
    }
}

/** What happens to sound and Do Not Disturb while a theme is active. */
enum class SoundProfile(@StringRes override val labelRes: Int, val needsPolicyAccess: Boolean) : Labeled {
    Unchanged(R.string.don_t_change, false),
    Normal(R.string.sound_on, false),
    Vibrate(R.string.vibrate, false),
    Silent(R.string.silent, true),
    DndPriority(R.string.do_not_disturb_priority_only, true),
    DndAlarms(R.string.do_not_disturb_alarms_only, true);

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: Unchanged
    }
}

/** A theme's own look; null fields follow the launcher settings. */
object ThemeLook {
    /** [Theme.iconPack] value for "the apps' own icons, whatever the launcher setting". */
    const val NO_ICON_PACK = ""

    /** [Theme.gridLayout] value for "fit icons to the screen". */
    const val GRID_FIT = "fit"
}

data class Theme(
    val id: Long,
    val name: String,
    val icon: ThemeIconKey,
    /** ARGB. */
    val accentColor: Int,
    /** Assigned app keys in display order. May include apps that are currently unavailable. */
    val appKeys: List<String>,
    val wallpaperPath: String? = null,
    val soundProfile: SoundProfile = SoundProfile.Unchanged,
    val ownDock: Boolean = false,
    /** Every configured rule, including disabled ones. */
    val rules: List<RuleSetting> = emptyList(),
    /** See [ThemeLook]. */
    val iconPack: String? = null,
    /** See [ThemeLook]. */
    val gridLayout: String? = null,
    val automation: ThemeAutomation = ThemeAutomation(),
    val focus: ThemeFocus = ThemeFocus(),
) {
    /** The rules that count when switching automatically. */
    val activeRules: List<ThemeRule> get() = rules.filter { it.enabled }.map { it.rule }
}

/** A theme that hasn't been saved yet (editor and onboarding). */
data class ThemeDraft(
    val name: String,
    val icon: ThemeIconKey,
    val accentColor: Int,
    /** In display order. */
    val appKeys: List<String> = emptyList(),
    val wallpaperPath: String? = null,
    val soundProfile: SoundProfile = SoundProfile.Unchanged,
    val ownDock: Boolean = false,
    val rules: List<RuleSetting> = emptyList(),
    val iconPack: String? = null,
    val gridLayout: String? = null,
    val automation: ThemeAutomation = ThemeAutomation(),
    val focus: ThemeFocus = ThemeFocus(),
)

fun Theme.toDraft() =
    ThemeDraft(name, icon, accentColor, appKeys, wallpaperPath, soundProfile, ownDock, rules, iconPack, gridLayout, automation, focus)

data class ThemePreset(
    @StringRes val nameRes: Int,
    val icon: ThemeIconKey,
    val accentColor: Int,
    /** Play Store categories ([ApplicationInfo.category]) whose apps are suggested for this theme. */
    val categories: Set<Int> = emptySet(),
    /** Well-known apps that often don't declare a category. */
    val packages: Set<String> = emptySet(),
    /** Starting rules; the user can change or remove them. */
    val rules: List<ThemeRule> = emptyList(),
    val automation: ThemeAutomation = ThemeAutomation(),
    val soundProfile: SoundProfile = SoundProfile.Unchanged,
    /** Offered during setup even without matching apps (e.g. a bedtime theme). */
    val alwaysOffer: Boolean = false,
) {
    fun suggest(apps: List<AppInfo>): List<AppInfo> =
        apps.filter { it.category in categories || it.packageName in packages }

    fun toDraft(res: Resources, appKeys: List<String>) = ThemeDraft(
        name = res.getString(nameRes),
        icon = icon,
        accentColor = accentColor,
        appKeys = appKeys,
        soundProfile = soundProfile,
        rules = rules.map { RuleSetting(it) },
        automation = automation,
    )
}

object ThemeDefaults {
    const val MAX_FAVORITES = 5

    /** Accent choices offered in the theme editor; readable on both light and dark wallpapers. */
    val AccentColors: List<Int> = listOf(
        0xFF7C4DFF, 0xFF448AFF, 0xFF00B8D4, 0xFF00BFA5, 0xFF64DD17,
        0xFFFFAB00, 0xFFFF6D00, 0xFFFF1744, 0xFFF50057, 0xFF8D6E63,
    ).map { it.toInt() }

    val Presets: List<ThemePreset> = listOf(
        ThemePreset(
            nameRes = R.string.gaming,
            icon = ThemeIconKey.Gaming,
            accentColor = AccentColors[0],
            categories = setOf(ApplicationInfo.CATEGORY_GAME),
            packages = setOf(
                "com.google.android.play.games",
                "com.samsung.android.game.gamehome",
                "com.discord",
                "com.valvesoftware.android.steam.community",
                "tv.twitch.android.app",
            ),
            rules = listOf(ThemeRule.Controller),
        ),
        ThemePreset(
            nameRes = R.string.work,
            icon = ThemeIconKey.Work,
            accentColor = AccentColors[1],
            categories = setOf(ApplicationInfo.CATEGORY_PRODUCTIVITY),
            packages = setOf(
                "com.google.android.gm",
                "com.google.android.calendar",
                "com.samsung.android.calendar",
                "com.google.android.apps.docs",
                "com.google.android.apps.docs.editors.docs",
                "com.google.android.apps.docs.editors.sheets",
                "com.google.android.apps.docs.editors.slides",
                "com.microsoft.office.outlook",
                "com.microsoft.teams",
                "com.microsoft.office.officehubrow",
                "com.Slack",
                "us.zoom.videomeetings",
                "notion.id",
                "com.linkedin.android",
            ),
            rules = listOf(ThemeRule.WORK_HOURS),
        ),
        ThemePreset(
            nameRes = R.string.social,
            icon = ThemeIconKey.Social,
            accentColor = AccentColors[8],
            categories = setOf(ApplicationInfo.CATEGORY_SOCIAL),
            packages = setOf(
                "com.whatsapp",
                "com.instagram.android",
                "com.facebook.katana",
                "com.facebook.orca",
                "org.telegram.messenger",
                "org.thoughtcrime.securesms",
                "com.twitter.android",
                "com.snapchat.android",
                "com.reddit.frontpage",
                "com.zhiliaoapp.musically",
                "com.viber.voip",
                "com.bereal.ft",
            ),
        ),
        ThemePreset(
            nameRes = R.string.entertainment,
            icon = ThemeIconKey.Movie,
            accentColor = AccentColors[7],
            categories = setOf(ApplicationInfo.CATEGORY_VIDEO),
            packages = setOf(
                "com.google.android.youtube",
                "com.netflix.mediaclient",
                "com.disney.disneyplus",
                "com.amazon.avod.thirdpartyclient",
                "com.hbo.hbonow",
                "tv.twitch.android.app",
            ),
        ),
        ThemePreset(
            nameRes = R.string.music,
            icon = ThemeIconKey.Music,
            accentColor = AccentColors[3],
            categories = setOf(ApplicationInfo.CATEGORY_AUDIO),
            packages = setOf(
                "com.spotify.music",
                "com.google.android.apps.youtube.music",
                "com.apple.android.music",
                "deezer.android.app",
                "com.soundcloud.android",
                "com.google.android.apps.podcasts",
                "au.com.shiftyjelly.pocketcasts",
                "com.audible.application",
            ),
            rules = listOf(ThemeRule.Headphones),
        ),
        ThemePreset(
            nameRes = R.string.travel,
            icon = ThemeIconKey.Travel,
            accentColor = AccentColors[2],
            categories = setOf(ApplicationInfo.CATEGORY_MAPS),
            packages = setOf(
                "com.google.android.apps.maps",
                "com.waze",
                "com.ubercab",
                "com.airbnb.android",
                "com.booking",
                "com.google.android.apps.translate",
            ),
        ),
        ThemePreset(
            nameRes = R.string.news,
            icon = ThemeIconKey.Study,
            accentColor = AccentColors[9],
            categories = setOf(ApplicationInfo.CATEGORY_NEWS),
            packages = setOf(
                "com.google.android.apps.magazines",
                "flipboard.app",
                "com.medium.reader",
                "com.amazon.kindle",
                "com.google.android.apps.books",
            ),
        ),
        ThemePreset(
            nameRes = R.string.photos,
            icon = ThemeIconKey.Photo,
            accentColor = AccentColors[5],
            categories = setOf(ApplicationInfo.CATEGORY_IMAGE),
            packages = setOf(
                "com.google.android.apps.photos",
                "com.google.android.GoogleCamera",
                "com.sec.android.app.camera",
                "com.adobe.lrmobile",
                "com.niksoftware.snapseed",
            ),
        ),
        ThemePreset(
            nameRes = R.string.bedtime,
            icon = ThemeIconKey.Night,
            accentColor = AccentColors[0],
            packages = setOf(
                "com.google.android.deskclock",
                "com.sec.android.app.clockpackage",
                "com.calm.android",
                "com.getsomeheadspace.android",
            ),
            // On the charger at night: both have to hold.
            rules = listOf(ThemeRule.Charging, ThemeRule.Schedule(DayOfWeek.entries.toSet(), 22 * 60, 7 * 60)),
            automation = ThemeAutomation(match = RuleMatch.All, onEnd = AutoEndAction.SwitchBack),
            soundProfile = SoundProfile.DndAlarms,
            alwaysOffer = true,
        ),
    )
}
