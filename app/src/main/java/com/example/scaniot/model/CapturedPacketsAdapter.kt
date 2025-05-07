// CapturedPacketsAdapter.kt
package com.example.scaniot.model

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.scaniot.databinding.ItemCapturedSessionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CapturedPacketsAdapter : ListAdapter<CaptureSession, CapturedPacketsAdapter.CapturedSessionViewHolder>(SessionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CapturedSessionViewHolder {
        val binding = ItemCapturedSessionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CapturedSessionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CapturedSessionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CapturedSessionViewHolder(
        private val binding: ItemCapturedSessionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: CaptureSession) {
            binding.apply {
                // Formatação da data
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                txtSessionDate.text = dateFormat.format(Date(session.timestamp))

                // Configuração dos dados da sessão
                txtSessionId.text = "Session: ${session.sessionId.take(8)}..."
                txtDevicesCount.text = "Devices: ${session.devices.size}"
                txtCaptureType.text = "Type: ${session.captureType.replace("_", " ")}"

                // Configuração do status
                txtSessionStatus.text = if (session.isActive) {
                    "Active (${session.captureProgress}/${session.captureTotal})"
                } else {
                    "Completed"
                }

                // Botões de ação
                btnStopSession.visibility = if (session.isActive) View.VISIBLE else View.GONE
                btnStopSession.setOnClickListener { stopSession(session) }
                btnDeleteSession.setOnClickListener { deleteSession(session) }
                btnViewDevices.setOnClickListener { viewDevices(session) }
            }
        }

        private fun stopSession(session: CaptureSession) {
            AlertDialog.Builder(binding.root.context)
                .setTitle("Stop Session")
                .setMessage("Stop this capture session?")
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                .setPositiveButton("Stop") { _, _ ->

                    val packetCapturer = PacketCapturer(binding.root.context)
                    packetCapturer.stopCapture(session.sessionId)

                    CaptureRepository.updateCaptureState(session.sessionId, false)

                    // Update UI
                    val updatedSession = session.copy(isActive = false)
                    val newList = currentList.toMutableList().apply {
                        set(adapterPosition, updatedSession)
                    }
                    submitList(newList)
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
    }

    private class SessionDiffCallback : DiffUtil.ItemCallback<CaptureSession>() {
        override fun areItemsTheSame(oldItem: CaptureSession, newItem: CaptureSession): Boolean {
            return oldItem.sessionId == newItem.sessionId
        }

        override fun areContentsTheSame(oldItem: CaptureSession, newItem: CaptureSession): Boolean {
            return oldItem == newItem
        }
    }
}