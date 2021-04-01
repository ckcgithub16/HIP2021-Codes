/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hip;

import java.util.EnumMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author lee
 */
public class VentiIO implements Runnable {
    final EnumMap<ValveEnum, IOValve> emValves;
    final EnumMap<PressureEnum, Float> emPressureSensors;

    final ScheduledExecutorService ses;
    ScheduledFuture<?> sf;

    // simulation period in milliseconds
    static final int ISIMPERIOD = 10;
    
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
        vio.getState(ValveEnum.VALVE1);
        vio.getState(ValveEnum.VALVE2);
        vio.getState(ValveEnum.VALVE3);
    }
    
    VentiIO() {
        emValves = new EnumMap(ValveEnum.class);
        for(ValveEnum ve : ValveEnum.values()) {
            emValves.put(ve, new IOValve());
        }
        emPressureSensors = new EnumMap(PressureEnum.class);
        for(PressureEnum pe : PressureEnum.values()) {
            emPressureSensors.put(pe, 0.0f);
        }
        ses = Executors.newSingleThreadScheduledExecutor();
    }
    
    void start() {
        sf = ses.scheduleAtFixedRate(this, 0, ISIMPERIOD, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public void run() {
        final boolean boV1Open = emValves.get(ValveEnum.VALVE1).getState();
        final boolean boV2Open = emValves.get(ValveEnum.VALVE2).getState();
        final boolean boV3Open = emValves.get(ValveEnum.VALVE3).getState();
        float fTankPressure = emPressureSensors.get(PressureEnum.TANK);
        float fLungPressure = emPressureSensors.get(PressureEnum.LUNG);
        
        if(boV2Open) {
            float fDelta = fTankPressure - fLungPressure;
            float fLungDeltaEqualized = fDelta * 1.0f / 3.0f;
            float fLungChange = Math.signum(fDelta) * 100.0f * ISIMPERIOD / 1000.0f;
            if(Math.abs(fLungDeltaEqualized) > Math.abs(fLungChange)) {
                fLungPressure += fLungChange;
            }
            else {
                fLungPressure += fLungDeltaEqualized;
            }

            float fTankDeltaEqualized = fDelta * 2.0f / 3.0f;
            float fTankChange = Math.signum(fDelta) * 200.0f * ISIMPERIOD / 1000.0f;
            if(Math.abs(fTankDeltaEqualized) > Math.abs(fTankChange)) {
                fTankPressure -= fTankChange;
            }
            else {
                fTankPressure -= fTankDeltaEqualized;
            }
        }
        
        if(boV1Open) {
            if(boV2Open) {
                if(!boV3Open) {
                    fLungPressure += 30.0f * ISIMPERIOD / 1000.0f;
                    fTankPressure += 30.0f * ISIMPERIOD / 1000.0f;
                }
            }
            else {
                fTankPressure += 100.0f * ISIMPERIOD / 1000.0f;
                if(boV3Open && fLungPressure > 0.0f) {
                    fLungPressure -= 10.0f * ISIMPERIOD / 1000.0f;
                }
            }
        }
        else {
            if(!boV2Open) {
                if(boV3Open && fLungPressure > 0.0f) {
                    fLungPressure -= 10.0f * ISIMPERIOD / 1000.0f;
                }
            }
            else {
                if(boV3Open && fLungPressure > 0.0f) {
                    fLungPressure -= 5.0f * ISIMPERIOD / 1000.0f;
                    fTankPressure -= 5.0f * ISIMPERIOD / 1000.0f;
                }
            }
        }
        
        emPressureSensors.put(PressureEnum.TANK, fTankPressure);
        emPressureSensors.put(PressureEnum.LUNG, fLungPressure);
    }
    
    void openValve(ValveEnum ve) {
        setState(ve, true);
    }
    
    void closeValve(ValveEnum ve) {
        setState(ve, false);
    }

    void setState(ValveEnum ve, boolean bo) {
        IOValve iov = emValves.get(ve);
        iov.setState(bo);
    }
    
    boolean getState(ValveEnum ve) {
        IOValve iov = emValves.get(ve);
        return iov.getState();
    }
    
    class IOValve {
        boolean boState;
        long lNewestChangeTime;
        
        IOValve() {
            boState = false;
            lNewestChangeTime = System.currentTimeMillis();
        }
        
        void setState(boolean bo) {
            if(bo != boState) {
                boState = bo;
                lNewestChangeTime = System.currentTimeMillis();
            }
        }
        
        boolean getState() {
            return boState;
        }
    }
    
    int readPressureCount(PressureEnum pe) throws Exception {
        Float fPressureCMH2O = emPressureSensors.get(pe);
        Float fCount = fPressureCMH2O * 14746.0f / 351.535f + 1638.0f;
        return fCount.intValue();
    }

    void shutdown() {
        sf.cancel(true);
    }
}
