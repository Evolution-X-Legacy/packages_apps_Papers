/*
 * Copyright (c) 2019. Jahir Fiquitiva
 *
 * Licensed under the CreativeCommons Attribution-ShareAlike
 * 4.0 International License. You may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *    http://creativecommons.org/licenses/by-sa/4.0/legalcode
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jahirfiquitiva.libs.frames.ui.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.preference.*
import android.view.Menu
import android.view.MenuItem
import android.widget.ListView
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import ca.allanwang.kau.utils.openLink
import ca.allanwang.kau.utils.snackbar
import ca.allanwang.kau.utils.toast
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.files.folderChooser
import com.bumptech.glide.Glide
import com.fondesa.kpermissions.extension.listeners
import com.fondesa.kpermissions.extension.permissionsBuilder
import com.fondesa.kpermissions.request.runtime.nonce.PermissionNonce
import com.google.android.material.snackbar.Snackbar
import de.psdev.licensesdialog.LicenseResolver
import de.psdev.licensesdialog.LicensesDialog
import de.psdev.licensesdialog.licenses.License
import jahirfiquitiva.libs.archhelpers.extensions.mdDialog
import jahirfiquitiva.libs.frames.R
import jahirfiquitiva.libs.frames.data.models.db.FavoritesDatabase
import jahirfiquitiva.libs.frames.helpers.extensions.clearDataAndCache
import jahirfiquitiva.libs.frames.helpers.extensions.dataCacheSize
import jahirfiquitiva.libs.frames.helpers.extensions.getToolbarIconsColorFor
import jahirfiquitiva.libs.frames.helpers.utils.DATABASE_NAME
import jahirfiquitiva.libs.frames.helpers.utils.FL
import jahirfiquitiva.libs.frames.helpers.utils.FramesKonfigs
import jahirfiquitiva.libs.frames.ui.adapters.CreditsAdapter
import jahirfiquitiva.libs.frames.ui.adapters.viewholders.Credit
import jahirfiquitiva.libs.frames.ui.widgets.CustomToolbar
import jahirfiquitiva.libs.frames.ui.widgets.EmptyViewRecyclerView
import jahirfiquitiva.libs.kext.extensions.*
import jahirfiquitiva.libs.kext.helpers.DARK
import jahirfiquitiva.libs.kext.helpers.LIGHT
import jahirfiquitiva.libs.kext.ui.activities.ThemedActivity
import org.jetbrains.anko.doAsync
import java.io.File

open class CreditsActivity : ThemedActivity<FramesKonfigs>() {
    
    override val prefs: FramesKonfigs by lazy { FramesKonfigs(this) }
    override fun lightTheme(): Int = R.style.LightTheme
    override fun darkTheme(): Int = R.style.DarkTheme
//    override fun amoledTheme(): Int = R.style.AmoledTheme
//    override fun transparentTheme(): Int = R.style.TransparentTheme
    
    override fun autoTintStatusBar(): Boolean = true
    override fun autoTintNavigationBar(): Boolean = true

    private val preferenceManager: PreferenceManager by lazy {
        val c = PreferenceManager::class.java.getDeclaredConstructor(Activity::class.java, Int::class.javaPrimitiveType).apply { isAccessible = true }
        c.newInstance(this, 100)
    }
    private val preferenceScreen: PreferenceScreen by lazy {
        val m = preferenceManager::class.java.getDeclaredMethod(
                "inflateFromResource",
                Context::class.java,
                Int::class.javaPrimitiveType,
                PreferenceScreen::class.java
        )
        m.invoke(preferenceManager, this, R.xml.preferences, null) as PreferenceScreen
    }
    private var dialog: MaterialDialog? = null
    private var database: FavoritesDatabase? = null
    private var downloadLocation: Preference? = null
    private var hasClearedFavs = false
    
    private val toolbar: CustomToolbar? by bind(R.id.toolbar)
    private val preferenceList: ListView? by bind(R.id.pref_list)
    private val recyclerView: EmptyViewRecyclerView? by bind(R.id.list_rv)

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_credits)
        
        registerExtraLicenses()
        
        toolbar?.bindToActivity(this)
        supportActionBar?.title = getString(R.string.about)

        initPreferences()
        initDatabase()
        
        val refreshLayout: SwipeRefreshLayout? by bind(R.id.swipe_to_refresh)
        refreshLayout?.isEnabled = false
        
        recyclerView?.itemAnimator = DefaultItemAnimator()
        recyclerView?.state = EmptyViewRecyclerView.State.LOADING
        
        val layoutManager =
            GridLayoutManager(this, if (isInHorizontalMode) 2 else 1, RecyclerView.VERTICAL, false)
        recyclerView?.layoutManager = layoutManager
        
        val adapter = CreditsAdapter(getDashboardTitle(), Glide.with(this), buildCreditsList())
        adapter.setLayoutManager(layoutManager)
        recyclerView?.adapter = adapter
        recyclerView?.isNestedScrollingEnabled = false
        
        try {
            adapter.collapseSection(1)
            adapter.collapseSection(2)
            adapter.collapseSection(3)
            adapter.collapseSection(4)
        } catch (ignored: Exception) {
        }
        
        recyclerView?.state = EmptyViewRecyclerView.State.NORMAL
    }
    
    @StringRes
    open fun getDashboardTitle() = R.string.frames_dashboard
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.about_settings_menu, menu)
        toolbar?.tint(
            getPrimaryTextColorFor(primaryColor),
            getSecondaryTextColorFor(primaryColor),
            getToolbarIconsColorFor(primaryColor))
        return super.onCreateOptionsMenu(menu)
    }
    
    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        item?.let {
            when (it.itemId) {
                android.R.id.home -> finish()
/*
                R.id.translate -> try {
                    openLink(getTranslationSite())
                } catch (ignored: Exception) {
                }
*/
                R.id.licenses -> LicensesDialog.Builder(this)
                    .setTitle(R.string.licenses)
                    .setNotices(R.raw.notices)
                    .setShowFullLicenseText(false)
                    .setIncludeOwnLicense(false)
                    .setDividerColor(dividerColor)
                    .build().show()
                else -> {
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        doFinish()
        super.onBackPressed()
    }
    
    open fun getTranslationSite(): String = "http://j.mp/Trnsl8Frames"
    
    private fun registerExtraLicenses() {
        val ccLicense = object : License() {
            override fun getName(): String =
                "CreativeCommons Attribution-ShareAlike 4.0 International License"
            
            override fun readSummaryTextFromResources(context: Context): String =
                readFullTextFromResources(context)
            
            override fun readFullTextFromResources(context: Context): String =
                "\tLicensed under the CreativeCommons Attribution-ShareAlike\n\t4.0 " +
                    "International License. You may not use this file except in compliance \n" +
                    "\twith the License. You may obtain a copy of the License at\n\n\t\t" +
                    "http://creativecommons.org/licenses/by-sa/4.0/legalcode\n\n" +
                    "\tUnless required by applicable law or agreed to in writing, software\n" +
                    "\tdistributed under the License is distributed on an \"AS IS\" BASIS,\n" +
                    "\tWITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
                    "\tSee the License for the specific language governing permissions and\n" +
                    "\tlimitations under the License."
            
            override fun getVersion(): String = "4.0"
            
            override fun getUrl(): String =
                "http://creativecommons.org/licenses/by-sa/4.0/legalcode"
        }
        
        val eclLicense = object : License() {
            override fun getName(): String = "Educational Community License v2.0"
            
            override fun readSummaryTextFromResources(context: Context): String =
                readFullTextFromResources(context)
            
            override fun readFullTextFromResources(context: Context): String =
                "The Educational Community License version 2.0 (\"ECL\") consists of the " +
                    "Apache 2.0 license, modified to change the scope of the patent grant in " +
                    "section 3 to be specific to the needs of the education communities " +
                    "using this license.\n\nLicensed under the Apache License, Version 2.0 " +
                    "(the \"License\");\n" + "you may not use this file except in compliance with " +
                    "the License.\nYou may obtain a copy of the License at\n\n\t" +
                    "http://www.apache.org/licenses/LICENSE-2.0\n\nUnless required by applicable " +
                    "law or agreed to in writing, software\ndistributed under the License is " +
                    "distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY " +
                    "KIND, either express or implied.\nSee the License for the specific " +
                    "language governing permissions and\nlimitations under the License."
            
            override fun getVersion(): String = "2.0"
            
            override fun getUrl(): String = "https://opensource.org/licenses/ECL-2.0"
        }
        LicenseResolver.registerLicense(ccLicense)
        LicenseResolver.registerLicense(eclLicense)
    }
    
    private fun buildCreditsList(): ArrayList<Credit> {
        val list = ArrayList<Credit>()
        
        try {
            val titles = stringArray(R.array.credits_titles) ?: arrayOf("")
            val descriptions = stringArray(R.array.credits_descriptions) ?: arrayOf("")
            val photos = stringArray(R.array.credits_photos) ?: arrayOf("")
            val buttons = stringArray(R.array.credits_buttons) ?: arrayOf("")
            val links = stringArray(R.array.credits_links) ?: arrayOf("")
            
            if (descriptions.size == titles.size && photos.size == titles.size) {
                for (it in 0 until titles.size) {
                    val title = titles[it]
                    if (title.hasContent()) {
                        list += Credit(
                            title, photos[it], Credit.Type.CREATOR,
                            description = descriptions[it],
                            buttonsTitles = buttons[it].split("|"),
                            buttonsLinks = links[it].split("|"))
                    }
                }
            }
            
            list.add(
                Credit(
                    "Jahir Fiquitiva", JAHIR_PHOTO_URL, Credit.Type.DASHBOARD,
                    description = getString(R.string.dashboard_copyright),
                    buttonsTitles = JAHIR_BUTTONS.split("|"),
                    buttonsLinks = JAHIR_LINKS.split("|")))
            
            list.add(
                Credit(
                    "Allan Wang", ALLAN_PHOTO_URL, Credit.Type.DASHBOARD,
                    description = getString(R.string.allan_description),
                    buttonsTitles = ALLAN_BUTTONS.split("|"),
                    buttonsLinks = ALLAN_LINKS.split("|")))
            
            list.add(
                Credit(
                    "Sherry Sabatine", SHERRY_PHOTO_URL, Credit.Type.DASHBOARD,
                    description = getString(R.string.sherry_description),
                    buttonsTitles = SHERRY_BUTTONS.split("|"),
                    buttonsLinks = SHERRY_LINKS.split("|")))
            
            list.addAll(Credit.EXTRA_CREDITS)
        } catch (e: Exception) {
            FL.e(e.message)
        }
        return list
    }

    private fun findPreference(key: String) = preferenceScreen.findPreference(key)

    private val request by lazy {
        permissionsBuilder(Manifest.permission.WRITE_EXTERNAL_STORAGE).build()
    }

    private fun requestStoragePermission(explanation: String, whenAccepted: () -> Unit) {
        try {
            request.detachAllListeners()
        } catch (e: Exception) {
        }
        request.listeners {
            onAccepted { whenAccepted() }
            onDenied {
                snackbar(R.string.permission_denied, duration = Snackbar.LENGTH_LONG)
            }
            onPermanentlyDenied {
                snackbar(R.string.permission_denied_completely, duration = Snackbar.LENGTH_LONG)
            }
            onShouldShowRationale { _, nonce -> showPermissionInformation(explanation, nonce) }
        }
        request.send()
    }

    private fun initDatabase() {
        if (boolean(R.bool.isFrames) && database == null) {
            database = Room.databaseBuilder(
                    this, FavoritesDatabase::class.java,
                    DATABASE_NAME).fallbackToDestructiveMigration().build()
        }
    }

    open fun initPreferences() {
        preferenceScreen.bind(preferenceList)

        val uiPrefs = findPreference("ui_settings") as? PreferenceCategory

        val themePref = findPreference("theme") as? SwitchPreference
        themePref?.setOnPreferenceChangeListener { _, result ->
            val isDark = result as Boolean
            prefs.currentTheme = if (isDark) DARK else LIGHT
            onThemeChanged()
            true
        }

        val columns = findPreference("columns")
        if (boolean(R.bool.isFrames)) {
            columns?.setOnPreferenceClickListener {
                clearDialog()
                val currentColumns = prefs.columns - 1
                dialog = mdDialog {
                    title(R.string.wallpapers_columns_setting_title)
                    itemsSingleChoice(arrayOf(1, 2, 3), currentColumns) { _, which, _ ->
                        if (which != currentColumns) prefs.columns = which + 1
                    }
                    positiveButton(android.R.string.ok)
                    negativeButton(android.R.string.cancel)
                }
                dialog?.show()
                false
            }
        } else {
            uiPrefs?.removePreference(columns)
        }

        val storagePrefs = findPreference("storage_settings") as? PreferenceCategory

        downloadLocation = findPreference("wallpapers_download_location")
        updateDownloadLocation()
        downloadLocation?.setOnPreferenceClickListener {
            requestPermission()
            true
        }

        val clearData = findPreference("clear_data")
        clearData?.summary = getString(R.string.data_cache_setting_content, dataCacheSize)
        clearData?.setOnPreferenceClickListener {
            clearDialog()
            dialog = mdDialog {
                title(R.string.data_cache_setting_title)
                message(R.string.data_cache_confirmation)
                negativeButton(android.R.string.cancel)
                positiveButton(android.R.string.ok) {
                    clearDataAndCache()
                    clearData.summary = getString(
                            R.string.data_cache_setting_content,
                            dataCacheSize)
                }
            }
            dialog?.show()
            true
        }

        val clearDatabase = findPreference("clear_database")
        if (boolean(R.bool.isFrames)) {
            clearDatabase?.setOnPreferenceClickListener {
                clearDialog()
                dialog = mdDialog {
                    title(R.string.clear_favorites_setting_title)
                    message(R.string.clear_favorites_confirmation)
                    negativeButton(android.R.string.cancel)
                    positiveButton(android.R.string.ok) {
                        doAsync {
                            database?.favoritesDao()?.nukeFavorites()
                            hasClearedFavs = true
                        }
                    }
                }
                dialog?.show()
                true
            }
        } else {
            storagePrefs?.removePreference(clearDatabase)
        }

        val notifPref = findPreference("enable_notifications") as? SwitchPreference
        notifPref?.isChecked = prefs.notificationsEnabled
        notifPref?.setOnPreferenceChangeListener { _, any ->
            val enable = any.toString().equals("true", true)
            if (enable != prefs.notificationsEnabled) {
                prefs.notificationsEnabled = enable
            }
            true
        }

        val privacyLink = try {
            string(R.string.privacy_policy_link, "")
        } catch (e: Exception) {
            ""
        }

        val termsLink = try {
            string(R.string.terms_conditions_link, "")
        } catch (e: Exception) {
            ""
        }

        val prefsScreen = findPreference("preferences") as? PreferenceScreen
        val legalCategory = findPreference("legal") as? PreferenceCategory

        if (privacyLink.hasContent() || termsLink.hasContent()) {
            val privacyPref = findPreference("privacy")
            if (privacyLink.hasContent()) {
                privacyPref?.setOnPreferenceClickListener {
                    try {
                        openLink(privacyLink)
                    } catch (e: Exception) {
                    }
                    true
                }
            } else {
                legalCategory?.removePreference(privacyPref)
            }

            val termsPref = findPreference("terms")
            if (termsLink.hasContent()) {
                termsPref?.setOnPreferenceClickListener {
                    try {
                        openLink(termsLink)
                    } catch (e: Exception) {
                    }
                    true
                }
            } else {
                legalCategory?.removePreference(termsPref)
            }
        } else {
            prefsScreen?.removePreference(legalCategory)
        }
    }

    fun showLocationChooserDialog() {
        clearDialog()
        try {
            dialog = mdDialog {
                folderChooser(
                        initialDirectory = try {
                            File(prefs.downloadsFolder)
                        } catch (e: Exception) {
                            @Suppress("DEPRECATION")
                            context.getExternalFilesDir(null)
                                    ?: Environment.getExternalStorageDirectory()
                        },
                        allowFolderCreation = true,
                        folderCreationLabel = R.string.create_folder) { dialog, folder ->
                    prefs.downloadsFolder = folder.absolutePath
                    updateDownloadLocation()
                    dialog.dismiss()
                }
                positiveButton(R.string.choose_folder)
            }
            dialog?.show()
        } catch (e: Exception) {
            toast(R.string.error_title)
        }
    }

    fun requestPermission() {
        requestStoragePermission(getString(R.string.permission_request, getAppName())) {
            showLocationChooserDialog()
        }
    }

    private fun showPermissionInformation(explanation: String, nonce: PermissionNonce) {
        snackbar(explanation) {
            setAction(R.string.allow) {
                dismiss()
                nonce.use()
                requestPermission()
            }
        }
    }

    fun updateDownloadLocation() {
        downloadLocation?.summary = getString(
                R.string.wallpapers_download_location_setting_content, prefs.downloadsFolder)
    }

    fun clearDialog() {
        dialog?.dismiss()
        dialog = null
    }

    private fun doFinish() {
        val intent = Intent()
        intent.putExtra("clearedFavs", hasClearedFavs)
        setResult(22, intent)
    }

    private companion object {
        const val JAHIR_PHOTO_URL =
            "https://github.com/jahirfiquitiva/Website-Resources/raw/master/myself/me-square-white.png"
        const val JAHIR_BUTTONS = "Website|Twitter"
        const val JAHIR_LINKS = "https://jahir.xyz/|https://jahir.xyz/twitter"
        
        const val ALLAN_PHOTO_URL = "https://avatars0.githubusercontent.com/u/6251823?v=4&s=400"
        const val ALLAN_BUTTONS = "Website|GitHub"
        const val ALLAN_LINKS = "https://www.allanwang.ca/|https://github.com/AllanWang"
        
        const val SHERRY_PHOTO_URL =
            "https://s3-img.pixpa.com/com/large/37571/newdo-2-pw69wd.jpg"
        const val SHERRY_BUTTONS = "Website|Instagram"
        const val SHERRY_LINKS =
            "http://www.ssabatinephotography.com/|https://www.instagram.com/sherry._.sabatine/"
    }
}
