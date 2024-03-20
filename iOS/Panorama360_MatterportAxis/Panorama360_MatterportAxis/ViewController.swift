//
//  ViewController.swift
//  Panorama360_MatterportAxis
//
//  Created by RyoTN on 2022/12/22.
//

import UIKit
import AVFAudio
import AVFoundation

class ViewController: UIViewController ,MatterportAxisManagerDelegate ,CameraCaptureDelegate{
    private let EXPOSURES_VALUE: [[Float]] = [[0.0], [1.0, 0.0, -1.0], [2.0, 1.0, 0.0, -1.0, -2.0], [3.0, 2.0, 1.0, 0.0, -1.0, -2.0, -3.0]]

    @IBOutlet weak var imgCameraPreview: UIImageView!
    @IBOutlet weak var btnConnect: UIButton!
    @IBOutlet weak var btnStartCapture: UIButton!
    @IBOutlet weak var lblStatus: UILabel!
    @IBOutlet weak var lblFocus: UILabel!
    @IBOutlet weak var segCameraLenz: UISegmentedControl!
    @IBOutlet weak var sldFocus: UISlider!
    @IBOutlet weak var btnExposureBracketMode: UIButton!
    
    
    private var mMatterportAxisManager: MatterportAxisManager!
    private var mAutoRotationFlg = false
    private var isSavePhoto = false
    private var capCount = 0
    private var autoRotationAngle = 30
    private var mExposureMode = 3
    private var mBracketCount = 0
    
    private var mCameraCapture: CameraCapture!
    
    private var mFileSaveManager: FileSaveManager!
    
    private var mStartSound: AVAudioPlayer!
    private var mCompSound: AVAudioPlayer!
    
    override func viewDidLoad() {
        super.viewDidLoad()
        mMatterportAxisManager = MatterportAxisManager(delegate: self)
        mCameraCapture = CameraCapture(view: imgCameraPreview,delegate: self)
        mCameraCapture.startListeningVolumeButton(view: self.view)
        mFileSaveManager = FileSaveManager()
        UIApplication.shared.isIdleTimerDisabled = true
        if let soundStartURL = Bundle.main.url(forResource: "start", withExtension: "mp3") {
            do {
                mStartSound = try AVAudioPlayer(contentsOf: soundStartURL)
            } catch {
                print("Sound Loading error")
            }
        }
        if let soundCompURL = Bundle.main.url(forResource: "comp", withExtension: "mp3") {
            do {
                mCompSound = try AVAudioPlayer(contentsOf: soundCompURL)
            } catch {
                print("Sound Loading error")
            }
        }
        // Do any additional setup after loading the view.
    }

    @IBAction func pushConnect(_ sender: UIButton) {
        if(mMatterportAxisManager.isConnected()) {
            mMatterportAxisManager.disconnect()
        } else {
            mMatterportAxisManager.connect()
        }
        sender.isEnabled = false
    }
    
    @IBAction func pushAngleReset(_ sender: UIButton) {
        if(mMatterportAxisManager.isConnected()) {
            mMatterportAxisManager.resetAngle()
        }
    }
    
    @IBAction func pushStartCapture(_ sender: UIButton) {
        if (!mAutoRotationFlg) {
            startCapture()
        } else {
            stopCapture()
        }
    }
    
    @IBAction func pushTestCap(_ sender: UIButton) {
        mCameraCapture.savePhoto(exposureValues: EXPOSURES_VALUE[mExposureMode])
    }

    @IBAction func pushCreateDir(_ sender: UIButton) {
        capCount = 0
        let result = mFileSaveManager.reCreateDir()
        if result {
            Toast.show("Successfully created directory", self.view)
        } else {
            Toast.show("Failed to create directory", self.view)
        }
    }
    
    @IBAction func changeFocus(_ sender: UISlider) {
        let value = round(sender.value * 10.0) / 10.0
        sender.value = value
        lblFocus.text = String(format: "%.1f", value)
        mCameraCapture.setFocus(position: value)
    }
    
