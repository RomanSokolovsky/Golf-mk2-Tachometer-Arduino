package com.example.tacho
import android.Manifest
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import java.util.concurrent.CountDownLatch
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private val REQUEST_CODE_DISCOVERABLE_BT: Int = 2
    private lateinit var rpmTextView: TextView
    private lateinit var textViewDifferRpm: TextView
    private lateinit var voltsTextView: TextView
    private lateinit var speedTextView: TextView
    private lateinit var maxSpeedView: TextView
    private lateinit var checkBatt: TextView
    private lateinit var textViewPositiveDifferences: TextView

    private var flagBatt = 0
    private lateinit var speedometerUtils: SpeedometerUtils
    private lateinit var arrowImageView: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var  mediaPlayerOver: MediaPlayer
    private lateinit var  mediaPlayerBatteryNo: MediaPlayer
    private lateinit var  mediaPlayerBatteryOk: MediaPlayer
    private lateinit var  mediaPlayerConnect: MediaPlayer
    private lateinit var  mediaPlayerDisConnect: MediaPlayer

    private var connectionLostDialog: AlertDialog? = null
    private var isConnected: Boolean = false
    private val voltsList = mutableListOf<Double>()
    private val rpmList = mutableListOf<Float>()
    private var averageRpm = 0
    private lateinit var timer: Timer
    private var count = 0
    private var connected = false
    private lateinit var device: BluetoothDevice
    private lateinit var socket: BluetoothSocket


    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val PERMISSION_REQUEST_CODE = 2
        private const val DEVICE_MAC_ADDRESS = "2F:2C:4C:14:02:32"

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        timer = Timer()
        mediaPlayerOver = MediaPlayer.create(this, R.raw.over)
        mediaPlayerBatteryNo = MediaPlayer.create(this, R.raw.no)
        mediaPlayerBatteryOk = MediaPlayer.create(this, R.raw.ok)
        mediaPlayerConnect = MediaPlayer.create(this, R.raw.connect)
        mediaPlayerDisConnect = MediaPlayer.create(this, R.raw.disconnect)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        rpmTextView = findViewById(R.id.rpm_textview)
        checkBatt = findViewById(R.id.checkBattOk)
        voltsTextView = findViewById(R.id.accText)
        textViewDifferRpm = findViewById(R.id.textViewDifferRpm)
        textViewPositiveDifferences = findViewById(R.id.textViewPositiveDifferences)
        arrowImageView = findViewById(R.id.arrow1)
        speedTextView = findViewById(R.id.speedTextView)
        maxSpeedView = findViewById(R.id.maxSpeed)
        Log.d("mytest", "START PROGRAM")
        // Initialize Bluetooth adapter

        Log.d("mytest", "Initialize Bluetooth adapter")
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        device = bluetoothAdapter.getRemoteDevice(DEVICE_MAC_ADDRESS)
        enableBluetooth()
        progressBar = findViewById(R.id.progress_bar)

        //init speedometr
        speedometerUtils = SpeedometerUtils()
        speedometerUtils.initializeSpeedometer(this, speedTextView, maxSpeedView)
        timerScheduleTasks()

        // Start the reconnection loop
        Thread {
            while (true) {
                connectToDevice()
                // Attempt reconnection after a short delay
                Thread.sleep(1000)
            }
        }.start()

    }

    private fun timerScheduleTasks() {
        val averageStyle = mutableListOf<Float>() // List to store average values of style
        //alculate Average Difference once in a second
        timer.schedule(object : TimerTask() {
            override fun run() {
                if (rpmList.size >= 2) {
                    val tempcalc = calculateAverageDifference(rpmList)
                    averageStyle.add(tempcalc)
                    runOnUiThread {
                        maxMinRPM()
                        rpmTextView.text = averageRpm.toString()
                    }
                }
            }
        }, 0, 500)


        // find an Averege value of Accumulator
        timer.schedule(object : TimerTask() {
            override fun run() {
                if (voltsList.isNotEmpty()) {
                    val middleValue = voltsList.average()
                    runOnUiThread {
                        voltsTextView.text = String.format("%.1f", middleValue)
                        voltsList.clear()
                        if (flagBatt != 1) {
                            checkBatteryFun(middleValue)
                            flagBatt = 1
                        }
                    }
                }
            }
        }, 0, 2000)

        // Calculate the average value of averageStyle once every 10 seconds
        timer.schedule(object : TimerTask() {
            override fun run() {
                if (averageStyle.isNotEmpty()) {
                    val averageValue = averageStyle.average() // Calculate the average of averageStyle list
//                    Log.d("mytest", "Average value of AverageStyle: $averageValue")
                    runOnUiThread(){
                        textViewPositiveDifferences.text = String.format("%.1f", averageValue)
                    }
                    averageStyle.clear()
                }
            }
        }, 0, 10000)
    }


    private fun checkBatteryFun(volt: Double) {
        if (volt < 12.00) {
            checkBatt.text = "Глибокий Розряд!"
            checkBatt.setTextColor(Color.RED)
            mediaPlayerBatteryNo.start()
        } else if (volt >= 12.00 && volt <= 12.20) {
            checkBatt.text = "Розряд"
            checkBatt.setTextColor(Color.YELLOW)
            mediaPlayerBatteryNo.start()
        } else if (volt >= 12.20 && volt <= 12.50) {
            checkBatt.text = "Нормальний Заряд"
            checkBatt.setTextColor(Color.GREEN)
            mediaPlayerBatteryOk.start()
        } else if (volt >= 12.50) {
            checkBatt.text = "Повний Заряд"
            checkBatt.setTextColor(Color.GREEN)
            mediaPlayerBatteryOk.start()
        }
    }


    private fun enableBluetooth() {
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            // Handle this case accordingly
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            // Bluetooth is not enabled, prompt the user to enable it
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        } else {
            // Bluetooth is already enabled, proceed with connection
            Log.d("mytest", "Bluetooth is already enabled.")
            checkBluetoothPermission()
        }

