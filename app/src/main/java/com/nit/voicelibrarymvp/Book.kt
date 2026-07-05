package com.nit.voicelibrarymvp

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.firebase.firestore.PropertyName

data class BookCopy(
    @get:PropertyName("accession_number") @set:PropertyName("accession_number") var accessionNumber: String = "", 
    @get:PropertyName("copyNumber") @set:PropertyName("copyNumber") var copyNumber: String = "", // Legacy field
    @get:PropertyName("status") @set:PropertyName("status") var status: String = "Available",
    @get:PropertyName("borrower_id") @set:PropertyName("borrower_id") var borrowerId: String? = null,
    @get:PropertyName("borrowerName") @set:PropertyName("borrowerName") var borrowerName: String? = null,
    @get:PropertyName("dueDate") @set:PropertyName("dueDate") var dueDate: Long? = null,
)

@Entity(tableName = "reviews")
data class Review(
    @PrimaryKey(autoGenerate = true) val reviewId: Int = 0,
    @get:PropertyName("bookId") @set:PropertyName("bookId") var bookId: String = "", 
    @get:PropertyName("userUid") @set:PropertyName("userUid") var userUid: String = "",
    @get:PropertyName("userName") @set:PropertyName("userName") var userName: String = "",
    @get:PropertyName("comment") @set:PropertyName("comment") var comment: String = "",
    @get:PropertyName("rating") @set:PropertyName("rating") var rating: Int = 0, 
    @get:PropertyName("time_stamp") @set:PropertyName("time_stamp") var timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "books")
data class Book(
    @PrimaryKey @get:PropertyName("accession_number") @set:PropertyName("accession_number") var accessionNumber: String = "", 
    @get:PropertyName("isbn") @set:PropertyName("isbn") var isbn: String? = null,
    @get:PropertyName("id") @set:PropertyName("id") var id: String = "", 
    @get:PropertyName("title") @set:PropertyName("title") var title: String = "",
    @get:PropertyName("author") @set:PropertyName("author") var author: String = "",
    @get:PropertyName("category") @set:PropertyName("category") var category: String = "",
    @get:PropertyName("publisher_name") @set:PropertyName("publisher_name") var publisherName: String? = null,
    @get:PropertyName("year_of_publication") @set:PropertyName("year_of_publication") var yearOfPublication: Int? = null,
    @get:PropertyName("call_number") @set:PropertyName("call_number") var callNumber: String? = null,
    @get:PropertyName("location") @set:PropertyName("location") var location: String = "",
    @get:PropertyName("price") @set:PropertyName("price") var price: Int? = null,
    @get:PropertyName("can_be_borrowed") @set:PropertyName("can_be_borrowed") var canBeBorrowed: Boolean = true,
    @get:PropertyName("status") @set:PropertyName("status") var status: String = "Available", 
    @get:PropertyName("number_of_copies") @set:PropertyName("number_of_copies") var numberOfCopies: Int = 1,
    @get:PropertyName("admin_phone_number") @set:PropertyName("admin_phone_number") var adminPhoneNumber: String = "",
    @get:PropertyName("library_id") @set:PropertyName("library_id") var libraryId: String = "",
    @get:PropertyName("language") @set:PropertyName("language") var language: String = "English",
    @get:PropertyName("book_type") @set:PropertyName("book_type") var bookType: String = "Normal",
    @get:PropertyName("unavailability_reason") @set:PropertyName("unavailability_reason") var unavailabilityReason: String? = null,
    @Ignore @get:PropertyName("copies") @set:PropertyName("copies") var copies: List<BookCopy> = emptyList(),
    @Ignore @get:PropertyName("review") @set:PropertyName("review") var reviews: List<Review> = emptyList()
)

@Entity(tableName = "borrow_requests")
data class BorrowRequest(
    @PrimaryKey @get:PropertyName("id") @set:PropertyName("id") var id: String = "",
    @get:PropertyName("library_id") @set:PropertyName("library_id") var libraryId: String = "",
    @get:PropertyName("bookId") @set:PropertyName("bookId") var bookId: String = "",
    @get:PropertyName("title") @set:PropertyName("title") var title: String = "",
    @get:PropertyName("accession_number") @set:PropertyName("accession_number") var accessionNumber: String = "",
    @get:PropertyName("copy_number") @set:PropertyName("copy_number") var copyNumber: Int = 0,
    @get:PropertyName("userUid") @set:PropertyName("userUid") var userUid: String = "",
    @get:PropertyName("user_name") @set:PropertyName("user_name") var userName: String = "",
    @get:PropertyName("time_stamp") @set:PropertyName("time_stamp") var timeStamp: Long = System.currentTimeMillis(),
    @get:PropertyName("status") @set:PropertyName("status") var status: String = "PENDING",
    @get:PropertyName("due_date") @set:PropertyName("due_date") var dueDate: Long? = null,
    @get:PropertyName("approval_date") @set:PropertyName("approval_date") var approvalDate: Long? = null,
    @get:PropertyName("return_date") @set:PropertyName("return_date") var returnDate: Long? = null
)
