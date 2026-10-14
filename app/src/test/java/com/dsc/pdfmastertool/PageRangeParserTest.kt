package com.dsc.pdfmastertool

import com.dsc.pdfmastertool.pdfengine.PageRangeParser
import com.dsc.pdfmastertool.pdfengine.PageRangeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageRangeParserTest {

    @Test
    fun `parse single page number correctly`() {
        val result = PageRangeParser.parse("4", 10)
        assertTrue(result is PageRangeResult.Success)
        assertEquals(listOf(4), (result as PageRangeResult.Success).pageNumbers)
    }

    @Test
    fun `parse single range correctly`() {
        val result = PageRangeParser.parse("1-4", 10)
        assertTrue(result is PageRangeResult.Success)
        assertEquals(listOf(1, 2, 3, 4), (result as PageRangeResult.Success).pageNumbers)
    }

    @Test
    fun `parse complex ranges with spaces and singles`() {
        val result = PageRangeParser.parse(" 1-3 , 5 , 8-10 ", 10)
        assertTrue(result is PageRangeResult.Success)
        assertEquals(listOf(1, 2, 3, 5, 8, 9, 10), (result as PageRangeResult.Success).pageNumbers)
    }

    @Test
    fun `parse overlapping and duplicate ranges and dedup`() {
        val result = PageRangeParser.parse("1-3, 2-4, 3", 10)
        assertTrue(result is PageRangeResult.Success)
        assertEquals(listOf(1, 2, 3, 4), (result as PageRangeResult.Success).pageNumbers)
    }

    @Test
    fun `reversed range fails with clear error`() {
        val result = PageRangeParser.parse("5-2", 10)
        assertTrue(result is PageRangeResult.Error)
        val errorMsg = (result as PageRangeResult.Error).message
        assertTrue(errorMsg.contains("cannot be greater than end page"))
    }

    @Test
    fun `page exceeding total pages fails`() {
        val result = PageRangeParser.parse("1-15", 10)
        assertTrue(result is PageRangeResult.Error)
        val errorMsg = (result as PageRangeResult.Error).message
        assertTrue(errorMsg.contains("exceeds total page count"))
    }

    @Test
    fun `zero or negative page fails`() {
        val result = PageRangeParser.parse("0-4", 10)
        assertTrue(result is PageRangeResult.Error)
    }

    @Test
    fun `malformed syntax fails`() {
        val res1 = PageRangeParser.parse("1,,3", 10)
        assertTrue(res1 is PageRangeResult.Error)

        val res2 = PageRangeParser.parse("abc", 10)
        assertTrue(res2 is PageRangeResult.Error)

        val res3 = PageRangeParser.parse("1-", 10)
        assertTrue(res3 is PageRangeResult.Error)
    }

    @Test
    fun `canonical range formatting converts page list to clean string`() {
        val formatted = PageRangeParser.formatToRangeString(listOf(1, 2, 3, 5, 8, 9, 10))
        assertEquals("1-3, 5, 8-10", formatted)

        val single = PageRangeParser.formatToRangeString(listOf(4))
        assertEquals("4", single)

        val twoConsecutive = PageRangeParser.formatToRangeString(listOf(1, 2))
        assertEquals("1-2", twoConsecutive)

        val disjoint = PageRangeParser.formatToRangeString(listOf(2, 5, 7))
        assertEquals("2, 5, 7", disjoint)

        val empty = PageRangeParser.formatToRangeString(emptyList())
        assertEquals("", empty)
    }
}
