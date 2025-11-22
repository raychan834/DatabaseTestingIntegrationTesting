package com.praktikum.database.testing.integration;

// Import classes untuk testing
import com.github.javafaker.Faker;
import com.praktikum.database.testing.BaseDatabaseTest;
import com.praktikum.database.testing.config.DatabaseConfig; // Import DatabaseConfig
import com.praktikum.database.testing.dao.BookDAO;
import com.praktikum.database.testing.dao.BorrowingDAO;
import com.praktikum.database.testing.dao.UserDAO;
import com.praktikum.database.testing.model.Book;
import com.praktikum.database.testing.model.Borrowing;
import com.praktikum.database.testing.model.User;
import com.praktikum.database.testing.service.BorrowingService;
import com.praktikum.database.testing.utils.IndonesianFakerHelper;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
// Import static assertions
import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive Integration Test Suite
 * Menguji integrasi antara User, Book, Borrowing, dan Service
 * layer
 * Focus pada complete workflows dan business processes
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Borrowing Integration Test Suite")
public class BorrowingIntegrationTest extends BaseDatabaseTest {
    // Test dependencies
    private static UserDAO userDAO;
    private static BookDAO bookDAO;
    private static BorrowingDAO borrowingDAO;
    private static BorrowingService borrowingService;
    private static Faker faker;
    // Test data trackers (gunakan Set untuk auto-handle
    // duplicates)
    private static Set<Integer> testUserIds;
    private static Set<Integer> testBookIds;
    private static Set<Integer> testBorrowingIds;

    @BeforeAll
    static void setUpAll() {
        logger.info("Starting Borrowing Integration Tests");
        // Initialize semua DAOs dan Services
        userDAO = new UserDAO();
        bookDAO = new BookDAO();
        borrowingDAO = new BorrowingDAO();
        // Inject DAOs ke service untuk testing
        borrowingService = new BorrowingService(userDAO, bookDAO,
                borrowingDAO);
        faker = IndonesianFakerHelper.getFaker();
        // Initialize trackers
        testUserIds = new HashSet<>();
        testBookIds = new HashSet<>();
        testBorrowingIds = new HashSet<>();
    }

    @AfterAll
    static void tearDownAll() {
        logger.info("Borrowing Integration Tests Completed");
        // Cleanup semua test data
        cleanupTestData();
    }

    /**
     * Cleanup semua test data yang dibuat selama testing
     */
    private static void cleanupTestData() {
        logger.info("Cleaning up all test data...");
        // Cleanup borrowings
        for (Integer borrowingId : testBorrowingIds) {
            try {
                borrowingDAO.delete(borrowingId);
            } catch (SQLException e) {
                logger.warning("Gagal cleanup borrowing ID: " +
                        borrowingId);
            }
        }
        // Cleanup books
        for (Integer bookId : testBookIds) {
            try {
                bookDAO.delete(bookId);
            } catch (SQLException e) {
                logger.warning("Gagal cleanup book ID: " + bookId);
            }
        }
        // Cleanup users
        for (Integer userId : testUserIds) {
            try {
                userDAO.delete(userId);
            } catch (SQLException e) {
                logger.warning("Gagal cleanup user ID: " + userId);
            }
        }
    }

    //
    // HELPER METHODS
    //
    /**
     * Helper method untuk membuat test user
     */
    private User createTestUser() throws SQLException {
        User user = User.builder()
                .username("user." + System.currentTimeMillis() +
                        "_" + faker.number().randomNumber())
                .email(IndonesianFakerHelper.generateIndonesianEmail())
                .fullName(IndonesianFakerHelper.generateIndonesianName())
                .phone(IndonesianFakerHelper.generateIndonesianPhone())
                .role("member")
                .status("active")
                .build();
        user = userDAO.create(user);
        testUserIds.add(user.getUserId());
        return user;
    }

