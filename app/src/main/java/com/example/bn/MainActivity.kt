package com.example.bn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigInteger
import java.util.Locale

private val CODE_MAP = mapOf(
    'A' to '0',
    'E' to '1',
    'I' to '2',
    'O' to '3',
    'U' to '4',
    'S' to '5',
    'B' to '6',
    'L' to '7',
    'W' to '8',
    'P' to '9'
)

private const val MAX_LENGTH = 20

/** Returns Pair(price, errorMessage). One of them is always empty. */
private fun decodePrice(raw: String): Pair<String, String> {
    val code = raw.trim().uppercase(Locale.ROOT)

    if (code.isEmpty()) {
        return "" to "Please enter your secret code."
    }

    val digits = StringBuilder()
    for (letter in code) {
        val digit = CODE_MAP[letter]
        if (digit == null) {
            return "" to "Invalid code character: $letter"
        }
        digits.append(digit)
    }

    // BigInteger removes leading zeros automatically.
    val number = BigInteger(digits.toString())
    return String.format(Locale.US, "%,d", number) to ""
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BNScreen()
            }
        }
    }
}

@Composable
fun BNScreen() {
    var code by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    val blue = Color(0xFF144B82)
    val green = Color(0xFF146E41)

    fun decode() {
        val (p, e) = decodePrice(code)
        price = p
        error = e
    }

    fun clearAll() {
        code = ""
        price = ""
        error = ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "BN",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = blue
            )
            Text(
                text = "BURHAN NEXUS  |  PRICE DECODER",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Enter your secret code:",
                fontSize = 16.sp,
                color = Color.Black,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = code,
                onValueChange = { new ->
                    code = new.uppercase(Locale.ROOT).take(MAX_LENGTH)
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
                color = Color.Black,
                modifier = Modifier.fillMaxWidth()
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
                    color = green,
                    textAlign = TextAlign.End
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (error.isEmpty()) " " else error,
                fontSize = 14.sp,
                color = Color(0xFFC0392B),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { decode() },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1964AF),
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
}
