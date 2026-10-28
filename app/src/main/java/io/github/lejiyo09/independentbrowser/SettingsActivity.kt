package io.github.lejiyo09.independentbrowser

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.github.lejiyo09.independentbrowser.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.padForSystemBarsAndIme()
        binding.btnClose.setOnClickListener { finish() }
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            findPreference<Preference>("clear_data")?.setOnPreferenceClickListener {
                requireActivity().apply {
                    setResult(RESULT_CLEAR_DATA)
                    finish()
                }
                true
            }

            val webView = WebView.getCurrentWebViewPackage()?.versionName ?: "?"
            findPreference<Preference>("about")?.summary =
                getString(R.string.about_version, BuildConfig.VERSION_NAME, webView)
        }
    }

    companion object {
        const val RESULT_CLEAR_DATA = RESULT_FIRST_USER + 1
    }
}
