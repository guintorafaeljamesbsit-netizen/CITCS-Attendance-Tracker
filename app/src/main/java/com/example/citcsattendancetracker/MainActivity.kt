package com.example.citcsattendancetracker

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val BackgroundBlue = Color(0xFFF2F7FC)
private val PrimaryBlue = Color(0xFF2476B9)
private val DarkBlue = Color(0xFF123B5D)
private val LightBlue = Color(0xFFE5F2FC)
private val TextGray = Color(0xFF667788)
private val White = Color.White
private val SuccessGreen = Color(0xFF258B50)
private val SuccessBackground = Color(0xFFE3F6EA)
private val WarningOrange = Color(0xFFB97818)
private val WarningBackground = Color(0xFFFFF1D9)
private val DangerRed = Color(0xFFC43D3D)
private val DangerBackground = Color(0xFFFFE5E5)

private const val PREFS_NAME = "citcs_attendance_preferences"
private const val STUDENT_KEY = "student_number"
private const val PHOTO_KEY_PREFIX = "profile_photo_uri_"
private const val WORK_NAME = "citcs_automatic_absent_checker"


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                CITCSApp()
            }
        }
    }
}


private fun scheduleAttendanceWorker(
    context: android.content.Context
) {

    val workRequest =
        PeriodicWorkRequestBuilder<AttendanceWorker>(
            15,
            TimeUnit.MINUTES
        ).build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest
    )
}


enum class AppPage {
    DASHBOARD,
    SUBJECTS,
    HISTORY,
    PROFILE
}


