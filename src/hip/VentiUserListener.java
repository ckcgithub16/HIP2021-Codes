/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hip;

/**
 *
 * @author lee
 */
public interface VentiUserListener {
    void shutdown();
    
    void runAuto();
    
    void stopAuto();
    
    void enableLogger();
    
    void disableLogger();
    
    void openV1();
    
    void shutV1();
    
    void openV2();
    
    void shutV2();
    
    void openV3();
    
    void shutV3();
    
    //Methods transfered from machine listener
    void addPEEP();
    
    void addPIP();
    
    void addTidalVolume();
    
    void addRPM();
    
    void minusPEEP();
    
    void minusPIP();
    
    void minusTidalVolume();
    
    void minusRPM();
    
}
