/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hip;

/*
   import com.pi4j.io.gpio.GpioController;
   import com.pi4j.io.gpio.GpioFactory;
   import com.pi4j.io.gpio.GpioPinDigitalOutput;
   import com.pi4j.io.gpio.PinState;
   import com.pi4j.io.gpio.RaspiPin;
   import com.pi4j.io.spi.SpiChannel;
   import com.pi4j.io.spi.SpiDevice;
   import com.pi4j.io.spi.SpiFactory;
   import com.pi4j.io.spi.SpiMode;


import hip.sim.GpioController;
import hip.sim.GpioFactory;
import hip.sim.GpioPinDigitalOutput;
import hip.sim.PinState;
import hip.sim.RaspiPin;
import hip.sim.SpiChannel;
import hip.sim.SpiDevice;
import hip.sim.SpiFactory;
import hip.sim.SpiMode;
*/

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author lee
 */
public class VentiMachine implements Runnable {
    final ScheduledExecutorService ses;
    final ScheduledFuture<?> sf;
    
    final VentilatorGUIListener vgl;
    
    /*
    final GpioController gpio;
    final GpioPinDigitalOutput gpdo1;
    final GpioPinDigitalOutput gpdo2;
    final GpioPinDigitalOutput gpdo3;
    final SpiDevice sd0;
    final SpiDevice sd1;
    */
    
    VentState iAutoState;
    long lStateStartTime;
    
    //Default values on GUI (rpm = respirations per minute), peep & pip = target peep and target pip (cm H2O)
    float rpm = 12.0f;
    float pip = 14.0f;
    float targetPEEP = 5.0f;
    
    //Default Tidal Volume in cubic centimeters (cc) displayed on GUI
    float tidalVolume = 500.0f;
    
    //1507.282cc (91.98in3) is the calculated volume of ventilator from valve 1 to valve 2
    //1723.264cc is the calculated ventilator volume from V1 to V3 (INHALE state)
    float  tankVolume = 1507.282f;
    float inhaleStateVolume = 1723.264f;
    
    //Default values shown for lowest and highest safe PIP in cm H2O
    final float maxPIP = 40.0f;
    float minPIP = 12.0f;
    
    //Highest safe PEEP value in cm H2O (calculated whenever changed from default)
    float maxPEEP = 5.5f;
    
    //Target tank pressure and minimum acceptible tank pressure in cm H2O (calculated whenever changed from default)
    float targetTankPressure = 20.65f;
    float minTankPressure = 18.65f;
      
    //Variables to store pressure sensor values
    float tankPressure;
    float lungPressure;
     
    //Variables for state times with changing values (inhale and exhale & tank refill states)
    //Initial values below are based on 12 rpm
    float inhaleTime = 1666.67f;
    float exhaleRefillTime = 3333.33f;
    
        
                 //Variables for state times with constant values\\
    
    //Transitional state times are 0.3s because that should be just long enough to ensure message has been received and action performed
    //Make sure these values are in milliseconds (had to adjust 2/16/21)
    final int buildTankTime = 2000;
    final int tranOneTime = 1000;
    final int tranTwoTime = 300;
    final int tranThreeTime = 300;
    final int faultOneTime = 300;
    final int faultTwoTime = 300;
    final int faultThreeTime = 300;

    //Aid in clearing message box
    boolean clearErrorMessage = false;

    //Code that establishes the 10 states
    enum VentState {
        INITIALOFF,
        BUILDPRESSURE,
        TRANSITION1,
        INHALE,
        TRANSITION2,
        EXHALEREFILL,
        TRANSITION3,
        FAULT1,
        FAULT2,
        FAULT3,
    }
    
    VentiLogger vl;
    
    final VentiPiIO vpio; //change to VentiPiIO for real hardware
    
