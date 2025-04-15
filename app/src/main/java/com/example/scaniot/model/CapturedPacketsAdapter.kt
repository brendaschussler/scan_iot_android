package com.example.scaniot.model

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.scaniot.databinding.ItemCapturedPacketsBinding

class CapturedPacketsAdapter : RecyclerView.Adapter<CapturedPacketsAdapter.CapturedPacketViewHolder>() {

    private var devicesList = emptyList<Device>()

    fun submitList(newList: List<Device>) {
        devicesList = newList
        notifyDataSetChanged()
    }

    inner class CapturedPacketViewHolder(
        private val binding: ItemCapturedPacketsBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: Device) {
            binding.apply {
                txtDeviceNameCaptured.text = device.name
                txtMacAdressCapturedPackets.text = device.mac

                progressBarCapturedPackets.max = device.captureTotal
                progressBarCapturedPackets.progress = device.captureProgress
                txtNumberPacketsCaptured.text = "${device.captureProgress}/${device.captureTotal}"
                txtPercentProgress.text = "${(device.captureProgress * 100 / device.captureTotal)}%"

                // Atualiza a UI baseado no estado de captura
                btnStopCapture.visibility = if (device.capturing) View.VISIBLE else View.GONE
                btnStopCapture.setOnClickListener {
                    stopCapture(device)
                }
            }
        }

        private fun stopCapture(device: Device) {
            // Atualiza localmente
            val updatedDevice = device.copy(capturing = false)
            val newList = devicesList.toMutableList().apply {
                set(indexOf(device), updatedDevice)
            }
            submitList(newList)

            // Atualiza no Firestore
            CaptureRepository.updateCaptureState(updatedDevice, false)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CapturedPacketViewHolder {
        val binding = ItemCapturedPacketsBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CapturedPacketViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CapturedPacketViewHolder, position: Int) {
        holder.bind(devicesList[position])
    }

    override fun getItemCount(): Int = devicesList.size
}

