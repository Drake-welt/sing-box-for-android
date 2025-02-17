package io.drake.sfa.ui.profileoverride

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import io.drake.sfa.R
import io.drake.sfa.constant.PerAppProxyUpdateType
import io.drake.sfa.database.Settings
import io.drake.sfa.databinding.ActivityConfigOverrideBinding
import io.drake.sfa.ktx.addTextChangedListener
import io.drake.sfa.ktx.setSimpleItems
import io.drake.sfa.ktx.text
import io.drake.sfa.ui.shared.AbstractActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileOverrideActivity : AbstractActivity() {

    private lateinit var binding: ActivityConfigOverrideBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.title_profile_override)
        binding = ActivityConfigOverrideBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.switchPerAppProxy.isChecked = Settings.perAppProxyEnabled
        binding.switchPerAppProxy.setOnCheckedChangeListener { _, isChecked ->
            Settings.perAppProxyEnabled = isChecked
            binding.perAppProxyUpdateOnChange.isEnabled = binding.switchPerAppProxy.isChecked
            binding.configureAppListButton.isEnabled = isChecked
        }
        binding.perAppProxyUpdateOnChange.isEnabled = binding.switchPerAppProxy.isChecked
        binding.configureAppListButton.isEnabled = binding.switchPerAppProxy.isChecked

        binding.perAppProxyUpdateOnChange.addTextChangedListener {
            lifecycleScope.launch(Dispatchers.IO) {
                Settings.perAppProxyUpdateOnChange = PerAppProxyUpdateType.valueOf(it).value()
            }
        }

        binding.configureAppListButton.setOnClickListener {
            startActivity(Intent(this, PerAppProxyActivity::class.java))
        }
        lifecycleScope.launch(Dispatchers.IO) {
            reloadSettings()
        }
    }

    private suspend fun reloadSettings() {
        val perAppUpdateOnChange = Settings.perAppProxyUpdateOnChange
        withContext(Dispatchers.Main) {
            binding.perAppProxyUpdateOnChange.text =
                PerAppProxyUpdateType.valueOf(perAppUpdateOnChange).name
            binding.perAppProxyUpdateOnChange.setSimpleItems(R.array.per_app_proxy_update_on_change_value)
        }
    }
}