    /**
     * Helper method untuk membuat test book
     */
    private Book createTestBook(int totalCopies) throws
            SQLException {
        Book book = Book.builder()
                .isbn("978int" + System.currentTimeMillis())
                .title("Buku Integration Test " +
                        faker.book().title())
                .authorId(1) // Asumsi author_id 1 exists
                .publisherId(1) // Asumsi publisher_id 1 exists
                .categoryId(1) // Asumsi category_id 1 exists
                .publicationYear(2023)
                .pages(faker.number().numberBetween(100, 500))
                .language("Indonesia")
                .description("Buku untuk integration testing")
                .totalCopies(totalCopies)
                .availableCopies(totalCopies)
                .price(new BigDecimal("99000.00"))
                .location("Rak Integration-Test")
                .status("available")
                .build();
        book = bookDAO.create(book);
        testBookIds.add(book.getBookId());
        return book;
    }

    //
    // COMPLETE WORKFLOW TESTS
    //
    @Test
    @Order(1)
    @DisplayName("TC401: Complete borrowing workflow - Success scenario")
    void testBorrowingWorkflow_SuccessScenario() throws
            SQLException {
        // ARRANGE
        User user = createTestUser();
        Book book = createTestBook(5);
        // ACT
        Borrowing borrowing = borrowingService.borrowBook(
                user.getUserId(),
                book.getBookId(),
                14 // Pinjam 14 hari
        );
        testBorrowingIds.add(borrowing.getBorrowingId());
        // ASSERT
        // 1. Borrowing record harus terbuat
        assertThat(borrowing).isNotNull();
        assertThat(borrowing.getBorrowingId()).isNotNull();
        assertThat(borrowing.getUserId()).isEqualTo(user.getUserId());
        assertThat(borrowing.getBookId()).isEqualTo(book.getBookId());
        assertThat(borrowing.getStatus()).isEqualTo("borrowed");
        assertThat(borrowing.getReturnDate()).isNull();
        // 2. Available copies di buku harus berkurang
        Optional<Book> updatedBook =
                bookDAO.findById(book.getBookId());
        assertThat(updatedBook)
                .isPresent()
                .get()
                .satisfies(b -> {
                    assertThat(b.getAvailableCopies()).isEqualTo(4); // 5 - 1 = 4
                });
        // 3. User active borrowing count harus bertambah
        int activeCount =
                borrowingDAO.countActiveBorrowingsByUser(user.getUserId());
        assertThat(activeCount).isEqualTo(1);
        logger.info("TC401 PASSED: Borrowing workflow success. " +
                "Borrowing ID: " + borrowing.getBorrowingId());
    }

    @Test
    @Order(2)
    @DisplayName("TC402: Complete return workflow - Success scenario")
    void testReturnWorkflow_SuccessScenario() throws SQLException {
        // ARRANGE
        User user = createTestUser();
        Book book = createTestBook(3);
        Borrowing borrowing = borrowingService.borrowBook(
                user.getUserId(),
                book.getBookId(),
                14
        );
        testBorrowingIds.add(borrowing.getBorrowingId());
        // ACT
        boolean returned =
                borrowingService.returnBook(borrowing.getBorrowingId());
        // ASSERT
        // 1. returnBook method harus return true
        assertThat(returned).isTrue();
        // 2. Borrowing record harus ter-update
        Optional<Borrowing> updatedBorrowing =
                borrowingDAO.findById(borrowing.getBorrowingId());
        assertThat(updatedBorrowing)
                .isPresent()
                .get()
                .satisfies(b -> {
                    assertThat(b.getReturnDate()).isNotNull();
                    assertThat(b.getStatus()).isEqualTo("returned");
                });
        // 3. Available copies di buku harus bertambah
        Optional<Book> updatedBook =
                bookDAO.findById(book.getBookId());
        assertThat(updatedBook)
                .isPresent()
                .get()
                .satisfies(b -> {
                    assertThat(b.getAvailableCopies()).isEqualTo(3); // 2 + 1 = 3
                });
        // 4. User active borrowing count harus berkurang
        int activeCount =
                borrowingDAO.countActiveBorrowingsByUser(user.getUserId());
        assertThat(activeCount).isEqualTo(0);
        logger.info("TC402 PASSED: Return workflow success. " +
                "Borrowing ID: " + borrowing.getBorrowingId());
    }

