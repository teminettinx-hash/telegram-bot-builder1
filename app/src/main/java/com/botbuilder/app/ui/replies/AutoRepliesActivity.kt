package com.botbuilder.app.ui.replies

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
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
import com.botbuilder.app.billing.PlanLimits
import com.botbuilder.app.billing.currentTier
import com.botbuilder.app.data.local.AppDatabase
import com.botbuilder.app.data.local.MatchType
import com.botbuilder.app.data.local.ReplyRule
import com.botbuilder.app.data.local.SecureStore
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val ACCENT = 0xFF6C74B8.toInt()
private const val ACCENT_DARK = 0xFF565EA0.toInt()
private const val TEXT_PRIMARY = 0xFF1D1D2B.toInt()
private const val TEXT_SECONDARY = 0xFF6E6E82.toInt()

class AutoRepliesActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var secureStore: SecureStore
    private lateinit var adapter: ReplyAdapter
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
            text = "Auto Replies"
            textSize = 26f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            setPadding(padding, dp(24), padding, dp(4))
        }

        val subtitle = TextView(this).apply {
            text = "Set what your bot says automatically — no need to reply yourself"
            textSize = 13f
            setTextColor(TEXT_SECONDARY)
            setPadding(padding, 0, padding, dp(16))
        }

        val addButton = MaterialButton(this).apply {
            text = "+ Add reply"
            isAllCaps = false
            textSize = 15f
            cornerRadius = dp(16)
            backgroundTintList = android.content.res.ColorStateList.valueOf(ACCENT)
            setOnClickListener { checkCapThenAdd() }
        }
        val addButtonWrap = LinearLayout(this).apply {
            setPadding(padding, 0, padding, dp(16))
            addView(addButton, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
            ))
        }

        emptyView = TextView(this).apply {
            text = "No replies yet. Add your first one below — e.g. keyword \"hello\" → answer \"I am good\"."
            textSize = 14f
            setTextColor(TEXT_SECONDARY)
            setPadding(padding, dp(12), padding, dp(12))
            visibility = View.GONE
        }

        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@AutoRepliesActivity)
            setPadding(padding, 0, padding, padding)
            clipToPadding = false
        }

        adapter = ReplyAdapter(
            onEdit = { rule -> showAddEditDialog(rule) },
            onDelete = { rule -> deleteRule(rule) }
        )
        recyclerView.adapter = adapter

        root.addView(title)
        root.addView(subtitle)
        root.addView(addButtonWrap)
        root.addView(emptyView)
        root.addView(recyclerView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        setContentView(root)

        lifecycleScope.launch {
            db.replyRuleDao().observeAll().collectLatest { rules ->
                adapter.submitList(rules)
                emptyView.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun checkCapThenAdd() {
        lifecycleScope.launch {
            val tier = secureStore.currentTier()
            val cap = PlanLimits.maxReplyRules(tier)
            val count = db.replyRuleDao().count()
            if (count >= cap) {
                showUpgradeDialog(
                    "Auto-reply limit reached",
                    "The ${tier.displayName} plan allows up to $cap auto-replies. Upgrade to add more."
                )
            } else {
                showAddEditDialog(null)
            }
        }
    }

    private fun showUpgradeDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("See plans") { _, _ ->
                startActivity(android.content.Intent(this, com.botbuilder.app.ui.plans.PlansActivity::class.java))
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun filledInput(hintText: String, multiline: Boolean = false): Pair<TextInputLayout, TextInputEditText> {
        val til = TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_FilledBox).apply {
            hint = hintText
            boxBackgroundColor = 0x14000000
            setBoxCornerRadii(dp(14).toFloat(), dp(14).toFloat(), dp(14).toFloat(), dp(14).toFloat())
            boxStrokeWidth = 0
            boxStrokeWidthFocused = dp(1)
            setBoxStrokeColorStateList(android.content.res.ColorStateList.valueOf(ACCENT))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(10) }
        }
        val edit = TextInputEditText(til.context).apply {
            setTextColor(TEXT_PRIMARY)
            if (multiline) {
                minLines = 2
                isSingleLine = false
            }
        }
        til.addView(edit)
        return til to edit
    }

    private fun showAddEditDialog(existing: ReplyRule?) {
        val context = this
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(8))
        }

        val heading = TextView(context).apply {
            text = if (existing == null) "Add reply" else "Edit reply"
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
        }
        container.addView(heading)

        val (labelTil, labelInput) = filledInput("Label (e.g. Greeting)")
        labelInput.setText(existing?.label ?: "")
        container.addView(labelTil)

        val (keywordsTil, keywordsInput) = filledInput("Keywords, comma separated (e.g. hi, hello, hey)")
        keywordsInput.setText(existing?.keywordList()?.joinToString(", ") ?: "")
        container.addView(keywordsTil)

        val (answerTil, answerInput) = filledInput("What the bot should reply", multiline = true)
        answerInput.setText(existing?.answer ?: "")
        container.addView(answerTil)

        // Match type as a proper Material dropdown instead of a default Spinner
        val matchOptions = listOf("Contains keyword", "Exact match", "Starts with", "Ends with")
        val matchTil = TextInputLayout(
            context, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_FilledBox_ExposedDropdownMenu
        ).apply {
            hint = "Match type"
            boxBackgroundColor = 0x14000000
            setBoxCornerRadii(dp(14).toFloat(), dp(14).toFloat(), dp(14).toFloat(), dp(14).toFloat())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(10) }
        }
        val matchDropdown = MaterialAutoCompleteTextView(context).apply {
            setSimpleItems(matchOptions.toTypedArray())
            val initialIndex = when (existing?.matchType) {
                MatchType.EXACT -> 1
                MatchType.STARTS_WITH -> 2
                MatchType.ENDS_WITH -> 3
                else -> 0
            }
            setText(matchOptions[initialIndex], false)
        }
        matchTil.addView(matchDropdown)
        container.addView(matchTil)

        val caseSensitiveCheck = MaterialCheckBox(context).apply {
            text = "Case sensitive"
            isChecked = existing?.caseSensitive ?: false
            setTextColor(TEXT_SECONDARY)
            buttonTintList = android.content.res.ColorStateList.valueOf(ACCENT)
            setPadding(0, dp(6), 0, 0)
        }
        container.addView(caseSensitiveCheck)

        val scroll = android.widget.ScrollView(context).apply { addView(container) }

        val dialog = AlertDialog.Builder(context)
            .setView(scroll)
            .setPositiveButton("Save") { _, _ ->
                val label = labelInput.text.toString().trim()
                val keywords = keywordsInput.text.toString()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString("|")
                val answer = answerInput.text.toString().trim()

                if (label.isEmpty() || keywords.isEmpty() || answer.isEmpty()) {
                    Toast.makeText(context, "Fill in label, at least one keyword, and an answer", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val matchType = when (matchDropdown.text.toString()) {
                    "Exact match" -> MatchType.EXACT
                    "Starts with" -> MatchType.STARTS_WITH
                    "Ends with" -> MatchType.ENDS_WITH
                    else -> MatchType.CONTAINS
                }

                val rule = ReplyRule(
                    id = existing?.id ?: 0,
                    label = label,
                    keywords = keywords,
                    answer = answer,
                    matchType = matchType,
                    caseSensitive = caseSensitiveCheck.isChecked,
                    priority = existing?.priority ?: 0
                )

                lifecycleScope.launch { db.replyRuleDao().upsert(rule) }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.window?.setBackgroundDrawable(
            ContextCompat.getDrawable(context, R.drawable.bg_dialog_card)
        )
        dialog.show()

        // Tint the dialog action buttons to match the app's accent instead of default blue
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ACCENT_DARK)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(TEXT_SECONDARY)
    }

    private fun deleteRule(rule: ReplyRule) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Delete this reply?")
            .setMessage("\"${rule.label}\" will be removed.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch { db.replyRuleDao().delete(rule) }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.window?.setBackgroundDrawable(
            ContextCompat.getDrawable(this, R.drawable.bg_dialog_card)
        )
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(0xFFD64545.toInt())
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(TEXT_SECONDARY)
    }
}

