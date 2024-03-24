//
//  MatterportAxisManager.swift
//  Matterport Axis Controller
//
//  Created by RyoTN on 2022/12/19.
//

import CoreBluetooth
import Foundation
import UIKit

protocol MatterportAxisManagerDelegate {
    func changeBtState(msg: String)
    func connected()
    func connectFailure()
    func disconnected()
    func receiveAngle()
}

class MatterportAxisManager: NSObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    private let CONNECT_DEVICE_NAME = "Matterport Axis"
    private let SERVICE_UUID = CBUUID(string: "FFE0")
    private let WRITE_CHARACTERISTIC_UUID = CBUUID(string: "FFE1")
    private let NOTIFY_CHARACTERISTIC_UUID = CBUUID(string: "FFE4")

    private var mBtStatus: Bool = false
    private var connected: Bool = false
    private var mAngle = 0
    private var mCentralManager: CBCentralManager!
    private var mPeripheral: CBPeripheral? = nil
    private var mWriteCharacteristic: CBCharacteristic? = nil
    private var mNotifyCharacteristic: CBCharacteristic? = nil

    private var delegate: MatterportAxisManagerDelegate!

    init(delegate: MatterportAxisManagerDelegate) {
        super.init()
        self.delegate = delegate
        mCentralManager = CBCentralManager(delegate: self, queue: nil)
    }

    func getBtStatus() -> Bool {
        return mBtStatus
    }

    func getAngle() -> Int {
        return mAngle
    }

    func isConnected() -> Bool {
        return connected
    }

    func resetAngle() {
        var zeroDegree = 360 - mAngle
        if zeroDegree > 0xFF {
            sendAngle(angle: 0xFF)
            zeroDegree = zeroDegree - 0xFF
        }
        sendAngle(angle: UInt8(zeroDegree))
    }

    func connect() {
        if mCentralManager.isScanning {
            mCentralManager.stopScan()
        }

        mCentralManager.scanForPeripherals(withServices: [SERVICE_UUID])
    }

    func disconnect() {
        connected = false
        if let p = mPeripheral {
            mCentralManager.cancelPeripheralConnection(p)
        }
        mPeripheral = nil
    }

    func sendAngle(angle: UInt8) {
        let sendAngle: UInt8 = angle
        let sendData = Data([0x00, 0x00, sendAngle, 0x00, 0x00, sendAngle])
        if let peripheral = mPeripheral, let writeCharacteristic = mWriteCharacteristic {
            peripheral.writeValue(sendData, for: writeCharacteristic, type: CBCharacteristicWriteType.withResponse)
        }
    }

    // MARK: - CBCentralManagerDelegate

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        var msg = "Bluetooth Unknown Error"
        mBtStatus = false
        switch central.state {
        case CBManagerState.poweredOn:
            print("Bluetooth PoweredON")
            msg = "Bluetooth PowerON"
            mBtStatus = true
        case CBManagerState.poweredOff:
            print("Bluetooth PoweredOff")
            msg = "Bluetooth PoweredOff"
        case CBManagerState.resetting:
            print("Bluetooth resetting")
            msg = "Bluetooth resetting"
        case CBManagerState.unauthorized:
            print("Bluetooth unauthorized")
            msg = "Bluetooth unauthorized"
        case CBManagerState.unknown:
            print("Bluetooth unknown")
            msg = "Bluetooth unknown"
        case CBManagerState.unsupported:
            print("Bluetooth unsupported")
            msg = "Bluetooth unsupported"
            break
        @unknown default:
            break
        }

        delegate.changeBtState(msg: msg)
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData _: [String: Any], rssi _: NSNumber) {
        if peripheral.name == CONNECT_DEVICE_NAME {
            mPeripheral = peripheral
            central.connect(peripheral, options: nil)
            mCentralManager.stopScan()
        }
    }

    func centralManager(_: CBCentralManager, didConnect peripheral: CBPeripheral) {
        print("Connection success")

        connected = true
        delegate.connected()

        peripheral.delegate = self
        peripheral.discoverServices([SERVICE_UUID])
    }

    func centralManager(_: CBCentralManager, didFailToConnect _: CBPeripheral, error _: Error?) {
        print("Connection failure")
        connected = false
        delegate.connectFailure()
        disconnect()
    }

    func centralManager(_: CBCentralManager, didDisconnectPeripheral _: CBPeripheral, error _: Error?) {
        print("Disconnect")
        connected = false
        delegate.disconnected()
        disconnect()
    }

    // MARK: - CBPeripheralDelegate

    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices _: Error?) {
        peripheral.discoverCharacteristics([WRITE_CHARACTERISTIC_UUID, NOTIFY_CHARACTERISTIC_UUID],
                                           for: (peripheral.services?.first)!)
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error _: Error?) {
        for characteristic in service.characteristics! {
            if characteristic.uuid.uuidString == WRITE_CHARACTERISTIC_UUID.uuidString {
                mWriteCharacteristic = characteristic
            }
            if characteristic.uuid.uuidString == NOTIFY_CHARACTERISTIC_UUID.uuidString {
                mNotifyCharacteristic = characteristic
                if let nc = mNotifyCharacteristic {
                    peripheral.setNotifyValue(true, for: nc)
                }
            }
            print("Found Characteristic:", characteristic.uuid.uuidString)
        }
    }

    func peripheral(_: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        if let error = error {
            print("Write Error:", error.localizedDescription)
            return
        } else {
            print("Write Success:", characteristic.uuid)
        }
    }

    func peripheral(_: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        print("Receive Notify UUID:", characteristic.uuid.uuidString)
        if let error = error {
            print("Recive error:", error.localizedDescription)
        } else {
            if let receivedData = characteristic.value {
                mAngle = (Int(receivedData[2]) * 256) + Int(receivedData[3])
                delegate.receiveAngle()
            }
        }
    }
}
