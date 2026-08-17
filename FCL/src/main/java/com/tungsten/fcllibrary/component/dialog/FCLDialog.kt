package com.tungsten.fcllibrary.component.dialog

import android.content.Context
import androidx.appcompat.app.AppCompatDialog
import top.newblock.fcl.R
import com.tungsten.fcllibrary.component.theme.ThemeEngine

open class FCLDialog(context: Context) : AppCompatDialog(context) {
    init {
        ThemeEngine.getInstance()
            .applyFullscreen(window, ThemeEngine.getInstance().getTheme().isFullscreen)
        window?.setBackgroundDrawableResource(R.drawable.dialog_background)
    }
}
