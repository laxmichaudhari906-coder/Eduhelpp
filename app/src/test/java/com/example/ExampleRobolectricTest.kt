package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.SampleStudyData
import com.example.data.repository.EduHelpRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Edu Help", appName)
  }

  @Test
  fun `sample study data has cards and quizzes`() {
    assertTrue(SampleStudyData.cards.isNotEmpty())
    assertTrue(SampleStudyData.sampleQuizzes.isNotEmpty())
    assertEquals(6, SampleStudyData.cards.size)
  }

  @Test
  fun `repository pairing stores and retrieves pair id locally`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = EduHelpRepository(context)
    val testPairId = "TEST-2026"
    val result = repo.connectPair(testPairId)
    assertTrue(result.isSuccess)
    assertEquals(testPairId, repo.getSavedPairId())
    assertNotNull(repo.getSavedRoomId())

    // Disconnect
    repo.disconnectPair()
    assertEquals(null, repo.getSavedPairId())
  }
}

