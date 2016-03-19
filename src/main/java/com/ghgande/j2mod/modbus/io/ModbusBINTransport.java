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
 * Class that implements the Modbus/BIN transport
 * flavor.
 *
 * @author Dieter Wimberger
 * @version 1.2rc1 (09/11/2004)
 */
public class ModbusBINTransport extends ModbusSerialTransport {

    /**
     * Defines a virtual number for the FRAME START token (COLON).
     */
    public static final int FRAME_START = 1000;
    /**
     * Defines a virtual number for the FRAME_END token (CR LF).
     */
    public static final int FRAME_END = 2000;
    /**
     * Defines the frame start token <tt>{</tt>.
     */
    public static final int FRAME_START_TOKEN = 123;
    /**
     * Defines the frame end token <tt>}</tt>.
     */
    public static final int FRAME_END_TOKEN = 125;
    private static final Logger logger = Logger.getLogger(ModbusBINTransport.class);
    private DataInputStream m_InputStream;     //used to read from
    private ASCIIOutputStream m_OutputStream;   //used to write to
    private byte[] m_InBuffer;
    private BytesInputStream m_ByteIn;         //to read message from
    private BytesOutputStream m_ByteInOut;     //to buffer message to
    private BytesOutputStream m_ByteOut;      //write frames

    /**
     * Constructs a new <tt>MobusBINTransport</tt> instance.
     */
    public ModbusBINTransport() {
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
     * @throws java.io.IOException if an I\O related error occurs.
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
            int len;
            synchronized (m_ByteOut) {
                //write message to byte out
                msg.setHeadless();
                msg.writeTo(m_ByteOut);
                byte[] buf = m_ByteOut.getBuffer();
                len = m_ByteOut.size();

                //write message
                m_OutputStream.write(FRAME_START);               //FRAMESTART
                m_OutputStream.write(buf, 0, len);                 //PDU
                int[] crc = ModbusUtil.calculateCRC(buf, 0, len); //CRC
                m_OutputStream.write(crc[0]);
                m_OutputStream.write(crc[1]);
                m_OutputStream.write(FRAME_END);                 //FRAMEEND
                m_OutputStream.flush();
                m_ByteOut.reset();
            }
            // clears out the echoed message
            // for RS485
            if (m_Echo) {
                // read back the echoed message
                readEcho(len + 4);
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
                }
                //2. Read to FRAME_END
                synchronized (m_InBuffer) {
                    m_ByteInOut.reset();
                    while ((in = m_InputStream.read()) != FRAME_END) {
                        m_ByteInOut.writeByte(in);
                    }
                    //check CRC
                    int[] crc = ModbusUtil.calculateCRC(m_InBuffer, 0, m_ByteInOut.size() - 2);

                    if (!(m_InBuffer[m_ByteInOut.size() - 2] == crc[0] //low byte first
                            && m_InBuffer[m_ByteInOut.size() - 1] == crc[1] //hibyte
                    )) {
                        continue;
                    }
                    m_ByteIn.reset(m_InBuffer, m_ByteInOut.size());
                    in = m_ByteIn.readUnsignedByte();
                    //check unit identifier
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
                while ((m_InputStream.read()) != FRAME_START) {
                }
                //2. Read to FRAME_END
                synchronized (m_InBuffer) {
                    m_ByteInOut.reset();
                    while ((in = m_InputStream.read()) != FRAME_END) {
                        m_ByteInOut.writeByte(in);
                    }
                    //check CRC
                    int[] crc = ModbusUtil.calculateCRC(m_InBuffer, 0, m_ByteInOut.size() - 2);
                    if (!(m_InBuffer[m_ByteInOut.size() - 2] == crc[0] //low byte first
                            && m_InBuffer[m_ByteInOut.size() - 1] == crc[1] //hibyte
                    )) {
                        continue;
                    }
                    m_ByteIn.reset(m_InBuffer, m_ByteInOut.size());
                    in = m_ByteIn.readUnsignedByte();
                    //check unit identifier
                    if (in != ModbusCoupler.getReference().getUnitID()) {
                        continue;
                    }
                    m_ByteIn.reset(m_InBuffer, m_ByteInOut.size());
                    in = m_ByteIn.readUnsignedByte();
                    //check unit identifier
                    if (in != ModbusCoupler.getReference().getUnitID()) {
                        continue;
                    }
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

}