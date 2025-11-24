package org.wordpress.android.ui.reader.discover

import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.*
import org.wordpress.android.BaseUnitTest
import org.wordpress.android.fluxc.store.AccountStore
import org.wordpress.android.models.ReaderCardType
import org.wordpress.android.models.ReaderPost
import org.wordpress.android.ui.reader.ReaderTypes.ReaderPostListType
import org.wordpress.android.ui.reader.models.ReaderImageList
import org.wordpress.android.ui.reader.utils.ReaderImageScanner
import org.wordpress.android.ui.reader.utils.ReaderImageScannerProvider
import org.wordpress.android.ui.reader.utils.ReaderUtilsWrapper
import org.wordpress.android.util.DateTimeUtilsWrapper
import org.wordpress.android.util.UrlUtilsWrapper
import org.wordpress.android.util.WPAvatarUtilsWrapper

/**
 * Advanced tests for ReaderPostUiStateBuilder using test doubles:
 * - Mocks: Mockito mocks for external dependencies
 * - Stubs: Fake implementations for predictable behavior
 * - Fakes: In-memory implementations instead of real services
 */
@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class ReaderPostUiStateBuilderAdvancedTest : BaseUnitTest() {

    private lateinit var builder: ReaderPostUiStateBuilder

    // MOCKS - Mockito mocks for external dependencies
    @Mock
    lateinit var accountStore: AccountStore

    @Mock
    lateinit var urlUtilsWrapper: UrlUtilsWrapper

    @Mock
    lateinit var avatarUtilsWrapper: WPAvatarUtilsWrapper

    @Mock
    lateinit var dateTimeUtilsWrapper: DateTimeUtilsWrapper

    @Mock
    lateinit var readerImageScannerProvider: ReaderImageScannerProvider

    @Mock
    lateinit var readerUtilsWrapper: ReaderUtilsWrapper

    // FAKE - In-memory fake for image scanning
    private lateinit var fakeImageScanner: FakeReaderImageScanner

    // STUB - Stub for date/time utilities
    private lateinit var stubDateTimeUtils: StubDateTimeUtils

    @Before
    fun setUp() = test {
        // Setup fakes and stubs
        fakeImageScanner = FakeReaderImageScanner()
        stubDateTimeUtils = StubDateTimeUtils()

        // Configure mocks with default behavior
        whenever(accountStore.hasAccessToken()).thenReturn(true)
        whenever(urlUtilsWrapper.removeScheme(any())).thenAnswer { invocation ->
            val url = invocation.getArgument<String>(0)
            url.replace("https://", "").replace("http://", "")
        }
        whenever(avatarUtilsWrapper.rewriteAvatarUrlWithResource(any(), anyOrNull())).thenReturn("avatar_url")
        whenever(dateTimeUtilsWrapper.javaDateToTimeSpan(any())).thenAnswer { 
            stubDateTimeUtils.formatTimeSpan(it.getArgument(0))
        }
        whenever(readerImageScannerProvider.createReaderImageScanner(any(), anyOrNull()))
            .thenReturn(fakeImageScanner)

        builder = ReaderPostUiStateBuilder(
            accountStore,
            urlUtilsWrapper,
            avatarUtilsWrapper,
            dateTimeUtilsWrapper,
            readerImageScannerProvider,
            readerUtilsWrapper,
            testDispatcher()
        )
    }

    // region MOCK TESTS - Verification of interactions with mocked objects

    @Test
    fun `MOCK - verify accountStore is called when checking user authentication`() = test {
        // Arrange
        val post = createTestPost()
        whenever(accountStore.hasAccessToken()).thenReturn(false)

        // Act
        builder.mapPostToUiStateBlocking(
            source = "test",
            post = post,
            photonWidth = 100,
            photonHeight = 100,
            postListType = ReaderPostListType.TAG_FOLLOWED,
            onButtonClicked = { _, _, _ -> },
            onItemClicked = { _, _ -> },
            onItemRendered = {},
            onMoreButtonClicked = {},
            onMoreDismissed = {},
            onVideoOverlayClicked = { _, _ -> },
            onPostHeaderViewClicked = { _, _ -> }
        )

        // Assert - verify that accountStore was called
        verify(accountStore, atLeastOnce()).hasAccessToken()
    }

    @Test
    fun `MOCK - verify urlUtils is called for blog URL formatting`() = test {
        // Arrange
        val blogUrl = "https://example.wordpress.com"
        val post = createTestPost(blogUrl = blogUrl)

        // Act
        builder.mapPostToBlogSectionUiState(post) { }

        // Assert - verify call with specific parameter
        verify(urlUtilsWrapper).removeScheme(blogUrl)
    }

    @Test
    fun `MOCK - verify avatar URL is rewritten with correct dimensions`() = test {
        // Arrange
        val avatarUrl = "https://gravatar.com/avatar/123"
        val post = createTestPost(postAvatar = avatarUrl)

        // Act
        builder.mapPostToBlogSectionUiState(post) { }

        // Assert
        verify(avatarUtilsWrapper).rewriteAvatarUrlWithResource(eq(avatarUrl), anyOrNull())
    }

    // endregion

    // region STUB TESTS - Using stubs for predictable responses

    @Test
    fun `STUB - date formatting returns predictable timespan`() = test {
        // Arrange
        val post = createTestPost()
        stubDateTimeUtils.setFixedTimeSpan("2 hours ago")

        // Act
        val uiState = builder.mapPostToUiStateBlocking(
            source = "test",
            post = post,
            photonWidth = 100,
            photonHeight = 100,
            postListType = ReaderPostListType.TAG_FOLLOWED,
            onButtonClicked = { _, _, _ -> },
            onItemClicked = { _, _ -> },
            onItemRendered = {},
            onMoreButtonClicked = {},
            onMoreDismissed = {},
            onVideoOverlayClicked = { _, _ -> },
            onPostHeaderViewClicked = { _, _ -> }
        )

        // Assert
        assertThat(uiState.dateLine).isEqualTo("2 hours ago")
    }

    @Test
    fun `STUB - different time formats are handled correctly`() = test {
        // Test different time formats
        val testCases = listOf(
            "just now" to "just now",
            "5 minutes ago" to "5 minutes ago",
            "yesterday" to "yesterday"
        )

        testCases.forEach { (input, expected) ->
            stubDateTimeUtils.setFixedTimeSpan(input)
            val post = createTestPost()
            
            val uiState = builder.mapPostToUiStateBlocking(
                source = "test",
                post = post,
                photonWidth = 100,
                photonHeight = 100,
                postListType = ReaderPostListType.TAG_FOLLOWED,
                onButtonClicked = { _, _, _ -> },
                onItemClicked = { _, _ -> },
                onItemRendered = {},
                onMoreButtonClicked = {},
                onMoreDismissed = {},
                onVideoOverlayClicked = { _, _ -> },
                onPostHeaderViewClicked = { _, _ -> }
            )

            assertThat(uiState.dateLine).isEqualTo(expected)
        }
    }

    // endregion

    // region FAKE TESTS - Using fake objects instead of real implementations

    @Test
    fun `FAKE - image scanner returns configured images for gallery`() = test {
        // Arrange
        val expectedImages = listOf("img1.jpg", "img2.jpg", "img3.jpg")
        fakeImageScanner.setGalleryImages(expectedImages)
        
        val post = createTestPost(cardType = ReaderCardType.GALLERY)

        // Act
        val uiState = builder.mapPostToUiStateBlocking(
            source = "test",
            post = post,
            photonWidth = 100,
            photonHeight = 100,
            postListType = ReaderPostListType.TAG_FOLLOWED,
            onButtonClicked = { _, _, _ -> },
            onItemClicked = { _, _ -> },
            onItemRendered = {},
            onMoreButtonClicked = {},
            onMoreDismissed = {},
            onVideoOverlayClicked = { _, _ -> },
            onPostHeaderViewClicked = { _, _ -> }
        )

        // Assert
        assertThat(uiState.thumbnailStripUrls).isNotNull
        assertThat(uiState.thumbnailStripUrls?.images).hasSize(3)
    }

    @Test
    fun `FAKE - image scanner tracks number of scan requests`() = test {
        // Arrange
        val post1 = createTestPost(cardType = ReaderCardType.GALLERY)
        val post2 = createTestPost(cardType = ReaderCardType.GALLERY)

        // Act
        builder.mapPostToUiStateBlocking(
            source = "test",
            post = post1,
            photonWidth = 100,
            photonHeight = 100,
            postListType = ReaderPostListType.TAG_FOLLOWED,
            onButtonClicked = { _, _, _ -> },
            onItemClicked = { _, _ -> },
            onItemRendered = {},
            onMoreButtonClicked = {},
            onMoreDismissed = {},
            onVideoOverlayClicked = { _, _ -> },
            onPostHeaderViewClicked = { _, _ -> }
        )

        builder.mapPostToUiStateBlocking(
            source = "test",
            post = post2,
            photonWidth = 100,
            photonHeight = 100,
            postListType = ReaderPostListType.TAG_FOLLOWED,
            onButtonClicked = { _, _, _ -> },
            onItemClicked = { _, _ -> },
            onItemRendered = {},
            onMoreButtonClicked = {},
            onMoreDismissed = {},
            onVideoOverlayClicked = { _, _ -> },
            onPostHeaderViewClicked = { _, _ -> }
        )

        // Assert - fake tracks number of calls
        assertThat(fakeImageScanner.getScanCount()).isEqualTo(2)
    }

    @Test
    fun `FAKE - empty gallery returns empty image list`() = test {
        // Arrange
        fakeImageScanner.setGalleryImages(emptyList())
        val post = createTestPost(cardType = ReaderCardType.GALLERY)

        // Act
        val uiState = builder.mapPostToUiStateBlocking(
            source = "test",
            post = post,
            photonWidth = 100,
            photonHeight = 100,
            postListType = ReaderPostListType.TAG_FOLLOWED,
            onButtonClicked = { _, _, _ -> },
            onItemClicked = { _, _ -> },
            onItemRendered = {},
            onMoreButtonClicked = {},
            onMoreDismissed = {},
            onVideoOverlayClicked = { _, _ -> },
            onPostHeaderViewClicked = { _, _ -> }
        )

        // Assert
        assertThat(uiState.thumbnailStripUrls?.images).isEmpty()
    }

    // endregion

    // region COMBINATION TESTS - Combination of mocks, stubs and fakes

    @Test
    fun `COMBINATION - all test doubles work together correctly`() = test {
        // Arrange - combine all types of test doubles
        stubDateTimeUtils.setFixedTimeSpan("3 hours ago")
        fakeImageScanner.setGalleryImages(listOf("img1.jpg", "img2.jpg"))
        whenever(accountStore.hasAccessToken()).thenReturn(true)
        
        val post = createTestPost(
            cardType = ReaderCardType.GALLERY,
            blogUrl = "https://test.wordpress.com"
        )

        // Act
        val uiState = builder.mapPostToUiStateBlocking(
            source = "test",
            post = post,
            photonWidth = 100,
            photonHeight = 100,
            postListType = ReaderPostListType.TAG_FOLLOWED,
            onButtonClicked = { _, _, _ -> },
            onItemClicked = { _, _ -> },
            onItemRendered = {},
            onMoreButtonClicked = {},
            onMoreDismissed = {},
            onVideoOverlayClicked = { _, _ -> },
            onPostHeaderViewClicked = { _, _ -> }
        )

        // Assert - verify results from all test doubles
        assertThat(uiState.dateLine).isEqualTo("3 hours ago") // Stub
        assertThat(uiState.thumbnailStripUrls?.images).hasSize(2) // Fake
        verify(accountStore).hasAccessToken() // Mock
        verify(urlUtilsWrapper).removeScheme(any()) // Mock
    }

    // endregion

    // region Helper Methods

    private fun createTestPost(
        cardType: ReaderCardType = ReaderCardType.DEFAULT,
        blogUrl: String = "https://example.com",
        postAvatar: String = "https://avatar.example.com/avatar.jpg"
    ): ReaderPost {
        return ReaderPost().apply {
            this.postId = 123L
            this.blogId = 456L
            this.feedId = 789L
            this.cardType = cardType
            this.blogUrl = blogUrl
            this.postAvatar = postAvatar
            this.title = "Test Post"
            this.text = "Test content"
            this.excerpt = "Test excerpt"
            this.authorName = "Test Author"
            this.blogName = "Test Blog"
            this.featuredImage = "https://example.com/featured.jpg"
            this.isLikedByCurrentUser = false
            this.isFollowedByCurrentUser = false
            this.numLikes = 10
            this.numReplies = 5
            this.canLikePost = true
            this.isCommentsOpen = true
            this.isWP = true
        }
    }

    // endregion
}

