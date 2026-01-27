package com.nendo.argosy.libretro.ra

import android.util.Log

private const val TAG = "RAConditionParser"

enum class MemSize(val bytes: Int) {
    BIT0(0), BIT1(0), BIT2(0), BIT3(0), BIT4(0), BIT5(0), BIT6(0), BIT7(0),
    LOWER4(0), UPPER4(0),
    BYTE(1),
    WORD(2),
    TBYTE(3),
    DWORD(4)
}

enum class Operator {
    EQ, NE, LT, LE, GT, GE
}

sealed class Operand {
    data class Memory(val address: Int, val size: MemSize) : Operand()
    data class Delta(val address: Int, val size: MemSize) : Operand()
    data class Prior(val address: Int, val size: MemSize) : Operand()
    data class Value(val value: Int) : Operand()
}

data class Condition(
    val left: Operand,
    val operator: Operator,
    val right: Operand,
    val hitTarget: Int = 0,
    val flag: ConditionFlag = ConditionFlag.NONE
)

enum class ConditionFlag {
    NONE,
    RESET_IF,
    PAUSE_IF,
    ADD_SOURCE,
    SUB_SOURCE,
    ADD_HITS,
    AND_NEXT,
    OR_NEXT,
    MEASURED,
    MEASURED_IF,
    ADD_ADDRESS,
    TRIGGER
}

data class ConditionGroup(val conditions: List<Condition>)

data class AchievementDefinition(
    val id: Long,
    val coreRequirements: ConditionGroup,
    val altGroups: List<ConditionGroup>
)

object RAConditionParser {

