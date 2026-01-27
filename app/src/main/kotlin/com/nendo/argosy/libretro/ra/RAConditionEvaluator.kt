package com.nendo.argosy.libretro.ra

class RAConditionEvaluator {

    private var previousMemory: ByteArray? = null
    private val hitCounts: MutableMap<Int, Int> = mutableMapOf()
    private var pauseCount = 0

    fun reset() {
        hitCounts.clear()
        pauseCount = 0
        previousMemory = null
    }

    fun evaluate(definition: AchievementDefinition, memory: ByteArray): Boolean {
        // If paused, count down
        if (pauseCount > 0) {
            pauseCount--
            previousMemory = memory.copyOf()
            return false
        }

        // Core requirements must pass, OR any alt group must pass
        val coreResult = evaluateGroup(definition.coreRequirements, memory, 0)

        val triggered = if (coreResult.triggered) {
            true
        } else if (coreResult.passed && definition.altGroups.isEmpty()) {
            true
        } else {
            // Check alt groups - any one passing is enough
            var altPassed = false
            for ((index, altGroup) in definition.altGroups.withIndex()) {
                val altResult = evaluateGroup(altGroup, memory, (index + 1) * 1000)
                if (altResult.triggered || altResult.passed) {
                    altPassed = true
                    break
                }
            }
            coreResult.passed && altPassed
        }

        previousMemory = memory.copyOf()
        return triggered
    }

    private data class GroupResult(val passed: Boolean, val triggered: Boolean)

    private fun evaluateGroup(group: ConditionGroup, memory: ByteArray, hitIndexOffset: Int): GroupResult {
        if (group.conditions.isEmpty()) return GroupResult(passed = true, triggered = false)

        var allPassed = true
        var hasResetIf = false
        var hasPauseIf = false
        var hasTrigger = false
        var triggerConditionMet = false

        for ((index, condition) in group.conditions.withIndex()) {
            val hitIndex = hitIndexOffset + index
            val result = evaluateCondition(condition, memory)

            when (condition.flag) {
                ConditionFlag.RESET_IF -> {
                    hasResetIf = true
                    if (result) {
                        hitCounts.clear()
                        return GroupResult(passed = false, triggered = false)
                    }
                }
                ConditionFlag.PAUSE_IF -> {
                    hasPauseIf = true
                    if (result) {
                        pauseCount = 1
                        return GroupResult(passed = false, triggered = false)
                    }
                }
                ConditionFlag.TRIGGER -> {
                    hasTrigger = true
                    if (result) {
                        triggerConditionMet = checkHitCount(condition, hitIndex, true)
                    }
                }
                ConditionFlag.AND_NEXT, ConditionFlag.OR_NEXT -> {
                    // These modify how the next condition is evaluated
                    // For now, treat as regular AND
                    if (!checkHitCount(condition, hitIndex, result)) {
                        allPassed = false
                    }
                }
                else -> {
                    if (!checkHitCount(condition, hitIndex, result)) {
                        allPassed = false
                    }
                }
            }
        }

        return GroupResult(
            passed = allPassed,
            triggered = hasTrigger && triggerConditionMet && allPassed
        )
    }

    private fun checkHitCount(condition: Condition, hitIndex: Int, currentResult: Boolean): Boolean {
        if (condition.hitTarget == 0) {
            // No hit count requirement
            return currentResult
        }

        val currentHits = hitCounts.getOrDefault(hitIndex, 0)
        if (currentResult) {
            val newHits = currentHits + 1
            hitCounts[hitIndex] = newHits
            return newHits >= condition.hitTarget
        }

        return currentHits >= condition.hitTarget
    }

    private fun evaluateCondition(condition: Condition, memory: ByteArray): Boolean {
        val leftValue = readOperand(condition.left, memory)
        val rightValue = readOperand(condition.right, memory)

        return when (condition.operator) {
            Operator.EQ -> leftValue == rightValue
            Operator.NE -> leftValue != rightValue
            Operator.LT -> leftValue < rightValue
            Operator.LE -> leftValue <= rightValue
            Operator.GT -> leftValue > rightValue
            Operator.GE -> leftValue >= rightValue
        }
    }

    private fun readOperand(operand: Operand, memory: ByteArray): Int {
        return when (operand) {
            is Operand.Value -> operand.value
            is Operand.Memory -> readMemory(operand.address, operand.size, memory)
            is Operand.Delta -> {
                val current = readMemory(operand.address, operand.size, memory)
                val previous = previousMemory?.let { readMemory(operand.address, operand.size, it) } ?: current
                current - previous
            }
            is Operand.Prior -> {
                previousMemory?.let { readMemory(operand.address, operand.size, it) } ?: 0
            }
        }
    }

    private fun readMemory(address: Int, size: MemSize, memory: ByteArray): Int {
        if (address < 0 || address >= memory.size) return 0

        return when (size) {
            MemSize.BIT0 -> (readByte(address, memory) shr 0) and 1
            MemSize.BIT1 -> (readByte(address, memory) shr 1) and 1
            MemSize.BIT2 -> (readByte(address, memory) shr 2) and 1
            MemSize.BIT3 -> (readByte(address, memory) shr 3) and 1
            MemSize.BIT4 -> (readByte(address, memory) shr 4) and 1
            MemSize.BIT5 -> (readByte(address, memory) shr 5) and 1
            MemSize.BIT6 -> (readByte(address, memory) shr 6) and 1
            MemSize.BIT7 -> (readByte(address, memory) shr 7) and 1
            MemSize.LOWER4 -> readByte(address, memory) and 0x0F
            MemSize.UPPER4 -> (readByte(address, memory) shr 4) and 0x0F
            MemSize.BYTE -> readByte(address, memory)
            MemSize.WORD -> readWord(address, memory)
            MemSize.TBYTE -> readTByte(address, memory)
            MemSize.DWORD -> readDWord(address, memory)
        }
    }

    private fun readByte(address: Int, memory: ByteArray): Int {
        if (address >= memory.size) return 0
        return memory[address].toInt() and 0xFF
    }

    private fun readWord(address: Int, memory: ByteArray): Int {
        if (address + 1 >= memory.size) return readByte(address, memory)
        return (readByte(address, memory)) or
               (readByte(address + 1, memory) shl 8)
    }

    private fun readTByte(address: Int, memory: ByteArray): Int {
        if (address + 2 >= memory.size) return readWord(address, memory)
        return (readByte(address, memory)) or
               (readByte(address + 1, memory) shl 8) or
               (readByte(address + 2, memory) shl 16)
    }

    private fun readDWord(address: Int, memory: ByteArray): Int {
        if (address + 3 >= memory.size) return readTByte(address, memory)
        return (readByte(address, memory)) or
               (readByte(address + 1, memory) shl 8) or
               (readByte(address + 2, memory) shl 16) or
               (readByte(address + 3, memory) shl 24)
    }
}
