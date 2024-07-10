/*
 * Copyright 2002-2016 jamod & j2mod development teams
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ghgande.j2mod.modbus.utils;

import com.ghgande.j2mod.modbus.Modbus;
import com.ghgande.j2mod.modbus.facade.ModbusSerialMaster;
import com.ghgande.j2mod.modbus.procimg.ProcessImage;
import com.ghgande.j2mod.modbus.slave.ModbusSlave;
import com.ghgande.j2mod.modbus.slave.ModbusSlaveFactory;
import com.ghgande.j2mod.modbus.util.SerialParameters;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.IOException;

import static org.junit.Assert.fail;

/**
 * All the master unit tests extend this class so that the system will automatically
 * create a test slave to work with and tear it down after a run
 *
 * @author Steve O'Hara (4NG)
 * @version 2.0 (March 2016)
 */
public class AbstractTestModbusSerialASCIIMaster extends AbstractTestModbus {

    protected static ModbusSerialMaster master;
    protected static ModbusSlave slave;
    protected static final String MASTER_PORT = isWindows() ? "CNCA0" : "/dev/ttys001";
    protected static final String SLAVE_PORT = isWindows() ? "CNCB0" : "/dev/ttys002";
    protected static final String SERIAL_PORT_ERROR = "Cannot initialise serial tests - %s%n" +
            "This is likely caused by not having the serial emulator running%n" +
            "For Windows, the com2com driver needs to be installed and configured so that CNCA0 is connected to CNCB0.%n" +
            "For Mac/linux the socat utility can be used e.g. socat -d -d pty,raw,echo=0 pty,raw,echo=0%n" +
            "to connect /dev/ttys001 to /dev/ttys002";


    @BeforeClass
    public static void setUpSlave() {
        try {
            slave = createSerialSlave(false);

            // Create master
            SerialParameters parameters = new SerialParameters();
            parameters.setPortName(MASTER_PORT);
            parameters.setOpenDelay(1000);
            parameters.setEncoding(Modbus.SERIAL_ENCODING_ASCII);
            master = new ModbusSerialMaster(parameters);
            master.connect();
        }
        catch (Exception e) {
            tearDownSlave();
            fail(String.format(SERIAL_PORT_ERROR, e.getMessage()));
        }
    }

    @AfterClass
    public static void tearDownSlave() {
        if (master != null) {
            master.disconnect();
        }
        if (slave != null) {
            ModbusSlaveFactory.close(slave);
        }
        master = null;
        slave = null;
    }

    /**
     * Creates a Slave to use for testing
     *
     * @return Listener of the slave
     *
     * @throws IOException If cannot connect to port
     */
    static ModbusSlave createSerialSlave(boolean RTU) throws Exception {
        ModbusSlave slave = null;
        try {
            // Create the test data
            ProcessImage spi = getSimpleProcessImage();

            // Create a serial slave
            SerialParameters parameters = new SerialParameters();
            parameters.setPortName(SLAVE_PORT);
            parameters.setOpenDelay(1000);
            parameters.setEncoding(RTU ? Modbus.SERIAL_ENCODING_RTU : Modbus.SERIAL_ENCODING_ASCII);
            slave = ModbusSlaveFactory.createSerialSlave(parameters);
            slave.addProcessImage(UNIT_ID, spi);

            // Open the slave
            slave.open();
        }
        catch (Exception x) {
            if (slave != null) {
                slave.close();
            }
            throw new Exception(x.getMessage());
        }
        return slave;
    }

}
