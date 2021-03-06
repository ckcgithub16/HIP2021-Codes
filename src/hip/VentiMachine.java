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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author lee
 */
public class VentiMachine implements VentiUserListener, Runnable {
    final ScheduledExecutorService ses;
    ScheduledFuture<?> sf;
    
    final VentiMachineListener vmlGUI;
    final VentiNetworkLink vnl;
    
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
    
    //Highest safe PEEP value in cm H2O (calculated whenever changed from default below)
    float maxPEEP = 5.5f;
    
    //Lowest safe PEEP value in cm H2O (calculated whenever changed from default below)
    float minPEEP = 4.5f;
    
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
    
    
    String lastMessage;

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
    
    final VentiIO vpio;
    List<VentiMachineListener> lVMLs;
    
    public VentiMachine(VentiMachineListener vml, boolean boSim) throws Exception {
        this.vmlGUI = vml;
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
        if(boSim == true) {
            vpio = new VentiIO();
        }
        else {
            vpio = new VentiPiIO();
        }
        
        //Instance of VentiLogger & starting the file
        vl = new VentiLogger();
        
        ses = Executors.newSingleThreadScheduledExecutor();
        
        vnl = new VentiNetworkLink(true);
        
        //Add vnl back to enable server
        lVMLs = Arrays.asList(vmlGUI/*, vnl*/);
    }
    
    void start() {
        stopAuto();
        
        //Display initial settings on the GUI
        notifyPEEP(Float.toString(targetPEEP));
        notifyPIP(Float.toString(pip));
        notifyRPM(Float.toString(rpm));
        notifyTidalVolume(Float.toString(tidalVolume));

        vnl.setListener(this);
        vnl.start();
        
        vl.start();

        //Experiment with different values and document
        sf = ses.scheduleAtFixedRate(this, 0, 100, TimeUnit.MILLISECONDS);
        
        // start simulation
        vpio.start();
    }
   
    /*Replace letters with new names as described in class variables (DONE)
    
    Added if and else to prevent the opening and closing of valves at the same time.
      Also, added if & else statements to ALLOW for closing of valves based on reached pressures
        WITHOUT changing the ventilator state. Need to refine.
    */
    
    
        
    //Code below is for automatic ventilator control
    
