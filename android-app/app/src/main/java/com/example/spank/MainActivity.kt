package com.example.spank

import android.content.*
import android.os.*
import androidx.activity.*
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private var spankService: SpankService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SpankService.LocalBinder
            spankService = binder.getService()
            isBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var isActive by remember { mutableStateOf(false) }
            var sensitivity by remember { mutableFloatStateOf(15f) }
            var mode by remember { mutableStateOf("pain") }
            var volumeScaling by remember { mutableStateOf(true) }

            LaunchedEffect(mode, sensitivity, volumeScaling) {
                if (isBound) {
                    spankService?.updateConfig(mode, sensitivity, volumeScaling)
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text("Spank for Android", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(24.dp))
                
                Text("Sensitivity (Slap Force): ${sensitivity.toInt()} m/s²")
                Slider(value = sensitivity, onValueChange = { sensitivity = it }, valueRange = 5f..40f)
                
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = volumeScaling, onCheckedChange = { volumeScaling = it })
                    Text("Volume Scaling (Harder = Louder)")
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Sound Pack:")
                Row {
                    FilterChip(selected = mode == "pain", onClick = { mode = "pain" }, label = { Text("Pain") })
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(selected = mode == "sexy", onClick = { mode = "sexy" }, label = { Text("Sexy") })
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(selected = mode == "halo", onClick = { mode = "halo" }, label = { Text("Halo") })
                }

                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val intent = Intent(this@MainActivity, SpankService::class.java)
                        if (isActive) {
                            try {
                                unbindService(connection)
                            } catch (e: Exception) {}
                            stopService(intent)
                        } else {
                            startForegroundService(intent)
                            bindService(intent, connection, Context.BIND_AUTO_CREATE)
                        }
                        isActive = !isActive
                    }
                ) {
                    Text(if (isActive) "STOP DETECTION" else "START DETECTION")
                }
            }
        }
    }
}
