package com.carsense.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.carsense.core.auth.AuthManager
import com.carsense.core.auth.AuthUIEvent
import com.carsense.core.auth.AuthViewModel
import com.carsense.core.auth.TokenStorageService
import com.carsense.features.vehicles.data.db.VehicleDao
import com.carsense.features.bluetooth.presentation.intent.BluetoothIntent
import com.carsense.features.bluetooth.presentation.viewmodel.BluetoothViewModel
// ForegroundLocationService import removed - location tracking handled by DiagnosticLocationService
import com.carsense.core.navigation.AppNavigation
import com.carsense.core.permissions.LocationPermissionHelper
import com.carsense.core.navigation.LocalNavController
import com.carsense.ui.theme.CarSenseTheme
import com.carsense.core.theme.ThemeManager
import com.carsense.core.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var vehicleDao: VehicleDao

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var tokenStorageService: TokenStorageService

    @Inject
    lateinit var themeManager: ThemeManager

    // Use lateinit var instead of lazy with hiltViewModel()
    private lateinit var authViewModel: AuthViewModel

    private val bluetoothManager by lazy {
        applicationContext.getSystemService(BluetoothManager::class.java)
    }
    private val bluetoothAdapter by lazy { bluetoothManager?.adapter }

    private val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    // Location Permission Launchers
    private lateinit var locationPermissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var backgroundLocationPermissionLauncher: ActivityResultLauncher<String>

    // Bluetooth Permission Launchers - adding class-level variables for these
    private lateinit var btPermissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var enableBluetoothLauncher: ActivityResultLauncher<Intent>

    // State for managing dialog visibility
    private var showBackgroundRationaleDialogState by mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before calling super.onCreate() to ensure it affects the activity creation
        applyThemeFromPreferences()
        
        super.onCreate(savedInstanceState)

        // Initialize the AuthViewModel using ViewModelProvider
        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setupBluetoothPermissionLaunchers()
        setupLocationPermissionLaunchers()

        // Request Bluetooth permissions on start. Location permissions will also be checked now.
        requestBluetoothPermissions()
        checkAndRequestLocationPermissions() // Request location permissions on startup as well

        // Handle incoming auth redirect from onCreate, in case Activity was launched fresh
        // Ensure intent is not null before calling handleAuthRedirect, which expects a non-null Intent
        try {
            val currentIntent = intent // Capture the initial intent
            if (currentIntent != null && currentIntent.action == Intent.ACTION_VIEW && currentIntent.data != null) {
                Timber.d("Initial intent has VIEW action, checking for auth callback")
                handleAuthRedirect(currentIntent)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling initial intent")
        }

        setContent {
            val themeMode by themeManager.themeMode.collectAsState(initial = com.carsense.core.theme.ThemeMode.SYSTEM)
            
            CarSenseTheme(themeMode = themeMode) {
                val viewModel = hiltViewModel<BluetoothViewModel>()
                val navController = rememberNavController()
                val bluetoothState by viewModel.state.collectAsState()

                // Provide the NavController to all composables via CompositionLocalProvider
                CompositionLocalProvider(LocalNavController provides navController) {
                    // Collect Auth UI Events from AuthViewModel
                    LaunchedEffect(key1 = Unit) { // Use Unit to launch once
                        authViewModel.uiEvents.collect { event ->
                            when (event) {
                                is AuthUIEvent.LaunchLoginFlow -> {
                                    launchAuthFlow() // Call the Activity's method
                                }

                                is AuthUIEvent.ShowToast -> {
                                    Toast.makeText(
                                        this@MainActivity,
                                        event.message,
                                        Toast.LENGTH_LONG
                                    )
                                        .show()
                                }
                            }
                        }
                    }

                    // Observe Bluetooth connection state to start/stop LocationService
                    LaunchedEffect(bluetoothState.isConnected) {
                        if (bluetoothState.isConnected) {
                            // Permissions should have been requested already.
                            // We just need to ensure they are still granted before starting.
                            Timber.d("Bluetooth connected, location tracking will be handled by DiagnosticLocationService after diagnostic creation.")
                            // Location tracking will be started automatically after diagnostic session is created
                            // No need to start location service directly here
                        } else {
                            Timber.d("Bluetooth disconnected, location tracking will be stopped by DiagnosticLocationService.")
                            // Location tracking will be stopped automatically in BluetoothViewModel.disconnectFromDevice()
                        }
                    }

                    // Main app container
                    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                        // Use our navigation graph from core
                        val rememberedOnLoginClick =
                            remember { { authViewModel.handleLoginLogoutClick() } }
                        AppNavigation(
                            navController = navController,
                            bluetoothViewModel = viewModel,
                            onLoginClick = rememberedOnLoginClick, // Single call to ViewModel
                            authViewModel = authViewModel // Pass the AuthViewModel to navigation
                        )

                        // Show connection and error states
                        ConnectionStateOverlay(viewModel)

                        // Show background rationale dialog if state is true
                        if (showBackgroundRationaleDialogState) {
                            BackgroundLocationRationaleDialog(onConfirm = {
                                showBackgroundRationaleDialogState = false
                                LocationPermissionHelper.requestBackgroundLocationPermission(
                                    backgroundLocationPermissionLauncher
                                )
                            }, onDismiss = {
                                showBackgroundRationaleDialogState = false
                                Timber.w("User dismissed background location rationale.")
                                // Handle if user explicitly denies/dismisses rationale
                            })
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // When the activity resumes, explicitly check the login state again.
        // This can help refresh the UI if it missed an update during a complex lifecycle transition (e.g. returning from browser).
        Timber.d("MainActivity onResume: Calling authViewModel.checkLoginState()")
        authViewModel.checkLoginState()
    }

    // Using public override as ComponentActivity's onNewIntent is public
    // The intent parameter must be non-nullable to match the superclass
    public override fun onNewIntent(intent: Intent) { // intent is non-nullable
        try {
            Timber.d("onNewIntent called with action: ${intent.action}, data: ${intent.data}")
            super.onNewIntent(intent)
            setIntent(intent) // Update the activity's intent.

            // Only process VIEW actions that potentially contain auth data
            if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
                handleAuthRedirect(intent)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling new intent")
            Toast.makeText(
                this,
                "Error processing redirect: ${e.message ?: "Unknown error"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Expects a non-nullable Intent that has matched the criteria for an auth callback.
    private fun handleAuthRedirect(intent: Intent) { // Explicitly non-nullable Intent
        try {
            if (intent.action == Intent.ACTION_VIEW) {
                val data = intent.data
                if (data != null && "carsense" == data.scheme && "auth-callback" == data.host) {
                    Timber.d("Auth callback received: $data")

                    // Safely get query parameters
                    val token = data.getQueryParameter("token")
                    val receivedState = data.getQueryParameter("state")
                    val storedState = tokenStorageService.getCsrfState()

                    Timber.d("Auth callback: token=${token != null}, receivedState=${receivedState != null}, storedState=${storedState != null}")

                    if (receivedState == null || token == null) {
                        Timber.e("Auth callback missing token or state. URI: $intent")
                        Toast.makeText(
                            this, "Login failed: Missing required parameters", Toast.LENGTH_LONG
                        ).show()
                        return
                    }
                    if (storedState == null) {
                        Timber.e("Auth callback received, but no stored CSRF state found.")
                        Toast.makeText(
                            this, "Login failed: Authentication state expired", Toast.LENGTH_LONG
                        ).show()
                        return
                    }

                    if (storedState == receivedState) {
                        Timber.i("CSRF state validated successfully.")
                        tokenStorageService.saveAuthToken(token)
                        Timber.i("Auth token saved successfully!")
                        tokenStorageService.clearCsrfState()

                        // Add visual feedback for successful login
                        Toast.makeText(
                            this, "Login successful", Toast.LENGTH_LONG
                        ).show()

                        // Log token details (truncated for security)
                        val truncatedToken =
                            if (token.length > 10) "${token.take(5)}...${token.takeLast(5)}"
                            else "token_too_short"
                        Timber.d("Auth token received: $token")

                        // Refresh auth state in ViewModel
                        authViewModel.checkLoginState()
                    } else {
                        Timber.e("CSRF state mismatch! Stored: $storedState, Received: $receivedState. Possible CSRF attack.")
                        Toast.makeText(
                            this, "Login failed: Security verification failed", Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error processing authentication redirect")
            Toast.makeText(
                this, "Login failed: ${e.message ?: "Unknown error"}", Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun applyThemeFromPreferences() {
        try {
            val sharedPrefs = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
            val themeString = sharedPrefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name

            val themeMode = try {
                ThemeMode.valueOf(themeString)
            } catch (e: IllegalArgumentException) {
                Timber.w("Invalid theme mode in preferences: $themeString, falling back to SYSTEM")
                ThemeMode.SYSTEM
            }

            val nightMode = when (themeMode) {
                ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            }

            AppCompatDelegate.setDefaultNightMode(nightMode)
            Timber.d("Applied theme mode in MainActivity: $themeMode")
        } catch (e: Exception) {
            Timber.e(e, "Error applying theme preference in MainActivity")
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun setupBluetoothPermissionLaunchers() {
        // Initialize the Bluetooth enable launcher
        enableBluetoothLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { /* Not needed */ }

        // Initialize the Bluetooth permissions launcher
        btPermissionsLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
                val canEnableBluetooth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    perms[Manifest.permission.BLUETOOTH_CONNECT] == true
                } else true

                if (canEnableBluetooth && !isBluetoothEnabled) {
                    enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
            }
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            btPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                )
            )
        } else {
            if (!isBluetoothEnabled) {
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        }
    }

    private fun setupLocationPermissionLaunchers() {
        locationPermissionsLauncher =
            LocationPermissionHelper.createPermissionLauncher(this) { permissionsResult ->
                val fineLocationGranted =
                    permissionsResult[Manifest.permission.ACCESS_FINE_LOCATION] == true
                val coarseLocationGranted =
                    permissionsResult[Manifest.permission.ACCESS_COARSE_LOCATION] == true

                if (fineLocationGranted || coarseLocationGranted) {
                    Timber.d("Foreground location permission GRANTED (Fine or Coarse)")
                    // Now that foreground is granted, check and request background if needed
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        if (LocationPermissionHelper.hasBackgroundLocationPermission(this)) {
                            Timber.d("Background location permission was already GRANTED")
                            // Both foreground and background are now granted, attempt to start service
                            // This will also be caught by the LaunchedEffect if BT is connected
                            // Location tracking will be handled by DiagnosticLocationService after diagnostic creation
                            // No need to start location service directly here
                        } else {
                            Timber.d("Foreground granted, background NOT granted. Showing rationale for background.")
                            showBackgroundRationaleDialogState = true
                        }
                    } else {
                        // Pre-Q, foreground grant is enough
                        Timber.d("Foreground granted (Pre-Q). Ready for location tracking.")
                        // Location tracking will be handled by DiagnosticLocationService after diagnostic creation
                    }
                } else {
                    Timber.w("Foreground Location permission DENIED")
                    // Handle permission denial (e.g., show a message to the user, disable location features)
                    // You might want to show a dialog explaining why the permission is crucial
                }
            }

        backgroundLocationPermissionLauncher =
            LocationPermissionHelper.createBackgroundPermissionLauncher(this) { isGranted ->
                if (isGranted) {
                    Timber.d("Background Location permission GRANTED after request")
                    // Both foreground (previously granted) and background are now granted
                    // This will also be caught by the LaunchedEffect if BT is connected
                    // Location tracking will be handled by DiagnosticLocationService after diagnostic creation
                    // No need to start location service directly here
                } else {
                    Timber.w("Background Location permission DENIED after request")
                    // Handle background permission denial. User might still be able to use app with foreground only.
                }
            }
    }

    private fun checkAndRequestLocationPermissions() {
        // Check for foreground permissions first
        if (LocationPermissionHelper.hasFineLocationPermission(this) || LocationPermissionHelper.hasCoarseLocationPermission(
                this
            )
        ) {
            Timber.d("Foreground location permission (Fine or Coarse) is already granted.")
            // If foreground is granted, check for background (if Q+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !LocationPermissionHelper.hasBackgroundLocationPermission(
                    this
                )
            ) {
                Timber.d("Foreground granted, but Background location permission is NOT granted. Showing rationale.")
                showBackgroundRationaleDialogState = true
            } else {
                // All necessary permissions are already granted (either pre-Q, or Q+ with background)
                Timber.d("All necessary location permissions (foreground and, if applicable, background) are already granted.")
                // Attempt to start service if BT is also connected - this check is also in LaunchedEffect
                // Location tracking will be handled by DiagnosticLocationService after diagnostic creation
                // No need to start location service directly here
            }
        } else {
            // Foreground permissions are not granted, request them.
            Timber.d("Foreground location permissions NOT granted. Requesting them now.")
            LocationPermissionHelper.requestLocationPermissions(locationPermissionsLauncher)
        }
    }

    // Location service methods removed - location tracking is now handled by DiagnosticLocationService
    // after diagnostic session creation in BluetoothViewModel

    // Example of when to stop the service, e.g., when the app is being destroyed,
    // or when a trip ends, or the user logs out.
    override fun onDestroy() {
        super.onDestroy()
        // If LocationService is running, it might be desirable to stop it explicitly
        // if the app is completely destroyed and no longer meant to track.
        // However, if tracking should persist (e.g. user just closed activity but trip is ongoing),
        // then this stop call should be conditional or handled elsewhere (e.g. user action to end trip)
        // stopLocationService()
    }

    /**
     * Launches the authentication flow using Chrome Custom Tabs
     * This needs to be done from an Activity context
     */
    private fun launchAuthFlow() {
        val authUri = authManager.prepareAuthFlow()
        Timber.d("Launching auth flow with URL: $authUri")

        val customTabsIntentBuilder = CustomTabsIntent.Builder()
        // You can customize the CCT further here, e.g.:
        // customTabsIntentBuilder.setToolbarColor(ContextCompat.getColor(this, R.color.your_toolbar_color))
        // customTabsIntentBuilder.setShowTitle(true)
        val customTabsIntent = customTabsIntentBuilder.build()

        // Get the package name of a CCT provider
        val packageName = CustomTabsClient.getPackageName(
            this, null
        ) // Pass null for a list of preferred packages to use the default

        if (packageName == null) {
            Timber.e("No application found to handle Custom Tabs. Falling back to standard browser intent.")
            // Fallback to a standard browser intent if no CCT handler is found
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, authUri)
                startActivity(browserIntent)
            } catch (e: Exception) {
                Timber.e(e, "Error launching standard browser intent.")
                Toast.makeText(
                    this, "No browser found to handle authentication.", Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        // If a CCT handler exists, proceed with launching it
        customTabsIntent.intent.setPackage(packageName) // Explicitly set the package

        try {
            customTabsIntent.launchUrl(this, authUri)
        } catch (e: Exception) {
            Timber.e(e, "Error launching Custom Chrome Tab for authentication.")
            Toast.makeText(
                this,
                "Could not launch browser for authentication. Error: ${e.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

@Composable
fun BackgroundLocationRationaleDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Background Location Access") },
        text = { Text("CarSense requires background location access to continuously track your trips, even when the app is minimized or the screen is off. This data is saved locally on your device and can be synced to your account when you choose.") },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Grant Permission") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("No Thanks") }
        })
}

@Composable
fun ConnectionStateOverlay(viewModel: BluetoothViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

//    // Show success toast when diagnostic is created
//    LaunchedEffect(state.diagnosticUuid, state.diagnosticCreationInProgress) {
//        if (state.diagnosticUuid != null && !state.diagnosticCreationInProgress) {
//            // Show success toast
//            Toast.makeText(
//                context,
//                "Diagnostic record created successfully",
//                Toast.LENGTH_SHORT
//            ).show()
//        }
//    }

    // Show diagnostic creation progress
    if (state.diagnosticCreationInProgress) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0x80000000) // Semi-transparent background
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Creating diagnostic record...",
                    color = Color.White
                )
            }
        }
    }

    // Show connecting overlay
    if (state.isConnecting) {
        Surface(
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Connecting to OBD2 device...")
            }
        }
    }

    // Show error messages if any
    state.errorMessage?.let { errorMessage ->
        AlertDialog(
            onDismissRequest = { viewModel.processIntent(BluetoothIntent.DismissError) },
            title = { Text("Connection Error") },
            text = { Text(errorMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.processIntent(BluetoothIntent.DisconnectFromDevice)
                    }) { Text("OK") }
            })
    }

}



