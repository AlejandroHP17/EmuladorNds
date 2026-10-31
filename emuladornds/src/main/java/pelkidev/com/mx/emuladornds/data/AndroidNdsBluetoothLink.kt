package pelkidev.com.mx.emuladornds.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Enlace RFCOMM entre dos teléfonos. Solo negocia el mismo [matchingGameId]; no envía la ROM.
 */
class AndroidNdsBluetoothLink(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : NdsBluetoothLink {

    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    private val linkedFlag = AtomicBoolean(false)
    override val linked: Boolean
        get() = linkedFlag.get()

    private var activeSocket: BluetoothSocket? = null
    private var connectJob: Job? = null

    @SuppressLint("MissingPermission")
    override fun connect(matchingGameId: String) {
        val gameId = matchingGameId.trim()
        if (gameId.isEmpty()) return
        disconnect()
        val bluetooth = adapter ?: return
        if (!bluetooth.isEnabled) return

        connectJob = scope.launch {
            linkedFlag.set(false)
            val socket = openSocket(bluetooth) ?: return@launch
            if (!performHandshake(socket, gameId)) {
                closeQuietly(socket)
                return@launch
            }
            activeSocket = socket
            linkedFlag.set(true)
        }
    }

    override fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        linkedFlag.set(false)
        activeSocket?.let { closeQuietly(it) }
        activeSocket = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun openSocket(bluetooth: BluetoothAdapter): BluetoothSocket? = coroutineScope {
        val serverSocket = try {
            bluetooth.listenUsingInsecureRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
        } catch (_: IOException) {
            return@coroutineScope null
        }
        val result = CompletableDeferred<BluetoothSocket>()
        val serverJob = launch {
            try {
                val accepted = serverSocket.accept()
                if (result.complete(accepted)) {
                    closeQuietly(serverSocket)
                } else {
                    closeQuietly(accepted)
                }
            } catch (_: IOException) {
            }
        }
        val clientJob = launch {
            for (device in bluetooth.bondedDevices) {
                if (result.isCompleted) return@launch
                try {
                    val client = device.createInsecureRfcommSocketToServiceRecord(SERVICE_UUID)
                    client.connect()
                    if (result.complete(client)) {
                        closeQuietly(serverSocket)
                        serverJob.cancel()
                    } else {
                        closeQuietly(client)
                    }
                    return@launch
                } catch (_: IOException) {
                }
            }
        }
        withTimeoutOrNull(LINK_TIMEOUT_MS) {
            result.await()
        }.also {
            serverJob.cancel()
            clientJob.cancel()
            closeQuietly(serverSocket)
        }
    }

    private fun performHandshake(socket: BluetoothSocket, localGameId: String): Boolean {
        return try {
            val input = socket.inputStream
            val output = socket.outputStream
            output.write(encodeJoin(localGameId))
            output.flush()
            val remote = decodeJoin(readFrame(input) ?: return false) ?: return false
            remote == localGameId
        } catch (_: IOException) {
            false
        }
    }

    private fun closeQuietly(socket: BluetoothSocket) {
        try {
            socket.close()
        } catch (_: IOException) {
        }
    }

    private fun closeQuietly(server: BluetoothServerSocket) {
        try {
            server.close()
        } catch (_: IOException) {
        }
    }

    suspend fun awaitConnection() {
        connectJob?.join()
    }

    fun shutdown() {
        disconnect()
        scope.cancel()
    }

    companion object {
        private const val SERVICE_NAME = "EmuladorNds"
        private val SERVICE_UUID: UUID =
            UUID.fromString("7a5fbc69-2e8c-4b91-9c22-efc905803957")
        private const val LINK_TIMEOUT_MS = 15_000L
        private const val JOIN_PREFIX = "JOIN:"
        private const val FRAME_END = '\n'.code.toByte()

        internal fun encodeJoin(gameId: String): ByteArray =
            (JOIN_PREFIX + gameId.trim() + "\n").encodeToByteArray()

        internal fun decodeJoin(frame: ByteArray): String? {
            val text = frame.decodeToString().trim()
            if (!text.startsWith(JOIN_PREFIX)) return null
            return text.removePrefix(JOIN_PREFIX).trim()
        }

        internal fun readFrame(input: InputStream): ByteArray? {
            val buffer = ArrayList<Byte>()
            while (true) {
                val byte = input.read()
                if (byte < 0) return null
                if (byte.toByte() == FRAME_END) break
                buffer.add(byte.toByte())
            }
            return buffer.toByteArray()
        }
    }
}
