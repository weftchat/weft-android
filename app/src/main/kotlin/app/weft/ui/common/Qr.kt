package app.weft.ui.common

import app.weft.design.QrModules
import io.nayuki.qrcodegen.QrCode

/** Encodes [text] as a QR code (medium error correction) for [app.weft.design.QrCard]. */
fun qrModules(text: String): QrModules {
    val qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM)
    return QrModules(qr.size) { x, y -> qr.getModule(x, y) }
}
