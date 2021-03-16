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
public interface VentilatorGUIListener {
    void notifyP1(String s);
    
    void notifyP2(String s);
    
    //void notifyP3(String s);
    
    void notifyP1(float f);
    
    void notifyP2(float f);
    
    void notifyMessageBox(String s);
    
    //void notifyP3(float f);
    
    void notifyValvesEnabled(boolean bo);
    
    void notifyV1State(boolean bo);
    
    void notifyV2State(boolean bo);
    
    void notifyV3State(boolean bo);
    
    //Plus and Minus Button control\\
    
    void addPEEP();
    
    void addPIP();

    void addRPM();

    void addTidalVolume();

    void minusPEEP();
    
    void minusPIP();
    
    void minusRPM();
    
    void minusTidalVolume();
    
  
    
}
