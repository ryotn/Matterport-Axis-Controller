//
//  CameraCapture.swift
//  Panorama360_MatterportAxis
//
//  元コード
//  https://qiita.com/t_okkan/items/f2ba9b7009b49fc2e30a
//  https://qiita.com/t_okkan/items/b2dd11426eab107c5d15
//

import AVFoundation
import Foundation
import MediaPlayer
import UIKit

enum CameraType: Int {
    case normal = 0
    case wide = 1
}

class CameraCapture: NSObject {
    protocol Delegate {
        func onSuccessCapturePhoto()
        func pushRemoteShutterButton()
        func onCameraChangeComplete()
    }

    private let EXPOSURES_VALUE: [[Float]] = [[0.0],
                                              [1.0, 0.0, -1.0],
                                              [2.0, 1.0, 0.0, -1.0, -2.0],
                                              [3.0, 2.0, 1.0, 0.0, -1.0, -2.0, -3.0]]
    private let INITAL_VOLUME: Float = 0.5
    private var mVolumeView: MPVolumeView!
    private var mVolumeSlider: UISlider!
    private var mVolumeObservTimer: Timer?
    private var mOutputVolumeObservers = [NSKeyValueObservation]()
    private var mBracketCaptureCount = 0
    private var mRemainingExposureValues: [Float] = []
    private var mFileSaveManager: FileSaveManager!
    private var mCapCount = 0
    private var mExposureMode = 3
    private var mSaveImages = [CIImage]()
    private var mDeviceOrientation: CGImagePropertyOrientation = .right

    var mCaptureSession = AVCaptureSession()

    var mWideCamera: AVCaptureDevice?
    var mUltraWideCamera: AVCaptureDevice?

    var mCurrentDevice: AVCaptureDevice?

    var mPhotoOutput: AVCapturePhotoOutput?

    var mPreviewLayer: AVCaptureVideoPreviewLayer?
    var mPreviewView: PreviewView
    var mDelegate: Delegate!

    init(view: PreviewView, delegate: Delegate) {
        mPreviewView = view
        mDelegate = delegate
        super.init()

        mFileSaveManager = FileSaveManager()

        setupCaptureSession()
        setupDevice()
        setupInputOutput()
        setupPreviewLayer()

        DispatchQueue.global(qos: .default).async {
            self.mCaptureSession.startRunning()
            self.mDelegate?.onCameraChangeComplete()
        }
    }
}

// MARK: カメラ設定メソッド

extension CameraCapture {
    // カメラの画質の設定
    func setupCaptureSession() {
        mCaptureSession.sessionPreset = AVCaptureSession.Preset.photo
    }

    // デバイスの設定
    func setupDevice() {
        // カメラデバイスのプロパティ設定
        let deviceDiscoverySession = AVCaptureDevice.DiscoverySession(deviceTypes:
            [.builtInWideAngleCamera, .builtInUltraWideCamera],
            mediaType: AVMediaType.video, position: AVCaptureDevice.Position.unspecified)
        // プロパティの条件を満たしたカメラデバイスの取得
        let devices = deviceDiscoverySession.devices

        for device in devices where device.position == AVCaptureDevice.Position.back {
            if device.deviceType == .builtInWideAngleCamera {
                mWideCamera = device
            } else if device.deviceType == .builtInUltraWideCamera {
                mUltraWideCamera = device
            }
        }
        // 起動時のカメラを設定
        mCurrentDevice = mWideCamera

        setFocus(position: 0.8)
    }

    func isUltraWideCameraUsable() -> Bool {
        mUltraWideCamera != nil
    }

    func changeCamera(type: CameraType, focus: Float) {
        let oldDeviceInput = mCaptureSession.inputs[0]

        if type == .normal {
            mCurrentDevice = mWideCamera
        } else if type == .wide {
            mCurrentDevice = mUltraWideCamera
        }

        do {
            let newDeviceInput = try AVCaptureDeviceInput(device: mCurrentDevice!)
            mCaptureSession.beginConfiguration()
            mCaptureSession.removeInput(oldDeviceInput)
            mCaptureSession.addInput(newDeviceInput)
            mCaptureSession.commitConfiguration()

            setFocus(position: focus)
            mDelegate?.onCameraChangeComplete()
        } catch {
            print("ChangeCamera Error")
        }
    }

