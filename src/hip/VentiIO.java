/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hip;

import java.util.EnumMap;

/**
 *
 * @author lee
 */
public class VentiIO {
    private final EnumMap<ValveEnum, Boolean> emValves;
    private final EnumMap<PressureEnum, Float> emPressureSensors;
    
    enum ValveEnum {
        VALVE1,
        VALVE2,
        VALVE3
    }
    
    enum PressureEnum {
        TANK,
        LUNG
    }
    
    public static void main(String[] ss) {
        VentiIO vio = new VentiIO();
        vio.openValve(ValveEnum.VALVE2);
        boolean bo1 = vio.getState(ValveEnum.VALVE1);
        boolean bo2 = vio.getState(ValveEnum.VALVE2);
        boolean bo3 = vio.getState(ValveEnum.VALVE3);
    }
    
    VentiIO() {
        emValves = new EnumMap(ValveEnum.class);
        for(ValveEnum ve : ValveEnum.values()) {
            emValves.put(ve, Boolean.FALSE);
        }
        emPressureSensors = new EnumMap(PressureEnum.class);
        for(PressureEnum pe : PressureEnum.values()) {
            emPressureSensors.put(pe, 0.0f);
        }
    }
    
    void openValve(ValveEnum ve) {
        emValves.put(ve, Boolean.TRUE);
    }
    
    void closeValve(ValveEnum ve) {
        emValves.put(ve, Boolean.FALSE);
    }

    void setState(ValveEnum ve, boolean bo) {
        emValves.put(ve, bo);
    }
    
    boolean getState(ValveEnum ve) {
        return emValves.get(ve);
    }
    
    int readPressureCount(PressureEnum pe) throws Exception {
        return 0;
    }

    void shutdown() {
    }
}
