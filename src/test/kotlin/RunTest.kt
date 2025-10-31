import com.github.redepicness.neurosdk.NeuroAction
import com.github.redepicness.neurosdk.NeuroActionWithoutResponse
import com.github.redepicness.neurosdk.NeuroSDK
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.time.Duration.Companion.seconds

fun main() {
    val sdk = NeuroSDK("Epic Game", "ws://localhost:8000/")

    // sdk.registerActions(EchoAction)

    runBlocking {
        launch {
            while (true) {
                delay(10.seconds)
                sdk.forceAction("Current state!", "Please send an echo:", false, listOf(EchoAction, NoResponseAction, ListAction, ValidationFail)) { action ->
                    println("Executed action callback for action: " + action.name)
                }
            }
        }
        sdk.start()
    }
}

object NoResponseAction : NeuroActionWithoutResponse("no-response", "This is the no response action") {
    override fun successMessage(): String {
        return "Successful!"
    }

    override suspend fun process() {
        println("Processing...")
    }

}

object ValidationFail : NeuroActionWithoutResponse("validation-fail", "Always fails validation") {
    override fun validate(data: Unit) = "Validation failed!"

    override fun successMessage(): String {
        return "Successful!"
    }

    override suspend fun process() {
        println("Processing...")
    }

}

object EchoAction : NeuroAction<String>(
    "echo",
    "A simple command that echoes the message received to the logs.",
    String.serializer(),
) {

    override fun validate(data: String) = null

    override fun successMessage(data: String) = "Successfully echoed: $data"

    override suspend fun process(data: String) {
        logger.info("ECHO: $data")
    }

}

object ListAction : NeuroAction<TestListResponse>(
    "list",
    "A simple list test action.",
    TestListResponse.serializer(),
) {

    override fun validate(data: TestListResponse) = null

    override fun successMessage(data: TestListResponse) = "Successfully processed: ${data}"

    override suspend fun process(data: TestListResponse) {
        logger.info("ECHO: ${data}")
    }

    override fun limitedResponseResolver(serialName: String): List<String> {
        logger.info("Requesting limit responses for $serialName")
        return if (serialName == "testString") {
            listOf("response1", "response2")
        } else if (serialName == "[testList]") {
            listOf("list-1", "list-2")
        } else {
            emptyList()
        }
    }

}

@Serializable
data class TestListResponse(
    val testString: String,
    val testList: List<String>,
)
