package com.nit.voicelibrarymvp

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.tasks.await
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object ExcelHelper {

    data class ImportResult(val total: Int, val imported: Int, val skipped: Int)

    /**
     * HIGH PERFORMANCE IMPORT
     * Handles 10,000+ books by using Local Indexing and Write Batches.
     */
    suspend fun importBooksFromExcel(context: Context, uri: Uri, libraryId: String): ImportResult {
        var totalRows = 0
        var importedUniqueBooks = 0
        var skippedRows = 0

        val db = FirebaseFirestore.getInstance()

        try {
            val existingSnapshot = db.collection("books")
                .whereEqualTo("library_id", libraryId)
                .get().await()

            // Map existing books by Stock Number and Title+Author for instant lookups
            val accessionMap = mutableMapOf<String, String?>() // Stock Number -> Document ID
            existingSnapshot.documents.forEach { doc ->
                val acc = doc.getString("accession_number") ?: doc.getString("accessionNumber") ?: ""
                if (acc.isNotEmpty()) accessionMap[acc] = doc.id
            }

            // Session books to be saved
            val sessionBooks = mutableMapOf<String, Book>()

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val workbook = XSSFWorkbook(inputStream)
                val sheet = workbook.getSheetAt(0)
                val rowIterator = sheet.iterator()

                if (rowIterator.hasNext()) rowIterator.next() // Skip Header

                while (rowIterator.hasNext()) {
                    totalRows++
                    val row = rowIterator.next()
                    val excelBook = processRow(row, libraryId) ?: continue

                    // Check if stock number exists
                    if (accessionMap.containsKey(excelBook.accessionNumber)) {
                        skippedRows++
                        continue
                    }

                    val docRef = db.collection("books").document()
                    val finalBook = excelBook.copy(id = docRef.id, numberOfCopies = 1)
                    sessionBooks[docRef.id] = finalBook
                    accessionMap[finalBook.accessionNumber] = docRef.id
                    importedUniqueBooks++
                }

                // Commit to Firestore
                var batch = db.batch()
                var batchCount = 0
                sessionBooks.values.forEach { book ->
                    val docRef = db.collection("books").document(book.id)
                    batch.set(docRef, book)
                    batchCount++
                    if (batchCount >= 500) {
                        batch.commit().await()
                        batch = db.batch()
                        batchCount = 0
                    }
                }
                if (batchCount > 0) batch.commit().await()
                workbook.close()
            }
        } catch (e: Exception) {
            Log.e("ExcelHelper", "Import failed", e)
        }
        return ImportResult(totalRows, importedUniqueBooks, skippedRows)
    }

    private fun processRow(row: Row, libraryId: String): Book? {
        val cell0 = row.getCell(0)
        val accessionStr = when (cell0?.cellType) {
            CellType.NUMERIC -> cell0.numericCellValue.toInt().toString()
            CellType.STRING -> cell0.stringCellValue.trim()
            else -> cell0?.toString()?.trim() ?: ""
        }

        val callNumber = row.getCell(1)?.toString()?.trim() ?: ""
        val title = row.getCell(2)?.toString()?.trim() ?: ""
        val author = row.getCell(3)?.toString()?.trim() ?: ""
        val language = row.getCell(4)?.toString()?.trim() ?: "English"
        val rawCategory = row.getCell(5)?.toString()?.trim() ?: ""
        val category = mapCategory(rawCategory)
        
        val priceCell = row.getCell(6)
        val price = when (priceCell?.cellType) {
            CellType.NUMERIC -> priceCell.numericCellValue.toInt()
            CellType.STRING -> priceCell.stringCellValue.trim().toDoubleOrNull()?.toInt()
            else -> null
        }

        val publisherName = row.getCell(7)?.toString()?.trim() ?: ""
        val yearCell = row.getCell(8)
        val year = when (yearCell?.cellType) {
            CellType.NUMERIC -> yearCell.numericCellValue.toInt()
            CellType.STRING -> yearCell.stringCellValue.trim().toIntOrNull()
            else -> null
        }
        
        val location = row.getCell(9)?.toString()?.trim() ?: ""
        val isbn = row.getCell(10)?.toString()?.trim() ?: ""
        val bookType = row.getCell(11)?.toString()?.trim() ?: "Normal"

        if (title.isEmpty() || author.isEmpty()) return null

        return Book(
            isbn = if(isbn.isBlank()) null else isbn,
            accessionNumber = accessionStr,
            callNumber = if(callNumber.isBlank()) null else callNumber,
            title = title,
            author = author,
            category = category,
            publisherName = if(publisherName.isBlank()) null else publisherName,
            yearOfPublication = year,
            location = location,
            price = price,
            language = language,
            bookType = bookType,
            libraryId = libraryId,
            numberOfCopies = 1
        )
    }

    fun exportCatalog(context: Context, books: List<Book>): File? {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Library Catalog")
        val header = sheet.createRow(0)
        val columns = arrayOf("Stock Number", "Call Number", "Book Name", "Author", "Language", "Category", "Price", "Publisher", "Edition", "Location", "ISBN", "Book Type")
        columns.forEachIndexed { index, s -> header.createCell(index).setCellValue(s) }

        var rowNum = 1
        books.sortedWith(compareBy { it.accessionNumber.padStart(20, '0') }).forEach { book ->
            val row = sheet.createRow(rowNum++)
            fillBookRow(row, book)
        }
        return saveWorkbook(context, workbook, "Library_Catalog.xlsx")
    }

    private fun fillBookRow(row: Row, book: Book) {
        row.createCell(0).setCellValue(book.accessionNumber)
        row.createCell(1).setCellValue(book.callNumber ?: "")
        row.createCell(2).setCellValue(book.title)
        row.createCell(3).setCellValue(book.author)
        row.createCell(4).setCellValue(book.language)
        row.createCell(5).setCellValue(book.category)
        row.createCell(6).setCellValue(book.price?.toDouble() ?: 0.0)
        row.createCell(7).setCellValue(book.publisherName ?: "")
        row.createCell(8).setCellValue(book.yearOfPublication?.toDouble() ?: 0.0)
        row.createCell(9).setCellValue(book.location)
        row.createCell(10).setCellValue(book.isbn ?: "")
        row.createCell(11).setCellValue(book.bookType)
    }

    fun exportReviews(context: Context, books: List<Book>): File? {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Reviews Report")
        val header = sheet.createRow(0)
        val columns = arrayOf("Stock Number", "Book Name", "User Name", "Rating", "Review", "Date")
        columns.forEachIndexed { index, s -> header.createCell(index).setCellValue(s) }

        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        var rowNum = 1
        books.sortedWith(compareBy { it.accessionNumber.padStart(20, '0') }).forEach { book ->
            book.reviews.forEach { review ->
                val row = sheet.createRow(rowNum++)
                row.createCell(0).setCellValue(book.accessionNumber)
                row.createCell(1).setCellValue(book.title)
                row.createCell(2).setCellValue(review.userName)
                row.createCell(3).setCellValue(review.rating.toDouble())
                row.createCell(4).setCellValue(review.comment)
                row.createCell(5).setCellValue(sdf.format(java.util.Date(review.timestamp)))
            }
        }
        return saveWorkbook(context, workbook, "Reviews_Report.xlsx")
    }

    fun exportBorrowedBooks(context: Context, requests: List<BorrowRequest>): File? {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Borrowed Books")
        val header = sheet.createRow(0)
        val columns = arrayOf("Stock Number", "Copy No", "Book Name", "Borrower Name", "Borrow Date", "Return Status")
        columns.forEachIndexed { index, s -> header.createCell(index).setCellValue(s) }

        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        requests.sortedWith(compareBy { it.accessionNumber.padStart(20, '0') }).forEachIndexed { index, req ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(req.accessionNumber)
            row.createCell(1).setCellValue(req.copyNumber.toDouble())
            row.createCell(2).setCellValue(req.title)
            row.createCell(3).setCellValue(req.userName)
            row.createCell(4).setCellValue(sdf.format(java.util.Date(req.approvalDate ?: req.timeStamp)))
            row.createCell(5).setCellValue(req.status)
        }
        return saveWorkbook(context, workbook, "Borrowed_Books_Report.xlsx")
    }

    fun exportStatistics(context: Context, books: List<Book>, requests: List<BorrowRequest>): File? {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Library Statistics")
        var rowNum = 0
        sheet.createRow(rowNum++).createCell(0).setCellValue("Total Titles: ${books.size}")
        sheet.createRow(rowNum++).createCell(0).setCellValue("Total Copies: ${books.size}")
        sheet.createRow(rowNum++).createCell(0).setCellValue("Active Borrows: ${requests.count { it.status == "APPROVED" }}")
        return saveWorkbook(context, workbook, "Library_Statistics.xlsx")
    }

    private fun saveWorkbook(context: Context, workbook: Workbook, fileName: String): File? {
        return try {
            val file = File(context.getExternalFilesDir(null), fileName)
            FileOutputStream(file).use { workbook.write(it) }
            workbook.close()
            file
        } catch (e: Exception) { null }
    }

    fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    fun generateSampleExcel(context: Context): File? {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Import Template")
        val header = sheet.createRow(0)
        val columns = arrayOf(
            "Stock Number", "Call Number", "Book Name", "Author", "Language", 
            "Category", "Price", "Publisher", "Edition", "Location", 
            "ISBN", "Book Type"
        )
        columns.forEachIndexed { index, s -> header.createCell(index).setCellValue(s) }
        return saveWorkbook(context, workbook, "Library_Import_Template.xlsx")
    }

    private fun mapCategory(input: String): String {
        val lower = input.lowercase()
        return when {
            lower.contains("fiction") || lower.contains("കഥാസാഹിത്യം") -> "Fiction"
            lower.contains("novel") || lower.contains("നോവൽ") -> "Novel"
            lower.contains("story") || lower.contains("കഥ") -> "Short Stories"
            lower.contains("autobiography") -> "Autobiography"
            lower.contains("essay") || lower.contains("non") -> "Non-Fiction & Essays"
            lower.contains("mindset") || lower.contains("self") -> "Self-Help & Mindset"
            lower.contains("child") -> "Children's Literature"
            else -> "Unknown/Miscellaneous"
        }
    }
}
