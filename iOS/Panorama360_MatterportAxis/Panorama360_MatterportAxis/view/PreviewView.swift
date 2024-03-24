//
//  PreviewView.swift
//  Panorama360_MatterportAxis
//
//  Created by RyoTN on 2024/03/23.
//
// 元コード
// https://zenn.dev/takiser/articles/c2f21bbbdf68da#previewview%E3%82%92uiviewrepresentable%E3%81%97%E3%81%A6%E4%BD%BF%E7%94%A8%E3%81%99%E3%82%8B

import AVFoundation
import Foundation
import UIKit

class PreviewView: UIView {
    override class var layerClass: AnyClass {
        AVCaptureVideoPreviewLayer.self
    }

    var videoPreviewLayer: AVCaptureVideoPreviewLayer? {
        guard let avCaptureVideoPreviewLayer = layer as? AVCaptureVideoPreviewLayer else {
            return nil
        }
        return avCaptureVideoPreviewLayer
    }
}