@Composable
fun CITCSApp() {

    val context = LocalContext.current

    val preferences = remember {
        context.getSharedPreferences(
            PREFS_NAME,
            android.content.Context.MODE_PRIVATE
        )
    }

    val savedStudentNumber = remember {
        preferences.getString(
            STUDENT_KEY,
            ""
        ) ?: ""
    }

    var studentNumber by remember {
        mutableStateOf(
            FirebaseManager.auth.currentUser?.email
                ?.substringBefore("@")
                ?: savedStudentNumber
        )
    }

    var isLoggedIn by remember {
        mutableStateOf(
            FirebaseManager.auth.currentUser != null
        )
    }

    var checkInMessage by remember {
        mutableStateOf("")
    }

    var studentProfile by remember {
        mutableStateOf<StudentProfile?>(null)
    }

    var currentPage by remember {
        mutableStateOf(AppPage.DASHBOARD)
    }

    var pendingSubject by remember {
        mutableStateOf<SubjectInfo?>(null)
    }

    val database = remember {
        AttendanceDatabase.getDatabase(context)
    }

    val dao = remember {
        database.attendanceDao()
    }

    val scope = rememberCoroutineScope()

    val records by dao
        .getRecordsForStudent(studentNumber)
        .collectAsState(initial = emptyList())


    // =========================================================
    // PROOF PHOTO PICKER
    // =========================================================

    val proofPicker =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.OpenDocument()
        ) { uri: android.net.Uri? ->

            val subject = pendingSubject

            if (uri == null || subject == null) {
                pendingSubject = null
                return@rememberLauncherForActivityResult
            }

            try {

                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

            } catch (_: Exception) {
            }

            val proofUri =
                uri.toString()

            val now = Date()

            val todayKey =
                SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.getDefault()
                ).format(now)

            val dateText =
                SimpleDateFormat(
                    "MMM dd, yyyy",
                    Locale.getDefault()
                ).format(now)

            val timeText =
                SimpleDateFormat(
                    "hh:mm a",
                    Locale.getDefault()
                ).format(now)

            val currentTime =
                LocalTime.now()

            val status =
                AttendancePolicy.getStatus(
                    currentTime,
                    subject
                )

            val sessionId =
                "${subject.code}-$todayKey"


            if (
                status != "Present" &&
                status != "Late"
            ) {

                checkInMessage =
                    when (status) {

                        "Not Yet Open" ->
                            "${subject.code}: Attendance is not open yet."

                        "Absent" ->
                            "${subject.code}: Attendance is already closed. You are marked Absent."

                        else ->
                            "${subject.code}: Attendance cannot be submitted."
                    }

                pendingSubject = null

                return@rememberLauncherForActivityResult
            }


            scope.launch {

                try {

                    val result =
                        dao.insertAttendance(
                            AttendanceEntity(
                                studentNumber =
                                    studentNumber,

                                dateKey =
                                    todayKey,

                                date =
                                    dateText,

                                time =
                                    timeText,

                                status =
                                    status,

                                subjectCode =
                                    subject.code,

                                subjectName =
                                    subject.name,

                                sessionId =
                                    sessionId,

                                proofUri =
                                    proofUri
                            )
                        )


                    checkInMessage =
                        if (result != -1L) {

                            "${subject.code}: $status attendance saved successfully with proof!"

                        } else {

                            "${subject.code}: You already have an attendance record today."
                        }

                } catch (e: Exception) {

                    checkInMessage =
                        "Unable to save attendance: ${
                            e.localizedMessage
                                ?: "Please try again."
                        }"
                }

                pendingSubject = null
            }
        }


    // =========================================================
    // ATTENDANCE ACTION
    // =========================================================

    fun handleAttendanceAction(
        subject: SubjectInfo
    ) {

        val calendar =
            Calendar.getInstance()

        val currentTime =
            LocalTime.now()

        val status =
            AttendancePolicy.getStatus(
                currentTime,
                subject
            )

        val todayDay =
            when (
                calendar.get(
                    Calendar.DAY_OF_WEEK
                )
            ) {

                Calendar.MONDAY ->
                    "MON"

                Calendar.TUESDAY ->
                    "TUE"

                Calendar.WEDNESDAY ->
                    "WED"

                Calendar.THURSDAY ->
                    "THU"

                Calendar.FRIDAY ->
                    "FRI"

                Calendar.SATURDAY ->
                    "SAT"

                Calendar.SUNDAY ->
                    "SUN"

                else ->
                    ""
            }


        if (
            todayDay !in subject.days
        ) {

            checkInMessage =
                "This subject is not scheduled today."

            return
        }


        when (status) {

            "Not Yet Open" -> {

                checkInMessage =
                    "${subject.code}: Attendance is not open yet. Scheduled time: ${subject.startTime}."
            }


            "Present",
            "Late" -> {

                pendingSubject = subject

                proofPicker.launch(
                    arrayOf("image/*")
                )
            }


            "Absent" -> {

                val now = Date()

                val todayKey =
                    SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.getDefault()
                    ).format(now)

                val dateText =
                    SimpleDateFormat(
                        "MMM dd, yyyy",
                        Locale.getDefault()
                    ).format(now)

                val timeText =
                    SimpleDateFormat(
                        "hh:mm a",
                        Locale.getDefault()
                    ).format(now)

                val sessionId =
                    "${subject.code}-$todayKey"


                scope.launch {

                    try {

                        val result =
                            dao.insertAttendance(
                                AttendanceEntity(
                                    studentNumber =
                                        studentNumber,

                                    dateKey =
                                        todayKey,

                                    date =
                                        dateText,

                                    time =
                                        timeText,

                                    status =
                                        "Absent",

                                    subjectCode =
                                        subject.code,

                                    subjectName =
                                        subject.name,

                                    sessionId =
                                        sessionId,

                                    proofUri = ""
                                )
                            )


                        checkInMessage =
                            if (result != -1L) {

                                "${subject.code}: You are marked Absent."

                            } else {

                                "${subject.code}: You already have an attendance record today."
                            }

                    } catch (e: Exception) {

                        checkInMessage =
                            "Unable to save attendance: ${
                                e.localizedMessage
                                    ?: "Please try again."
                            }"
                    }
                }
            }
        }
    }


    // =========================================================
    // LOGGED IN APP
    // =========================================================

    if (isLoggedIn) {

        LaunchedEffect(studentNumber) {

            StudentProfileRepository.getProfile {
                    profile,
                    error ->

                studentProfile = profile

                if (error != null) {

                    checkInMessage =
                        "Unable to load profile: $error"
                }
            }
        }


        Scaffold(

            containerColor = BackgroundBlue,

            bottomBar = {

                CITCSBottomNavigation(
                    currentPage = currentPage,
                    onPageSelected = { page ->
                        currentPage = page
                    }
                )
            }

        ) { paddingValues ->

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {

                when (currentPage) {

                    AppPage.DASHBOARD -> {

                        DashboardPage(
                            studentNumber =
                                studentNumber,

                            studentProfile =
                                studentProfile,

                            records =
                                records,

                            checkInMessage =
                                checkInMessage,

                            onCheckIn = { subject ->
                                handleAttendanceAction(
                                    subject
                                )
                            }
                        )
                    }


                    AppPage.SUBJECTS -> {

                        SubjectsPage(
                            records = records,

                            onCheckIn = { subject ->
                                handleAttendanceAction(
                                    subject
                                )
                            }
                        )
                    }


                    AppPage.HISTORY -> {

                        AttendanceHistoryPage(
                            records =
                                records
                        )
                    }


                    AppPage.PROFILE -> {

                        ProfilePage(
                            studentNumber =
                                studentNumber,

                            studentProfile =
                                studentProfile,

                            onLogout = {

                                StudentAuth.logout()

                                preferences.edit()
                                    .remove(
                                        STUDENT_KEY
                                    )
                                    .apply()

                                isLoggedIn = false

                                studentNumber = ""

                                studentProfile = null

                                checkInMessage = ""

                                currentPage =
                                    AppPage.DASHBOARD
                            }
                        )
                    }
                }
            }
        }

    } else {

        AttendanceLoginScreen(

            onLogin = {
                    number,
                    password,
                    onResult ->

                StudentAuth.login(
                    number,
                    password,
                    onResult
                )
            },

            onRegister = {
                    number,
                    password,
                    profile,
                    onResult ->

                StudentAuth.register(
                    number,
                    password
                ) { success, resultMessage ->

                    if (success) {

                        StudentProfileRepository
                            .saveProfile(
                                profile
                            ) { saved, saveMessage ->

                                onResult(
                                    saved,
                                    if (saved)
                                        resultMessage
                                    else
                                        saveMessage
                                )
                            }

                    } else {

                        onResult(
                            false,
                            resultMessage
                        )
                    }
                }
            },

            onAuthSuccess = { number ->

                studentNumber = number

                preferences.edit()
                    .putString(
                        STUDENT_KEY,
                        number
                    )
                    .apply()

                scheduleAttendanceWorker(
                    context
                )

                checkInMessage = ""

                isLoggedIn = true
            }
        )
    }
}


// =============================================================
// BOTTOM NAVIGATION
// =============================================================

