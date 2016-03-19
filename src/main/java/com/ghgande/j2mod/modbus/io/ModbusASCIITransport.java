/*
 * This file is part of j2mod.
 *
 * j2mod is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * j2mod is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Foobar.  If not, see <http://www.gnu.org/licenses
 */
package com.ghgande.j2mod.modbus.io;

import com.ghgande.j2mod.modbus.Modbus;
import com.ghgande.j2mod.modbus.ModbusCoupler;
import com.ghgande.j2mod.modbus.ModbusIOException;
import com.ghgande.j2mod.modbus.msg.ModbusMessage;
import com.ghgande.j2mod.modbus.msg.ModbusRequest;
import com.ghgande.j2mod.modbus.msg.ModbusResponse;
import com.ghgande.j2mod.modbus.util.Logger;
import com.ghgande.j2mod.modbus.util.ModbusUtil;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Class that implements the Modbus/ASCII transport
 * flavor.
 *
 * @author Dieter Wimberger
 * @author John Charlton
 * @version 1.2rc1 (09/11/2004)
 */
public class ModbusASCIITransport extends ModbusSerialTransport {

    /**
     * Defines a virtual number for the FRAME START token (COLON).
     */
    public static final int FRAME_START = 1000;
    /**
     * Defines a virtual number for the FRAME_END token (CR LF).
     */
    public static final int FRAME_END = 2000;
    private static final Logger logger = Logger.getLogger(ModbusASCIITransport.class);
    private DataInputStream m_InputStream;     //used to read from
    private ASCIIOutputStream m_OutputStream;   //used to write to
    private byte[] m_InBuffer;
    private BytesInputStream m_ByteIn;         //to read message from
    private BytesOutputStream m_ByteInOut;     //to buffer message to
    private BytesOutputStream m_ByteOut;      //write frames

    /**
     * Constructs a new <tt>MobusASCIITransport</tt> instance.
     */
    public ModbusASCIITransport() {
    }

    private static int calculateLRC(byte[] data, int off, int len) {
        int lrc = 0;
        for (int i = off; i < len; i++) {
            lrc += ((int)data[i]) & 0xFF;
        }
        return (-lrc) & 0xff;
    }

    public ModbusTransaction createTransaction() {
        return new ModbusSerialTransaction();
    }

    /**
     * Prepares the input and output streams of this
     * <tt>ModbusASCIITransport</tt> instance.
     * The raw input stream will be wrapped into a
     * filtered <tt>DataInputStream</tt>.
     *
     * @param in  the input stream to be used for reading.
     * @param out the output stream to be used for writing.
     *
     * @throws IOException if an I\O related error occurs.
     */
    public void prepareStreams(InputStream in, OutputStream out) throws IOException {
        m_InputStream = new DataInputStream(new ASCIIInputStream(in));
        m_OutputStream = new ASCIIOutputStream(out);
        m_ByteOut = new BytesOutputStream(Modbus.MAX_MESSAGE_LENGTH);
        m_InBuffer = new byte[Modbus.MAX_MESSAGE_LENGTH];
        m_ByteIn = new BytesInputStream(m_InBuffer);
        m_ByteInOut = new BytesOutputStream(m_InBuffer);
    }

    public void close() throws IOException {
        m_InputStream.close();
        m_OutputStream.close();
    }

    public void writeMessage(ModbusMessage msg) throws ModbusIOException {

        try {
            synchronized (m_ByteOut) {
                //write message to byte out
                msg.setHeadless();
                msg.writeTo(m_ByteOut);
                byte[] buf = m_ByteOut.getBuffer();
                int len = m_ByteOut.size();

                //write message
                m_OutputStream.write(FRAME_START);               //FRAMESTART
                m_OutputStream.write(buf, 0, len);                 //PDU
                logger.debug("Writing: " + ModbusUtil.toHex(buf, 0, len));
                m_OutputStream.write(calculateLRC(buf, 0, len)); //LRC
                m_OutputStream.write(FRAME_END);                 //FRAMEEND
                m_OutputStream.flush();
                m_ByteOut.reset();
                // clears out the echoed message
                // for RS485
                if (m_Echo) {
                    // read back the echoed message
                    readEcho(len + 3);
                }
            }
        }
        catch (Exception ex) {
            throw new ModbusIOException("I/O failed to write");
        }
    }

