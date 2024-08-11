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
    private let ud = UserDefaults.standard

    private var isGyro = false

    init() {
        ud.register(defaults: [KEY_USE_GYRO: true])

        isGyro = ud.bool(forKey: KEY_USE_GYRO)
    }

    func getUseGyro() -> Bool {
        isGyro
    }

    func setUseGyro(enable: Bool) {
        isGyro = enable
        ud.set(enable, forKey: KEY_USE_GYRO)
    }
}
