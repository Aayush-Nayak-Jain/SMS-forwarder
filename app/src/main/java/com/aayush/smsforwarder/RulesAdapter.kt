package com.aayush.smsforwarder

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aayush.smsforwarder.databinding.ItemRuleBinding

class RulesAdapter(
    private val onToggle: (ForwardRule, Boolean) -> Unit,
    private val onDelete: (ForwardRule) -> Unit,
    private val onEdit: (ForwardRule) -> Unit,
    private val onSelectionChanged: (selectionMode: Boolean, selectedCount: Int) -> Unit
) : RecyclerView.Adapter<RulesAdapter.RuleViewHolder>() {

    private val rules = mutableListOf<ForwardRule>()
    private val selectedIds = mutableSetOf<String>()
    private var selectionMode = false

    fun submitList(newRules: List<ForwardRule>) {
        rules.clear()
        rules.addAll(newRules)
        notifyDataSetChanged()
    }

    /** Called by the activity's Cancel button, or after a bulk delete completes. */
    fun setSelectionMode(enabled: Boolean) {
        selectionMode = enabled
        if (!enabled) selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged(selectionMode, selectedIds.size)
    }

    fun getSelectedIds(): Set<String> = selectedIds.toSet()

    private fun toggleSelected(ruleId: String) {
        if (selectedIds.contains(ruleId)) selectedIds.remove(ruleId) else selectedIds.add(ruleId)
        if (selectedIds.isEmpty()) selectionMode = false
        notifyDataSetChanged()
        onSelectionChanged(selectionMode, selectedIds.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
        val binding = ItemRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RuleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
        holder.bind(rules[position])
    }

    override fun getItemCount() = rules.size

    inner class RuleViewHolder(private val binding: ItemRuleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(rule: ForwardRule) {
            binding.textSender.text = rule.senderLabel
            binding.textRecipient.text = "→ ${rule.recipient}"

            // Selection mode swaps the enable-switch/delete-button for a checkbox.
            binding.checkboxSelect.visibility = if (selectionMode) android.view.View.VISIBLE else android.view.View.GONE
            binding.switchRuleEnabled.visibility = if (selectionMode) android.view.View.GONE else android.view.View.VISIBLE
            binding.buttonDeleteRule.visibility = if (selectionMode) android.view.View.GONE else android.view.View.VISIBLE

            binding.checkboxSelect.isChecked = selectedIds.contains(rule.id)
            binding.checkboxSelect.setOnClickListener { toggleSelected(rule.id) }

            binding.switchRuleEnabled.setOnCheckedChangeListener(null)
            binding.switchRuleEnabled.isChecked = rule.enabled
            binding.switchRuleEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggle(rule, isChecked)
            }
            binding.buttonDeleteRule.setOnClickListener { onDelete(rule) }

            binding.root.setOnClickListener {
                if (selectionMode) toggleSelected(rule.id) else onEdit(rule)
            }
            binding.root.setOnLongClickListener {
                if (!selectionMode) {
                    selectionMode = true
                    selectedIds.add(rule.id)
                    notifyDataSetChanged()
                    onSelectionChanged(selectionMode, selectedIds.size)
                }
                true
            }
        }
    }
}