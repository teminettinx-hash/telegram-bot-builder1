package com.botbuilder.app.ui.commands

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
import com.botbuilder.app.data.local.BotCommand
import com.botbuilder.app.data.local.SecureStore
import com.botbuilder.app.data.repository.BotRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val ACCENT = 0xFF6C74B8.toInt()
private const val ACCENT_DARK = 0xFF565EA0.toInt()
private const val TEXT_PRIMARY = 0xFF1D1D2B.toInt()
private const val TEXT_SECONDARY = 0xFF6E6E82.toInt()

/** Screen that manages the commands shown in Telegram's native "/" popup menu
 *  (the same UI BotFather uses for /newbot, /deletebot, etc.). */
class BotCommandsActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var repository: BotRepository
    private lateinit var adapter: CommandAdapter
    private lateinit var emptyView: TextView
    private lateinit var syncStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = AppDatabase.getInstance(applicationContext)
        repository = BotRepository(db, SecureStore(applicationContext))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFEDEEF7.toInt())
        }
        val padding = dp(20)

        val title = TextView(this).apply {
            text = "Bot Commands"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            setPadding(padding, dp(24), padding, dp(4))
        }
        val subtitle = TextView(this).apply {
            text = "These show up when someone taps \"/\" in your bot's chat — like BotFather's command list."
            textSize = 13f
            setTextColor(TEXT_SECONDARY)
            setPadding(padding, 0, padding, dp(16))
        }

        val addButton = MaterialButton(this).apply {
            text = "+ Add command"
            isAllCaps = false
            textSize = 15f
            cornerRadius = dp(16)
            backgroundTintList = android.content.res.ColorStateList.valueOf(ACCENT)
            setOnClickListener { showAddEditDialog(null) }
        }
        val syncButton = MaterialButton(this).apply {
            text = "Sync to Telegram"
            isAllCaps = false
            textSize = 15f
            cornerRadius = dp(16)
            setOnClickListener { syncCommands() }
        }
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(padding, 0, padding, dp(8))
            addView(addButton, LinearLayout.LayoutParams(0, dp(52), 1f).also { it.marginEnd = dp(8) })
            addView(syncButton, LinearLayout.LayoutParams(0, dp(52), 1f).also { it.marginStart = dp(8) })
        }

        syncStatus = TextView(this).apply {
            text = "Commands sync to Telegram automatically when saved. Use this button if the / menu ever looks out of date."
            textSize = 12f
            setTextColor(TEXT_SECONDARY)
            setPadding(padding, 0, padding, dp(16))
        }

        emptyView = TextView(this).apply {
            text = "No commands yet. Add one below — e.g. command \"price\", description \"See our price list\", answer with your prices."
            textSize = 14f
            setTextColor(TEXT_SECONDARY)
            setPadding(padding, dp(12), padding, dp(12))
            visibility = View.GONE
        }

        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@BotCommandsActivity)
            setPadding(padding, 0, padding, padding)
            clipToPadding = false
        }

        adapter = CommandAdapter(
            onEdit = { cmd -> showAddEditDialog(cmd) },
            onDelete = { cmd -> deleteCommand(cmd) }
        )
        recyclerView.adapter = adapter

        root.addView(title)
        root.addView(subtitle)
        root.addView(buttonRow)
        root.addView(syncStatus)
        root.addView(emptyView)
        root.addView(recyclerView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        setContentView(root)

        lifecycleScope.launch {
            db.botCommandDao().observeAll().collectLatest { commands ->
                adapter.submitList(commands)
                emptyView.visibility = if (commands.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun syncCommands() {
        syncStatus.text = "Syncing…"
        lifecycleScope.launch {
            val result = repository.syncCommandsToTelegram()
            syncStatus.text = if (result.isSuccess)
                "Synced! Reopen your bot's chat in Telegram to see the updated menu."
            else
                "Couldn't sync: ${result.exceptionOrNull()?.message}"
        }
    }

    private fun filledInput(hintText: String, multiline: Boolean = false): Pair<TextInputLayout, TextInputEditText> {
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
        val edit = TextInputEditText(til.context).apply {
            setTextColor(TEXT_PRIMARY)
            if (multiline) { minLines = 2; isSingleLine = false }
        }
        til.addView(edit)
        return til to edit
    }

    private fun showAddEditDialog(existing: BotCommand?) {
        val context = this
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(8))
        }

        val heading = TextView(context).apply {
            text = if (existing == null) "Add command" else "Edit command"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
        }
        container.addView(heading)

        val (commandTil, commandInput) = filledInput("Command — no slash, lowercase (e.g. price)")
        commandInput.setText(existing?.command ?: "")
        container.addView(commandTil)

        val (descTil, descInput) = filledInput("Description shown in the / menu (e.g. See our price list)")
        descInput.setText(existing?.description ?: "")
        container.addView(descTil)

        val (answerTil, answerInput) = filledInput("What the bot replies", multiline = true)
        answerInput.setText(existing?.answer ?: "")
        container.addView(answerTil)

        val scroll = android.widget.ScrollView(context).apply { addView(container) }

        val dialog = AlertDialog.Builder(context)
            .setView(scroll)
            .setPositiveButton("Save") { _, _ ->
                var command = commandInput.text.toString().trim().lowercase().removePrefix("/")
                command = command.replace(Regex("[^a-z0-9_]"), "")
                val description = descInput.text.toString().trim()
                val answer = answerInput.text.toString().trim()

                if (command.isEmpty() || description.isEmpty() || answer.isEmpty()) {
                    Toast.makeText(context, "Fill in command, description, and answer", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val cmd = BotCommand(
                    id = existing?.id ?: 0,
                    command = command,
                    description = description,
                    answer = answer
                )
                lifecycleScope.launch {
                    db.botCommandDao().upsert(cmd)
                    Toast.makeText(context, "Saved — syncing to Telegram…", Toast.LENGTH_SHORT).show()
                    syncCommands()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.window?.setBackgroundDrawable(ContextCompat.getDrawable(context, R.drawable.bg_dialog_card))
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ACCENT_DARK)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(TEXT_SECONDARY)
    }

    private fun deleteCommand(cmd: BotCommand) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Delete this command?")
            .setMessage("\"/${cmd.command}\" will be removed and the / menu will update automatically.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    db.botCommandDao().delete(cmd)
                    syncCommands()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.window?.setBackgroundDrawable(ContextCompat.getDrawable(this, R.drawable.bg_dialog_card))
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(0xFFD64545.toInt())
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(TEXT_SECONDARY)
    }
}

private class CommandAdapter(
    private val onEdit: (BotCommand) -> Unit,
    private val onDelete: (BotCommand) -> Unit
) : RecyclerView.Adapter<CommandAdapter.ViewHolder>() {

    private var items: List<BotCommand> = emptyList()
    fun submitList(newItems: List<BotCommand>) { items = newItems; notifyDataSetChanged() }

    class ViewHolder(val root: LinearLayout, val label: TextView, val detail: TextView) : RecyclerView.ViewHolder(root)

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
        val label = TextView(ctx).apply { textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(TEXT_PRIMARY) }
        val detail = TextView(ctx).apply { textSize = 13f; setTextColor(TEXT_SECONDARY); setPadding(0, dp(6), 0, 0) }
        card.addView(label); card.addView(detail)
        return ViewHolder(card, label, detail)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cmd = items[position]
        holder.label.text = "/${cmd.command}"
        holder.detail.text = "${cmd.description}\n→ ${cmd.answer}\nTap to edit · Long-press to delete"
        holder.root.setOnClickListener { onEdit(cmd) }
        holder.root.setOnLongClickListener { onDelete(cmd); true }
    }

    override fun getItemCount(): Int = items.size
}
