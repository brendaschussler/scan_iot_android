package com.example.scaniot.model

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.example.scaniot.databinding.ItemDeviceBinding
import androidx.recyclerview.widget.RecyclerView

class ScanDevicesAdapter : RecyclerView.Adapter<ScanDevicesAdapter.ScanDeviceViewHolder>() {

    private var listScanDevices = emptyList<Device>()

    fun addList(myList: List<Device>) {
        listScanDevices = myList
        notifyDataSetChanged() // Adicionado para atualizar a RecyclerView
    }

    inner class ScanDeviceViewHolder(
        private val binding: ItemDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: Device) {
            binding.txtDeviceName.text = device.name
            binding.txtDescription.text = device.description
            binding.txtMacAdress.text = device.mac
            binding.txtIpAdress.text = device.ip
            binding.txtManufacturer.text = device.manufacturer
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