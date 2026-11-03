package com.neuralroots.kosh.sms

import com.neuralroots.kosh.data.db.PaymentMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {
    private val ts = 1_762_150_000_000L

    private fun p(sender: String, body: String) = SmsParser.parse(sender, body, ts)

    @Test fun hdfcUpiSent() {
        val d = p("AX-HDFCBK-S", "Sent Rs.250.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY\nOn 03/11/26\nRef 430712345678\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808")
        assertNotNull(d); d!!
        assertEquals(25000L, d.amount); assertTrue(d.isDebit)
        assertEquals("1234", d.accountLast4); assertEquals("Swiggy", d.merchant)
        assertEquals("430712345678", d.refNo)
    }

    @Test fun sbiUpiDebitNoCurrency() {
        val d = p("VM-SBIUPI", "Dear UPI user A/C X5678 debited by 1499.0 on date 02Nov26 trf to ZOMATO LTD Refno 430698765432. If not u? call 1800111109. -SBI")
        assertNotNull(d); d!!
        assertEquals(149900L, d.amount); assertTrue(d.isDebit)
        assertEquals("5678", d.accountLast4); assertEquals("Zomato Ltd", d.merchant)
        assertEquals(PaymentMode.UPI, d.mode)
    }

    @Test fun iciciDebitWithMerchantCredited() {
        val d = p("JD-ICICIT", "ICICI Bank Acct XX123 debited for Rs 2,350.00 on 01-Nov-26; AMAZON PAY credited. UPI:430611112222. Call 18002662 for dispute. SMS BLOCK 123 to 9215676766.")
        assertNotNull(d); d!!
        assertEquals(235000L, d.amount); assertTrue(d.isDebit)
        assertEquals("123", d.accountLast4); assertEquals("Amazon Pay", d.merchant)
        assertEquals("430611112222", d.refNo)
    }

    @Test fun axisCardSpend() {
        val d = p("BZ-AXISBK", "Spent INR 899 Axis Bank Card no. XX4321 02-11-26 19:42:10 IST at DMART AVENUE Avl Limit: INR 1,45,200.50 Not you? SMS BLOCK 4321 to 919951860002")
        assertNotNull(d); d!!
        assertEquals(89900L, d.amount); assertTrue(d.isDebit)
        assertEquals("4321", d.accountLast4); assertEquals("Dmart Avenue", d.merchant)
        assertEquals(PaymentMode.CARD, d.mode)
    }

    @Test fun kotakUpiReceived() {
        val d = p("AD-KOTAKB", "Received Rs.500.00 in your Kotak Bank a/c X9012 from rahul.sharma99@okaxis on 03-11-26.UPI Ref:430799998888.")
        assertNotNull(d); d!!
        assertEquals(50000L, d.amount); assertTrue(!d.isDebit)
        assertEquals("9012", d.accountLast4); assertEquals("Rahul Sharma", d.merchant)
    }

    @Test fun salaryNeftCredit() {
        val d = p("VK-HDFCBK", "Update! INR 85,000.00 deposited in HDFC Bank A/c XX1234 on 31-OCT-26 for NEFT Cr-CITI0000001-ACME TECHNOLOGIES PVT LTD-SALARY OCT.Avl bal INR 1,02,345.60. Cheque deposits in A/C are subject to clearing")
        assertNotNull(d); d!!
        assertEquals(8_500_000L, d.amount); assertTrue(!d.isDebit)
        assertEquals("1234", d.accountLast4)
        assertEquals(PaymentMode.NET_BANKING, d.mode)
    }

    @Test fun atmWithdrawal() {
        val d = p("AX-SBIINB", "Dear Customer, Rs.2000 withdrawn at SBI ATM S1AW000123 from A/cX5678 on 02Nov26 Transaction Number 6123. Available Balance Rs.10,500. If not withdrawn by you, call 1800111109")
        assertNotNull(d); d!!
        assertEquals(200000L, d.amount); assertTrue(d.isDebit)
        assertEquals(PaymentMode.CASH, d.mode)
    }

    @Test fun nachEmiDebit() {
        val d = p("VM-ICICIB", "Your A/c XX7788 has been debited with INR 25,410.00 on 05-Nov-26 towards NACH-HDFC LTD HOME LOAN. Avl Bal INR 40,120.00")
        assertNotNull(d); d!!
        assertEquals(2_541_000L, d.amount); assertTrue(d.isDebit)
        assertEquals("7788", d.accountLast4)
        assertEquals(PaymentMode.AUTO_DEBIT, d.mode)
    }

    @Test fun paytmWalletPaid() {
        val d = p("JM-PAYTMB", "Paid Rs.120 to Chaiwala Tea Stall from Paytm Balance. Updated Balance: Rs.880. Txn ID: 31234567890")
        assertNotNull(d); d!!
        assertEquals(12000L, d.amount); assertTrue(d.isDebit)
        assertEquals("Chaiwala Tea Stall", d.merchant)
        assertEquals("31234567890", d.refNo)
    }

    @Test fun phonepeStyleVpaDebit() {
        val d = p("VM-YESBNK", "Rs 349.00 debited from A/c XX2468 to VPA netflix.upi@hdfcbank on 01-11-26. UPI Ref No 430512341234. Not you? Call 18001200")
        assertNotNull(d); d!!
        assertEquals(34900L, d.amount); assertTrue(d.isDebit)
        assertEquals("2468", d.accountLast4); assertEquals("Netflix", d.merchant)
    }

    @Test fun refundCredit() {
        val d = p("AX-HDFCBK", "Refund of Rs.1,299.00 from FLIPKART has been credited to your HDFC Bank Credit Card ending 4455 on 02-11-26.")
        assertNotNull(d); d!!
        assertEquals(129900L, d.amount); assertTrue(!d.isDebit)
        assertEquals("4455", d.accountLast4); assertEquals("Flipkart", d.merchant)
    }

    @Test fun creditCardSpendAt() {
        val d = p("VM-ICICIB", "INR 4,560.00 spent on ICICI Bank Card XX9876 on 02-Nov-26 at RELIANCE DIGITAL. Avl Lmt: INR 95,440.00. To dispute, call 18001080.")
        assertNotNull(d); d!!
        assertEquals(456000L, d.amount); assertTrue(d.isDebit)
        assertEquals("9876", d.accountLast4); assertEquals("Reliance Digital", d.merchant)
    }

    // ---------------------------------------------------------------- negatives

    @Test fun rejectsOtp() {
        assertNull(p("AX-HDFCBK", "OTP is 482913 for txn of Rs 2,999.00 at AMAZON on HDFC Bank card ending 1234. Valid for 5 mins. Do not share."))
    }

    @Test fun rejectsPromo() {
        assertNull(p("VM-HDFCBK-P", "Congratulations! You are pre-approved for a Personal Loan of Rs 5,00,000. Apply now"))
        assertNull(p("AX-AXISBK", "Get cashback up to Rs 500 on your next UPI payment. Offer valid till 30 Nov."))
    }

    @Test fun rejectsCollectRequest() {
        assertNull(p("JD-PAYTMB", "Ramesh has requested money of Rs 1,500 from you on Paytm UPI. Pay now or decline."))
    }

    @Test fun rejectsDueReminder() {
        assertNull(p("VM-ICICIB", "Your ICICI Bank Credit Card XX9876 statement is generated. Total amount due Rs 12,450.00, Minimum amount due Rs 620. Payment due date 20-Nov-26."))
        assertNull(p("AX-SBIINB", "Rs.25,410 will be debited from your A/c XX7788 on 05-Nov-26 towards NACH mandate. Please maintain sufficient balance."))
    }

    @Test fun rejectsFailed() {
        assertNull(p("AX-HDFCBK", "Your UPI txn of Rs 250 to SWIGGY has failed. Amount, if debited, will be refunded."))
    }

    @Test fun rejectsPersonalNumberAndBalanceOnly() {
        assertNull(p("+919876543210", "Rs 500 sent to you on GPay"))
        assertNull(p("AX-HDFCBK", "Available balance in your A/c XX1234 is Rs 45,000.00 as on 03-11-26."))
    }

    @Test fun fingerprintStable() {
        val a = p("AX-HDFCBK-S", "Sent Rs.250.00 From HDFC Bank A/C *1234 To SWIGGY On 03/11/26 Ref 430712345678")!!
        val b = SmsParser.parse("VK-HDFCBK", "Sent Rs.250.00 From HDFC Bank A/C *1234 To SWIGGY On 03/11/26 Ref 430712345678", ts + 90_000)!!
        assertEquals(a.fingerprint, b.fingerprint)
        assertEquals("HDFC", SmsParser.bankName("AX-HDFCBK-S"))
    }
}
