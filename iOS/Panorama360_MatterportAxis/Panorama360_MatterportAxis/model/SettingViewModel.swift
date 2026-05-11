//
//  SettingViewModel.swift
//  Panorama360_MatterportAxis
//
//  Created by RyoTN on 2024/08/11.
//

import Foundation

class SettingViewModel: ObservableObject {
    let preferencesManager = PreferencesManager.shared

    @Published var isGyro = false
    @Published var isFocusPeaking = false

    init() {
        isGyro = preferencesManager.getUseGyro()
        isFocusPeaking = preferencesManager.getUseFocusPeaking()
    }

    func changeGyro() {
        preferencesManager.setUseGyro(enable: isGyro)
    }

    func changeFocusPeaking() {
        preferencesManager.setUseFocusPeaking(enable: isFocusPeaking)
    }
}
