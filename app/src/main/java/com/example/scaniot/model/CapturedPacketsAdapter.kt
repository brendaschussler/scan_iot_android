package com.example.scaniot.model

import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.scaniot.databinding.ItemCapturedSessionBinding
import com.example.scaniot.model.CaptureRepository.suspendDeleteDeviceCapture
import com.example.scaniot.utils.RootUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                    txtSessionStartDate.text = "Started: ${dateFormat.format(Date(device.lastCaptureTimestamp))}"
                }

                if (device.endDate != null) {
                    if (device.capturing) {
                        txtSessionEndDate.visibility = View.GONE
                    } else {
                        txtSessionEndDate.visibility = View.VISIBLE
                        txtSessionEndDate.text = "Finished: ${dateFormat.format(Date(device.endDate))}"
                    }
                }

                txtSessionId.text = "Device: ${device.name}"
                txtDevicesCount.text = "MAC: ${device.mac}"
                txtCaptureType.text = "Output Filename: ${device.filename}_${device.mac}_${device.sessionId}"
                //txtCaptureType.text = "Type: ${if (device.timeLimitMs > 0) "TIME LIMIT" else "PACKET COUNT"}"

                progressBarSession.max = device.captureTotal
                progressBarSession.progress = device.captureProgress

                val percent = if (device.captureTotal > 0) {
                    (device.captureProgress * 100) / device.captureTotal
                } else {
                    0
                }

                // Determinar o tipo de captura e formatar as mensagens
                val captureType = if (device.timeLimitMs > 0) {
                    // Formatar o tempo limite para uma string legível
                    val hours = device.timeLimitMs / (1000 * 60 * 60)
                    val minutes = (device.timeLimitMs % (1000 * 60 * 60)) / (1000 * 60)
                    val seconds = (device.timeLimitMs % (1000 * 60)) / 1000
                    String.format("Time Limit (%02d:%02d:%02d)", hours, minutes, seconds)
                } else {
                    "Number of Packets (${device.captureTotal})"
                }

                txtSessionStatus.text = if (device.capturing) {
                    "Active Capture by $captureType ($percent%)"
                } else {
                    "Completed: ${if (device.timeLimitMs > 0) "Time Limit Reached" else "Packets Captured (${device.captureProgress}/${device.captureTotal})"}"
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

                    RootUtils.checkRootAccess(binding.root.context) { hasRoot ->
                        if (hasRoot) {
                            executeStopCapture(device)
                        } else {
                            // Mensagem de erro já é mostrada pelo RootUtils
                        }
                    }

                }
                .show()
        }

        private fun executeStopCapture(device: Device) {
            // 1. Primeiro atualiza a UI imediatamente (feedback visual rápido)
            val currentList = currentList.toMutableList()
            val position = currentList.indexOfFirst { it.mac == device.mac && it.sessionId == device.sessionId }

            if (position != -1) {
                val updatedDevice = currentList[position].copy(capturing = false)
                currentList[position] = updatedDevice
                submitList(currentList)
                notifyItemChanged(position) // Força a atualização do item específico
            }

            // 2. Depois executa as operações em background
            Thread {
                // Operações de I/O (banco de dados e rede)
                val packetCapturer = PacketCapturer(binding.root.context)
                packetCapturer.stopDeviceCapture(device)
                CaptureRepository.updateDeviceCaptureState(device.sessionId, device.mac, false)

                // 3. Verificação final para garantir consistência
                Handler(Looper.getMainLooper()).post {
                    // Atualiza novamente para garantir sincronização
                    val freshList = currentList.toMutableList()
                    val freshPosition = freshList.indexOfFirst { it.mac == device.mac && it.sessionId == device.sessionId }

                    if (freshPosition != -1) {
                        val finalDevice = freshList[freshPosition].copy(capturing = false)
                        freshList[freshPosition] = finalDevice
                        submitList(freshList)
                    }
                }
            }.start()
        }

        private fun deleteDevice(device: Device) {
            AlertDialog.Builder(binding.root.context)
                .setTitle("Delete Device")
                .setMessage("Delete capture data for ${device.name}?")
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                .setPositiveButton("Delete") { _, _ ->
                    // Iniciar uma corrotina para a operação de delete
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val success = withContext(Dispatchers.IO) {
                                // Chamada suspensa para a operação de delete
                                suspendDeleteDeviceCapture(device.sessionId, device.mac)
                            }

                            withContext(Dispatchers.Main) {
                                if (success) {
                                    // Remove da lista local
                                    val newList = currentList.toMutableList().apply {
                                        removeAt(adapterPosition)
                                    }
                                    submitList(newList)
                                    Toast.makeText(
                                        binding.root.context,
                                        "Capture deleted",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    binding.root.context,
                                    "Error deleting capture: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
                .show()
        }
    }

    private class DeviceDiffCallback : DiffUtil.ItemCallback<Device>() {
        override fun areItemsTheSame(oldItem: Device, newItem: Device): Boolean {
            return oldItem.mac == newItem.mac &&
                   oldItem.sessionId == newItem.sessionId &&
                   oldItem.capturing == newItem.capturing
        }

        override fun areContentsTheSame(oldItem: Device, newItem: Device): Boolean {
            return oldItem == newItem
        }
    }
}