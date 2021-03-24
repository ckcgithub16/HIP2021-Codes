/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hip;

/**
 *
 * I commented out the lines associated with pressure sensor 3
 * @author lee
 */
public interface VentiMachineListener {
    // TODO combine notifyP1 and notifyP2 to reduce event queue overhead
    void notifyP1(String s);
    
    void notifyP2(String s);
    
    //void notifyP3(String s);
    
    // void notifyP1(float f);
    
    //  void notifyP2(float f);
    
    void notifyMessageBox(String s);
    
    //void notifyP3(float f);
    
    void notifyValvesEnabled(String s);
    
    void notifyV1State(String s);
    
    void notifyV2State(String s);
    
    void notifyV3State(String s);
    
    //Plus and Minus Button control
    // TODO copy the following into VentiUserListener
    // TODO rework the following into notify methods
    void addPEEP();
    
    void addPIP();

    void addRPM();

    void addTidalVolume();

    void minusPEEP();
    
    void minusPIP();
    
    void minusRPM();
    
    void minusTidalVolume();
    
  
    
}