    public VentiMachine(VentilatorGUIListener vgl) throws Exception {
        this.vgl = vgl;
        iAutoState = VentState.INITIALOFF;
        
        /*
        gpio = GpioFactory.getInstance();
        gpdo1 = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01, "Valve1", PinState.LOW);
        gpdo1.setShutdownOptions(true, PinState.LOW);
        gpdo2 = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_02, "Valve2", PinState.LOW);
        gpdo2.setShutdownOptions(true, PinState.LOW);
        gpdo3 = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_03, "Valve3", PinState.LOW);
        gpdo3.setShutdownOptions(true, PinState.LOW);
        //Both SPI devices should use mode 0
        sd0 = SpiFactory.getInstance(SpiChannel.CS0, 500000, SpiMode.MODE_0);
        sd1 = SpiFactory.getInstance(SpiChannel.CS1, 500000, SpiMode.MODE_0);
        */
        vpio = new VentiPiIO();// change to VentiPiIO for real hardware
        
        //Instance of VentiLogger & starting the file
        vl = new VentiLogger();
        vl.start();
        
        ses = Executors.newSingleThreadScheduledExecutor();
        
        //Experiment with different values and document
        sf = ses.scheduleAtFixedRate(this, 0, 10, TimeUnit.MILLISECONDS);
    }
   
    /*Replace letters with new names as described in class variables (DONE)
    
    Added if and else to prevent the opening and closing of valves at the same time.
      Also, added if & else statements to ALLOW for closing of valves based on reached pressures
        WITHOUT changing the ventilator state. Need to refine.
    */
    
    
        
    //Code below is for automatic ventilator control
    