    fun parse(memAddr: String): AchievementDefinition? {
        if (memAddr.isBlank()) return null

        return try {
            val groups = memAddr.split('S')
            if (groups.isEmpty()) return null

            val coreGroup = parseGroup(groups.first())
            val altGroups = groups.drop(1).map { parseGroup(it) }

            AchievementDefinition(
                id = 0,
                coreRequirements = coreGroup,
                altGroups = altGroups
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse condition: $memAddr", e)
            null
        }
    }

    private fun parseGroup(group: String): ConditionGroup {
        if (group.isBlank()) return ConditionGroup(emptyList())

        val conditions = group.split('_').mapNotNull { parseCondition(it.trim()) }
        return ConditionGroup(conditions)
    }

    private fun parseCondition(condStr: String): Condition? {
        if (condStr.isBlank()) return null

        var remaining = condStr
        var flag = ConditionFlag.NONE

        // Check for condition flags at the start
        when {
            remaining.startsWith("R:") -> {
                flag = ConditionFlag.RESET_IF
                remaining = remaining.drop(2)
            }
            remaining.startsWith("P:") -> {
                flag = ConditionFlag.PAUSE_IF
                remaining = remaining.drop(2)
            }
            remaining.startsWith("A:") -> {
                flag = ConditionFlag.ADD_SOURCE
                remaining = remaining.drop(2)
            }
            remaining.startsWith("B:") -> {
                flag = ConditionFlag.SUB_SOURCE
                remaining = remaining.drop(2)
            }
            remaining.startsWith("C:") -> {
                flag = ConditionFlag.ADD_HITS
                remaining = remaining.drop(2)
            }
            remaining.startsWith("N:") -> {
                flag = ConditionFlag.AND_NEXT
                remaining = remaining.drop(2)
            }
            remaining.startsWith("O:") -> {
                flag = ConditionFlag.OR_NEXT
                remaining = remaining.drop(2)
            }
            remaining.startsWith("M:") -> {
                flag = ConditionFlag.MEASURED
                remaining = remaining.drop(2)
            }
            remaining.startsWith("Q:") -> {
                flag = ConditionFlag.MEASURED_IF
                remaining = remaining.drop(2)
            }
            remaining.startsWith("I:") -> {
                flag = ConditionFlag.ADD_ADDRESS
                remaining = remaining.drop(2)
            }
            remaining.startsWith("T:") -> {
                flag = ConditionFlag.TRIGGER
                remaining = remaining.drop(2)
            }
        }

        // Find operator
        val (operatorStr, operatorIdx) = findOperator(remaining) ?: return null
        val leftStr = remaining.substring(0, operatorIdx)
        var rightStr = remaining.substring(operatorIdx + operatorStr.length)

        // Parse hit count (format: .N. at the end)
        var hitTarget = 0
        val hitMatch = Regex("\\.(\\d+)\\.\\s*$").find(rightStr)
        if (hitMatch != null) {
            hitTarget = hitMatch.groupValues[1].toIntOrNull() ?: 0
            rightStr = rightStr.substring(0, hitMatch.range.first)
        }

        val left = parseOperand(leftStr) ?: return null
        val right = parseOperand(rightStr) ?: return null
        val operator = parseOperator(operatorStr) ?: return null

        return Condition(left, operator, right, hitTarget, flag)
    }

    private fun findOperator(str: String): Pair<String, Int>? {
        // Order matters - check longer operators first
        val operators = listOf("!=", "<=", ">=", "=", "<", ">")
        for (op in operators) {
            val idx = str.indexOf(op)
            if (idx >= 0) return op to idx
        }
        return null
    }

    private fun parseOperator(op: String): Operator? {
        return when (op) {
            "=" -> Operator.EQ
            "!=" -> Operator.NE
            "<" -> Operator.LT
            "<=" -> Operator.LE
            ">" -> Operator.GT
            ">=" -> Operator.GE
            else -> null
        }
    }

    private fun parseOperand(str: String): Operand? {
        val trimmed = str.trim()
        if (trimmed.isBlank()) return null

        // Check for delta prefix
        val isDelta = trimmed.startsWith("d", ignoreCase = true) &&
                      trimmed.getOrNull(1)?.equals('0') == true
        val isPrior = trimmed.startsWith("p", ignoreCase = true) &&
                      trimmed.getOrNull(1)?.equals('0') == true

        val working = when {
            isDelta -> trimmed.drop(1)
            isPrior -> trimmed.drop(1)
            else -> trimmed
        }

        // Check for memory address (starts with 0x)
        if (working.startsWith("0x", ignoreCase = true)) {
            val (size, addressStr) = parseSizePrefix(working.drop(2))
            val address = addressStr.toIntOrNull(16) ?: return null

            return when {
                isDelta -> Operand.Delta(address, size)
                isPrior -> Operand.Prior(address, size)
                else -> Operand.Memory(address, size)
            }
        }

        // Must be a numeric value
        val value = if (working.startsWith("0x", ignoreCase = true)) {
            working.drop(2).toIntOrNull(16)
        } else {
            working.toIntOrNull()
        }

        return value?.let { Operand.Value(it) }
    }

    private fun parseSizePrefix(str: String): Pair<MemSize, String> {
        if (str.isEmpty()) return MemSize.BYTE to str

        return when (str[0].uppercaseChar()) {
            'M' -> MemSize.BIT0 to str.drop(1)
            'N' -> MemSize.BIT1 to str.drop(1)
            'O' -> MemSize.BIT2 to str.drop(1)
            'P' -> MemSize.BIT3 to str.drop(1)
            'Q' -> MemSize.BIT4 to str.drop(1)
            'R' -> MemSize.BIT5 to str.drop(1)
            'S' -> MemSize.BIT6 to str.drop(1)
            'T' -> MemSize.BIT7 to str.drop(1)
            'L' -> MemSize.LOWER4 to str.drop(1)
            'U' -> MemSize.UPPER4 to str.drop(1)
            'H' -> MemSize.WORD to str.drop(1)
            'X' -> MemSize.TBYTE to str.drop(1)
            'W', ' ' -> MemSize.DWORD to str.drop(1)
            else -> MemSize.BYTE to str
        }
    }
}
