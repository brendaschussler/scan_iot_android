package com.example.scaniot.model

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.scaniot.databinding.ItemDeviceBinding
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.scaniot.R

class ScanDevicesAdapter(
    private val onEditClick: (Device) -> Unit,
    private val savedDevices: List<Device> = emptyList(),
    private val scannedNotSavedDevices: List<Device> = emptyList()
) : RecyclerView.Adapter<ScanDevicesAdapter.ScanDeviceViewHolder>() {

    private var listScanDevices = emptyList<Device>()

    fun addList(myList: List<Device>) {
        listScanDevices = myList
        notifyDataSetChanged()
    }

    inner class ScanDeviceViewHolder(
        private val binding: ItemDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: Device) {
            binding.apply {
                txtDeviceName.text = device.name
                txtDescription.text = device.description
                txtMacAdress.text = device.mac
                txtIpAdress.text = device.ip
                txtVendor.text = device.vendor
                txtModel.text = device.deviceModel
                txtLocation.text = device.deviceLocation
                txtVersion.text = device.deviceVersion
                txtType.text = device.deviceType
                txtCategory.text = device.deviceCategory ?: "Not specified"
                imgNew.visibility = if (device.isNew) View.VISIBLE else View.GONE

                val isNewDevice = !savedDevices.any { it.mac == device.mac } &&
                        !scannedNotSavedDevices.any { it.mac == device.mac }

                imgNew.visibility = if (isNewDevice) View.VISIBLE else View.GONE

                if (device.photoUrl != null) {
                    Glide.with(itemView.context)
                        .load(device.photoUrl)
                        .placeholder(R.drawable.ic_device_unknown) // Img default
                        .into(imgDevice)
                } else {
                    imgDevice.setImageResource(R.drawable.ic_device_unknown)
                }

                imgNew.visibility = if (device.isNew) View.VISIBLE else View.GONE

                btnEditDevice.setOnClickListener {
                    onEditClick(device)
                }

            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScanDeviceViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemDeviceBinding.inflate(inflater, parent, false)
        return ScanDeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ScanDeviceViewHolder, position: Int) {
        holder.bind(listScanDevices[position])
    }

    override fun getItemCount(): Int = listScanDevices.size
}