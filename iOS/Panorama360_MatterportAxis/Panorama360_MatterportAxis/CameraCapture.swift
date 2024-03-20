//
//  CameraCapture.swift
//  Panorama360_MatterportAxis
//
//  元コード
//  https://qiita.com/t_okkan/items/f2ba9b7009b49fc2e30a
//  https://qiita.com/t_okkan/items/b2dd11426eab107c5d15
//

import Foundation
import UIKit
import AVFoundation
import MediaPlayer

protocol CameraCaptureDelegate {
    func onPhotoOutput(image: CIImage)
    func pushRemoteShutterButton()
}

enum CameraType: Int {
    case normal = 0
    case wide = 1
}

class CameraCapture: NSObject {
    private var INITAL_VOLUME: Float = 0.5
    private var mVolumeView: MPVolumeView!
    private var _observers = [NSKeyValueObservation]()
    private var mBracketCaptureCount = 0
    private var mRemainingExposureValues: [Float] = []
    
    var mCaptureSession = AVCaptureSession()
    
    var mWideCamera: AVCaptureDevice?
    var mUltraWideCamera: AVCaptureDevice?
    
    var mCurrentDevice: AVCaptureDevice?
    
    var mPhotoOutput : AVCapturePhotoOutput?
    
    var mPreviewLayer : AVCaptureVideoPreviewLayer?
    var mPreviewView : UIView
    var mDelegate: CameraCaptureDelegate!
    
    init(view : UIView, delegate : CameraCaptureDelegate){
        self.mPreviewView = view
        self.mDelegate = delegate
        super.init()
        
        setupCaptureSession()
        setupDevice()
        setupInputOutput()
        setupPreviewLayer()
        
        DispatchQueue.global(qos: .default).async {
            self.mCaptureSession.startRunning()
        }
    }
    
}


//MARK: カメラ設定メソッド
extension CameraCapture{
    // カメラの画質の設定
    func setupCaptureSession() {
        mCaptureSession.sessionPreset = AVCaptureSession.Preset.photo
    }

    // デバイスの設定
    func setupDevice() {
        // カメラデバイスのプロパティ設定
        let deviceDiscoverySession = AVCaptureDevice.DiscoverySession(deviceTypes: [.builtInWideAngleCamera, .builtInUltraWideCamera], mediaType: AVMediaType.video, position: AVCaptureDevice.Position.unspecified)
        // プロパティの条件を満たしたカメラデバイスの取得
        let devices = deviceDiscoverySession.devices

        for device in devices {
            if device.position == AVCaptureDevice.Position.back {
                if device.deviceType == .builtInWideAngleCamera {
                    mWideCamera = device
                } else if device.deviceType == .builtInUltraWideCamera {
                    mUltraWideCamera = device
                }
            }
        }
        // 起動時のカメラを設定
        mCurrentDevice = mWideCamera
        
        setFocus(position: 0.8)
    }
    