// region TEST DOUBLES IMPLEMENTATIONS

/**
 * FAKE - In-memory implementation of ReaderImageScanner
 * Simulates image scanner behavior without real processing
 */
class FakeReaderImageScanner : ReaderImageScanner {
    private var galleryImages: List<String> = emptyList()
    private var scanCount = 0

    fun setGalleryImages(images: List<String>) {
        this.galleryImages = images
    }

    fun getScanCount(): Int = scanCount

    override fun getImageList(minImageWidth: Int, maxImageCount: Int): ReaderImageList {
        scanCount++
        return ReaderImageList(galleryImages.isNotEmpty()).apply {
            galleryImages.forEach { addImageUrl(it, minImageWidth, minImageWidth) }
        }
    }

    override fun beginScan() {
        // No-op in fake implementation
    }

    override fun getBestFeaturedImage(): String? {
        return galleryImages.firstOrNull()
    }
}

/**
 * STUB - Stub for DateTimeUtils with predictable behavior
 */
class StubDateTimeUtils {
    private var fixedTimeSpan: String = "1 hour ago"

    fun setFixedTimeSpan(timeSpan: String) {
        this.fixedTimeSpan = timeSpan
    }

    fun formatTimeSpan(date: Any?): String {
        return fixedTimeSpan
    }
}

// endregion
