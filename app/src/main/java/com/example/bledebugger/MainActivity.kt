package com.example.bledebugger

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.le.*
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import com.example.bledebugger.ui.theme.BLEDebuggerTheme
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.*

class MainActivity : ComponentActivity() {

    // Safely nullable scanner to prevent crashes if BT is off on startup
    var scanner: BluetoothLeScanner? = null
    lateinit var scanCallback: ScanCallback

    var chart: LineChart? = null
    var timeIndex = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        // Safely assign scanner. Will be null if Bluetooth is off.
        scanner = bluetoothAdapter?.bluetoothLeScanner

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.d("BLE_DEBUG", "Bluetooth is OFF")
        }

        // Request appropriate permissions based on Android version
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                1
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        }

        setContent {
            BLEDebuggerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BLEScreen()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(
        targetUuidString: String,
        majorState: MutableState<String>,
        minorState: MutableState<String>
    ) {
        // --- API-SPECIFIC PERMISSION CHECKS ---
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // Android 12+ requires BLUETOOTH_SCAN
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.e("BLE_DEBUG", "❌ NO BLUETOOTH_SCAN PERMISSION")
                return
            }
        } else {
            // API 26 to Android 11 requires Location to scan
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.e("BLE_DEBUG", "❌ NO LOCATION PERMISSION")
                return
            }
        }

        Log.d("BLE_DEBUG", "=== SCAN STARTING ===")

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val raw = result.scanRecord?.bytes
                val rssi = result.rssi

                if (raw == null) return

                val beacon = parseIBeacon(result.scanRecord)

                if (beacon != null) {
                    // Compare against the UUID typed into the UI
                    if (beacon.uuid.equals(targetUuidString.trim(), ignoreCase = true)) {
                        Log.d("BLE_DEBUG", "🎯🎯🎯 TARGET BEACON FOUND! RSSI: $rssi")

                        // Update your UI states
                        majorState.value = beacon.major.toString()
                        minorState.value = beacon.minor.toString()

                        // Add to graph
                        addEntryToGraph(rssi)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BLE_DEBUG", "❌ SCAN FAILED errorCode=$errorCode")
            }
        }

        // Aggressive scan settings to prevent packet loss
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        scanner?.startScan(null, settings, scanCallback)
        Log.d("BLE_DEBUG", "=== scanner?.startScan() called with LOW_LATENCY ===")
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!::scanCallback.isInitialized) return

        // --- API-SPECIFIC PERMISSION CHECKS ---
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return
            }
        } else {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        scanner?.stopScan(scanCallback)
        Log.d("BLE_DEBUG", "=== SCAN STOPPED ===")
    }

    fun addEntryToGraph(rssi: Int) {
        val chart = chart ?: return
        val data = chart.data ?: return

        data.addEntry(Entry(timeIndex++, rssi.toFloat()), 0)
        data.notifyDataChanged()

        chart.notifyDataSetChanged()
        chart.invalidate()
    }
}

// ----------------------------------------------------
// PARSING & DATA HANDLING
// ----------------------------------------------------

// 🔥 Kept the vital .toInt() and 0xFF fix to prevent sign extension bugs
fun bytesToUuid(bytes: ByteArray): String {
    val hex = bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    return "${hex.substring(0,8)}-${hex.substring(8,12)}-${hex.substring(12,16)}-${hex.substring(16,20)}-${hex.substring(20)}"
}

data class Beacon(val uuid: String, val major: Int, val minor: Int)

fun parseIBeacon(scanRecord: ScanRecord?): Beacon? {
    if (scanRecord == null) return null

    // Ask Android to natively extract Apple Manufacturer Data (0x004C)
    val appleData = scanRecord.getManufacturerSpecificData(0x004C) ?: return null

    if (appleData.size >= 23 &&
        (appleData[0].toInt() and 0xFF) == 0x02 &&
        (appleData[1].toInt() and 0xFF) == 0x15) {

        val uuidBytes = appleData.copyOfRange(2, 18)
        val uuid = bytesToUuid(uuidBytes)

        val major = ((appleData[18].toInt() and 0xFF) shl 8) or
                (appleData[19].toInt() and 0xFF)

        val minor = ((appleData[20].toInt() and 0xFF) shl 8) or
                (appleData[21].toInt() and 0xFF)

        return Beacon(uuid, major, minor)
    }
    return null
}

// ----------------------------------------------------
// JETPACK COMPOSE UI
// ----------------------------------------------------

@Composable
fun RSSIChart(activity: MainActivity) {
    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                data = LineData(LineDataSet(mutableListOf(), "RSSI").apply {
                    color = android.graphics.Color.BLUE
                    setCircleColor(android.graphics.Color.RED)
                    lineWidth = 2f
                    circleRadius = 3f
                    setDrawValues(false)
                })
                description.isEnabled = false
                legend.isEnabled = false
                axisRight.isEnabled = false
                xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                activity.chart = this
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .padding(8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BLEScreen() {
    val context = LocalContext.current
    val activity = context as MainActivity

    // UI States (Defaults to the custom "IITH-ATTENDANCE " hex string)
    val targetUuid = remember { mutableStateOf("49495448-2d41-5454-454e-44414e434520") }
    val major = remember { mutableStateOf("-") }
    val minor = remember { mutableStateOf("-") }
    val isScanning = remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BLE Beacon Debugger", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // --- CONFIGURATION CARD ---
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Configuration", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = targetUuid.value,
                        onValueChange = { targetUuid.value = it },
                        label = { Text("Target UUID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isScanning.value // Disable editing while scanning
                    )
                }
            }

            // --- SCAN CONTROLS ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = {
                        activity.startScan(targetUuid.value, major, minor)
                        isScanning.value = true
                    },
                    enabled = !isScanning.value,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Start Scan", fontSize = 16.sp)
                }

                Button(
                    onClick = {
                        activity.stopScan()
                        isScanning.value = false
                    },
                    enabled = isScanning.value,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Stop Scan", fontSize = 16.sp)
                }
            }

            // --- DATA DISPLAY CARD ---
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Status:", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (isScanning.value) "Scanning..." else "Not Scanning",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isScanning.value) androidx.compose.ui.graphics.Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Major:", fontSize = 18.sp)
                        Text(text = major.value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Minor:", fontSize = 18.sp)
                        Text(text = minor.value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // --- CHART CARD ---
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("RSSI Signal Strength", modifier = Modifier.padding(start = 8.dp, top = 8.dp), fontWeight = FontWeight.SemiBold)
                    RSSIChart(activity)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BLEDebuggerTheme {
        BLEScreen()
    }
}