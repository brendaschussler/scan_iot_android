package com.example.scaniot.model

import android.animation.ObjectAnimator
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

    private var lastUpdateTime = 0L

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

    private var lastList: List<Device> = emptyList()

    override fun submitList(list: List<Device>?) {
        val newList = list ?: emptyList()
        if (newList != lastList) {
            lastList = newList
            super.submitList(newList)
        }
    }

    inner class CapturedPacketViewHolder(
        private val binding: ItemCapturedPacketsBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: Device) {
            binding.apply {
                // Configuração dos dados
                txtDeviceNameCaptured.text = device.name
                txtMacAdressCapturedPackets.text = device.mac

                // Formatação da data
                val timestamp = device.lastCaptureTimestamp ?: System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                txtCaptureDate.text = dateFormat.format(Date(timestamp))

                // Configuração da progress bar
                if (device.timeLimitMs > 0) {
                    val progress = calculateTimeProgress(device)
                    val totalHours = device.timeLimitMs / (3600 * 1000f)

                    // Garante que o tempo decorrido não exceda o limite
                    val elapsedHours = (progress / 100 * totalHours).coerceAtMost(totalHours)

                    progressBarCapturedPackets.max = 100
                    progressBarCapturedPackets.progress = progress.toInt()
                    txtPercentProgress.text = "${progress.toInt()}%"
                    txtNumberPacketsCaptured.text = "Time: ${"%.1f".format(elapsedHours)}/${"%.1f".format(totalHours)}h"
                } else {
                    val total = if (device.captureTotal == 0) 1 else device.captureTotal
                    txtNumberPacketsCaptured.text = "Packets: ${device.captureProgress}/${device.captureTotal}"
                    progressBarCapturedPackets.max = total
                    progressBarCapturedPackets.progress = device.captureProgress
                    txtPercentProgress.text = "${(device.captureProgress * 100 / total)}%"
                }

                // Animação suave da progress bar
                ObjectAnimator.ofInt(progressBarCapturedPackets, "progress", progressBarCapturedPackets.progress)
                    .setDuration(500)
                    .start()

                // Controles de captura
                btnStopCapture.visibility = if (device.capturing) View.VISIBLE else View.GONE
                btnStopCapture.setOnClickListener { stopCapture(device) }
                btnDeleteCapture.setOnClickListener { deleteCapture(device) }
            }
        }

        private fun calculateTimeProgress(device: Device): Float {
            if (device.timeLimitMs <= 0) return 0f

            val currentTime = System.currentTimeMillis()
            val startTime = device.lastCaptureTimestamp ?: currentTime
            val elapsed = (currentTime - startTime).coerceAtMost(device.timeLimitMs) // Impede que exceda o limite

            return elapsed.toFloat() / device.timeLimitMs * 100
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
                                val newList = currentList.filterNot {
                                    it.mac == device.mac && it.sessionId == device.sessionId
                                }
                                submitList(newList)
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
            return oldItem.captureProgress == newItem.captureProgress &&
                    oldItem.capturing == newItem.capturing &&
                    oldItem.lastCaptureTimestamp == newItem.lastCaptureTimestamp
        }
    }

}