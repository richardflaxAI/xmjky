package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "mtz_themes")
data class MtzThemeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val designer: String,
    val version: String,
    val uiVersion: String,
    val description: String,
    val archiveFileName: String,
    val extractedDirPath: String,
    val iconScale: Float,
    val iconPaddingDp: Float,
    val titleColorHex: String,
    val directIconCount: Int,
    val fancyIconCount: Int,
    val hasMask: Boolean,
    val hasBg: Boolean,
    val hasBorder: Boolean,
    val hasWallpaper: Boolean,
    val isBuiltIn: Boolean,
    val importedAtMillis: Long
)

@Entity(tableName = "workspace_shortcuts")
data class WorkspaceShortcutEntity(
    @PrimaryKey val packageName: String,
    val activityName: String,
    val label: String,
    val isDockItem: Boolean,
    val pageIndex: Int,
    val sortOrder: Int
)

@Entity(tableName = "workspace_folders")
data class WorkspaceFolderEntity(
    @PrimaryKey val id: String,
    val title: String,
    val layoutModeCode: String,
    val packageNamesCsv: String,
    val sortOrder: Int
)

@Entity(tableName = "workspace_widgets")
data class WorkspaceWidgetEntity(
    @PrimaryKey val id: String,
    val widgetTypeCode: String,
    val appWidgetId: Int,
    val providerComponent: String?,
    val customTitle: String,
    val sortOrder: Int
)

@Entity(tableName = "icon_overrides")
data class IconOverrideEntity(
    @PrimaryKey val packageName: String,
    val themeId: String,
    val assetKey: String
)

@Entity(tableName = "engine_settings")
data class EngineSettingsEntity(
    @PrimaryKey val configKey: Int = 1,
    val activeThemeId: String = "builtin_red_white_emerald",
    val useCustomScaleOverride: Boolean = false,
    val customScaleValue: Float = 0.78f,
    val enableDynamicMasking: Boolean = true,
    val enableThirdPartyColorFilter: Boolean = true,
    val enableFancyMamlIcons: Boolean = true,
    val enableIconBorderOverlay: Boolean = true,
    val enableMultiBgHashSelection: Boolean = true,
    val gridColumns: Int = 4,
    val iconSizeDp: Int = 58,
    val showAppLabels: Boolean = true,
    val showApiSourceBadges: Boolean = false
)

@Dao
interface LauncherDao {
    @Query("SELECT * FROM mtz_themes ORDER BY isBuiltIn DESC, importedAtMillis DESC")
    fun observeAllThemes(): Flow<List<MtzThemeEntity>>

    @Query("SELECT * FROM mtz_themes")
    suspend fun getAllThemesOnce(): List<MtzThemeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTheme(theme: MtzThemeEntity)

    @Query("DELETE FROM mtz_themes WHERE id = :themeId AND isBuiltIn = 0")
    suspend fun deleteCustomTheme(themeId: String)

    @Query("SELECT * FROM workspace_shortcuts ORDER BY isDockItem DESC, pageIndex ASC, sortOrder ASC")
    fun observeWorkspaceShortcuts(): Flow<List<WorkspaceShortcutEntity>>

    @Query("SELECT * FROM workspace_shortcuts")
    suspend fun getWorkspaceShortcutsOnce(): List<WorkspaceShortcutEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkspaceShortcut(shortcut: WorkspaceShortcutEntity)

    @Query("DELETE FROM workspace_shortcuts WHERE packageName = :packageName")
    suspend fun removeWorkspaceShortcut(packageName: String)

    @Query("SELECT * FROM workspace_folders ORDER BY sortOrder ASC")
    fun observeWorkspaceFolders(): Flow<List<WorkspaceFolderEntity>>

    @Query("SELECT * FROM workspace_folders ORDER BY sortOrder ASC")
    suspend fun getWorkspaceFoldersOnce(): List<WorkspaceFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkspaceFolder(folder: WorkspaceFolderEntity)

    @Query("DELETE FROM workspace_folders WHERE id = :folderId")
    suspend fun deleteWorkspaceFolder(folderId: String)

    @Query("SELECT * FROM workspace_widgets ORDER BY sortOrder ASC")
    fun observeWorkspaceWidgets(): Flow<List<WorkspaceWidgetEntity>>

    @Query("SELECT * FROM workspace_widgets ORDER BY sortOrder ASC")
    suspend fun getWorkspaceWidgetsOnce(): List<WorkspaceWidgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkspaceWidget(widget: WorkspaceWidgetEntity)

    @Query("DELETE FROM workspace_widgets WHERE id = :widgetId")
    suspend fun deleteWorkspaceWidget(widgetId: String)

    @Query("SELECT * FROM icon_overrides")
    fun observeIconOverrides(): Flow<List<IconOverrideEntity>>

    @Query("SELECT * FROM icon_overrides")
    suspend fun getIconOverridesOnce(): List<IconOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIconOverride(overrideEntity: IconOverrideEntity)

    @Query("DELETE FROM icon_overrides WHERE packageName = :packageName")
    suspend fun clearIconOverride(packageName: String)

    @Query("SELECT * FROM engine_settings WHERE configKey = 1")
    fun observeEngineSettings(): Flow<EngineSettingsEntity?>

    @Query("SELECT * FROM engine_settings WHERE configKey = 1")
    suspend fun getEngineSettingsOnce(): EngineSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEngineSettings(settings: EngineSettingsEntity)
}

@Database(
    entities = [
        MtzThemeEntity::class,
        WorkspaceShortcutEntity::class,
        WorkspaceFolderEntity::class,
        WorkspaceWidgetEntity::class,
        IconOverrideEntity::class,
        EngineSettingsEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun launcherDao(): LauncherDao

    companion object {
        @Volatile
        private var INSTANCE: LauncherDatabase? = null

        fun getInstance(context: Context): LauncherDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LauncherDatabase::class.java,
                    "hypermtz_launcher.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
