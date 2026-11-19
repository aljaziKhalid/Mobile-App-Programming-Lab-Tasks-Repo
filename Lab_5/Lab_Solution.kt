package sa.edu.iau.cshj.lab05

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

// =====================================================================
// Activity (the only one in the manifest)
// =====================================================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CourseApp()
            }
        }
    }
}

// =====================================================================
// Task 2.5 - Routes (what each screen needs in order to open)
// =====================================================================
@Serializable
object Home // needs nothing to open

@Serializable
object CourseList // needs nothing to open

@Serializable
data class CourseDetail(val code: String) // needs a course code

// =====================================================================
// Task 3.1 - NavHost: the map of screens. Only this function knows
// that a NavController exists.
// =====================================================================
@Composable
fun CourseApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            HomeRoute(onCoursesClick = { navController.navigate(CourseList) })
        }
        composable<CourseList> {
            CourseListRoute(
                onCourseClick = { code ->
                    // TODO 1
                    navController.navigate(CourseDetail(code))
                }
            )
        }
        composable<CourseDetail> { backStackEntry ->
            // TODO 2
            val detail: CourseDetail = backStackEntry.toRoute()
            CourseDetailRoute(
                code = detail.code, // TODO 3
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

// =====================================================================
// Starter screens (Appendix C) - stateless: state in, events out
// =====================================================================

// ---------- Home ----------
@Composable
fun HomeScreen(
    onWebsiteClick: () -> Unit,
    onCallClick: () -> Unit,
    onCoursesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("CSC Courses", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onWebsiteClick) { Text("University website") }
        Button(onClick = onCallClick) { Text("Call the department") }
        Button(onClick = onCoursesClick) { Text("Browse courses") }
    }
}

// Task 1.2 - Route: decides what each tap means (builds the Intents)
@Composable
fun HomeRoute(onCoursesClick: () -> Unit) {
    val context = LocalContext.current
    HomeScreen(
        onWebsiteClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.iau.edu.sa"))
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "No app can open this", Toast.LENGTH_SHORT).show()
            }
        },
        onCallClick = {
            // TODO 1: open the dialer, started safely like the website
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:0138000000"))
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "No app can open this", Toast.LENGTH_SHORT).show()
            }
        },
        onCoursesClick = onCoursesClick
    )
}

// ---------- Course list ----------
data class CourseListUiState(
    val courses: List<String> = emptyList()
)

@Composable
fun CourseListScreen(
    uiState: CourseListUiState,
    onCourseClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Courses", style = MaterialTheme.typography.headlineMedium)
        uiState.courses.forEach { code ->
            Button(onClick = { onCourseClick(code) }) { Text(code) }
        }
    }
}

@Composable
fun CourseListRoute(onCourseClick: (String) -> Unit) {
    CourseListScreen(
        uiState = CourseListUiState(listOf("CSC 402", "CSC 231", "CSC 311")),
        onCourseClick = onCourseClick
    )
}

// ---------- Course detail ----------
data class CourseDetailUiState(
    val code: String = ""
)

@Composable
fun CourseDetailScreen(
    uiState: CourseDetailUiState,
    onShareClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(uiState.code, style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onShareClick) { Text("Share") }
        OutlinedButton(onClick = onBackClick) { Text("Back") }
    }
}

@Composable
fun CourseDetailRoute(code: String, onBackClick: () -> Unit) {
    val context = LocalContext.current
    CourseDetailScreen(
        uiState = CourseDetailUiState(code = code),
        // Task 4.1 - share sheet (a chooser, so no try/catch is needed)
        onShareClick = {
            val send = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, "I'm taking $code this semester!")
                type = "text/plain"
            }
            context.startActivity(Intent.createChooser(send, "Share via"))
        },
        onBackClick = onBackClick
    )
}

