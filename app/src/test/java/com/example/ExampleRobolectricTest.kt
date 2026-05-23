package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.VravCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    assertEquals("VRAV Messenger", appName)
  }

  @Test
  fun `keystore secure key storing encryption and decryption`() {
    val originalPrivateKey = "super_secret_sovereign_private_key_bytes_hex"
    
    // Encrypt securely via Keystore AndroidKeyStore provider
    val encryptedText = VravCrypto.encryptPrivateKeySecurely(originalPrivateKey)
    
    assertNotEquals(originalPrivateKey, encryptedText)
    
    // Decrypt securely back to memory RAM
    val decryptedText = VravCrypto.decryptPrivateKeySecurely(encryptedText)
    
    assertEquals(originalPrivateKey, decryptedText)
  }
}
