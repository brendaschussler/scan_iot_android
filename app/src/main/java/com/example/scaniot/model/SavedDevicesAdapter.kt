package com.example.scaniot.model

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.scaniot.R
import com.example.scaniot.databinding.ItemSavedDeviceBinding

class SavedDevicesAdapter(
    private val onDeleteClick: (Device) -> Unit,
    private val onCheckboxChange: (Device, Boolean) -> Unit
) : RecyclerView.Adapter<SavedDevicesAdapter.SavedDeviceViewHolder>() {

    private var devicesList = emptyList<Device>()
    private val selectedDevices = mutableSetOf<String>()

    fun getSelectedDevices(): List<Device> {
        return devicesList.filter { selectedDevices.contains(it.mac) }
    }

    fun submitList(newList: List<Device>) {
        selectedDevices.retainAll { mac -> newList.any { it.mac == mac } }

        devicesList = newList.sortedBy { it.name?.lowercase() }
        notifyDataSetChanged()
    }

    inner class SavedDeviceViewHolder(
        private val binding: ItemSavedDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: Device) {
            binding.apply {
                txtDeviceNameSaved.text = device.name
                txtDescriptionSaved.text = device.description
                txtMacAdressSaved.text = device.mac
                txtIpAdressSaved.text = device.ip
                txtVendorSaved.text = device.vendor
                txtModelSaved.text = device.deviceModel ?: "Unknown"
                txtLocationSaved.text = device.deviceLocation ?: "Not specified"
                txtVersionSaved.text = device.deviceVersion ?: "Not specified"
                txtTypeSaved.text = device.deviceType ?: "Not specified"
                txtCategorySaved.text = device.deviceCategory ?: "Not specified"


                if (device.photoUrl != null) {
                    Glide.with(itemView.context)
                        .load(device.photoUrl)
                        .placeholder(R.drawable.ic_device_unknown)
                        .into(imgDeviceSaved)
                } else {
                    imgDeviceSaved.setImageResource(R.drawable.ic_device_unknown)
                }

                btnDeleteDevice.setOnClickListener {
                    onDeleteClick(device)
                }

                checkBoxSavedDevice.setOnCheckedChangeListener(null)

                checkBoxSavedDevice.isChecked = selectedDevices.contains(device.mac)

                checkBoxSavedDevice.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedDevices.add(device.mac)
                    } else {
                        selectedDevices.remove(device.mac)
                    }
                    onCheckboxChange(device, isChecked)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavedDeviceViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemSavedDeviceBinding.inflate(inflater, parent, false)
        return SavedDeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SavedDeviceViewHolder, position: Int) {
        holder.bind(devicesList[position])
    }

    override fun getItemCount(): Int = devicesList.size
}