//
//  MatterportAxisManager.swift
//  Matterport Axis Controller
//
//  Created by RyoTN on 2022/12/19.
//

import Foundation
import UIKit
import CoreBluetooth

protocol MatterportAxisManagerDelegate {
    func changeBtState(msg:String)
    func connected()
    func connectFailure()
    func disconnected()
    func receiveAngle()
}

class MatterportAxisManager: NSObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    
    private let CONNECT_DEVICE_NAME = "Matterport Axis"
    private let SERVICE_UUID = CBUUID.init(string: "FFE0")
    private let WRITE_CHARACTERISTIC_UUID = CBUUID.init(string: "FFE1")
    private let NOTIFY_CHARACTERISTIC_UUID = CBUUID.init(string: "FFE4")
    
    private var mBtStatus: Bool = false
    private var mBtConnected: Bool = false
    private var mAngle = 0
    private var mCentralManager: CBCentralManager!
    private var mPeripheral: CBPeripheral? = nil
    private var mWriteCharacteristic: CBCharacteristic? = nil
    private var mNotifyCharacteristic: CBCharacteristic? = nil
    
    private var delegate: MatterportAxisManagerDelegate!
    
    init(delegate: MatterportAxisManagerDelegate) {
        super.init()
        self.delegate = delegate
        mCentralManager = CBCentralManager.init(delegate: self, queue: nil)
    }
    
    func getBtStatus() -> Bool {
        return mBtStatus
    }
    
    func getAngle() -> Int {
        return mAngle
    }
    
    func getBtConnected() -> Bool {
        return mBtConnected
    }
    
    func resetAngle() {
        var zeroDegree = 360 - mAngle
        if (zeroDegree > 0xFF){
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
        mBtConnected = false
        if let p = mPeripheral {
            mCentralManager.cancelPeripheralConnection(p)
        }
        mPeripheral = nil
    }
    
    func sendAngle(angle: UInt8) {
        let sendAngle: UInt8 = angle
        let sendData = Data([0x00, 0x00, sendAngle, 0x00, 0x00, sendAngle])
        if let peripheral = self.mPeripheral,let writeCharacteristic = self.mWriteCharacteristic{
            peripheral.writeValue(sendData, for: writeCharacteristic, type: CBCharacteristicWriteType.withResponse)
        }
    }
    
//MARK: - CBCentralManagerDelegate
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        var msg = "Bluetooth Unknown Error"
        switch central.state {
            case CBManagerState.poweredOn:
                print("Bluetooth PoweredON")
                msg = "Bluetooth PowerON"
                mBtStatus = true
                break
            case CBManagerState.poweredOff:
                print("Bluetooth PoweredOff")
                msg = "Bluetooth PoweredOff"
                break
            case CBManagerState.resetting:
                print("Bluetooth resetting")
                msg = "Bluetooth resetting"
                break
            case CBManagerState.unauthorized:
                print("Bluetooth unauthorized")
                msg = "Bluetooth unauthorized"
                break
            case CBManagerState.unknown:
                print("Bluetooth unknown")
                msg = "Bluetooth unknown"
                break
            case CBManagerState.unsupported:
                print("Bluetooth unsupported")
                msg = "Bluetooth unsupported"
                break
            @unknown default:
                break
        }
        
        delegate.changeBtState(msg: msg)
    }
    
    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        if peripheral.name == CONNECT_DEVICE_NAME {
            self.mPeripheral = peripheral
            central.connect(peripheral, options: nil)
            mCentralManager.stopScan()
        }
    }
    
    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        print("Connection success")
        
        mBtConnected = true
        delegate.connected()
        
        peripheral.delegate = self
        peripheral.discoverServices([SERVICE_UUID])
    }
    
    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        print("Connection failure")
        mBtConnected = false
        delegate.connectFailure()
        disconnect()
    }
    
    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        print("Disconnect")
        mBtConnected = false
        delegate.disconnected()
        disconnect()
    }
    
//MARK: - CBPeripheralDelegate
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        peripheral.discoverCharacteristics([WRITE_CHARACTERISTIC_UUID, NOTIFY_CHARACTERISTIC_UUID],
                                           for: (peripheral.services?.first)!)
    }
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        for characteristic in service.characteristics!{
            if characteristic.uuid.uuidString == WRITE_CHARACTERISTIC_UUID.uuidString {
                mWriteCharacteristic = characteristic
            }
            if characteristic.uuid.uuidString == NOTIFY_CHARACTERISTIC_UUID.uuidString {
                mNotifyCharacteristic = characteristic
                if let nc = mNotifyCharacteristic {
                    peripheral.setNotifyValue(true, for: nc)
                }
            }
            print("Found Characteristic:",characteristic.uuid.uuidString)
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        if let error = error {
            print("Write Error:",error.localizedDescription)
            return
        }else{
            print("Write Success:",characteristic.uuid)
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        print("Receive Notify UUID:",characteristic.uuid.uuidString)
        if let error = error {
            print("Recive error:",error.localizedDescription)
        } else {
            if let receivedData = characteristic.value {
                mAngle = (Int(receivedData[2]) * 256) + Int(receivedData[3])
                delegate.receiveAngle()
            }
        }
    }
}
