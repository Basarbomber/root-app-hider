package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.HiddenAppEntity
import com.example.data.HiddenAppRepository
import com.example.model.AppItem
import com.example.util.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TermLog(
    val timestamp: Long = System.currentTimeMillis(),
    val command: String,
    val stdout: String,
    val stderr: String,
    val success: Boolean,
    val type: LogType
) {
    enum class LogType { INFO, ROOT_CMD, ERROR, SUCCESS }
}

class HiderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: HiddenAppRepository
    
    // UI flows
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _rootVerified = MutableStateFlow(false)
    val rootVerified = _rootVerified.asStateFlow()

    private val _shellLogs = MutableStateFlow<List<TermLog>>(emptyList())
    val shellLogs = _shellLogs.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppItem>>(emptyList())
    val installedApps = _installedApps.asStateFlow()

    private val _hiddenApps = MutableStateFlow<List<AppItem>>(emptyList())
    val hiddenApps = _hiddenApps.asStateFlow()

    // Shared list of all system-queried apps
    private val _allQueriedApps = MutableStateFlow<List<AppItem>>(emptyList())

    init {
        val database = AppDatabase.getDatabase(application)
        repository = HiddenAppRepository(database.hiddenAppDao())
        
        // Add initial welcome logs to CLI console
        addLog(
            command = "init_stealth_engine",
            stdout = "Stealth App Hider Engine v1.0.0 initialized successfully.\nReady for secure execution.",
            stderr = "",
            success = true,
            type = TermLog.LogType.INFO
        )

        // Verify root status on a background thread
        checkRootAccess()

        // Sync lists
        viewModelScope.launch {
            // Combine Database entries & system apps whenever either updates
            combine(
                _allQueriedApps,
                repository.allHiddenApps,
                _searchQuery
            ) { systemApps, dbHiddenApps, query ->
                Triple(systemApps, dbHiddenApps, query)
            }.collect { (systemApps, dbHiddenApps, query) ->
                processAppLists(systemApps, dbHiddenApps, query)
            }
        }

        // Run initial package fetch
        fetchPackages()
    }

    private fun checkRootAccess() {
        viewModelScope.launch(Dispatchers.IO) {
            val rootAvailable = RootUtils.isRootAvailable()
            _rootVerified.value = rootAvailable
            if (rootAvailable) {
                addLog(
                    command = "check_superuser",
                    stdout = "Superuser (su) access: GRANTED.\nSecure package manager modifications enabled.",
                    stderr = "",
                    success = true,
                    type = TermLog.LogType.SUCCESS
                )
            } else {
                addLog(
                    command = "check_superuser",
                    stdout = "Superuser (su) access: ABSENT. Operating in Simulated Safe Mode.",
                    stderr = "Running without full root permissions. System hiding/freezing will operate via safe-vault simulation logs.",
                    success = false,
                    type = TermLog.LogType.INFO
                )
            }
        }
    }

    fun addLog(command: String, stdout: String, stderr: String, success: Boolean, type: TermLog.LogType) {
        val newLog = TermLog(
            command = command,
            stdout = stdout,
            stderr = stderr,
            success = success,
            type = type
        )
        _shellLogs.update { current -> listOf(newLog) + current }
    }

    fun clearLogs() {
        _shellLogs.value = emptyList()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun fetchPackages() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                val pm = getApplication<Application>().packageManager
                // MATCH_DISABLED_COMPONENTS and MATCH_UNINSTALLED_PACKAGES let us locate disabled/uninstalled apps!
                val flags = PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES
                val packages = pm.getInstalledPackages(flags)

                val appsList = ArrayList<AppItem>()
                // Fetch our own package so we don't allow hiding oneself
                val selfPackageName = getApplication<Application>().packageName

                for (pack in packages) {
                    val appInfo = pack.applicationInfo ?: continue
                    
                    // Filter: Skip system apps, keep only third-party & user installed apps
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    if (isSystem) continue

                    // Also never list ourselves to prevent user locking themselves out
                    if (pack.packageName == selfPackageName) continue

                    val appName = appInfo.loadLabel(pm).toString()
                    val icon = appInfo.loadIcon(pm)
                    val isSystemDisabled = !appInfo.enabled

                    appsList.add(
                        AppItem(
                            packageName = pack.packageName,
                            appName = appName,
                            isSystemApp = false,
                            isHiddenInDb = false, // matching later
                            isSystemDisabled = isSystemDisabled,
                            icon = icon,
                            lastUpdateTime = pack.lastUpdateTime,
                            versionName = pack.versionName ?: "1.0"
                        )
                    )
                }

                // Sort by name
                appsList.sortBy { it.appName.lowercase() }
                _allQueriedApps.value = appsList

            } catch (e: Exception) {
                addLog(
                    command = "get_installed_packages",
                    stdout = "",
                    stderr = e.localizedMessage ?: "Failed to read application manager packages",
                    success = false,
                    type = TermLog.LogType.ERROR
                )
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun processAppLists(
        systemApps: List<AppItem>,
        dbHiddenApps: List<HiddenAppEntity>,
        query: String
    ) {
        val hiddenPackageNames = dbHiddenApps.map { it.packageName }.toSet()
        
        // Match system configurations with our internal Room DB configurations
        val matchedApps = systemApps.map { app ->
            val isHiddenInDatabase = hiddenPackageNames.contains(app.packageName)
            app.copy(isHiddenInDb = isHiddenInDatabase)
        }

        // Apply search filter if query is not blank
        val filteredApps = if (query.isBlank()) {
            matchedApps
        } else {
            matchedApps.filter {
                it.appName.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
        }

        // Partition into:
        // 1. Installed/Visible: App is neither system-disabled nor flagged in DB as hidden
        // 2. Hidden/Stealth Apps: App is either flagged as hidden in DB or completely system-disabled
        val (hidden, visible) = filteredApps.partition {
            it.isHiddenInDb || it.isSystemDisabled
        }

        _installedApps.value = visible
        _hiddenApps.value = hidden
    }

    fun toggleAppStealth(app: AppItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val dbMatch = repository.getHiddenApp(app.packageName)
            val isCurrentlyStealth = dbMatch != null || app.isSystemDisabled

            if (isCurrentlyStealth) {
                // Reveal App
                val hasRoot = _rootVerified.value
                val cmd = "pm enable ${app.packageName}"
                
                addLog(
                    command = "unhide_app_init",
                    stdout = "Initiating restoration for component: ${app.packageName}...",
                    stderr = "",
                    success = true,
                    type = TermLog.LogType.INFO
                )

                if (hasRoot) {
                    val result = RootUtils.executeAsRoot(cmd)
                    addLog(result.command, result.output, result.error, result.success, TermLog.LogType.ROOT_CMD)
                    
                    if (result.success) {
                        repository.delete(app.packageName)
                        addLog(
                            command = "sys_sync",
                            stdout = "SUCCESS: ${app.appName} restored from system stealth vault. Icon returned to home screen.",
                            stderr = "",
                            success = true,
                            type = TermLog.LogType.SUCCESS
                        )
                    } else {
                        // DB Fallback if root command fails
                        repository.delete(app.packageName)
                        addLog(
                            command = "fallback_revealer",
                            stdout = "WARNING: Root PM restoration failed. Removed local db stealth hook.",
                            stderr = result.error,
                            success = false,
                            type = TermLog.LogType.ERROR
                        )
                    }
                } else {
                    // Safe / Demo flow
                    repository.delete(app.packageName)
                    addLog(
                        command = cmd,
                        stdout = "(SIMULATED SUCCESS) App state set to visible. Component re-enabled in local database.\nCommand that would run: 'su -c pm enable ${app.packageName}'",
                        stderr = "",
                        success = true,
                        type = TermLog.LogType.SUCCESS
                    )
                }
            } else {
                // Hide App
                val hasRoot = _rootVerified.value
                val cmd = "pm disable-user --user 0 ${app.packageName}"
                
                addLog(
                    command = "hide_app_init",
                    stdout = "Initiating stealth-masking for component: ${app.packageName}...",
                    stderr = "",
                    success = true,
                    type = TermLog.LogType.INFO
                )

                if (hasRoot) {
                    val result = RootUtils.executeAsRoot(cmd)
                    addLog(result.command, result.output, result.error, result.success, TermLog.LogType.ROOT_CMD)
                    
                    if (result.success) {
                        repository.insert(
                            HiddenAppEntity(
                                packageName = app.packageName,
                                appName = app.appName,
                                isSimulated = false
                            )
                        )
                        addLog(
                            command = "sys_sync",
                            stdout = "SUCCESS: ${app.appName} successfully frozen and masked completely. Locked inside root stealth vault.",
                            stderr = "",
                            success = true,
                            type = TermLog.LogType.SUCCESS
                        )
                    } else {
                        // DB Fallback
                        repository.insert(
                            HiddenAppEntity(
                                packageName = app.packageName,
                                appName = app.appName,
                                isSimulated = true
                            )
                        )
                        addLog(
                            command = "fallback_vault_hook",
                            stdout = "WARNING: System masking failed. Stored in internal simulator vault.",
                            stderr = result.error,
                            success = false,
                            type = TermLog.LogType.ERROR
                        )
                    }
                } else {
                    // Simulated stealth hook
                    repository.insert(
                        HiddenAppEntity(
                            packageName = app.packageName,
                            appName = app.appName,
                            isSimulated = true
                        )
                    )
                    addLog(
                        command = cmd,
                        stdout = "(SIMULATED SUCCESS) App status written to local room-db vault successfully.\nLauncher hiding complete.\nCommand that would run: 'su -c pm disable-user --user 0 ${app.packageName}'",
                        stderr = "",
                        success = true,
                        type = TermLog.LogType.SUCCESS
                    )
                }
            }
            
            // Refetch package manager results to stay in sync
            fetchPackages()
        }
    }
}