    public void run() {
        
        // Code for updating GUI with psi values
        //Changed to VentiPiIO
        try {
            tankPressure = readPSI(VentiPiIO.PressureEnum.TANK);
            vgl.notifyP1(tankPressure);
        }
        catch (Exception e) {
            vgl.notifyP1("Error");
        }
        
        //Changed to VentiPiIO
        try {
            lungPressure = readPSI(VentiPiIO.PressureEnum.LUNG);
            vgl.notifyP2(lungPressure);
        }
        catch (Exception e) {
            vgl.notifyP2("Error");
        }
        
        //lStateTime respresents the time in a given state
        long lStateTime = System.currentTimeMillis() - lStateStartTime;
                    
        switch(iAutoState) {
            case INITIALOFF:
                //When Run button pressed, runAuto is run: it changes state to BUILDPRESS
                //To allow for manual ventilator operation, get rid of the shut methods below
             /* shutV1();
                shutV2();
                shutV3();  */
                break;
            case BUILDPRESSURE:
//Parent if checks if state time is up and then nested check if tank pressure is in range or not
                if(lStateTime >= buildTankTime) {
                    if(tankPressure >= minTankPressure) {
                        setState(VentState.TRANSITION1);
                        shutV1();
                    }
                    else {
                        setState(VentState.FAULT1);                            }
                    }
//Else if checks if target pressure reached within state time and closes V1 without exiting the state
                else if(tankPressure >= targetTankPressure){
                    shutV1();
                }
//If there is time left in state and target tank pressure not reached, open V1
                else{
                     openV1();
                    }
                break;
            case TRANSITION1:
                if(lStateTime >= tranOneTime) {
                    setState(VentState.INHALE);
                    openV2();
                }
                else{
                    shutV1();
                    shutV2();
                    shutV3();
                }
                break;
            case FAULT1:
                if(lStateTime >= faultOneTime) {
                    setState(VentState.INHALE);
                    openV2();
                }
                else{
                    shutV1();
                    shutV2();
                    shutV3();
                }
                break;
            case INHALE:
//Parent if checks if PIP is within acceptible range                
                if(lStateTime >= inhaleTime) {
                    if((maxPIP >= lungPressure) && (lungPressure >= minPIP)) {
                        shutV2();
                        //Clears message box
                        clearErrorMessage = true;
                        setState(VentState.TRANSITION2);
                    }
                    else{
                         shutV2();
                         setState(VentState.FAULT2);
                        }
                    }
                else if(lungPressure >= maxPIP) {
                     shutV2();
                    }
                else{
                    openV2();    
                    }
                break;
            case TRANSITION2:
                if(lStateTime >= tranTwoTime) {
                    setState(VentState.EXHALEREFILL);
                    openV1();
                    openV3();
                }
                else{
                    shutV1();
                    shutV2();
                    shutV3();
                }
                break;
            case FAULT2:
                if(lStateTime >= faultTwoTime) {
                    setState(VentState.EXHALEREFILL);
                    openV1();
                    openV3();
                }
                else{
                    shutV1();
                    shutV2();
                    shutV3();
                }
                break;
            case EXHALEREFILL:
                if(lStateTime >= exhaleRefillTime) { 
                    if((maxPEEP < lungPressure) || (lungPressure < targetPEEP)) {
                       shutV3();
                       setState(VentState.FAULT3);
                    }
                    if((targetTankPressure < tankPressure) || (tankPressure < minTankPressure)) {
                       shutV1();
                       setState(VentState.FAULT3);
                    }
                    
                    else{
                        shutV3();
                        //Clears message box
                        clearErrorMessage = true;
                        setState(VentState.TRANSITION3);
                    }
                }
                else {
                    if(lungPressure <= targetPEEP) {
                       shutV3();
                    }
                    if(tankPressure >= targetTankPressure) {
                       shutV1();
                    }
                }                  
                break;
            case TRANSITION3:
                if(lStateTime >= tranThreeTime) {
                    setState(VentState.INHALE);
                    openV2();
                }
                else {
                    shutV1();
                    shutV2();
                    shutV3();
                }
                break;
            case FAULT3:
                if(lStateTime >= faultThreeTime) {
                    setState(VentState.INHALE);
                    openV2();
                }
                else {
                    shutV1();
                    shutV2();
                    shutV3();
                }
                break;   
            default:
                setState(VentState.TRANSITION3);
                break;
        }
        
        //Sets timing outside of lstateTime & formats data in CSV files
        vl.logData(System.currentTimeMillis(), 
                new boolean[]{
                    //Changed to VentiPiIO
                    vpio.getState(VentiPiIO.ValveEnum.VALVE1),
                    vpio.getState(VentiPiIO.ValveEnum.VALVE2),
                    vpio.getState(VentiPiIO.ValveEnum.VALVE3)
                }, 
                    new float[]{tankPressure, lungPressure});
       
        
        //Error Messages displayed in the message Box of the GUI
        if(VentState.FAULT1 == iAutoState) {
            vgl.notifyMessageBox("The tank pressure is too low. Close the bleed valve.\n");
        }
        else if(VentState.FAULT2 == iAutoState){
            vgl.notifyMessageBox("The PIP is too low.\n");
        }
        //Error messages for the ventilator in FAULT 3 state
        else if(VentState.FAULT3 == iAutoState) {
            if(lungPressure > maxPEEP) {
            vgl.notifyMessageBox("The PEEP is too high.\n");
            }
            if(tankPressure < targetTankPressure) {
                vgl.notifyMessageBox("The tank pressure is too low. Close the bleed valve.\n");
            }
        }
        //Loading ". . ." to signify there are no current messages
        else{
            if(clearErrorMessage) {
                vgl.notifyMessageBox(" . . .\n");
            }
        }
     }
    
       
    //Code for determining inhale and exhale & tank refill state times
    public void calcStateTime() {
            
       //Factor in the transition times after calculating inhale:exhale ratio--easier to adjust
       float respiTime = 60000/(rpm);
       inhaleTime = ((respiTime)/(3.0f));
       exhaleRefillTime = inhaleTime * 2.0f;
       inhaleTime -= (tranTwoTime);
       exhaleRefillTime -= (tranThreeTime);
    }
    
   //Code for reading pressure sensor values
    //Eventually convert to cm of Water
    //Changed to VentiPiIO
    float readPSI(VentiPiIO.PressureEnum pe) throws Exception {
        int i = vpio.readPressureCount(pe);
        float f = 5.0f * (i - 1638.0f) / 14746.0f;
        
        //Converts psi to cm H2O
        float g = f * 70.307f;
        return g;
    }
        
    //Code for Boyle's Law (PV = PV) to determine tank pressures
    public void calculateTankPressure() {
       
       //Tank pressure calculation
       targetTankPressure = ((pip)*(tidalVolume + inhaleStateVolume))/tankVolume;
       
       //2.0 is in cm H2O
       minTankPressure = (targetTankPressure - 2.0f);    
    }
    
    //Calculation for minPIP (2.0 is in cm H2O)
    public void calcMinPIP() {
       minPIP = pip - 2.0f;
    }
    
