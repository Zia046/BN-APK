package com.example.bn

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.util.Locale

// ---------------------------------------------------------------------------
// Fixed developer credit. This is hard-coded and has no edit/delete option.
// ---------------------------------------------------------------------------
private const val DEV_NAME = "Burhan Nexus"
private const val DEV_PHONE = "03475093446"

// Fixed help / support details (shown in the Help dialog).
private const val HELP_NAME = "Burhan Nexus"
private const val HELP_ADDRESS = "Sahara Center, opposite 2D stand, Jowar Chowk Buner"
private const val HELP_PHONE = "03475093446"

// Digit 0..9 -> letter (index = digit). Default code.
private const val DEFAULT_MAP = "AEIOUSBLWP"
private const val MAX_INPUT_LENGTH = 20

private val DeepBlue = Color(0xFF0B2E5C)
private val Blue = Color(0xFF144B82)
private val ButtonBlue = Color(0xFF1964AF)
private val Green = Color(0xFF146E41)
private val Red = Color(0xFFD32F2F)
private val ErrorRed = Color(0xFFC0392B)
private val Gold = Color(0xFFFFC107)
private val PageTop = Color(0xFFF5F7FA)
private val PageBottom = Color(0xFFE3EBF6)

private fun validMapping(s: String?): String {
    if (s == null) return DEFAULT_MAP
    if (s.length != 10) return DEFAULT_MAP
    if (!s.all { it in 'A'..'Z' }) return DEFAULT_MAP
    if (s.toSet().size != 10) return DEFAULT_MAP
    return s
}

/** Secret code (letters) -> price. Returns Pair(price, errorMessage). */
private fun decodePrice(raw: String, mapping: String): Pair<String, String> {
    val code = raw.trim().uppercase(Locale.ROOT)
    if (code.isEmpty()) return "" to "Please enter your secret code."

    val digits = StringBuilder()
    for (letter in code) {
        val index = mapping.indexOf(letter)
        if (index < 0) return "" to "Invalid code character: $letter"
        digits.append('0' + index)
    }
    val number = BigInteger(digits.toString())
    return String.format(Locale.US, "%,d", number) to ""
}

/** Price (numbers) -> secret code (letters). Returns Pair(code, errorMessage). */
private fun generateCode(raw: String, mapping: String): Pair<String, String> {
    val digits = raw.trim()
    if (digits.isEmpty()) return "" to "Please enter the purchase price."

    val sb = StringBuilder()
    for (ch in digits) {
        if (ch !in '0'..'9') return "" to "Only numbers (0-9) are allowed."
        sb.append(mapping[ch - '0'])
    }
    return sb.toString() to ""
}

private fun decodeLogo(context: Context, uri: Uri): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    } catch (e: Exception) {
        null
    }
}

// ---------------------------------------------------------------------------
// App state (saved on the phone, survives closing the app)
// ---------------------------------------------------------------------------
class AppState(context: Context) {
    private val sp = context.getSharedPreferences("bn_prefs", Context.MODE_PRIVATE)
    private val logoFile = File(context.filesDir, "shop_logo.png")

    var setupDone by mutableStateOf(sp.getBoolean("setup_done", false))
        private set
    var shopName by mutableStateOf(sp.getString("shop_name", "") ?: "")
        private set
    var address by mutableStateOf(sp.getString("address", "") ?: "")
        private set
    var phone by mutableStateOf(sp.getString("phone", "") ?: "")
        private set
    var mapping by mutableStateOf(validMapping(sp.getString("mapping", null)))
        private set
    var logo by mutableStateOf<Bitmap?>(loadLogo())
        private set

    private fun loadLogo(): Bitmap? {
        return try {
            if (logoFile.exists()) BitmapFactory.decodeFile(logoFile.path) else null
        } catch (e: Exception) {
            null
        }
    }

