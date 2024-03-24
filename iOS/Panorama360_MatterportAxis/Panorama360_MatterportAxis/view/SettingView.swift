//
//  SettingView.swift
//  Panorama360_MatterportAxis
//
//  Created by RyoTN on 2024/03/23.
//

import SwiftUI

struct SettingView: View {
    @State var v = false
    var body: some View {
        NavigationView {
            List {
                Toggle(isOn: $v, label: {
                    Text("Label")
                })
            }
        }
        .navigationBarTitle("Setting", displayMode: .large)
    }
}

#Preview {
    SettingView()
}
