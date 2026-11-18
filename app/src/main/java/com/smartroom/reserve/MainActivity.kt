package com.smartroom.reserve

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.smartroom.reserve.ui.AppRoot
import com.smartroom.reserve.ui.AppViewModel
import com.smartroom.reserve.ui.theme.SmartRoomTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartRoomTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(vm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-evaluate auto-release / live availability whenever the user returns to the app.
        vm.refresh()
    }
}