    public ModbusRequest readRequest() throws ModbusIOException {

        boolean done = false;
        ModbusRequest request = null;

        int in;

        try {
            do {
                //1. Skip to FRAME_START
                while ((m_InputStream.read()) != FRAME_START) {
                    // Nothing to do
                }

                //2. Read to FRAME_END
                synchronized (m_InBuffer) {
                    m_ByteInOut.reset();
                    while ((in = m_InputStream.read()) != FRAME_END) {
                        if (in == -1) {
                            throw new IOException("I/O exception - Serial port timeout.");
                        }
                        m_ByteInOut.writeByte(in);
                    }
                    //check LRC
                    if (m_InBuffer[m_ByteInOut.size() - 1] != calculateLRC(m_InBuffer, 0, m_ByteInOut.size(), 1)) {
                        continue;
                    }
                    m_ByteIn.reset(m_InBuffer, m_ByteInOut.size());
                    in = m_ByteIn.readUnsignedByte();
                    //check message with this slave unit identifier
                    if (in != ModbusCoupler.getReference().getUnitID()) {
                        continue;
                    }
                    in = m_ByteIn.readUnsignedByte();
                    //create request
                    request = ModbusRequest.createModbusRequest(in);
                    request.setHeadless();
                    //read message
                    m_ByteIn.reset(m_InBuffer, m_ByteInOut.size());
                    request.readFrom(m_ByteIn);
                }
                done = true;
            } while (!done);
            return request;
        }
        catch (Exception ex) {
            if (Modbus.debug) {
                logger.debug(ex.getMessage());
            }
            throw new ModbusIOException("I/O exception - failed to read.");
        }

    }

    public ModbusResponse readResponse() throws ModbusIOException {

        boolean done = false;
        ModbusResponse response = null;
        int in;

        try {
            do {
                //1. Skip to FRAME_START
                while ((in = m_InputStream.read()) != FRAME_START) {
                    if (in == -1) {
                        throw new IOException("I/O exception - Serial port timeout.");
                    }
                }
                //2. Read to FRAME_END
                synchronized (m_InBuffer) {
                    m_ByteInOut.reset();
                    while ((in = m_InputStream.read()) != FRAME_END) {
                        if (in == -1) {
                            throw new IOException("I/O exception - Serial port timeout.");
                        }
                        m_ByteInOut.writeByte(in);
                    }
                    int len = m_ByteInOut.size();
                    if (Modbus.debug) {
                        logger.debug("Received: " + ModbusUtil.toHex(m_InBuffer, 0, len));
                    }
                    //check LRC
                    if (m_InBuffer[len - 1] != calculateLRC(m_InBuffer, 0, len, 1)) {
                        continue;
                    }

                    m_ByteIn.reset(m_InBuffer, m_ByteInOut.size());
                    m_ByteIn.readUnsignedByte();
                    // JDC: To check slave unit identifier in a response we need to know
                    // the slave id in the request.  This is not tracked since slaves
                    // only respond when a master request is made and there is only one
                    // master.  We are the only master, so we can assume that this
                    // response message is from the slave responding to the last request.
                    in = m_ByteIn.readUnsignedByte();
                    //create request
                    response = ModbusResponse.createModbusResponse(in);
                    response.setHeadless();
                    //read message
                    m_ByteIn.reset(m_InBuffer, m_ByteInOut.size());
                    response.readFrom(m_ByteIn);
                }
                done = true;
            } while (!done);
            return response;
        }
        catch (Exception ex) {
            if (Modbus.debug) {
                logger.debug(ex.getMessage());
            }
            throw new ModbusIOException("I/O exception - failed to read.");
        }
    }

    private byte calculateLRC(byte[] data, int off, int length, int tailskip) {
        int lrc = 0;
        for (int i = off; i < length - tailskip; i++) {
            lrc += ((int)data[i]) & 0xFF;
        }
        return (byte)((-lrc) & 0xff);
    }

    public boolean getDebug() {
        return "true".equals(System.getProperty("com.ghgande.j2mod.modbus.debug"));
    }

}