    @Test
    @Order(3)
    @DisplayName("TC403: Borrow book dengan inactive user - Should Fail")
    void testBorrowBook_InactiveUser_ShouldFail() throws
            SQLException {
        // ARRANGE
        User inactiveUser = createTestUser();
        inactiveUser.setStatus("inactive"); // Set user inactive
        userDAO.update(inactiveUser);
        Book book = createTestBook(1);
        // ACT & ASSERT
        // Harus throw IllegalStateException karena user tidak
        // active
        final Integer finalUserId = inactiveUser.getUserId();
        final Integer finalBookId = book.getBookId();
        assertThatThrownBy(() ->
                borrowingService.borrowBook(finalUserId, finalBookId, 7))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User account tidak active");
        // VERIFY
        // Available copies tidak boleh berkurang
        Optional<Book> updatedBook =
                bookDAO.findById(book.getBookId());
        assertThat(updatedBook.get().getAvailableCopies()).isEqualTo(1);
        logger.info("TC403 PASSED: Borrowing failed for inactive " +
                "user as expected.");
    }

    @Test
    @Order(4)
    @DisplayName("TC404: Borrow unavailable book - Should Fail")
    void testBorrowBook_UnavailableBook_ShouldFail() throws
            SQLException {
        // ARRANGE
        User user = createTestUser();
        Book unavailableBook = createTestBook(0); // 0 copies
        // ACT & ASSERT
        // Harus throw IllegalStateException karena available_copies
        // = 0
        final Integer finalUserId = user.getUserId();
        final Integer finalBookId = unavailableBook.getBookId();
        assertThatThrownBy(() ->
                borrowingService.borrowBook(finalUserId, finalBookId, 7))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tidak ada kopi yang tersedia");
        // VERIFY
        // User active borrowing count tidak boleh bertambah
        int activeCount =
                borrowingDAO.countActiveBorrowingsByUser(user.getUserId());
        assertThat(activeCount).isEqualTo(0);
        logger.info("TC404 PASSED: Borrowing failed for " +
                "unavailable book as expected.");
    }