private class ReplyAdapter(
    private val onEdit: (ReplyRule) -> Unit,
    private val onDelete: (ReplyRule) -> Unit
) : RecyclerView.Adapter<ReplyAdapter.ViewHolder>() {

    private var items: List<ReplyRule> = emptyList()

    fun submitList(newItems: List<ReplyRule>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(val root: LinearLayout, val label: TextView, val detail: TextView, val tag: TextView) :
        RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val ctx = parent.context
        val density = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_list_item)
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(12) }
        }

        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val label = TextView(ctx).apply {
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tag = TextView(ctx).apply {
            textSize = 11f
            setTextColor(Color.WHITE)
            val bg = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(ACCENT)
            }
            background = bg
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }

        topRow.addView(label)
        topRow.addView(tag)

        val detail = TextView(ctx).apply {
            textSize = 13f
            setTextColor(TEXT_SECONDARY)
            setPadding(0, dp(8), 0, 0)
        }

        val hint = TextView(ctx).apply {
            text = "Tap to edit · Long-press to delete"
            textSize = 11f
            setTextColor(0xFFA0A0B5.toInt())
            setPadding(0, dp(8), 0, 0)
        }

        card.addView(topRow)
        card.addView(detail)
        card.addView(hint)

        return ViewHolder(card, label, detail, tag)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val rule = items[position]
        holder.label.text = rule.label
        holder.tag.text = when (rule.matchType) {
            MatchType.EXACT -> "Exact"
            MatchType.STARTS_WITH -> "Starts with"
            MatchType.ENDS_WITH -> "Ends with"
            else -> "Contains"
        }
        holder.detail.text = "Triggers: ${rule.keywordList().joinToString(", ")}\n→ ${rule.answer}"
        holder.root.setOnClickListener { onEdit(rule) }
        holder.root.setOnLongClickListener { onDelete(rule); true }
    }

    override fun getItemCount(): Int = items.size
}
