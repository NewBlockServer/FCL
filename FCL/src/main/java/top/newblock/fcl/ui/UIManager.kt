package top.newblock.fcl.ui

import android.content.Context
import top.newblock.fcl.R
import top.newblock.fcl.ui.account.AccountUI
import top.newblock.fcl.ui.controller.ControllerUI
import top.newblock.fcl.ui.download.DownloadUI
import top.newblock.fcl.ui.main.MainUI
import top.newblock.fcl.ui.manage.ManageUI
import top.newblock.fcl.ui.multiplayer.MultiplayerUI
import top.newblock.fcl.ui.setting.SettingUI
import top.newblock.fcl.ui.version.VersionUI
import com.tungsten.fclcore.util.Logging
import com.tungsten.fcllibrary.component.ui.FCLBaseUI
import com.tungsten.fcllibrary.component.ui.FCLCommonUI
import com.tungsten.fcllibrary.component.view.FCLUILayout
import java.util.logging.Level

class UIManager(val context: Context, val parent: FCLUILayout) {
    companion object {
        @JvmStatic
        lateinit var instance: UIManager
    }

    private var initialized = false
    lateinit var mainUI: MainUI
    val accountUI: AccountUI by lazy { AccountUI(context, parent, R.layout.ui_account) }
    val versionUI: VersionUI by lazy { VersionUI(context, parent, R.layout.ui_version) }
    val manageUI: ManageUI by lazy { ManageUI(context, parent, R.layout.ui_manage) }
    val downloadUI: DownloadUI by lazy { DownloadUI(context, parent, R.layout.ui_download) }
    val controllerUI: ControllerUI by lazy { ControllerUI(context, parent, R.layout.ui_controller) }
    val multiplayerUI: MultiplayerUI by lazy { MultiplayerUI(context, parent, R.layout.ui_multiplayer) }
    val settingUI: SettingUI by lazy { SettingUI(context, parent, R.layout.ui_setting) }

    private val allUIList = mutableListOf<FCLBaseUI>()
    var currentUI: FCLBaseUI? = null

    fun init() {
        if (initialized) {
            Logging.LOG.log(Level.WARNING, "UIManager already initialized!")
            return
        }
        instance = this
        mainUI = MainUI(context, parent, R.layout.ui_main)
        allUIList.add(mainUI)
    }

    fun switchUI(ui: FCLCommonUI) {
        if (!allUIList.contains(ui)) {
            allUIList.add(ui)
        }
        for (baseUI in allUIList) {
            if (ui === baseUI) {
                currentUI?.onStop()
                ui.onStart()
                currentUI = ui
                break
            }
        }
    }

    fun registerDefaultBackEvent(runnable: Runnable?) {
        FCLBaseUI.setDefaultBackEvent(runnable)
    }

    fun onBackPressed() {
        currentUI?.onBackPressed()
    }

    fun onPause() {
        for (baseUI in allUIList) {
            baseUI.onPause()
        }
    }

    fun onResume() {
        for (baseUI in allUIList) {
            baseUI.onResume()
        }
    }
}