//        if (!bluetoothAdapter.isDiscovering) {
//            val intent = Intent(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE))
//            startActivityForResult(intent, REQUEST_CODE_DISCOVERABLE_BT)
//        }
    }

    // Override onActivityResult to handle the Bluetooth enable request
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                // User enabled Bluetooth, proceed with connection
                checkBluetoothPermission()
            } else {
                // User didn't enable Bluetooth, handle this case accordingly
            }
        }
    }
    private fun checkBluetoothPermission() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            // Add the following permission for Bluetooth discovery
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val hasAllPermissions = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (hasAllPermissions) {
            Log.d("mytest", "hasAllPermissions : granted")
            // Bluetooth permissions are granted, proceed with connection
//            discoverDevices()
            connectToDevice()
        } else {
            // Request permissions
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
        }
    }

    private fun connectToDevice() {
        try {
            if (!::socket.isInitialized) {
                socket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
            }
            if (!connected) {
                socket?.connect()
                connected = true
                isConnected = true
                Log.d("mytest", "connected : $connected ")
                mediaPlayerConnect.start()
                runOnUiThread {
                    dismissConnectionLostDialog()
                }
                // Continuously read incoming bytes and convert them to integers
                Thread {
                    readIncomeByte(socket)
                }.start()
            }
        } catch (e: IOException) {
            Log.e("mytest", "DISCONNECTED from device: ${e.message}")
            connected = false

            runOnUiThread {
                showConnectionLostDialog()
            }
        }
    }


    private fun readIncomeByte(socket: BluetoothSocket?) {
        val inputStream: InputStream? = socket?.inputStream
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))
        try {
            while (connected) {
                val line = bufferedReader.readLine()
                if (line == null || line == "-1") {
                    break // Exit the loop if the line is null or "-1"
                }
                // Process the received data
                if (line.startsWith("tb")) {
                    runOnUiThread { taho(line) }
                } else if (line.startsWith("rv")) {
                    runOnUiThread { readVolts(line) }
                }
            }
        } catch (e: IOException) {
            Log.e("mytest", "Error reading data: ${e.message}")
            connected = false
            mediaPlayerDisConnect.start()
        }
    }

    private fun showConnectionLostDialog() {
        if (connectionLostDialog == null || !connectionLostDialog!!.isShowing) {
            // Create an AlertDialog.Builder
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Connection Lost")
            builder.setMessage("The Bluetooth connection was lost.")

            // Add a button to dismiss the dialog
            builder.setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }

            // Create the AlertDialog
            connectionLostDialog = builder.create()

            // Show the dialog
            connectionLostDialog?.show()
        }
    }

    private fun dismissConnectionLostDialog() {
        if (isConnected) {
            connectionLostDialog?.dismiss()
        }
    }

    fun updateArrowRotation(rpmAvr: Int) {
        val maxRpm = 6000f // Define the maximum RPM value
        val maxRotation = 170f // Define the maximum rotation angle in degrees
        val rotation = (rpmAvr.toFloat() / maxRpm) * maxRotation - 65
        arrowImageView.rotation = rotation
    }

    fun taho(receivedString: String) {
        val receivedTimeBetween = receivedString.substring(2).toIntOrNull()
        var receivedRpm: Float? = null
        if (receivedTimeBetween != null && receivedTimeBetween > 4000) {
            receivedRpm = 60000000.0f / (receivedTimeBetween * 2).toFloat()
            //every one second update average value - rpmTextView
            addRpmValue(receivedRpm)
            averageRpm = synchronized(rpmList) {
                if (rpmList.isNotEmpty() && rpmList.size >= 10) {
                    rpmList.average().toInt()
                } else {
                    0
                }
            }

            }


        runOnUiThread() {
            if (averageRpm != null) {
                updateArrowRotation(averageRpm)
                val minRpm = 600f
                val maxRpm = 1000f
                val progressBarMin = 0
                val progressBarMax = progressBar.max
                val progress = (((averageRpm - minRpm) / (maxRpm - minRpm)) * (progressBarMax - progressBarMin)).toInt() + progressBarMin
                progressBar.progress = progress
            }
            if (averageRpm != null && averageRpm > 5000 && !mediaPlayerOver.isPlaying) {
                mediaPlayerOver.start() // Start playing "no.mp3"
            } else if (averageRpm == null || averageRpm <= 5000 && mediaPlayerOver.isPlaying) {
                mediaPlayerOver.pause() // Pause or stop the MP3 file
                mediaPlayerOver.seekTo(0) // Reset the playback position
            }
        }


    }
    fun addRpmValue(rpm: Float?) {
        if (rpm != null) {
            rpmList.add(rpm)
            // Ensure the list contains a maximum of 20 samples
            if (rpmList.size > 10) {
                rpmList.removeAt(0) // Remove the oldest sample
            }
        }
    }

    fun maxMinRPM() {
        val measurements = rpmList?.toList() ?: return // Return early if rpmList is null
        val filteredMeasurements = measurements.filterNotNull() // Filter out null values
        val maxRpm = filteredMeasurements.maxOrNull() ?: 0f
        val minRpm = filteredMeasurements.minOrNull() ?: 0f
        val differenceRpm = if (maxRpm != 0f) maxRpm - minRpm else 0f
        textViewDifferRpm.text = String.format("%.0f", differenceRpm)
    }

    fun calculateAverageDifference(measurements: List<Float>): Float {
        var totalDifference = 0f
        var count = 0 // Counter for positive differences
        for (i in 0 until measurements.size) {
            if(i + 1 != measurements.size) {
                val difference = measurements[i + 1] - measurements[i]
                if (difference > 0) {
                    totalDifference += difference
                    count++
                }
            } else {
                break
            }

        }

        val averageDifference = if (count > 0) totalDifference / count else 0f
        return averageDifference
    }

    fun readVolts(receivedString: String) {
        val receivedVolts = receivedString.substring(2).toIntOrNull()
        Log.d("mytest", "receivedVolts :  $receivedVolts")
        val formattedVolts = receivedVolts?.div(100.0)
        Log.d("mytest", "formattedVolts :  $formattedVolts")
        if (formattedVolts != null) {
            voltsList.add(formattedVolts)
        }
    }



    override fun onDestroy() {
        super.onDestroy()
        mediaPlayerOver.release()
        mediaPlayerBatteryNo.release()
        mediaPlayerBatteryOk.release()
        bluetoothAdapter.disable()
        speedometerUtils.stopLocationUpdates(this)
        timer.cancel()
    }



}
