package com.nodeplayer.music.support

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object SupportConfig {
    const val UPI_ID = "7063061981@fam"
    const val PAYEE_NAME = "Krighz"
    const val UPI_PAY_URI = "upi://pay?pa=7063061981@fam&pn=Krighz"
    const val SUPPORT_URL = "https://krighz.github.io/node-player/#support"

    fun copyUpiId(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("Node Player UPI ID", UPI_ID)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "Copied UPI ID: $UPI_ID", Toast.LENGTH_SHORT).show()
    }

    fun openUpiPayment(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(UPI_PAY_URI)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            copyUpiId(context)
            Toast.makeText(context, "Copied UPI ID ($UPI_ID). Paste it in any UPI app", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            copyUpiId(context)
            Toast.makeText(context, "Copied UPI ID ($UPI_ID). Paste it in any UPI app", Toast.LENGTH_LONG).show()
        }
    }
}
