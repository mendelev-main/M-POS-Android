package com.mendelev.mpos.domain.report

import java.math.BigDecimal
import java.math.BigInteger

/** Facts from the receipt and its immutable opening-shift snapshot, never the live directory/catalogue. */
data class ReportDetailLine(val id: String,val productId: String,val name: String,val categoryId: String,
    val categoryName: String,val unit: String,val quantity: BigDecimal,val netMinor: Long,val costMinor: BigInteger)
data class ReportDetailSale(val receiptId: String,val employeeId: String,val employeeName: String,val lines: List<ReportDetailLine>)
