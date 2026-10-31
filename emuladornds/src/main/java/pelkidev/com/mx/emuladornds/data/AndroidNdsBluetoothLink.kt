package pelkidev.com.mx.emuladornds.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Enlace RFCOMM entre dos teléfonos. Negocia el mismo [matchingGameId] y, si
 * encaja, transporta los paquetes MP de melonDS. No envía la ROM.
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

    private val ioLock = Any()
    private var activeSocket: BluetoothSocket? = null
    private var output: OutputStream? = null
    private var connectJob: Job? = null
    private var readerJob: Job? = null

    @SuppressLint("MissingPermission")
    override fun connect(matchingGameId: String) {
        val gameId = matchingGameId.trim()
        if (gameId.isEmpty()) return
        disconnect()
        val bluetooth = adapter ?: return
        if (!bluetooth.isEnabled) return

        connectJob = scope.launch {
            linkedFlag.set(false)
            val opened = openSocket(bluetooth) ?: return@launch
            if (!performHandshake(opened.socket, gameId)) {
                closeQuietly(opened.socket)
                return@launch
            }
            activeSocket = opened.socket
            output = opened.socket.outputStream
            nativeMpAttach(if (opened.host) 0 else 1)
            startReader(opened.socket)
            linkedFlag.set(true)
        }
    }

    override fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        readerJob?.cancel()
        readerJob = null
        linkedFlag.set(false)
        nativeMpDetach()
        synchronized(ioLock) {
            output = null
        }
        activeSocket?.let { closeQuietly(it) }
        activeSocket = null
    }

    override fun sendMp(frame: ByteArray): Boolean = writeFrame(frame)

    @Suppress("unused")
    fun sendMpFrame(frame: ByteArray): Boolean = writeFrame(frame)

    @SuppressLint("MissingPermission")
    private suspend fun openSocket(bluetooth: BluetoothAdapter): OpenedLink? = coroutineScope {
        val serverSocket = try {
            bluetooth.listenUsingInsecureRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
        } catch (_: IOException) {
            return@coroutineScope null
        }
        val result = CompletableDeferred<OpenedLink>()
        val serverJob = launch {
            try {
                val accepted = serverSocket.accept()
                val link = OpenedLink(accepted, host = true)
                if (result.complete(link)) {
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
                    val link = OpenedLink(client, host = false)
                    if (result.complete(link)) {
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
            val stream = socket.outputStream
            stream.write(encodeJoin(localGameId))
            stream.flush()
            val remote = decodeJoin(readLineFrame(input) ?: return false) ?: return false
            remote == localGameId
        } catch (_: IOException) {
            false
        }
    }

    private fun startReader(socket: BluetoothSocket) {
        readerJob?.cancel()
        readerJob = scope.launch {
            try {
                val input = socket.inputStream
                while (isActive && linkedFlag.get()) {
                    val frame = readLengthPrefixed(input) ?: break
                    nativeMpEnqueue(frame)
                }
            } catch (_: IOException) {
            } finally {
                if (linkedFlag.get()) {
                    nativeMpDetach()
                    linkedFlag.set(false)
                }
            }
        }
    }

    private fun writeFrame(frame: ByteArray): Boolean {
        if (frame.isEmpty()) return false
        val stream = synchronized(ioLock) { output } ?: return false
        return try {
            val header = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
                .putInt(frame.size)
                .array()
            synchronized(ioLock) {
                stream.write(header)
                stream.write(frame)
                stream.flush()
            }
            true
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

    private external fun nativeMpAttach(senderId: Int)

    private external fun nativeMpDetach()

    private external fun nativeMpEnqueue(frame: ByteArray)

    private data class OpenedLink(
        val socket: BluetoothSocket,
        val host: Boolean,
    )

    companion object {
        private const val SERVICE_NAME = "EmuladorNds"
        private val SERVICE_UUID: UUID =
            UUID.fromString("7a5fbc69-2e8c-4b91-9c22-efc905803957")
        private const val LINK_TIMEOUT_MS = 15_000L
        private const val JOIN_PREFIX = "JOIN:"
        private const val FRAME_END = '\n'.code.toByte()

        init {
            System.loadLibrary("emuladornds_melonds")
        }

        internal fun encodeJoin(gameId: String): ByteArray =
            (JOIN_PREFIX + gameId.trim() + "\n").encodeToByteArray()

        internal fun decodeJoin(frame: ByteArray): String? {
            val text = frame.decodeToString().trim()
            if (!text.startsWith(JOIN_PREFIX)) return null
            return text.removePrefix(JOIN_PREFIX).trim()
        }

        internal fun readLineFrame(input: InputStream): ByteArray? {
            val buffer = ArrayList<Byte>()
            while (true) {
                val byte = input.read()
                if (byte < 0) return null
                if (byte.toByte() == FRAME_END) break
                buffer.add(byte.toByte())
            }
            return buffer.toByteArray()
        }

        internal fun readLengthPrefixed(input: InputStream): ByteArray? {
            val header = ByteArray(4)
            if (!readFully(input, header)) return null
            val length = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).int
            if (length <= 0 || length > MAX_FRAME) return null
            val body = ByteArray(length)
            if (!readFully(input, body)) return null
            return body
        }

        private fun readFully(input: InputStream, dest: ByteArray): Boolean {
            var offset = 0
            while (offset < dest.size) {
                val read = input.read(dest, offset, dest.size - offset)
                if (read < 0) return false
                offset += read
            }
            return true
        }

        private const val MAX_FRAME = 4096
    }
}
