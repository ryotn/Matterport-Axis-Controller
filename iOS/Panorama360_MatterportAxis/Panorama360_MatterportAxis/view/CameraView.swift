//
//  CameraView.swift
//  Panorama360_MatterportAxis
//
//  Created by RyoTN on 2024/03/22.
//
// 元コード
// https://zenn.dev/takiser/articles/c2f21bbbdf68da#previewview%E3%82%92uiviewrepresentable%E3%81%97%E3%81%A6%E4%BD%BF%E7%94%A8%E3%81%99%E3%82%8B

import SwiftUI

typealias UIViewControllerType = PreviewView
struct CameraView: View {
    let previewUIView: PreviewView
    var body: some View {
        PreviewViewUIView(previewUIView: previewUIView)
    }

    struct PreviewViewUIView: UIViewRepresentable {
        let previewUIView: PreviewView
        func makeUIView(context _: Context) -> UIViewControllerType {
            let previewView = previewUIView
            previewView.backgroundColor = UIColor.cyan
            return previewView
        }

        func updateUIView(_: UIViewControllerType, context _: Context) {}
    }
}

struct CameraView_Previews: PreviewProvider {
    static var previews: some View {
        CameraView(previewUIView: PreviewView())
    }
}
