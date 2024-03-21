//
//  FileSaveManager.swift
//  Panorama360_MatterportAxis
//
//  Created by RyoTN on 2022/12/30.
//

import Foundation
import UIKit

class FileSaveManager: NSObject {
    
    var mFileManager: FileManager!
    var mDateFormat: DateFormatter!
    var mSavePath: URL?
    
    override init() {
        mFileManager = FileManager.default
        mDateFormat = DateFormatter()
        mDateFormat.calendar = Calendar(identifier: .gregorian)
        mDateFormat.locale = Locale(identifier: "ja_JP")
        mDateFormat.timeZone = TimeZone(identifier: "Asia/Tokyo")
        mDateFormat.dateFormat = "yyyy-MM-dd_HH-mm-ss"
        super.init()
        
        _ = reCreateDir()
    }
    
    func createDir(dirName: String) {
        if let dir = mFileManager.urls(for: .documentDirectory, in: .userDomainMask).last {
            let path = dir.appendingPathComponent(dirName, isDirectory: true)


            do {
                try mFileManager.createDirectory(at: path, withIntermediateDirectories: true, attributes: nil)
                mSavePath = path
            } catch {
                mSavePath = nil
                print("ディレクトリ作成エラー")
            }
        }
    }
    
    func reCreateDir() -> Bool {
        let date = Date()
        let dirName = mDateFormat.string(from: date)
        createDir(dirName: dirName)
        
        return mSavePath != nil
    }
    
    func saveImage(image: CIImage, fileName: String) {
        guard let imageData = CIContext()
            .jpegRepresentation(of: image,
                                colorSpace: image.colorSpace ?? CGColorSpaceCreateDeviceRGB())
        else {
            print("Failed to jpegRepresentation")
            return
        }
        
        guard let dirPath = mSavePath else {
            print("mSavePath is null")
            return
        }
        
        let path = dirPath.appendingPathComponent(fileName)

        do {
            try imageData.write(to: path)
            print("Image saved. filename : \(fileName)")
        } catch {
            print("Failed to save the image:", error)
        }
    }
}