    @Override
    public void run() {
        
        // Code for updating GUI with pressure values 
        
        String sTankPressure;
        String sLungPressure;
        //(CHECK THIS LOGIC): Does not identify which sensor is not working\\
        try {
            // refer to base class VentiIO even if using a class that extends VentiIO
            tankPressure = readPressure(VentiIO.PressureEnum.TANK);
            lungPressure = readPressure(VentiIO.PressureEnum.LUNG);
            
            sTankPressure = String.format("%.2f", tankPressure);
            sLungPressure = String.format("%.2f", lungPressure);
            // vmlGUI.notifyPressures(String.format("%.2f", tankPressure),String.format("%.2f", lungPressure));
        }
        catch (Exception e) {
            //Adjust
            sTankPressure = "Error";
            sLungPressure = "Error";
            // vmlGUI.notifyPressures("Error", "Error");
        }
        final String sTankFinal = sTankPressure;
        final String sLungFinal = sLungPressure;
        lVMLs.forEach(vml -> vml.notifyPressures(sTankFinal, sLungFinal));
        
        /*           No Longer Needed b/se of unified pressure update
        try {
            lungPressure = readPressure(VentiIO.PressureEnum.LUNG);
            vml.notifyP2(String.format("%.2f", lungPressure));
        }
        catch (Exception e) {
            vml.notifyP2("Error");
        }
        */
        
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
                    if((maxPEEP < lungPressure) || (lungPressure < minPEEP)) {
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
                    if(lungPressure <= minPEEP) {
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
                    vpio.getState(VentiIO.ValveEnum.VALVE1),
                    vpio.getState(VentiIO.ValveEnum.VALVE2),
                    vpio.getState(VentiIO.ValveEnum.VALVE3)
                }, 
                    new float[]{tankPressure, lungPressure});
       
        
        //Error Messages displayed in the message Box of the GUI
        if(VentState.FAULT1 == iAutoState) {
            notifyMessageBox("The tank pressure is too low. Close the bleed valve.\n");
        }
        else if(VentState.FAULT2 == iAutoState){
            notifyMessageBox("The PIP is too low.\n");
        }
        //Error messages for the ventilator in FAULT 3 state
        else if(VentState.FAULT3 == iAutoState) {
            if(lungPressure > maxPEEP) {
                notifyMessageBox("The PEEP is too high.\n");
            }
            if(tankPressure < targetTankPressure) {
                notifyMessageBox("The tank pressure is too low. Close the bleed valve.\n");
            }
        }
        //Loading ". . ." to signify there are no current messages
        else{
            if(clearErrorMessage) {
               notifyMessageBox(" . . .\n");
            }
        }
     }
    
    
    void notifyMessageBox(String s) {
        if (!s.equals(lastMessage)) {
            lVMLs.forEach(vml -> vml.notifyMessageBox(s));
            lastMessage = s;
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
    
   //Code for reading pressure sensor values and converting them to cm H2O
    float readPressure(VentiIO.PressureEnum pe) throws Exception {
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
    
    //Calculation for MaxPEEP based the fact that too low peep is 90% of target peep (so I multiply by 1.10 here)
    public void calcMaxPEEP() {
       maxPEEP = targetPEEP * (1.10f);
    }
    
    //Calculation for minPEEP based on the fact that too low peep is 90% of target peep (multiply by 0.9)
    public void calcMinPEEP() {
        minPEEP = targetPEEP * (0.9f);
    }
    
    //Use addPEEP as an example...
    //Checks if PEEP has reached max, adds 0.5 cm H20 if it hasn't, converts PEEP to a string, and displays it on GUI
    @Override
    public void addPEEP() {
        if (targetPEEP < 20.0f) {
            targetPEEP += 0.5f;
        }
        calcMaxPEEP();
        calcMinPEEP();
        notifyPEEP(Float.toString(targetPEEP));
    }
    
    void notifyPEEP(String s) {
        lVMLs.forEach(vml -> vml.notifyPEEP(s));
    }
    
    //Checks if PIP has reached max, adds 1.0 cm H20 if it hasn't, converts PIP to a string, and displays it on GUI
    @Override
    public void addPIP() {
        if (pip < 30.0f) {
            pip += 1.0f;
        }
        calcMinPIP();
        calculateTankPressure();
        notifyPIP(Float.toString(pip));
    }
    
    void notifyPIP(String s) {
        lVMLs.forEach(vml -> vml.notifyPIP(s));
    }
    
    //Checks if RPM has reached max, adds 1.0/min if it hasn't, converts RPM to a string, and displays it on GUI
    @Override
    public void addRPM() {
        if (rpm < 15.0f) {
            rpm += 1.0f;
        }    
        calcStateTime();
        notifyRPM(Float.toString(rpm));
    }
    
    void notifyRPM(String s) {
        lVMLs.forEach(vml -> vml.notifyRPM(s));
    }
    
    //Checks if TV has reached max, adds 25 cc if it hasn't, converts TV to a string, and displays it on GUI
    @Override
    public void addTidalVolume() {
        if (tidalVolume < 650.0f) {
            tidalVolume += 25.0f;
        } 
        calculateTankPressure();
        notifyTidalVolume(Float.toString(tidalVolume));
    }
    
    void notifyTidalVolume(final String s) {
        lVMLs.forEach(vml -> vml.notifyTidalVolume(s));
    }
    
    //Checks if PEEP has reached min, subtracts 0.5 cm H20 if it hasn't, converts PEEP to a string, and displays it on GUI
    @Override
    public void minusPEEP() {
        if (targetPEEP > 4.0f) {
            targetPEEP -= 0.5f;
        }
        calcMaxPEEP();
        calcMinPEEP();
        notifyPEEP(Float.toString(targetPEEP));
    }
    
    //Checks if PIP has reached min, subtracts 1.0 cm H20 if it hasn't, converts PIP to a string, and displays it on GUI
    @Override
    public void minusPIP() {
        if (pip > 13.0f) {
            pip -= 1.0f;
        }
        calcMinPIP();
        calculateTankPressure();
        notifyPIP(Float.toString(pip));
    }
    
    //Checks if RPM has reached min, subtracts 1.0/min if it hasn't, converts RPM to a string, and displays it on GUI
    @Override
    public void minusRPM() {
        if (rpm > 12.0f) {
            rpm -= 1.0f;
        }
        calcStateTime();
        notifyRPM(Float.toString(rpm));
    }
    
    //Checks if TV has reached min, subtracts 25 cc if it hasn't, converts TV to a string, and displays it on GUI
    @Override
    public void minusTidalVolume() {
        if (tidalVolume > 300.0f) {
            tidalVolume -= 25.0f;
        }
        calculateTankPressure();
        notifyTidalVolume(Float.toString(tidalVolume));
    }
    
    //Change the state
    void setState(VentState i) {
        iAutoState = i;
        lStateStartTime = System.currentTimeMillis();
    }
    
    //First checks if the current state is INITIALOFF: if it is, then the automatic cycle begins
    @Override
    public void runAuto() {
        if(VentState.INITIALOFF == iAutoState) {
            notifyValvesEnabled(Boolean.FALSE.toString());
            setState(VentState.BUILDPRESSURE);
            openV1();
            shutV2();
            shutV3();
        }
    }
    
    void notifyValvesEnabled(String s) {
        lVMLs.forEach(vml -> vml.notifyValvesEnabled(s));
    }
    
    @Override
    public void stopAuto() {
        setState(VentState.INITIALOFF);
        shutV1();
        shutV2();
        shutV3();
        notifyValvesEnabled(Boolean.TRUE.toString());
    }
    
    //Starts logging values
    @Override
    public void enableLogger() {
        vl.setEnabled(true);
    }
    //Stops logging values
    @Override
    public void disableLogger() {
        vl.setEnabled(false);

    }
    
    
    @Override
    public void openV1() {
        vpio.openValve(VentiIO.ValveEnum.VALVE1);
        notifyV1State(Boolean.TRUE.toString());
    }
    
    @Override
    public void shutV1() {
        vpio.closeValve(VentiIO.ValveEnum.VALVE1);
        notifyV1State(Boolean.FALSE.toString());
    }
    
    void notifyV1State(String s) {
        lVMLs.forEach(vml -> vml.notifyV1State(s));
    }
    
    @Override
    public void openV2() {
        vpio.openValve(VentiIO.ValveEnum.VALVE2);
        notifyV2State(Boolean.TRUE.toString());
    }
    
    @Override
    public void shutV2() {
        vpio.closeValve(VentiIO.ValveEnum.VALVE2);
        notifyV2State(Boolean.FALSE.toString());
    }
    
    void notifyV2State(String s) {
        lVMLs.forEach(vml -> vml.notifyV2State(s));
    }
    
    @Override
    public void openV3() {
        vpio.openValve(VentiIO.ValveEnum.VALVE3);
        notifyV3State(Boolean.TRUE.toString());
    }
    
    @Override
    public void shutV3() {
        vpio.closeValve(VentiIO.ValveEnum.VALVE3);
        notifyV3State(Boolean.FALSE.toString());
    }
    
    void notifyV3State(String s) {
        lVMLs.forEach(vml -> vml.notifyV3State(s));
    }
    
    @Override
    public void shutdown() {
        sf.cancel(false);
        vpio.shutdown();
    }
    
}
