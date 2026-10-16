package com.pcopi.usbimager

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import me.jahnen.libaums.core.driver.BlockDeviceDriver
import me.jahnen.libaums.core.driver.BlockDeviceDriverFactory
import me.jahnen.libaums.core.usb.UsbCommunication
import me.jahnen.libaums.core.usb.UsbCommunicationFactory
import java.io.Closeable
import java.io.IOException

class UsbRawDevice private constructor(
    private val communication: UsbCommunication,
    val blockDevice: BlockDeviceDriver
) : Closeable {

    val blockSize: Int get() = blockDevice.blockSize
    val capacityBytes: Long get() = blockDevice.blocks * blockDevice.blockSize.toLong()

    fun init() {
        blockDevice.init()
        if (blockDevice.blockSize <= 0 || blockDevice.blocks <= 0L) {
            throw IOException("USB รายงานความจุไม่ถูกต้อง")
        }
    }

    override fun close() {
        runCatching { communication.close() }
    }

    companion object {
        private const val SUBCLASS_SCSI = 6
        private const val PROTOCOL_BULK_ONLY = 80

        fun isSupportedMassStorage(device: UsbDevice): Boolean =
            findMassStorageInterface(device) != null

        fun findMassStorageInterface(device: UsbDevice): UsbInterface? {
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                if (intf.interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE &&
                    intf.interfaceSubclass == SUBCLASS_SCSI &&
                    intf.interfaceProtocol == PROTOCOL_BULK_ONLY
                ) {
                    return intf
                }
            }
            return null
        }

        @Throws(IOException::class)
        fun open(usbManager: UsbManager, device: UsbDevice): UsbRawDevice {
            if (!usbManager.hasPermission(device)) {
                throw SecurityException("ยังไม่ได้รับสิทธิ์ใช้งาน USB")
            }

            val intf = findMassStorageInterface(device)
                ?: throw IOException("USB นี้ไม่ใช่ Mass Storage แบบ SCSI/Bulk-Only ที่ V1 รองรับ")

            var inEndpoint: UsbEndpoint? = null
            var outEndpoint: UsbEndpoint? = null
            for (i in 0 until intf.endpointCount) {
                val endpoint = intf.getEndpoint(i)
                if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                        inEndpoint = endpoint
                    } else if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
                        outEndpoint = endpoint
                    }
                }
            }

            val input = inEndpoint ?: throw IOException("ไม่พบ USB Bulk IN endpoint")
            val output = outEndpoint ?: throw IOException("ไม่พบ USB Bulk OUT endpoint")

            val communication = UsbCommunicationFactory.createUsbCommunication(
                usbManager,
                device,
                intf,
                output,
                input
            )
            val driver = BlockDeviceDriverFactory.createBlockDevice(communication, 0.toByte())
            return UsbRawDevice(communication, driver)
        }
    }
}
