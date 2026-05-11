//
//  PreferencesManager.swift
//  Panorama360_MatterportAxis
//
//  Created by RyoTN on 2024/08/11.
//

import Foundation

class PreferencesManager {
    static let shared = PreferencesManager()

    private let KEY_USE_GYRO = "USE_GYRO"
    private let KEY_USE_FOCUS_PEAKING = "USE_FOCUS_PEAKING"
    private let ud = UserDefaults.standard

    private var isGyro = false
    private var isFocusPeaking = false

    init() {
        ud.register(defaults: [KEY_USE_GYRO: true,
                                KEY_USE_FOCUS_PEAKING: false])

        isGyro = ud.bool(forKey: KEY_USE_GYRO)
        isFocusPeaking = ud.bool(forKey: KEY_USE_FOCUS_PEAKING)
    }

    func getUseGyro() -> Bool {
        isGyro
    }

    func setUseGyro(enable: Bool) {
        isGyro = enable
        ud.set(enable, forKey: KEY_USE_GYRO)
    }

    func getUseFocusPeaking() -> Bool {
        isFocusPeaking
    }

    func setUseFocusPeaking(enable: Bool) {
        isFocusPeaking = enable
        ud.set(enable, forKey: KEY_USE_FOCUS_PEAKING)
    }
}
