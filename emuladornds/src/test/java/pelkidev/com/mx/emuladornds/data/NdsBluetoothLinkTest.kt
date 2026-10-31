package pelkidev.com.mx.emuladornds.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NdsBluetoothLinkTest {

    @Test
    fun mpCodec_roundTripsHeaderAndPayload() {
        val encoded = NdsMpCodec.encode(
            NdsMpFrame(
                senderId = 1,
                type = 2,
                timestamp = 99L,
                payload = byteArrayOf(9, 8, 7),
            ),
        )

        val decoded = NdsMpCodec.decode(encoded)!!

        assertEquals(1, decoded.senderId)
        assertEquals(2, decoded.type)
        assertEquals(99L, decoded.timestamp)
        assertArrayEquals(byteArrayOf(9, 8, 7), decoded.payload)
    }

    @Test
    fun pairing_sameNds_exchangesMpFramesWithoutSendingRom() {
        val host = PairingNdsBluetoothLink()
        val client = PairingNdsBluetoothLink()
        host.connect("Pokemon HeartGold.nds")
        client.connect("Pokemon HeartGold.nds")
        assertTrue(host.linked)
        assertTrue(client.linked)

        val frame = NdsMpCodec.encode(
            NdsMpFrame(
                senderId = 0,
                type = 0,
                timestamp = 1L,
                payload = byteArrayOf(0x11, 0x22),
            ),
        )
        assertTrue(host.sendMp(frame))

        val received = client.recvMp()!!
        val decoded = NdsMpCodec.decode(received)!!
        assertArrayEquals(byteArrayOf(0x11, 0x22), decoded.payload)
        assertFalse(received.decodeToString().contains("HeartGold"))
        assertNotEquals("Pokemon HeartGold.nds", received.decodeToString())
    }

    @Test
    fun pairing_withoutPeer_sendMpDoesNothing() {
        val alone = PairingNdsBluetoothLink()
        alone.connect("juego.nds")

        assertFalse(alone.linked)
        assertFalse(alone.sendMp(byteArrayOf(1)))
        assertNull(alone.recvMp())
        alone.disconnect()
    }

    @Test
    fun pairing_doesNotQueueARomFile() {
        val host = PairingNdsBluetoothLink()
        val client = PairingNdsBluetoothLink()
        host.connect("juego.nds")
        client.connect("juego.nds")

        assertNull(client.recvMp())
        assertNull(host.recvMp())
    }
}
