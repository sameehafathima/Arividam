package com.nit.voicelibrarymvp

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

enum class ScanType {
    BOOK_COVER, TITLE_PAGE, REGISTER, MEMBER_ID
}

@Composable
fun OcrScannerDialog(
    scanType: ScanType = ScanType.BOOK_COVER,
    onDismiss: () -> Unit,
    onResult: (List<OcrHelper.BookInfo>) -> Unit,
    onMemberScanned: (String) -> Unit = {}
) {
    val TAG = "OcrScanner"
    Log.d(TAG, "Opening OcrScannerDialog: $scanType")
    
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var extractedList by remember { mutableStateOf<List<OcrHelper.BookInfo>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }

    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            Log.d(TAG, "Disposing OcrScannerDialog, shutting down executor")
            cameraExecutor.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                cameraProvider.unbindAll()
                
                val cameraSelector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    Log.e(TAG, "No camera found on device")
                    scope.launch(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "No camera found", android.widget.Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                    return@addListener
                }

                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                Log.d(TAG, "Camera bound successfully")
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                android.widget.Toast.makeText(context, "Camera failed to start", android.widget.Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (capturedBitmap == null) {
                    // Camera Preview
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                    
                    // Controls
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }

                    FloatingActionButton(
                        onClick = {
                            if (isProcessing) return@FloatingActionButton
                            isProcessing = true
                            Log.d(TAG, "Capture button clicked")
                            try {
                                imageCapture.takePicture(
                                    cameraExecutor,
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            Log.d(TAG, "Image captured successfully")
                                            val rotation = image.imageInfo.rotationDegrees
                                            val bitmap = try {
                                                val original = image.toBitmap()
                                                image.close()
                                                
                                                // MEMORY OPTIMIZATION: Resize immediately (1280px is enough for OCR)
                                                val maxDim = 1280
                                                if (original.width > maxDim || original.height > maxDim) {
                                                    val ratio = original.width.toFloat() / original.height.toFloat()
                                                    val (w, h) = if (ratio > 1) maxDim to (maxDim / ratio).toInt()
                                                                 else (maxDim * ratio).toInt() to maxDim
                                                    val scaled = Bitmap.createScaledBitmap(original, w, h, true)
                                                    if (scaled != original) original.recycle()
                                                    scaled
                                                } else original
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Failed to convert image to bitmap", e)
                                                image.close()
                                                null
                                            }

                                            if (bitmap == null) {
                                                scope.launch(Dispatchers.Main) {
                                                    android.widget.Toast.makeText(context, "Image conversion failed", android.widget.Toast.LENGTH_SHORT).show()
                                                    isProcessing = false
                                                }
                                                return
                                            }

                                            scope.launch {
                                                try {
                                                    // Handle Barcode/QR scan regardless of Language Toggle
                                                    if (scanType == ScanType.MEMBER_ID) {
                                                        // Bitmap is already oriented correctly by image.toBitmap(), so pass 0 rotation
                                                        val qrResult = BarcodeHelper.scanQrCode(bitmap, 0)
                                                        if (qrResult != null) {
                                                            withContext(Dispatchers.Main) {
                                                                onMemberScanned(qrResult)
                                                                onDismiss()
                                                            }
                                                        } else {
                                                            withContext(Dispatchers.Main) {
                                                                android.widget.Toast.makeText(context, "No QR Code detected", android.widget.Toast.LENGTH_SHORT).show()
                                                                isProcessing = false
                                                            }
                                                        }
                                                        return@launch
                                                    }

                                                    Log.d(TAG, "Processing OCR")
                                                    val mlResult = withContext(Dispatchers.Default) {
                                                        OcrHelper.extractText(bitmap)
                                                    }
                                                    withContext(Dispatchers.Main) {
                                                        extractedList = if (scanType == ScanType.REGISTER) {
                                                            OcrHelper.parseRegisterRows(mlResult)
                                                        } else {
                                                            listOf(OcrHelper.parseBookInfo(mlResult))
                                                        }
                                                    }
                                                    
                                                    withContext(Dispatchers.Main) {
                                                        if (extractedList.isEmpty() || (extractedList.size == 1 && extractedList[0].title.isBlank())) {
                                                            android.widget.Toast.makeText(context, "No text detected. Try again.", android.widget.Toast.LENGTH_SHORT).show()
                                                            isProcessing = false
                                                        } else {
                                                            capturedBitmap = bitmap
                                                            isProcessing = false
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "Processing error", e)
                                                    withContext(Dispatchers.Main) {
                                                        android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                                        isProcessing = false
                                                    }
                                                }
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            Log.e(TAG, "Capture failed", exception)
                                            scope.launch(Dispatchers.Main) {
                                                android.widget.Toast.makeText(context, "Capture failed: ${exception.message}", android.widget.Toast.LENGTH_SHORT).show()
                                                isProcessing = false
                                            }
                                        }
                                    }
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Camera operation failed", e)
                                android.widget.Toast.makeText(context, "Camera error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                isProcessing = false
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)
                    ) {
                        if (isProcessing) CircularProgressIndicator(color = Color.White) else Icon(Icons.Default.Camera, contentDescription = "Capture")
                    }
                } else {
                    // Review Screen
                    if (scanType == ScanType.REGISTER) {
                        RegisterReviewScreen(
                            bitmap = capturedBitmap!!,
                            books = extractedList,
                            onDismiss = { 
                                capturedBitmap?.recycle()
                                capturedBitmap = null 
                            },
                            onSave = { 
                                onResult(it)
                                capturedBitmap?.recycle()
                                onDismiss()
                            }
                        )
                    } else {
                        OcrReviewScreen(
                            bitmap = capturedBitmap!!,
                            info = extractedList.firstOrNull() ?: OcrHelper.BookInfo(),
                            onDismiss = { 
                                capturedBitmap?.recycle()
                                capturedBitmap = null 
                            },
                            onSave = { 
                                onResult(listOf(it))
                                capturedBitmap?.recycle()
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RegisterReviewScreen(
    bitmap: Bitmap,
    books: List<OcrHelper.BookInfo>,
    onDismiss: () -> Unit,
    onSave: (List<OcrHelper.BookInfo>) -> Unit
) {
    var editedBooks by remember { mutableStateOf(books) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Verify Extracted Data", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Captured Image",
            modifier = Modifier.fillMaxWidth().height(200.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(editedBooks) { index, book ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        OutlinedTextField(
                            value = book.title,
                            onValueChange = { editedBooks = editedBooks.toMutableList().apply { this[index] = book.copy(title = it) } },
                            label = { Text("Title") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = book.author,
                            onValueChange = { editedBooks = editedBooks.toMutableList().apply { this[index] = book.copy(author = it) } },
                            label = { Text("Author") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) {
                Text("Retake")
            }
            Button(onClick = { onSave(editedBooks) }) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save All")
            }
        }
    }
}

@Composable
fun OcrReviewScreen(
    bitmap: Bitmap,
    info: OcrHelper.BookInfo,
    onDismiss: () -> Unit,
    onSave: (OcrHelper.BookInfo) -> Unit
) {
    var title by remember { mutableStateOf(info.title) }
    var author by remember { mutableStateOf(info.author) }
    var publisher by remember { mutableStateOf(info.publisher) }
    var accession by remember { mutableStateOf(info.accession) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Verify Details", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Captured Image",
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = author,
            onValueChange = { author = it },
            label = { Text("Author") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = publisher,
            onValueChange = { publisher = it },
            label = { Text("Publisher") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = accession,
            onValueChange = { accession = it },
            label = { Text("Stock Number") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text("Retake")
            }
            Button(
                onClick = {
                    onSave(OcrHelper.BookInfo(title, author, publisher, accession))
                }
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Confirm")
            }
        }
    }
}