    fun saveShop(name: String, addr: String, ph: String, logoChanged: Boolean, newLogo: Bitmap?) {
        if (logoChanged) {
            if (newLogo != null) {
                try {
                    FileOutputStream(logoFile).use {
                        newLogo.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                } catch (e: Exception) {
                    // ignore: logo just will not be kept
                }
            } else {
                logoFile.delete()
            }
            logo = newLogo
        }
        sp.edit()
            .putBoolean("setup_done", true)
            .putString("shop_name", name)
            .putString("address", addr)
            .putString("phone", ph)
            .apply()
        shopName = name
        address = addr
        phone = ph
        setupDone = true
    }

    fun saveMapping(newMapping: String) {
        val clean = validMapping(newMapping)
        sp.edit().putString("mapping", clean).apply()
        mapping = clean
    }
}

private enum class Screen { Setup, Main, EditShop, EditCode }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val state = AppState(applicationContext)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = ButtonBlue,
                    onPrimary = Color.White,
                    surface = Color.White,
                    onSurface = Color(0xFF1B1B1B)
                )
            ) {
                BNApp(state)
            }
        }
    }
}

@Composable
fun BNApp(state: AppState) {
    var screen by remember {
        mutableStateOf(if (state.setupDone) Screen.Main else Screen.Setup)
    }
    var showOffers by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    BackHandler(enabled = screen == Screen.EditShop || screen == Screen.EditCode) {
        screen = Screen.Main
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PageTop, PageBottom)))
            .imePadding()
    ) {
        when (screen) {
            Screen.Main -> MainToolbar(
                shopName = state.shopName,
                onChangeInfo = { screen = Screen.EditShop },
                onEditCode = { screen = Screen.EditCode },
                onOffers = { showOffers = true },
                onHelp = { showHelp = true }
            )

            Screen.Setup -> SimpleBar("Welcome! Set up your shop", null)
            Screen.EditShop -> SimpleBar("Change information") { screen = Screen.Main }
            Screen.EditCode -> SimpleBar("Edit secret code") { screen = Screen.Main }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (screen) {
                Screen.Setup -> ShopForm(
                    subtitle = "This is asked only once. You can change it later from the menu.",
                    state = state,
                    saveLabel = "SAVE & START",
                    onSaved = { screen = Screen.Main }
                )

                Screen.Main -> MainScreen(state)

                Screen.EditShop -> ShopForm(
                    subtitle = "",
                    state = state,
                    saveLabel = "SAVE",
                    onSaved = { screen = Screen.Main }
                )

                Screen.EditCode -> CodeEditorScreen(
                    current = state.mapping,
                    onSave = {
                        state.saveMapping(it)
                        screen = Screen.Main
                    }
                )
            }
        }

        DeveloperFooter()
    }

    if (showOffers) {
        AlertDialog(
            onDismissRequest = { showOffers = false },
            confirmButton = {
                TextButton(onClick = { showOffers = false }) { Text("OK") }
            },
            title = { Text("Offers", fontWeight = FontWeight.Bold) },
            text = { Text("Coming soon", fontSize = 18.sp) }
        )
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text("OK") }
            },
            title = { Text("Help", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(HELP_NAME, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Blue)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("📍  $HELP_ADDRESS", fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("📞  $HELP_PHONE", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Top bars
// ---------------------------------------------------------------------------
@Composable
fun GradientBar(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(DeepBlue, Blue)))
            .statusBarsPadding()
    ) {
        content()
    }
}

@Composable
fun ToolbarButton(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            icon()
        }
        Text(text = label, color = Color.White, fontSize = 11.sp)
    }
}

