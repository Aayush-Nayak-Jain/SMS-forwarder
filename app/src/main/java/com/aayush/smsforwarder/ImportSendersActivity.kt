package com.aayush.smsforwarder

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aayush.smsforwarder.databinding.ActivityImportSendersBinding
import com.aayush.smsforwarder.databinding.ItemSenderBinding

/**
 * Reads the device's SMS inbox (READ_SMS) and lists distinct senders so the
 * user can pick one to watch, instead of typing a number by hand.
 * Nothing here leaves the device — it's a local ContentResolver query.
 */
class ImportSendersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImportSendersBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImportSendersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.import_senders_title)

        val senders = loadDistinctSenders()
        binding.recyclerSenders.layoutManager = LinearLayoutManager(this)
        binding.recyclerSenders.adapter = SenderAdapter(senders) { chosen ->
            val result = Intent().putExtra(EXTRA_SELECTED_SENDER, chosen)
            setResult(RESULT_OK, result)
            finish()
        }
        binding.textEmptySenders.visibility =
            if (senders.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun loadDistinctSenders(): List<String> {
        val uri = Uri.parse("content://sms/inbox")
        val projection = arrayOf("address")
        val seen = LinkedHashSet<String>()

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, null, null, "date DESC")
            val addressIndex = cursor?.getColumnIndex("address") ?: -1
            if (cursor != null && addressIndex >= 0) {
                while (cursor.moveToNext() && seen.size < MAX_SENDERS) {
                    val address = cursor.getString(addressIndex)
                    if (!address.isNullOrBlank()) seen.add(address.trim())
                }
            }
        } catch (e: Exception) {
            // Some OEM ROMs restrict content://sms access even with the permission granted.
            // Fail quietly to an empty list; the user can still add a sender manually.
        } finally {
            cursor?.close()
        }
        return seen.toList()
    }

    private class SenderAdapter(
        private val senders: List<String>,
        private val onPick: (String) -> Unit
    ) : RecyclerView.Adapter<SenderAdapter.SenderViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SenderViewHolder {
            val binding = ItemSenderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return SenderViewHolder(binding)
        }

        override fun onBindViewHolder(holder: SenderViewHolder, position: Int) {
            val sender = senders[position]
            holder.text.text = sender
            holder.itemView.setOnClickListener { onPick(sender) }
        }

        override fun getItemCount() = senders.size

        class SenderViewHolder(binding: ItemSenderBinding) : RecyclerView.ViewHolder(binding.root) {
            val text: TextView = binding.textSenderAddress
        }
    }

    companion object {
        const val EXTRA_SELECTED_SENDER = "extra_selected_sender"
        private const val MAX_SENDERS = 200
    }
}
