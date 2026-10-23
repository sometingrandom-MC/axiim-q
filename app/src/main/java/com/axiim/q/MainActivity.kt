package com.axiim.q

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.axiim.q.data.AppPrefs
import com.axiim.q.ui.AxiimAppShell

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    // Pick the window theme before the first frame so the background behind Compose matches the color setting
    val materialYou = getSharedPreferences(AppPrefs.FILE, MODE_PRIVATE).getBoolean(AppPrefs.MATERIAL_YOU_COLORS, false)
    if (materialYou) setTheme(R.style.Theme_AxiimQ_MaterialYou)
    super.onCreate(savedInstanceState)
    setContent {
      AxiimAppShell()
    }
  }
}
