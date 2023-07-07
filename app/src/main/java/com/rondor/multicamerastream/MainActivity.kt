package com.rondor.multicamerastream

import android.Manifest
import android.app.DownloadManager.Request
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.rondor.multicamerastream.databinding.ActivityMainBinding
import java.util.concurrent.Executor


typealias MutableCameraOutputs = Triple<MutableList<Surface>?, MutableList<Surface>, MutableList<Surface>>

class MainActivity : AppCompatActivity() {

    data class MultipleCameras(
        val logicalId: String,
        val physicalId1: String,
        val physicalId2: String
    )

    private val debugTag = "Robert app"
    private lateinit var binding: ActivityMainBinding

    private fun findMultipleCameras(
        manager: CameraManager,
        facing: Int? = null
    ): List<MultipleCameras> {
        val multipleCameras = ArrayList<MultipleCameras>()

        manager.cameraIdList.map {
            Pair(manager.getCameraCharacteristics(it), it)
        }.filter {
            //Filter by facing camera
            facing == null || it.first.get(CameraCharacteristics.LENS_FACING) == facing
        }.filter {
            //Filter logical cameras
            it.first.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)!!
                .contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)
        }.forEach {
            val physicalCameras = it.first.physicalCameraIds.toTypedArray()
            for (idx1 in physicalCameras.indices) {
                for (idx2 in (idx1 + 1) until physicalCameras.size) {
                    multipleCameras.add(
                        MultipleCameras(
                            it.second,
                            physicalCameras[idx1],
                            physicalCameras[idx2]
                        )
                    )
                }
            }
        }

        return multipleCameras
    }

    fun findShortLongCameraPair(manager: CameraManager, facing: Int? = null): MultipleCameras? {

        return findMultipleCameras(manager, facing).map {
            val characteristics1 = manager.getCameraCharacteristics(it.physicalId1)
            val characteristics2 = manager.getCameraCharacteristics(it.physicalId2)

            // Query the focal lengths advertised by each physical camera
            val focalLengths1 = characteristics1.get(
                CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
            ) ?: floatArrayOf(0F)
            val focalLengths2 = characteristics2.get(
                CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
            ) ?: floatArrayOf(0F)

            // Compute the largest difference between min and max focal lengths between cameras
            val focalLengthsDiff1 = focalLengths2.maxOrNull()!! - focalLengths1.minOrNull()!!
            val focalLengthsDiff2 = focalLengths1.maxOrNull()!! - focalLengths2.minOrNull()!!

            // Return the pair of camera IDs and the difference between min and max focal lengths
            if (focalLengthsDiff1 < focalLengthsDiff2) {
                Pair(
                    MultipleCameras(it.logicalId, it.physicalId1, it.physicalId2),
                    focalLengthsDiff1
                )
            } else {
                Pair(
                    MultipleCameras(it.logicalId, it.physicalId2, it.physicalId1),
                    focalLengthsDiff2
                )
            }

            // Return only the pair with the largest difference, or null if no pairs are found
        }.maxByOrNull { it.second }?.first
    }


    private fun openMultipleCameras(
        multipleCameras: MultipleCameras,
        cameraManager: CameraManager,
        executor: Executor = mainExecutor,
        callback: (CameraDevice) -> Unit
    ) {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestCameraPermission()
        }

        cameraManager.openCamera(
            multipleCameras.logicalId,
            executor,
            object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    callback(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    TODO("Not yet implemented")
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    TODO("Not yet implemented")
                }
            })
    }

    fun requestCameraPermission() {
        requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
        return
    }


    fun createMultipleCameraSession(
        cameraManager: CameraManager,
        multipleCameras: MultipleCameras,
        targets: MutableCameraOutputs,
        executor: Executor = mainExecutor,
        callback: (CameraCaptureSession) -> Unit
    ) {
        val outputConfigsLogical = targets.first?.map { OutputConfiguration(it) }
        val outputConfigsPhysical1 = targets.second?.map {
            OutputConfiguration(it).apply { setPhysicalCameraId(multipleCameras.physicalId1) }
        }
        val outputConfigsPhysical2 = targets.third?.map {
            OutputConfiguration(it).apply { setPhysicalCameraId(multipleCameras.physicalId2) }
        }

        //Flatten the structure of arrays of configurations
        val outputConfigsAll = arrayOf(
            outputConfigsLogical, outputConfigsPhysical1, outputConfigsPhysical2
        )
            .filterNotNull().flatMap { it }


        // Instantiate a session configuration that can be used to create a session
        val sessionConfiguration = SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
            outputConfigsAll, executor, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) = callback(session)

                // Omitting for brevity...
                override fun onConfigureFailed(session: CameraCaptureSession) =
                    session.device.close()
            })


        openMultipleCameras(multipleCameras, cameraManager, executor) {
            it.createCaptureSession(sessionConfiguration)
        }

    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
                if (isGranted) {
                    Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Camera permission not granted", Toast.LENGTH_SHORT).show()
                }
            }

        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        } else {
            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val surface1 = binding.surfaceView5.holder.surface
            val surface2 = binding.surfaceView6.holder.surface

            val outputTargets = MutableCameraOutputs(
                null,
                mutableListOf<Surface>(surface1),
                mutableListOf<Surface>(surface2)
            )


            val multipleCameras = findShortLongCameraPair(manager)!! //Ensure this is non-null
            createMultipleCameraSession(
                manager,
                multipleCameras,
                targets = outputTargets
            ) { session ->

                val requestTemplate = CameraDevice.TEMPLATE_PREVIEW
                Log.i(debugTag, "TEMPLATE PREVIEW created")
                val captureRequest =
                    session.device.createCaptureRequest(requestTemplate).apply {
                        arrayOf(surface1, surface2).forEach { addTarget(it) }
                    }.build()
                Log.i(debugTag, "CAPTURE request done")

                // Set the sticky request for the session and you are done
                Log.i(debugTag, "Repeating session")
                session.setRepeatingRequest(captureRequest, null, null)
            }
        }


    }

}
