package com.dsc.pdfmaster.pdfengine

sealed class PageRangeResult {
    data class Success(val pageNumbers: List<Int>) : PageRangeResult()
    data class Error(val message: String) : PageRangeResult()
}

object PageRangeParser {

    /**
     * Parses a page range string such as "1-3, 5, 8-10" into a list of 1-based page numbers.
     * Validates that all page numbers are within 1..totalPages, ranges are ordered (start <= end),
     * and input syntax is valid.
     */
    fun parse(input: String, totalPages: Int): PageRangeResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return PageRangeResult.Error("Enter at least one page number or range (e.g. 1-3, 5)")
        }

        if (totalPages <= 0) {
            return PageRangeResult.Error("Total pages must be at least 1")
        }

        val tokens = trimmed.split(",")
        val resultPages = mutableSetOf<Int>()

        for (rawToken in tokens) {
            val token = rawToken.trim()
            if (token.isEmpty()) {
                return PageRangeResult.Error("Empty token found. Check for trailing or consecutive commas.")
            }

            if (token.contains("-")) {
                val parts = token.split("-")
                if (parts.size != 2) {
                    return PageRangeResult.Error("Invalid range expression: '$token'")
                }
                val startStr = parts[0].trim()
                val endStr = parts[1].trim()

                if (startStr.isEmpty() || endStr.isEmpty()) {
                    return PageRangeResult.Error("Incomplete range: '$token'")
                }

                val start = startStr.toIntOrNull()
                    ?: return PageRangeResult.Error("Invalid start page number in '$token'")
                val end = endStr.toIntOrNull()
                    ?: return PageRangeResult.Error("Invalid end page number in '$token'")

                if (start <= 0 || end <= 0) {
                    return PageRangeResult.Error("Page numbers must be 1 or greater")
                }

                if (start > end) {
                    return PageRangeResult.Error("Invalid range '$token': start page ($start) cannot be greater than end page ($end)")
                }

                if (start > totalPages || end > totalPages) {
                    val max = maxOf(start, end)
                    return PageRangeResult.Error("Page $max exceeds total page count of $totalPages")
                }

                for (p in start..end) {
                    resultPages.add(p)
                }
            } else {
                val singlePage = token.toIntOrNull()
                    ?: return PageRangeResult.Error("Invalid page number: '$token'")

                if (singlePage <= 0) {
                    return PageRangeResult.Error("Page numbers must be 1 or greater")
                }

                if (singlePage > totalPages) {
                    return PageRangeResult.Error("Page $singlePage exceeds total page count of $totalPages")
                }

                resultPages.add(singlePage)
            }
        }

        if (resultPages.isEmpty()) {
            return PageRangeResult.Error("No valid pages selected")
        }

        return PageRangeResult.Success(resultPages.sorted())
    }

    /**
     * Converts a collection of 1-based page numbers into a canonical range string,
     * e.g. [1, 2, 3, 5, 8, 9, 10] -> "1-3, 5, 8-10".
     */
    fun formatToRangeString(pageNumbers: Collection<Int>): String {
        if (pageNumbers.isEmpty()) return ""
        val sorted = pageNumbers.distinct().sorted()

        val ranges = mutableListOf<String>()
        var rangeStart = sorted[0]
        var rangeEnd = sorted[0]

        for (i in 1 until sorted.size) {
            val current = sorted[i]
            if (current == rangeEnd + 1) {
                rangeEnd = current
            } else {
                if (rangeStart == rangeEnd) {
                    ranges.add("$rangeStart")
                } else {
                    ranges.add("$rangeStart-$rangeEnd")
                }
                rangeStart = current
                rangeEnd = current
            }
        }

        if (rangeStart == rangeEnd) {
            ranges.add("$rangeStart")
        } else {
            ranges.add("$rangeStart-$rangeEnd")
        }

        return ranges.joinToString(", ")
    }
}
