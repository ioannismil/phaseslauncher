package com.phaseslauncher.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Dock
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.InvertColors
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.outlined.SwipeDown
import androidx.compose.material.icons.outlined.SwipeUp
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.phaseslauncher.R
import com.phaseslauncher.api.PhaseApi
import com.phaseslauncher.data.apps.AppInfo
import com.phaseslauncher.data.icons.IconPackInfo
import com.phaseslauncher.data.notifications.NotificationDotsRepository
import com.phaseslauncher.data.settings.AppearanceMode
import com.phaseslauncher.data.settings.ClockPosition
import com.phaseslauncher.data.settings.DateFormatOption
import com.phaseslauncher.data.settings.DrawerSort
import com.phaseslauncher.data.settings.IconArrangement
import com.phaseslauncher.data.settings.IconSize
import com.phaseslauncher.data.settings.LauncherPrefs
import com.phaseslauncher.data.settings.SwitcherSide
import com.phaseslauncher.data.settings.SwitcherStyle
import com.phaseslauncher.data.system.LockScreenService
import com.phaseslauncher.data.themes.Theme
import com.phaseslauncher.ui.components.label
import com.phaseslauncher.ui.home.formatDate
import com.phaseslauncher.ui.onboarding.rememberDefaultHomeState
import com.phaseslauncher.ui.theme.contentColorOn
import com.phaseslauncher.ui.theme.imageVector
import java.time.LocalDate