    //Calculation for MaxPEEP based the fact than too low peep is 90% of target peep (multiply by 1.10)
    public void calcMaxPEEP() {
       maxPEEP = targetPEEP * (1.10f);
    }
    
    
    //Checks if PEEP has reached max, adds 0.5 cm H20 if it hasn't, converts PEEP to a string, and displays it on GUI
    public String addPEEP() {
        if (targetPEEP < 20.0f) {
            targetPEEP += 0.5f;
        }
        return Float.toString(targetPEEP);
    }
    
    //Checks if PIP has reached max, adds 1.0 cm H20 if it hasn't, converts PIP to a string, and displays it on GUI
    public String addPIP() {
        if (pip < 30.0f) {
            pip += 1.0f;
        }
        return Float.toString(pip);
    }
    
    //Checks if RPM has reached max, adds 1.0/min if it hasn't, converts RPM to a string, and displays it on GUI
    public String addRPM() {
        if (rpm < 15.0f) {
            rpm += 1.0f;
        }    
        return Float.toString(rpm);
    }
    
    //Checks if TV has reached max, adds 25 cc if it hasn't, converts TV to a string, and displays it on GUI
    public String addTidalVolume() {
        if (tidalVolume < 650.0f) {
            tidalVolume += 25.0f;
        }    
        return Float.toString(tidalVolume);
    }
    
    //Checks if PEEP has reached min, subtracts 0.5 cm H20 if it hasn't, converts PEEP to a string, and displays it on GUI
    public String minusPEEP() {
        if (targetPEEP > 4.0f) {
            targetPEEP -= 0.5f;
        }
        return Float.toString(targetPEEP);
    }
    
    //Checks if PIP has reached min, subtracts 1.0 cm H20 if it hasn't, converts PIP to a string, and displays it on GUI
    public String minusPIP() {
        if (pip > 13.0f) {
            pip -= 1.0f;
        }
        return Float.toString(pip);
    }
    
    //Checks if RPM has reached min, subtracts 1.0/min if it hasn't, converts RPM to a string, and displays it on GUI
    public String minusRPM() {
        if (rpm > 12.0f) {
            rpm -= 1.0f;
        }
         return Float.toString(rpm);
    }
    
    //Checks if TV has reached min, subtracts 25 cc if it hasn't, converts TV to a string, and displays it on GUI
    public String minusTidalVolume() {
        if (tidalVolume > 300.0f) {
            tidalVolume -= 25.0f;
        }
        return Float.toString(tidalVolume);
    }
    
    //Change the state
    void setState(VentState i) {
        iAutoState = i;
        lStateStartTime = System.currentTimeMillis();
    }
    
    //First checks if the current state is INITIALOFF: if it is, then the automatic cycle begins
    public void runAuto() {
        if(VentState.INITIALOFF == iAutoState) {
            vgl.notifyValvesEnabled(false);
            setState(VentState.BUILDPRESSURE);
            openV1();
            shutV2();
            shutV3();
        }
    }
    
    public void stopAuto() {
        setState(VentState.INITIALOFF);
        shutV1();
        shutV2();
        shutV3();
        vgl.notifyValvesEnabled(true);
    }
    
    //Starts logging values
    public void enableLogger() {
        vl.setEnabled(true);
    }
    //Stops logging values
    public void disableLogger() {
        vl.setEnabled(false);

    }
    
    //Changed to VentiPiIO in the methods below

    
    public void openV1() {
        vpio.openValve(VentiPiIO.ValveEnum.VALVE1);
        vgl.notifyV1State(true);
    }
    
    public void shutV1() {
        vpio.closeValve(VentiPiIO.ValveEnum.VALVE1);
        vgl.notifyV1State(false);
    }
    
    public void openV2() {
        vpio.openValve(VentiPiIO.ValveEnum.VALVE2);
        vgl.notifyV2State(true);
    }
    
    public void shutV2() {
        vpio.closeValve(VentiPiIO.ValveEnum.VALVE2);
        vgl.notifyV2State(false);
    }
    
    public void openV3() {
        vpio.openValve(VentiPiIO.ValveEnum.VALVE3);
        vgl.notifyV3State(true);
    }
    
    public void shutV3() {
        vpio.closeValve(VentiPiIO.ValveEnum.VALVE3);
        vgl.notifyV3State(false);
    }
    
    public void shutdown() {
        sf.cancel(false);
        vpio.shutdown();
    }
}
