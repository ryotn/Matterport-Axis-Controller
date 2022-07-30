//
//  ViewController.swift
//  fake Matterport Axis
//
//  Created by RyoTN on 2022/07/30.
//

import UIKit
import CoreBluetooth

class ViewController: UIViewController ,CBPeripheralManagerDelegate ,CBPeripheralDelegate {
    let UUID: String = "C531A7DA-2F3B-4DD9-E79F-7865D56F1A44"
    let DEVICE_NAME: String = "Matterport Axis"
    let SERVICE_UUID: String = "FFE0"
    let SEND_DATA: Data = Data([0x4E,0x00,0x00,0x01,0x00,0x4F])
    
    var peripheralManager: CBPeripheralManager!
    var btState = false
    var notifyState = false
    var tmrNotify: Timer!
    var notifyCharacteristic: CBCharacteristic!

    @IBOutlet weak var lblState: UILabel!
    @IBOutlet weak var btnStartDiscover: UIButton!
    @IBOutlet weak var btnStopDiscover: UIButton!
    @IBOutlet weak var txtFFE1Log: UITextView!
    @IBOutlet weak var txtFFE3Log: UITextView!

    override func viewDidLoad() {
        super.viewDidLoad()
        peripheralManager = CBPeripheralManager.init(delegate: self, queue: nil)
        tmrNotify = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true, block: { Timer in
            if(self.notifyState) {
                self.peripheralManager?.updateValue(self.SEND_DATA, for: self.notifyCharacteristic! as! CBMutableCharacteristic, onSubscribedCentrals: nil)
            }
        })
        
    }
    

    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        switch (peripheral.state){
        case .poweredOn:
            print("PeripheralManager state is ok")
            btState = true

        default:
            print("PeripheralManager state is ng:", peripheral.state)
            btState = false
        }
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        if(error != nil){
            print("Add Service error:", error ?? "")
        }else{
            print("Add Service ok")
            peripheralManager.startAdvertising([
                CBAdvertisementDataLocalNameKey: DEVICE_NAME,
                CBAdvertisementDataServiceUUIDsKey: [SERVICE_UUID]
                ])
        }
    }
    
    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        if (error == nil) {
            lblState.text = "Start Advertising"
        }
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveRead request: CBATTRequest) {
        print("Read Request:", request.characteristic.uuid)
        let reqUUID = request.characteristic.uuid
        if (reqUUID.isEqual(CBUUID.init(string:"FFE2")) || reqUUID.isEqual(CBUUID.init(string:"FFE4"))) {
            request.value = SEND_DATA
            peripheral.respond(to: request, withResult: CBATTError.success)
            print("Read success")
        }else{
            print("Read fail: wrong characteristic uuid:", request.characteristic.uuid)
        }
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveWrite requests: [CBATTRequest]) {
        
        for request in requests {
            let char = request.characteristic
            let reqUUID = char.uuid
            var valueString = ""
            if let value = request.value {
                valueString = value.map {
                    String(format: "%.2hhx/", $0 as CVarArg)
                }.joined()
            } else {
                return
            }

            let writeDateStamp = Date.timeIntervalSinceReferenceDate
            print("Write Request:", reqUUID)
            print("Write Value:", valueString)
            
            if reqUUID.isEqual("FFE1") {
                txtFFE1Log.text.append("\(writeDateStamp):\(valueString)\n")
            } else if reqUUID.isEqual("FFE3") {
                txtFFE3Log.text.append("\(writeDateStamp):\(valueString)\n")
            }
            
            
            peripheralManager?.respond(to: requests[0], withResult: .success)
        }
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didSubscribeTo characteristic: CBCharacteristic) {
        print("Notify Subscribe Request:", characteristic.uuid)
        let reqUUID = characteristic.uuid
        if (reqUUID.isEqual(CBUUID.init(string:"FFE4"))) {
            notifyCharacteristic = characteristic
            notifyState = true
        }
        
    }
    
    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didUnsubscribeFrom characteristic: CBCharacteristic) {
        print("Notify Unsubscribe Request:", characteristic.uuid)
        if(characteristic.uuid == CBUUID.init(string: "FFE4")){
           notifyState = false
        }
    }
    
    @IBAction func pushStartDiscover(_ sender: UIButton) {
        if (btState) {
            peripheralManager!.add(createService())
        }
    }
    
    @IBAction func pushStopDiscover(_ sender: UIButton) {
        if (btState) {
            peripheralManager.stopAdvertising()
            lblState.text = "Stop Advertising"
        }
    }
    
    func createService() -> CBMutableService {
        let charFFE1 = CBMutableCharacteristic(
            type: CBUUID.init(string: "FFE1"),
            properties: CBCharacteristicProperties.write,
            value:nil,
            permissions:CBAttributePermissions.writeable)
        
        let charFFE2 = CBMutableCharacteristic(
            type: CBUUID.init(string: "FFE2"),
            properties: CBCharacteristicProperties.read,
            value:nil,
            permissions:CBAttributePermissions.readable)
    
        let charFFE3 = CBMutableCharacteristic(
            type: CBUUID.init(string: "FFE3"),
            properties: CBCharacteristicProperties.write,
            value:nil,
            permissions:CBAttributePermissions.writeable)
        
        
        let charFFE4 = CBMutableCharacteristic(
            type: CBUUID.init(string: "FFE4"),
            properties: CBCharacteristicProperties.notify,
            value:nil,
            permissions:CBAttributePermissions.readable)
    
        let service = CBMutableService(type: CBUUID.init(string: SERVICE_UUID), primary: true)
        service.characteristics = [charFFE1,charFFE2,charFFE3,charFFE4]
        
        return service

    }

}

