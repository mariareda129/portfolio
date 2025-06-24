package com.example.arabicsignlanguage

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.arabicsignlanguage.databinding.ActivityDictionaryBinding
import com.example.arabicsignlanguage.databinding.ItemDictionaryEntryBinding
import com.google.android.material.button.MaterialButton
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.storage
import io.ktor.client.HttpClient
import io.ktor.client.request.head
import io.ktor.http.isSuccess
import kotlinx.coroutines.launch

class DictionaryActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityDictionaryBinding
    private lateinit var supabase: SupabaseClient
    private lateinit var dictionaryAdapter: DictionaryAdapter
    private val ktorClient = HttpClient()
    private var isNetworkAvailable = true
    
    private val networkReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            checkNetworkStatus()
        }
    }
    
    private val dictionaryEntries = listOf(
        mapOf("phrase" to "السلام عليكم", "video_name" to "alslam alikom", "english" to "Peace be upon you"),
        mapOf("phrase" to "وعليكم السلام", "video_name" to "walikoom alslam", "english" to "And peace be upon you too"),
        mapOf("phrase" to "الساعة كام؟", "video_name" to "what is the time", "english" to "What time is it?"),
        mapOf("phrase" to "ما اسمك؟", "video_name" to "what is your name", "english" to "What is your name?"),
        mapOf("phrase" to "اسف", "video_name" to "sorry", "english" to "Sorry"),
        mapOf("phrase" to "شكرا", "video_name" to "thanks", "english" to "Thank you"),
        mapOf("phrase" to "ساعدني", "video_name" to "help", "english" to "Help me"),
        mapOf("phrase" to "كيف حالك؟", "video_name" to "how are you", "english" to "How are you?"),
        mapOf("phrase" to "كم عمرك؟", "video_name" to "how old are you", "english" to "How old are you?"),
        mapOf("phrase" to "انا ما فهمت", "video_name" to "I am not understand", "english" to "I don't understand"),
        mapOf("phrase" to "انا ذاهب", "video_name" to "I will go", "english" to "I am going"),
        mapOf("phrase" to "انا احب", "video_name" to "love", "english" to "I love"),
        mapOf("phrase" to "انتظر", "video_name" to "wait", "english" to "Wait"),
        mapOf("phrase" to "خائف", "video_name" to "waried", "english" to "Afraid"),
        mapOf("phrase" to "اين انت تعيش؟", "video_name" to "where are you live", "english" to "Where do you live?"),
        mapOf("phrase" to "اين المكان؟", "video_name" to "where is the place", "english" to "Where is the place?")
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDictionaryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        checkNetworkStatus()
        setupSupabase()
        setupRecyclerView()
        setupSearchFunctionality()
        setupToolbar()
        
        // Register network connectivity receiver
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(networkReceiver, filter)
    }
    
    private fun setupSupabase() {
        supabase = createSupabaseClient(
            supabaseUrl = "https://rqtetpffdoqlqktyzgeb.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJxdGV0cGZmZG9xbHFrdHl6Z2ViIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDQzMDA5MTQsImV4cCI6MjA1OTg3NjkxNH0.YJ_s2vfL6o-TATvkdwNKQRD9lFKK5564phcHGwiSjvE"
        ) {
            install(io.github.jan.supabase.storage.Storage)
        }
    }
    
    private fun checkNetworkStatus() {
        isNetworkAvailable = NetworkUtils.isNetworkAvailable(this)
        if (::dictionaryAdapter.isInitialized) {
            dictionaryAdapter.updateNetworkStatus(isNetworkAvailable)
        }
    }
    
    private fun setupRecyclerView() {
        dictionaryAdapter = DictionaryAdapter(dictionaryEntries, isNetworkAvailable) { entry ->
            if (isNetworkAvailable) {
                playVideo(entry)
            } else {
                showOfflineMessage()
            }
        }
        
        binding.dictionaryRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@DictionaryActivity)
            adapter = dictionaryAdapter
        }
    }
    
    private fun setupSearchFunctionality() {
        // Add focus listeners to hide/show hint text
        binding.searchEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // Hide hint when focused
                binding.searchLayout.isHintEnabled = false
            } else {
                // Show hint when not focused and text is empty
                if (binding.searchEditText.text.isNullOrEmpty()) {
                    binding.searchLayout.isHintEnabled = true
                }
            }
        }
        
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterEntries(s.toString())
            }
        })
    }
    
    private fun setupToolbar() {
        binding.toolbarDictionary.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun filterEntries(query: String) {
        val filteredEntries = if (query.isBlank()) {
            dictionaryEntries
        } else {
            dictionaryEntries.filter {
                it["phrase"]?.contains(query, ignoreCase = true) == true
            }
        }
        dictionaryAdapter.updateEntries(filteredEntries)
    }
    
    private fun showOfflineMessage() {
        AlertDialog.Builder(this)
            .setTitle("لا يوجد اتصال بالإنترنت")
            .setMessage("يرجى التحقق من اتصالك بالإنترنت لمشاهدة الفيديوهات")
            .setPositiveButton("موافق", null)
            .show()
    }
    
    private fun playVideo(entry: Map<String, String>) {
        if (!isNetworkAvailable) {
            showOfflineMessage()
            return
        }
        
        val videoName = entry["video_name"] ?: return
        
        binding.loadingProgress.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val videoUrl = getVideoUrl(videoName)
                binding.loadingProgress.visibility = View.GONE
                
                if (videoUrl.isNotEmpty()) {
                    showVideoDialog(videoUrl, entry["phrase"] ?: "")
                } else {
                    Toast.makeText(this@DictionaryActivity, "فشل الاتصال بالفيديو", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.loadingProgress.visibility = View.GONE
                Toast.makeText(this@DictionaryActivity, "فشل الاتصال بالفيديو", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private suspend fun getVideoUrl(videoName: String): String {
        val targetName = "${videoName}.mp4"
        val url = supabase.storage.from("videos").publicUrl(targetName)
        return try {
            val response = ktorClient.head(url)
            if (response.status.isSuccess()) url else ""
        } catch (e: Exception) {
            ""
        }
    }
    
    private fun showVideoDialog(videoUrl: String, title: String) {
        try {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_video_player, null)
            val playerView = dialogView.findViewById<PlayerView>(R.id.player_view)
            val titleText = dialogView.findViewById<TextView>(R.id.video_title)
            
            titleText.text = title
            
            val exoPlayer = ExoPlayer.Builder(this).build()
            playerView.player = exoPlayer
            
            val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
            
            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setNegativeButton("إغلاق") { _, _ ->
                    try {
                        exoPlayer.release()
                    } catch (e: Exception) {
                        Log.e("DictionaryActivity", "Error releasing ExoPlayer: ${e.message}", e)
                    }
                }
                .create()
            
            dialog.setOnDismissListener {
                try {
                    exoPlayer.release()
                } catch (e: Exception) {
                    Log.e("DictionaryActivity", "Error releasing ExoPlayer on dismiss: ${e.message}", e)
                }
            }
            
            if (!isFinishing && !isDestroyed) {
                dialog.show()
            }
        } catch (e: Exception) {
            Log.e("DictionaryActivity", "Error showing video dialog: ${e.message}", e)
            Toast.makeText(this, "فشل في عرض الفيديو", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            ktorClient.close()
        } catch (e: Exception) {
            Log.e("DictionaryActivity", "Error closing ktorClient: ${e.message}", e)
        }
        
        try {
            unregisterReceiver(networkReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
            Log.d("DictionaryActivity", "Network receiver not registered or already unregistered")
        }
    }
}

class DictionaryAdapter(
    private var entries: List<Map<String, String>>,
    private var isNetworkAvailable: Boolean,
    private val onItemClick: (Map<String, String>) -> Unit
) : RecyclerView.Adapter<DictionaryAdapter.ViewHolder>() {
    
    class ViewHolder(val binding: ItemDictionaryEntryBinding) : RecyclerView.ViewHolder(binding.root)
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDictionaryEntryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        
        holder.binding.phraseText.text = entry["phrase"]
        holder.binding.englishText.text = entry["english"]
        
        // Handle network status
        if (isNetworkAvailable) {
            holder.binding.offlineMessage.visibility = View.GONE
            holder.binding.playButton.isEnabled = true
            holder.binding.playButton.alpha = 1.0f
            holder.binding.root.setOnClickListener {
                onItemClick(entry)
            }
            holder.binding.playButton.setOnClickListener {
                onItemClick(entry)
            }
        } else {
            holder.binding.offlineMessage.visibility = View.VISIBLE
            holder.binding.playButton.isEnabled = false
            holder.binding.playButton.alpha = 0.5f
            holder.binding.root.setOnClickListener {
                onItemClick(entry) // This will show offline message
            }
            holder.binding.playButton.setOnClickListener {
                onItemClick(entry) // This will show offline message
            }
        }
    }
    
    override fun getItemCount() = entries.size
    
    fun updateEntries(newEntries: List<Map<String, String>>) {
        entries = newEntries
        notifyDataSetChanged()
    }
    
    fun updateNetworkStatus(networkAvailable: Boolean) {
        isNetworkAvailable = networkAvailable
        notifyDataSetChanged()
    }
}
