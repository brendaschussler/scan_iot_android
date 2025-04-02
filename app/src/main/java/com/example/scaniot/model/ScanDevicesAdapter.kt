package com.example.scaniot.model

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.example.scaniot.databinding.ItemDeviceBinding
import androidx.recyclerview.widget.RecyclerView

class ScanDevicesAdapter(
    private val onEditClick: (Device) -> Unit
) : RecyclerView.Adapter<ScanDevicesAdapter.ScanDeviceViewHolder>() {

    private var listScanDevices = emptyList<Device>()

    fun addList(myList: List<Device>) {
        listScanDevices = myList
        notifyDataSetChanged() // Adicionado para atualizar a RecyclerView
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
                txtManufacturer.text = device.manufacturer

                btnEditDevice.setOnClickListener {
                    onEditClick(device)
                }

                // Esconde o botão de salvar pois não será usado agora
                btnSaveDevice.visibility = View.GONE
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