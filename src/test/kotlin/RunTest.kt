import com.github.redepicness.neurogamesdk.NeuroAction
import com.github.redepicness.neurogamesdk.NeuroActionWithoutResponse
import com.github.redepicness.neurogamesdk.NeuroGameSDK
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.time.Duration.Companion.seconds

fun main() {
    val sdk = NeuroGameSDK("Epic Game", "ws://localhost:8000/")

    // sdk.registerActions(EchoAction)

    runBlocking {
        launch {
            while (true) {
                delay(5.seconds)
                sdk.forceAction("Current state!", "Please send an echo:", false, listOf(EchoAction, NoResponseAction, ListAction)) { action ->
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

object EchoAction : NeuroAction<String>(
    "echo",
    "A simple command that echoes the message received to the logs.",
    String.serializer(),
) {

    override fun validate(data: String) = true

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

    override fun validate(data: TestListResponse) = true

    override fun successMessage(data: TestListResponse) = "Successfully processed: ${data}"

    override suspend fun process(data: TestListResponse) {
        logger.info("ECHO: ${data}")
    }

    override fun limitedResponseResolver(serialName: String): List<String> {
        logger.info("Requesting limit responses for $serialName")
        return if (serialName == "testString") {
            listOf("response1", "response2")
        } else {
            emptyList()
        }
    }

}

@Serializable
data class TestListResponse(
    val testString: String
)
