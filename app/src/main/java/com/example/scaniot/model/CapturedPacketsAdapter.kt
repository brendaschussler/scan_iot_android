package com.example.scaniot.model

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.scaniot.R
import com.example.scaniot.databinding.ItemCapturedPacketsBinding
import com.example.scaniot.utils.showMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CapturedPacketsAdapter : ListAdapter<Device, CapturedPacketsAdapter.CapturedPacketViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CapturedPacketViewHolder {
        val binding = ItemCapturedPacketsBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CapturedPacketViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CapturedPacketViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CapturedPacketViewHolder(
        private val binding: ItemCapturedPacketsBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: Device) {
            binding.apply {
                txtDeviceNameCaptured.text = device.name
                txtMacAdressCapturedPackets.text = device.mac

                // Formata a data da captura
                val timestamp = device.lastCaptureTimestamp ?: System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                txtCaptureDate.text = dateFormat.format(Date(timestamp))
                progressBarCapturedPackets.max = device.captureTotal
                progressBarCapturedPackets.progress = device.captureProgress
                txtNumberPacketsCaptured.text = "${device.captureProgress}/${device.captureTotal}"
                txtPercentProgress.text = "${(device.captureProgress * 100 / device.captureTotal)}%"

                // Mostra o botão Stop apenas se estiver capturando
                btnStopCapture.visibility = if (device.capturing) View.VISIBLE else View.GONE
                btnStopCapture.setOnClickListener {
                    stopCapture(device)
                }

                btnDeleteCapture.setOnClickListener {
                    deleteCapture(device)
                }
            }
        }

        private fun stopCapture(device: Device) {
            if (device.sessionId.isNotEmpty()) {
                CaptureRepository.updateCaptureState(device.sessionId, device, false)

                // Atualiza localmente
                val newList = currentList.toMutableList().apply {
                    val index = indexOfFirst { it.mac == device.mac && it.sessionId == device.sessionId }
                    if (index != -1) {
                        set(index, device.copy(capturing = false))
                    }
                }
                submitList(newList)
            }
        }

        private fun deleteCapture(device: Device) {
            if (device.sessionId.isNotEmpty() && device.mac.isNotEmpty()) {
                AlertDialog.Builder(binding.root.context)
                    .setTitle("Delete Device Capture")
                    .setMessage("Delete this capture for ${device.name}?")
                    .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                    .setPositiveButton("Delete") { _, _ ->
                        CaptureRepository.deleteDeviceFromCapture(device.sessionId, device.mac) { success ->
                            if (success) {
                                // Remove apenas o dispositivo específico da lista local
                                val newList = currentList.filterNot {
                                    it.mac == device.mac && it.sessionId == device.sessionId
                                }
                                submitList(newList)
                                //binding.root.context.showMessage("Device capture deleted")
                            } else {
                                //binding.root.context.showMessage("Failed to delete device capture")
                            }
                        }
                    }
                    .show()
            }
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