@Composable
fun MainToolbar(
    shopName: String,
    onChangeInfo: () -> Unit,
    onEditCode: () -> Unit,
    onOffers: () -> Unit,
    onHelp: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    GradientBar {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Settings: three small lines, opens a menu.
            Box {
                ToolbarButton(label = "Settings", onClick = { menuOpen = true }) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .size(width = 22.dp, height = 3.dp)
                                    .background(Color.White, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Change information") },
                        onClick = {
                            menuOpen = false
                            onChangeInfo()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit secret code") },
                        onClick = {
                            menuOpen = false
                            onEditCode()
                        }
                    )
                }
            }

            Text(
                text = shopName,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            )

            ToolbarButton(label = "Offers", onClick = onOffers) {
                Text("🎁", fontSize = 22.sp)
            }

            ToolbarButton(label = "Help", onClick = onHelp) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("?", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SimpleBar(title: String, onBack: (() -> Unit)?) {
    GradientBar {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Text("←", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Footer: always visible, cannot be edited or removed from inside the app.
// ---------------------------------------------------------------------------
@Composable
fun DeveloperFooter() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(DeepBlue, Blue)))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Developed by",
                color = Color.White,
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic
            )
            Text(
                text = DEV_NAME,
                color = Gold,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic
            )
        }
        Text(
            text = "📞  $DEV_PHONE",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ---------------------------------------------------------------------------
// Main screen: decode (code -> price) and generate (price -> code)
// ---------------------------------------------------------------------------
@Composable
fun Segment(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) ButtonBlue else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Blue,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun MainScreen(state: AppState) {
    var mode by remember { mutableStateOf(0) } // 0 = decode, 1 = generate
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    fun process() {
        val pair = if (mode == 0) {
            decodePrice(input, state.mapping)
        } else {
            generateCode(input, state.mapping)
        }
        result = pair.first
        error = pair.second
    }

    fun clearAll() {
        input = ""
        result = ""
        error = ""
    }

    fun switchMode(newMode: Int) {
        mode = newMode
        clearAll()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Shop header
        val logo = state.logo
        if (logo != null) {
            Image(
                bitmap = logo.asImageBitmap(),
                contentDescription = "Shop logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(18.dp))
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        Text(
            text = state.shopName,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Blue,
            textAlign = TextAlign.Center
        )

        if (state.address.isNotEmpty() || state.phone.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(14.dp))
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.address.isNotEmpty()) {
                    Text("📍", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = state.address,
                        fontSize = 13.sp,
                        color = Color(0xFF333333),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (state.address.isNotEmpty() && state.phone.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .size(width = 1.dp, height = 28.dp)
                            .background(Color(0xFFCCD5E0))
                    )
                }
                if (state.phone.isNotEmpty()) {
                    Text("📞", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = state.phone,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF222222)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Main card
        val cardShape = RoundedCornerShape(18.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, cardShape)
                .background(Color.White, cardShape)
                .border(1.5.dp, ButtonBlue, cardShape)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier
                        .background(DeepBlue, RoundedCornerShape(50))
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔒", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (mode == 0) "PRICE DECODER" else "CODE GENERATOR",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mode switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE6EDF7), RoundedCornerShape(10.dp))
                    .padding(4.dp)
            ) {
                Segment(
                    text = "Code → Price",
                    selected = mode == 0,
                    modifier = Modifier.weight(1f)
                ) { switchMode(0) }
                Segment(
                    text = "Price → Code",
                    selected = mode == 1,
                    modifier = Modifier.weight(1f)
                ) { switchMode(1) }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = if (mode == 0) "Enter your secret code:" else "Enter purchase price (numbers):",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B1B1B)
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = input,
                onValueChange = { new ->
                    input = if (mode == 0) {
                        new.uppercase(Locale.ROOT).take(MAX_INPUT_LENGTH)
                    } else {
                        new.filter { it in '0'..'9' }.take(MAX_INPUT_LENGTH)
                    }
                    error = ""
                },
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 24.sp,
                    color = Color.Black
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = if (mode == 0) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
                    keyboardType = if (mode == 0) KeyboardType.Text else KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { process() }),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = if (mode == 0) "Purchase Price (Rs.)" else "Secret code",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B1B1B)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF1F5FA), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFD5DEEA), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = if (result.isEmpty()) " " else result,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = if (mode == 0) FontFamily.Default else FontFamily.Monospace,
                    color = if (mode == 0) Green else Blue,
                    textAlign = TextAlign.End
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (error.isEmpty()) " " else error,
                fontSize = 14.sp,
                color = ErrorRed
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { process() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ButtonBlue,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                ) {
                    Text(
                        text = if (mode == 0) "DECODE" else "GENERATE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = { clearAll() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE1E6EC),
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                ) {
                    Text(text = "CLEAR / NEW", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------------------
// Shop details form (first-time setup, and later editing)
// ---------------------------------------------------------------------------
@Composable
fun ShopForm(
    subtitle: String,
    state: AppState,
    saveLabel: String,
    onSaved: () -> Unit
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf(state.shopName) }
    var address by remember { mutableStateOf(state.address) }
    var phone by remember { mutableStateOf(state.phone) }
    var logo by remember { mutableStateOf(state.logo) }
    var logoChanged by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bmp = decodeLogo(context, uri)
            if (bmp != null) {
                logo = bmp
                logoChanged = true
                error = ""
            } else {
                error = "Could not read this image. Please choose another one."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        if (subtitle.isNotEmpty()) {
            Text(text = subtitle, fontSize = 14.sp, color = Color.DarkGray)
            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it; error = "" },
            label = { Text("Company / Shop name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Address") },
            minLines = 2,
            maxLines = 3,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Contact number") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(text = "Logo", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            val current = logo
            if (current != null) {
                Image(
                    bitmap = current.asImageBitmap(),
                    contentDescription = "Shop logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFFE1E6EC), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No logo", fontSize = 12.sp, color = Color.DarkGray)
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                OutlinedButton(onClick = { picker.launch("image/*") }) {
                    Text(if (current == null) "Choose logo" else "Change logo")
                }
                if (current != null) {
                    TextButton(onClick = { logo = null; logoChanged = true }) {
                        Text("Remove logo")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (error.isEmpty()) " " else error,
            fontSize = 14.sp,
            color = ErrorRed
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (name.trim().isEmpty()) {
                    error = "Please enter your shop name."
                } else {
                    state.saveShop(
                        name.trim(),
                        address.trim(),
                        phone.trim(),
                        logoChanged,
                        logo
                    )
                    onSaved()
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ButtonBlue, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) { Text(saveLabel, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}

// ---------------------------------------------------------------------------
// Secret code editor: one letter per digit, a letter cannot be used twice.
// ---------------------------------------------------------------------------
@Composable
fun CodeEditorScreen(current: String, onSave: (String) -> Unit) {
    val letters = remember { mutableStateListOf<String>().apply { current.forEach { add(it.toString()) } } }

    val counts = letters.filter { it.isNotEmpty() }.groupingBy { it }.eachCount()
    val duplicates = counts.filter { it.value > 1 }.keys
    val allFilled = letters.all { it.isNotEmpty() }
    val canSave = allFilled && duplicates.isEmpty()

    @Composable
    fun Cell(index: Int, modifier: Modifier) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$index  =",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(52.dp)
            )
            OutlinedTextField(
                value = letters[index],
                onValueChange = { raw ->
                    val clean = raw.filter { it in 'A'..'Z' || it in 'a'..'z' }
                        .uppercase(Locale.ROOT)
                        .takeLast(1)
                    letters[index] = clean
                },
                singleLine = true,
                isError = letters[index].isNotEmpty() && letters[index] in duplicates,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.width(84.dp)
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Choose one letter for each digit. The same letter cannot be used for two digits.",
            fontSize = 14.sp,
            color = Color.DarkGray
        )

        Spacer(modifier = Modifier.height(16.dp))

        for (row in 0..4) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Cell(row, Modifier.weight(1f))
                Cell(row + 5, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        val message = when {
            duplicates.isNotEmpty() ->
                "Repeated letter: " + duplicates.joinToString(", ") + ". Use a different letter."
            !allFilled -> "Every digit needs a letter."
            else -> " "
        }
        Text(text = message, fontSize = 14.sp, color = ErrorRed)

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Note: products already labelled with the old code will not decode correctly after you change it.",
            fontSize = 12.sp,
            color = Color.DarkGray
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onSave(letters.joinToString("")) },
            enabled = canSave,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ButtonBlue, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) { Text("SAVE CODE", fontSize = 16.sp, fontWeight = FontWeight.Bold) }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                for (i in 0..9) letters[i] = DEFAULT_MAP[i].toString()
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) { Text("Reset to default code") }
    }
}
