//
//  ViewController.swift
//  Matterport Axis Controller
//
//  Created by RyoTN on 2022/07/30.
//

import UIKit

class ViewController: UIViewController, MatterportAxisManagerDelegate {
    @IBOutlet weak var btnConnect: UIButton!
    @IBOutlet weak var lblBtStatus: UILabel!
    @IBOutlet weak var lblAngle: UILabel!
    @IBOutlet weak var viewBtns: UIView!
    @IBOutlet weak var btnAutoStop: UIButton!
    @IBOutlet weak var viewBtnAutoRotation: UIView!
    @IBOutlet weak var lblAutoBtnMsg: UILabel!
    @IBOutlet weak var btnReset: UIButton!
    
    let AUTO_ERROR_MSG_NOT_0_DEGREE = "Angle is not set to 0 degrees.\nPress Reset."
    let AUTO_ERROR_MSG_AUTO_RUN = "During auto-rotation.\nTo abort, press the Stop button below."
    
    var mAutoTimer: Timer? = nil
    var mAutoNextAngle = 0
    var mAutoAngle = 0
    
    var mMatterportAxisManager: MatterportAxisManager!
    
    override func viewDidLoad() {
        super.viewDidLoad()
        // Do any additional setup after loading the view.
        mMatterportAxisManager = MatterportAxisManager(delegate: self)
        setControlView(isHidden: true)
    }

    @IBAction func pushConnect(_ sender: UIButton) {
        if (!mMatterportAxisManager.getBtStatus()) {
            return
        }
        if sender.tag == 0 {
            mMatterportAxisManager.connect()
            
            sender.setTitle("Disconnect", for: UIControl.State.normal)
            sender.tag = 1
        } else if sender.tag == 1 {
            disconnect()
        }
        
    }
    
    @IBAction func pushSendBtn(_ sender: UIButton) {
        mMatterportAxisManager.sendAngle(angle: UInt8(sender.tag))
    }
    
    @IBAction func pushReset(_ sender: UIButton) {
        mMatterportAxisManager.resetAngle()
    }

    
    @IBAction func pushAuto(_ sender: UIButton) {
        stopAutoTimer()
        viewBtns.isHidden = true
        
        btnAutoStop.isEnabled = true
        
        mAutoAngle = sender.tag
        mAutoNextAngle = 0
        
        lblAutoBtnMsg.text = AUTO_ERROR_MSG_AUTO_RUN
        lblAutoBtnMsg.isHidden = false
        
        mAutoTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true, block: { Timer in
            self.autoRotation()
        })
    }
    
    @IBAction func pushAutoStop(_ sender: UIButton) {
        stopAutoTimer()
    }
    
    func autoRotation() {
        if (mAutoNextAngle >= 359) {
            stopAutoTimer()
        }
        
        let angle = mMatterportAxisManager.getAngle()
        
        if (angle >= mAutoNextAngle - 1) {
            mAutoNextAngle = mAutoNextAngle + mAutoAngle
            mMatterportAxisManager.sendAngle(angle: UInt8(mAutoAngle))
        }
    }
    
    func stopAutoTimer() {
        if (mAutoTimer != nil) {
            mAutoTimer?.invalidate()
            mAutoTimer = nil
        }
        btnAutoStop.isEnabled = false
        viewBtns.isHidden = false
    }
    
    func setControlView(isHidden:Bool) {
        viewBtns.isHidden = isHidden
        viewBtnAutoRotation.isHidden = isHidden
        btnReset.isHidden = isHidden
    }
    
    func disconnect() {
        setControlView(isHidden: true)
        lblBtStatus.text = "Disconnected"
        
        mMatterportAxisManager.disconnect()
        
        btnConnect.setTitle("Connect", for: UIControl.State.normal)
        btnConnect.tag = 0
    }
    
//MARK: - MatterportAxisManagerDelegate
    func changeBtState(msg: String) {
        Toast.show(msg, self.view)
    }
    
    func connected() {
        Toast.show("Connection success", self.view)
        lblBtStatus.text = "Connected"
        
        setControlView(isHidden: false)
    }
    
    func connectFailure() {
        Toast.show("Connection failure", self.view)
    }
    
    func disconnected() {
        Toast.show("Disconnect", self.view)
    }
    
    func receiveAngle() {
        let angle = mMatterportAxisManager.getAngle()
        if (mAutoTimer == nil) {
            if (angle != 0) {
                lblAutoBtnMsg.text = AUTO_ERROR_MSG_NOT_0_DEGREE
                lblAutoBtnMsg.isHidden = false
            } else {
                lblAutoBtnMsg.isHidden = true
            }
        }
        lblAngle.text = String.init(format: "%d°", angle)
    }
}