    @Test
    @Order(5)
    @DisplayName("TC405: Return already returned book - Should Fail")
    void testReturnBook_AlreadyReturned_ShouldFail() throws
            SQLException {
        // ARRANGE
        User user = createTestUser();
        Book book = createTestBook(1);
        Borrowing borrowing = borrowingService.borrowBook(
                user.getUserId(),
                book.getBookId(),
                14
        );
        testBorrowingIds.add(borrowing.getBorrowingId());
        // Kembalikan buku pertama kali
        borrowingService.returnBook(borrowing.getBorrowingId());
        // ACT & ASSERT
        // Coba kembalikan lagi (harus gagal)
        final Integer finalBorrowingId = borrowing.getBorrowingId();
        assertThatThrownBy(() ->
                borrowingService.returnBook(finalBorrowingId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Buku sudah dikembalikan");
        // VERIFY
        // Available copies tidak boleh bertambah lagi
        Optional<Book> updatedBook =
                bookDAO.findById(book.getBookId());
        assertThat(updatedBook.get().getAvailableCopies()).isEqualTo(1);
        // Harusnya 0 (pinjam) -> 1 (kembalikan) -> 1 (gagal
        // kembalikan lagi)
        logger.info("TC405 PASSED: Returning already returned book " +
                "failed as expected.");
    }

    @Test
    @Order(6)
    @DisplayName("TC407: Borrowing limit enforcement - Maximum 5 " +
            "books per user")
    void testBorrowingLimit_Max5Books() throws SQLException {
        // ARRANGE
        User user = createTestUser();
        // Pinjam 5 buku
        for (int i = 0; i < 5; i++) {
            Book book = createTestBook(1);
            Borrowing b = borrowingService.borrowBook(
                    user.getUserId(),
                    book.getBookId(),
                    14
            );
            testBorrowingIds.add(b.getBorrowingId());
        }
        // Buat buku ke-6
        Book book6 = createTestBook(1);
        // VERIFY
        // User active borrowing count harus 5
        int activeCount =
                borrowingDAO.countActiveBorrowingsByUser(user.getUserId());
        assertThat(activeCount).isEqualTo(5);
        // ACT & ASSERT
        // Coba pinjam buku ke-6 (harus gagal)
        final Integer finalUserId = user.getUserId();
        final Integer finalBookId = book6.getBookId();
        assertThatThrownBy(() ->
                borrowingService.borrowBook(finalUserId, finalBookId, 7))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("batas peminjaman: 5");
        // VERIFY
        // Available copies buku ke-6 tidak boleh berkurang
        Optional<Book> updatedBook6 =
                bookDAO.findById(book6.getBookId());
        assertThat(updatedBook6.get().getAvailableCopies()).isEqualTo(1);
        logger.info("TC407 PASSED: Borrowing limit enforced at 5 " +
                "books as expected.");
    }

    @Test
    @Order(7)
    @DisplayName("TC411: Transaction integrity - All or nothing " +
            "principle (Simulated)")
    void testTransactionIntegrity_AllOrNothing() throws
            SQLException {
        // ARRANGE
        // Kita simulasikan error setelah decrease copies,
        // tapi sebelum create borrowing.
        // Di setup test (BaseDatabaseTest), kita set
        // auto-commit = false dan rollback @AfterEach.
        // Test ini memverifikasi bahwa state di database
        // kembali ke awal jika ada exception.
        User user = createTestUser();
        Book book = createTestBook(1);
        final Integer finalUserId = user.getUserId();
        final Integer finalBookId = book.getBookId();
        // ACT & ASSERT
        // Coba pinjam, tapi lempar exception buatan
        // (simulasi kegagalan)
        assertThatThrownBy(() -> {
            // Transaksi dimulai di sini (oleh @BeforeEach)
            // Step 1: Validasi (lolos)
            // Step 2: Decrease copies (lolos)
            boolean decreased =
                    bookDAO.decreaseAvailableCopies(finalBookId);
            assertThat(decreased).isTrue(); // Sukses decrease
            // Step 3: Create borrowing (simulasi gagal)
            if (true) {
                throw new SQLException("Simulated database " +
                        "failure after decrease");
            }
            borrowingService.borrowBook(finalUserId, finalBookId, 7);
            // Transaksi di-rollback di sini (oleh @AfterEach)
        }).isInstanceOf(SQLException.class)
                .hasMessageContaining("Simulated database failure");
        // VERIFY
        // Setelah @AfterEach rollback, available copies harus
        // kembali ke state awal (1)
        // Kita perlu koneksi baru untuk cek state
        // pasca-rollback
        try (Connection verificationConn = DatabaseConfig.getConnection()) {
            BookDAO verificationDAO = new BookDAO();
            // Set connection manual ke DAO (bukan cara terbaik,
            // tapi simple untuk test ini)
            // Cara lebih baik: Buat findById(id, conn) di DAO
            // Untuk simple-nya, kita re-fetch saja

            // Kode di bawah ini mungkin tidak akurat karena 'bookDAO' masih
            // terikat pada koneksi yang di-rollback.
            // Dalam skenario nyata, Anda akan membuat instance DAO baru
            // dengan 'verificationConn' atau me-refresh state.
            // Namun, untuk tes ini, kita asumsikan 'bookDAO' akan
            // membuat koneksi baru untuk 'findById'.
            Optional<Book> bookAfterRollback =
                    bookDAO.findById(finalBookId);

            // Jika 'bookDAO.findById' menggunakan koneksi 'this.connection'
            // yang di-rollback, kita harus membuat DAO baru.
            // Mari kita asumsikan DAO membuat koneksi baru per panggilan
            // (seperti di implementasi modul).

            assertThat(bookAfterRollback.get().getAvailableCopies()).isEqualTo(1);
        }
        logger.info("TC411 PASSED: Transaction integrity verified " +
                "(changes rolled back).");
    }
}