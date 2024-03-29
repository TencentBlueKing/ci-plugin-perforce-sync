package com.tencent.bk.devops.p4sync.task.util

import com.tencent.bk.devops.p4sync.task.pojo.util.DHKeyPair
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Security
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.interfaces.DHPublicKey
import javax.crypto.spec.DHParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Created by Aaron Sheng on 2017/9/29.
 */

object DHUtil {
    private val KEY_ALGORITHM = "DH"
    private val KEY_PROVIDER = "BC"
    private val SECRECT_ALGORITHM = "DES"

    // private val KEY_SIZE = 1024
    private val p = BigInteger("16560215747140417249215968347342080587", 16)
    private val g = BigInteger("1234567890", 16)

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    fun initKey(): DHKeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM, KEY_PROVIDER)
        val serverParam = DHParameterSpec(p, g, 128)
        keyPairGenerator.initialize(serverParam, SecureRandom())
        // keyPairGenerator.initialize(KEY_SIZE)
        val keyPair = keyPairGenerator.generateKeyPair()
        return DHKeyPair(keyPair.public.encoded, keyPair.private.encoded)
    }

    fun initKey(partyAPublicKey: ByteArray): DHKeyPair {
        val x509KeySpec = X509EncodedKeySpec(partyAPublicKey)
        val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
        val publicKey = keyFactory.generatePublic(x509KeySpec)

        val dhParameterSpec = (publicKey as DHPublicKey).params
        val keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM, KEY_PROVIDER)
        keyPairGenerator.initialize(dhParameterSpec)
        // keyPairGenerator.initialize(KEY_SIZE)
        val keyPair = keyPairGenerator.genKeyPair()
        return DHKeyPair(keyPair.public.encoded, keyPair.private.encoded)
    }

    fun encrypt(data: ByteArray, partAPublicKey: ByteArray, partBPrivateKey: ByteArray): ByteArray {
        val key = getSecretKey(partAPublicKey, partBPrivateKey)
        val secretKey = SecretKeySpec(key, SECRECT_ALGORITHM)
        val cipher = Cipher.getInstance(secretKey.algorithm)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher.doFinal(data)
    }

    fun decrypt(data: ByteArray, partBPublicKey: ByteArray, partAPrivateKey: ByteArray): ByteArray {
        val key = getSecretKey(partBPublicKey, partAPrivateKey)
        val secretKey = SecretKeySpec(key, SECRECT_ALGORITHM)
        val cipher = Cipher.getInstance(secretKey.algorithm)
        cipher.init(Cipher.DECRYPT_MODE, secretKey)
        return cipher.doFinal(data)
    }

    private fun getSecretKey(publicKey: ByteArray, privateKey: ByteArray): ByteArray {
        // 实例化密钥工厂
        val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
        // 初始化公钥
        val x509KeySpec = X509EncodedKeySpec(publicKey)
        // 产生公钥
        val pubKey = keyFactory.generatePublic(x509KeySpec)
        // 初始化私钥
        val pkcs8KeySpec = PKCS8EncodedKeySpec(privateKey)
        // 产生私钥
        val priKey = keyFactory.generatePrivate(pkcs8KeySpec)
        // 实例化
        val keyAgree = KeyAgreement.getInstance(KEY_ALGORITHM, KEY_PROVIDER)
        // 初始化
        keyAgree.init(priKey)
        keyAgree.doPhase(pubKey, true)
        // 生成本地密钥
        val secretKey = keyAgree.generateSecret(SECRECT_ALGORITHM)
        return secretKey.encoded
    }
}
