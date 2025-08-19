package com.github.redepicness.neurogamesdk

abstract class NeuroActionWithoutResponse(
    name: String,
    description: String,
) : NeuroAction<Unit>(name, description, null) {

    override fun validate(data: Unit): String? = null

    override fun successMessage(data: Unit) = successMessage()

    final override suspend fun process(data: Unit) = process()

    abstract fun successMessage(): String

    abstract suspend fun process()

}
