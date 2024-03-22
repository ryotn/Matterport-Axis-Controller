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
    private var autoRotationAngle = 30
    
    private var mCameraCapture: CameraCapture!
    
    private var mStartSound: AVAudioPlayer!
    private var mCompSound: AVAudioPlayer!
    
    override func viewDidLoad() {
        super.viewDidLoad()
        mMatterportAxisManager = MatterportAxisManager(delegate: self)
        mCameraCapture = CameraCapture(view: imgCameraPreview,delegate: self)
        mCameraCapture.startListeningVolumeButton()
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

        NotificationCenter.default.addObserver(forName: UIApplication.willEnterForegroundNotification, object: nil, queue: .main) { notify in
            self.mCameraCapture.setInitalVolume()
        }
        
        NotificationCenter.default.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { notify in
            self.mCameraCapture.stopListeningVolume()
        }
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
        mCameraCapture.startCapture()
    }

    @IBAction func pushCreateDir(_ sender: UIButton) {
        let result = mCameraCapture.createDir()
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
        mCameraCapture.startCapture()
    }
    
    
    @IBAction func changeCamera(_ sender: UISegmentedControl) {
        let type = CameraType(rawValue: segCameraLenz.selectedSegmentIndex) ?? .normal
        let focus = sldFocus.value
        
        if type == .normal {
            autoRotationAngle = 30
        } else {
            autoRotationAngle = 60
        }
        
        mCameraCapture.changeCamera(type: type, focus: focus)
    }
    
    @IBAction func changeExposureMode(_ sender: UICommand) {
        var mode = 0
        switch sender.title {
        case "None":
            mode = 0
        case "+-1 EV":
            mode = 1
        case "+-2 EV":
            mode = 2
        case "+-3 EV":
            mode = 3
        default : break
        }
        mCameraCapture.setExposureMode(mode: mode)
        print("changeExposureMode \(mode)")
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
    func onSuccessCapturePhoto() {
        mMatterportAxisManager.sendAngle(angle: UInt8(autoRotationAngle))
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            self.isSavePhoto = false
        }
    }
    
    func pushRemoteShutterButton() {
        if mMatterportAxisManager.isConnected() {
            startCapture()
        } else {
            
        }
    }
}

