/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hip.sim;

/**
 *
 * @author lee
 */
public class SpiFactory {
    
    public static SpiDevice getInstance(int i1, int i2, int i3) {
        return new SpiDevice();
    }
}
