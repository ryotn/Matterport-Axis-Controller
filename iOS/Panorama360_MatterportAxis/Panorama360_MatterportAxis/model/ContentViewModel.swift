//
//  ContentViewModel.swift
//  Panorama360_MatterportAxis
//
//  Created by RyoTN on 2024/03/22.
//

import AVFAudio
import CoreMotion
import Foundation
import UIKit

class ContentViewModel: ObservableObject {
    let EXPOSURE_MODES_LABEL = ["None", "+-1 EV", "+-2 EV", "+-3 EV"]
    let CAMERA_TYPE_LABEL = ["Wide", "Ultra Wide"]

    // Toast
    @Published var isToastShown = false
    @Published var mToastMsg = ""

    // MatterportAxis
    private var mMatterportAxisManager: MatterportAxisManager?
    private var mAutoRotationAngle = 30
    private var mAutoRotationFlg = false
    private var mReceiveAngleDate = Date()
    private var mSendAngleDate = Date()
    @Published var mAngle = 0
    @Published var isConnected = false
    @Published var isBtStandby = false

    // Camera
    let mPreviewView = PreviewView()
    private var mCameraCapture: CameraCapture?
    private var isSavePhoto = false
    @Published var mFocus: Float = 0.8
    @Published var mExposureBracketMode = 3
    @Published var mCameraType: CameraType = .normal
    @Published var isCapture = false
    @Published var isUltraWideCamera = false

    // Sound
    private var mStartSound: AVAudioPlayer!
    private var mCompSound: AVAudioPlayer!

    // Motion
    private var mMotionManager: MotionManager?

    init(isPreview: Bool) {
        if !isPreview {
            mCameraCapture = CameraCapture(view: mPreviewView, delegate: self)
            mCameraCapture?.initVolumeView()
            if let cameraCapture = mCameraCapture {
                isUltraWideCamera = cameraCapture.isUltraWideCameraUsable()
            }
            mMatterportAxisManager = MatterportAxisManager(delegate: self)
            mMotionManager = MotionManager(delegate: self)

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
        }
    }

    func startCapture() {
        if isCapture {
            stopCapture()
            return
        }

        if !isConnected {
            mCameraCapture?.startCapture()
            showToast(msg: "Test Shooting")
            return
        }

        isCapture = true
        mStartSound.play()
        mMotionManager?.start()

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            self.savePhoto()
        }
    }

    func stopCapture() {
        mAutoRotationFlg = false
        isCapture = false
        mMotionManager?.stop()
    }

    func autoRotation() {
        if !mAutoRotationFlg {
            return
        }

        if mAngle == 0 {
            stopCapture()
            mCompSound.play()
        } else if mAngle % mAutoRotationAngle == 0 {
            var isRotationStop = mReceiveAngleDate.timeIntervalSince(mSendAngleDate) > 0.5

            if PreferencesManager.shared.getUseGyro() {
                guard let totalGyroAbs = mMotionManager?.getTotalGyroAbsHf() else {
                    stopCapture()
                    return
                }

                isRotationStop = totalGyroAbs < 0.01 && isRotationStop
            }

            if !isSavePhoto, isRotationStop {
                print("start save Photo")
                savePhoto()
            }
        }
    }

    // Camera
    func chengeExposureBracketMode(mode: Int) {
        mExposureBracketMode = mode
        mCameraCapture?.setExposureMode(mode: mode)
    }

    func changeLens() {
        guard isUltraWideCamera else {
            showToast(msg: "お使いの端末には超広角レンズが搭載されていません。")
            return
        }
        mCameraType = mCameraType == .normal ? CameraType.wide : CameraType.normal

        if mCameraType == .normal {
            mAutoRotationAngle = 30
        } else {
            mAutoRotationAngle = 60
        }

        mCameraCapture?.changeCamera(type: mCameraType, focus: mFocus)
    }

    func setFocus() {
        mCameraCapture?.setFocus(position: mFocus)
    }

    func createDir() {
        if mCameraCapture?.createDir() ?? false {
            showToast(msg: "Successfully created directory")
        } else {
            showToast(msg: "Failed to create directory")
        }
    }

    func savePhoto() {
        isSavePhoto = true
        mCameraCapture?.startCapture()
    }

    func setInitalVolume() {
        mCameraCapture?.setInitalVolume()
    }

    func stopListeningVolume() {
        mCameraCapture?.stopListeningVolume()
    }

    // MatterportAxis
    func connectMatterportAxis() {
        if mMatterportAxisManager?.isConnected() ?? false {
            mMatterportAxisManager?.disconnect()
            showToast(msg: "Disconnecting...")
        } else {
            mMatterportAxisManager?.connect()
            showToast(msg: "Connecting...")
        }
    }

    func resetAngle() {
        if isConnected {
            mMatterportAxisManager?.resetAngle()
        }
    }

    // Toast
    func showToast(msg: String) {
        isToastShown = true
        mToastMsg = msg
    }
}

extension ContentViewModel: MatterportAxisManager.Delegate {
    func changeBtState(msg: String) {
        isBtStandby = mMatterportAxisManager?.getBtStatus() ?? false
        showToast(msg: msg)
    }

    func connected() {
        isConnected = true
    }

    func connectFailure() {
        isConnected = false
        showToast(msg: "Connection failed.")
    }

    func disconnected() {
        isConnected = false
    }

    func receiveAngle() {
        mAngle = mMatterportAxisManager?.getAngle() ?? 0
        mReceiveAngleDate = Date()
        autoRotation()
    }
}

extension ContentViewModel: CameraCapture.Delegate {
    func onSuccessCapturePhoto() {
        if mAutoRotationFlg || (!mAutoRotationFlg || mAngle != 0) {
            mMatterportAxisManager?.sendAngle(angle: UInt8(mAutoRotationAngle))
            mSendAngleDate = Date()
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { [self] in
            if isCapture, !mAutoRotationFlg {
                mAutoRotationFlg = true
            }
        }

        isSavePhoto = false
    }

    func pushRemoteShutterButton() {
        if isConnected {
            startCapture()
        }
    }
}

extension ContentViewModel: MotionManager.Delegate {
    func updateGyroData(gyro _: CMGyroData, totalAbs _: Double) {}
}
