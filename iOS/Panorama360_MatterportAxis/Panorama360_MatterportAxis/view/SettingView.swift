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
            }
        }
        .navigationBarTitle("Setting", displayMode: .large)
    }
}

#Preview {
    SettingView()
}
