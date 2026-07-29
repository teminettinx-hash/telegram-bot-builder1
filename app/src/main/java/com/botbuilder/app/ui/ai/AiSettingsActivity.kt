package com.botbuilder.app.ui.ai

import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.botbuilder.app.R
import com.botbuilder.app.data.local.SecureStore
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

private const val ACCENT = 0xFF6C74B8.toInt()
private const val TEXT_PRIMARY = 0xFF1D1D2B.toInt()
private const val TEXT_SECONDARY = 0xFF6E6E82.toInt()

class AiSettingsActivity : AppCompatActivity() {

    private lateinit var secureStore: SecureStore

    private lateinit var aiSwitch: MaterialSwitch
    private lateinit var providerDropdown: MaterialAutoCompleteTextView
    private lateinit var apiKeyInput: TextInputEditText
    private lateinit var baseUrlInput: TextInputEditText
    private lateinit var modelInput: TextInputEditText
    private lateinit var tempInput: TextInputEditText
    private lateinit var maxTokensInput: TextInputEditText
    private lateinit var systemPromptInput: TextInputEditText

    private val providers = listOf("Gemini (has a free tier)", "OpenAI", "Anthropic", "OpenRouter", "Custom endpoint")
    private val providerKeys = listOf("gemini", "openai", "anthropic", "openrouter", "custom")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureStore = SecureStore(applicationContext)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFEDEEF7.toInt())
        }
        val padding = dp(20)

        val title = TextView(this).apply {
            text = "AI Settings"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_PRIMARY)
            setPadding(padding, dp(24), padding, dp(4))
        }
        val subtitle = TextView(this).apply {
            text = "AI only replies when nothing in Auto Replies matches. Bring your own API key — you're billed directly by the provider, not by this app."
            textSize = 13f
            setTextColor(TEXT_SECONDARY)
            setPadding(padding, 0, padding, dp(20))
        }

        val switchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(padding, 0, padding, dp(16))
        }
        val switchLabel = TextView(this).apply {
            text = "Enable AI replies"
            textSize = 15f
            setTextColor(TEXT_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        aiSwitch = MaterialSwitch(this).apply {
            isChecked = secureStore.aiEnabled
            thumbTintList = android.content.res.ColorStateList.valueOf(ACCENT)
        }
        switchRow.addView(switchLabel)
        switchRow.addView(aiSwitch)

        val formContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, padding)
        }

        val (providerTil, providerField) = dropdownField("AI provider")
        providerDropdown = providerField
        providerDropdown.setSimpleItems(providers.toTypedArray())
        val savedProviderIndex = providerKeys.indexOf(secureStore.aiProvider ?: "gemini").coerceAtLeast(0)
        providerDropdown.setText(providers[savedProviderIndex], false)
        formContainer.addView(providerTil)

        val (apiKeyTil, apiKeyField) = filledInput("API key")
        apiKeyInput = apiKeyField
        apiKeyInput.setText(secureStore.aiApiKey ?: "")
        apiKeyInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        formContainer.addView(apiKeyTil)

        val (baseUrlTil, baseUrlField) = filledInput("Base URL (only needed for Custom endpoint)")
        baseUrlInput = baseUrlField
        baseUrlInput.setText(secureStore.aiBaseUrl ?: "")
        formContainer.addView(baseUrlTil)

        val (modelTil, modelField) = filledInput("Model name (leave blank for a sensible default)")
        modelInput = modelField
        modelInput.setText(secureStore.aiModel ?: "")
        formContainer.addView(modelTil)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val (tempTil, tempField) = filledInput("Temperature (0.0–1.0)")
        tempInput = tempField
        tempInput.setText(secureStore.aiTemperature.toString())
        tempTil.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = dp(8) }

        val (maxTokensTil, maxTokensField) = filledInput("Max tokens")
        maxTokensInput = maxTokensField
        maxTokensInput.setText(secureStore.aiMaxTokens.toString())
        maxTokensTil.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = dp(8) }

        row.addView(tempTil)
        row.addView(maxTokensTil)
        formContainer.addView(row)

        val (promptTil, promptField) = filledInput("System prompt — tells the AI how to behave", multiline = true)
        systemPromptInput = promptField
        systemPromptInput.setText(secureStore.aiSystemPrompt ?: "You are a friendly, helpful assistant.")
        formContainer.addView(promptTil)

        val note = TextView(this).apply {
            text = "Your saved Auto Replies (labels + answers) are automatically added to this prompt when AI is used, so pricing and other saved info stays accurate."
            textSize = 12f
            setTextColor(TEXT_SECONDARY)
            setPadding(0, dp(4), 0, dp(4))
        }
        formContainer.addView(note)

        val saveButton = MaterialButton(this).apply {
            text = "Save AI Settings"
            isAllCaps = false
            textSize = 15f
            cornerRadius = dp(16)
            backgroundTintList = android.content.res.ColorStateList.valueOf(ACCENT)
            setOnClickListener { saveSettings() }
        }
        formContainer.addView(saveButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).also { it.topMargin = dp(8) })

        val scroll = ScrollView(this).apply {
            addView(formContainer)
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(switchRow)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        setContentView(root)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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
            if (multiline) { minLines = 3; isSingleLine = false }
        }
        til.addView(edit)
        return til to edit
    }

    private fun dropdownField(hintText: String): Pair<TextInputLayout, MaterialAutoCompleteTextView> {
        val til = TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_FilledBox_ExposedDropdownMenu).apply {
            hint = hintText
            boxBackgroundColor = 0x14000000
            setBoxCornerRadii(dp(14).toFloat(), dp(14).toFloat(), dp(14).toFloat(), dp(14).toFloat())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.topMargin = dp(10) }
        }
        val dropdown = MaterialAutoCompleteTextView(this)
        til.addView(dropdown)
        return til to dropdown
    }

    private fun saveSettings() {
        val providerIndex = providers.indexOf(providerDropdown.text.toString()).coerceAtLeast(0)
        val providerKey = providerKeys[providerIndex]

        if (aiSwitch.isChecked && apiKeyInput.text.toString().trim().isEmpty()) {
            Toast.makeText(this, "Enter an API key to enable AI, or turn AI off", Toast.LENGTH_SHORT).show()
            return
        }

        val temp = tempInput.text.toString().toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.7f
        val maxTokens = maxTokensInput.text.toString().toIntOrNull()?.coerceIn(50, 4000) ?: 512

        secureStore.aiEnabled = aiSwitch.isChecked
        secureStore.aiProvider = providerKey
        secureStore.aiApiKey = apiKeyInput.text.toString().trim()
        secureStore.aiBaseUrl = baseUrlInput.text.toString().trim().ifEmpty { null }
        secureStore.aiModel = modelInput.text.toString().trim().ifEmpty { null }
        secureStore.aiTemperature = temp
        secureStore.aiMaxTokens = maxTokens
        secureStore.aiSystemPrompt = systemPromptInput.text.toString().trim().ifEmpty { "You are a friendly, helpful assistant." }

        Toast.makeText(this, "AI settings saved", Toast.LENGTH_SHORT).show()
        finish()
    }
}
