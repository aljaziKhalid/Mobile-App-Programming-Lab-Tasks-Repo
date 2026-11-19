package com.example.lab_8_10_12

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.lab_8_10_12.ui.theme.Lab_8_10_12Theme
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ===================== The data (Room, Lecture 10) =====================

// (was StudyTask.kt) - now a table
@Entity(tableName = "study_tasks")
data class StudyTask(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val course: String,
    @ColumnInfo(name = "is_done")
    val isDone: Boolean = false
)

// (was TodayUiState.kt) - unchanged
data class TodayUiState(
    val isLoading: Boolean = true,
    val tasks: List<StudyTask> = emptyList(),
    val message: String? = null
)

// (was StudyTaskDao.kt)
@Dao
interface StudyTaskDao {
    @Query("SELECT * FROM study_tasks ORDER BY id")
    fun getAllTasks(): Flow<List<StudyTask>>

    @Insert
    suspend fun insert(task: StudyTask)

    @Update
    suspend fun update(task: StudyTask)

    @Delete
    suspend fun delete(task: StudyTask)

    // Lecture 12: read once (not watched) for the worker
    @Query("SELECT COUNT(*) FROM study_tasks WHERE is_done = 0")
    suspend fun countUnfinished(): Int
}

// (was StudyDatabase.kt)
@Database(
    entities = [StudyTask::class],
    version = 1,
    exportSchema = false
)
abstract class StudyDatabase : RoomDatabase() {
    abstract fun studyTaskDao(): StudyTaskDao

    companion object {
        @Volatile
        private var INSTANCE: StudyDatabase? = null

        fun getDatabase(context: Context): StudyDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    StudyDatabase::class.java,
                    "study_database"
                ).build().also { INSTANCE = it }
            }
    }
}

// (was TaskRepository.kt) - replaces FakeTaskRepository
class TaskRepository(private val dao: StudyTaskDao) {
    val allTasks: Flow<List<StudyTask>> = dao.getAllTasks()

    suspend fun add(title: String, course: String) {
        dao.insert(StudyTask(title = title, course = course))
    }

    suspend fun setDone(task: StudyTask, done: Boolean) {
        dao.update(task.copy(isDone = done))
    }

    suspend fun delete(task: StudyTask) {
        dao.delete(task)
    }
}

// ===================== Lecture 12: Focus banner =====================

// (was AirplaneMode.kt)
class AirplaneModeReceiver(
    private val onChange: (Boolean) -> Unit
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val isOn = intent.getBooleanExtra("state", false)
        onChange(isOn)
    }
}

// Reads the setting once, for the first value
fun isAirplaneModeOn(context: Context): Boolean =
    Settings.Global.getInt(
        context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0
    ) != 0

// Register while on screen, unregister when it leaves
@Composable
fun rememberAirplaneModeOn(): Boolean {
    val context = LocalContext.current
    var isOn by remember {
        mutableStateOf(isAirplaneModeOn(context))
    }
    DisposableEffect(context) {
        val receiver = AirplaneModeReceiver { newValue ->
            isOn = newValue
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED // sent by the system
        )
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
    return isOn
}

// ===================== Lecture 12: Notifications =====================

// (was NotificationHelper.kt)
const val REMINDER_CHANNEL_ID = "study_reminders"

fun createReminderChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "Study reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = "A daily reminder of unfinished tasks"
        val manager =
            context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}

fun showReminder(context: Context, unfinished: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return // the user said no: show nothing
    }
    val notification = NotificationCompat
        .Builder(context, REMINDER_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("Study Planner")
        .setContentText("You have $unfinished unfinished tasks.")
        .build()
    NotificationManagerCompat.from(context).notify(1, notification)
}

// (was NotificationPermission.kt)
@Composable
fun AskNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Nothing to do: showReminder() checks it again
    }
    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

// ===================== Lecture 12: WorkManager =====================

// (was ReminderWorker.kt) WHAT to do
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val database = StudyDatabase.getDatabase(applicationContext)
        val unfinished = database.studyTaskDao().countUnfinished()
        if (unfinished > 0) {
            showReminder(applicationContext, unfinished)
        }
        return Result.success()
    }
}

// WHEN to do it: about once a day, when the battery is not low
fun scheduleDailyReminder(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .build()
    val request =
        PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "daily_reminder",
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
}

// ===================== The ViewModel =====================

