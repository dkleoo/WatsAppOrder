package com.example.watsapporderservices.data.database.step

import com.example.watsapporderservices.data.database.input.Inputs
import org.jetbrains.exposed.v1.core.Table

object StepInputs : Table("step_inputs") {
    val stepId = integer("step_id").references(Steps.id)
    val inputId = integer("input_id").references(Inputs.id)

    override val primaryKey = PrimaryKey(stepId, inputId)
}

data class StepInputEntity(
    val stepId: Int,
    val inputId: Int,
)
