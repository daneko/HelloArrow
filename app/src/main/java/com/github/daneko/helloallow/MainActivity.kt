package com.github.daneko.helloallow

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProviders
import com.github.daneko.helloallow.databinding.MainActivityBinding


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = DataBindingUtil.setContentView<MainActivityBinding>(this, R.layout.main_activity)
        binding.setLifecycleOwner(this)
        binding.model = ViewModelProviders.of(this).get(MainViewModel::class.java)
    }
}
