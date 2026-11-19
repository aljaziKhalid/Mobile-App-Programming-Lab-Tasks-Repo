package sa.edu.iau.cshj.lab04

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// ---------------- Activity (entry point) ----------------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AttendanceRoute()
            }
        }
    }
}

// ---------------- State ----------------
data class AttendanceUiState(
    val sessionTitle: String = "",
    val checkedInCount: Int = 0,
    val isCheckedIn: Boolean = false
)

// ---------------- Stateless screen ----------------
@Composable
fun AttendanceScreen(
    uiState: AttendanceUiState,
    onCheckIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(uiState.sessionTitle)
        Text("Checked in: ${uiState.checkedInCount}")
        Button(
            onClick = onCheckIn,
            enabled = !uiState.isCheckedIn
        ) {
            Text("Check in")
        }
    }
}

// ---------------- Previews ----------------
@Preview(showBackground = true, name = "Not checked in yet")
@Composable
private fun AttendanceScreenPreview() {
    MaterialTheme {
        AttendanceScreen(
            uiState = AttendanceUiState("CSC 402 - Week 4", 12, false),
            onCheckIn = {}
        )
    }
}

@Preview(showBackground = true, name = "Already checked in")
@Composable
private fun AttendanceScreenCheckedInPreview() {
    MaterialTheme {
        AttendanceScreen(
            uiState = AttendanceUiState("CSC 402 - Week 4", 13, true),
            onCheckIn = {}
        )
    }
}

// ---------------- Route (owns the state) ----------------
@Composable
fun AttendanceRoute() {
    var uiState by remember {
        mutableStateOf(AttendanceUiState("CSC 402 - Week 4", 12))
    }

    AttendanceScreen(
        uiState = uiState,
        onCheckIn = {
            uiState = uiState.copy(
                checkedInCount = uiState.checkedInCount + 1,
                isCheckedIn = true
            )
        }
    )
}
