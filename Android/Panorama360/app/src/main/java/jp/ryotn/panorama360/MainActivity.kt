package jp.ryotn.panorama360

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class MainActivity : AppCompatActivity() {
    private lateinit var mMatterportAxisManager: MatterportAxisManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mMatterportAxisManager = MatterportAxisManager(context = this)

        val btn = findViewById<Button>(R.id.Scan)
        btn.setOnClickListener {
            mMatterportAxisManager.connect()
        }
    }
}