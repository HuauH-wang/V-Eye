package com.veye.mobile.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.veye.mobile.ui.theme.VeyeTheme

class DebugTestActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      VeyeTheme {
        DebugTestScreen()
      }
    }
  }
}