/** "Clock & status bar": what the top of the home screen shows, and where. */
internal fun LazyListScope.clockSection(
    prefs: LauncherPrefs,
    onUpdatePrefs: ((LauncherPrefs) -> LauncherPrefs) -> Unit,
    onChooseDateFormat: () -> Unit,
) {
    item { SectionHeader(stringResource(R.string.clock_status_bar)) }
    item {
        Text(
            if (prefs.showTime || prefs.showDate || prefs.showBattery || prefs.showThemeLabel) {
                stringResource(R.string.show_any_mix_at_the_top_of_the_home_screen)
            } else {
                stringResource(R.string.all_off_apps_use_the_whole_screen)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
    }
    item {
        SwitchOption(
            Icons.Outlined.Schedule,
            stringResource(R.string.time),
            stringResource(R.string.large_at_the_top),
            prefs.showTime,
        ) { on -> onUpdatePrefs { it.copy(showTime = on) } }
    }
    item {
        SwitchOption(
            Icons.Outlined.CalendarToday,
            stringResource(R.string.date),
            if (prefs.showTime) stringResource(R.string.under_the_time) else stringResource(R.string.at_the_top),
            prefs.showDate,
        ) { on -> onUpdatePrefs { it.copy(showDate = on) } }
    }
    item {
        ListItem(
            headlineContent = { Text(stringResource(R.string.date_format)) },
            supportingContent = { Text(formatDate(prefs.dateFormat)) },
            leadingContent = { Icon(Icons.Outlined.EditCalendar, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .clickable(enabled = prefs.showDate, onClick = onChooseDateFormat)
                .alpha(if (prefs.showDate) 1f else 0.38f)
                .padding(horizontal = 8.dp),
        )
    }
    item {
        SwitchOption(
            Icons.Outlined.BatteryStd,
            stringResource(R.string.battery_percentage),
            if (prefs.showDate) {
                stringResource(R.string.next_to_the_date)
            } else if (prefs.showTime) {
                stringResource(R.string.under_the_time)
            } else {
                stringResource(R.string.at_the_top)
            },
            prefs.showBattery,
        ) { on -> onUpdatePrefs { it.copy(showBattery = on) } }
    }
    item {
        SwitchOption(
            Icons.Outlined.Style,
            stringResource(R.string.theme_name),
            if (prefs.showThemeLabel) stringResource(R.string.pill_with_the_active_theme_tap_it_to_switch) else stringResource(R.string.hidden_use_the_edge_bar_to_switch_themes),
            prefs.showThemeLabel,
        ) { on -> onUpdatePrefs { it.copy(showThemeLabel = on) } }
    }
    item {
        val anyShown = prefs.showTime || prefs.showDate || prefs.showBattery || prefs.showThemeLabel
        Column(
            Modifier
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .alpha(if (anyShown) 1f else 0.38f),
        ) {
            Text(stringResource(R.string.position), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ClockPosition.entries.forEachIndexed { index, position ->
                    SegmentedButton(
                        selected = position == prefs.clockPosition,
                        onClick = { onUpdatePrefs { it.copy(clockPosition = position) } },
                        enabled = anyShown,
                        shape = SegmentedButtonDefaults.itemShape(index, ClockPosition.entries.size),
                    ) { Text(position.label) }
                }
            }
        }
    }
    item {
        SwitchOption(
            Icons.Outlined.ViewDay,
            stringResource(R.string.status_bar),
            if (prefs.showStatusBar) stringResource(R.string.shown_on_the_home_screen) else stringResource(R.string.hidden_swipe_down_from_the_top_edge_to_see_it),
            prefs.showStatusBar,
        ) { on -> onUpdatePrefs { it.copy(showStatusBar = on) } }
    }
}

/** The grid part of "Home screen": dock, fitting, columns, icon size, arrangement and labels. */
internal fun LazyListScope.homeGridItems(
    prefs: LauncherPrefs,
    onUpdatePrefs: ((LauncherPrefs) -> LauncherPrefs) -> Unit,
    onChooseArrangement: () -> Unit,
) {
    item {
        SwitchOption(
            Icons.Outlined.Dock,
            stringResource(R.string.dock),
            if (prefs.showDock) stringResource(R.string.favorites_at_the_bottom_of_the_home_screen) else stringResource(R.string.hidden_apps_get_the_room),
            prefs.showDock,
        ) { on -> onUpdatePrefs { it.copy(showDock = on) } }
    }
    item {
        SwitchOption(
            Icons.Outlined.FitScreen,
            stringResource(R.string.fit_icons_to_the_screen),
            if (prefs.autoFitIcons) {
                stringResource(R.string.icons_grow_or_shrink_so_each_theme_fits_without)
            } else {
                stringResource(R.string.off_fixed_columns_and_icon_size)
            },
            prefs.autoFitIcons,
        ) { on -> onUpdatePrefs { it.copy(autoFitIcons = on) } }
    }
    item {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.grid_columns), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    "${prefs.gridColumns}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            val range = LauncherPrefs.COLUMN_RANGE
            Slider(
                value = prefs.gridColumns.toFloat(),
                onValueChange = { value -> onUpdatePrefs { it.copy(gridColumns = value.toInt()) } },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = range.last - range.first - 1,
            )
            Spacer(Modifier.height(8.dp))
            // Fitted home icons size themselves; the setting still sizes the dock.
            Text(if (prefs.autoFitIcons) stringResource(R.string.dock_icon_size) else stringResource(R.string.icon_size), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                IconSize.entries.forEachIndexed { index, size ->
                    SegmentedButton(
                        selected = size == prefs.iconSize,
                        onClick = { onUpdatePrefs { it.copy(iconSize = size) } },
                        shape = SegmentedButtonDefaults.itemShape(index, IconSize.entries.size),
                    ) { Text(size.label) }
                }
            }
        }
    }
    item {
        Option(Icons.Outlined.GridView, stringResource(R.string.icon_arrangement), prefs.iconArrangement.label) {
            onChooseArrangement()
        }
    }
    item {
        SwitchOption(Icons.AutoMirrored.Outlined.Label, stringResource(R.string.app_labels), stringResource(R.string.show_names_under_icons_on_the_home_screen), prefs.showLabels) { on ->
            onUpdatePrefs { it.copy(showLabels = on) }
        }
    }
}