@Composable
fun CITCSBottomNavigation(
    currentPage: AppPage,
    onPageSelected: (AppPage) -> Unit
) {

    NavigationBar(
        containerColor = White,
        tonalElevation = 8.dp
    ) {

        NavigationBarItem(
            selected =
                currentPage == AppPage.DASHBOARD,

            onClick = {
                onPageSelected(
                    AppPage.DASHBOARD
                )
            },

            icon = {
                Text(
                    "⌂",
                    fontSize = 24.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            },

            label = {
                Text("Home")
            },

            colors =
                NavigationBarItemDefaults.colors(
                    selectedIconColor =
                        PrimaryBlue,

                    selectedTextColor =
                        PrimaryBlue,

                    indicatorColor =
                        LightBlue
                )
        )


        NavigationBarItem(
            selected =
                currentPage == AppPage.SUBJECTS,

            onClick = {
                onPageSelected(
                    AppPage.SUBJECTS
                )
            },

            icon = {
                Text(
                    "▣",
                    fontSize = 22.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            },

            label = {
                Text("Subjects")
            },

            colors =
                NavigationBarItemDefaults.colors(
                    selectedIconColor =
                        PrimaryBlue,

                    selectedTextColor =
                        PrimaryBlue,

                    indicatorColor =
                        LightBlue
                )
        )


        NavigationBarItem(
            selected =
                currentPage == AppPage.HISTORY,

            onClick = {
                onPageSelected(
                    AppPage.HISTORY
                )
            },

            icon = {
                Text(
                    "☷",
                    fontSize = 24.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            },

            label = {
                Text("History")
            },

            colors =
                NavigationBarItemDefaults.colors(
                    selectedIconColor =
                        PrimaryBlue,

                    selectedTextColor =
                        PrimaryBlue,

                    indicatorColor =
                        LightBlue
                )
        )


        NavigationBarItem(
            selected =
                currentPage == AppPage.PROFILE,

            onClick = {
                onPageSelected(
                    AppPage.PROFILE
                )
            },

            icon = {
                Text(
                    "●",
                    fontSize = 20.sp
                )
            },

            label = {
                Text("Profile")
            },

            colors =
                NavigationBarItemDefaults.colors(
                    selectedIconColor =
                        PrimaryBlue,

                    selectedTextColor =
                        PrimaryBlue,

                    indicatorColor =
                        LightBlue
                )
        )
    }
}


// =============================================================
// PAGE HEADER
// =============================================================

@Composable
fun PageHeader(
    title: String,
    subtitle: String
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = 22.dp,
                bottom = 18.dp
            )
    ) {

        Text(
            text = "CITCS STUDENT PORTAL",
            color = PrimaryBlue,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(
            modifier =
                Modifier.height(3.dp)
        )

        Text(
            text = title,
            color = DarkBlue,
            fontSize = 29.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(
            modifier =
                Modifier.height(4.dp)
        )

        Text(
            text = subtitle,
            color = TextGray,
            fontSize = 13.sp
        )
    }
}


// =============================================================
// DASHBOARD
// =============================================================

@Composable
fun DashboardPage(
    studentNumber: String,
    studentProfile: StudentProfile?,
    records: List<AttendanceEntity>,
    checkInMessage: String,
    onCheckIn: (SubjectInfo) -> Unit
) {

    val today =
        SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.getDefault()
        ).format(Date())

    val todayRecords =
        records.filter {
            it.dateKey == today &&
                    it.subjectCode != "LEGACY"
        }

    val presentCount =
        records.count {
            it.status == "Present"
        }

    val lateCount =
        records.count {
            it.status == "Late"
        }

    val absentCount =
        records.count {
            it.status == "Absent"
        }

    val firstName =
        studentProfile
            ?.fullName
            ?.trim()
            ?.split(" ")
            ?.firstOrNull()
            ?: "Student"


    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(
                horizontal = 18.dp
            )
    ) {

        PageHeader(
            title = "Dashboard",
            subtitle =
                "Your attendance overview"
        )


        Card(
            modifier =
                Modifier.fillMaxWidth(),

            shape =
                RoundedCornerShape(24.dp),

            colors =
                CardDefaults.cardColors(
                    containerColor =
                        PrimaryBlue
                )
        ) {

            Column(
                modifier =
                    Modifier.padding(23.dp)
            ) {

                Text(
                    "WELCOME BACK",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    modifier =
                        Modifier.height(5.dp)
                )

                Text(
                    firstName,
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight =
                        FontWeight.ExtraBold
                )

                Spacer(
                    modifier =
                        Modifier.height(5.dp)
                )

                Text(
                    "Student Number: $studentNumber",
                    color =
                        Color.White.copy(
                            alpha = 0.92f
                        ),
                    fontSize = 13.sp
                )

                Spacer(
                    modifier =
                        Modifier.height(15.dp)
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {

                    DashboardMiniInfo(
                        label = "Course",
                        value =
                            studentProfile?.course
                                ?: "N/A"
                    )

                    DashboardMiniInfo(
                        label = "Section",
                        value =
                            studentProfile?.section
                                ?: "N/A"
                    )
                }
            }
        }


        Spacer(
            modifier =
                Modifier.height(22.dp)
        )


        Text(
            "Attendance Overview",
            color = DarkBlue,
            fontSize = 21.sp,
            fontWeight =
                FontWeight.ExtraBold
        )


        Spacer(
            modifier =
                Modifier.height(10.dp)
        )


        Row(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            SummaryCard(
                title = "Present",
                count = presentCount,
                background =
                    SuccessBackground,
                foreground =
                    SuccessGreen,
                modifier =
                    Modifier.weight(1f)
            )

            SummaryCard(
                title = "Late",
                count = lateCount,
                background =
                    WarningBackground,
                foreground =
                    WarningOrange,
                modifier =
                    Modifier.weight(1f)
            )

            SummaryCard(
                title = "Absent",
                count = absentCount,
                background =
                    DangerBackground,
                foreground =
                    DangerRed,
                modifier =
                    Modifier.weight(1f)
            )
        }


        if (checkInMessage.isNotBlank()) {

            Spacer(
                modifier =
                    Modifier.height(14.dp)
            )

            Card(
                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(16.dp),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            White
                    )
            ) {

                Text(
                    text = checkInMessage,
                    modifier =
                        Modifier.padding(16.dp),
                    color = DarkBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }


        Spacer(
            modifier =
                Modifier.height(24.dp)
        )


        Text(
            "Today's Subjects",
            color = DarkBlue,
            fontSize = 21.sp,
            fontWeight =
                FontWeight.ExtraBold
        )


        Text(
            "Submit your proof during your scheduled attendance window.",
            color = TextGray,
            fontSize = 13.sp
        )


        Spacer(
            modifier =
                Modifier.height(12.dp)
        )


        // =====================================================
        // ONLY SHOW SUBJECTS SCHEDULED FOR TODAY
        // =====================================================

        val todayDay =
            when (
                Calendar.getInstance()
                    .get(Calendar.DAY_OF_WEEK)
            ) {

                Calendar.MONDAY ->
                    "MON"

                Calendar.TUESDAY ->
                    "TUE"

                Calendar.WEDNESDAY ->
                    "WED"

                Calendar.THURSDAY ->
                    "THU"

                Calendar.FRIDAY ->
                    "FRI"

                Calendar.SATURDAY ->
                    "SAT"

                Calendar.SUNDAY ->
                    "SUN"

                else ->
                    ""
            }

        val todaysSubjects =
            SubjectData.subjects.filter { subject ->
                todayDay in subject.days
            }


        todaysSubjects.forEach { subject ->

            val subjectRecord =
                todayRecords.firstOrNull {
                    it.subjectCode ==
                            subject.code
                }

            SubjectCard(
                subject = subject,
                record = subjectRecord,
                onCheckIn = {
                    onCheckIn(subject)
                }
            )

            Spacer(
                modifier =
                    Modifier.height(14.dp)
            )
        }


        Spacer(
            modifier =
                Modifier.height(20.dp)
        )
    }
}


// =============================================================
// DASHBOARD MINI INFO
// =============================================================

@Composable
fun DashboardMiniInfo(
    label: String,
    value: String
) {

    Column {

        Text(
            label,
            color =
                Color.White.copy(
                    alpha = 0.72f
                ),
            fontSize = 11.sp
        )

        Text(
            value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight =
                FontWeight.Bold
        )
    }
}


// =============================================================
// SUBJECTS
// =============================================================

@Composable
fun SubjectsPage(
    records: List<AttendanceEntity>,
    onCheckIn: (SubjectInfo) -> Unit
) {

    val today =
        SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.getDefault()
        ).format(Date())

    val todayRecords =
        records.filter {
            it.dateKey == today &&
                    it.subjectCode != "LEGACY"
        }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(
                horizontal = 18.dp
            )
    ) {

        PageHeader(
            title = "My Subjects",
            subtitle =
                "View your class schedule and attendance"
        )


        // =====================================================
        // SHOW ALL SUBJECTS
        // =====================================================

        SubjectData.subjects.forEach { subject ->

            val subjectRecord =
                todayRecords.firstOrNull {
                    it.subjectCode ==
                            subject.code
                }

            SubjectCard(
                subject = subject,
                record = subjectRecord,
                onCheckIn = {
                    onCheckIn(subject)
                }
            )

            Spacer(
                modifier =
                    Modifier.height(14.dp)
            )
        }


        Spacer(
            modifier =
                Modifier.height(20.dp)
        )
    }
}


// =============================================================
// HISTORY
// =============================================================

@Composable
fun AttendanceHistoryPage(
    records: List<AttendanceEntity>
) {

    val history =
        records.filter {
            it.subjectCode != "LEGACY"
        }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(
                horizontal = 18.dp
            )
    ) {

        PageHeader(
            title = "Attendance History",
            subtitle =
                "Review your previous attendance records"
        )


        if (history.isEmpty()) {

            Card(
                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(22.dp),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            White
                    )
            ) {

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(28.dp),

                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {

                    Text(
                        "No attendance records",
                        color = DarkBlue,
                        fontSize = 19.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(5.dp)
                    )

                    Text(
                        "Your attendance records will appear here.",
                        color = TextGray,
                        fontSize = 13.sp,
                        textAlign =
                            TextAlign.Center
                    )
                }
            }

        } else {

            history.forEach { record ->

                HistoryCard(record)

                Spacer(
                    modifier =
                        Modifier.height(11.dp)
                )
            }
        }


        Spacer(
            modifier =
                Modifier.height(20.dp)
        )
    }
}


