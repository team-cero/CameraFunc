package com.kohshin.camera2sample



import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.*
import android.support.v7.app.AppCompatActivity
import android.support.annotation.RequiresApi
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.ImageButton
import android.widget.NumberPicker
import android.widget.Toast
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {
   val PERMISION_CAMERA        = 200
   val PERMISION_WRITE_STORAGE = 1000
   val PERMISION_READ_STORAGE  = 1001

    //layout object variables
    private lateinit var shutterButton : ImageButton
    private lateinit var numberPicker  : NumberPicker
    private lateinit var previewView   : TextureView
    private lateinit var imageReader   : ImageReader

    //initiate each variable
    private lateinit var previewRequestBuilder : CaptureRequest.Builder
    private lateinit var previewRequest        : CaptureRequest
    private var backgroundHandler              : Handler?                = null
    private var backgroundThread               : HandlerThread?          = null
    private var cameraDevice                   : CameraDevice?           = null
    private lateinit var captureSession        : CameraCaptureSession


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // permission to read and write storage
        val writePermission  = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val readPermission  = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)

        if ( (writePermission != PackageManager.PERMISSION_GRANTED) || (readPermission != PackageManager.PERMISSION_GRANTED) ) {
            requestStoragePermission()
        }

        previewView = findViewById(R.id.mySurfaceView)
        previewView.surfaceTextureListener = surfaceTextureListener
        startBackgroundThread()


        shutterButton = findViewById(R.id.Shutter);

        //shutter button event
        shutterButton.setOnClickListener {
            // use folder
            val appDir = File(Environment.getExternalStorageDirectory(), "Camera2Sample")

            if (!appDir.exists()) {
                // if there is no folder, create it
                appDir.mkdirs()
            }

            try {
                val filename = "picture.jpg"
                var savefile : File? = null

                // stop the update of preview
                captureSession.stopRepeating()
                if (previewView.isAvailable) {

                    savefile = File(appDir, filename)
                    val fos = FileOutputStream(savefile)
                    val bitmap: Bitmap = previewView.bitmap
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                    fos.close()
                }

                if (savefile != null) {
                    Log.d("edulog", "Image Saved On: $savefile")
                    Toast.makeText(this, "Saved: $savefile", Toast.LENGTH_SHORT).show()
                }

            } catch (e: CameraAccessException) {
                Log.d("edulog", "CameraAccessException_Error: $e")
            } catch (e: FileNotFoundException) {
                Log.d("edulog", "FileNotFoundException_Error: $e")
            } catch (e: IOException) {
                Log.d("edulog", "IOException_Error: $e")
            }

            // restart preview
            captureSession.setRepeatingRequest(previewRequest, null, null)
        }
    }



    //launch cameras on a background
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread?.looper)
    }

    /**
     * TextureView Listener
     */
    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener
    {
        // when TextureView is available
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int)
        {
            imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG,2)
            openCamera()
        }

        // when the size of TextureView is changed
        override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture?, p1: Int, p2: Int) { }

        // when TextureView is updated
        override fun onSurfaceTextureUpdated(p0: SurfaceTexture?) { }

        // when TextureView is deleted
        override fun onSurfaceTextureDestroyed(p0: SurfaceTexture?): Boolean
        {
            return false
        }
    }

    // function to launch camera
    private fun openCamera() {
        //aquire a camera manager
        val manager: CameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            //acquire camera ID
            val camerId: String = manager.cameraIdList[0]

            //camera launch permission
            val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)

            if (permission != PackageManager.PERMISSION_GRANTED) {
                requestCameraPermission()
                return
            }

            //launch cameras
            manager.openCamera(camerId, stateCallback, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // acquire a permission to use cameras
    private fun requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            AlertDialog.Builder(baseContext)
                .setMessage("Permission Check")
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    requestPermissions(arrayOf(Manifest.permission.CAMERA), PERMISION_CAMERA)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    finish()
                }
                .show()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), PERMISION_CAMERA)
        }
    }

    // callback function about the status of camera
    private val stateCallback = object : CameraDevice.StateCallback() {

        // end the connection to cameras
        override fun onOpened(cameraDevice: CameraDevice) {
            this@MainActivity.cameraDevice = cameraDevice
            createCameraPreviewSession()
        }

        //quit the connection to cameras
        override fun onDisconnected(cameraDevice: CameraDevice) {
            cameraDevice.close()
            this@MainActivity.cameraDevice = null
        }

        // errors on cameras
        override fun onError(cameraDevice: CameraDevice, error: Int) {
            onDisconnected(cameraDevice)
            finish()
        }
    }


    // show the dialog to aquire the camera image creation
    private fun createCameraPreviewSession()
    {
        try
        {
            val texture = previewView.surfaceTexture
            texture.setDefaultBufferSize(previewView.width, previewView.height)

            val surface = Surface(texture)
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder.addTarget(surface)

            cameraDevice?.createCaptureSession(Arrays.asList(surface, imageReader.surface),
                @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
                object : CameraCaptureSession.StateCallback()
                {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession)
                    {
                        if (cameraDevice == null) return
                        try
                        {
                            captureSession = cameraCaptureSession
                            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            previewRequest = previewRequestBuilder.build()
                            cameraCaptureSession.setRepeatingRequest(previewRequest, null, Handler(backgroundThread?.looper))
                        } catch (e: CameraAccessException) {
                            Log.e("erfs", e.toString())
                        }

                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        //Tools.makeToast(baseContext, "Failed")
                    }
                }, null)
        } catch (e: CameraAccessException) {
            Log.e("erf", e.toString())
        }
    }

    private fun requestStoragePermission() {
        // permission to write
        if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            AlertDialog.Builder(baseContext)
                .setMessage("Permission Here")
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISION_WRITE_STORAGE)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    finish()
                }
                .create()
        } else {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISION_WRITE_STORAGE)
        }

        // permission to read
        if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            AlertDialog.Builder(baseContext)
                .setMessage("Permission Here")
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), PERMISION_READ_STORAGE)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    finish()
                }
                .create()
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), PERMISION_READ_STORAGE)
        }
    }


}
