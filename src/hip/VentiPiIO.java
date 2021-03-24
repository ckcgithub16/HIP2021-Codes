/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hip;

import java.util.EnumMap;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.spi.SpiChannel;
import com.pi4j.io.spi.SpiDevice;
import com.pi4j.io.spi.SpiFactory;
import com.pi4j.io.spi.SpiMode;

/**
 *
 * @author lee
 */
public class VentiPiIO extends VentiIO {
    private final EnumMap<ValveEnum, PiIOValve> emValves;
    private final EnumMap<PressureEnum, SpiDevice> emPressures;
    private final GpioController gpio;
    
    VentiPiIO() throws Exception {
        gpio = GpioFactory.getInstance();
        emValves = new EnumMap(ValveEnum.class);
        emValves.put(ValveEnum.VALVE1, new PiIOValve(RaspiPin.GPIO_01, ValveEnum.VALVE1.toString()));
        emValves.put(ValveEnum.VALVE2, new PiIOValve(RaspiPin.GPIO_02, ValveEnum.VALVE2.toString()));
        emValves.put(ValveEnum.VALVE3, new PiIOValve(RaspiPin.GPIO_03, ValveEnum.VALVE3.toString()));
        emPressures = new EnumMap(PressureEnum.class);
        emPressures.put(PressureEnum.TANK, SpiFactory.getInstance(SpiChannel.CS0, 500000, SpiMode.MODE_0));
        emPressures.put(PressureEnum.LUNG, SpiFactory.getInstance(SpiChannel.CS1, 500000, SpiMode.MODE_0));
    }
    
    @Override
    void openValve(ValveEnum ve) {
        setState(ve, true);
    }
    
    @Override
    void closeValve(ValveEnum ve) {
        setState(ve, false);
    }
    
    @Override
    void setState(ValveEnum ve, boolean bo) {
        PiIOValve piov = emValves.get(ve);
        piov.setState(bo);
    }
   
    @Override
    boolean getState(ValveEnum ve) {
        PiIOValve piov = emValves.get(ve);
        return piov.boState;
    }
    
    class PiIOValve {
        boolean boState;
        final GpioPinDigitalOutput gpdo;
        
        PiIOValve(Pin p, String s) {
            boState = false;
            gpdo = gpio.provisionDigitalOutputPin(p, s, PinState.LOW);
            gpdo.setShutdownOptions(true, PinState.LOW);
        }
        
        void setState(boolean bo) {
            boState = bo;
            gpdo.setState(bo);
        }
    }
    
    // reads the encoded count from a pressure sensor
    @Override
    int readPressureCount(PressureEnum pe) throws Exception {
        SpiDevice sd = emPressures.get(pe);
        byte[] bs = sd.write(new byte[2], 0, 2);
        
        int i = 0;
        for(int j = 0; j < bs.length; j++) {
            if(bs[j] < 0) {
                i = (i * 0x100) + 0x100 + bs[j];
            }
            else {
                i = (i * 0x100) + bs[j];
            }
        }
        return i;
    }
    
    @Override
    void shutdown() {
        gpio.shutdown();
    }
}
