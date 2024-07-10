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
import com.ghgande.j2mod.modbus.util.SerialParameters;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import static org.junit.Assert.fail;

/**
 * All the master unit tests extend this class so that the system will automatically
 * create a test slave to work with and tear it down after a run
 *
 * @author Steve O'Hara (4NG)
 * @version 2.0 (March 2016)
 */
public class AbstractTestModbusSerialRTUMaster extends AbstractTestModbusSerialASCIIMaster {

    @BeforeClass
    public static void setUpSlave() {
        try {
            slave = createSerialSlave(true);

            // Create master
            SerialParameters parameters = new SerialParameters();
            parameters.setPortName(MASTER_PORT);
            parameters.setOpenDelay(1000);
            parameters.setEncoding(Modbus.SERIAL_ENCODING_RTU);
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
        AbstractTestModbusSerialASCIIMaster.tearDownSlave();
    }
}
