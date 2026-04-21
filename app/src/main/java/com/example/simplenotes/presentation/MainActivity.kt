package com.example.simplenotes.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.simplenotes.data.repository.AppTheme
import com.example.simplenotes.presentation.screens.NoteEditScreen
import com.example.simplenotes.presentation.screens.NoteListScreen
import com.example.simplenotes.presentation.screens.TrashScreen
import com.example.simplenotes.presentation.ui.theme.SimpleNotesTheme
import com.example.simplenotes.presentation.service.AlarmService
import com.example.simplenotes.presentation.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private var noteIdFromIntent by mutableStateOf<Long?>(null)
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handleIntent(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val appTheme by settingsViewModel.appTheme.collectAsStateWithLifecycle()
            
            SimpleNotesTheme(appTheme = appTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    LaunchedEffect(noteIdFromIntent) {
                        noteIdFromIntent?.let { id ->
                            if (id != -1L) {
                                navController.navigate("edit/$id") {
                                    popUpTo("list") { inclusive = false }
                                    launchSingleTop = true
                                }
                                noteIdFromIntent = null
                            }
                        }
                    }

                    NavHost(
                        navController = navController, 
                        startDestination = "list",
                        enterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                animationSpec = tween(400)
                            )
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Left,
                                animationSpec = tween(400)
                            )
                        },
                        popEnterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                animationSpec = tween(400)
                            )
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Right,
                                animationSpec = tween(400)
                            )
                        }
                    ) {
                        composable("list") {
                            NoteListScreen(navController)
                        }
                        composable("trash") {
                            TrashScreen(navController)
                        }
                        composable("edit/{noteId}") { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("noteId")?.toLongOrNull()
                            NoteEditScreen(navController, noteId = id)
                        }
                        composable("edit") {
                            NoteEditScreen(navController, noteId = null)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "OPEN_NOTE" || intent?.hasExtra("note_id") == true) {
            val id = intent.getLongExtra("note_id", -1L)
            if (id != -1L) {
                noteIdFromIntent = id
            }
            val stopIntent = Intent(this, AlarmService::class.java).apply {
                action = AlarmService.ACTION_STOP_ALARM
            }
            stopService(stopIntent)
        }
    }
}
