package com.aayush.smsforwarder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.aayush.smsforwarder.databinding.ActivityMainBinding
import com.aayush.smsforwarder.databinding.DialogAddRuleBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: RulesAdapter

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS
    )

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            refreshMasterSwitchFromStore()
        } else {
            Toast.makeText(this, R.string.permissions_required, Toast.LENGTH_LONG).show()
            binding.switchMasterEnable.isChecked = false
        }
    }

    private val importLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val sender = result.data?.getStringExtra(ImportSendersActivity.EXTRA_SELECTED_SENDER)
            if (sender != null) showAddRuleDialog(prefillSender = sender)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = RulesAdapter(
            onToggle = { rule, checked ->
                rule.enabled = checked
                RuleStore.updateRule(this, rule)
            },
            onDelete = { rule ->
                RuleStore.deleteRule(this, rule.id)
                loadRules()
            },
            onEdit = { rule ->
                showAddRuleDialog(editingRule = rule)
            },
            onSelectionChanged = { selectionMode, count ->
                updateSelectionToolbar(selectionMode, count)
            }
        )
        binding.recyclerRules.layoutManager = LinearLayoutManager(this)
        binding.recyclerRules.adapter = adapter

        binding.switchMasterEnable.isChecked = RuleStore.isMasterEnabled(this)
        binding.switchMasterEnable.setOnCheckedChangeListener { _, checked ->
            if (checked && !hasAllPermissions()) {
                binding.switchMasterEnable.isChecked = false
                permissionLauncher.launch(requiredPermissions)
            } else {
                RuleStore.setMasterEnabled(this, checked)
                if (checked) maybePromptBatteryOptimizationExemption()
            }
        }

        binding.buttonAddManually.setOnClickListener { showAddRuleDialog() }

        binding.buttonImportFromInbox.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                importLauncher.launch(Intent(this, ImportSendersActivity::class.java))
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_SMS), REQ_READ_SMS_FOR_IMPORT)
            }
        }

        binding.buttonCancelSelection.setOnClickListener { adapter.setSelectionMode(false) }

        binding.buttonDeleteSelected.setOnClickListener {
            val ids = adapter.getSelectedIds()
            if (ids.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_selected_title)
                .setMessage(R.string.confirm_delete_selected_message)
                .setPositiveButton(R.string.delete_selected) { _, _ ->
                    RuleStore.deleteRules(this, ids)
                    adapter.setSelectionMode(false)
                    loadRules()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        onBackPressedDispatcher.addCallback(this) {
            if (binding.selectionToolbar.visibility == android.view.View.VISIBLE) {
                adapter.setSelectionMode(false)
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        loadRules()
    }

    override fun onResume() {
        super.onResume()
        loadRules()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_READ_SMS_FOR_IMPORT &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            importLauncher.launch(Intent(this, ImportSendersActivity::class.java))
        }
    }

    private fun hasAllPermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun refreshMasterSwitchFromStore() {
        val enable = hasAllPermissions()
        RuleStore.setMasterEnabled(this, enable)
        binding.switchMasterEnable.isChecked = enable
        if (enable) maybePromptBatteryOptimizationExemption()
    }

    /**
     * Doze mode defers broadcast delivery (and delays/queues outgoing SMS)
     * for apps that aren't exempted from battery optimization. This is the
     * single biggest source of forwarding lag and dropped messages on
     * stock Android and most OEM skins. We ask once per session, not
     * aggressively — the user can always say no.
     */
    private fun maybePromptBatteryOptimizationExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return

        AlertDialog.Builder(this)
            .setTitle(R.string.battery_opt_title)
            .setMessage(R.string.battery_opt_message)
            .setPositiveButton(R.string.battery_opt_allow) { _, _ ->
                try {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
            .setNegativeButton(R.string.battery_opt_skip, null)
            .show()
    }

    /** Swaps the add/import button row for the selection toolbar, and back. */
    private fun updateSelectionToolbar(selectionMode: Boolean, selectedCount: Int) {
        binding.selectionToolbar.visibility = if (selectionMode) android.view.View.VISIBLE else android.view.View.GONE
        binding.buttonAddManually.visibility = if (selectionMode) android.view.View.GONE else android.view.View.VISIBLE
        binding.buttonImportFromInbox.visibility = if (selectionMode) android.view.View.GONE else android.view.View.VISIBLE
        binding.textSelectedCount.text = getString(R.string.selected_count_format, selectedCount)
    }

    private fun loadRules() {
        adapter.submitList(RuleStore.getRules(this))
        binding.textEmptyState.visibility =
            if (RuleStore.getRules(this).isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun showAddRuleDialog(prefillSender: String? = null, editingRule: ForwardRule? = null) {
        val dialogBinding = DialogAddRuleBinding.inflate(LayoutInflater.from(this))
        dialogBinding.editSender.setText(editingRule?.senderMatch ?: prefillSender ?: "")
        dialogBinding.editRecipient.setText(editingRule?.recipient ?: "")

        AlertDialog.Builder(this)
            .setTitle(if (editingRule != null) R.string.edit_rule_title else R.string.add_rule_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val sender = dialogBinding.editSender.text.toString().trim()
                val recipient = dialogBinding.editRecipient.text.toString().trim()
                if (sender.isEmpty() || recipient.isEmpty()) {
                    Toast.makeText(this, R.string.fill_both_fields, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (editingRule != null) {
                    editingRule.senderMatch = sender
                    editingRule.senderLabel = sender
                    editingRule.recipient = recipient
                    RuleStore.updateRule(this, editingRule)
                } else {
                    RuleStore.addRule(this, ForwardRule(senderMatch = sender, senderLabel = sender, recipient = recipient))
                }
                loadRules()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        private const val REQ_READ_SMS_FOR_IMPORT = 42
    }
}