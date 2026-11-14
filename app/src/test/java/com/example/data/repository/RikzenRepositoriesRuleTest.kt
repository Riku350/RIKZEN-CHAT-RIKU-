package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class RikzenRepositoriesRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createProfile_andSearchByRikzenId_worksCrossAccount() = runBlocking {
        val aliceUid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val aliceRepo = ProfileAndFriendsRepository(firestore, auth)
        val uniqueId = "RIKZEN-${UUID.randomUUID().toString().take(6).uppercase()}"

        val createRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createAccountProfile(
                displayName = "Alice Mercer",
                email = "alice@test.com",
                bio = "Gold member",
                phoneNumber = "+15550101234",
                dob = "1998-01-15",
                photoBase64 = "",
                isDiscoverableByPhone = true,
                isDiscoverableByName = true,
                preferredRikzenId = uniqueId
            )
        }
        assertTrue("Profile creation failed: ${createRes.exceptionOrNull()}", createRes.isSuccess)
        val aliceProfile = createRes.getOrThrow()
        assertEquals(uniqueId, aliceProfile.rikzenId)

        // Bob searches for Alice by RIKZEN ID
        signInTestUser("bob_${UUID.randomUUID()}@test.com")
        val bobRepo = ProfileAndFriendsRepository(firestore, auth)
        val searchRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.searchPeople(uniqueId)
        }
        assertTrue(searchRes.isSuccess)
        assertTrue(searchRes.getOrThrow().any { it.uid == aliceUid })
    }

    @Test
    fun friendRequest_acceptAndRealTimeMessaging_worksBetweenAccounts() = runBlocking {
        val aliceEmail = "alice_${UUID.randomUUID()}@test.com"
        val bobEmail = "bob_${UUID.randomUUID()}@test.com"

        val aliceUid = signInTestUser(aliceEmail)
        val aliceProfileRepo = ProfileAndFriendsRepository(firestore, auth)
        val aliceProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceProfileRepo.createAccountProfile(
                displayName = "Alice",
                email = aliceEmail,
                bio = "",
                phoneNumber = "",
                dob = "",
                photoBase64 = "",
                isDiscoverableByPhone = false,
                isDiscoverableByName = true
            ).getOrThrow()
        }

        signInTestUser(bobEmail)
        val bobProfileRepo = ProfileAndFriendsRepository(firestore, auth)
        val bobProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobProfileRepo.createAccountProfile(
                displayName = "Bob",
                email = bobEmail,
                bio = "",
                phoneNumber = "",
                dob = "",
                photoBase64 = "",
                isDiscoverableByPhone = false,
                isDiscoverableByName = true
            ).getOrThrow()
        }

        // Alice sends friend request to Bob
        signInTestUser(aliceEmail)
        val sendRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceProfileRepo.sendFriendRequest(aliceProfile, bobProfile)
        }
        assertTrue("sendFriendRequest failed: ${sendRes.exceptionOrNull()}", sendRes.isSuccess)

        // Bob accepts
        signInTestUser(bobEmail)
        val bobRequests = withTimeout(FLOW_TIMEOUT_MS) {
            bobProfileRepo.observeFriendRequests(bobProfile.uid).first { it.isNotEmpty() }
        }
        val acceptRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobProfileRepo.acceptFriendRequest(bobRequests.first(), bobProfile)
        }
        assertTrue("acceptFriendRequest failed: ${acceptRes.exceptionOrNull()}", acceptRes.isSuccess)
        val dmId = acceptRes.getOrThrow()

        // Bob sends a real-time message
        val bobChatRepo = ChatRepository(firestore, auth)
        val dmConv = withTimeout(FLOW_TIMEOUT_MS) {
            bobChatRepo.observeConversation(dmId, bobProfile.uid).first { it != null }!!
        }
        val msgRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobChatRepo.sendMessage(dmConv, bobProfile.displayName, "Hello from Bob!")
        }
        assertTrue("sendMessage failed: ${msgRes.exceptionOrNull()}", msgRes.isSuccess)

        // Alice observes message in real time
        signInTestUser(aliceEmail)
        val aliceChatRepo = ChatRepository(firestore, auth)
        val msgs = withTimeout(FLOW_TIMEOUT_MS) {
            aliceChatRepo.observeMessages(dmId, aliceUid).first { list -> list.any { it.text == "Hello from Bob!" } }
        }
        assertTrue(msgs.any { it.text == "Hello from Bob!" })
    }

    @Test
    fun observeConversations_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val chatRepo = ChatRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                chatRepo.observeConversations("any_uid").first()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertNotNull("Expected Firestore exception, got $e", firestoreEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx!!.code)
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 6000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
