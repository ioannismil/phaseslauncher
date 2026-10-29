package com.themelauncher.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "themes")
data class ThemeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** A [com.themelauncher.data.themes.ThemeIconKey] name. */
    @ColumnInfo(name = "icon_key") val iconKey: String,
    /** ARGB. */
    @ColumnInfo(name = "accent_color") val accentColor: Int,
    /** Order in the theme switcher, ascending. */
    val position: Int,
)

/** An app assigned to a theme. An app may be in several themes. */
@Entity(
    tableName = "theme_apps",
    primaryKeys = ["theme_id", "app_key"],
    foreignKeys = [
        ForeignKey(
            entity = ThemeEntity::class,
            parentColumns = ["id"],
            childColumns = ["theme_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("app_key")],
)
data class ThemeAppEntity(
    @ColumnInfo(name = "theme_id") val themeId: Long,
    @ColumnInfo(name = "app_key") val appKey: String,
    val position: Int,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey @ColumnInfo(name = "app_key") val appKey: String,
    val position: Int,
)
