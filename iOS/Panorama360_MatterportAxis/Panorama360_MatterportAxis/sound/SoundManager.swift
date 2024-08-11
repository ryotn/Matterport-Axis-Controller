//
//  SoundManager.swift
//  Panorama360_MatterportAxis
//
//  Created by RyoTN on 2024/08/12.
//

import AVFAudio
import Foundation

class SoundManager {
    // Sound
    private var mStartSound: AVAudioPlayer!
    private var mCompSound: AVAudioPlayer!

    init() {
        if let soundStartURL = Bundle.main.url(forResource: "start", withExtension: "mp3") {
            do {
                mStartSound = try AVAudioPlayer(contentsOf: soundStartURL)
            } catch {
                print("Sound Loading error")
            }
        }
        if let soundCompURL = Bundle.main.url(forResource: "comp", withExtension: "mp3") {
            do {
                mCompSound = try AVAudioPlayer(contentsOf: soundCompURL)
            } catch {
                print("Sound Loading error")
            }
        }
    }

    func playStart() {
        playSound(player: mStartSound)
    }

    func playComp() {
        playSound(player: mCompSound)
    }

    private func playSound(player: AVAudioPlayer) {
        if player.isPlaying {
            player.stop()
        }

        player.currentTime = 0
        player.play()
    }
}
