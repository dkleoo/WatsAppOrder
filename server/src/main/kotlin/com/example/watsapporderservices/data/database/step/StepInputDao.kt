package com.example.watsapporderservices.data.database.step

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

class StepInputDao {
    fun findByStepId(stepId: Int): List<StepInputEntity> =
        StepInputs.selectAll().where { StepInputs.stepId eq stepId }.map { it.toEntity() }

    fun findByStepIds(stepIds: List<Int>): List<StepInputEntity> {
        if (stepIds.isEmpty()) return emptyList()
        return StepInputs.selectAll().where { StepInputs.stepId inList stepIds }.map { it.toEntity() }
    }

    fun insert(stepId: Int, inputId: Int) {
        StepInputs.insert {
            it[StepInputs.stepId] = stepId
            it[StepInputs.inputId] = inputId
        }
    }

    fun deleteByStepIds(stepIds: List<Int>): Int {
        if (stepIds.isEmpty()) return 0
        return StepInputs.deleteWhere { StepInputs.stepId inList stepIds }
    }
}

private fun ResultRow.toEntity(): StepInputEntity = StepInputEntity(
    stepId = this[StepInputs.stepId],
    inputId = this[StepInputs.inputId],
)
