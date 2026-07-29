package com.botbuilder.app.ui.files

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.botbuilder.app.R
import com.botbuilder.app.data.local.AppDatabase
import com.botbuilder.app.data.local.FileDeliveryRule
import com.botbuilder.app.data.local.SecureStore
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val ACCENT = 0xFF6C74B8.toInt()
private const val ACCENT_DARK = 0xFF565EA0.toInt()
private const val TEXT_PRIMARY = 0xFF1D1D2B.toInt()
private const val TEXT_SECONDARY = 0xFF6E6E82.toInt()

/** Screen for setting up "click link -> tap Start -> get a file automatically" flows. */
class FileLinksActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var secureStore: SecureStore
    private lateinit var adapter: FileRuleAdapter
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = AppDatabase.getInstance(applicationContext)
        secureStore = SecureStore(applicationContext)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFEDEEF7.toInt())
        }
        val padding = dp(20)

        val title = TextView(this).apply {
            text = "File Delivery Links"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            setPadding(padding, dp(24), padding, dp(4))
        }

        val botUsername = secureStore.botUsername
        val subtitle = TextView(this).apply {
            text = if (botUsername != null)
                "Share a link — when someone taps Start, they get a file automatically. Your bot: @$botUsername"
            else
                "Connect your bot first (on the previous screen) to generate shareable links."
            textSize = 13f
            setTextColor(TEXT_SECONDARY)
            setPadding(padding, 0, padding, dp(16))
        }

        val addButton = MaterialButton(this).apply {
            text = "+ Add file link"
            isAllCaps = false
            textSize = 15f
            cornerRadius = dp(16)
            backgroundTintList = android.content.res.ColorStateList.valueOf(ACCENT)
            setOnClickListener { showAddEditDialog(null) }
        }
        val addButtonWrap = LinearLayout(this).apply {
            setPadding(padding, 0, padding, dp(16))
            addView(addButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        }

        emptyView = TextView(this).apply {
            text = "No file links yet. Add one below — e.g. label \"Free ebook\", a link payload like \"ebook1\", and the direct file URL."
            textSize = 14f
            setTextColor(TEXT_SECONDARY)
            setPadding(padding, dp(12), padding, dp(12))
            visibility = View.GONE
        }

        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@FileLinksActivity)
            setPadding(padding, 0, padding, padding)
            clipToPadding = false
        }

        adapter = FileRuleAdapter(
            botUsername = botUsername,
            onEdit = { rule -> showAddEditDialog(rule) },
            onDelete = { rule -> deleteRule(rule) },
            onCopyLink = { link -> copyToClipboard(link) }
        )
        recyclerView.adapter = adapter

        root.addView(title)
        root.addView(subtitle)
        root.addView(addButtonWrap)
        root.addView(emptyView)
        root.addView(recyclerView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        setContentView(root)

        lifecycleScope.launch {
            db.fileDeliveryRuleDao().observeAll().collectLatest { rules ->
                adapter.submitList(rules)
                emptyView.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Bot link", text))
        Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show()
    }

    private fun filledInput(hintText: String): Pair<TextInputLayout, TextInputEditText> {
        val til = TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_FilledBox).apply {
            hint = hintText
            boxBackgroundColor = 0x14000000
            setBoxCornerRadii(dp(14).toFloat(), dp(14).toFloat(), dp(14).toFloat(), dp(14).toFloat())
            boxStrokeWidth = 0
            boxStrokeWidthFocused = dp(1)
            setBoxStrokeColorStateList(android.content.res.ColorStateList.valueOf(ACCENT))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.topMargin = dp(10) }
        }
        val edit = TextInputEditText(til.context).apply { setTextColor(TEXT_PRIMARY) }
        til.addView(edit)
        return til to edit
    }

    private fun showAddEditDialog(existing: FileDeliveryRule?) {
        val context = this
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(8))
        }

        val heading = TextView(context).apply {
            text = if (existing == null) "Add file link" else "Edit file link"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
        }
        container.addView(heading)

        val (labelTil, labelInput) = filledInput("Label (e.g. Free ebook)")
        labelInput.setText(existing?.label ?: "")
        container.addView(labelTil)

        val (payloadTil, payloadInput) = filledInput("Link code — letters/numbers only, no spaces (blank = default Start)")
        payloadInput.setText(existing?.payload ?: "")
        container.addView(payloadTil)

        val (urlTil, urlInput) = filledInput("Direct file URL (must be publicly downloadable)")
        urlInput.setText(existing?.fileUrl ?: "")
        container.addView(urlTil)

        val (captionTil, captionInput) = filledInput("Caption sent with the file (optional)")
        captionInput.setText(existing?.caption ?: "")
        container.addView(captionTil)

        val scroll = android.widget.ScrollView(context).apply { addView(container) }

        val dialog = AlertDialog.Builder(context)
            .setView(scroll)
            .setPositiveButton("Save") { _, _ ->
                val label = labelInput.text.toString().trim()
                val payload = payloadInput.text.toString().trim().replace(" ", "")
                val fileUrl = urlInput.text.toString().trim()
                val caption = captionInput.text.toString().trim()

                if (label.isEmpty() || fileUrl.isEmpty()) {
                    Toast.makeText(context, "Fill in a label and the file URL", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (!fileUrl.startsWith("http://") && !fileUrl.startsWith("https://")) {
                    Toast.makeText(context, "File URL must start with http:// or https://", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val rule = FileDeliveryRule(
                    id = existing?.id ?: 0,
                    label = label,
                    payload = payload,
                    fileUrl = fileUrl,
                    caption = caption
                )
                lifecycleScope.launch { db.fileDeliveryRuleDao().upsert(rule) }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.window?.setBackgroundDrawable(ContextCompat.getDrawable(context, R.drawable.bg_dialog_card))
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ACCENT_DARK)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(TEXT_SECONDARY)
    }

    private fun deleteRule(rule: FileDeliveryRule) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Delete this link?")
            .setMessage("\"${rule.label}\" will be removed.")
            .setPositiveButton("Delete") { _, _ -> lifecycleScope.launch { db.fileDeliveryRuleDao().delete(rule) } }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.window?.setBackgroundDrawable(ContextCompat.getDrawable(this, R.drawable.bg_dialog_card))
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(0xFFD64545.toInt())
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(TEXT_SECONDARY)
    }
}

private class FileRuleAdapter(
    private val botUsername: String?,
    private val onEdit: (FileDeliveryRule) -> Unit,
    private val onDelete: (FileDeliveryRule) -> Unit,
    private val onCopyLink: (String) -> Unit
) : RecyclerView.Adapter<FileRuleAdapter.ViewHolder>() {

    private var items: List<FileDeliveryRule> = emptyList()
    fun submitList(newItems: List<FileDeliveryRule>) { items = newItems; notifyDataSetChanged() }

    class ViewHolder(val root: LinearLayout, val label: TextView, val detail: TextView, val linkText: TextView, val editText: TextView) :
        RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val ctx = parent.context
        val density = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_list_item)
            layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
                .also { it.bottomMargin = dp(12) }
        }
        val label = TextView(ctx).apply {
            textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(TEXT_PRIMARY)
        }
        val detail = TextView(ctx).apply {
            textSize = 13f; setTextColor(TEXT_SECONDARY); setPadding(0, dp(6), 0, 0)
        }
        val linkText = TextView(ctx).apply {
            textSize = 12f; setTextColor(ACCENT_DARK); setPadding(0, dp(8), 0, 0)
        }
        val editText = TextView(ctx).apply {
            text = "Edit · Long-press to delete"
            textSize = 11f
            setTextColor(0xFFA0A0B5.toInt())
            setPadding(0, dp(8), 0, 0)
        }
        card.addView(label); card.addView(detail); card.addView(linkText); card.addView(editText)
        return ViewHolder(card, label, detail, linkText, editText)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val rule = items[position]
        holder.label.text = rule.label
        holder.detail.text = "File: ${rule.fileUrl}"
        val link = if (botUsername != null) {
            if (rule.payload.isBlank()) "https://t.me/$botUsername" else "https://t.me/$botUsername?start=${rule.payload}"
        } else "Connect your bot to generate this link"
        holder.linkText.text = "Tap link to copy: $link"
        holder.linkText.setOnClickListener { if (botUsername != null) onCopyLink(link) }
        holder.editText.setOnClickListener { onEdit(rule) }
        holder.root.setOnLongClickListener { onDelete(rule); true }
    }

    override fun getItemCount(): Int = items.size
}