@Composable
fun HistoryCard(
    record: AttendanceEntity
) {

    val statusColor =
        when (record.status) {

            "Present" ->
                SuccessGreen

            "Late" ->
                WarningOrange

            else ->
                DangerRed
        }


    val statusBackground =
        when (record.status) {

            "Present" ->
                SuccessBackground

            "Late" ->
                WarningBackground

            else ->
                DangerBackground
        }


    Card(
        modifier =
            Modifier.fillMaxWidth(),

        shape =
            RoundedCornerShape(20.dp),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    White
            )
    ) {

        Column(
            modifier =
                Modifier.padding(18.dp)
        ) {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween,

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        record.subjectCode,
                        color = PrimaryBlue,
                        fontSize = 12.sp,
                        fontWeight =
                            FontWeight.ExtraBold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(3.dp)
                    )

                    Text(
                        record.subjectName,
                        color = DarkBlue,
                        fontSize = 17.sp,
                        fontWeight =
                            FontWeight.Bold
                    )
                }


                Surface(
                    shape =
                        RoundedCornerShape(50.dp),

                    color =
                        statusBackground
                ) {

                    Text(
                        record.status,

                        modifier =
                            Modifier.padding(
                                horizontal = 12.dp,
                                vertical = 7.dp
                            ),

                        color =
                            statusColor,

                        fontSize = 12.sp,

                        fontWeight =
                            FontWeight.Bold
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(13.dp)
            )


            HorizontalDivider()


            Spacer(
                modifier =
                    Modifier.height(11.dp)
            )


            Text(
                "Date: ${record.date}",
                color = TextGray,
                fontSize = 13.sp
            )

            Text(
                "Time: ${record.time}",
                color = TextGray,
                fontSize = 13.sp
            )


            if (
                record.proofUri.isNotBlank()
            ) {

                Spacer(
                    modifier =
                        Modifier.height(6.dp)
                )

                Text(
                    "Proof submitted ✓",
                    color = SuccessGreen,
                    fontSize = 12.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }
        }
    }
}


// =============================================================
// PROFILE
// =============================================================

