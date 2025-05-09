// CapturedPacketsAdapter.kt
package com.example.scaniot.model

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.scaniot.databinding.ItemCapturedSessionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CapturedPacketsAdapter : ListAdapter<Device, CapturedPacketsAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemCapturedSessionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DeviceViewHolder(
        private val binding: ItemCapturedSessionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: Device) {
            binding.apply {
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                if(device.lastCaptureTimestamp != null){
                    txtSessionDate.text = dateFormat.format(Date(device.lastCaptureTimestamp))
                }

                txtSessionId.text = "Device: ${device.name}"
                txtDevicesCount.text = "MAC: ${device.mac}"
                txtCaptureType.text = "Capture Session: ${device.sessionId}"
                //txtCaptureType.text = "Type: ${if (device.timeLimitMs > 0) "TIME LIMIT" else "PACKET COUNT"}"

                progressBarSession.max = device.captureTotal
                progressBarSession.progress = device.captureProgress

                val percent = if (device.captureTotal > 0) {
                    (device.captureProgress * 100) / device.captureTotal
                } else {
                    0
                }
                txtSessionStatus.text = if (device.capturing) {
                    "Active ($percent%)"
                } else {
                    "Completed (${device.captureProgress}/${device.captureTotal})"
                }

                if (!device.capturing) {
                    btnStopSession.visibility = View.GONE
                }

                btnStopSession.visibility = if (device.capturing) View.VISIBLE else View.GONE

                btnStopSession.setOnClickListener {
                    stopDeviceCapture(device)
                }

                btnDeleteSession.setOnClickListener { deleteDevice(device) }
                btnViewDevices.visibility = View.GONE // Not needed anymore
            }
        }

        private fun stopDeviceCapture(device: Device) {
            AlertDialog.Builder(binding.root.context)
                .setTitle("Stop Capture")
                .setMessage("Stop capturing for ${device.name}?")
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                .setPositiveButton("Stop") { _, _ ->

                    val packetCapturer = PacketCapturer(binding.root.context)
                    packetCapturer.stopDeviceCapture(device.sessionId, device.mac)

                    CaptureRepository.updateDeviceCaptureState(device.sessionId, device.mac, false)

                    //Update UI
                    val currentList = currentList.toMutableList()
                    val position = currentList.indexOfFirst {
                        it.mac == device.mac && it.sessionId == device.sessionId
                    }

                    if (position != -1) {
                        val updatedDevice = currentList[position].copy(
                            capturing = false
                        )
                        currentList[position] = updatedDevice
                        submitList(currentList)
                    }
                }
                .show()
        }


        private fun deleteSession(session: CaptureSession) {
            AlertDialog.Builder(binding.root.context)
                .setTitle("Delete Session")
                .setMessage("Delete this capture session and all its devices?")
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                .setPositiveButton("Delete") { _, _ ->
                    CaptureRepository.deleteCaptureSession(session.sessionId) { success ->
                        if (success) {
                            val newList = currentList.toMutableList().apply {
                                removeAt(adapterPosition)
                            }
                            submitList(newList)
                        }
                    }
                }
                .show()
        }

        private fun viewDevices(session: CaptureSession) {
            val devicesList = session.devices.joinToString("\n") { "${it.name} (${it.mac})" }

            AlertDialog.Builder(binding.root.context)
                .setTitle("Devices in Session")
                .setMessage(devicesList)
                .setPositiveButton("OK", null)
                .show()
        }

        private fun deleteDevice(device: Device) {
            AlertDialog.Builder(binding.root.context)
                .setTitle("Delete Device")
                .setMessage("Delete capture data for ${device.name}?")
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                .setPositiveButton("Delete") { _, _ ->
                    CaptureRepository.deleteDeviceCapture(device.sessionId, device.mac) { success ->
                        if (success) {
                            // Remove da lista local
                            val newList = currentList.toMutableList().apply { removeAt(adapterPosition) }
                            submitList(newList)
                            Toast.makeText(binding.root.context, "Capture deleted", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .show()
        }
    }

    private class DeviceDiffCallback : DiffUtil.ItemCallback<Device>() {
        override fun areItemsTheSame(oldItem: Device, newItem: Device): Boolean {
            return oldItem.mac == newItem.mac && oldItem.sessionId == newItem.sessionId
        }

        override fun areContentsTheSame(oldItem: Device, newItem: Device): Boolean {
            return oldItem == newItem
        }
    }
}