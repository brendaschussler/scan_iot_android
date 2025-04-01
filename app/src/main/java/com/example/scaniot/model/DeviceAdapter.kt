package com.example.scaniot.model

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.scaniot.R
import com.example.scaniot.databinding.ItemDeviceBinding

class DeviceAdapter(
    private val onEditClick: (Device) -> Unit,
    private val onSaveClick: (Device) -> Unit
) : ListAdapter<Device, DeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    inner class DeviceViewHolder(private val binding: ItemDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(device: Device) {
            binding.apply {
                // Carrega a imagem com Glide
                Glide.with(root.context)
                    .load(device.photoUrl ?: R.drawable.ic_device_unknown)
                    .into(deviceImage)

                deviceName.text = device.name
                deviceIp.text = "IP: ${device.ip}"
                deviceMac.text = "MAC: ${device.mac}"
                deviceManufacturer.text = "Fabricante: ${device.manufacturer}"

                btnEdit.setOnClickListener { onEditClick(device) }
                btnSave.setOnClickListener { onSaveClick(device) }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DeviceDiffCallback : DiffUtil.ItemCallback<Device>() {
        override fun areItemsTheSame(oldItem: Device, newItem: Device): Boolean {
            return oldItem.mac == newItem.mac
        }

        override fun areContentsTheSame(oldItem: Device, newItem: Device): Boolean {
            return oldItem == newItem
        }
    }
}