@Composable
fun ProfilePage(
    studentNumber: String,
    studentProfile: StudentProfile?,
    onLogout: () -> Unit
) {

    val context = LocalContext.current

    val preferences = remember {
        context.getSharedPreferences(
            PREFS_NAME,
            android.content.Context.MODE_PRIVATE
        )
    }

    val photoKey =
        PHOTO_KEY_PREFIX +
                studentNumber

    var photoUri by remember(studentNumber) {
        mutableStateOf(
            preferences.getString(
                photoKey,
                null
            )
        )
    }

    var profileBitmap by remember(studentNumber) {
        mutableStateOf<
                android.graphics.Bitmap?
                >(null)
    }


    val photoPicker =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.OpenDocument()
        ) { uri: android.net.Uri? ->

            if (uri != null) {

                try {

                    context.contentResolver
                        .takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )

                } catch (_: Exception) {
                }

                val uriString =
                    uri.toString()

                preferences.edit()
                    .putString(
                        photoKey,
                        uriString
                    )
                    .apply()

                photoUri =
                    uriString
            }
        }


    LaunchedEffect(photoUri) {

        if (photoUri.isNullOrBlank()) {

            profileBitmap = null

        } else {

            profileBitmap =
                withContext(Dispatchers.IO) {

                    try {

                        val uri =
                            android.net.Uri.parse(
                                photoUri
                            )

                        context.contentResolver
                            .openInputStream(uri)
                            ?.use { inputStream ->

                                BitmapFactory
                                    .decodeStream(
                                        inputStream
                                    )
                            }

                    } catch (_: Exception) {

                        null
                    }
                }
        }
    }


    val courseDisplay =
        when (
            studentProfile?.course?.trim()
        ) {

            "BSIT" ->
                "BSIT – Bachelor of Sleeping and Internet Technology"

            else ->
                studentProfile?.course
                    ?: "Not available"
        }


    val sectionDisplay =
        when (
            studentProfile?.section?.trim()
        ) {

            "3C" ->
                "3C – Certified Crammers"

            else ->
                studentProfile?.section
                    ?: "Not available"
        }


    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    horizontal = 18.dp
                )
    ) {

        PageHeader(
            title = "My Profile",
            subtitle =
                "Your student account information"
        )


        Card(
            modifier =
                Modifier.fillMaxWidth(),

            shape =
                RoundedCornerShape(24.dp),

            colors =
                CardDefaults.cardColors(
                    containerColor =
                        PrimaryBlue
                )
        ) {

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(25.dp),

                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                Surface(
                    modifier =
                        Modifier.size(120.dp),

                    shape =
                        RoundedCornerShape(60.dp),

                    color = Color.White
                ) {

                    Box(
                        modifier =
                            Modifier.fillMaxSize(),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        if (profileBitmap != null) {

                            Image(
                                bitmap =
                                    profileBitmap!!
                                        .asImageBitmap(),

                                contentDescription =
                                    "Profile Photo",

                                modifier =
                                    Modifier.fillMaxSize()
                            )

                        } else {

                            Text(
                                text =
                                    studentProfile
                                        ?.fullName
                                        ?.firstOrNull()
                                        ?.uppercase()
                                        ?: "S",

                                color =
                                    PrimaryBlue,

                                fontSize = 42.sp,

                                fontWeight =
                                    FontWeight.ExtraBold
                            )
                        }
                    }
                }


                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )


                OutlinedButton(
                    onClick = {

                        photoPicker.launch(
                            arrayOf("image/*")
                        )
                    },

                    shape =
                        RoundedCornerShape(14.dp),

                    colors =
                        ButtonDefaults
                            .outlinedButtonColors(
                                containerColor =
                                    Color.White,

                                contentColor =
                                    PrimaryBlue
                            )
                ) {

                    Text(
                        if (
                            profileBitmap != null
                        )
                            "CHANGE PHOTO"
                        else
                            "ADD PHOTO",

                        fontWeight =
                            FontWeight.ExtraBold
                    )
                }


                Spacer(
                    modifier =
                        Modifier.height(13.dp)
                )


                Text(
                    studentProfile?.fullName
                        ?: "Student",

                    color = Color.White,

                    fontSize = 24.sp,

                    fontWeight =
                        FontWeight.ExtraBold
                )


                Text(
                    "CITCS Student",

                    color =
                        Color.White.copy(
                            alpha = 0.85f
                        ),

                    fontSize = 13.sp
                )
            }
        }


        Spacer(
            modifier =
                Modifier.height(18.dp)
        )


        ProfileInfo(
            title = "Student Number",
            value = studentNumber
        )


        ProfileInfo(
            title = "Course",
            value = courseDisplay
        )


        ProfileInfo(
            title = "Section",
            value = sectionDisplay
        )


        ProfileInfo(
            title = "Full Name",
            value =
                studentProfile?.fullName
                    ?: "Not available"
        )


        Spacer(
            modifier =
                Modifier.height(14.dp)
        )


        Card(
            modifier =
                Modifier.fillMaxWidth(),

            shape =
                RoundedCornerShape(20.dp),

            colors =
                CardDefaults.cardColors(
                    containerColor =
                        White
                )
        ) {

            Column(
                modifier =
                    Modifier.padding(18.dp)
            ) {

                Text(
                    "Account",
                    color = DarkBlue,
                    fontSize = 17.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    modifier =
                        Modifier.height(5.dp)
                )

                Text(
                    "Your CITCS attendance account is currently active.",
                    color = TextGray,
                    fontSize = 13.sp
                )
            }
        }


        Spacer(
            modifier =
                Modifier.height(20.dp)
        )


        OutlinedButton(
            onClick = onLogout,

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(54.dp),

            shape =
                RoundedCornerShape(16.dp),

            colors =
                ButtonDefaults
                    .outlinedButtonColors(
                        contentColor =
                            DangerRed
                    )
        ) {

            Text(
                "LOG OUT",
                fontWeight =
                    FontWeight.Bold
            )
        }


        Spacer(
            modifier =
                Modifier.height(25.dp)
        )


        Text(
            "CITCS Attendance Tracker",

            modifier =
                Modifier.fillMaxWidth(),

            textAlign =
                TextAlign.Center,

            color = TextGray,

            fontSize = 12.sp
        )


        Text(
            "© 2026 CITCS Student Portal",

            modifier =
                Modifier.fillMaxWidth(),

            textAlign =
                TextAlign.Center,

            color = TextGray,

            fontSize = 11.sp
        )


        Spacer(
            modifier =
                Modifier.height(20.dp)
        )
    }
}


