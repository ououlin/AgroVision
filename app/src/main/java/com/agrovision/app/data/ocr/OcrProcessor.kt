package com.agrovision.app.data.ocr

import android.graphics.Bitmap
import javax.inject.Singleton

@Singleton
class OcrProcessor {

    suspend fun recognizeText(bitmap: Bitmap): Result<String> {
        return Result.success("OCR is not wired yet.")
    }

    fun parsePesticideInfo(text: String): PesticideInfo {
        return PesticideInfo(rawText = text)
    }

    fun parseFertilizerInfo(text: String): FertilizerInfo {
        return FertilizerInfo(rawText = text)
    }
}

data class PesticideInfo(
    val productName: String = "",
    val activeIngredient: String = "",
    val content: String = "",
    val dosageForm: String = "",
    val target: String = "",
    val dosage: String = "",
    val safetyInterval: String = "",
    val maxUses: String = "",
    val precautions: String = "",
    val rawText: String = ""
)

data class FertilizerInfo(
    val productName: String = "",
    val npk: String = "",
    val organicMatter: String = "",
    val applicableCrops: String = "",
    val usage: String = "",
    val dosage: String = "",
    val rawText: String = ""
)