    func changeCamera(type: CameraType, focus: Float) {
        let oldDeviceInput = mCaptureSession.inputs[0]
        
        if type == .normal {
            mCurrentDevice = mWideCamera
        } else if type == .wide {
            mCurrentDevice = mUltraWideCamera
        }
        
        let newDeviceInput = try! AVCaptureDeviceInput(device: mCurrentDevice!)
        mCaptureSession.beginConfiguration()
        mCaptureSession.removeInput(oldDeviceInput)
        mCaptureSession.addInput(newDeviceInput)
        mCaptureSession.commitConfiguration()
        
        setFocus(position: focus)
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
            mPhotoOutput!.setPreparedPhotoSettingsArray([AVCapturePhotoSettings(format: [AVVideoCodecKey : AVVideoCodecType.jpeg])], completionHandler: nil)
            mCaptureSession.addOutput(mPhotoOutput!)
        } catch {
            print(error)
        }
    }

    // カメラのプレビューを表示するレイヤの設定
    func setupPreviewLayer() {
        // 指定したAVCaptureSessionでプレビューレイヤを初期化
        self.mPreviewLayer = AVCaptureVideoPreviewLayer(session: mCaptureSession)
        // プレビューレイヤが、カメラのキャプチャーを縦横比を維持した状態で、表示するように設定
        self.mPreviewLayer?.videoGravity = AVLayerVideoGravity.resizeAspectFill
        // プレビューレイヤの表示の向きを設定
        self.mPreviewLayer?.connection?.videoOrientation = AVCaptureVideoOrientation.portrait

        self.mPreviewLayer?.frame = CGRect(x: 0, y: 0, width: mPreviewView.frame.width, height: mPreviewView.frame.height)
        self.mPreviewView.layer.insertSublayer(self.mPreviewLayer!, at: 0)
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
        } catch _ {
        }
    }
    
    func canChangeFocus(device: AVCaptureDevice) -> Bool {
        return device.isFocusModeSupported(.autoFocus)
    }
    
    func savePhoto(exposureValues: [Float]) {
        mBracketCaptureCount = 0
        mRemainingExposureValues = exposureValues
        capturePhoto()
    }
    
    // 一度に撮影できる枚数上限があるので、ブラケット撮影で上限を超える場合は小分けする
    private func capturePhoto() {
        let values = mRemainingExposureValues.prefix(mPhotoOutput!.maxBracketedCapturePhotoCount)
        mBracketCaptureCount = values.count
        mRemainingExposureValues = mRemainingExposureValues.dropFirst(mPhotoOutput!.maxBracketedCapturePhotoCount).map{ $0 }
        let makeAutoExposureSettings = AVCaptureAutoExposureBracketedStillImageSettings.autoExposureSettings(exposureTargetBias:)
        let exposureSettings = values.map(makeAutoExposureSettings)
        
        let photoSettings = AVCapturePhotoBracketSettings(rawPixelFormatType: 0,
            processedFormat: [AVVideoCodecKey : AVVideoCodecType.jpeg],
            bracketedSettings: exposureSettings)
        photoSettings.isLensStabilizationEnabled = mPhotoOutput!.isLensStabilizationDuringBracketedCaptureSupported
        photoSettings.flashMode = .off
        
        // 撮影された画像をdelegateメソッドで処理
        self.mPhotoOutput?.capturePhoto(with: photoSettings, delegate: self as AVCapturePhotoCaptureDelegate)
    }
}


//MARK: AVCapturePhotoCaptureDelegate
extension CameraCapture: AVCapturePhotoCaptureDelegate{
    // 撮影した画像データが生成されたときに呼び出されるデリゲートメソッド
    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        if let imageData = photo.fileDataRepresentation() {
            guard let image = CIImage(data: imageData) else { return }
            mDelegate.onPhotoOutput(image: image)
            mBracketCaptureCount -= 1
            if mBracketCaptureCount == 0 {
                if mRemainingExposureValues.count != 0 {
                    capturePhoto()
                }
            }
        }
    }
    //無音にする
    func photoOutput(_ output: AVCapturePhotoOutput, willCapturePhotoFor resolvedSettings: AVCaptureResolvedPhotoSettings) {
      AudioServicesDisposeSystemSoundID(1108)
    }
}

//MARK: ListeningVolumeButton
//元コード
//https://gist.github.com/kazz12211/9d58af5c42ecbe35de58d66418412690
extension CameraCapture {
    
    func startListeningVolumeButton(view: UIView) {
        // MPVolumeViewを画面の外側に追い出して見えないようにする
        let frame = CGRect(x: -100, y: -100, width: 100, height: 100)
        mVolumeView = MPVolumeView(frame: frame)
        mVolumeView.sizeToFit()
        mPreviewView.addSubview(mVolumeView)

        let audioSession = AVAudioSession.sharedInstance()
        do {
            try audioSession.setActive(true)
            setVolume(INITAL_VOLUME)
            // 出力音量の監視を開始
            _observers.append(audioSession.observe(\.outputVolume, options: .new) {_, _ in
                self.changedVolume()
            })
        } catch {
            print("Could not observer outputVolume ", error)
        }
    }
    
    func stopListeningVolume() {
        // 出力音量の監視を終了
        _observers.removeAll()
        // ボリュームビューを破棄
        mVolumeView.removeFromSuperview()
        mVolumeView = nil
    }
    
    func setVolume(_ volume: Float) {
        for view : UIView in mVolumeView.subviews {
            if(NSStringFromClass( view.classForCoder ) == "MPVolumeSlider" ){
                let sldVolume = view as! UISlider
                sldVolume.setValue(volume, animated: false)
                break
            }
        }
    }
    
    func changedVolume() {
        print("シャッターボタン")
        mDelegate.pushRemoteShutterButton()
        // 一旦出力音量の監視をやめて出力音量を設定してから出力音量の監視を再開する
        _observers.removeAll()
        setVolume(INITAL_VOLUME)
        _observers.append(AVAudioSession.sharedInstance().observe(\.outputVolume, options: .new) {_, change in
            print("changevolume?")
            self.changedVolume()
        })
    }
}