    func getFocalLength() -> Float {
        guard let device = mCurrentDevice else {
            return 0.0
        }

        let sortDevice = device.formats.sorted { $0.videoFieldOfView > $1.videoFieldOfView }

        return get35mmEquivalentFocalLength(format: sortDevice[0])
    }

    // コピペ元
    // https://github.com/flutter/flutter/issues/119908
    private func get35mmEquivalentFocalLength(format: AVCaptureDevice.Format) -> Float {
        // get reported field of view. Documentation says this is the horizontal field of view
        var fov = format.videoFieldOfView
        // convert to radians
        fov *= Float.pi / 180.0
        // angle and opposite of right angle triangle are half the fov and half the width of
        // 35mm film (ie 18mm). The adjacent value of the right angle triangle is the equivalent
        // focal length. Using some right angle triangle math you can work out focal length
        let focalLen = 18 / tan(fov / 2)
        return focalLen
    }

    // 入出力データの設定
    func setupInputOutput() {
        do {
            // 指定したデバイスを使用するために入力を初期化
            let captureDeviceInput = try AVCaptureDeviceInput(device: mCurrentDevice!)
            // 指定した入力をセッションに追加
            mCaptureSession.addInput(captureDeviceInput)
            // 出力データを受け取るオブジェクトの作成
            mPhotoOutput = AVCapturePhotoOutput()
            // 出力ファイルのフォーマットを指定
            mPhotoOutput!.setPreparedPhotoSettingsArray(
                [AVCapturePhotoSettings(format: [AVVideoCodecKey: AVVideoCodecType.jpeg])],
                completionHandler: nil
            )
            mCaptureSession.addOutput(mPhotoOutput!)
        } catch {
            print(error)
        }
    }

    // カメラのプレビューを表示するレイヤの設定
    func setupPreviewLayer() {
        /*
         // 指定したAVCaptureSessionでプレビューレイヤを初期化
         self.mPreviewLayer = AVCaptureVideoPreviewLayer(session: mCaptureSession)
         // プレビューレイヤが、カメラのキャプチャーを縦横比を維持した状態で、表示するように設定
         self.mPreviewLayer?.videoGravity = AVLayerVideoGravity.resizeAspectFill
         // プレビューレイヤの表示の向きを設定
         self.mPreviewLayer?.connection?.videoOrientation = AVCaptureVideoOrientation.portrait

         self.mPreviewLayer?.frame = CGRect(x: 0, y: 0, width: mPreviewView.frame.width, height: mPreviewView.frame.height)
         self.mPreviewView.layer.insertSublayer(self.mPreviewLayer!, at: 0)*/
        mPreviewView.videoPreviewLayer?.session = mCaptureSession
        mPreviewView.videoPreviewLayer?.videoGravity = AVLayerVideoGravity.resizeAspectFill
    }

    func setFocus(position: Float) {
        guard let camDevice = mCurrentDevice else {
            return
        }

        let isAutoFocus = canChangeFocus(device: camDevice)
        if !isAutoFocus { return }

        do {
            // AVCaptureDeviceをロックして設定
            try camDevice.lockForConfiguration()
            // 現在のフォーカス（焦点）距離
            print("\(camDevice.lensPosition)")
            // フォーカス（焦点）距離設定(0.0~1.0の間)
            camDevice.setFocusModeLocked(lensPosition: position) { _ in
                // フォーカス（焦点）距離が固定されたら呼ばれるコールバック
                print("\(camDevice.lensPosition)")
            }
            camDevice.unlockForConfiguration()
        } catch _ {}
    }

    func setDeviceOrientation(orientation: CGImagePropertyOrientation) {
        mDeviceOrientation = orientation
    }

    func canChangeFocus(device: AVCaptureDevice) -> Bool {
        device.isFocusModeSupported(.autoFocus)
    }

    func startCapture() {
        mBracketCaptureCount = 0
        mRemainingExposureValues = EXPOSURES_VALUE[mExposureMode]
        capturePhoto()
    }