@Composable
fun ProfileInfo(
    title: String,
    value: String
) {

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),

        shape =
            RoundedCornerShape(18.dp),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    White
            )
    ) {

        Column(
            modifier =
                Modifier.padding(17.dp)
        ) {

            Text(
                title.uppercase(),
                color = PrimaryBlue,
                fontSize = 10.sp,
                fontWeight =
                    FontWeight.ExtraBold
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Text(
                value,
                color = DarkBlue,
                fontSize = 15.sp,
                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}


// =============================================================
// LOGIN / REGISTER
// =============================================================

@Composable
fun AttendanceLoginScreen(
    onLogin: (
        String,
        String,
        (Boolean, String) -> Unit
    ) -> Unit,

    onRegister: (
        String,
        String,
        StudentProfile,
        (Boolean, String) -> Unit
    ) -> Unit,

    onAuthSuccess: (String) -> Unit
) {

    var fullName by remember {
        mutableStateOf("")
    }

    var studentNumber by remember {
        mutableStateOf("")
    }

    var course by remember {
        mutableStateOf("")
    }

    var section by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    var confirmPassword by remember {
        mutableStateOf("")
    }

    var isRegistering by remember {
        mutableStateOf(false)
    }

    var registerStep by remember {
        mutableStateOf(1)
    }

    var message by remember {
        mutableStateOf("")
    }

    var isLoading by remember {
        mutableStateOf(false)
    }


    Surface(
        modifier =
            Modifier.fillMaxSize(),

        color =
            BackgroundBlue
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(
                        rememberScrollState()
                    )
                    .padding(20.dp),

            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Spacer(
                modifier =
                    Modifier.height(15.dp)
            )


            Image(
                painter =
                    painterResource(
                        R.drawable.jakroberto_seal
                    ),

                contentDescription =
                    "University Seal",

                modifier =
                    Modifier.size(100.dp)
            )


            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )


            Text(
                "JAK ROBERTO ANTI-SELOS",
                fontSize = 18.sp,
                fontWeight =
                    FontWeight.ExtraBold,
                color = PrimaryBlue
            )


            Text(
                "UNIVERSITY",
                fontSize = 22.sp,
                fontWeight =
                    FontWeight.ExtraBold,
                color = DarkBlue
            )


            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )


            Card(
                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(24.dp),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            PrimaryBlue
                    )
            ) {

                Column(
                    modifier =
                        Modifier.padding(23.dp)
                ) {

                    Text(
                        "CITCS STUDENT PORTAL",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(6.dp)
                    )

                    Text(
                        "Welcome!",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight =
                            FontWeight.ExtraBold
                    )

                    Text(
                        "Your attendance, made simple.",
                        color =
                            Color.White.copy(
                                alpha = 0.9f
                            ),
                        fontSize = 13.sp
                    )
                }
            }


            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )


            Card(
                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(24.dp),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            White
                    )
            ) {

                Column(
                    modifier =
                        Modifier.padding(22.dp)
                ) {

                    Text(
                        if (isRegistering) {

                            if (registerStep == 1)
                                "Student Information"
                            else
                                "Account Security"

                        } else {

                            "Student Sign In"
                        },

                        fontSize = 23.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color = DarkBlue
                    )


                    Spacer(
                        modifier =
                            Modifier.height(15.dp)
                    )


                    if (!isRegistering) {

                        OutlinedTextField(
                            value = studentNumber,

                            onValueChange = {
                                studentNumber = it
                                message = ""
                            },

                            modifier =
                                Modifier.fillMaxWidth(),

                            label = {
                                Text("Student Number")
                            },

                            placeholder = {
                                Text(
                                    "Enter student number"
                                )
                            },

                            singleLine = true,

                            enabled = !isLoading,

                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType =
                                        KeyboardType.Number
                                )
                        )


                        Spacer(
                            modifier =
                                Modifier.height(10.dp)
                        )


                        OutlinedTextField(
                            value = password,

                            onValueChange = {
                                password = it
                                message = ""
                            },

                            modifier =
                                Modifier.fillMaxWidth(),

                            label = {
                                Text("Password")
                            },

                            placeholder = {
                                Text(
                                    "Enter your password"
                                )
                            },

                            singleLine = true,

                            enabled = !isLoading,

                            visualTransformation =
                                PasswordVisualTransformation()
                        )

                    } else if (
                        registerStep == 1
                    ) {

                        Text(
                            "Enter your student details first.",
                            color = TextGray,
                            fontSize = 13.sp
                        )


                        Spacer(
                            modifier =
                                Modifier.height(12.dp)
                        )


                        OutlinedTextField(
                            value = fullName,

                            onValueChange = {
                                fullName = it
                                message = ""
                            },

                            modifier =
                                Modifier.fillMaxWidth(),

                            label = {
                                Text("Full Name")
                            },

                            placeholder = {
                                Text(
                                    "Enter your full name"
                                )
                            },

                            singleLine = true,

                            enabled = !isLoading
                        )


                        Spacer(
                            modifier =
                                Modifier.height(10.dp)
                        )


                        OutlinedTextField(
                            value = studentNumber,

                            onValueChange = {
                                studentNumber = it
                                message = ""
                            },

                            modifier =
                                Modifier.fillMaxWidth(),

                            label = {
                                Text("Student Number")
                            },

                            placeholder = {
                                Text(
                                    "Enter student number"
                                )
                            },

                            singleLine = true,

                            enabled = !isLoading,

                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType =
                                        KeyboardType.Number
                                )
                        )


                        Spacer(
                            modifier =
                                Modifier.height(10.dp)
                        )


                        OutlinedTextField(
                            value = course,

                            onValueChange = {
                                course = it
                                message = ""
                            },

                            modifier =
                                Modifier.fillMaxWidth(),

                            label = {
                                Text("Course")
                            },

                            placeholder = {
                                Text(
                                    "Example: BSIT"
                                )
                            },

                            singleLine = true,

                            enabled = !isLoading
                        )


                        Spacer(
                            modifier =
                                Modifier.height(10.dp)
                        )


                        OutlinedTextField(
                            value = section,

                            onValueChange = {
                                section = it
                                message = ""
                            },

                            modifier =
                                Modifier.fillMaxWidth(),

                            label = {
                                Text("Section")
                            },

                            placeholder = {
                                Text(
                                    "Example: 3C"
                                )
                            },

                            singleLine = true,

                            enabled = !isLoading
                        )

                    } else {

                        Text(
                            "Create a secure password for your account.",
                            color = TextGray,
                            fontSize = 13.sp
                        )


                        Spacer(
                            modifier =
                                Modifier.height(12.dp)
                        )


                        OutlinedTextField(
                            value = password,

                            onValueChange = {
                                password = it
                                message = ""
                            },

                            modifier =
                                Modifier.fillMaxWidth(),

                            label = {
                                Text("Password")
                            },

                            placeholder = {
                                Text(
                                    "Enter your password"
                                )
                            },

                            singleLine = true,

                            enabled = !isLoading,

                            visualTransformation =
                                PasswordVisualTransformation()
                        )


                        Spacer(
                            modifier =
                                Modifier.height(10.dp)
                        )


                        OutlinedTextField(
                            value =
                                confirmPassword,

                            onValueChange = {
                                confirmPassword = it
                                message = ""
                            },

                            modifier =
                                Modifier.fillMaxWidth(),

                            label = {
                                Text(
                                    "Confirm Password"
                                )
                            },

                            placeholder = {
                                Text(
                                    "Re-enter your password"
                                )
                            },

                            singleLine = true,

                            enabled = !isLoading,

                            visualTransformation =
                                PasswordVisualTransformation()
                        )
                    }


                    if (message.isNotBlank()) {

                        Spacer(
                            modifier =
                                Modifier.height(10.dp)
                        )


                        Text(
                            text = message,

                            color =
                                if (
                                    message.contains(
                                        "successful",
                                        ignoreCase = true
                                    )
                                )
                                    SuccessGreen
                                else
                                    DangerRed,

                            fontSize = 13.sp,

                            fontWeight =
                                FontWeight.Medium
                        )
                    }


                    Spacer(
                        modifier =
                            Modifier.height(18.dp)
                    )


                    if (
                        isRegistering &&
                        registerStep == 2
                    ) {

                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),

                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    10.dp
                                )
                        ) {

                            OutlinedButton(
                                onClick = {
                                    registerStep = 1
                                    message = ""
                                },

                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(53.dp),

                                enabled = !isLoading
                            ) {

                                Text(
                                    "BACK",
                                    fontWeight =
                                        FontWeight.Bold
                                )
                            }


                            Button(
                                onClick = {

                                    when {

                                        password.isBlank() -> {

                                            message =
                                                "Please enter your password."
                                        }

                                        password.length < 6 -> {

                                            message =
                                                "Password must be at least 6 characters."
                                        }

                                        password !=
                                                confirmPassword -> {

                                            message =
                                                "Passwords do not match."
                                        }

                                        else -> {

                                            isLoading = true
                                            message = ""

                                            val number =
                                                studentNumber.trim()

                                            val callback:
                                                        (
                                                Boolean,
                                                String
                                            ) -> Unit =
                                                {
                                                        success,
                                                        resultMessage ->

                                                    isLoading =
                                                        false

                                                    message =
                                                        resultMessage

                                                    if (
                                                        success
                                                    ) {

                                                        onAuthSuccess(
                                                            number
                                                        )
                                                    }
                                                }


                                            val profile =
                                                StudentProfile(
                                                    fullName =
                                                        fullName.trim(),

                                                    studentNumber =
                                                        number,

                                                    course =
                                                        course.trim(),

                                                    section =
                                                        section.trim()
                                                )


                                            onRegister(
                                                number,
                                                password,
                                                profile,
                                                callback
                                            )
                                        }
                                    }
                                },

                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(53.dp),

                                enabled =
                                    !isLoading
                            ) {

                                if (isLoading) {

                                    CircularProgressIndicator(
                                        modifier =
                                            Modifier.size(
                                                21.dp
                                            ),

                                        color =
                                            Color.White,

                                        strokeWidth =
                                            2.dp
                                    )

                                } else {

                                    Text(
                                        "CREATE ACCOUNT",
                                        fontSize = 13.sp,
                                        fontWeight =
                                            FontWeight.Bold
                                    )
                                }
                            }
                        }

                    } else {

                        Button(
                            onClick = {

                                if (!isRegistering) {

                                    when {

                                        studentNumber.isBlank() ||
                                                !studentNumber.all {
                                                    it.isDigit()
                                                } -> {

                                            message =
                                                "Please enter a valid student number."
                                        }

                                        password.isBlank() -> {

                                            message =
                                                "Please enter your password."
                                        }

                                        else -> {

                                            isLoading = true
                                            message = ""

                                            val number =
                                                studentNumber.trim()

                                            val callback:
                                                        (
                                                Boolean,
                                                String
                                            ) -> Unit =
                                                {
                                                        success,
                                                        resultMessage ->

                                                    isLoading =
                                                        false

                                                    message =
                                                        resultMessage

                                                    if (
                                                        success
                                                    ) {

                                                        onAuthSuccess(
                                                            number
                                                        )
                                                    }
                                                }


                                            onLogin(
                                                number,
                                                password,
                                                callback
                                            )
                                        }
                                    }

                                } else {

                                    when {

                                        fullName.isBlank() ||
                                                course.isBlank() ||
                                                section.isBlank() -> {

                                            message =
                                                "Please complete your student profile."
                                        }

                                        studentNumber.isBlank() ||
                                                !studentNumber.all {
                                                    it.isDigit()
                                                } -> {

                                            message =
                                                "Please enter a valid student number."
                                        }

                                        else -> {

                                            message = ""
                                            registerStep = 2
                                        }
                                    }
                                }
                            },

                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(55.dp),

                            enabled = !isLoading
                        ) {

                            if (isLoading) {

                                CircularProgressIndicator(
                                    modifier =
                                        Modifier.size(
                                            22.dp
                                        ),

                                    color =
                                        Color.White,

                                    strokeWidth =
                                        2.dp
                                )

                            } else {

                                Text(
                                    if (isRegistering)
                                        "NEXT"
                                    else
                                        "LOGIN",

                                    fontSize = 17.sp,

                                    fontWeight =
                                        FontWeight.Bold
                                )
                            }
                        }
                    }


                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )


                    TextButton(
                        onClick = {

                            isRegistering =
                                !isRegistering

                            registerStep = 1
                            message = ""

                            password = ""
                            confirmPassword = ""

                            fullName = ""
                            studentNumber = ""
                            course = ""
                            section = ""
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        enabled = !isLoading
                    ) {

                        Text(
                            if (isRegistering) {
                                "Already have an account? Login"
                            } else {
                                "New student? Create an account"
                            },

                            color = PrimaryBlue
                        )
                    }
                }
            }


            Spacer(
                modifier =
                    Modifier.height(22.dp)
            )


            Text(
                "© 2026 CITCS Attendance Tracker",
                color = TextGray,
                fontSize = 11.sp
            )


            Spacer(
                modifier =
                    Modifier.height(15.dp)
            )
        }
    }
}


