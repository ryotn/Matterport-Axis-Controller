//
//  SettingView.swift
//  Panorama360_MatterportAxis
//
//  Created by RyoTN on 2024/03/23.
//

import SwiftUI

struct SettingView: View {
    @StateObject var model = SettingViewModel()

    var body: some View {
        NavigationView {
            List {
                Toggle(isOn: $model.isGyro, label: {
                    Text("雲台の回転停止検知に\nジャイロセンサーを利用する")
                }).onChange(of: model.isGyro) { _, _ in
                    model.changeGyro()
                }
                Toggle(isOn: $model.isFocusPeaking, label: {
                    Text("フォーカスピーキング")
                }).onChange(of: model.isFocusPeaking) { _, _ in
                    model.changeFocusPeaking()
                }
                VStack(alignment: .leading) {
                    Text("フォーカスピーキング しきい値: \(String(format: "%.1f", model.focusPeakingThreshold))")
                    Slider(value: $model.focusPeakingThreshold,
                           in: 1.0 ... 10.0,
                           step: 0.5,
                           onEditingChanged: { _ in
                               model.changeFocusPeakingThreshold()
                           })
                }
            }
        }
        .navigationBarTitle("Setting", displayMode: .large)
    }
}

#Preview {
    SettingView()
}