    // 一度に撮影できる枚数上限があるので、ブラケット撮影で上限を超える場合は小分けする
    private func capturePhoto() {
        let values = mRemainingExposureValues.prefix(mPhotoOutput!.maxBracketedCapturePhotoCount)
        mBracketCaptureCount = values.count
        mRemainingExposureValues = mRemainingExposureValues
            .dropFirst(mPhotoOutput!.maxBracketedCapturePhotoCount).map { $0 }
        let makeAutoExposureSettings = AVCaptureAutoExposureBracketedStillImageSettings
            .autoExposureSettings(exposureTargetBias:)
        let exposureSettings = values.map(makeAutoExposureSettings)

        let photoSettings = AVCapturePhotoBracketSettings(rawPixelFormatType: 0,
                                                          processedFormat: [AVVideoCodecKey: AVVideoCodecType.jpeg],
                                                          bracketedSettings: exposureSettings)
        photoSettings.isLensStabilizationEnabled = mPhotoOutput!.isLensStabilizationDuringBracketedCaptureSupported
        photoSettings.flashMode = .off

        // 撮影された画像をdelegateメソッドで処理
        mPhotoOutput?.capturePhoto(with: photoSettings, delegate: self as AVCapturePhotoCaptureDelegate)
    }

    func createDir() -> Bool {
        mCapCount = 0
        return mFileSaveManager.reCreateDir()
    }

    func setExposureMode(mode: Int) {
        mExposureMode = mode
    }
}

// MARK: AVCapturePhotoCaptureDelegate

extension CameraCapture: AVCapturePhotoCaptureDelegate {
    // 撮影した画像データが生成されたときに呼び出されるデリゲートメソッド
    func photoOutput(_: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error _: Error?) {
        if let imageData = photo.fileDataRepresentation() {
            guard let image = CIImage(data: imageData) else {
                print("Failed to convert imageData to CIImage")
                return
            }
            mSaveImages.append(image)
            mBracketCaptureCount -= 1
            if mBracketCaptureCount == 0 {
                if mRemainingExposureValues.count != 0 {
                    capturePhoto()
                } else {
                    for (index, image) in mSaveImages.enumerated() {
                        var fileName = "\(mCapCount).jpeg"
                        if mExposureMode != 0 {
                            fileName = "\(mCapCount)_\(index).jpeg"
                        }
                        mFileSaveManager.saveImage(image: image.oriented(mDeviceOrientation), fileName: fileName)
                    }
                    mSaveImages.removeAll()
                    mCapCount += 1
                    mDelegate.onSuccessCapturePhoto()
                }
            }
        }
    }

    // 無音にする
    func photoOutput(_: AVCapturePhotoOutput, willCapturePhotoFor _: AVCaptureResolvedPhotoSettings) {
        AudioServicesDisposeSystemSoundID(1108)
    }
}

// MARK: ListeningVolumeButton

// 元コード
// https://gist.github.com/kazz12211/9d58af5c42ecbe35de58d66418412690
extension CameraCapture {
    func initVolumeView() {
        let frame = CGRect(x: -100, y: -100, width: 100, height: 100)
        mVolumeView = MPVolumeView(frame: frame)
        mPreviewView.addSubview(mVolumeView)

        for view: UIView in mVolumeView.subviews where NSStringFromClass(view.classForCoder) == "MPVolumeSlider" {
            guard let volume = view as? UISlider else {
                return
            }
            mVolumeSlider = volume
            break
        }
    }

    func startListeningVolume() {
        setAudioSessionActive(active: true)
        mVolumeObservTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true, block: { [self] _ in
            let outputVolume = AVAudioSession.sharedInstance().outputVolume
            if outputVolume.truncatingRemainder(dividingBy: 1) == 0 {
                setInitalVolume()
                print("resetValume")
            }
        })
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [self] in
            mOutputVolumeObservers.append(AVAudioSession
                .sharedInstance()
                .observe(\.outputVolume, options: .new) { _, _ in
                    self.changedVolume()
                })
        }
    }

    func stopListeningVolume() {
        mVolumeObservTimer?.invalidate()
        mVolumeObservTimer = nil
        mOutputVolumeObservers.removeAll()
        setAudioSessionActive(active: false)
    }

    func setInitalVolume() {
        stopListeningVolume()
        mOutputVolumeObservers.removeAll()
        mVolumeSlider.setValue(INITAL_VOLUME, animated: false)
        startListeningVolume()
    }

    func changedVolume() {
        print("シャッターボタン")
        mDelegate.pushRemoteShutterButton()

        setInitalVolume()
    }

    private func setAudioSessionActive(active: Bool) {
        do {
            try AVAudioSession.sharedInstance().setActive(active)
        } catch {
            print("setActive failed ", error)
        }
    }
}
