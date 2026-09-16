package com.gc.waravi.skyway.popservice.atpop

import java.text.ParseException
import java.text.SimpleDateFormat

/**
 * フィルタリング関連のユーティリティクラス
 */
class FilterUtil {

    companion object {
        private const val fNum = 1
        private const val sNum = 2
        private const val keySiz = 16

        /**
         * フィルタIDをユーザー情報と比較して対象かのチェック
         *
         * @param filterID フィルタID
         * @param DAO 各ユーザー情報
         * @return true=対象、false=非対象
         */
        fun filterJudgment(filterID:String, userinfodb: Triple<Map<String, String>, Map<String, String>, Map<String, String>>):Boolean {
            // フィルタIDが無いメールは常に表示
            if (filterID == "")return true

            val charAll = filterID.toCharArray()
            // デフォルト判定
            val defaultUserInfo = getSegment(charAll)

            defaultUserInfo?.first?.map { it ->
                val itdb = userinfodb.first.filter { userInfo -> userInfo.key == it.key}[it.key]
                if (itdb != null && itdb != "") {
                    when(it.key) {
                        "3" -> {
                            if (!dataRengeCheck(it.value, itdb)) return false
                        }
                        else -> {
                            if (!filterBitwise(itdb, it.value)) return false
                        }
                    }
                } else {
                    return false
                }
            }

            if(defaultUserInfo?.second != null) {
                // モジュール判定
                val moduleUserInfo = getSegment(defaultUserInfo.second)
                moduleUserInfo?.first?.map { it ->
                    val itdb = userinfodb.second.filter { userInfodb -> userInfodb.key == it.key}[it.key]
                    if (itdb != null && itdb != "") {
                        if (!filterBitwise(itdb,it.value)) return false
                    }
                    else {
                        return false
                    }
                }
                if (moduleUserInfo?.second != null) {
                    // フリー判定
                    val freeUserInfo = getSegment(moduleUserInfo.second)
                    freeUserInfo?.first?.map { it ->
                        val itdb = userinfodb.third.filter { userInfodb -> userInfodb.key == it.key}[it.key]
                        if (itdb != null && itdb != "") {
                            if (!filterBitwise(itdb,it.value)) return false
                        } else
                        {
                            return true
                        }
                    }
                    return true
                }
            }
            return false
        }

        /**
         * 件名からフィルタIDと予約領域の情報を分離する
         *
         * @param subject 件名データ
         * @return 件名、フィルタID、予約領域
         */
        fun subjectCheck(subject: String): Triple<String, String, String> {
            val (setSubject, id) = this.delimiterKey(subject) ?: return Triple(subject,"","")
            if(!checkHexadecimal(id)) return Triple(setSubject, id,"")

            val checkSet = id.toCharArray()
            val setReservedArea = stringLin(checkSet, keySiz) ?: return Triple(setSubject, id,"")

            val charAll = checkSet.copyOf(checkSet.size - keySiz)

            var checksegment = getSegment(
                getSegment(
                    getSegment(charAll)?.second ?:return Triple(setSubject, id,""))
                    ?.second ?:return Triple(setSubject, id,""))
                ?.second ?:return Triple(setSubject, id,"")
            if(checksegment.isNotEmpty())return Triple(setSubject, id,"")

            return Triple(setSubject,
                charAll.contentToString()
                    .replace(",","").replace(" ","")
                    .replace("[","").replace("]","")
                , setReservedArea)
        }

        fun checkHexadecimal(checkData:String):Boolean {
            val regex = Regex("[0-9a-fA-F]+")
            if (!regex.matches(checkData)) {
                return false
            }
            return true
        }

        private fun filterBitwise(filterValue:String, segmentValue:String):Boolean {
            val segmentValuetoULong = segmentValue.toULong(16)
            val segmentFilterValuetoULong = (1.toULong().shl(filterValue.toInt() -1))
            if((segmentValuetoULong and segmentFilterValuetoULong) == segmentFilterValuetoULong) return true
            return false
        }

        private fun delimiterKey(titls: String): Pair<String, String>? {
            return when(val setInt = titls.lastIndexOf("|")) {
                null -> null
                -1 -> null
                else -> Pair(titls.substring(0, setInt), titls.substring(setInt + 1, titls.length))
            }
        }

        private tailrec fun stringLin(setChar: CharArray, end: Int): String? =
            if(setChar.size >= end) setChar.concatToString(startIndex = setChar.size -end, endIndex = setChar.size) else null

        private tailrec fun stringLin(top: Int, setChar: CharArray): String? =
            if(setChar.size >= top) setChar.concatToString(startIndex = 0, endIndex = top) else null

        private tailrec fun stringLin(top: Int, setChar: CharArray, end: Int): String? =
            if(setChar.size >= end) setChar.concatToString(startIndex = top, endIndex = end) else null

        private fun getSegment(setData: CharArray): Pair<MutableMap<String, String>?, CharArray>? {
            var setLin = 0
            var setflength: String? = stringLin(sNum, setData) ?: return null
            setLin += sNum

            //一回のループ
            val dataNum: String? =
                stringLin(setLin, setData, setLin + (setflength?.toInt(16) ?: return null)) ?: return null

            if(setflength.toInt(16) == 0) return Pair(null, setData.copyOfRange(setLin, setData.size))
            setLin += setflength.toInt(16)

            val setMap = mutableMapOf<String, String>()
            if (dataNum != null) {
                for (i in 1..dataNum.length step Companion.fNum) {
                    val setData = stringLin(setLin,
                        setData,
                        (setLin + dataNum[i - 1].toString().toLong(16)).toInt())
                    when (setData) {
                        null -> return null
                        "" -> null//何もしない
                        "0" -> return null
                        else -> {
                            setData.toLong(16).toString()
                            setMap[i.toString()] = setData
                        }
                    }
                    setLin = (setLin + dataNum[i - 1].toString().toLong(16)).toInt()
                }
            }
            return Pair(setMap, setData.copyOfRange(setLin, setData.size))
        }

        private fun dataLimit(filDataStart: String, filDataEnd: String, UData: String): Boolean{
            if((UData.toInt(16) >= filDataStart.toInt(16)) &&
                (UData.toInt(16) <= filDataEnd.toInt(16))) return true
            return false
        }

        fun dataRengeCheck(data: String, dataUse: String):Boolean {
            //変換　yymmdd(16)　→　yyyymmdd(10)+1900y
            when (data.substring(0,1)) {
                "1" -> {
                    val startDate = StringBuilder()
                        .append(
                            (data.substring(1,3).toInt(16) + 1900 - 1).toString()
                        ).append(
                            String.format("%02d", data.substring(3,4).toInt(16))
                        ).append(
                            String.format("%02d", (data.substring(4,6)).toInt(16))
                        ).toString()
                    if(!dateFormatCheck(startDate)) return false

                    val endDate = StringBuilder()
                        .append(
                            (data.substring(6, 8).toInt(16) + 1900 - 1).toString()
                        ).append(
                            String.format("%02d", (data.substring(8, 9)).toInt(16))
                        ).append(
                            String.format("%02d", (data.substring(9, 11)).toInt(16))
                        ).toString()
                    if(!dateFormatCheck(endDate)) return false
                    return dataLimit(startDate, endDate, dataUse)
                }
                "2" -> {
                    val startDate = StringBuilder()
                        .append(
                            if(data.substring(1,3) == "00")  dataUse.substring(0,4) else (data.substring(1,3).toInt(16) + 1900 - 1).toString()
                        ).append(
                            if(data.substring(3,4) == "0")  dataUse.substring(4,6) else String.format("%02d", data.substring(3,4).toInt(16))
                        ).append(
                            if(data.substring(4,6) == "00")  dataUse.substring(6,8) else String.format("%02d", (data.substring(4,6)).toInt(16))
                        ).toString()
                    return dataLimit(startDate, startDate, dataUse)
                }
                else -> false
            }
            return false
        }

        fun dateFormatCheck(data: String):Boolean {
            //日付フォーマットチェック
            val dateFormat = SimpleDateFormat("yyyyMMdd")
            dateFormat.isLenient = false
            return try{
                ((dateFormat.parse(data).before(dateFormat.parse("21550101")))
                        && (dateFormat.parse(data).after(dateFormat.parse("18991231"))))
            } catch (e: ParseException) {
                //フォーマット異常
                false
            }
        }
    }
}