// =============================================================
// SUBJECT CARD
// =============================================================

@Composable
fun SubjectCard(
    subject: SubjectInfo,
    record: AttendanceEntity?,
    onCheckIn: () -> Unit
) {

    val days =
        subject.days.joinToString(" / ")


    val statusColor =
        when (record?.status) {

            "Present" ->
                SuccessGreen

            "Late" ->
                WarningOrange

            "Absent" ->
                DangerRed

            else ->
                TextGray
        }


    val statusBackground =
        when (record?.status) {

            "Present" ->
                SuccessBackground

            "Late" ->
                WarningBackground

            "Absent" ->
                DangerBackground

            else ->
                LightBlue
        }


    Card(
        modifier =
            Modifier.fillMaxWidth(),

        shape =
            RoundedCornerShape(21.dp),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    White
            )
    ) {

        Column(
            modifier =
                Modifier.padding(18.dp)
        ) {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween,

                verticalAlignment =
                    Alignment.Top
            ) {

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        subject.code,
                        color = PrimaryBlue,
                        fontSize = 12.sp,
                        fontWeight =
                            FontWeight.ExtraBold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )

                    Text(
                        subject.name,
                        color = DarkBlue,
                        fontSize = 18.sp,
                        fontWeight =
                            FontWeight.ExtraBold
                    )
                }


                if (record != null) {

                    Surface(
                        shape =
                            RoundedCornerShape(50.dp),

                        color =
                            statusBackground
                    ) {

                        Text(
                            record.status,

                            modifier =
                                Modifier.padding(
                                    horizontal = 10.dp,
                                    vertical = 6.dp
                                ),

                            color =
                                statusColor,

                            fontSize = 11.sp,

                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
            }


            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )


            HorizontalDivider()


            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )


            Text(
                "Professor: ${subject.professor}",
                color = TextGray,
                fontSize = 12.sp
            )

            Text(
                "Schedule: $days",
                color = TextGray,
                fontSize = 12.sp
            )

            Text(
                "Time: ${subject.startTime} - ${subject.endTime}",
                color = TextGray,
                fontSize = 12.sp
            )


            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )


            if (record != null) {

                Surface(
                    modifier =
                        Modifier.fillMaxWidth(),

                    shape =
                        RoundedCornerShape(13.dp),

                    color =
                        statusBackground
                ) {

                    Column(
                        modifier =
                            Modifier.padding(12.dp)
                    ) {

                        Text(
                            "Today's Attendance",
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            "${record.status} • ${record.time}",
                            color = statusColor,
                            fontSize = 14.sp,
                            fontWeight =
                                FontWeight.ExtraBold
                        )

                        if (
                            record.proofUri.isNotBlank()
                        ) {

                            Spacer(
                                modifier =
                                    Modifier.height(3.dp)
                            )

                            Text(
                                "Proof submitted ✓",
                                color = SuccessGreen,
                                fontSize = 11.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }
                    }
                }

            } else {

                Text(
                    "Submit proof during your attendance window.",
                    color = TextGray,
                    fontSize = 12.sp
                )


                Spacer(
                    modifier =
                        Modifier.height(9.dp)
                )


                Button(
                    onClick = onCheckIn,

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),

                    shape =
                        RoundedCornerShape(13.dp)
                ) {

                    Text(
                        "SUBMIT PROOF",
                        fontWeight =
                            FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}


// =============================================================
// SUMMARY CARD
// =============================================================

@Composable
fun SummaryCard(
    title: String,
    count: Int,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier
) {

    Card(
        modifier =
            modifier.height(100.dp),

        shape =
            RoundedCornerShape(18.dp),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    background
            )
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(8.dp),

            horizontalAlignment =
                Alignment.CenterHorizontally,

            verticalArrangement =
                Arrangement.Center
        ) {

            Text(
                count.toString(),
                fontSize = 27.sp,
                fontWeight =
                    FontWeight.ExtraBold,
                color = foreground
            )

            Text(
                title,
                fontSize = 11.sp,
                fontWeight =
                    FontWeight.Bold,
                color = foreground
            )
        }
    }
}