package com.nit.voicelibrarymvp

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.nit.voicelibrarymvp.ScanType
import com.nit.voicelibrarymvp.ui.theme.ArividamTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FactCheck
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CategoryConstants {
    val standardCategories = listOf(
        "Fiction",
        "Novel",
        "Short Stories",
        "Autobiography",
        "Non-Fiction & Essays",
        "Self-Help & Mindset",
        "Children's Literature",
        "Unknown/Miscellaneous"
    )

    fun mapToStandard(input: String): String {
        val lower = input.lowercase()
        return when {
            lower.contains("fiction") || lower.contains("കഥാസാഹിത്യം") -> "Fiction"
            lower.contains("novel") || lower.contains("നോവൽ") -> "Novel"
            lower.contains("story") || lower.contains("കഥ") || lower.contains("stories") || lower.contains("ചെറുകഥകൾ") -> "Short Stories"
            lower.contains("autobiography") || lower.contains("ആത്മകഥ") -> "Autobiography"
            lower.contains("essay") || lower.contains("പ്രബന്ധം") || lower.contains("non") || lower.contains("പ്രബന്ധങ്ങൾ") -> "Non-Fiction & Essays"
            lower.contains("mindset") || lower.contains("self") || lower.contains("ചിന്ത") || lower.contains("ചിന്തകൾ") -> "Self-Help & Mindset"
            lower.contains("child") || lower.contains("കുട്ടി") || lower.contains("ബാലസാഹിത്യം") -> "Children's Literature"
            else -> "Unknown/Miscellaneous"
        }
    }
}

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var roomDb: AppDatabase
    
    // --- UI State ---
    private var userRole by mutableStateOf("USER")
    private var userName by mutableStateOf("Guest")
    private var libraryId by mutableStateOf("")
    private var isDarkMode by mutableStateOf(false)
    private var allBooks by mutableStateOf(listOf<Book>())
    private var filteredBooks by mutableStateOf(listOf<Book>())
    private var groupedBooks by mutableStateOf(listOf<List<Book>>())
    private var statusText by mutableStateOf("Ready to assist you")
    private var currentMode = ""
    private var selectedBook: Book? = null
    private var showAddBookDialog by mutableStateOf(false)
    private var showReviewDialog by mutableStateOf(false)
    private var showEmailConfigDialog by mutableStateOf(false)
    private var showSettingsDialog by mutableStateOf(false)
    private var showBorrowRequestsDialog by mutableStateOf(false)
    private var showUserNotificationsDialog by mutableStateOf(false)
    private var showBorrowersDialog by mutableStateOf(false)
    private var showImportSummary by mutableStateOf(false)
    private var showOcrScanner by mutableStateOf(false)
    private var showVoiceDialog by mutableStateOf(false)
    private var showEditBookDialog by mutableStateOf(false)
    private var showMembersDialog by mutableStateOf(false)
    private var selectedUserForDetail by mutableStateOf<User?>(null)
    private var allLibraryUsers by mutableStateOf(listOf<User>())
    private var showCategoryFilter by mutableStateOf(false)
    private var selectedCategory by mutableStateOf("All")
    private var currentSearchQuery by mutableStateOf("")
    private var showMyBooksOnly by mutableStateOf(false)
    private val registrations = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()
    private var currentScanType by mutableStateOf(ScanType.BOOK_COVER)
    private var preFilledBook by mutableStateOf<Book?>(null)
    private var importResult by mutableStateOf<ExcelHelper.ImportResult?>(null)
    private var borrowRequests by mutableStateOf(listOf<BorrowRequest>())
    private var activeBorrows by mutableStateOf(listOf<BorrowRequest>())
    private var allBorrowHistory by mutableStateOf(listOf<BorrowRequest>())
    private var notifications by mutableStateOf(listOf<BorrowRequest>())
    private var currentBookBorrowers by mutableStateOf(listOf<BorrowRequest>())
    private var showMemberIdDialog by mutableStateOf(false)
    private var onVoiceResult: ((String) -> Unit)? = null

    private var showDeleteOptionsDialog by mutableStateOf(false)
    private var bookToModify: Book? = null
    private var lastDeletedBook: Book? = null
    private var currentGroupToModify by mutableStateOf(listOf<Book>())
    private val snackbarHostState = SnackbarHostState()

    // --- Add Book Manual State (for Auto-Save) ---
    private var abIsMalayalam by mutableStateOf(true)
    private var abIsbn by mutableStateOf("")
    private var abAccessionNumber by mutableStateOf("")
    private var abTitle by mutableStateOf("")
    private var abAuthor by mutableStateOf("")
    private var abCategory by mutableStateOf("")
    private var abPublisherName by mutableStateOf("")
    private var abYearOfPublication by mutableStateOf("")
    private var abCallNumber by mutableStateOf("")
    private var abIsCallNumberAuto by mutableStateOf(true)
    private var abLocation by mutableStateOf("")
    private var abPrice by mutableStateOf("")
    private var abBookType by mutableStateOf("Normal")
    private var abLanguage by mutableStateOf("Malayalam")
    private var abNumberOfCopies by mutableStateOf("1")
    private var abAccessionNumbers by mutableStateOf(listOf(""))
    private var abIsbns by mutableStateOf(listOf(""))
    private var abLocations by mutableStateOf(listOf(""))
    private var abPrices by mutableStateOf(listOf(""))
    private var abBookTypes by mutableStateOf(listOf("Normal"))
    private var abUnavailabilityReason by mutableStateOf("")

    // --- Voice Assistant State ---
    private var isMalayalamBook by mutableStateOf(false)
    private var isBorrowable by mutableStateOf(true)
    private var voiceAddTitle by mutableStateOf("")
    private var voiceAddAuthor by mutableStateOf("")
    private var voiceAddCategory by mutableStateOf("")
    private var voiceAddPublisher by mutableStateOf("")
    private var voiceAddYear by mutableStateOf("")
    private var voiceAddCopies by mutableIntStateOf(1)
    private var voiceAddCurrentAccIndex by mutableIntStateOf(0)
    private var voiceAddAccList by mutableStateOf(mutableListOf<String>())
    private var voiceAddIsbnList by mutableStateOf(mutableListOf<String>())
    private var voiceAddLocList by mutableStateOf(mutableListOf<String>())
    private var voiceAddPriceList by mutableStateOf(mutableListOf<String>())
    private var voiceAddTypeList by mutableStateOf(mutableListOf<String>())
    private var voiceAddLanguage by mutableStateOf("English")
    private var voiceAddBookType by mutableStateOf("Normal")

    private lateinit var tts: TextToSpeech
    private var speechRecognizer: SpeechRecognizer? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isRecording = false
    private var pendingVoiceAction: (() -> Unit)? = null

    private val audioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            pendingVoiceAction?.invoke()
            pendingVoiceAction = null
        } else {
            showToast("Audio permission required")
            pendingVoiceAction = null
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            showOcrScanner = true
        } else {
            showToast("Camera permission required for scanning")
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            importExcel(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Firebase and Room first
        auth = FirebaseAuth.getInstance()
        roomDb = AppDatabase.getDatabase(this)
        storage = FirebaseStorage.getInstance()
        
        val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        db = FirebaseFirestore.getInstance()
        db.firestoreSettings = settings

        // Load preferences
        val prefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        isDarkMode = prefs.getBoolean("isDarkMode", false)
        
        // Try to get libraryId from Intent or Prefs
        val passedLibId = intent.getStringExtra("LIBRARY_ID")
        val passedRole = intent.getStringExtra("USER_ROLE")
        val passedName = intent.getStringExtra("USER_NAME")
        
        if (!passedLibId.isNullOrEmpty()) {
            libraryId = passedLibId
            prefs.edit().putString("libraryId", passedLibId).apply()
        } else {
            libraryId = prefs.getString("libraryId", "") ?: ""
        }
        
        if (!passedRole.isNullOrEmpty()) {
            userRole = passedRole
            prefs.edit().putString("userRole", passedRole).apply()
        } else {
            userRole = prefs.getString("userRole", "USER") ?: "USER"
        }
        
        if (!passedName.isNullOrEmpty()) {
            userName = passedName
            prefs.edit().putString("userName", passedName).apply()
        } else {
            userName = prefs.getString("userName", "Guest") ?: "Guest"
        }

        // If libraryId is STILL missing, but user is logged in, we must fetch it
        if (libraryId.isEmpty() && auth.currentUser != null) {
            lifecycleScope.launch {
                try {
                    val doc = db.collection("users").document(auth.currentUser!!.uid).get().await()
                    val id = doc.getString("library_id") ?: doc.getString("libraryId") ?: ""
                    val role = doc.getString("role") ?: "USER"
                    val name = doc.getString("name") ?: doc.getString("fullName") ?: "User"
                    
                    if (id.isNotEmpty()) {
                        libraryId = id
                        userRole = role
                        userName = name
                        prefs.edit()
                            .putString("libraryId", id)
                            .putString("userRole", role)
                            .putString("userName", name)
                            .apply()
                        
                        // Restart listeners with the correct ID
                        startDataListeners()
                    } else {
                        handleMissingLibraryId()
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to fetch user profile", e)
                    handleMissingLibraryId()
                }
            }
        } else if (libraryId.isEmpty()) {
            handleMissingLibraryId()
            return
        }

        startDataListeners()

        setContent {
            ArividamTheme(darkTheme = isDarkMode) {
                MainDashboard()

                if (showAddBookDialog) {
                    LaunchedEffect(showAddBookDialog) {
                        if (showAddBookDialog && preFilledBook != null) {
                            val book = preFilledBook!!
                            abTitle = book.title
                            abAuthor = book.author
                            abIsbn = book.isbn ?: ""
                            abPublisherName = book.publisherName ?: ""
                            abYearOfPublication = book.yearOfPublication?.toString() ?: ""
                            abCategory = book.category
                            abLocation = book.location
                            abPrice = book.price?.toString() ?: ""
                            abLanguage = book.language
                            abBookType = book.bookType
                            abAccessionNumbers = listOf(book.accessionNumber)
                            abIsbns = listOf(book.isbn ?: "")
                            abLocations = listOf(book.location)
                            abPrices = listOf(book.price?.toString() ?: "")
                            abBookTypes = listOf(book.bookType)
                            abNumberOfCopies = "1"
                        }
                    }
                    AddBookDialog(
                        onDismiss = { 
                            showAddBookDialog = false 
                            preFilledBook = null
                        },
                        onVoiceInput = { isMal, callback -> 
                            isMalayalamBook = isMal
                            startVoiceDictation(callback) 
                        },
                        onSave = { books ->
                            lifecycleScope.launch {
                                saveMultipleBooksToFirestore(books)
                                showAddBookDialog = false
                                preFilledBook = null
                            }
                        }
                    )
                }

                if (showEditBookDialog && selectedBook != null) {
                    val currentGroup = groupedBooks.find { it.any { b -> b.id == selectedBook!!.id } } ?: listOf(selectedBook!!)
                    val groupTotal = currentGroup.sumOf { it.numberOfCopies }
                    
                    EditBookDialog(
                        book = selectedBook!!,
                        groupTotal = groupTotal,
                        onDismiss = { showEditBookDialog = false },
                        onVoiceInput = { isMal, callback -> 
                            isMalayalamBook = isMal
                            startVoiceDictation(callback) 
                        },
                        onSave = { updatedBook ->
                            updateBookInFirestore(updatedBook)
                            showEditBookDialog = false
                        },
                        onSaveMulti = { newBooks ->
                            lifecycleScope.launch {
                                saveMultipleBooksToFirestore(newBooks)
                                showEditBookDialog = false
                            }
                        }
                    )
                }
                if (showOcrScanner) {
                    OcrScannerDialog(
                        scanType = currentScanType,
                        onDismiss = { showOcrScanner = false },
                        onResult = { list ->
                            if (currentScanType == ScanType.REGISTER) {
                                lifecycleScope.launch {
                                    statusText = "Importing books..."
                                    
                                    // Get initial max stock number
                                    var currentMaxAcc = 0
                                    val snapshot = db.collection("books")
                                        .whereEqualTo("library_id", libraryId)
                                        .get().await()
                                    snapshot.documents.forEach { doc ->
                                        val acc = doc.getString("accession_number") ?: doc.getString("accessionNumber") ?: ""
                                        val num = acc.filter { it.isDigit() }.toIntOrNull() ?: 0
                                        if (num > currentMaxAcc) currentMaxAcc = num
                                    }

                                    list.forEach { info ->
                                        val finalAccession = if (info.accession.isBlank()) "" else info.accession
                                        
                                        val bookToSave = Book(
                                            accessionNumber = finalAccession,
                                            title = LanguageUtils.correctMalayalam(info.title),
                                            author = LanguageUtils.correctMalayalam(info.author),
                                            libraryId = libraryId,
                                            status = "Available",
                                            numberOfCopies = 1,
                                            category = "Unknown", // Default for register scan
                                            location = "Unknown"  // Default for register scan
                                        )
                                        
                                        saveMultipleBooksToFirestore(listOf(bookToSave))
                                    }
                                    showToast("Imported ${list.size} books")
                                    statusText = "Ready"
                                }
                            } else {
                                val info = list.firstOrNull()
                                if (info != null) {
                                    val accession = info.accession
                                    preFilledBook = Book(
                                        isbn = info.isbn,
                                        accessionNumber = accession,
                                        title = LanguageUtils.correctMalayalam(info.title), 
                                        author = LanguageUtils.correctMalayalam(info.author),
                                        publisherName = LanguageUtils.correctMalayalam(info.publisher),
                                        yearOfPublication = info.year
                                    )
                                    showAddBookDialog = true
                                }
                            }
                            showOcrScanner = false
                        },
                        onMemberScanned = { uid ->
                            val user = allLibraryUsers.find { it.uid == uid }
                            if (user != null) {
                                selectedUserForDetail = user
                            } else {
                                showToast("Member not found in this library")
                            }
                            showOcrScanner = false
                        }
                    )
                }
                if (showReviewDialog && selectedBook != null) {
                    ReviewDialog(
                        bookTitle = selectedBook!!.title,
                        onDismiss = { showReviewDialog = false },
                        onVoiceInput = { callback -> startVoiceDictation(callback) },
                        onSave = { comment, rating ->
                            saveWrittenReview(selectedBook!!, comment, rating)
                            showReviewDialog = false
                        }
                    )
                }

                // Add Overdue & Reminder Check
                LaunchedEffect(allBooks) {
                    if (userRole == "USER") {
                        val myUid = auth.currentUser?.uid ?: return@LaunchedEffect
                        allBooks.find { book -> book.copies.any { it.borrowerId == myUid } }?.let { myBook ->
                            val myCopy = myBook.copies.find { it.borrowerId == myUid }
                            db.collection("borrow_requests")
                                .whereEqualTo("bookId", myBook.id)
                                .whereEqualTo("copy_number", myCopy?.copyNumber?.substringAfter("-C")?.toIntOrNull() ?: 0)
                                .whereEqualTo("userUid", myUid)
                                .whereEqualTo("status", "APPROVED")
                                .get()
                                .addOnSuccessListener { snapshot ->
                                    val req = snapshot.documents.firstOrNull()?.toObject(BorrowRequest::class.java)
                                    req?.dueDate?.let { due ->
                                        val now = System.currentTimeMillis()
                                        if (now > due) {
                                            speak("ശ്രദ്ധിക്കുക. ${myBook.title} തിരിച്ചേൽപ്പിക്കാനുള്ള സമയം കഴിഞ്ഞു. ദയവായി പിഴ അടച്ചു പുസ്തകം തിരിച്ചേൽപ്പിക്കുക.")
                                            statusText = "OVERDUE: Please return ${myBook.title}"
                                        } else if (due - now < 3 * 24 * 60 * 60 * 1000L) {
                                            speak("ശ്രദ്ധിക്കുക. ${myBook.title} തിരിച്ചേൽപ്പിക്കാൻ മൂന്ന് ദിവസം കൂടി മാത്രമേ സമയമുള്ളൂ.")
                                            statusText = "REMINDER: ${myBook.title} due in 3 days"
                                        }
                                    }
                                }
                        }
                    }
                }

                if (showBorrowRequestsDialog) {
                    BorrowRequestsDialog(
                        requests = borrowRequests,
                        onDismiss = { showBorrowRequestsDialog = false },
                        onApprove = { approveBorrowRequest(it) },
                        onReject = { rejectBorrowRequest(it) }
                    )
                }

                if (showUserNotificationsDialog) {
                    UserNotificationsDialog(
                        notifications = notifications,
                        onDismiss = { showUserNotificationsDialog = false }
                    )
                }

                if (showBorrowersDialog) {
                    BorrowersListDialog(
                        borrowers = currentBookBorrowers,
                        onDismiss = { showBorrowersDialog = false }
                    )
                }

                if (showCategoryFilter) {
                    CategoryFilterDialog(
                        onDismiss = { showCategoryFilter = false },
                        onCategorySelect = { cat ->
                            selectedCategory = cat
                            applyCurrentFilters()
                            statusText = "Filtering by $cat"
                            showCategoryFilter = false
                        }
                    )
                }

                if (showMembersDialog) {
                    MembersDialog(
                        users = allLibraryUsers,
                        onDismiss = { showMembersDialog = false },
                        onUserClick = { user: User -> 
                            selectedUserForDetail = user
                            showMembersDialog = false
                        }
                    )
                }

                if (selectedUserForDetail != null) {
                    UserDetailDialog(
                        user = selectedUserForDetail!!,
                        borrowedBooks = allBooks.filter { book -> book.copies.any { it.borrowerId == selectedUserForDetail!!.uid } },
                        requests = borrowRequests.filter { it.userUid == selectedUserForDetail!!.uid },
                        approvedRequests = activeBorrows.filter { it.userUid == selectedUserForDetail!!.uid },
                        historyRequests = allBorrowHistory.filter { it.userUid == selectedUserForDetail!!.uid && it.status == "RETURNED" },
                        onDismiss = { selectedUserForDetail = null },
                        onApprove = { req: BorrowRequest -> approveBorrowRequest(req) },
                        onReject = { req: BorrowRequest -> rejectBorrowRequest(req) },
                        onAcceptReturn = { book: Book, copyNum: String -> returnBook(book, copyNum) }
                    )
                }

                if (showImportSummary && importResult != null) {
                    ImportSummaryDialog(
                        result = importResult!!,
                        onDismiss = { showImportSummary = false }
                    )
                }

                if (showSettingsDialog) {
                    SettingsDialog(
                        onDismiss = { showSettingsDialog = false }
                    )
                }

                if (showVoiceDialog) {
                    VoiceListeningDialog(
                        status = statusText,
                        onDismiss = { 
                            showVoiceDialog = false
                            speechRecognizer?.destroy()
                        },
                        onRetry = {
                            launchSpeechRecognizer()
                        }
                    )
                }

                if (showMemberIdDialog) {
                    val myUid = auth.currentUser?.uid ?: ""
                    MemberIdDialog(
                        userName = userName,
                        libraryId = libraryId,
                        userUid = myUid,
                        onDismiss = { showMemberIdDialog = false }
                    )
                }

                if (showDeleteOptionsDialog && bookToModify != null) {
                    DeleteOptionsDialog(
                        bookTitle = bookToModify!!.title,
                        copyCount = maxOf(currentGroupToModify.size, bookToModify!!.numberOfCopies),
                        onDismiss = { 
                            showDeleteOptionsDialog = false
                            bookToModify = null
                            currentGroupToModify = emptyList()
                        },
                        onDeleteEntire = {
                            if (currentGroupToModify.size > 1) {
                                currentGroupToModify.forEach { db.collection("books").document(it.id).delete() }
                                speak("${bookToModify!!.title} പൂർണ്ണമായും ഒഴിവാക്കി.")
                            } else {
                                deleteBookWithUndo(bookToModify!!)
                            }
                            showDeleteOptionsDialog = false
                            bookToModify = null
                            currentGroupToModify = emptyList()
                        },
                        onReduceCopy = {
                            if (currentGroupToModify.size > 1) {
                                // Multi-doc model: just delete one doc
                                db.collection("books").document(bookToModify!!.id).delete()
                                speak("ഒരു കോപ്പി ഒഴിവാക്കി.")
                            } else {
                                // Single-doc model: update list
                                reduceCopyCount(bookToModify!!)
                            }
                            showDeleteOptionsDialog = false
                            bookToModify = null
                            currentGroupToModify = emptyList()
                        }
                    )
                }
            }
        }
    }

    private fun startDataListeners() {
        if (libraryId.isEmpty()) return
        
        // Remove existing listeners before re-attaching
        registrations.forEach { it.remove() }
        registrations.clear()
        
        initTTS()
        listenForBooks()
        listenForBorrowRequests()
        listenForNotifications()
    }

    private fun handleMissingLibraryId() {
        Log.e("MainActivity", "libraryId is empty, redirecting to Login")
        Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show()
        auth.signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun listenForNotifications() {
        if (userRole == "USER") {
            val myUid = auth.currentUser?.uid ?: return
            val reg = db.collection("borrow_requests")
                .whereEqualTo("userUid", myUid)
                .whereEqualTo("library_id", libraryId)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val newNotifications = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(BorrowRequest::class.java)?.copy(id = doc.id)
                        }
                        notifications = newNotifications
                        if (showMyBooksOnly) applyCurrentFilters()
                    }
                }
            registrations.add(reg)
        }
    }

    private fun listenForBorrowRequests() {
        if (userRole == "ADMIN") {
            val reg1 = db.collection("borrow_requests")
                .whereEqualTo("library_id", libraryId)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val all = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(BorrowRequest::class.java)?.copy(id = doc.id)
                        }
                        borrowRequests = all.filter { it.status == "PENDING" }.sortedBy { it.timeStamp }
                        activeBorrows = all.filter { it.status == "APPROVED" }
                        allBorrowHistory = all.filter { it.status == "RETURNED" }.sortedByDescending { it.returnDate }

                        // Sync to local Room table
                        lifecycleScope.launch(Dispatchers.IO) {
                            all.forEach { roomDb.borrowRequestDao().insertRequest(it) }
                        }
                    }
                }
            registrations.add(reg1)
            
            // Also listen for members (filter for USER role only)
            val reg2 = db.collection("users")
                .whereEqualTo("library_id", libraryId)
                .whereEqualTo("role", "USER")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("MainActivity", "Error listening for users: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val users = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(User::class.java)?.apply { uid = doc.id }
                        }
                        Log.d("MainActivity", "Found ${users.size} users for library $libraryId")
                        allLibraryUsers = users
                    }
                }
            registrations.add(reg2)
        }
    }

    private fun initTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Use runOnUiThread to ensure tts variable is assigned if onInit was synchronous
                runOnUiThread {
                    if (::tts.isInitialized) {
                        tts.language = Locale("ml", "IN")
                        setupTtsListener()
                        speak("സ്വാഗതം, $userName. ലൈബ്രറി അസിസ്റ്റന്റ് ഇപ്പോൾ സജ്ജമാണ്.")
                    }
                }
            }
        }
    }

    private fun setupTtsListener() {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                Log.d("VoiceFlow", "TTS Started: $id")
            }
            override fun onDone(id: String?) {
                Log.d("VoiceFlow", "TTS Done: $id")
                runOnUiThread {
                    if (id != null && (id.endsWith("_prompt"))) {
                        // Reduced delay to 400ms to improve responsiveness while still ensuring hardware release
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            launchSpeechRecognizer()
                        }, 400)
                    }
                }
            }
            override fun onError(id: String?) {
                Log.e("VoiceFlow", "TTS Error: $id")
            }
        })
    }

    private fun listenForBooks() {
        val reg = db.collection("books")
            .whereEqualTo("library_id", libraryId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val books = snapshot.documents.mapNotNull { doc ->
                        try {
                            val data = doc.data ?: return@mapNotNull null
                            
                            // Manually deserialize to handle type mismatches and legacy field names
                            val accessionNumber = data["accession_number"]?.toString() 
                                ?: data["accessionNumber"]?.toString() ?: ""
                            
                            val numCopies = (data["number_of_copies"] as? Number)?.toInt() 
                                ?: (data["numberOfCopies"] as? Number)?.toInt() ?: 1
                            
                            val copiesData = data["copies"] as? List<Map<String, Any>>
                            var copiesList = copiesData?.map { c ->
                                BookCopy(
                                    accessionNumber = c["accession_number"] as? String ?: c["accessionNumber"] as? String ?: "",
                                    copyNumber = c["copyNumber"] as? String ?: c["copy_number"] as? String ?: "",
                                    status = c["status"] as? String ?: "Available",
                                    borrowerId = c["borrower_id"] as? String ?: c["borrowerId"] as? String,
                                    borrowerName = c["borrowerName"] as? String ?: c["borrowername"] as? String,
                                    dueDate = (c["dueDate"] as? Number)?.toLong() ?: (c["due_date"] as? Number)?.toLong()
                                )
                            } ?: emptyList()

                            if (copiesList.isEmpty() && numCopies > 0) {
                                val docStatus = data["status"] as? String ?: "Available"
                                copiesList = (1..numCopies).map { i ->
                                    BookCopy(
                                        accessionNumber = if (numCopies == 1) accessionNumber else "$accessionNumber-C$i",
                                        copyNumber = if (numCopies == 1) accessionNumber else "$accessionNumber-C$i",
                                        status = docStatus
                                    )
                                }
                            }

                            val bookToSave = Book(
                                accessionNumber = accessionNumber,
                                id = doc.id,
                                isbn = data["isbn"] as? String,
                                title = data["title"] as? String ?: "",
                                author = data["author"] as? String ?: "",
                                category = data["category"] as? String ?: "",
                                publisherName = data["publisher_name"] as? String ?: data["publisherName"] as? String,
                                yearOfPublication = (data["year_of_publication"] as? Number)?.toInt() ?: (data["yearOfPublication"] as? Number)?.toInt(),
                                callNumber = data["call_number"] as? String ?: data["callNumber"] as? String,
                                location = data["location"] as? String ?: "",
                                price = (data["price"] as? Number)?.toInt(),
                                status = data["status"] as? String ?: "Available",
                                numberOfCopies = numCopies,
                                canBeBorrowed = data["can_be_borrowed"] as? Boolean ?: data["canBeBorrowed"] as? Boolean ?: true,
                                adminPhoneNumber = data["admin_phone_number"] as? String ?: data["adminPhoneNumber"] as? String ?: "",
                                libraryId = data["library_id"] as? String ?: data["libraryId"] as? String ?: "",
                                language = data["language"] as? String ?: "English",
                                bookType = data["book_type"] as? String ?: data["bookType"] as? String ?: "Normal",
                                unavailabilityReason = data["unavailability_reason"] as? String ?: data["unavailabilityReason"] as? String
                            ).apply {
                                copies = copiesList
                            }
                                
                                val reviewsData = data["review"] as? List<Map<String, Any>> ?: data["reviews"] as? List<Map<String, Any>>
                                bookToSave.reviews = reviewsData?.map { r ->
                                    Review(
                                        userUid = r["userUid"] as? String ?: "",
                                        userName = r["userName"] as? String ?: "",
                                        comment = r["comment"] as? String ?: "",
                                        rating = (r["rating"] as? Number)?.toInt() ?: 0,
                                        timestamp = (r["time_stamp"] as? Number)?.toLong() ?: (r["timestamp"] as? Number)?.toLong() ?: 0L
                                    )
                                } ?: emptyList()
                                
                                bookToSave
                            } catch (e: Exception) {
                            Log.e("MainActivity", "Error deserializing book ${doc.id}", e)
                            null
                        }
                    }
                    allBooks = books
                    applyCurrentFilters()

                    // Sync to local Room table
                    lifecycleScope.launch(Dispatchers.IO) {
                        books.forEach { roomDb.bookDao().insertBook(it) }
                    }
                }
            }
        registrations.add(reg)
    }

    private fun applyCurrentFilters() {
        var filtered = allBooks
        if (showMyBooksOnly) {
            val myUid = auth.currentUser?.uid ?: ""
            if (myUid.isNotEmpty()) {
                // Include books that are actually borrowed in the 'copies' list
                // OR books that have an active request (PENDING/APPROVED) for this user
                val activeRequestBookIds = notifications
                    .filter { it.status == "PENDING" || it.status == "APPROVED" }
                    .map { it.bookId }
                    .toSet()

                filtered = filtered.filter { book -> 
                    book.copies.any { it.borrowerId == myUid } || activeRequestBookIds.contains(book.id)
                }
            } else {
                filtered = emptyList()
            }
        }
        if (selectedCategory != "All") {
            filtered = filtered.filter { it.category == selectedCategory }
        }
        if (currentSearchQuery.isNotEmpty()) {
            val lower = currentSearchQuery.lowercase()
            filtered = filtered.filter { 
                it.title.lowercase().contains(lower) || 
                it.author.lowercase().contains(lower) ||
                it.location.lowercase().contains(lower) ||
                it.isbn?.lowercase()?.contains(lower) == true ||
                it.accessionNumber.lowercase().contains(lower)
            }
        }
        filteredBooks = filtered
        groupedBooks = filtered.groupBy { it.title.lowercase().trim() to it.author.lowercase().trim() }.values.toList()
    }

    private fun calculateNextAccession(): String {
        var maxNum = 0
        allBooks.forEach { book ->
            val num = book.accessionNumber.filter { it.isDigit() }.toIntOrNull() ?: 0
            if (num > maxNum) maxNum = num
        }
        return (maxNum + 1).toString()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainDashboard() {
        val mainColor = if (isDarkMode) Color(0xFFD7CCC8) else Color(0xFF452719)
        val bgBeige = if (isDarkMode) Color(0xFF12100E) else Color(0xFFFAF9F6)
        val cardBg = if (isDarkMode) Color(0xFF1E1916) else Color.White
        val surfaceBeige = if (isDarkMode) Color(0xFF2D2420) else Color(0xFFF3EAE2)
        val accentColor = if (isDarkMode) Color(0xFF8D6E63) else Color(0xFFB08968)
        val textPrimary = if (isDarkMode) Color(0xFFE0E0E0) else Color(0xFF452719)
        
        Scaffold(
            containerColor = bgBeige,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = bgBeige,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Initials Avatar
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = "App Logo",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Text(
                            "Arividam",
                            fontSize = 20.sp, 
                            fontWeight = FontWeight.Bold, 
                            color = mainColor,
                            modifier = Modifier.weight(1f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { 
                                startDataListeners()
                                showToast("Dashboard refreshed")
                            }) {
                                Icon(Icons.Default.Refresh, null, tint = mainColor)
                            }

                            IconButton(onClick = { 
                                if (userRole == "ADMIN") showBorrowRequestsDialog = true 
                                else showUserNotificationsDialog = true
                            }) {
                                BadgedBox(badge = { 
                                    val count = if (userRole == "ADMIN") borrowRequests.size 
                                                else notifications.count { it.status == "APPROVED" }
                                    if (count > 0) Badge { Text(count.toString()) } 
                                }) {
                                    Icon(Icons.Default.NotificationsNone, null, tint = mainColor)
                                }
                            }
                            
                            IconButton(onClick = { showSettingsDialog = true }) {
                                Icon(Icons.Default.Settings, null, tint = mainColor)
                            }

                            IconButton(onClick = {
                                auth.signOut()
                                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                                finish()
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Logout, null, tint = mainColor)
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { startVoiceAssistant() },
                    containerColor = mainColor,
                    contentColor = Color.White,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
                ) { Icon(Icons.Default.Mic, "Voice Assistant", modifier = Modifier.size(28.dp)) }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp)
            ) {
                // Voice Assistant Card
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = surfaceBeige.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(accentColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Mic, null, tint = if (isDarkMode) Color.Black else Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    "സ്വാഗതം, $userName.",
                                    fontSize = 14.sp,
                                    color = mainColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "ലൈബ്രറി അസിസ്റ്റന്റ് ഇപ്പോൾ സജ്ജമാണ്.",
                                    fontSize = 12.sp,
                                    color = mainColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // User Info Card
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(32.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        colors = if (isDarkMode) 
                                            listOf(Color(0xFF3E2723), Color(0xFF1B1614)) 
                                            else listOf(Color(0xFF6B4226), mainColor)
                                    )
                                )
                                .padding(24.dp)
                        ) {
                            Column {
                                Text("Welcome,", fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
                                Text(
                                    userName, 
                                    fontSize = 32.sp, 
                                    fontWeight = FontWeight.Bold, 
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    color = Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(
                                        " Access Level: $userRole | ID: ${libraryId.takeLast(4)} ",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }

                                if (userRole == "USER") {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { showMemberIdDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.QrCode, null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Digital Member ID", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                // Library Functions Header
                item {
                    if (selectedCategory != "All" || currentSearchQuery.isNotEmpty() || showMyBooksOnly) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            color = mainColor.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val filterText = buildString {
                                    if (showMyBooksOnly) append("My Books")
                                    if (selectedCategory != "All") {
                                        if (isNotEmpty()) append(" in ")
                                        append(selectedCategory)
                                    }
                                    if (currentSearchQuery.isNotEmpty()) {
                                        if (isNotEmpty()) append(" matching ")
                                        append("'${currentSearchQuery}'")
                                    }
                                }
                                Text(
                                    text = filterText,
                                    fontSize = 12.sp,
                                    color = mainColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "Clear",
                                    fontSize = 12.sp,
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        selectedCategory = "All"
                                        currentSearchQuery = ""
                                        showMyBooksOnly = false
                                        applyCurrentFilters()
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val toolCount = if (userRole == "ADMIN") "6 TOOLS" else "3 TOOLS"
                        Text("Library Functions", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = mainColor)
                        Text(toolCount, fontSize = 12.sp, color = mainColor.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (userRole == "ADMIN") {
                    // ADMIN LAYOUT
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    currentMode = "search_lang"
                                    speak("പുസ്തകം മലയാളമാണോ? അതെ അല്ലെങ്കിൽ അല്ല എന്ന് പറയുക", "search_lang_prompt")
                                },
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Brush.horizontalGradient(listOf(Color(0xFF452719), Color(0xFF6B4226))))
                                    .padding(20.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color.White.copy(alpha = 0.1f),
                                        modifier = Modifier.size(56.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Search, null, tint = Color.White, modifier = Modifier.size(28.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Search", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        Text("Find books, members & records", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                                    }
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White.copy(alpha = 0.15f),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FunctionGridButton(
                                text = "Add Book",
                                icon = Icons.Default.Add,
                                modifier = Modifier.weight(1f),
                                containerColor = mainColor
                            ) {
                                preFilledBook = Book(accessionNumber = "")
                                showAddBookDialog = true 
                            }
                            
                            FunctionGridButton(
                                text = "Scan Book",
                                icon = Icons.Default.PhotoCamera,
                                modifier = Modifier.weight(1f),
                                containerColor = Color(0xFFB08968)
                            ) {
                                currentScanType = ScanType.BOOK_COVER
                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }

                            FunctionGridButton(
                                text = "Scan Register",
                                icon = Icons.AutoMirrored.Filled.Assignment,
                                modifier = Modifier.weight(1f),
                                containerColor = Color(0xFF5D4037)
                            ) {
                                currentScanType = ScanType.REGISTER
                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ToolOutlineButton("Categories", Icons.Default.Category, Modifier.weight(1f)) {
                                showCategoryFilter = true
                            }
                            ToolOutlineButton("Catalog", Icons.Default.GridView, Modifier.weight(1f)) {
                                selectedCategory = "All"
                                currentSearchQuery = ""
                                showMyBooksOnly = false
                                applyCurrentFilters()
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ToolOutlineButton("Members", Icons.Default.Groups, Modifier.weight(1f)) {
                                showMembersDialog = true
                            }
                            ToolOutlineButton("Scan ID", Icons.Default.QrCodeScanner, Modifier.weight(1f)) {
                                currentScanType = ScanType.MEMBER_ID
                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                        }
                    }

                    // Excel Management Section for ADMIN
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Excel Management", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = mainColor)
                            Text("5 ACTIONS", fontSize = 12.sp, color = mainColor.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column {
                                ExcelActionItem("Download Template", Icons.Default.Description) {
                                    downloadImportTemplate()
                                }
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp, color = if (isDarkMode) Color.Gray.copy(alpha = 0.2f) else Color.LightGray.copy(alpha = 0.3f))
                                ExcelActionItem("Import", Icons.Default.FileUpload) {
                                    importLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                                }
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp, color = if (isDarkMode) Color.Gray.copy(alpha = 0.2f) else Color.LightGray.copy(alpha = 0.3f))
                                ExcelActionItem("Export Catalog", Icons.Default.FileDownload) { exportCatalog() }
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp, color = if (isDarkMode) Color.Gray.copy(alpha = 0.2f) else Color.LightGray.copy(alpha = 0.3f))
                                ExcelActionItem("Borrowed Books", Icons.AutoMirrored.Filled.ListAlt) { exportBorrowedBooks() }
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp, color = if (isDarkMode) Color.Gray.copy(alpha = 0.2f) else Color.LightGray.copy(alpha = 0.3f))
                                ExcelActionItem("Reviews Report", Icons.Default.RateReview) { exportReviews() }
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), thickness = 0.5.dp, color = if (isDarkMode) Color.Gray.copy(alpha = 0.2f) else Color.LightGray.copy(alpha = 0.3f))
                                ExcelActionItem("Library Stats", Icons.Default.PieChart) { exportStatistics() }
                            }
                        }
                    }
                } else {
                    // USER LAYOUT
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FunctionGridButton(
                                text = "Search",
                                icon = Icons.Default.Search,
                                modifier = Modifier.weight(1f),
                                containerColor = mainColor
                            ) {
                                currentMode = "search_lang"
                                speak("പുസ്തകം മലയാളമാണോ? അതെ അല്ലെങ്കിൽ അല്ല എന്ന് പറയുക", "search_lang_prompt")
                            }
                            
                            FunctionGridButton(
                                text = "Categories",
                                icon = Icons.Default.Category,
                                modifier = Modifier.weight(1f),
                                containerColor = mainColor
                            ) {
                                showCategoryFilter = true
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FunctionGridButton(
                                text = "My Books",
                                icon = Icons.AutoMirrored.Filled.MenuBook,
                                modifier = Modifier.weight(1f),
                                containerColor = mainColor
                            ) {
                                showMyBooksOnly = true
                                applyCurrentFilters()
                                statusText = "Showing your books"
                            }
                            FunctionGridButton(
                                text = "Catalog",
                                icon = Icons.Default.GridView,
                                modifier = Modifier.weight(1f),
                                containerColor = Color(0xFFB08968)
                            ) {
                                selectedCategory = "All"
                                currentSearchQuery = ""
                                showMyBooksOnly = false
                                applyCurrentFilters()
                                statusText = "Showing all books"
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        if (showMyBooksOnly) "My Borrowed Books" else "Available Books",
                        fontWeight = FontWeight.Bold, 
                        fontSize = 18.sp, 
                        color = mainColor
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                items(groupedBooks) { group ->
                    DashboardBookItem(group)
                }
                
                item {
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }

    @Composable
    fun FunctionGridButton(text: String, icon: ImageVector, modifier: Modifier, containerColor: Color, onClick: () -> Unit) {
        Card(
            modifier = modifier
                .height(120.dp)
                .clickable { onClick() },
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }

    @Composable
    fun ToolOutlineButton(text: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
        val cardBg = if (isDarkMode) Color(0xFF1E1916) else Color.White
        val mainColor = if (isDarkMode) Color(0xFFD7CCC8) else Color(0xFF452719)
        val accentColor = if (isDarkMode) Color(0xFF8D6E63) else Color(0xFFB08968)
        
        Card(
            modifier = modifier
                .height(100.dp)
                .clickable { onClick() },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, if (isDarkMode) Color.Gray.copy(alpha = 0.2f) else Color.LightGray.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(text, color = mainColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
    }

    @Composable
    fun ExcelActionItem(text: String, icon: ImageVector, onClick: () -> Unit) {
        val mainColor = if (isDarkMode) Color(0xFFD7CCC8) else Color(0xFF452719)
        val surfaceBeige = if (isDarkMode) Color(0xFF2D2420) else Color(0xFFF3EAE2)
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = surfaceBeige,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = mainColor, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(text, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = mainColor)
            Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
        }
    }

    @Composable
    fun DashboardBookItem(group: List<Book>) {
        val primaryBook = group.first()
        val totalCopiesInGroup = group.sumOf { it.numberOfCopies }
        
        // Accurate counts based on individual copies
        val allCopies = group.flatMap { b ->
            if (b.copies.isEmpty() && b.numberOfCopies > 0) {
                // Fallback for documents that haven't synced the copies list yet
                val docStatus = b.status
                (1..b.numberOfCopies).map { i ->
                    BookCopy(
                        accessionNumber = if (b.numberOfCopies == 1) b.accessionNumber else "${b.accessionNumber}-C$i",
                        copyNumber = if (b.numberOfCopies == 1) b.accessionNumber else "${b.accessionNumber}-C$i",
                        status = docStatus
                    )
                }
            } else b.copies
        }
        
        val availableCopies = allCopies.count { it.status == "Available" }
        val borrowedCopiesCount = allCopies.count { it.status == "Borrowed" }
        
        val displayStatus = when {
            availableCopies > 0 -> "Available"
            borrowedCopiesCount > 0 -> "Borrowed"
            else -> primaryBook.status
        }
        val mainColor = if (isDarkMode) Color(0xFFD7CCC8) else Color(0xFF452719)
        val cardBg = if (isDarkMode) Color(0xFF1E1916) else Color.White
        val accentColor = if (isDarkMode) Color(0xFF8D6E63) else Color(0xFFB08968)
        val bgBeige = if (isDarkMode) Color(0xFF12100E) else Color(0xFFFAF9F6)
        
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            var showReviews by remember { mutableStateOf(false) }
            var showCopies by remember { mutableStateOf(false) }
                    val groupIds = group.map { it.id }.toSet()
            val hasActiveRequest = notifications.any { 
                it.bookId in groupIds && (it.status == "PENDING" || it.status == "APPROVED")
            }

            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = primaryBook.title, 
                            style = MaterialTheme.typography.titleLarge, 
                            fontWeight = FontWeight.Bold,
                            color = mainColor
                        )
                        Text(
                            text = "by ${primaryBook.author}", 
                            fontSize = 13.sp,
                            color = if (isDarkMode) Color.LightGray else Color.Gray,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    val statusColor = when {
                        availableCopies > 0 -> Color(0xFF4CAF50) // Green
                        borrowedCopiesCount > 0 -> Color(0xFF2196F3) // Blue
                        else -> Color(0xFFF44336) // Red
                    }
                    Surface(
                        color = statusColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = when {
                                availableCopies > 0 -> "Available"
                                borrowedCopiesCount > 0 -> "Borrowed"
                                else -> primaryBook.status
                            },
                            color = statusColor, 
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), 
                            fontSize = 12.sp, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BookInfoChip(Icons.AutoMirrored.Filled.MenuBook, primaryBook.category.ifBlank { "കഥ" })
                    BookInfoChip(Icons.Default.Place, "Rack: ${primaryBook.location}")
                    if (totalCopiesInGroup == 1) {
                        BookInfoChip(Icons.Default.QrCode, "Stock: ${primaryBook.accessionNumber}")
                    }
                    BookInfoChip(Icons.Default.LibraryBooks, "Total: $totalCopiesInGroup | $availableCopies Avail")
                    primaryBook.unavailabilityReason?.let { reason ->
                        if (reason.isNotBlank()) {
                            BookInfoChip(Icons.Default.Info, "Reason: $reason")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                // Aggregate Reviews
                val allReviews = group.flatMap { it.reviews }.distinctBy { it.userUid + it.timestamp }
                val avgRating = if (allReviews.isNotEmpty()) allReviews.map { it.rating }.average() else 0.0
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFBC02D), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = String.format(Locale.getDefault(), "%.1f (%d reviews)", avgRating, allReviews.size), 
                        fontSize = 13.sp,
                        color = if (isDarkMode) Color.LightGray else Color.Gray
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if(showReviews) "Hide Reviews" else "View Reviews", 
                        color = accentColor, 
                        fontSize = 13.sp, 
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { showReviews = !showReviews }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if(showCopies) "Hide Items" else "View Items", 
                        color = accentColor, 
                        fontSize = 13.sp, 
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { showCopies = !showCopies }
                    )
                }

                if (showCopies) {
                    Spacer(modifier = Modifier.height(12.dp))
                    group.forEach { bookItem ->
                        val itemCopies = if (bookItem.copies.isEmpty() && bookItem.numberOfCopies > 0) {
                            val docStatus = bookItem.status
                            (1..bookItem.numberOfCopies).map { i ->
                                BookCopy(
                                    accessionNumber = if (bookItem.numberOfCopies == 1) bookItem.accessionNumber else "${bookItem.accessionNumber}-C$i",
                                    copyNumber = if (bookItem.numberOfCopies == 1) bookItem.accessionNumber else "${bookItem.accessionNumber}-C$i",
                                    status = docStatus
                                )
                            }
                        } else bookItem.copies

                        itemCopies.forEach { copy ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), 
                                colors = CardDefaults.cardColors(containerColor = if (isDarkMode) Color(0xFF2D2420) else Color(0xFFFAF9F6)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Stock: ${copy.accessionNumber}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = mainColor)
                                        Text(copy.status, fontSize = 11.sp, color = if(copy.status == "Available") Color(0xFF4CAF50) else Color(0xFFF44336))
                                        if (copy.borrowerName != null) {
                                            Text("Borrowed by: ${copy.borrowerName}", fontSize = 10.sp, color = Color.Gray)
                                        }
                                        if (bookItem.isbn?.isNotBlank() == true) {
                                            Text("ISBN: ${bookItem.isbn}", fontSize = 10.sp, color = Color.Gray)
                                        }
                                        Text("Rack: ${bookItem.location}", fontSize = 10.sp, color = Color.Gray)
                                    }
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (userRole == "ADMIN") {
                                            if (copy.status == "Borrowed") {
                                                IconButton(onClick = { returnBook(bookItem, copy.copyNumber) }) {
                                                    Icon(Icons.Default.SettingsBackupRestore, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                                                }
                                            }
                                            IconButton(onClick = { selectedBook = bookItem; showEditBookDialog = true }) {
                                                Icon(Icons.Default.Edit, null, tint = mainColor, modifier = Modifier.size(20.dp))
                                            }
                                            IconButton(onClick = { 
                                                if (copy.status == "Borrowed") {
                                                    showToast("Cannot delete: This copy is currently lent out.")
                                                } else {
                                                    if (bookItem.numberOfCopies > 1) {
                                                        reduceCopyCount(bookItem)
                                                    } else {
                                                        deleteBook(bookItem.id)
                                                    }
                                                }
                                            }) {
                                                Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showReviews) {
                    Spacer(modifier = Modifier.height(12.dp))
                    allReviews.forEach { review ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), 
                            colors = CardDefaults.cardColors(containerColor = if (isDarkMode) Color(0xFF2D2420) else Color(0xFFFAF9F6)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(review.userName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = mainColor)
                                    Row {
                                        repeat(5) { i ->
                                            Icon(imageVector = if (i < review.rating) Icons.Default.Star else Icons.Default.StarBorder, null, tint = Color(0xFFFBC02D), modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                                if (review.comment.isNotEmpty()) {
                                    Text(review.comment, fontSize = 12.sp, color = if (isDarkMode) Color.LightGray else Color.DarkGray)
                                }
                                if (review.userUid == auth.currentUser?.uid) {
                                    TextButton(
                                        onClick = { deleteReview(primaryBook, review) },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text("Delete", color = Color.Red, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    if (userRole == "USER") {
                        val availableBook = group.find { it.status == "Available" }
                        val hasAvailable = availableBook != null

                        if (primaryBook.canBeBorrowed) {
                            Button(
                                onClick = { 
                                    if (!hasActiveRequest && hasAvailable) {
                                        // Request the first available book document in the group
                                        requestBorrow(availableBook!!, availableBook.accessionNumber)
                                    } else if (!hasAvailable) {
                                        showToast("No copies available for borrowal at the moment.")
                                    }
                                },
                                enabled = !hasActiveRequest,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(40.dp).weight(1f).padding(end = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (hasAvailable) mainColor else Color.Gray, 
                                    contentColor = if (isDarkMode) Color(0xFF452719) else Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = when {
                                        hasActiveRequest -> Icons.Default.Check
                                        !hasAvailable -> Icons.Default.Info
                                        else -> Icons.Default.LibraryAdd
                                    }, 
                                    null, 
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when {
                                        hasActiveRequest -> "Requested"
                                        !hasAvailable -> "Out of Stock"
                                        else -> "Request Borrow"
                                    }, 
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (!primaryBook.canBeBorrowed) {
                            val phone = primaryBook.adminPhoneNumber
                            if (phone.isNotEmpty()) {
                                Button(
                                    onClick = { 
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                        startActivity(intent)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(40.dp).weight(1f).padding(end = 8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White)
                                ) {
                                    Icon(Icons.Default.Phone, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Contact", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        
                        IconButton(onClick = { selectedBook = primaryBook; showReviewDialog = true }) {
                            Icon(Icons.Default.RateReview, null, tint = mainColor)
                        }
                    }

                    if (userRole == "ADMIN") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = { 
                                currentBookBorrowers = group.flatMap { b ->
                                    activeBorrows.filter { it.bookId == b.id }
                                }
                                showBorrowersDialog = true
                            }) { Icon(Icons.Default.Groups, null, tint = mainColor) }
                            IconButton(onClick = { selectedBook = primaryBook; showEditBookDialog = true }) { Icon(Icons.Default.Edit, null, tint = mainColor) }
                            IconButton(onClick = { 
                                val isAnyBorrowed = group.any { it.status == "Borrowed" || it.copies.any { c -> c.status == "Borrowed" } }
                                if (isAnyBorrowed) {
                                    showToast("Cannot delete: Some copies are currently lent out.")
                                    speak("ഈ പുസ്തകം ആരുടെയോ കയ്യിലാണ്. അതിനാൽ ഒഴിവാക്കാൻ കഴിയില്ല.")
                                } else {
                                    val effectiveCopies = maxOf(group.size, primaryBook.numberOfCopies)
                                    if (effectiveCopies > 1) {
                                        bookToModify = primaryBook
                                        currentGroupToModify = group
                                        showDeleteOptionsDialog = true
                                    } else {
                                        deleteBookWithUndo(primaryBook)
                                    }
                                }
                            }) { Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444)) }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun BookInfoChip(icon: ImageVector, text: String) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = Color(0xFFB08968))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
        }
    }

    private fun startVoiceAssistant() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingVoiceAction = { startVoiceAssistant() }
            audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            return
        }

        val options = if (userRole == "ADMIN") {
            "എന്താണ് ചെയ്യേണ്ടത്? തിരയുക, വിവരം, അഭിപ്രായം, ചേർക്കുക, അല്ലെങ്കിൽ ഒഴിവാക്കുക?"
        } else {
            "എന്താണ് ചെയ്യേണ്ടത്? തിരയുക, വിവരം, അഭിപ്രായം, വായ്പ, അല്ലെങ്കിൽ തിരിച്ചു കിട്ടി?"
        }
        currentMode = "" // Reset mode on fresh start
        isMalayalamBook = true // Default to true for the prompts
        speak(options, "options_prompt")
    }

    private fun handleVoiceCommand(rawText: String) {
        val text = LanguageUtils.correctMalayalam(rawText)
        val lower = text.lowercase()
        statusText = "Heard: $text"

        // Robust Yes/No detection
        val isYes = lower.contains("അതെ") || lower.contains("അതേ") || lower.contains("അത") || lower.contains("venam") || lower.contains("വേണം") || lower.contains("yes") || lower.contains("malayalam") || lower.contains("മലയാളം")
        val isNo = lower.contains("അല്ല") || lower.contains("venda") || lower.contains("വേണ്ട") || lower.contains("no") || lower.contains("english") || lower.contains("ഇംഗ്ലീഷ്")

        when {
            // Conversational States: Search Flow
            currentMode == "search_lang" -> {
                isMalayalamBook = !isNo 
                if (isYes) isMalayalamBook = true
                currentMode = "performing_search"
                speak("തിരയേണ്ട പുസ്തകം പറയുക", "search_prompt")
            }
            currentMode == "performing_search" -> {
                currentSearchQuery = lower
                applyCurrentFilters()
                val results = filteredBooks
                if (results.isEmpty()) {
                    speak("പുസ്തകങ്ങൾ കണ്ടെത്താനായില്ല")
                } else {
                    val grouped = results.groupBy { it.title.lowercase() to it.author.lowercase() }
                    if (grouped.size == 1) {
                        val book = results[0]
                        speak("${book.author} എഴുതിയ ${book.title} എന്ന പുസ്തകം റാക്ക് ${book.location} ൽ കണ്ടെത്തി.")
                    } else {
                        speak("${results.size} പുസ്തകങ്ങൾ കണ്ടെത്തി.")
                    }
                }
                currentMode = ""
            }

            // Conversational States: Addition Flow
            currentMode == "add_lang" -> {
                voiceAddLanguage = when {
                    lower.contains("malayalam") || lower.contains("മലയാളം") -> {
                        isMalayalamBook = true
                        "Malayalam"
                    }
                    lower.contains("english") || lower.contains("ഇംഗ്ലീഷ്") -> {
                        isMalayalamBook = false
                        "English"
                    }
                    else -> {
                        isMalayalamBook = false
                        "Others"
                    }
                }
                currentMode = "add_title"
                speak("പുസ്തകത്തിന്റെ പേര് പറയുക.", "add_title_prompt")
            }
            currentMode == "add_title" -> {
                voiceAddTitle = LanguageUtils.formatTitleWithSpaces(text)
                currentMode = "add_author"
                speak("രചയിതാവിന്റെ പേര് പറയുക", "add_author_prompt")
            }
            currentMode == "add_author" -> {
                voiceAddAuthor = LanguageUtils.formatTitleWithSpaces(text)
                currentMode = "add_cat"
                speak("വിഭാഗം പറയുക. നോവൽ, ചെറുകഥകൾ, ആത്മകഥ, പ്രബന്ധങ്ങൾ, ചിന്തകൾ, അല്ലെങ്കിൽ ബാലസാഹിത്യം?", "add_cat_prompt")
            }
            currentMode == "add_cat" -> {
                voiceAddCategory = CategoryConstants.mapToStandard(text)
                voiceAddCopies = 1
                voiceAddAccList = mutableListOf("")
                voiceAddIsbnList = mutableListOf("")
                voiceAddLocList = mutableListOf("")
                voiceAddPriceList = mutableListOf("")
                voiceAddTypeList = mutableListOf("Normal")
                voiceAddCurrentAccIndex = 0
                currentMode = "add_acc_multi"
                speak("അക്സെഷൻ നമ്പർ പറയുക", "add_acc_multi_prompt")
            }
            currentMode == "add_acc_multi" -> {
                voiceAddAccList[voiceAddCurrentAccIndex] = LanguageUtils.transliterateToEnglish(text)
                currentMode = "add_isbn_multi"
                speak("ISBN നമ്പർ ഇംഗ്ലീഷിൽ പറയുക", "add_isbn_multi_prompt")
            }
            currentMode == "add_isbn_multi" -> {
                voiceAddIsbnList[voiceAddCurrentAccIndex] = LanguageUtils.transliterateToEnglishPreserveSpaces(text)
                currentMode = "add_loc_multi"
                speak("റാക്ക് നമ്പർ പറയുക", "add_loc_multi_prompt")
            }
            currentMode == "add_loc_multi" -> {
                voiceAddLocList[voiceAddCurrentAccIndex] = LanguageUtils.formatRackNumber(text)
                currentMode = "add_price_multi"
                speak("വില ഇംഗ്ലീഷിൽ പറയുക", "add_price_multi_prompt")
            }
            currentMode == "add_price_multi" -> {
                voiceAddPriceList[voiceAddCurrentAccIndex] = text.filter { it.isDigit() || it == '.' }
                currentMode = "add_type_multi"
                speak("പുസ്തകത്തിന്റെ തരം പറയുക. നോർമൽ അല്ലെങ്കിൽ റഫറൻസ്?", "add_type_multi_prompt")
            }
            currentMode == "add_type_multi" -> {
                voiceAddTypeList[voiceAddCurrentAccIndex] = when {
                    lower.contains("reference") || lower.contains("റഫറൻസ്") -> "Reference"
                    else -> "Normal"
                }
                
                if (voiceAddCurrentAccIndex < voiceAddCopies - 1) {
                    voiceAddCurrentAccIndex++
                    currentMode = "add_acc_multi"
                    speak("${voiceAddCurrentAccIndex + 1}-ാമത്തെ പുസ്തകത്തിന്റെ അക്സെഷൻ നമ്പർ പറയുക", "add_acc_multi_prompt")
                } else {
                    currentMode = "add_borrowable"
                    speak("ഈ പുസ്തകങ്ങൾ വായ്പ നൽകാൻ കഴിയുമോ?", "add_borrowable_prompt")
                }
            }
            currentMode == "add_borrowable" -> {
                isBorrowable = !isNo
                if (isYes) isBorrowable = true
                currentMode = "ask_more_fields"
                speak("കൂടുതൽ വിവരങ്ങൾ ചേർക്കണോ?", "ask_more_fields_prompt")
            }
            currentMode == "ask_more_fields" -> {
                if (isYes) {
                    currentMode = "add_publisher"
                    speak("പബ്ലിഷറുടെ പേര് പറയുക", "add_publisher_prompt")
                } else {
                    validateAndFinalizeVoiceAdd()
                }
            }
            currentMode == "add_publisher" -> {
                voiceAddPublisher = text
                currentMode = "add_year"
                speak("പ്രസിദ്ധീകരിച്ച വർഷം പറയുക", "add_year_prompt")
            }
            currentMode == "add_year" -> {
                voiceAddYear = text.filter { it.isDigit() }
                currentMode = "add_book_type"
                speak("പുസ്തകത്തിന്റെ തരം പറയുക. നോർമൽ അല്ലെങ്കിൽ റഫറൻസ്?", "add_book_type_prompt")
            }
            currentMode == "add_book_type" -> {
                voiceAddBookType = when {
                    lower.contains("reference") || lower.contains("റഫറൻസ്") -> "Reference"
                    else -> "Normal"
                }
                validateAndFinalizeVoiceAdd()
            }

            // Dictation for manual forms
            currentMode == "dictation" -> {
                onVoiceResult?.invoke(text)
                onVoiceResult = null
                currentMode = ""
            }

            // Conversational States: Details & Review Search
            currentMode == "details_lang" -> {
                isMalayalamBook = !isNo 
                if (isYes) isMalayalamBook = true
                currentMode = "details_search"
                speak("ഏത് പുസ്തകത്തിന്റെ വിവരമാണ് വേണ്ടത്?", "details_prompt")
            }
            currentMode == "details_search" -> { readBookDetails(text); currentMode = "" }
            
            currentMode == "review_lang" -> {
                isMalayalamBook = !isNo 
                if (isYes) isMalayalamBook = true
                currentMode = "review_search"
                speak("അഭിപ്രായം ചേർക്കേണ്ട പുസ്തകം പറയുക", "review_prompt")
            }
            currentMode == "review_search" -> {
                val book = allBooks.find { it.title.lowercase().contains(lower) }
                if (book != null) {
                    selectedBook = book
                    showReviewDialog = true
                    speak("അഭിപ്രായം ഇവിടെ നൽകാം.")
                } else speak("പുസ്തകം കണ്ടെത്താനായില്ല.")
                currentMode = ""
            }

            currentMode == "delete_lang" -> {
                isMalayalamBook = !isNo 
                if (isYes) isMalayalamBook = true
                currentMode = "delete_search"
                speak("ഒഴിവാക്കേണ്ട പുസ്തകം പറയുക", "delete_prompt")
            }
            currentMode == "delete_search" -> {
                val book = allBooks.find { it.title.lowercase().contains(lower) }
                if (book != null) {
                    deleteBook(book.id)
                    speak("${book.title} ഒഴിവാക്കി.")
                } else speak("പുസ്തകം കണ്ടെത്താനായില്ല.")
                currentMode = ""
            }

            // --- Root Commands ---
            lower.contains("തിരയുക") || lower.contains("search") || lower.contains("തിരച്ചിൽ") || lower.contains("thirayuka") -> {
                currentMode = "search_lang"
                speak("പുസ്തകം മലയാളമാണോ? അതെ അല്ലെങ്കിൽ അല്ല എന്ന് പറയുക", "search_lang_prompt")
            }
            lower.contains("വിവരം") || lower.contains("details") || lower.contains("vivaram") || lower.contains("info") -> {
                currentMode = "details_lang"
                speak("പുസ്തകം മലയാളമാണോ? അതെ അല്ലെങ്കിൽ അല്ല എന്ന് പറയുക", "details_lang_prompt")
            }
            lower.contains("അഭിപ്രായം") || lower.contains("review") || lower.contains("comment") || lower.contains("abhiprayam") -> {
                currentMode = "review_lang"
                speak("പുസ്തകം മലയാളമാണോ? അതെ അല്ലെങ്കിൽ അല്ല എന്ന് പറയുക", "review_lang_prompt")
            }
            lower.contains("ഒഴിവാക്കുക") || lower.contains("delete") || lower.contains("remove") || lower.contains("ozhivakkuka") || lower.contains("ozhivakkua") || lower.contains("ozhivakua") -> {
                if (userRole == "ADMIN") {
                    currentMode = "delete_lang"
                    speak("പുസ്തകം മലയാളമാണോ? അതെ അല്ലെങ്കിൽ അല്ല എന്ന് പറയുക", "delete_lang_prompt")
                } else speak("അഡ്മിന് മാത്രമേ ഇത് സാധിക്കൂ.")
            }
            lower.contains("ചേർക്കുക") || lower.contains("add") || lower.contains("insert") || lower.contains("cherkuka") || lower.contains("cherkka") || lower.contains("cherkkuka") || lower.contains("cherkkukka") -> {
                if (userRole == "ADMIN") {
                    currentMode = "add_lang"
                    speak("ഏത് ഭാഷയാണ്? മലയാളം, ഇംഗ്ലീഷ്, അല്ലെങ്കിൽ മറ്റുള്ളവ?", "add_lang_prompt")
                } else speak("അഡ്മിന് മാത്രമേ ഇത് സാധിക്കൂ.")
            }
            lower.contains("എണ്ണം") || lower.contains("stats") || lower.contains("count") || lower.contains("status") || lower.contains("സ്റ്റാറ്റസ്") || lower.contains("ennam") -> speakStats()
            lower.contains("വായ്പ") || lower.contains("borrow") || lower.contains("lend") || lower.contains("vaypa") -> speak("ലിസ്റ്റിലെ റിക്വസ്റ്റ് ബട്ടൺ അമർത്തുക.")
            lower.contains("തിരിച്ചു") || lower.contains("return") || lower.contains("thirichu") -> speak("ലിസ്റ്റിലെ റിട്ടേൺ ബട്ടൺ അമർത്തുക.")
            
            else -> {
                speak("ക്ഷമിക്കണം, എന്താണെന്ന് മനസ്സിലായില്ല. ഒന്നുകൂടി പറയാമോ?", if(currentMode.isNotEmpty()) currentMode + "_prompt" else "general_prompt")
            }
        }
    }

    private fun speak(text: String, id: String = "general") {
        if (!::tts.isInitialized) {
            Log.e("MainActivity", "TTS not initialized yet")
            return
        }
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
        statusText = text
    }

    private fun validateAndFinalizeVoiceAdd() {
        when {
            voiceAddTitle.isBlank() -> {
                currentMode = "add_title"
                speak("പുസ്തകത്തിന്റെ പേര് പറയാൻ വിട്ടുപോയി. അത് പറയൂ.", "add_title_prompt")
            }
            voiceAddAuthor.isBlank() -> {
                currentMode = "add_author"
                speak("രചയിതാവിന്റെ പേര് പറയാൻ വിട്ടുപോയി. അത് പറയൂ.", "add_author_prompt")
            }
            voiceAddCategory.isBlank() -> {
                currentMode = "add_cat"
                speak("വിഭാഗം പറയാൻ വിട്ടുപോയി. നോവൽ, ചെറുകഥകൾ, ആത്മകഥ, പ്രബന്ധങ്ങൾ, ചിന്തകൾ, അല്ലെങ്കിൽ ബാലസാഹിത്യം?", "add_cat_prompt")
            }
            voiceAddAccList.any { it.isBlank() } -> {
                voiceAddCurrentAccIndex = voiceAddAccList.indexOfFirst { it.isBlank() }
                currentMode = "add_acc_multi"
                speak("${voiceAddCurrentAccIndex + 1}-ാമത്തെ പുസ്തകത്തിന്റെ അക്സെഷൻ നമ്പർ പറയാൻ വിട്ടുപോയി. അത് പറയൂ.", "add_acc_multi_prompt")
            }
            voiceAddIsbnList.any { it.isBlank() } -> {
                voiceAddCurrentAccIndex = voiceAddIsbnList.indexOfFirst { it.isBlank() }
                currentMode = "add_isbn_multi"
                speak("ISBN നമ്പർ പറയാൻ വിട്ടുപോയി. അത് പറയൂ.", "add_isbn_multi_prompt")
            }
            voiceAddLocList.any { it.isBlank() } -> {
                voiceAddCurrentAccIndex = voiceAddLocList.indexOfFirst { it.isBlank() }
                currentMode = "add_loc_multi"
                speak("റാക്ക് നമ്പർ പറയാൻ വിട്ടുപോയി. അത് പറയൂ.", "add_loc_multi_prompt")
            }
            else -> {
                saveFullBook()
            }
        }
    }

    private fun launchSpeechRecognizer() {
        showVoiceDialog = true
        
        val lang = when {
            currentMode == "add_isbn_multi" || currentMode == "add_price_multi" || 
            currentMode == "add_acc_multi" || currentMode == "add_loc_multi" -> "en-US"
            isMalayalamBook -> "ml-IN"
            else -> if (currentMode == "add_lang") "ml-IN" else "en-US"
        }
        
        Log.d("VoiceInput", "Launching Recognizer: lang=$lang, mode=$currentMode")
        statusText = "Listening ($lang)..."

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        }
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { statusText = "🎤 Listening..." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { statusText = "Processing..." }
            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Busy"
                    else -> "Error $error"
                }
                Log.e("VoiceInput", "Error: $msg")
                statusText = "Voice Error: $msg"
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    speak("ക്ഷമിക്കണം, എനിക്ക് കേൾക്കാൻ കഴിഞ്ഞില്ല. ഒന്നുകൂടി പറയാമോ?", if(currentMode.isNotEmpty()) currentMode + "_prompt" else "general_prompt")
                }
            }
            override fun onResults(results: Bundle?) {
                showVoiceDialog = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                if (text.isNotEmpty()) handleVoiceCommand(text)
                else {
                    statusText = "Heard nothing"
                    speak("ക്ഷമിക്കണം, എനിക്ക് കേൾക്കാൻ കഴിഞ്ഞില്ല.")
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun saveFullBook() {
        // Validation check for mandatory fields and copies
        val missingFields = mutableListOf<String>()
        if (voiceAddTitle.isBlank()) missingFields.add("Title")
        if (voiceAddAuthor.isBlank()) missingFields.add("Author")
        if (voiceAddCategory.isBlank()) missingFields.add("Category")
        if (voiceAddAccList.any { it.isBlank() }) missingFields.add("Stock Numbers")
        if (voiceAddIsbnList.any { it.isBlank() }) missingFields.add("ISBN Numbers")
        if (voiceAddLocList.any { it.isBlank() }) missingFields.add("Rack Locations")
        // Price is optional for voice input now

        if (missingFields.isNotEmpty()) {
            val missingStr = missingFields.joinToString(", ")
            speak("ക്ഷമിക്കണം, പുസ്തകത്തിന്റെ വിവരങ്ങൾ അപൂർണ്ണമാണ്. $missingStr എന്നിവ ആവശ്യമാണ്.")
            statusText = "Error: Missing $missingStr"
            return
        }

        if (voiceAddCopies <= 0) {
            speak("ക്ഷമിക്കണം, പുസ്തകത്തിന്റെ എണ്ണം ഒന്നെങ്കിലും ആയിരിക്കണം.")
            statusText = "Error: Number of copies must be at least 1."
            return
        }

        // Capture current voice fields to avoid race conditions during reset
        val language = voiceAddLanguage
        val isMal = language == "Malayalam"
        
        // Title, Author: Malayalam if language is Malayalam, else English
        val title = if (isMal) LanguageUtils.correctMalayalam(voiceAddTitle) else LanguageUtils.transliterateToEnglishPreserveSpaces(voiceAddTitle)
        val author = if (isMal) LanguageUtils.correctMalayalam(voiceAddAuthor) else LanguageUtils.transliterateToEnglishPreserveSpaces(voiceAddAuthor)
        
        val category = voiceAddCategory
        val publisher = if (isMal) LanguageUtils.correctMalayalam(voiceAddPublisher) else LanguageUtils.transliterateToEnglishPreserveSpaces(voiceAddPublisher)
        val year = voiceAddYear.filter { it.isDigit() }.toIntOrNull()
        val copies = voiceAddCopies
        val borrowable = isBorrowable

        // CRITICAL: Capture lists before reset to avoid race conditions in the Firebase listener
        val accList = voiceAddAccList.toList()
        val isbnList = voiceAddIsbnList.toList()
        val locList = voiceAddLocList.toList()
        val priceList = voiceAddPriceList.toList()
        val typeList = voiceAddTypeList.toList()
        val bType = voiceAddBookType

        db.collection("users").document(auth.currentUser?.uid ?: "").get().addOnSuccessListener { doc ->
            val phone = doc.getString("phoneNumber") ?: ""
            
            val booksToSave = accList.mapIndexed { index, acc ->
                val isbn = isbnList.getOrNull(index) ?: ""
                val location = locList.getOrNull(index) ?: ""
                val priceStr = priceList.getOrNull(index) ?: ""
                // Use voiceAddBookType as general type, or fallback to per-copy type
                val bookType = if (bType != "Normal") bType else (typeList.getOrNull(index) ?: "Normal")

                Book(
                    accessionNumber = acc,
                    isbn = if(isbn.isBlank()) null else isbn,
                    callNumber = LanguageUtils.generateCallNumber(title, author),
                    title = title, 
                    author = author, 
                    category = category, 
                    publisherName = if(publisher.isBlank()) null else publisher,
                    yearOfPublication = year,
                    location = location, 
                    price = priceStr.filter { it.isDigit() }.toIntOrNull(),
                    numberOfCopies = 1,
                    canBeBorrowed = borrowable,
                    adminPhoneNumber = phone, 
                    status = "Available", 
                    libraryId = libraryId,
                    language = language,
                    bookType = bookType
                )
            }
            
            lifecycleScope.launch {
                saveMultipleBooksToFirestore(booksToSave)
            }
        }
        currentMode = ""
        resetVoiceFields()
    }

    private fun resetVoiceFields() {
        voiceAddTitle = ""; voiceAddAuthor = ""; voiceAddCategory = ""
        voiceAddPublisher = ""; voiceAddYear = ""; voiceAddCopies = 1
        voiceAddCurrentAccIndex = 0
        voiceAddAccList = mutableListOf(); voiceAddIsbnList = mutableListOf()
        voiceAddLocList = mutableListOf(); voiceAddPriceList = mutableListOf()
        voiceAddTypeList = mutableListOf()
        voiceAddBookType = "Normal"
    }

    private fun resetAddBookState() {
        abIsMalayalam = true
        abIsbn = ""
        abAccessionNumber = ""
        abAccessionNumbers = listOf("")
        abIsbns = listOf("")
        abLocations = listOf("")
        abPrices = listOf("")
        abBookTypes = listOf("Normal")
        abTitle = ""
        abAuthor = ""
        abCategory = ""
        abPublisherName = ""
        abYearOfPublication = ""
        abCallNumber = ""
        abIsCallNumberAuto = true
        abLocation = ""
        abPrice = ""
        abBookType = "Normal"
        abLanguage = "Malayalam"
        abNumberOfCopies = "1"
        abUnavailabilityReason = ""
    }

    private fun startVoiceDictation(callback: (String) -> Unit) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingVoiceAction = { startVoiceDictation(callback) }
            audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            return
        }
        onVoiceResult = callback
        currentMode = "dictation"
        speak(if (isMalayalamBook) "ശ്രദ്ധിക്കുന്നു..." else "Listening...", "dictation_prompt")
    }

    private fun saveWrittenReview(book: Book, comment: String, rating: Int) {
        val review = Review(userUid = auth.currentUser?.uid ?: "", userName = userName, comment = comment, rating = rating)
        val updated = book.reviews.toMutableList().apply { add(review) }
        db.collection("books").document(book.id).update("review", updated)
            .addOnSuccessListener { 
                speak("അഭിപ്രായം രേഖപ്പെടുത്തി.")
                statusText = "Review saved"
            }
    }

    private fun playReviewAudio(url: String) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(url)
                prepareAsync()
                setOnPreparedListener { 
                    start()
                }
                setOnCompletionListener { 
                    release() 
                }
            }
        } catch (e: Exception) { showToast("Could not play audio") }
    }

    private fun speakStats() {
        val total = allBooks.size
        val available = allBooks.count { it.status == "Available" }
        val borrowed = total - available
        speak("ആകെ $total പുസ്തകങ്ങൾ ഉണ്ട്. അതിൽ $available എണ്ണം ലഭ്യമാണ്. $borrowed എണ്ണം വായ്പ നൽകിയിട്ടുണ്ട്.")
    }

    private fun calculateFine(dueDate: Long?): Int {
        if (dueDate == null) return 0
        val overTime = System.currentTimeMillis() - dueDate
        if (overTime <= 0) return 0
        val days = (overTime / (24 * 60 * 60 * 1000)).toInt()
        return days * 1 // ₹1 per day fine
    }

    private fun requestBorrow(book: Book, copyNumber: String) {
        val userUid = auth.currentUser?.uid ?: return
        
        db.collection("borrow_requests")
            .whereEqualTo("userUid", userUid)
            .whereEqualTo("bookId", book.id)
            .whereIn("status", listOf("PENDING", "APPROVED"))
            .get()
            .addOnSuccessListener { existingSnapshot ->
                if (!existingSnapshot.isEmpty) {
                    showToast("You already have an active request for this book.")
                    speak("ഈ പുസ്തകത്തിനായി നിങ്ങൾക്ക് ഇതിനകം ഒരു അപേക്ഷയുണ്ട്.")
                    return@addOnSuccessListener
                }

                db.collection("borrow_requests")
                    .whereEqualTo("userUid", userUid)
                    .whereEqualTo("library_id", libraryId)
                    .whereEqualTo("status", "PENDING")
                    .get()
                    .addOnSuccessListener { snapshot ->
                        if (snapshot.size() >= 5) {
                            showToast("You can only have 5 pending requests at a time.")
                            speak("നിങ്ങൾക്ക് പരമാവധി 5 അപേക്ഷകൾ മാത്രമേ ഒരേസമയം നൽകാൻ കഴിയൂ.")
                        } else {
                            val request = BorrowRequest(
                                accessionNumber = book.accessionNumber,
                                title = book.title,
                                copyNumber = 0, // Not used in unique-doc model
                                libraryId = libraryId,
                                userName = userName,
                                userUid = userUid,
                                bookId = book.id
                            )
                            db.collection("borrow_requests").add(request)
                                .addOnSuccessListener {
                                    showToast("Borrow request sent!")
                                    speak("${book.title} വായ്പയെടുക്കാനുള്ള അപേക്ഷ അയച്ചു.")
                                }
                        }
                    }
            }
    }

    private fun approveBorrowRequest(request: BorrowRequest) {
        if (request.bookId.isBlank()) {
            showToast("Invalid Book ID in request")
            return
        }
        
        db.runTransaction { transaction ->
            val bookRef = db.collection("books").document(request.bookId)
            val doc = transaction.get(bookRef)
            if (!doc.exists()) return@runTransaction "BOOK_NOT_FOUND"
            
            val data = doc.data ?: return@runTransaction "BOOK_NOT_FOUND"
            
            // Manual Deserialization for Transaction Safety
            val accessionNumber = data["accession_number"]?.toString() ?: data["accessionNumber"]?.toString() ?: ""
            val numCopies = (data["number_of_copies"] as? Number)?.toInt() ?: (data["numberOfCopies"] as? Number)?.toInt() ?: 1
            
            // Critical: Get the actual 'copies' list from Firestore data
            val copiesData = data["copies"] as? List<Map<String, Any>>
            var copiesList = copiesData?.map { c ->
                BookCopy(
                    accessionNumber = c["accession_number"] as? String ?: c["accessionNumber"] as? String ?: "",
                    copyNumber = c["copyNumber"] as? String ?: c["copy_number"] as? String ?: "",
                    status = c["status"] as? String ?: "Available",
                    borrowerId = c["borrower_id"] as? String ?: c["borrowerId"] as? String,
                    borrowerName = c["borrowerName"] as? String ?: c["borrowername"] as? String,
                    dueDate = (c["dueDate"] as? Number)?.toLong() ?: (c["due_date"] as? Number)?.toLong()
                )
            } ?: emptyList()

            if (copiesList.isEmpty() && numCopies > 0) {
                val docStatus = data["status"]?.toString() ?: "Available"
                copiesList = (1..numCopies).map { i ->
                    BookCopy(
                        accessionNumber = if (numCopies == 1) accessionNumber else "$accessionNumber-C$i",
                        copyNumber = if (numCopies == 1) accessionNumber else "$accessionNumber-C$i",
                        status = docStatus
                    )
                }
            }

            // Find first available copy
            val availableCopy = copiesList.find { it.status == "Available" }
            if (availableCopy == null) {
                return@runTransaction "NO_COPIES"
            }
            
            val dueDate = System.currentTimeMillis() + (20L * 24 * 60 * 60 * 1000)
            
            // Update the specific copy in the list
            val updatedCopies = copiesList.map { 
                if (it.copyNumber == availableCopy.copyNumber) {
                    it.copy(
                        status = "Borrowed",
                        borrowerId = request.userUid,
                        borrowerName = request.userName,
                        dueDate = dueDate
                    )
                } else it
            }
            
            val availableCount = updatedCopies.count { it.status == "Available" }
            val borrowCount = updatedCopies.count { it.status == "Borrowed" }
            val finalStatus = when {
                availableCount > 0 -> "Available"
                borrowCount > 0 -> "Borrowed"
                else -> "Unavailable"
            }
            
            transaction.update(bookRef, 
                "status", finalStatus,
                "copies", updatedCopies
            )
            
            transaction.update(db.collection("borrow_requests").document(request.id), 
                "status", "APPROVED",
                "due_date", dueDate,
                "approval_date", System.currentTimeMillis()
            )
            
            // If this was the last copy, we'll return a special status to trigger auto-rejection
            if (availableCount == 0) "SUCCESS_LAST_COPY" else "SUCCESS"
        }.addOnSuccessListener { result ->
            when (result) {
                "SUCCESS", "SUCCESS_LAST_COPY" -> {
                    showToast("Request Approved!")
                    speak("${request.userName}-ന്റെ അപേക്ഷ സ്വീകരിച്ചു.")
                    
                    if (result == "SUCCESS_LAST_COPY") {
                        // Auto-reject other pending requests for this book
                        db.collection("borrow_requests")
                            .whereEqualTo("bookId", request.bookId)
                            .whereEqualTo("status", "PENDING")
                            .get()
                            .addOnSuccessListener { snapshot ->
                                val batch = db.batch()
                                snapshot.documents.forEach { doc ->
                                    if (doc.id != request.id) {
                                        batch.update(doc.reference, "status", "REJECTED")
                                    }
                                }
                                batch.commit()
                            }
                    }
                }
                "NO_COPIES" -> {
                    showToast("No available copies to lend")
                    speak("വായ്പ നൽകാൻ പുസ്തകം ലഭ്യമല്ല.")
                }
                else -> {
                    showToast("Failed to approve: Book not found")
                }
            }
        }.addOnFailureListener { e ->
            Log.e("MainActivity", "Approval Transaction Failed", e)
            showToast("Failed to approve request: ${e.message}")
        }
    }

    private fun rejectBorrowRequest(request: BorrowRequest) {
        db.collection("borrow_requests").document(request.id).update("status", "REJECTED")
            .addOnSuccessListener { showToast("Request Rejected") }
    }

    private fun returnBook(book: Book, copyNumber: String) {
        // Find the borrower info. It could be in the copies list or (if unique doc model) 
        // we might just look for the first borrowed copy in this document.
        val borrowedCopy = book.copies.find { it.status == "Borrowed" }
            ?: book.copies.firstOrNull() // Fallback
            
        val borrowerId = borrowedCopy?.borrowerId ?: return
        val targetCopyNumber = borrowedCopy.copyNumber

        db.collection("borrow_requests")
            .whereEqualTo("bookId", book.id)
            .whereEqualTo("userUid", borrowerId)
            .whereEqualTo("status", "APPROVED")
            .get()
            .addOnSuccessListener { snapshot ->
                val requestDoc = snapshot.documents.firstOrNull() ?: return@addOnSuccessListener
                
                db.runTransaction { transaction ->
                    val bookRef = db.collection("books").document(book.id)
                    val currentBook = transaction.get(bookRef).toObject(Book::class.java) ?: return@runTransaction
                    
                    // Update the specific copy to Available
                    val updatedCopies = currentBook.copies.map { 
                        if (it.copyNumber == targetCopyNumber || currentBook.numberOfCopies == 1) {
                            it.copy(
                                status = "Available",
                                borrowerId = null,
                                borrowerName = null,
                                dueDate = null
                            )
                        } else it
                    }
                    
                    val availableCount = updatedCopies.count { it.status == "Available" }
                    val finalStatus = if (availableCount > 0) "Available" else "Borrowed"

                    transaction.update(bookRef, 
                        "status", finalStatus,
                        "copies", updatedCopies
                    )
                    
                    // Mark request as RETURNED
                    transaction.update(requestDoc.reference, 
                        "status", "RETURNED",
                        "return_date", System.currentTimeMillis()
                    )
                }.addOnSuccessListener { 
                    speak("${book.title} തിരിച്ചേൽപ്പിച്ചു.") 
                    showToast("Return accepted")
                }
            }
    }

    private fun importExcel(uri: Uri) {
        lifecycleScope.launch {
            statusText = "Importing books from Excel..."
            val result = ExcelHelper.importBooksFromExcel(this@MainActivity, uri, libraryId)
            importResult = result
            showImportSummary = true
            statusText = "Import completed"
            speak("എക്സൽ ഇറക്കുമതി പൂർത്തിയായി. ${result.imported} പുസ്തകങ്ങൾ ചേർത്തു.")
        }
    }

    private fun downloadImportTemplate() {
        val file = ExcelHelper.generateSampleExcel(this)
        shareFile(file, "Library Import Template")
    }

    private fun exportCatalog() {
        val file = ExcelHelper.exportCatalog(this, allBooks)
        shareFile(file, "Library Catalog")
    }

    private fun exportReviews() {
        val file = ExcelHelper.exportReviews(this, allBooks)
        shareFile(file, "Reviews Report")
    }

    private fun exportBorrowedBooks() {
        db.collection("borrow_requests")
            .whereEqualTo("library_id", libraryId)
            .get().addOnSuccessListener { snapshot ->
                val allRequests = snapshot.documents.mapNotNull { it.toObject(BorrowRequest::class.java) }
                val file = ExcelHelper.exportBorrowedBooks(this, allRequests)
                shareFile(file, "Borrowed Books Report")
            }
    }

    private fun exportStatistics() {
        db.collection("borrow_requests")
            .whereEqualTo("library_id", libraryId)
            .get().addOnSuccessListener { snapshot ->
                val allRequests = snapshot.documents.mapNotNull { it.toObject(BorrowRequest::class.java) }
                val file = ExcelHelper.exportStatistics(this, allBooks, allRequests)
                shareFile(file, "Library Statistics")
            }
    }

    private fun shareFile(file: File?, title: String) {
        if (file == null) {
            showToast("Failed to generate file")
            return
        }
        val uri = ExcelHelper.getUriForFile(this, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share $title"))
    }

    private fun deleteBookWithUndo(book: Book) {
        if (book.copies.any { it.status == "Borrowed" }) {
            showToast("Cannot delete: Some copies are currently lent out.")
            speak("ഈ പുസ്തകം ആരുടെയോ കയ്യിലാണ്. അതിനാൽ ഒഴിവാക്കാൻ കഴിയില്ല.")
            return
        }
        lastDeletedBook = book
        db.collection("books").document(book.id).delete().addOnSuccessListener {
            speak("${book.title} ഒഴിവാക്കി.")
            lifecycleScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "${book.title} deleted",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    lastDeletedBook?.let { restoredBook ->
                        db.collection("books").document(restoredBook.id).set(restoredBook).addOnSuccessListener {
                            showToast("Restored ${restoredBook.title}")
                        }
                    }
                }
            }
        }
    }

    private fun reduceCopyCount(book: Book) {
        if (book.numberOfCopies <= 1) return
        
        // Find an available copy to remove
        val availableCopy = book.copies.findLast { it.status == "Available" }
        if (availableCopy == null) {
            showToast("Cannot reduce: No available copies to remove.")
            return
        }

        val updatedCopies = book.copies.toMutableList().apply { remove(availableCopy) }
        val newTotal = updatedCopies.size
        val availableCount = updatedCopies.count { it.status == "Available" }
        val borrowCount = updatedCopies.count { it.status == "Borrowed" }
        val finalStatus = when {
            availableCount > 0 -> "Available"
            borrowCount > 0 -> "Borrowed"
            else -> "Unavailable"
        }
        
        db.collection("books").document(book.id).update(
            "number_of_copies", newTotal,
            "copies", updatedCopies,
            "status", finalStatus
        ).addOnSuccessListener {
            showToast("Reduced to $newTotal copies")
            speak("ഒരു കോപ്പി ഒഴിവാക്കി.")
        }
    }

    private fun deleteBook(id: String) { 
        db.collection("books").document(id).get().addOnSuccessListener { doc ->
            val book = doc.toObject(Book::class.java)
            if (book != null && book.copies.any { it.status == "Borrowed" }) {
                showToast("Cannot delete: Some copies are currently lent out.")
                speak("ഈ പുസ്തകം ആരുടെയോ കയ്യിലാണ്. അതിനാൽ ഒഴിവാക്കാൻ കഴിയില്ല.")
            } else {
                db.collection("books").document(id).delete().addOnSuccessListener {
                    speak("പുസ്തകം ഒഴിവാക്കി.")
                }
            }
        }
    }

    private fun saveEmailConfig(email: String, pass: String) {
        val data = mapOf("senderEmail" to email, "appPassword" to pass)
        db.collection("config").document("email").set(data)
            .addOnSuccessListener { 
                showToast("Email Config Saved!")
                speak("ഇമെയിൽ ക്രമീകരണം പൂർത്തിയായി")
            }
            .addOnFailureListener { showToast("Failed to save config") }
    }

    private fun deleteReview(book: Book, review: Review) {
        val updatedReviews = book.reviews.filter { it != review }
        db.collection("books").document(book.id).update("review", updatedReviews)
            .addOnSuccessListener {
                speak("അഭിപ്രായം ഒഴിവാക്കി.")
                statusText = "Review deleted"
            }
    }

    private fun nuclearProjectReset() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { statusText = "Performing Nuclear Reset..." }
                
                val collections = listOf("books", "borrow_requests")
                for (col in collections) {
                    var moreDocs = true
                    while (moreDocs) {
                        val snapshot = db.collection(col).limit(500).get().await()
                        if (snapshot.isEmpty) {
                            moreDocs = false
                        } else {
                            val batch = db.batch()
                            snapshot.documents.forEach { batch.delete(it.reference) }
                            batch.commit().await()
                        }
                    }
                }

                withContext(Dispatchers.IO) {
                    roomDb.clearAllTables()
                }

                withContext(Dispatchers.Main) {
                    showToast("Project Factory Reset Complete: Database is Empty.")
                    speak("ഡാറ്റാബേസ് പൂർണ്ണമായും ഒഴിവാക്കി.")
                    statusText = "Ready"
                }
            } catch (e: Exception) {
                Log.e("NuclearWipe", "Failed", e)
            }
        }
    }

    private fun wipeLibraryData() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { statusText = "Wiping Library Data..." }
                
                val collections = listOf("books", "borrow_requests")
                for (col in collections) {
                    val libIdField = if (col == "books") "library_id" else "library_id"
                    val snapshot = db.collection(col)
                        .whereEqualTo(libIdField, libraryId)
                        .get().await()
                    
                    if (!snapshot.isEmpty) {
                        val batch = db.batch()
                        snapshot.documents.forEach { batch.delete(it.reference) }
                        batch.commit().await()
                    }
                }

                withContext(Dispatchers.IO) {
                    // Also clear relevant Room data
                    roomDb.bookDao().deleteByLibraryId(libraryId)
                    roomDb.borrowRequestDao().deleteByLibraryId(libraryId)
                }

                withContext(Dispatchers.Main) {
                    showToast("Library data wiped successfully.")
                    speak("ഈ ലൈബ്രറിയിലെ വിവരങ്ങൾ പൂർണ്ണമായും ഒഴിവാക്കി.")
                    statusText = "Ready"
                }
            } catch (e: Exception) {
                Log.e("WipeData", "Failed", e)
                withContext(Dispatchers.Main) { showToast("Failed to wipe data.") }
            }
        }
    }

    private fun wipeAllCollections() {
        // Warning: This clears everything for a total fresh start
        listOf("books", "borrow_requests", "users").forEach { col ->
            db.collection(col).get().addOnSuccessListener { snapshot ->
                val batch = db.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit()
            }
        }
    }

    private fun updateBookInFirestore(book: Book) {
        db.collection("books").document(book.id).set(book)
            .addOnSuccessListener {
                showToast("Book updated successfully")
                speak("പുസ്തക വിവരങ്ങൾ പുതുക്കി.")
            }
            .addOnFailureListener { showToast("Update failed") }
    }

    private suspend fun saveMultipleBooksToFirestore(books: List<Book>) {
        if (books.isEmpty()) return
        
        statusText = "Saving books..."
        
        try {
            val batch = db.batch()
            
            for (book in books) {
                // Check uniqueness of Stock Number globally
                val snapshot = db.collection("books")
                    .whereEqualTo("library_id", libraryId)
                    .whereEqualTo("accession_number", book.accessionNumber)
                    .get().await()
                
                if (!snapshot.isEmpty) {
                    withContext(Dispatchers.Main) {
                        showToast("Book with Stock Number ${book.accessionNumber} already exists!")
                        speak("ഈ സ്റ്റോക്ക് നമ്പർ നിലവിലുണ്ട്.")
                    }
                    statusText = "Save failed: Duplicate Stock Number"
                    return
                }
                
                val docRef = db.collection("books").document()
                val finalBook = book.copy(id = docRef.id)
                batch.set(docRef, finalBook)
            }
            
            batch.commit().await()
            
            withContext(Dispatchers.Main) {
                showToast("Saved ${books.size} items to library.")
                speak("പുസ്തകങ്ങൾ വിജയകരമായി ചേർത്തു.")
                statusText = "Ready"
                resetAddBookState()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Save error", e)
            withContext(Dispatchers.Main) { showToast("Error saving books.") }
            statusText = "Error saving books"
        }
    }

    private fun readBookDetails(query: String) {
        val lower = query.lowercase()
        val results = allBooks.filter { 
            it.title.lowercase().contains(lower) || 
            it.author.lowercase().contains(lower) ||
            it.accessionNumber.lowercase().contains(lower) ||
            it.isbn?.lowercase()?.contains(lower) == true
        }
        if (results.isNotEmpty()) {
            val grouped = results.groupBy { it.title.lowercase() to it.author.lowercase() }
            if (grouped.size == 1) {
                val book = grouped.values.first().first()
                val totalCopies = grouped.values.first().sumOf { it.numberOfCopies }
                val availableCount = grouped.values.first().sumOf { it.copies.count { c -> c.status == "Available" } }
                
                val statusMsg = if(availableCount > 0) "$availableCount എണ്ണം ലഭ്യമാണ്" else "നിലവിൽ ലഭ്യമല്ല"
                
                val details = StringBuilder()
                details.append("${book.title} കണ്ടെത്തി. ")
                details.append("രചയിതാവ്: ${book.author}. ")
                details.append("വിഭാഗം: ${book.category}. ")
                details.append("റാക്ക്: ${book.location}. ")
                details.append("ആകെ $totalCopies കോപ്പികൾ ഉണ്ട്. അതിൽ $statusMsg. ")
                
                book.isbn?.let { 
                    if(it.isNotBlank()) {
                        val digits = it.map { c -> if(c.isDigit()) "$c " else c }.joinToString("")
                        details.append("ISBN: $digits. ")
                    }
                }
                book.publisherName?.let { if(it.isNotBlank()) details.append("Publisher: $it. ") }
                book.yearOfPublication?.let { details.append("Year: $it. ") }
                book.price?.let { details.append("വില: $it രൂപ. ") }
                
                speak(details.toString())
            } else {
                speak("${grouped.size} വ്യത്യസ്ത പുസ്തകങ്ങൾ കണ്ടെത്തി.")
            }
        } else speak("പുസ്തകം കണ്ടെത്താനായില്ല.")
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        if (::tts.isInitialized) {
            tts.shutdown()
        }
        mediaPlayer?.release()
        super.onDestroy()
    }

    private fun updateDarkMode(enabled: Boolean) {
        isDarkMode = enabled
        getSharedPreferences("user_prefs", MODE_PRIVATE).edit()
            .putBoolean("isDarkMode", enabled)
            .apply()
    }

    @Composable
    fun SettingsDialog(onDismiss: () -> Unit) {
        val mainColor = if (isDarkMode) Color(0xFFD7CCC8) else Color(0xFF452719)
        val cardBg = if (isDarkMode) Color(0xFF1E1916) else Color.White
        val textColor = if (isDarkMode) Color(0xFFE0E0E0) else Color(0xFF452719)
        val surfaceBeige = if (isDarkMode) Color(0xFF2D2420) else Color(0xFFF3EAE2)

        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    
                    if (userRole == "ADMIN") {
                        Spacer(modifier = Modifier.height(12.dp))
                        var showConfirmWipe by remember { mutableStateOf(false) }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Red.copy(alpha = 0.1f))
                                .clickable { showConfirmWipe = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DeleteForever, null, tint = Color.Red, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Wipe Library Data", fontWeight = FontWeight.Medium, color = Color.Red)
                        }

                        if (showConfirmWipe) {
                            AlertDialog(
                                onDismissRequest = { showConfirmWipe = false },
                                containerColor = cardBg,
                                title = { Text("Wipe Everything?", color = textColor, fontWeight = FontWeight.Bold) },
                                text = { Text("This will delete ALL books, members, and records for this library. This action is permanent and cannot be undone.", color = textColor.copy(alpha = 0.7f)) },
                                confirmButton = {
                                    Button(
                                        onClick = { 
                                            showConfirmWipe = false
                                            wipeLibraryData() 
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                                    ) { Text("Wipe All Data", color = Color.White) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showConfirmWipe = false }) {
                                        Text("Cancel", color = mainColor)
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Dark Mode Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(surfaceBeige)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                null,
                                tint = mainColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Dark Mode", fontWeight = FontWeight.Medium, color = mainColor)
                        }
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = { updateDarkMode(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = mainColor,
                                checkedTrackColor = mainColor.copy(alpha = 0.5f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = mainColor)
                    ) {
                        Text("Close", color = if (isDarkMode) Color(0xFF452719) else Color.White)
                    }
                }
            }
        }
    }

    @Composable
    fun ReviewDialog(bookTitle: String, onDismiss: () -> Unit, onVoiceInput: ((String) -> Unit) -> Unit = {}, onSave: (String, Int) -> Unit) {
        var comment by remember { mutableStateOf("") }
        var rating by remember { mutableIntStateOf(5) }
        val mainColor = if (isDarkMode) Color(0xFFD7CCC8) else Color(0xFF452719)
        val cardBg = if (isDarkMode) Color(0xFF1E1916) else Color.White
        val textColor = if (isDarkMode) Color(0xFFE0E0E0) else Color(0xFF1E293B)

        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Review: $bookTitle",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Text("Your Rating", style = MaterialTheme.typography.labelMedium, color = if (isDarkMode) Color.LightGray else Color(0xFF64748B))
                    Row(modifier = Modifier.padding(vertical = 8.dp)) {
                        repeat(5) { i ->
                            Icon(
                                imageVector = if (i < rating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(36.dp).clickable { rating = i + 1 }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text("Share your thoughts...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = mainColor,
                            unfocusedTextColor = mainColor,
                            focusedBorderColor = mainColor,
                            unfocusedBorderColor = if (isDarkMode) Color.Gray else Color.LightGray,
                            focusedLabelColor = mainColor,
                            unfocusedLabelColor = Color.Gray
                        ),
                        trailingIcon = {
                            IconButton(onClick = { onVoiceInput { comment = it } }) {
                                Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = mainColor)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cancel", color = if (isDarkMode) Color.LightGray else Color(0xFF64748B)) }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onSave(comment, rating) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = mainColor, contentColor = if (isDarkMode) Color(0xFF452719) else Color.White)
                        ) { Text("Submit Review") }
                    }
                }
            }
        }
    }

    @Composable
    fun ImportSummaryDialog(result: ExcelHelper.ImportResult, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Import Successful", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Total Rows: ${result.total}")
                    Text("Imported: ${result.imported}", color = Color(0xFF2E7D32))
                    Text("Skipped: ${result.skipped}", color = Color.Red)
                }
            },
            confirmButton = {
                Button(onClick = onDismiss) { Text("OK") }
            }
        )
    }

    @Composable
    fun BorrowersListDialog(
        borrowers: List<BorrowRequest>,
        onDismiss: () -> Unit
    ) {
        val mainColor = if (isDarkMode) Color(0xFFD7CCC8) else Color(0xFF452719)
        val cardBg = if (isDarkMode) Color(0xFF1E1916) else Color.White
        val textColor = if (isDarkMode) Color(0xFFE0E0E0) else Color.Black

        Dialog(onDismissRequest = onDismiss) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = cardBg)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Current Borrowers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = textColor)
                    Spacer(modifier = Modifier.height(16.dp))
                    if (borrowers.isEmpty()) {
                        Text("No active borrowers for this book.", color = textColor)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                            items(borrowers) { req ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = if (isDarkMode) Color(0xFF2D2420) else MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(req.userName, fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.Black)
                                        req.approvalDate?.let {
                                            Text("Borrowed on: ${java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(java.util.Date(it))}", fontSize = 12.sp, color = if (isDarkMode) Color.LightGray else Color.Gray)
                                        }
                                        req.dueDate?.let {
                                            Text("Due date: ${java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(java.util.Date(it))}", fontSize = 12.sp, color = Color.Red)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close", color = mainColor) }
                }
            }
        }
    }

    @Composable
    fun UserNotificationsDialog(
        notifications: List<BorrowRequest>,
        onDismiss: () -> Unit
    ) {
        val alertNotifications = notifications.filter { it.status == "APPROVED" }
        val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val mainColor = if (isDarkMode) Color(0xFFD7CCC8) else Color(0xFF452719)
        val cardBg = if (isDarkMode) Color(0xFF1E1916) else Color.White
        val textColor = if (isDarkMode) Color(0xFFE0E0E0) else Color.Black

        Dialog(onDismissRequest = onDismiss) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = cardBg)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Your Notifications", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = textColor)
                    Spacer(modifier = Modifier.height(16.dp))
                    if (alertNotifications.isEmpty()) {
                        Text("No new notifications", color = textColor)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                            items(alertNotifications) { req ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = if (isDarkMode) Color(0xFF2D2420) else Color(0xFFE3F2FD))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(req.title, fontWeight = FontWeight.Bold, color = if (isDarkMode) mainColor else Color(0xFF0D47A1))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Status: APPROVED", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))

                                        req.approvalDate?.let { approvedAt ->
                                            Text("Accepted on: ${dateFormat.format(java.util.Date(approvedAt))}", fontSize = 12.sp, color = if (isDarkMode) Color.LightGray else Color.Gray)
                                        }

                                        req.dueDate?.let { dueDate ->
                                            Text("Please return by: ${dateFormat.format(java.util.Date(dueDate))}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close", color = mainColor) }
                }
            }
        }
    }

    @Composable
    fun BorrowRequestsDialog(
        requests: List<BorrowRequest>,
        onDismiss: () -> Unit,
        onApprove: (BorrowRequest) -> Unit,
        onReject: (BorrowRequest) -> Unit
    ) {
        val mainColor = if (isDarkMode) Color(0xFFD7CCC8) else Color(0xFF452719)
        val cardBg = if (isDarkMode) Color(0xFF1E1916) else Color.White
        val textColor = if (isDarkMode) Color(0xFFE0E0E0) else Color.Black

        Dialog(onDismissRequest = onDismiss) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = cardBg)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Borrow Requests", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = textColor)
                    Spacer(modifier = Modifier.height(16.dp))
                    if (requests.isEmpty()) {
                        Text("No pending requests", color = textColor)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                            items(requests) { req ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = if (isDarkMode) Color(0xFF2D2420) else MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(req.title, fontWeight = FontWeight.Bold, color = if (isDarkMode) Color.White else Color.Black)
                                        Text("From: ${req.userName}", style = MaterialTheme.typography.bodySmall, color = if (isDarkMode) Color.LightGray else Color.Gray)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                            TextButton(onClick = { onReject(req) }) { Text("Reject", color = Color.Red) }
                                            Button(
                                                onClick = { onApprove(req) },
                                                colors = ButtonDefaults.buttonColors(containerColor = mainColor, contentColor = if (isDarkMode) Color(0xFF452719) else Color.White)
                                            ) { Text("Approve") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close", color = mainColor) }
                }
            }
        }
    }

    @Composable
    fun EditBookDialog(book: Book, groupTotal: Int, onDismiss: () -> Unit, onVoiceInput: (Boolean, (String) -> Unit) -> Unit = { _, _ -> }, onSave: (Book) -> Unit, onSaveMulti: (List<Book>) -> Unit) {
        var isMalayalam by remember { mutableStateOf(true) }
        var isbn by remember { mutableStateOf(book.isbn ?: "") }
        var accessionNumber by remember { mutableStateOf(book.accessionNumber) }
        var title by remember { mutableStateOf(book.title) }
        var author by remember { mutableStateOf(book.author) }
        var category by remember { mutableStateOf(book.category) }
        var publisherName by remember { mutableStateOf(book.publisherName ?: "") }
        var yearOfPublication by remember { mutableStateOf(book.yearOfPublication?.toString() ?: "") }
        var callNumber by remember { mutableStateOf(book.callNumber ?: "") }
        var isCallNumberAuto by remember { mutableStateOf(book.callNumber.isNullOrBlank()) }
        var location by remember { mutableStateOf(book.location) }
        var price by remember { mutableStateOf(book.price?.toString() ?: "") }
        var language by remember { mutableStateOf(book.language) }
        var bookType by remember { mutableStateOf(book.bookType) }
        var canBeBorrowed by remember { mutableStateOf(book.canBeBorrowed) }
        var numberOfCopies by remember { mutableStateOf(groupTotal.toString()) }
        var unavailabilityReason by remember { mutableStateOf(book.unavailabilityReason ?: "") }

        // State for adding multiple copies during edit
        var extraAccessionNumbers by remember { mutableStateOf(listOf<String>()) }
        var extraIsbns by remember { mutableStateOf(listOf<String>()) }
        var extraLocations by remember { mutableStateOf(listOf<String>()) }
        var extraPrices by remember { mutableStateOf(listOf<String>()) }
        var extraBookTypes by remember { mutableStateOf(listOf<String>()) }

        // Auto-suggest Call Number on edit if Title/Author changes
        LaunchedEffect(title, author) {
            if (isCallNumberAuto) {
                val suggested = LanguageUtils.generateCallNumber(title, author)
                if (suggested.isNotEmpty()) {
                    callNumber = suggested
                }
            }
        }

        val mainColor = if (isDarkMode) Color(0xFFD7CCC8) else Color(0xFF452719)
        val cardBg = if (isDarkMode) Color(0xFF1E1916) else Color.White
        val borderColor = if (isDarkMode) Color.Gray.copy(alpha = 0.5f) else Color(0xFFD7CCC8)
        val surfaceBeige = if (isDarkMode) Color(0xFF2D2420) else Color(0xFFF3EAE2)

        Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = true, 
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.95f)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                    Text("Edit Book", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = mainColor)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Voice Language:", style = MaterialTheme.typography.labelMedium, color = if (isDarkMode) Color.LightGray else Color.Gray)
                        LanguageToggle(
                            isMalayalam = isMalayalam,
                            onToggle = { isMalayalam = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    EditVoiceInputField(value = isbn, onValueChange = { isbn = it }, label = "ISBN", onVoiceInput = { onVoiceInput(false, it) }, borderColor = borderColor)
                    OutlinedTextField(
                        value = accessionNumber,
                        onValueChange = { accessionNumber = it },
                        label = { Text("Stock Number") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = mainColor,
                            unfocusedTextColor = mainColor,
                            focusedBorderColor = mainColor,
                            unfocusedBorderColor = borderColor,
                            focusedLabelColor = mainColor,
                            unfocusedLabelColor = Color.Gray
                        )
                    )
                    EditVoiceInputField(value = title, onValueChange = { title = it }, label = "Title", onVoiceInput = { onVoiceInput(isMalayalam, it) }, borderColor = borderColor)
                    EditVoiceInputField(value = author, onValueChange = { author = it }, label = "Author", onVoiceInput = { onVoiceInput(isMalayalam, it) }, borderColor = borderColor)

                    // Category Dropdown
                    var catExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { catExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, null, tint = mainColor)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = mainColor,
                                unfocusedTextColor = mainColor,
                                focusedBorderColor = mainColor,
                                unfocusedBorderColor = borderColor,
                                focusedLabelColor = mainColor,
                                unfocusedLabelColor = Color.Gray
                            )
                        )
                        DropdownMenu(
                            expanded = catExpanded,
                            onDismissRequest = { catExpanded = false },
                            modifier = Modifier.background(cardBg)
                        ) {
                            CategoryConstants.standardCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat, color = mainColor) },
                                    onClick = {
                                        category = cat
                                        catExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Language Dropdown
                    var langExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        OutlinedTextField(
                            value = language,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Language") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { langExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, null, tint = mainColor)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = mainColor,
                                unfocusedTextColor = mainColor,
                                focusedBorderColor = mainColor,
                                unfocusedBorderColor = borderColor,
                                focusedLabelColor = mainColor,
                                unfocusedLabelColor = Color.Gray
                            )
                        )
                        DropdownMenu(
                            expanded = langExpanded,
                            onDismissRequest = { langExpanded = false },
                            modifier = Modifier.background(cardBg)
                        ) {
                            listOf("Malayalam", "English", "Others").forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang, color = mainColor) },
                                    onClick = {
                                        language = lang
                                        langExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Book Type Dropdown
                    var typeExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        OutlinedTextField(
                            value = bookType,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Book Type") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { typeExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, null, tint = mainColor)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = mainColor,
                                unfocusedTextColor = mainColor,
                                focusedBorderColor = mainColor,
                                unfocusedBorderColor = borderColor,
                                focusedLabelColor = mainColor,
                                unfocusedLabelColor = Color.Gray
                            )
                        )
                        DropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false },
                            modifier = Modifier.background(cardBg)
                        ) {
                            listOf("Normal", "Reference").forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type, color = mainColor) },
                                    onClick = {
                                        bookType = type
                                        typeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    EditVoiceInputField(value = publisherName, onValueChange = { publisherName = it }, label = "Publisher", onVoiceInput = { onVoiceInput(isMalayalam, it) }, borderColor = borderColor)
                    EditVoiceInputField(value = yearOfPublication, onValueChange = { if (it.all { char -> char.isDigit() }) yearOfPublication = it }, label = "Year of Publication", onVoiceInput = { onVoiceInput(false, it) }, borderColor = borderColor)
                    EditVoiceInputField(
                        value = callNumber, 
                        onValueChange = { 
                            callNumber = it
                            isCallNumberAuto = false 
                        }, 
                        label = "Call Number", 
                        onVoiceInput = { onVoiceInput(false, {
                            callNumber = it
                            isCallNumberAuto = false
                        }) }, 
                        borderColor = borderColor
                    )
                    EditVoiceInputField(value = location, onValueChange = { location = it }, label = "Rack Number", onVoiceInput = { onVoiceInput(false, it) }, borderColor = borderColor)
                    EditVoiceInputField(value = price, onValueChange = { if (it.all { char -> char.isDigit() || char == '.' }) price = it }, label = "Price", onVoiceInput = { onVoiceInput(false, it) }, borderColor = borderColor)

                    OutlinedTextField(
                        value = numberOfCopies,
                        onValueChange = { 
                            if (it.all { char -> char.isDigit() }) {
                                val newCount = it.toIntOrNull() ?: groupTotal
                                if (newCount < groupTotal && newCount != 0) {
                                    showToast("To reduce copies, please use the Delete option in the library list.")
                                } else {
                                    numberOfCopies = it 
                                    if (newCount > groupTotal) {
                                        val needed = newCount - groupTotal
                                        extraAccessionNumbers = (1..needed).map { "" }
                                        extraIsbns = (1..needed).map { isbn }
                                        extraLocations = (1..needed).map { location }
                                        extraPrices = (1..needed).map { price }
                                        extraBookTypes = (1..needed).map { bookType }
                                    } else {
                                        extraAccessionNumbers = emptyList()
                                    }
                                }
                            }
                        },
                        label = { Text("Total Number of Copies") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = mainColor,
                            unfocusedTextColor = mainColor,
                            focusedBorderColor = mainColor,
                            unfocusedBorderColor = borderColor,
                            focusedLabelColor = mainColor,
                            unfocusedLabelColor = Color.Gray
                        )
                    )

                    if (extraAccessionNumbers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Details for New Copies", style = MaterialTheme.typography.titleMedium, color = mainColor, fontWeight = FontWeight.Bold)
                        
                        extraAccessionNumbers.forEachIndexed { index, _ ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = surfaceBeige.copy(alpha = 0.3f)),
                                border = BorderStroke(0.5.dp, borderColor.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("New Copy #${index + 1}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = mainColor)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    val accVal = extraAccessionNumbers[index]
                                    val isDup = accVal.isNotBlank() && (allBooks.any { it.accessionNumber == accVal } || (accVal == accessionNumber) || extraAccessionNumbers.filterIndexed { i, s -> i != index && s == accVal }.isNotEmpty())

                                    EditVoiceInputField(
                                        value = accVal,
                                        onValueChange = { v ->
                                            extraAccessionNumbers = extraAccessionNumbers.toMutableList().apply { this[index] = v }
                                        },
                                        label = "Stock Number",
                                        onVoiceInput = { onVoiceInput(false, it) },
                                        borderColor = borderColor,
                                        isError = isDup,
                                        errorText = if (isDup) "Stock Number already exists" else ""
                                    )
                                    
                                    EditVoiceInputField(
                                        value = extraIsbns[index],
                                        onValueChange = { v -> extraIsbns = extraIsbns.toMutableList().apply { this[index] = v } },
                                        label = "ISBN",
                                        onVoiceInput = { onVoiceInput(false, it) },
                                        borderColor = borderColor
                                    )

                                    EditVoiceInputField(
                                        value = extraLocations[index],
                                        onValueChange = { v -> extraLocations = extraLocations.toMutableList().apply { this[index] = v } },
                                        label = "Rack Number",
                                        onVoiceInput = { onVoiceInput(false, it) },
                                        borderColor = borderColor
                                    )
                                }
                            }
                        }
                    }

                    if (numberOfCopies.toIntOrNull() == 0) {
                        EditVoiceInputField(
                            value = unavailabilityReason,
                            onValueChange = { unavailabilityReason = it },
                            label = "Reason for Unavailability (Mandatory)",
                            onVoiceInput = { onVoiceInput(isMalayalam, it) },
                            borderColor = Color.Red
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = if (numberOfCopies.toIntOrNull() == 0) false else canBeBorrowed,
                            onCheckedChange = { if (numberOfCopies.toIntOrNull() != 0) canBeBorrowed = it },
                            enabled = numberOfCopies.toIntOrNull() != 0,
                            colors = CheckboxDefaults.colors(checkedColor = mainColor)
                        )
                        Text("Can be borrowed", color = if (numberOfCopies.toIntOrNull() == 0) Color.Gray else mainColor)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cancel", color = if (isDarkMode) Color.LightGray else Color.Gray) }
                        Button(
                            onClick = {
                                val newCount = numberOfCopies.toIntOrNull() ?: groupTotal
                                
                                if (newCount == 0 && unavailabilityReason.isBlank()) {
                                    showToast("Please provide a reason for unavailability")
                                    return@Button
                                }

                                if (newCount < groupTotal && newCount != 0) {
                                    showToast("To reduce copies, please use the Delete option in the library list.")
                                    return@Button
                                }

                                if (extraAccessionNumbers.any { it.isBlank() }) {
                                    showToast("Please fill all new Stock Numbers")
                                    return@Button
                                }

                                val isMal = language == "Malayalam"
                                val finalTitle = if (isMal) LanguageUtils.correctMalayalam(title.trim()) else LanguageUtils.transliterateToEnglishPreserveSpaces(title)
                                val finalAuthor = if (isMal) LanguageUtils.correctMalayalam(author.trim()) else LanguageUtils.transliterateToEnglishPreserveSpaces(author)
                                
                                // Update existing book
                                val updatedBook = book.copy(
                                    accessionNumber = accessionNumber,
                                    isbn = if(isbn.isBlank()) null else isbn,
                                    title = finalTitle, author = finalAuthor, category = category, 
                                    publisherName = if(publisherName.isBlank()) null else publisherName,
                                    yearOfPublication = yearOfPublication.toIntOrNull(),
                                    callNumber = if(callNumber.isBlank()) null else callNumber,
                                    location = location,
                                    price = price.toIntOrNull()?.let { it },
                                    canBeBorrowed = if (newCount == 0) false else canBeBorrowed,
                                    numberOfCopies = 1, // Single doc model
                                    status = if (newCount == 0) "Unavailable: $unavailabilityReason" else book.status,
                                    language = language,
                                    bookType = bookType,
                                    unavailabilityReason = if (newCount == 0) unavailabilityReason else null
                                )

                                onSave(updatedBook)

                                // Add new copies as separate books
                                if (extraAccessionNumbers.isNotEmpty()) {
                                    val newBooks = extraAccessionNumbers.mapIndexed { index, acc ->
                                        Book(
                                            accessionNumber = acc,
                                            isbn = if(extraIsbns[index].isBlank()) null else extraIsbns[index],
                                            title = finalTitle,
                                            author = finalAuthor,
                                            category = category,
                                            publisherName = updatedBook.publisherName,
                                            yearOfPublication = updatedBook.yearOfPublication,
                                            callNumber = updatedBook.callNumber,
                                            location = extraLocations[index],
                                            price = extraPrices[index].toIntOrNull(),
                                            numberOfCopies = 1,
                                            status = "Available",
                                            canBeBorrowed = true,
                                            libraryId = libraryId,
                                            language = language,
                                            bookType = extraBookTypes[index]
                                        )
                                    }
                                    onSaveMulti(newBooks)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = mainColor, contentColor = if (isDarkMode) Color(0xFF452719) else Color.White)
                        ) { Text("Update") }
                    }
                }
            }
        }
    }

    @Composable
    fun AddBookDialog(onDismiss: () -> Unit, onVoiceInput: (Boolean, (String) -> Unit) -> Unit = { _, _ -> }, onSave: (List<Book>) -> Unit) {
        // Auto-generate Call Number: VAS/M format
        LaunchedEffect(abTitle, abAuthor) {
            if (abIsCallNumberAuto && abTitle.isNotBlank() && abAuthor.isNotBlank()) {
                val suggested = LanguageUtils.generateCallNumber(abTitle, abAuthor)
                if (suggested.isNotEmpty()) {
                    abCallNumber = suggested
                }
            }
        }

        val mainColor = if (isDarkMode) Color(0xFFD7CCC8) else Color(0xFF452719)
        val cardBg = if (isDarkMode) Color(0xFF1E1916) else Color.White
        val textColor = if (isDarkMode) Color(0xFFE0E0E0) else Color(0xFF1E293B)
        val surfaceBeige = if (isDarkMode) Color(0xFF2D2420) else Color(0xFFF3EAE2)
        val borderColor = if (isDarkMode) Color.Gray.copy(alpha = 0.5f) else Color.LightGray

        Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = true, 
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false // Allows us to use full screen width
            )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.95f) // Take up most of the height
                    .padding(16.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Add New Book",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor
                    )
                    Text(
                        text = "Enter a unique Stock Number for each copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDarkMode) Color.LightGray else Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Voice Language:", style = MaterialTheme.typography.labelMedium, color = textColor)
                        LanguageToggle(
                            isMalayalam = abIsMalayalam,
                            onToggle = { abIsMalayalam = it }
                        )
                    }

                    OutlinedTextField(
                        value = abNumberOfCopies,
                        onValueChange = { 
                            if (it.all { char -> char.isDigit() }) {
                                abNumberOfCopies = it
                                val count = it.toIntOrNull() ?: 1
                                if (count > 0) {
                                    val accList = abAccessionNumbers.toMutableList()
                                    while (accList.size < count) accList.add("")
                                    while (accList.size > count) accList.removeAt(accList.size - 1)
                                    abAccessionNumbers = accList

                                    val isbnList = abIsbns.toMutableList()
                                    while (isbnList.size < count) isbnList.add("")
                                    while (isbnList.size > count) isbnList.removeAt(isbnList.size - 1)
                                    abIsbns = isbnList

                                    val locList = abLocations.toMutableList()
                                    while (locList.size < count) locList.add("")
                                    while (locList.size > count) locList.removeAt(locList.size - 1)
                                    abLocations = locList

                                    val priceList = abPrices.toMutableList()
                                    while (priceList.size < count) priceList.add("")
                                    while (priceList.size > count) priceList.removeAt(priceList.size - 1)
                                    abPrices = priceList

                                    val typeList = abBookTypes.toMutableList()
                                    while (typeList.size < count) typeList.add("Normal")
                                    while (typeList.size > count) typeList.removeAt(typeList.size - 1)
                                    abBookTypes = typeList
                                }
                            }
                        },
                        label = { Text("Number of Copies") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = mainColor,
                            unfocusedTextColor = mainColor,
                            focusedBorderColor = mainColor,
                            unfocusedBorderColor = borderColor,
                            focusedLabelColor = mainColor,
                            unfocusedLabelColor = Color.Gray
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Copy Details", style = MaterialTheme.typography.titleMedium, color = mainColor, fontWeight = FontWeight.Bold)
                    
                    abAccessionNumbers.forEachIndexed { index, _ ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = surfaceBeige.copy(alpha = 0.3f)),
                            border = BorderStroke(0.5.dp, borderColor.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Copy #${index + 1}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = mainColor)
                                Spacer(modifier = Modifier.height(8.dp))

                                val accValue = abAccessionNumbers[index]
                                val isDuplicateInDB = accValue.isNotBlank() && allBooks.any { it.accessionNumber == accValue }
                                val isDuplicateInForm = accValue.isNotBlank() && abAccessionNumbers.filterIndexed { i, s -> i != index && s == accValue }.isNotEmpty()
                                val isError = isDuplicateInDB || isDuplicateInForm
                                
                                EditVoiceInputField(
                                    value = accValue,
                                    onValueChange = { newValue ->
                                        val newList = abAccessionNumbers.toMutableList()
                                        newList[index] = newValue
                                        abAccessionNumbers = newList
                                    },
                                    label = "Stock Number",
                                    onVoiceInput = { onVoiceInput(false, it) },
                                    borderColor = borderColor,
                                    isError = isError,
                                    errorText = when {
                                        isDuplicateInDB -> "Book already exists in library"
                                        isDuplicateInForm -> "Duplicate Stock Number in form"
                                        else -> ""
                                    }
                                )

                                EditVoiceInputField(
                                    value = abIsbns[index],
                                    onValueChange = { newValue ->
                                        val newList = abIsbns.toMutableList()
                                        newList[index] = newValue
                                        abIsbns = newList
                                    },
                                    label = "ISBN",
                                    onVoiceInput = { onVoiceInput(false, it) },
                                    borderColor = borderColor
                                )

                                EditVoiceInputField(
                                    value = abLocations[index],
                                    onValueChange = { newValue ->
                                        val newList = abLocations.toMutableList()
                                        newList[index] = newValue
                                        abLocations = newList
                                    },
                                    label = "Rack Location",
                                    onVoiceInput = { onVoiceInput(false, it) },
                                    borderColor = borderColor
                                )

                                EditVoiceInputField(
                                    value = abPrices[index],
                                    onValueChange = { newValue ->
                                        val newList = abPrices.toMutableList()
                                        newList[index] = newValue
                                        abPrices = newList
                                    },
                                    label = "Price",
                                    onVoiceInput = { onVoiceInput(false, it) },
                                    borderColor = borderColor
                                )

                                // Book Type Dropdown per copy
                                var typeExpanded by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    OutlinedTextField(
                                        value = abBookTypes[index],
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Book Type") },
                                        modifier = Modifier.fillMaxWidth(),
                                        trailingIcon = {
                                            IconButton(onClick = { typeExpanded = true }) {
                                                Icon(Icons.Default.ArrowDropDown, null, tint = mainColor)
                                            }
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = mainColor,
                                            unfocusedTextColor = mainColor,
                                            focusedBorderColor = mainColor,
                                            unfocusedBorderColor = borderColor,
                                            focusedLabelColor = mainColor,
                                            unfocusedLabelColor = Color.Gray
                                        )
                                    )
                                    DropdownMenu(
                                        expanded = typeExpanded,
                                        onDismissRequest = { typeExpanded = false },
                                        modifier = Modifier.background(cardBg)
                                    ) {
                                        listOf("Normal", "Reference").forEach { type ->
                                            DropdownMenuItem(
                                                text = { Text(type, color = mainColor) },
                                                onClick = {
                                                    val newList = abBookTypes.toMutableList()
                                                    newList[index] = type
                                                    abBookTypes = newList
                                                    typeExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    VoiceInputField(value = abTitle, onValueChange = { abTitle = it }, label = "Book Title", onVoiceInput = { onVoiceInput(abIsMalayalam, it) })
                    VoiceInputField(value = abAuthor, onValueChange = { abAuthor = it }, label = "Author Name", onVoiceInput = { onVoiceInput(abIsMalayalam, it) })

                    // Language Dropdown
                    var langExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        OutlinedTextField(
                            value = abLanguage,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Book Language") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { langExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, null, tint = mainColor)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = mainColor,
                                unfocusedTextColor = mainColor,
                                focusedBorderColor = mainColor,
                                unfocusedBorderColor = borderColor,
                                focusedLabelColor = mainColor,
                                unfocusedLabelColor = Color.Gray
                            )
                        )
                        DropdownMenu(
                            expanded = langExpanded,
                            onDismissRequest = { langExpanded = false },
                            modifier = Modifier.background(cardBg)
                        ) {
                            listOf("Malayalam", "English", "Others").forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang, color = mainColor) },
                                    onClick = {
                                        abLanguage = lang
                                        abIsMalayalam = lang == "Malayalam"
                                        langExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Category Dropdown
                    var catExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        OutlinedTextField(
                            value = abCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { catExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, null, tint = mainColor)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = mainColor,
                                unfocusedTextColor = mainColor,
                                focusedBorderColor = mainColor,
                                unfocusedBorderColor = borderColor,
                                focusedLabelColor = mainColor,
                                unfocusedLabelColor = Color.Gray
                            )
                        )
                        DropdownMenu(
                            expanded = catExpanded,
                            onDismissRequest = { catExpanded = false },
                            modifier = Modifier.background(cardBg)
                        ) {
                            CategoryConstants.standardCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat, color = mainColor) },
                                    onClick = {
                                        abCategory = cat
                                        catExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    VoiceInputField(value = abPublisherName, onValueChange = { abPublisherName = it }, label = "Publisher", onVoiceInput = { onVoiceInput(abIsMalayalam, it) })
                    VoiceInputField(value = abYearOfPublication, onValueChange = { if (it.all { char -> char.isDigit() }) abYearOfPublication = it }, label = "Year of Publication", onVoiceInput = { onVoiceInput(false, it) })
                    VoiceInputField(
                        value = abCallNumber, 
                        onValueChange = { 
                            abCallNumber = it
                            abIsCallNumberAuto = false 
                        }, 
                        label = "Call Number", 
                        onVoiceInput = { onVoiceInput(false, {
                            abCallNumber = it
                            abIsCallNumberAuto = false
                        }) }
                    )

                    if (abNumberOfCopies.toIntOrNull() == 0) {
                        EditVoiceInputField(
                            value = abUnavailabilityReason,
                            onValueChange = { abUnavailabilityReason = it },
                            label = "Reason for Unavailability (Mandatory)",
                            onVoiceInput = { onVoiceInput(abIsMalayalam, it) },
                            borderColor = Color.Red
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cancel", color = if (isDarkMode) Color.LightGray else Color.Gray) }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = { 
                                val copiesNum = abNumberOfCopies.toIntOrNull() ?: 1
                                
                                if (copiesNum == 0 && abUnavailabilityReason.isBlank()) {
                                    showToast("Please provide a reason for unavailability.")
                                    return@Button
                                }

                                if(abTitle.isNotBlank() && abAuthor.isNotBlank() && abCategory.isNotBlank() && 
                                    abCallNumber.isNotBlank() && copiesNum >= 0 && 
                                    abAccessionNumbers.all { it.isNotBlank() } &&
                                    abLocations.all { it.isNotBlank() } &&
                                    abPrices.all { it.isNotBlank() }) {

                                    if (abAccessionNumbers.distinct().size != abAccessionNumbers.size) {
                                        showToast("Stock Numbers must be unique.")
                                        return@Button
                                    }
                                    
                                    val existingAcc = abAccessionNumbers.map { it.trim() }.find { acc ->
                                        allBooks.any { it.accessionNumber == acc }
                                    }
                                    if (existingAcc != null) {
                                        showToast("Stock Number $existingAcc already exists in library.")
                                        return@Button
                                    }

                                    val isMal = abLanguage == "Malayalam"
                                    val finalTitle = if (isMal) LanguageUtils.correctMalayalam(abTitle.trim()) else LanguageUtils.transliterateToEnglishPreserveSpaces(abTitle)
                                    val finalAuthor = if (isMal) LanguageUtils.correctMalayalam(abAuthor.trim()) else LanguageUtils.transliterateToEnglishPreserveSpaces(abAuthor)
                                    val finalPublisher = if (isMal) LanguageUtils.correctMalayalam(abPublisherName.trim()) else LanguageUtils.transliterateToEnglishPreserveSpaces(abPublisherName)
                                    val finalCall = LanguageUtils.transliterateToEnglishPreserveSpaces(abCallNumber)
                                    
                                    val finalStatus = when {
                                        copiesNum > 0 -> "Available"
                                        copiesNum == 0 -> "Unavailable: $abUnavailabilityReason"
                                        else -> "Unavailable"
                                    }

                                    val booksToSave = abAccessionNumbers.mapIndexed { index, acc ->
                                        Book(
                                            accessionNumber = LanguageUtils.transliterateToEnglish(acc), 
                                            isbn = if(abIsbns[index].isBlank()) null else LanguageUtils.transliterateToEnglishPreserveSpaces(abIsbns[index]),
                                            title = finalTitle, 
                                            author = finalAuthor, 
                                            category = abCategory, 
                                            publisherName = if(finalPublisher.isBlank()) null else finalPublisher,
                                            yearOfPublication = abYearOfPublication.filter { it.isDigit() }.toIntOrNull(),
                                            callNumber = if(finalCall.isBlank()) null else finalCall,
                                            location = LanguageUtils.transliterateToEnglishPreserveSpaces(abLocations[index]),
                                            price = abPrices[index].filter { it.isDigit() }.toIntOrNull(),
                                            numberOfCopies = 1, // Each doc represents 1 physical copy now
                                            bookType = abBookTypes[index],
                                            language = abLanguage,
                                            status = finalStatus,
                                            libraryId = libraryId,
                                            unavailabilityReason = if (copiesNum == 0) abUnavailabilityReason else null
                                        )
                                    }
                                    
                                    onSave(booksToSave)
                                } else {
                                    showToast("All fields (Title, Author, Category, Location) are mandatory.")
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = mainColor, contentColor = if (isDarkMode) Color(0xFF452719) else Color.White)
                        ) { Text("Save to Library") }
                    }
                }
            }
        }
    }

    @Composable
    fun VoiceListeningDialog(
        status: String,
        onDismiss: () -> Unit,
        onRetry: () -> Unit
    ) {
        val mainColor = if (isDarkMode) Color(0xFFD7CCC8) else Color(0xFF452719)
        val cardBg = if (isDarkMode) Color(0xFF1E1916) else Color.White
        val textColor = if (isDarkMode) Color(0xFFE0E0E0) else Color(0xFF1E293B)

        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Assistant",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isDarkMode) Color.LightGray else Color(0xFF64748B),
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Surface(
                        shape = CircleShape,
                        color = mainColor.copy(alpha = 0.1f),
                        modifier = Modifier.size(100.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Surface(
                                shape = CircleShape,
                                color = mainColor,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp).padding(16.dp),
                                    tint = if (isDarkMode) Color(0xFF452719) else Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = status,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel", color = if (isDarkMode) Color.LightGray else Color(0xFF64748B))
                        }
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = mainColor, contentColor = if (isDarkMode) Color(0xFF452719) else Color.White)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun EditVoiceInputField(
        value: String, 
        onValueChange: (String) -> Unit, 
        label: String, 
        onVoiceInput: ((String) -> Unit) -> Unit, 
        borderColor: Color,
        isError: Boolean = false,
        errorText: String = ""
    ) {
        val mainColor = if (isDarkMode) Color(0xFFD7CCC8) else Color(0xFF452719)
        Column {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                isError = isError,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = if (isError) Color.Red else mainColor,
                    unfocusedTextColor = if (isError) Color.Red else mainColor,
                    focusedLabelColor = if (isError) Color.Red else mainColor,
                    unfocusedLabelColor = if (isError) Color.Red else Color.Gray,
                    focusedBorderColor = if (isError) Color.Red else mainColor,
                    unfocusedBorderColor = if (isError) Color.Red else borderColor
                ),
                trailingIcon = {
                    IconButton(onClick = { onVoiceInput { onValueChange(it) } }) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = mainColor)
                    }
                }
            )
            if (isError && errorText.isNotEmpty()) {
                Text(
                    text = errorText,
                    color = Color.Red,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 16.dp, top = 2.dp)
                )
            }
        }
    }

    @Composable
    fun VoiceInputField(value: String, onValueChange: (String) -> Unit, label: String, onVoiceInput: ((String) -> Unit) -> Unit) {
        val mainColor = if (isDarkMode) Color(0xFFD7CCC8) else Color(0xFF452719)
        val borderColor = if (isDarkMode) Color.Gray.copy(alpha = 0.5f) else Color.LightGray
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = mainColor,
                unfocusedTextColor = mainColor,
                focusedLabelColor = mainColor,
                unfocusedLabelColor = Color.Gray,
                focusedBorderColor = mainColor,
                unfocusedBorderColor = borderColor
            ),
            trailingIcon = {
                IconButton(onClick = { onVoiceInput { onValueChange(it) } }) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = mainColor)
                }
            }
        )
    }

    @Composable
    fun LanguageToggle(isMalayalam: Boolean, onToggle: (Boolean) -> Unit) {
        val mainColor = if (isDarkMode) Color(0xFFD7CCC8) else Color(0xFF452719)
        val containerColor = if (isDarkMode) Color(0xFF2D2420) else Color(0xFFF3EAE2)
        
        Surface(
            modifier = Modifier
                .height(40.dp)
                .width(160.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(containerColor),
            color = Color.Transparent
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isMalayalam) mainColor else Color.Transparent)
                        .clickable { onToggle(true) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Malayalam",
                        color = if (isMalayalam) (if (isDarkMode) Color(0xFF452719) else Color.White) else mainColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (!isMalayalam) mainColor else Color.Transparent)
                        .clickable { onToggle(false) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "English",
                        color = if (!isMalayalam) (if (isDarkMode) Color(0xFF452719) else Color.White) else mainColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    @Composable
    fun CategoryFilterDialog(onDismiss: () -> Unit, onCategorySelect: (String) -> Unit) {
        val mainColor = if (isDarkMode) Color(0xFFD7CCC8) else Color(0xFF452719)
        val cardBg = if (isDarkMode) Color(0xFF1E1916) else Color.White

        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Filter by Category",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = mainColor
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        item {
                            FilterItem("All", onCategorySelect)
                        }
                        items(CategoryConstants.standardCategories) { cat ->
                            FilterItem(cat, onCategorySelect)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text("Cancel", color = mainColor)
                    }
                }
            }
        }
    }

    @Composable
    fun FilterItem(text: String, onClick: (String) -> Unit) {
        val mainColor = if (isDarkMode) Color(0xFFD7CCC8) else Color(0xFF452719)
        val bgBeige = if (isDarkMode) Color(0xFF2D2420) else Color(0xFFFAF9F6)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable { onClick(text) },
            color = bgBeige,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(16.dp),
                fontWeight = FontWeight.Medium,
                color = mainColor
            )
        }
    }

    @Composable
    fun MembersDialog(
        users: List<User>,
        onDismiss: () -> Unit,
        onUserClick: (User) -> Unit
    ) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredUsers = users.filter { it.name.contains(searchQuery, ignoreCase = true) }
        val mainColor = if (isDarkMode) Color(0xFFD7CCC8) else Color(0xFF452719)
        val cardBg = if (isDarkMode) Color(0xFF1E1916) else Color.White
        val textColor = if (isDarkMode) Color(0xFFE0E0E0) else Color(0xFF452719)

        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Library Members",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Text(
                        "Library ID: $libraryId",
                        style = MaterialTheme.typography.labelSmall,
                        color = mainColor.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by name...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = mainColor) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = mainColor,
                            unfocusedTextColor = mainColor,
                            focusedBorderColor = mainColor,
                            unfocusedBorderColor = if (isDarkMode) Color.Gray else Color.LightGray
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        if (filteredUsers.isEmpty()) {
                            item {
                                Text(
                                    "No members found",
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    textAlign = TextAlign.Center,
                                    color = if (isDarkMode) Color.LightGray else Color.Gray
                                )
                            }
                        }
                        items(filteredUsers) { user ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { onUserClick(user) },
                                colors = CardDefaults.cardColors(containerColor = if (isDarkMode) Color(0xFF2D2420) else Color(0xFFFAF9F6)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(mainColor, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            user.name.firstOrNull()?.toString()?.uppercase() ?: "",
                                            color = if (isDarkMode) Color(0xFF452719) else Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(user.name, fontWeight = FontWeight.Bold, color = textColor)
                                        Text("Roll: ${user.rollNumber} | ${user.department}", fontSize = 12.sp, color = if (isDarkMode) Color.LightGray else Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text("Close", color = mainColor)
                    }
                }
            }
        }
    }

    @Composable
    fun UserDetailDialog(
        user: User,
        borrowedBooks: List<Book>,
        requests: List<BorrowRequest>,
        approvedRequests: List<BorrowRequest>,
        historyRequests: List<BorrowRequest>,
        onDismiss: () -> Unit,
        onApprove: (BorrowRequest) -> Unit,
        onReject: (BorrowRequest) -> Unit,
        onAcceptReturn: (Book, String) -> Unit
    ) {
        val mainColor = if (isDarkMode) Color(0xFFD7CCC8) else Color(0xFF452719)
        val cardBg = if (isDarkMode) Color(0xFF1E1916) else Color.White
        val textColor = if (isDarkMode) Color(0xFFE0E0E0) else Color(0xFF452719)

        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .background(mainColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                user.name.firstOrNull()?.toString()?.uppercase() ?: "",
                                color = if (isDarkMode) Color(0xFF452719) else Color.White,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(user.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = textColor)
                            Text(user.email, fontSize = 14.sp, color = if (isDarkMode) Color.LightGray else Color.Gray)
                            Text("Roll: ${user.rollNumber} | ${user.department}", fontSize = 12.sp, color = if (isDarkMode) Color.LightGray else Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text("Borrowed Books", fontWeight = FontWeight.Bold, color = textColor)
                    if (borrowedBooks.isEmpty()) {
                        Text("No books currently borrowed", fontSize = 14.sp, color = if (isDarkMode) Color.LightGray else Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        borrowedBooks.forEach { book ->
                            val myCopy = book.copies.find { it.borrowerId == user.uid } ?: return@forEach
                            val activeReq = approvedRequests.find { it.bookId == book.id && "${it.accessionNumber}-C${it.copyNumber}" == myCopy.copyNumber }
                            val overTime = activeReq?.dueDate?.let { System.currentTimeMillis() - it } ?: 0L
                            val fine = if (overTime > 0) (overTime / (24 * 60 * 60 * 1000)).toInt() * 1 else 0

                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isDarkMode) Color(0xFF2D2420) else Color(0xFFFAF9F6))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column {
                                            Text(book.title, fontWeight = FontWeight.Bold, color = textColor)
                                            Text("Copy: ${myCopy.copyNumber}", fontSize = 11.sp, color = mainColor)
                                        }
                                        if (fine > 0) {
                                            Text("Fine: ₹$fine", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                    activeReq?.dueDate?.let {
                                        Text("Due: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it))}", fontSize = 11.sp, color = if(fine > 0) Color.Red else (if (isDarkMode) Color.LightGray else Color.Gray))
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { onAcceptReturn(book, myCopy.copyNumber) },
                                        modifier = Modifier.align(Alignment.End).height(32.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = mainColor),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                    ) {
                                        Text("Accept Return", fontSize = 11.sp, color = if (isDarkMode) Color(0xFF452719) else Color.White)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text("Pending Requests", fontWeight = FontWeight.Bold, color = textColor)
                    if (requests.isEmpty()) {
                        Text("No pending requests", fontSize = 14.sp, color = if (isDarkMode) Color.LightGray else Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        requests.forEach { req ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isDarkMode) Color(0xFF3E2723).copy(alpha = 0.3f) else Color(0xFFFFF3E0))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(req.title, fontWeight = FontWeight.Bold, color = textColor)
                                    Text("Requested on: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(req.timeStamp))}", fontSize = 11.sp, color = if (isDarkMode) Color.LightGray else Color.Gray)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = { onReject(req) }) { Text("Reject", color = Color.Red, fontSize = 12.sp) }
                                        Button(
                                            onClick = { onApprove(req) },
                                            colors = ButtonDefaults.buttonColors(containerColor = mainColor),
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                        ) { Text("Approve", fontSize = 11.sp, color = if (isDarkMode) Color(0xFF452719) else Color.White) }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text("Returned History", fontWeight = FontWeight.Bold, color = textColor)
                    if (historyRequests.isEmpty()) {
                        Text("No return history available", fontSize = 14.sp, color = if (isDarkMode) Color.LightGray else Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        historyRequests.forEach { req ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isDarkMode) Color(0xFF2D2420) else Color(0xFFE8F5E9))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(req.title, fontWeight = FontWeight.Bold, color = textColor)
                                    Text("Stock: ${req.accessionNumber} | Copy: C${req.copyNumber}", fontSize = 11.sp, color = mainColor)
                                    req.returnDate?.let {
                                        Text("Returned on: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it))}", fontSize = 11.sp, color = Color(0xFF2E7D32))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = mainColor)
                    ) {
                        Text("Close", color = if (isDarkMode) Color(0xFF452719) else Color.White)
                    }
                }
            }
        }
    }

    @Composable
    fun MemberIdDialog(
        userName: String,
        libraryId: String,
        userUid: String,
        onDismiss: () -> Unit
    ) {
        val mainColor = if (isDarkMode) Color(0xFFD7CCC8) else Color(0xFF452719)
        val cardBg = if (isDarkMode) Color(0xFF1E1916) else Color.White
        val textColor = if (isDarkMode) Color(0xFFE0E0E0) else Color(0xFF452719)

        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Digital ID",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isDarkMode) Color.LightGray else Color(0xFF64748B),
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = if (isDarkMode) Color(0xFF2D2420) else Color(0xFFF3EAE2),
                        modifier = Modifier.size(240.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(16.dp)) {
                            val qrBitmap = remember(userUid) { QrHelper.generateQrCode(userUid) }
                            if (qrBitmap != null) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "User QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = userName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )

                    Text(
                        text = "ID: ${userUid.takeLast(8).uppercase()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isDarkMode) Color.LightGray else Color.Gray
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = mainColor)
                    ) {
                        Text("Done", color = if (isDarkMode) Color(0xFF452719) else Color.White)
                    }
                }
            }
        }
    }

    @Composable
    fun DeleteOptionsDialog(
        bookTitle: String,
        copyCount: Int,
        onDismiss: () -> Unit,
        onDeleteEntire: () -> Unit,
        onReduceCopy: () -> Unit
    ) {
        val cardBg = if (isDarkMode) Color(0xFF1E1916) else Color.White
        val textColor = if (isDarkMode) Color(0xFFE0E0E0) else Color(0xFF1E293B)
        val mainColor = if (isDarkMode) Color(0xFFD7CCC8) else Color(0xFF452719)

        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Delete Options",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        bookTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = textColor.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        "This book has $copyCount copies. What would you like to do?",
                        color = if (isDarkMode) Color.LightGray else Color.DarkGray
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onReduceCopy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = mainColor)
                    ) {
                        Text("Reduce by 1 Copy", color = if (isDarkMode) Color(0xFF452719) else Color.White)
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = onDeleteEntire,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("Delete Entire Book", color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel", color = if (isDarkMode) Color.LightGray else Color.Gray)
                    }
                }
            }
        }
    }
}

