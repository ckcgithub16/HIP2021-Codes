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
public class GpioFactory {
    static GpioController gc;
    static {
        gc = new GpioController();
    }
    
    public static GpioController getInstance() {
        return gc;
    }

}
