package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class PinataResult {
    data class Success(val cid: String, val size: Long) : PinataResult()
    data class Error(val errorMessage: String) : PinataResult()
}

object PinataUploader {
    private const val TAG = "PinataUploader"
    
    // Configured client with robust timeout options suitable for mobile networks
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Uploads bytes (ciphertext) to Pinata IPFS on behalf of the user.
     * Uses Pinata API: POST https://api.pinata.cloud/pinning/pinFileToIPFS
     */
    suspend fun uploadToPinata(
        fileBytes: ByteArray,
        fileName: String,
        jwtToken: String
    ): PinataResult = withContext(Dispatchers.IO) {
        if (jwtToken.isBlank() || jwtToken == "DEMO_TOKEN") {
            // Generate standard unique IPFS CID simulation
            val fakeHash = "Qm" + (0..43).map { 
                "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".random() 
            }.joinToString("")
            Log.d(TAG, "Simulated Pinata IPFS file pinning. Token empty. Generated simulated CID: $fakeHash")
            return@withContext PinataResult.Success(fakeHash, fileBytes.size.toLong())
        }

        try {
            val endpoint = "https://api.pinata.cloud/pinning/pinFileToIPFS"
            
            val filePart = MultipartBody.Part.createFormData(
                "file",
                fileName,
                fileBytes.toRequestBody("application/octet-stream".toMediaTypeOrNull())
            )

            // Building metadata body
            val metadataJson = JSONObject().apply {
                put("name", fileName)
                put("keyvalues", JSONObject().apply {
                    put("origin", "vrav_sovereign_messenger")
                    put("crypto_envelope", "aes_256_gcm_kyber_x25519")
                })
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(filePart)
                .addFormDataPart("pinataMetadata", metadataJson.toString())
                .build()

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $jwtToken")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val ipfsHash = json.getString("IpfsHash")
                    val pinSize = json.optLong("PinSize", fileBytes.size.toLong())
                    Log.d(TAG, "Successfully pinned to Pinata! CID: $ipfsHash")
                    PinataResult.Success(ipfsHash, pinSize)
                } else {
                    val errorStr = response.body?.string() ?: ""
                    Log.e(TAG, "Upload failure: ${response.code} -> $errorStr")
                    PinataResult.Error("API Limit/Auth Error (${response.code}): $errorStr")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception uploading stream", e)
            PinataResult.Error(e.localizedMessage ?: "Unknown network exception")
        }
    }

    /**
     * Unpins a pinned CID from Pinata on demand to maintain transience.
     * Uses Pinata API: DELETE https://api.pinata.cloud/pinning/unpin/{hash}
     */
    suspend fun unpinFromPinata(
        cid: String,
        jwtToken: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (jwtToken.isBlank() || jwtToken == "DEMO_TOKEN") {
            Log.d(TAG, "Simulated Pinata unpin: Successfully removed simulated CID $cid from sandbox nodes")
            return@withContext true
        }

        try {
            val endpoint = "https://api.pinata.cloud/pinning/unpin/$cid"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $jwtToken")
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully unpinned CID from Pinata: $cid")
                    true
                } else {
                    val err = response.body?.string() ?: ""
                    Log.e(TAG, "Unpin failure: ${response.code} -> $err")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during unpin", e)
            false
        }
    }
}