// (was TodayViewModel.kt)
class TodayViewModel(application: Application) :
    AndroidViewModel(application) {

    private val repository = TaskRepository(
        StudyDatabase.getDatabase(application).studyTaskDao()
    )

    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.allTasks.collect { tasks ->
                _uiState.update {
                    it.copy(isLoading = false, tasks = tasks)
                }
            }
        }
    }

    fun onAddTask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.add(title.trim(), "Mobile Apps")
            _uiState.update { it.copy(message = "Task added") }
        }
    }

    fun onTaskCheckedChange(task: StudyTask, done: Boolean) {
        viewModelScope.launch {
            repository.setDone(task, done)
            if (done) _uiState.update { it.copy(message = "Marked as done") }
        }
    }

    // Lecture 12: run the worker once, 10 seconds from now (for testing)
    fun onRemindMe() {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(getApplication<Application>()).enqueue(request)
        _uiState.update {
            it.copy(message = "Reminder in 10 seconds. Close the app!")
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }
}

// ===================== The screen =====================

// (was TodayScreen.kt)
@Composable
fun TodayRoute(viewModel: TodayViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isFocusMode = rememberAirplaneModeOn()
    AskNotificationPermission()
    TodayScreen(
        uiState = uiState,
        isFocusMode = isFocusMode,
        onAddTask = { title -> viewModel.onAddTask(title) },
        onTaskCheckedChange = { task, done ->
            viewModel.onTaskCheckedChange(task, done)
        },
        onMessageShown = { viewModel.onMessageShown() },
        onRemindMe = { viewModel.onRemindMe() }
    )
}

@Composable
fun TaskRow(task: StudyTask, onCheckedChange: (StudyTask, Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = task.isDone,
            onCheckedChange = { done -> onCheckedChange(task, done) }
        )
        Column {
            Text(task.title)
            Text(task.course, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun TodayScreen(
    uiState: TodayUiState,
    isFocusMode: Boolean,
    onAddTask: (String) -> Unit,
    onTaskCheckedChange: (StudyTask, Boolean) -> Unit,
    onMessageShown: () -> Unit,
    onRemindMe: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.message) {
        val message = uiState.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onMessageShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Today", Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = onRemindMe) { Text("Remind me") }
                TextButton(onClick = {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            "Tick a task when you finish it"
                        )
                    }
                }) { Text("Help") }
            }
            if (isFocusMode) {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "Focus mode: airplane mode is on. No distractions!",
                        Modifier.padding(12.dp)
                    )
                }
            }
            AddTaskRow(onAddTask)
            when {
                uiState.isLoading -> CircularProgressIndicator()
                uiState.tasks.isEmpty() ->
                    Text("No tasks yet. Add your first one above.")
                else -> uiState.tasks.forEach { task ->
                    TaskRow(task, onTaskCheckedChange)
                }
            }
        }
    }
}

@Composable
fun AddTaskRow(onAddTask: (String) -> Unit) {
    var newTitle by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = newTitle,
            onValueChange = { newTitle = it },
            label = { Text("New task") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Button(onClick = {
            onAddTask(newTitle)
            newTitle = ""
        }) { Text("Add") }
    }
}

// ===================== Previews =====================

private val previewTasks = listOf(
    StudyTask(1, "Read Chapter 11", "Mobile Apps"),
    StudyTask(2, "Lab 8 report", "Mobile Apps", isDone = true),
    StudyTask(3, "Revise SQL joins", "Databases")
)

@Preview(showBackground = true, name = "Normal")
@Composable
fun TodayScreenPreview() {
    Lab_8_10_12Theme {
        TodayScreen(
            uiState = TodayUiState(isLoading = false, tasks = previewTasks),
            isFocusMode = false,
            onAddTask = {},
            onTaskCheckedChange = { _, _ -> },
            onMessageShown = {},
            onRemindMe = {}
        )
    }
}

@Preview(showBackground = true, name = "Focus mode (airplane on)")
@Composable
fun TodayScreenFocusPreview() {
    Lab_8_10_12Theme {
        TodayScreen(
            uiState = TodayUiState(isLoading = false, tasks = previewTasks),
            isFocusMode = true,
            onAddTask = {},
            onTaskCheckedChange = { _, _ -> },
            onMessageShown = {},
            onRemindMe = {}
        )
    }
}

@Preview(showBackground = true, name = "Empty list")
@Composable
fun TodayScreenEmptyPreview() {
    Lab_8_10_12Theme {
        TodayScreen(
            uiState = TodayUiState(isLoading = false, tasks = emptyList()),
            isFocusMode = false,
            onAddTask = {},
            onTaskCheckedChange = { _, _ -> },
            onMessageShown = {},
            onRemindMe = {}
        )
    }
}

// ===================== The Activity =====================

// (was MainActivity.kt)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createReminderChannel(this)
        scheduleDailyReminder(this)
        enableEdgeToEdge()
        setContent {
            Lab_8_10_12Theme {
                TodayRoute()
            }
        }
    }
}