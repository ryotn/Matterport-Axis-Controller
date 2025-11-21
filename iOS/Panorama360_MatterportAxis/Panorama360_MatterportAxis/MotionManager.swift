//
//  MotionManager.swift
//  Panorama360_MatterportAxis
//
//  Created by RyoTN on 2024/08/11.
//

import CoreMotion
import Foundation

class MotionManager: NSObject {
    protocol Delegate {
        func updateGyroData(gyro: CMGyroData, totalAbs: Double)
        func updateGravityData(gravity: CMAcceleration)
    }

    private let motionManager = CMMotionManager()
    private let alpha: CGFloat = 0.4
    private var isStarted = false
    private var totalGyroAbs = 10.0
    private var totalGyroAbsHf = 10.0
    private var delegate: Delegate!

    init(delegate: Delegate) {
        super.init()
        self.delegate = delegate
    }

    func start() {
        guard !isStarted else {
            print("Sensor reception has already started.")
            return
        }

        totalGyroAbs = 10
        totalGyroAbsHf = 10

        if motionManager.isGyroAvailable {
            motionManager.gyroUpdateInterval = 0.01
            motionManager.startGyroUpdates(to: .current!) { [self] (motion: CMGyroData?, error: Error?) in
                guard let motion else {
                    print("Gyro data is nil. Error:\(String(describing: error))")
                    return
                }

                let x = fabs(motion.rotationRate.x)
                let y = fabs(motion.rotationRate.y)
                let z = fabs(motion.rotationRate.z)
                totalGyroAbs = x + y + z
                totalGyroAbsHf = alpha * totalGyroAbs + totalGyroAbsHf * (1 - alpha)

                delegate?.updateGyroData(gyro: motion, totalAbs: totalGyroAbs)
            }
        }

        if motionManager.isDeviceMotionAvailable {
            motionManager.deviceMotionUpdateInterval = 0.2
            motionManager.startDeviceMotionUpdates(to: .current!) { (motion: CMDeviceMotion?, error: Error?) in
                guard let motion else {
                    print("DeviceMotion data is nil. Error:\(String(describing: error))")
                    return
                }

                self.delegate?.updateGravityData(gravity: motion.gravity)
            }
        }

        isStarted = true
    }

    func stop() {
        motionManager.stopGyroUpdates()
        motionManager.stopAccelerometerUpdates()
        isStarted = false
        totalGyroAbs = 10
    }

    func getTotalGyroAbs() -> Double {
        totalGyroAbs
    }

    func getTotalGyroAbsHf() -> Double {
        totalGyroAbsHf
    }
}
