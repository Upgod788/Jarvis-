package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.agent.CommandNormalizer
import com.example.agent.ConfirmationManager
import com.example.agent.ToolRegistry
import com.example.ai.AIRequest
import com.example.ai.LocalRuleAIProvider
import com.example.database.JarvisDatabase
import com.example.memory.MemoryEntity
import com.example.memory.MemoryRepository
import com.example.services.CommandHandlerService
import com.example.services.IntentActionType
import com.example.tools.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Comprehensive A to Z test suite covering every system component, tool,
 * intent, database operation, and safety mechanism in JARVIS.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AToZComprehensiveRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: JarvisDatabase
    private lateinit var memoryRepo: MemoryRepository
    private lateinit var toolRegistry: ToolRegistry
    private val localAi = LocalRuleAIProvider()
    private val confirmationManager = ConfirmationManager()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, JarvisDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        memoryRepo = MemoryRepository(database.memoryDao())
        toolRegistry = ToolRegistry(memoryRepo)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // A - Alarm Tool
    @Test
    fun testA_AlarmTool() = runBlocking {
        val alarmTool = AlarmTool()
        assertEquals("AlarmTool", alarmTool.name)
        assertEquals(RiskLevel.LOW, alarmTool.riskLevel)
        val result = alarmTool.execute(context, mapOf("hour" to 7, "minute" to 30, "message" to "Wake up"))
        assertNotNull(result)
        assertTrue(result.message.contains("alarm", ignoreCase = true) || result.message.contains("7:30"))
    }

    // B - Bluetooth and Battery Tools
    @Test
    fun testB_BluetoothAndBatteryTools() = runBlocking {
        val btTool = BluetoothTool()
        assertEquals("BluetoothTool", btTool.name)
        val btStatusResult = btTool.execute(context, mapOf("action" to "status"))
        assertNotNull(btStatusResult)
        assertTrue(btStatusResult.message.contains("Bluetooth is"))

        val batteryTool = BatteryTool()
        assertEquals("BatteryTool", batteryTool.name)
        val batteryResult = batteryTool.execute(context, emptyMap())
        assertNotNull(batteryResult)
        assertTrue(batteryResult.message.contains("battery") || !batteryResult.success)
    }

    // C - Camera, Call, and Command Normalizer
    @Test
    fun testC_CameraAndCallContactTools() = runBlocking {
        val cameraTool = CameraTool()
        assertEquals("CameraTool", cameraTool.name)
        val camResult = cameraTool.execute(context, mapOf("mode" to "photo"))
        assertNotNull(camResult)

        val callTool = CallContactTool()
        assertEquals("CallContactTool", callTool.name)
        assertEquals(RiskLevel.MEDIUM, callTool.riskLevel)
        assertTrue(confirmationManager.requiresConfirmation(callTool, mapOf("contactName" to "John")))

        // Command Normalizer
        val normalized = CommandNormalizer.standardizeHinglish("torch on karo")
        assertEquals("turn on flashlight", normalized)
    }

    // D - Device Info Tool
    @Test
    fun testD_DeviceInfoTool() = runBlocking {
        val deviceTool = DeviceInfoTool()
        assertEquals("DeviceInfoTool", deviceTool.name)
        val modelResult = deviceTool.execute(context, mapOf("queryType" to "model"))
        assertTrue(modelResult.success)
        assertTrue(modelResult.message.contains("running Android"))

        val storageResult = deviceTool.execute(context, mapOf("queryType" to "storage"))
        assertTrue(storageResult.success)
        assertTrue(storageResult.message.contains("GB"))
    }

    // E - Error and Fallback Handling
    @Test
    fun testE_ErrorAndFallbackHandling() = runBlocking {
        // Unknown command should fall back gracefully with a helpful answer
        val response = localAi.processCommand(AIRequest("tell me a random riddle", emptyList()))
        assertNotNull(response.textResponse)
        assertTrue(response.textResponse.isNotBlank())
    }

    // F - Flashlight Tool
    @Test
    fun testF_FlashlightTool() = runBlocking {
        val flashlightTool = FlashlightTool()
        assertEquals("FlashlightTool", flashlightTool.name)
        assertEquals(RiskLevel.LOW, flashlightTool.riskLevel)
        val resultOn = flashlightTool.execute(context, mapOf("enabled" to true))
        assertNotNull(resultOn)
        val resultOff = flashlightTool.execute(context, mapOf("enabled" to false))
        assertNotNull(resultOff)
    }

    // G - Greetings and Identity Provider
    @Test
    fun testG_GreetingsAndAIProvider() = runBlocking {
        val greeting = localAi.processCommand(AIRequest("hello", emptyList()))
        assertTrue(greeting.textResponse.contains("JARVIS"))

        val identity = localAi.processCommand(AIRequest("who are you", emptyList()))
        assertTrue(identity.textResponse.contains("JARVIS"))
    }

    // H - Hinglish Normalization
    @Test
    fun testH_HinglishNormalization() {
        assertEquals("open whatsapp", CommandNormalizer.standardizeHinglish("whatsapp kholo"))
        assertEquals("what is my battery", CommandNormalizer.standardizeHinglish("battery kitni hai"))
        assertEquals("call mom", CommandNormalizer.standardizeHinglish("mom ko call lagao"))
        assertEquals("turn on flashlight", CommandNormalizer.standardizeHinglish("torch jalao"))
    }

    // I - Intent Action Parsing
    @Test
    fun testI_IntentActionParsing() {
        val taggedAction = CommandHandlerService.parseIntent("Sure thing! [INTENT: TOGGLE_WIFI]")
        assertNotNull(taggedAction)
        assertEquals(IntentActionType.TOGGLE_WIFI, taggedAction?.actionType)

        val btAction = CommandHandlerService.parseIntent("[INTENT: ENABLE_BLUETOOTH]")
        assertNotNull(btAction)
        assertEquals(IntentActionType.ENABLE_BLUETOOTH, btAction?.actionType)

        val jsonAction = CommandHandlerService.parseIntent("""{"intent": "OPEN_CAMERA", "mode": "photo"}""")
        assertNotNull(jsonAction)
        assertEquals(IntentActionType.OPEN_CAMERA, jsonAction?.actionType)
    }

    // J - Jarvis Database and Memory Dao
    @Test
    fun testJ_JarvisDatabaseAndMemoryDao() = runBlocking {
        val dao = database.memoryDao()
        val id = dao.insertMemory(MemoryEntity(key = "favorite_drink", value = "Green Tea", category = "preference"))
        assertTrue(id > 0)

        val retrieved = dao.getMemoryByKey("favorite_drink")
        assertNotNull(retrieved)
        assertEquals("Green Tea", retrieved?.value)

        val list = dao.getAllMemories().first()
        assertEquals(1, list.size)
    }

    // K - Key-value Memory Tool
    @Test
    fun testK_KeyStoreMemoryTool() = runBlocking {
        val memoryTool = MemoryTool(memoryRepo)
        val saveResult = memoryTool.execute(context, mapOf("action" to "remember", "key" to "city", "value" to "San Francisco"))
        assertTrue(saveResult.success)
        assertTrue(saveResult.message.contains("San Francisco"))

        val recallResult = memoryTool.execute(context, mapOf("action" to "recall", "key" to "city"))
        assertTrue(recallResult.success)
        assertTrue(recallResult.message.contains("San Francisco"))

        val forgetResult = memoryTool.execute(context, mapOf("action" to "forget", "key" to "city"))
        assertTrue(forgetResult.success)
    }

    // L - Local Rule AI Provider Tools
    @Test
    fun testL_LocalRuleAIProviderTools() = runBlocking {
        val wifiRes = localAi.processCommand(AIRequest("turn off wifi", emptyList()))
        assertEquals("WifiTool", wifiRes.toolInvocation?.toolName)
        assertEquals("off", wifiRes.toolInvocation?.arguments?.get("action"))

        val btRes = localAi.processCommand(AIRequest("turn on bluetooth", emptyList()))
        assertEquals("BluetoothTool", btRes.toolInvocation?.toolName)
        assertEquals("on", btRes.toolInvocation?.arguments?.get("action"))

        val timeRes = localAi.processCommand(AIRequest("what time is it", emptyList()))
        assertEquals("CurrentTimeTool", timeRes.toolInvocation?.toolName)
    }

    // M - Memory Repository Operations
    @Test
    fun testM_MemoryRepositoryOperations() = runBlocking {
        memoryRepo.saveMemory("pet_name", "Luna")
        val entity = memoryRepo.getMemoryByKey("pet_name")
        assertNotNull(entity)
        assertEquals("Luna", entity?.value)

        // Updating existing key
        memoryRepo.saveMemory("pet_name", "Milo")
        val updated = memoryRepo.getMemoryByKey("pet_name")
        assertEquals("Milo", updated?.value)

        // Deleting
        val deleted = memoryRepo.deleteMemoryByKey("pet_name")
        assertTrue(deleted)
        assertNull(memoryRepo.getMemoryByKey("pet_name"))
    }

    // N - Notification Tool
    @Test
    fun testN_NotificationTool() = runBlocking {
        val notifTool = NotificationTool()
        assertEquals("NotificationTool", notifTool.name)
        val result = notifTool.execute(context, mapOf("title" to "Test Alert", "message" to "Hello World"))
        assertNotNull(result)
        assertTrue(result.message.contains("Notification") || result.message.contains("alert"))
    }

    // O - Open App Tool
    @Test
    fun testO_OpenAppTool() = runBlocking {
        val appTool = OpenAppTool()
        assertEquals("OpenAppTool", appTool.name)
        val result = appTool.execute(context, mapOf("appName" to "youtube"))
        assertNotNull(result)
    }

    // P - Permission and Confirmation Safety
    @Test
    fun testP_PermissionAndConfirmationSafety() {
        val callTool = CallContactTool()
        val sendSmsTool = SendSmsTool()
        val timeTool = CurrentTimeTool()

        assertTrue("CallContactTool should require confirmation", confirmationManager.requiresConfirmation(callTool, emptyMap()))
        assertTrue("SendSmsTool should require confirmation", confirmationManager.requiresConfirmation(sendSmsTool, emptyMap()))
        assertFalse("CurrentTimeTool should NOT require confirmation", confirmationManager.requiresConfirmation(timeTool, emptyMap()))
    }

    // Q - Query Web Search Tool
    @Test
    fun testQ_QueryWebSearchTool() = runBlocking {
        val searchTool = WebSearchTool()
        assertEquals("WebSearchTool", searchTool.name)
        val result = searchTool.execute(context, mapOf("query" to "Android Jetpack Compose"))
        assertNotNull(result)
        assertTrue(result.message.contains("Android Jetpack Compose"))
    }

    // R - Risk Level Evaluations
    @Test
    fun testR_RiskLevelEvaluations() {
        val all = toolRegistry.getAllTools()
        val mediumRiskTools = all.filter { it.riskLevel == RiskLevel.MEDIUM }
        val lowRiskTools = all.filter { it.riskLevel == RiskLevel.LOW }

        assertTrue(mediumRiskTools.any { it.name == "CallContactTool" })
        assertTrue(mediumRiskTools.any { it.name == "SendSmsTool" })
        assertTrue(lowRiskTools.any { it.name == "CurrentTimeTool" })
        assertTrue(lowRiskTools.any { it.name == "DeviceInfoTool" })
    }

    // S - Send SMS and Settings Tools
    @Test
    fun testS_SendSmsAndSettingsTools() = runBlocking {
        val smsTool = SendSmsTool()
        assertEquals("SendSmsTool", smsTool.name)
        assertEquals(RiskLevel.MEDIUM, smsTool.riskLevel)
        val smsResult = smsTool.execute(context, mapOf("recipient" to "123456", "message" to "Test"))
        assertNotNull(smsResult)

        val settingsTool = SettingsTool()
        assertEquals("SettingsTool", settingsTool.name)
        val settingsResult = settingsTool.execute(context, mapOf("settingType" to "wifi"))
        assertNotNull(settingsResult)
    }

    // T - Timer and Current Time Tools
    @Test
    fun testT_TimerAndCurrentTimeTools() = runBlocking {
        val timerTool = TimerTool()
        assertEquals("TimerTool", timerTool.name)
        val timerResult = timerTool.execute(context, mapOf("seconds" to 60, "message" to "Pasta"))
        assertNotNull(timerResult)

        val timeTool = CurrentTimeTool()
        assertEquals("CurrentTimeTool", timeTool.name)
        val timeResult = timeTool.execute(context, emptyMap())
        assertTrue(timeResult.success)
        assertTrue(timeResult.message.contains("It is currently"))
    }

    // U - User Prompt to Intent Mapping
    @Test
    fun testU_UserPromptToIntentMapping() {
        val detectedWifi = CommandHandlerService.parseIntent("Can you please turn off wifi?")
        assertEquals(IntentActionType.DISABLE_WIFI, detectedWifi?.actionType)

        val detectedBt = CommandHandlerService.parseIntent("Please turn on bluetooth")
        assertEquals(IntentActionType.ENABLE_BLUETOOTH, detectedBt?.actionType)

        val detectedCam = CommandHandlerService.parseIntent("Open camera to take photo")
        assertEquals(IntentActionType.OPEN_CAMERA, detectedCam?.actionType)
    }

    // V - Voice Command Recognition Normalization
    @Test
    fun testV_VoiceCommandRecognition() {
        val speechText = "ALARM LAGAO 6 AM"
        val standardized = CommandNormalizer.standardizeHinglish(speechText)
        assertTrue(standardized.contains("alarm"))
    }

    // W - Wifi and Weather Tools
    @Test
    fun testW_WifiAndWeatherTools() = runBlocking {
        val wifiTool = WifiTool()
        assertEquals("WifiTool", wifiTool.name)
        val wifiResult = wifiTool.execute(context, mapOf("action" to "status"))
        assertNotNull(wifiResult)
        assertTrue(wifiResult.message.contains("Wi-Fi is"))

        val weatherTool = WeatherTool()
        assertEquals("WeatherTool", weatherTool.name)
        val weatherResult = weatherTool.execute(context, mapOf("city" to "Tokyo"))
        assertNotNull(weatherResult)
        assertNotNull(weatherResult.message)
    }

    // X - eXecution Pipeline
    @Test
    fun testX_eXecuteActionPipeline() = runBlocking {
        val detected = CommandHandlerService.parseIntent("[INTENT: CHECK_TIME]")
        assertNotNull(detected)
        val execResult = CommandHandlerService.executeAction(context, detected!!, toolRegistry)
        assertTrue(execResult.success)
        assertTrue(execResult.message.contains("It is currently"))
    }

    // Y - Yield and Coroutine State
    @Test
    fun testY_YieldAndCoroutineState() = runBlocking {
        val memoryList = memoryRepo.allMemories.first()
        assertNotNull(memoryList)
    }

    // Z - Zero-Failure Registry Verification
    @Test
    fun testZ_ZeroFailureRegistryVerification() {
        val all = toolRegistry.getAllTools()
        assertTrue(all.isNotEmpty())
        for (tool in all) {
            assertTrue("Tool name must not be blank", tool.name.isNotBlank())
            assertTrue("Tool description must not be blank", tool.description.isNotBlank())
            assertNotNull("Tool parameters must not be null", tool.parameters)
            assertNotNull("Tool risk level must not be null", tool.riskLevel)
        }
    }
}
