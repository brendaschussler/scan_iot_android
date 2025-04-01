package com.example.scaniot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scaniot.model.Device
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ScanDevicesViewModel : ViewModel() {
    // StateFlows não precisam de asStateFlow() quando declarados corretamente
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun scanNetwork() {
        _isLoading.value = true
        viewModelScope.launch {
            // Simulação de scan
            val fakeDevices = listOf(
                Device(ip = "192.168.1.1", mac = "00:1A:2B:3C:4D:5E"),
                Device(ip = "192.168.1.2", mac = "00:1A:2B:3C:4D:5F")
            )
            _devices.value = fakeDevices
            _isLoading.value = false
        }
    }

    fun saveDevice(device: Device) {
        viewModelScope.launch {
            val updatedList = _devices.value.map {
                if (it.mac == device.mac) device else it
            }
            _devices.value = updatedList
        }
    }

    fun filterDevices(query: String) {
        val currentList = _devices.value
        _devices.value = if (query.isEmpty()) {
            currentList
        } else {
            currentList.filter { device ->
                device.name.contains(query, true) ||
                        device.ip.contains(query, true) ||
                        device.mac.contains(query, true)
            }
        }
    }
}