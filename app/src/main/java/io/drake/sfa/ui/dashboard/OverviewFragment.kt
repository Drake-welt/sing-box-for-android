package io.drake.sfa.ui.dashboard

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.divider.MaterialDividerItemDecoration
import io.drake.libbox.Libbox
import io.drake.libbox.StatusMessage
import io.drake.sfa.R
import io.drake.sfa.bg.BoxService
import io.drake.sfa.constant.Status
import io.drake.sfa.database.Profile
import io.drake.sfa.database.ProfileManager
import io.drake.sfa.database.Settings
import io.drake.sfa.databinding.FragmentDashboardOverviewBinding
import io.drake.sfa.databinding.ViewClashModeButtonBinding
import io.drake.sfa.databinding.ViewProfileItemBinding
import io.drake.sfa.ktx.errorDialogBuilder
import io.drake.sfa.ktx.getAttrColor
import io.drake.sfa.ui.MainActivity
import io.drake.sfa.utils.CommandClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverviewFragment : Fragment() {

    private val activity: MainActivity? get() = super.getActivity() as MainActivity?
    private var binding: FragmentDashboardOverviewBinding? = null
    private val statusClient =
        CommandClient(lifecycleScope, CommandClient.ConnectionType.Status, StatusClient())
    private val clashModeClient =
        CommandClient(lifecycleScope, CommandClient.ConnectionType.ClashMode, ClashModeClient())

    private var adapter: Adapter? = null
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val binding = FragmentDashboardOverviewBinding.inflate(inflater, container, false)
        this.binding = binding
        onCreate()
        return binding.root
    }

    private fun onCreate() {
        val activity = activity ?: return
        val binding = binding ?: return
        binding.profileList.adapter = Adapter(lifecycleScope, binding).apply {
            adapter = this
            reload()
        }
        binding.profileList.layoutManager = LinearLayoutManager(requireContext())
        val divider = MaterialDividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL)
        divider.isLastItemDecorated = false
        binding.profileList.addItemDecoration(divider)
        activity.serviceStatus.observe(viewLifecycleOwner) {
            binding.statusContainer.isVisible = it == Status.Starting || it == Status.Started
            when (it) {
                Status.Stopped -> {
                    binding.clashModeCard.isVisible = false
                    binding.systemProxyCard.isVisible = false
                    binding.fab.setImageResource(R.drawable.ic_play_arrow_24)
                    binding.fab.show()
                }

                Status.Starting -> {
                    binding.fab.hide()
                }

                Status.Started -> {
                    statusClient.connect()
                    clashModeClient.connect()
                    reloadSystemProxyStatus()
                    binding.fab.setImageResource(R.drawable.ic_stop_24)
                    binding.fab.show()
                }

                Status.Stopping -> {
                    binding.fab.hide()
                }

                else -> {}
            }
        }
        binding.fab.setOnClickListener {
            when (activity.serviceStatus.value) {
                Status.Stopped -> {
                    activity.startService()
                }

                Status.Started -> {
                    BoxService.stop()
                }

                else -> {}
            }
        }
        ProfileManager.registerCallback(this::updateProfiles)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter = null
        binding = null
        statusClient.disconnect()
        clashModeClient.disconnect()
        ProfileManager.unregisterCallback(this::updateProfiles)
    }

    private fun updateProfiles() {
        adapter?.reload()
    }

    private fun reloadSystemProxyStatus() {
        val binding = binding ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val status = Libbox.newStandaloneCommandClient().systemProxyStatus
            withContext(Dispatchers.Main) {
                binding.systemProxyCard.isVisible = status.available
                binding.systemProxySwitch.setOnClickListener(null)
                binding.systemProxySwitch.isChecked = status.enabled
                binding.systemProxySwitch.isEnabled = true
                binding.systemProxySwitch.setOnCheckedChangeListener { buttonView, isChecked ->
                    binding.systemProxySwitch.isEnabled = false
                    lifecycleScope.launch(Dispatchers.IO) {
                        Settings.systemProxyEnabled = isChecked
                        runCatching {
                            Libbox.newStandaloneCommandClient().setSystemProxyEnabled(isChecked)
                        }.onFailure {
                            buttonView.context.errorDialogBuilder(it).show()
                        }
                    }
                }
            }
        }
    }

    inner class StatusClient : CommandClient.Handler {

        override fun onConnected() {
            val binding = binding ?: return
            lifecycleScope.launch(Dispatchers.Main) {
                binding.memoryText.text = getString(R.string.loading)
                binding.goroutinesText.text = getString(R.string.loading)
            }
        }

        override fun onDisconnected() {
            val binding = binding ?: return
            lifecycleScope.launch(Dispatchers.Main) {
                binding.memoryText.text = getString(R.string.loading)
                binding.goroutinesText.text = getString(R.string.loading)
            }
        }

        override fun updateStatus(status: StatusMessage) {
            val binding = binding ?: return
            lifecycleScope.launch(Dispatchers.Main) {
                binding.memoryText.text = Libbox.formatBytes(status.memory)
                binding.goroutinesText.text = status.goroutines.toString()
                val trafficAvailable = status.trafficAvailable
                binding.trafficContainer.isVisible = trafficAvailable
                if (trafficAvailable) {
                    binding.inboundConnectionsText.text = status.connectionsIn.toString()
                    binding.outboundConnectionsText.text = status.connectionsOut.toString()
                    binding.uplinkText.text = Libbox.formatBytes(status.uplink) + "/s"
                    binding.downlinkText.text = Libbox.formatBytes(status.downlink) + "/s"
                    binding.uplinkTotalText.text = Libbox.formatBytes(status.uplinkTotal)
                    binding.downlinkTotalText.text = Libbox.formatBytes(status.downlinkTotal)
                }
            }
        }

    }

    inner class ClashModeClient : CommandClient.Handler {

        override fun initializeClashMode(modeList: List<String>, currentMode: String) {
            val binding = binding ?: return
            if (modeList.size > 1) {
                lifecycleScope.launch(Dispatchers.Main) {
                    binding.clashModeCard.isVisible = true
                    binding.clashModeList.adapter = ClashModeAdapter(modeList, currentMode)
                    binding.clashModeList.layoutManager =
                        GridLayoutManager(
                            requireContext(),
                            if (modeList.size < 3) modeList.size else 3
                        )
                }
            } else {
                lifecycleScope.launch(Dispatchers.Main) {
                    binding.clashModeCard.isVisible = false
                }
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun updateClashMode(newMode: String) {
            val binding = binding ?: return
            val adapter = binding.clashModeList.adapter as? ClashModeAdapter ?: return
            adapter.selected = newMode
            lifecycleScope.launch(Dispatchers.Main) {
                adapter.notifyDataSetChanged()
            }
        }

    }

    private inner class ClashModeAdapter(
        val items: List<String>,
        var selected: String
    ) :
        RecyclerView.Adapter<ClashModeItemView>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClashModeItemView {
            val view = ClashModeItemView(
                ViewClashModeButtonBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
            view.binding.clashModeButton.clipToOutline = true
            return view
        }

        override fun getItemCount(): Int {
            return items.size
        }

        override fun onBindViewHolder(holder: ClashModeItemView, position: Int) {
            holder.bind(items[position], selected)
        }
    }

    private inner class ClashModeItemView(val binding: ViewClashModeButtonBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: String, selected: String) {
            binding.clashModeButtonText.text = item
            if (item != selected) {
                binding.clashModeButtonText.setTextColor(
                    binding.root.context.getAttrColor(com.google.android.material.R.attr.colorOnPrimaryContainer)
                )
                binding.clashModeButton.setBackgroundResource(R.drawable.bg_rounded_rectangle)
                binding.clashModeButton.setOnClickListener {
                    runCatching {
                        Libbox.newStandaloneCommandClient().setClashMode(item)
                        clashModeClient.connect()
                    }.onFailure {
                        GlobalScope.launch(Dispatchers.Main) {
                            binding.root.context.errorDialogBuilder(it).show()
                        }
                    }
                }
            } else {
                binding.clashModeButtonText.setTextColor(
                    binding.root.context.getAttrColor(com.google.android.material.R.attr.colorOnPrimary)
                )
                binding.clashModeButton.setBackgroundResource(R.drawable.bg_rounded_rectangle_active)
                binding.clashModeButton.isClickable = false
            }

        }
    }


    class Adapter(
        internal val scope: CoroutineScope,
        internal val parent: FragmentDashboardOverviewBinding
    ) :
        RecyclerView.Adapter<Holder>() {

        internal var items: MutableList<Profile> = mutableListOf()
        internal var selectedProfileID = -1L
        internal var lastSelectedIndex: Int? = null
        internal fun reload() {
            scope.launch(Dispatchers.IO) {
                items = ProfileManager.list().toMutableList()
                if (items.isNotEmpty()) {
                    selectedProfileID = Settings.selectedProfile
                    for ((index, profile) in items.withIndex()) {
                        if (profile.id == selectedProfileID) {
                            lastSelectedIndex = index
                            break
                        }
                    }
                    if (lastSelectedIndex == null) {
                        lastSelectedIndex = 0
                        selectedProfileID = items[0].id
                        Settings.selectedProfile = selectedProfileID
                    }
                }
                withContext(Dispatchers.Main) {
                    parent.statusText.isVisible = items.isEmpty()
                    parent.container.isVisible = items.isNotEmpty()
                    notifyDataSetChanged()
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(
                this,
                ViewProfileItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int {
            return items.size
        }

    }

    class Holder(
        private val adapter: Adapter,
        private val binding: ViewProfileItemBinding
    ) :
        RecyclerView.ViewHolder(binding.root) {

        internal fun bind(profile: Profile) {
            binding.profileName.text = profile.name
            binding.profileSelected.setOnCheckedChangeListener(null)
            binding.profileSelected.isChecked = profile.id == adapter.selectedProfileID
            binding.profileSelected.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    adapter.parent.profileList.isClickable = false
                    adapter.selectedProfileID = profile.id
                    adapter.lastSelectedIndex?.let { index ->
                        adapter.notifyItemChanged(index)
                    }
                    adapter.lastSelectedIndex = adapterPosition
                    adapter.scope.launch(Dispatchers.IO) {
                        switchProfile(profile)
                        withContext(Dispatchers.Main) {
                            adapter.parent.profileList.isEnabled = true
                        }
                    }
                }
            }
            binding.root.setOnClickListener {
                binding.profileSelected.toggle()
            }
        }

        private suspend fun switchProfile(profile: Profile) {
            Settings.selectedProfile = profile.id
            val mainActivity = (binding.root.context as? MainActivity) ?: return
            val started = mainActivity.serviceStatus.value == Status.Started
            if (!started) {
                return
            }
            val restart = Settings.rebuildServiceMode()
            if (restart) {
                mainActivity.reconnect()
                BoxService.stop()
                delay(1000L)
                mainActivity.startService()
                return
            }
            runCatching {
                Libbox.newStandaloneCommandClient().serviceReload()
            }.onFailure {
                withContext(Dispatchers.Main) {
                    mainActivity.errorDialogBuilder(it).show()
                }
            }
        }
    }

}