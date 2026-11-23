package com.nodeplayer.library.lyrics.romanization

object UrduMusicalLexicon {
    private val POETIC_DICTIONARY = mapOf(
        "دل" to "Dil", "محبت" to "Mohabbat", "عشق" to "Ishq", "زندگی" to "Zindagi",
        "تیرے" to "Tere", "میرے" to "Mere", "تجھ" to "Tujh", "مجھ" to "Mujh",
        "تم" to "Tum", "ہم" to "Hum", "ہوا" to "Hawa", "رات" to "Raat",
        "یاد" to "Yaad", "خدا" to "Khuda", "صنم" to "Sanam", "جان" to "Jaan",
        "پاس" to "Paas", "ساتھ" to "Saath", "کیوں" to "Kyun", "نہیں" to "Nahin",
        "کوئی" to "Koi", "کبھی" to "Kabhi", "اک" to "Ik", "یہی" to "Yehi"
    )

    fun preprocessUrdu(text: String): String {
        val words = text.split(Regex("\\s+"))
        return words.joinToString(" ") { word ->
            val clean = word.replace(Regex("[،۔؟!؟]"), "")
            POETIC_DICTIONARY[clean] ?: normalizeUrduCharacters(word)
        }
    }

    private fun normalizeUrduCharacters(word: String): String {
        return word
            .replace('ے', 'e')
            .replace('ں', 'n')
            .replace("ٹ", "t")
            .replace("ڈ", "d")
            .replace("ڑ", "r")
            .replace("ھ", "h")
    }
}
