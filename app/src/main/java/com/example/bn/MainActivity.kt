package com.example.bn

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.util.Locale

// ---------------------------------------------------------------------------
// Fixed developer credit. This is hard-coded and has no edit/delete option.
// ---------------------------------------------------------------------------
private const val DEV_NAME = "Developed by Burhan Nexus"
private const val DEV_PHONE = "03475093446"

// Digit 0..9 -> letter (index = digit). Default code.
private const val DEFAULT_MAP = "AEIOUSBLWP"
private const val MAX_CODE_LENGTH = 20

private val Blue = Color(0xFF144B82)
private val ButtonBlue = Color(0xFF1964AF)
private val Green = Color(0xFF146E41)
private val ErrorRed = Color(0xFFC0392B)
private val Background = Color(0xFFF5F7FA)

private fun validMapping(s: String?): String {
    if (s == null) return DEFAULT_MAP
    if (s.length != 10) return DEFAULT_MAP
    if (!s.all { it in 'A'..'Z' }) return DEFAULT_MAP
    if (s.toSet().size != 10) return DEFAULT_MAP
    return s
}

/** Returns Pair(price, errorMessage). One of them is always empty. */
private fun decodePrice(raw: String, mapping: String): Pair<String, String> {
    val code = raw.trim().uppercase(Locale.ROOT)
    if (code.isEmpty()) return "" to "Please enter your secret code."

    val digits = StringBuilder()
    for (letter in code) {
        val index = mapping.indexOf(letter)
        if (index < 0) return "" to "Invalid code character: $letter"
        digits.append(('0' + index))
    }
    val number = BigInteger(digits.toString())
    return String.format(Locale.US, "%,d", number) to ""
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

private enum class Screen { Setup, Main, Settings, EditShop, EditCode }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val state = AppState(applicationContext)
        setContent {
            MaterialTheme {
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

    BackHandler(enabled = screen != Screen.Main && screen != Screen.Setup) {
        screen = when (screen) {
            Screen.EditShop, Screen.EditCode -> Screen.Settings
            else -> Screen.Main
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .systemBarsPadding()
            .imePadding()
    ) {
        Box(modifier = Modifier.weight(1f)) {
            when (screen) {
                Screen.Setup -> ShopForm(
                    title = "Welcome! Set up your shop",
                    subtitle = "This is asked only once. You can change it later from Settings.",
                    state = state,
                    saveLabel = "SAVE & START",
                    onBack = null,
                    onSaved = { screen = Screen.Main }
                )

                Screen.Main -> MainScreen(state, onSettings = { screen = Screen.Settings })

                Screen.Settings -> SettingsScreen(
                    onEditShop = { screen = Screen.EditShop },
                    onEditCode = { screen = Screen.EditCode },
                    onBack = { screen = Screen.Main }
                )

                Screen.EditShop -> ShopForm(
                    title = "Shop details",
                    subtitle = "",
                    state = state,
                    saveLabel = "SAVE",
                    onBack = { screen = Screen.Settings },
                    onSaved = { screen = Screen.Settings }
                )

                Screen.EditCode -> CodeEditorScreen(
                    current = state.mapping,
                    onSave = {
                        state.saveMapping(it)
                        screen = Screen.Settings
                    },
                    onBack = { screen = Screen.Settings }
                )
            }
        }
        DeveloperFooter()
    }
}

// ---------------------------------------------------------------------------
// Footer: always visible, cannot be edited or removed from inside the app.
// ---------------------------------------------------------------------------
@Composable
fun DeveloperFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFE3E9F0))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = DEV_NAME,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Blue
        )
        Text(
            text = "Mobile: $DEV_PHONE",
            fontSize = 13.sp,
            color = Color.Black
        )
    }
}

// ---------------------------------------------------------------------------
// Main screen
// ---------------------------------------------------------------------------
@Composable
fun MainScreen(state: AppState, onSettings: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    fun decode() {
        val (p, e) = decodePrice(code, state.mapping)
        price = p
        error = e
    }

    fun clearAll() {
        code = ""
        price = ""
        error = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        // Shop header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            val logo = state.logo
            if (logo != null) {
                Image(
                    bitmap = logo.asImageBitmap(),
                    contentDescription = "Shop logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.shopName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Blue
                )
                if (state.address.isNotEmpty()) {
                    Text(text = state.address, fontSize = 14.sp, color = Color.DarkGray)
                }
                if (state.phone.isNotEmpty()) {
                    Text(text = state.phone, fontSize = 14.sp, color = Color.DarkGray)
                }
            }
            TextButton(onClick = onSettings) {
                Text("Settings", fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "PRICE DECODER",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Enter your secret code:",
            fontSize = 16.sp,
            color = Color.Black
        )
        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { new ->
                code = new.uppercase(Locale.ROOT).take(MAX_CODE_LENGTH)
                error = ""
            },
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 24.sp,
                color = Color.Black
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { decode() }),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "PURCHASE PRICE (Rs.)",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = if (price.isEmpty()) " " else price,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Green,
                textAlign = TextAlign.End
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (error.isEmpty()) " " else error,
            fontSize = 14.sp,
            color = ErrorRed
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { decode() },
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ButtonBlue,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Text("DECODE", fontSize = 16.sp)
            }

            Button(
                onClick = { clearAll() },
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE1E6EC),
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Text("CLEAR / NEW", fontSize = 16.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Settings menu
// ---------------------------------------------------------------------------
@Composable
fun SettingsScreen(onEditShop: () -> Unit, onEditCode: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        TextButton(onClick = onBack) { Text("< Back") }

        Text(
            text = "Settings",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Blue
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onEditCode,
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ButtonBlue, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) { Text("Edit secret code (digits to letters)", fontSize = 16.sp) }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedButton(
            onClick = onEditShop,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) { Text("Edit shop name, address, phone, logo", fontSize = 16.sp) }
    }
}

// ---------------------------------------------------------------------------
// Shop details form (first-time setup, and later editing)
// ---------------------------------------------------------------------------
@Composable
fun ShopForm(
    title: String,
    subtitle: String,
    state: AppState,
    saveLabel: String,
    onBack: (() -> Unit)?,
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
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) { Text("< Back") }
        }

        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Blue
        )
        if (subtitle.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, fontSize = 14.sp, color = Color.DarkGray)
        }

        Spacer(modifier = Modifier.height(20.dp))

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
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ButtonBlue, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) { Text(saveLabel, fontSize = 16.sp) }
    }
}

// ---------------------------------------------------------------------------
// Secret code editor: one letter per digit, a letter cannot be used twice.
// ---------------------------------------------------------------------------
@Composable
fun CodeEditorScreen(current: String, onSave: (String) -> Unit, onBack: () -> Unit) {
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
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        TextButton(onClick = onBack) { Text("< Back") }

        Text(
            text = "Secret code",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Blue
        )
        Spacer(modifier = Modifier.height(4.dp))
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
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ButtonBlue, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) { Text("SAVE CODE", fontSize = 16.sp) }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                for (i in 0..9) letters[i] = DEFAULT_MAP[i].toString()
            },
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) { Text("Reset to default code") }
    }
}
