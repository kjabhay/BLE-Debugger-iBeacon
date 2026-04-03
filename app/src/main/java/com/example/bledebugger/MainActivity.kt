package com.example.bledebugger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.bledebugger.ui.theme.BLEDebuggerTheme
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.*
import androidx.compose.ui.viewinterop.AndroidView
import android.util.Log

class MainActivity : ComponentActivity() {

    lateinit var scanner: BluetoothLeScanner
    lateinit var scanCallback: ScanCallback

    var chart: LineChart? = null
    var timeIndex = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        scanner = bluetoothAdapter.bluetoothLeScanner

        if (!bluetoothAdapter.isEnabled) {
            Log.d("BLE_DEBUG", "Bluetooth is OFF")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION // 🔥 ADD THIS
                ),
                1
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                1
            )
        }

        setContent {
            BLEScreen()
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(
        majorState: MutableState<String>,
        minorState: MutableState<String>
    ) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.e("BLE_DEBUG", "❌ NO BLUETOOTH_SCAN PERMISSION")
                return
            }
        }

        Log.d("BLE_DEBUG", "=== SCAN STARTING ===")

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val address = result.device.address
                val raw = result.scanRecord?.bytes
                val rssi = result.rssi

                // 1. LOG EVERY SINGLE BLUETOOTH DEVICE FOUND
                Log.d("BLE_DEBUG_ALL", "📡 Device spotted: MAC=$address | RSSI=$rssi")

                if (raw == null) return

                val beacon = parseIBeacon(result.scanRecord)

                if (beacon != null) {
                    // 2. LOG EVERY SUCCESSFULLY PARSED IBEACON
                    Log.d("BLE_DEBUG_ALL", "🏷️ iBeacon Parsed -> UUID: ${beacon.uuid} | Major: ${beacon.major} | Minor: ${beacon.minor}")

                    val targetUuid = "49495448-2d41-5454-454e-44414e434520"

                    if (beacon.uuid.equals(targetUuid, ignoreCase = true)) {
                        Log.d("BLE_DEBUG", "🎯🎯🎯 IITH ATTENDANCE BEACON FOUND!")

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

        // BARE MINIMUM - no filters, no settings at all
        // Create high-speed scan settings
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0) // Force immediate results
            .build()

// Start scan with empty filters and aggressive settings
        scanner.startScan(null, settings, scanCallback)
        Log.d("BLE_DEBUG", "=== scanner.startScan() called with LOW_LATENCY ===")
    }

    fun stopScan() {
        if (!::scanCallback.isInitialized) return

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        scanner.stopScan(scanCallback)
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

fun bytesToUuid(bytes: ByteArray): String {
    val hex = bytes.joinToString("") { "%02x".format(it) }
    return "${hex.substring(0,8)}-${hex.substring(8,12)}-${hex.substring(12,16)}-${hex.substring(16,20)}-${hex.substring(20)}"
}

data class Beacon(val uuid: String, val major: Int, val minor: Int)

// Now accepts a ScanRecord instead of a raw ByteArray
fun parseIBeacon(scanRecord: ScanRecord?): Beacon? {
    if (scanRecord == null) return null

    // 1. Ask Android to pull out ONLY Apple Manufacturer Data (Company ID: 0x004C)
    val appleData = scanRecord.getManufacturerSpecificData(0x004C) ?: return null

    // 2. Check if it's an iBeacon (Type 0x02, Length 0x15)
    // Note: The byte array returned by Android starts AFTER the Company ID.
    if (appleData.size >= 23 &&
        (appleData[0].toInt() and 0xFF) == 0x02 &&
        (appleData[1].toInt() and 0xFF) == 0x15) {

        // UUID (Indices 2 to 17)
        val uuidBytes = appleData.copyOfRange(2, 18)
        val uuid = bytesToUuid(uuidBytes)

        // Major (Indices 18 and 19)
        val major = ((appleData[18].toInt() and 0xFF) shl 8) or
                (appleData[19].toInt() and 0xFF)

        // Minor (Indices 20 and 21)
        val minor = ((appleData[20].toInt() and 0xFF) shl 8) or
                (appleData[21].toInt() and 0xFF)

        return Beacon(uuid, major, minor)
    }

    return null
}

@Composable
fun RSSIChart(activity: MainActivity) {

    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                data = LineData(LineDataSet(mutableListOf(), "RSSI"))
                activity.chart = this
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    )
}

@Composable
fun BLEScreen() {
    val major = remember { mutableStateOf("-") }
    val minor = remember { mutableStateOf("-") }
    val isScanning = remember { mutableStateOf(false) }


    val context = LocalContext.current
    val activity = context as MainActivity

    Column(modifier = Modifier.padding(16.dp)) {

        Button(
            onClick = {
                activity.startScan(major, minor)
                isScanning.value = true
            },
            enabled = !isScanning.value
        ) {
            Text("Start Scan")
            Log.d("BLE_DEBUG", "Scan started")
        }

        Button(
            onClick = {
                activity.stopScan()
                isScanning.value = false
            },
            enabled = isScanning.value
        ) {
            Text("Stop Scan")
            Log.d("BLE_DEBUG", "Scan stopped")
        }

        Text(
            text = if (isScanning.value) "🔍 Scanning..." else "⏹ Not Scanning",
            fontSize = 18.sp,
            color = if (isScanning.value) androidx.compose.ui.graphics.Color.Green
            else androidx.compose.ui.graphics.Color.Red
        )

        Text("Major: ${major.value}", fontSize = 20.sp)
        Text("Minor: ${minor.value}", fontSize = 20.sp)

        Spacer(modifier = Modifier.height(20.dp))

        RSSIChart(activity)
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BLEDebuggerTheme {
        BLEScreen()
    }
}