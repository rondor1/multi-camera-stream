package com.rondor.multicamerastream

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.rondor.multicamerastream.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {

    data class DualCamera(val logicalId: String, val physicalId1: String, val physicalId2: String)

    private val debugTag = "Robert app"

    private fun findDualCameras(manager: CameraManager, facing: Int? = null): List<DualCamera> {
        val dualCameras = ArrayList<DualCamera>()

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
                    dualCameras.add(
                        DualCamera(
                            it.second,
                            physicalCameras[idx1],
                            physicalCameras[idx2]
                        )
                    )
                }
            }
        }

        return dualCameras
    }

    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val dualCameras = findDualCameras(manager)!! //Ensure this is non-null
    }


}