    func startCapture() {
        if mAutoRotationFlg { return }
        btnStartCapture.isEnabled = false
        btnStartCapture.setTitle("Stop Capture", for: UIControl.State.normal)
        mStartSound.play()
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            self.btnStartCapture.isEnabled = true
            self.savePhoto()
        }
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            self.mAutoRotationFlg = true
        }
    }
    
    func stopCapture() {
        btnStartCapture.isEnabled = false
        mAutoRotationFlg = false
        btnStartCapture.setTitle("Start Capture", for: UIControl.State.normal)
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            self.btnStartCapture.isEnabled = true
        }
    }
    
    func updateStatus() {
        let isConnected = mMatterportAxisManager.isConnected()
        let btConnected = isConnected ? "Connected" : "Disconnected"
        let btnLabel = isConnected ? "Disconnect" : "Connect"
        let angle = mMatterportAxisManager.getAngle()
        
        lblStatus.text = String(format: "%@\nAngle:%d", btConnected, angle)
        btnConnect.setTitle(btnLabel, for: UIControl.State.normal)
        btnConnect.isEnabled = true
    }
    
    func autoRotation() {
        if (!mAutoRotationFlg) {
            return
        }
        
        let angle = mMatterportAxisManager.getAngle()
        if (angle == 0) {
            mAutoRotationFlg = false
            btnStartCapture.setTitle("Start Capture", for: UIControl.State.normal)
            mCompSound.play()
        } else if(angle % autoRotationAngle == 0) {
            if(!isSavePhoto) {
                savePhoto()
            }
            
        }
        
    }
    
    func savePhoto() {
        isSavePhoto = true
        mCameraCapture.savePhoto(exposureValues: EXPOSURES_VALUE[mExposureMode])
    }
    
    
    @IBAction func changeCamera(_ sender: UISegmentedControl) {
        let type = CameraType(rawValue: segCameraLenz.selectedSegmentIndex) ?? .normal
        let focus = sldFocus.value
        
        mCameraCapture.changeCamera(type: type, focus: focus)
    }
    
    @IBAction func changeExposureMode(_ sender: UICommand) {
        switch sender.title {
        case "None":
            mExposureMode = 0
        case "+-1 EV":
            mExposureMode = 1
        case "+-2 EV":
            mExposureMode = 2
        case "+-3 EV":
            mExposureMode = 3
        default : break
        }
        print("changeExposureMode \(mExposureMode)")
    }
    
    @IBAction func changeAutoRotationAngle(_ sender: UISegmentedControl) {
        switch sender.selectedSegmentIndex {
        case 0:
            autoRotationAngle = 15
        case 1:
            autoRotationAngle = 30
        case 2:
            autoRotationAngle = 45
        case 3:
            autoRotationAngle = 60
        default : break
        }
        print("autoRotationAngle:\(autoRotationAngle)")
        Toast.show("Change autoRotationAngle : \(autoRotationAngle)", self.view)
    }
    
    
// MARK: - MatterportAxisManagerDelegate
    func changeBtState(msg: String) {
        updateStatus()
    }
    
    func connected() {
        updateStatus()
    }
    
    func connectFailure() {
        updateStatus()
    }
    
    func disconnected() {
        updateStatus()
    }
    
    func receiveAngle() {
        updateStatus()
        autoRotation()
    }
    
// MARK: - CameraDelegate
    func onPhotoOutput(image: CIImage) {
        var fileName = "\(capCount).jpeg"
        if mExposureMode != 0 {
            let exif = image.properties["{Exif}"] as! [String: Any]
            let bias = exif[String(kCGImagePropertyExifExposureBiasValue)] as! Double
            fileName = "\(capCount)_EV\(Int(round(bias))).jpeg"
        }
        mFileSaveManager.saveImage(image: image, fileName: fileName)
        mBracketCount += 1
        
        if mBracketCount >= EXPOSURES_VALUE[mExposureMode].count {
            capCount += 1
            mBracketCount = 0
            mMatterportAxisManager.sendAngle(angle: UInt8(autoRotationAngle))
            
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                self.isSavePhoto = false
            }
        }
    }
    
    func pushRemoteShutterButton() {
        if mMatterportAxisManager.isConnected() {
            startCapture()
        } else {
            
        }
    }
}

