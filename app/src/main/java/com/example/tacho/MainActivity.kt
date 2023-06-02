package com.example.tacho
import android.Manifest
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
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

class MainActivity : AppCompatActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private val REQUEST_CODE_DISCOVERABLE_BT: Int = 2
    private lateinit var rpmTextView: TextView
    private lateinit var textViewDifferRpm: TextView
    private lateinit var voltsTextView: TextView
    private lateinit var speedTextView: TextView
    private lateinit var maxSpeedView: TextView
    private lateinit var checkBatt: TextView
    private var flagBatt = 0
    private lateinit var speedometerUtils: SpeedometerUtils
    private lateinit var arrowImageView: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var  mediaPlayer: MediaPlayer
    private var connectionLostDialog: AlertDialog? = null
    private var isConnected: Boolean = false
    private val voltsList = mutableListOf<Double>()
    private val rpmList = mutableListOf<Float>()
    private var averageRpm = 0
    private lateinit var timer: Timer
    private var count = 0
    private var connected = false

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val PERMISSION_REQUEST_CODE = 2
        private const val DEVICE_MAC_ADDRESS = "2F:2C:4C:14:02:32"

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        timer = Timer()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        rpmTextView = findViewById(R.id.rpm_textview)
        checkBatt = findViewById(R.id.checkBattOk)
        voltsTextView = findViewById(R.id.accText)
        textViewDifferRpm = findViewById(R.id.textViewDifferRpm)
        arrowImageView = findViewById(R.id.arrow1)
        speedTextView = findViewById(R.id.speedTextView)
        maxSpeedView = findViewById(R.id.maxSpeed)
        Log.d("mytest", "START PROGRAM")
        // Initialize Bluetooth adapter

        Log.d("mytest", "Initialize Bluetooth adapter")
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        enableBluetooth()
        progressBar = findViewById(R.id.progress_bar)
        mediaPlayer = MediaPlayer.create(this, R.raw.over)

        //init speedometr
        speedometerUtils = SpeedometerUtils()
        speedometerUtils.initializeSpeedometer(this, speedTextView, maxSpeedView)
        timerScheduleTasks()

    }

    private fun timerScheduleTasks() {
        //alculate Average Difference once in a second
        timer.schedule(object : TimerTask() {
            override fun run() {
                if (rpmList.size >= 2) {
                    val tempcalc = calculateAverageDifference(rpmList)
                    runOnUiThread {
                        Log.d("mytest", "calculateAverageDifference(rpmList) = : $tempcalc")
                        maxMinRPM()
                        rpmTextView.text = averageRpm.toString()
                    }
                }
            }
        }, 0, 1000)

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
    }


    private fun checkBatteryFun(volt: Double) {
        if (volt < 12.00) {
            checkBatt.text = "Глибокий Розряд!"
            checkBatt.setTextColor(Color.RED)
            mediaPlayer.stop() // Stop any ongoing playback
            mediaPlayer = MediaPlayer.create(this, R.raw.no)
            mediaPlayer.start() // Start playing "no.mp3"
        } else if (volt >= 12.00 && volt <= 12.20) {
            checkBatt.text = "Розряд"
            checkBatt.setTextColor(Color.YELLOW)
            mediaPlayer.stop() // Stop any ongoing playback
            mediaPlayer = MediaPlayer.create(this, R.raw.no)
            mediaPlayer.start() // Start playing "no.mp3"
        } else if (volt >= 12.20 && volt <= 12.50) {
            checkBatt.text = "Нормальний Заряд"
            checkBatt.setTextColor(Color.GREEN)
            mediaPlayer.stop() // Stop any ongoing playback
            mediaPlayer = MediaPlayer.create(this, R.raw.ok)
            mediaPlayer.start() // Start playing "ok.mp3"
        } else if (volt >= 12.50) {
            checkBatt.text = "Повний Заряд"
            checkBatt.setTextColor(Color.GREEN)
            mediaPlayer.stop() // Stop any ongoing playback
            mediaPlayer = MediaPlayer.create(this, R.raw.ok)
            mediaPlayer.start() // Start playing "ok.mp3"
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
        Log.d("mytest", "connectToDevice() : ok")
        val device = bluetoothAdapter.getRemoteDevice(DEVICE_MAC_ADDRESS)
        // Create a BluetoothSocket for the device
        val socket: BluetoothSocket? = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
        // Connect to the device in a separate thread
        val latch = CountDownLatch(1)
        Thread {
            while (!connected) {
                try {
                    // This is a blocking call and will only return after a successful connection or an exception
                    socket?.connect()
                    // Connection successful, perform your desired operations with the device
                    Log.d("mytest", "Connected to device: ${device.name} (${device.address})")
                    connected = true
                    isConnected = true
                    runOnUiThread {
                        dismissConnectionLostDialog()
                    }
                    // Continuously read incoming bytes and convert them to integers
                    Thread {
                        readIncomeByte(socket)
                        latch.countDown() // Signal that the reading is completed
                    }.start()
                    // Wait for the reading to complete
                    try {
                        latch.await()
                    } catch (e: InterruptedException) {
                        // Handle InterruptedException if needed
                    }

                } catch (e: IOException) {
                    Log.e("mytest", "Error connecting to device: ${e.message}")
                    connected = false
                    runOnUiThread {
                        showConnectionLostDialog()
                    }
                    // Attempt reconnection after a short delay
                    Thread.sleep(2000)
                }

            }
            // Close the socket if the loop exits
            socket?.close()
        }.start()

    }

    private fun readIncomeByte(socket: BluetoothSocket?) {
        val inputStream: InputStream? = socket?.inputStream
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))
        while (connected) {
            try {
                val receivedString = bufferedReader.readLine()
                if (receivedString != null) {
                    runOnUiThread {
                        if (receivedString.startsWith("tbw=")) {
                            taho(receivedString)
                        } else if (receivedString.startsWith("rvlt=")){
                            readVolts(receivedString)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e("mytest", "Error reading data: ${e.message}")
                connected = false
            }
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

    fun updateArrowRotation(rpm: Float) {
        // Calculate the rotation angle based on the received RPM
        val maxRpm = 6000f // Define the maximum RPM value
        val maxRotation = 170f // Define the maximum rotation angle in degrees
        val rotation = (rpm / maxRpm) * maxRotation
        // Create an ObjectAnimator to animate the rotation property of the arrowImageView
        val animator = ObjectAnimator.ofFloat(arrowImageView, "rotation", arrowImageView.rotation, rotation - 65)
        animator.duration = 200 // Set the duration of the animation (in milliseconds)
        animator.interpolator = LinearInterpolator() // Set the desired interpolator for the animation
        animator.start() // Start the animation
    }

    fun taho(receivedString: String) {
        val receivedTimeBetween = receivedString.substring(4).toIntOrNull()
        var receivedRpm: Float? = null
        if (receivedTimeBetween != null && receivedTimeBetween > 4000) {
            receivedRpm = 60000000.0f / (receivedTimeBetween * 2).toFloat()
            //every one second update average value - rpmTextView
            addRpmValue(receivedRpm)
            averageRpm = if (rpmList.isNotEmpty() && rpmList.size >= 50) {
                rpmList.average().toInt()
            } else {
                0
            }

            }
//        rpmTextView.text = if (receivedRpm != null) receivedRpm.toInt().toString()  else ""
        if (receivedRpm != null) {
            updateArrowRotation(receivedRpm)
            val minRpm = 600f
            val maxRpm = 1000f
            val progressBarMin = 0
            val progressBarMax = progressBar.max
            val transitionDuration = 600L
            val progress = (((receivedRpm - minRpm) / (maxRpm - minRpm)) * (progressBarMax - progressBarMin)).toInt() + progressBarMin
            val animator = ObjectAnimator.ofInt(progressBar, "progress", progress)
            animator.interpolator = LinearInterpolator()
            animator.duration = transitionDuration
            animator.start()
        }
        if (receivedRpm != null && receivedRpm > 4500 && !mediaPlayer.isPlaying) {
            mediaPlayer.start() // Start playing the MP3 file
        } else if (receivedRpm == null || receivedRpm <= 4500 && mediaPlayer.isPlaying) {
            mediaPlayer.pause() // Pause or stop the MP3 file
            mediaPlayer.seekTo(0) // Reset the playback position
        }


    }
    fun addRpmValue(rpm: Float?) {
        if (rpm != null) {
            rpmList.add(rpm)
            // Ensure the list contains a maximum of 20 samples
            if (rpmList.size > 50) {
                rpmList.removeAt(0) // Remove the oldest sample
            }
        }
    }

    fun maxMinRPM(){
        // Calculate and update the maximum and minimum RPM values
        var differenceRpm = 0f
        val maxRpm = rpmList.maxOrNull() ?: 0f
        val minRpm = rpmList.minOrNull() ?: 0f
        differenceRpm = if (maxRpm != 0f)  maxRpm - minRpm else 0f
        textViewDifferRpm.text = String.format("%.0f", differenceRpm)
    }

    fun calculateAverageDifference(measurements: List<Float>): Float {
        var totalDifference = 0f
        for (i in 0 until measurements.size - 1) {
            val difference = measurements[i + 1] - measurements[i]
            totalDifference += difference
        }
        val averageDifference = totalDifference / (measurements.size - 1)
        return averageDifference
    }

    fun readVolts(receivedString: String) {
        val receivedVolts = receivedString.substring(5).toIntOrNull()
        val formattedVolts = receivedVolts?.div(100.0)
        if (formattedVolts != null) {
            voltsList.add(formattedVolts)
        }
    }



    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release() // Release the MediaPlayer resources
        bluetoothAdapter.disable()
        speedometerUtils.stopLocationUpdates(this)
        timer.cancel()
    }



}
