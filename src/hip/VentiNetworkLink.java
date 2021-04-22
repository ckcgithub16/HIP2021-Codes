/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hip;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Iterator;

/**
 *
 * @author lee
 */
public class VentiNetworkLink implements VentiMachineListener, VentiUserListener, Runnable {
    
    final boolean boVentiMachine;
    final Thread tvnl;
    final Thread tsr;
    final Thread tdr;
    VentiMachineListener vml;
    VentiUserListener vul;
    VentiUserListener vulThis;
    boolean boReady;
    
    public static void main(String[] ss) throws Exception {
        System.err.println(InetAddress.getLocalHost().getHostAddress());
        boolean bo = false;
        if(ss.length > 0 && ss[0].equals("-machine")) {
            bo = true;
        }
        VentiNetworkLink vnl = new VentiNetworkLink(bo);
        vnl.start();
    }
    
    public VentiNetworkLink(boolean boVentiMachine) {
        this.boVentiMachine = boVentiMachine;
        tvnl = new Thread(this);
        tsr = new Thread(new SessionReceiver());
        tdr = new Thread(new DataReceiver());
    }
    
    public void setListener(VentiUserListener vul) {
        this.vul = vul;
    }
    
    public void setListener(VentiMachineListener vml) {
        this.vml = vml;
    }
    
    public void start() {
        tvnl.start();
        tsr.start();
        tdr.start();
        if(!boVentiMachine) {
            vulThis = this;
        }
        boReady = true;
    }
    
    @Override
    public boolean isReady() {
        return boReady;
    }
    
    InetAddress iaLAN;
    InetAddress iaLANBroadcast;
    DatagramSocket dsSessionSend;
    final static int ISESSIONPORT = 12021;
    DatagramSocket dsDataSend;
    DatagramPacket dpDataSend;
    final String SVENTIMACHINE = "VentiMachine";
    final String SVENTIUSER = "VentiUser";

    @Override
    public void run() {
        DatagramPacket dp = new DatagramPacket(
                new byte[VentiNetworkLink.IMAXPACKETLENGTH], 
                VentiNetworkLink.IMAXPACKETLENGTH);

        try {
            Enumeration<NetworkInterface> eni = NetworkInterface.getNetworkInterfaces();
            while(eni.hasMoreElements()) {
                NetworkInterface ni = eni.nextElement();
                Iterator<InterfaceAddress> iia = ni.getInterfaceAddresses().iterator();
                while(iia.hasNext()) {
                    InterfaceAddress ifa = iia.next();
                    InetAddress ia = ifa.getAddress();
                    if(!ia.isLoopbackAddress() && ia.isSiteLocalAddress()) {
                        if(iaLAN == null) {
                            iaLAN = ia;
                            iaLANBroadcast = ifa.getBroadcast();
                            System.err.println(iaLAN);
                            System.err.println(iaLANBroadcast);
                        }
                    }
                }
            }
        }
        catch(IOException ioe) {
            System.err.println("IOException in determining addresses");
        }
        
        while(true) {
            if(dsSessionSend == null) {
                try {
                    dsSessionSend = new DatagramSocket();
                }
                catch(IOException ioe) {
                    System.err.println("IOException in creating Session Send socket");
                }
                if(boVentiMachine) {
                    try {
                        dsSessionSend.setBroadcast(true);
                    }
                    catch(SocketException se) {
                        dsSessionSend.close();
                        System.err.println("SocketException in enabling broadcast");
                    }
                }
            }
            if(dsDataSend == null) {
                try {
                    dsDataSend = new DatagramSocket();
                }
                catch(IOException ioe) {
                    System.err.println("IOException in creating Data Send socket");
                }
                dpDataSend = new DatagramPacket(
                    new byte[VentiNetworkLink.IMAXPACKETLENGTH], 
                    VentiNetworkLink.IMAXPACKETLENGTH);
            }
            if(dsSessionSend != null && dsDataSend != null) {
                if(boVentiMachine) {
                    try {
                        dp.setAddress(iaLANBroadcast);
                        dp.setPort(ISESSIONPORT);
                        dp.setData(SVENTIMACHINE.getBytes());
                        dsSessionSend.send(dp);
                        System.err.println("Broadcasting");
                    }
                    catch(IOException ioe) {
                        dsSessionSend.close();
                        System.err.println("IOException in Session send");
                    }
                }
            }
            try {
                Thread.sleep(1000);
            }
            catch(InterruptedException ie) {
                System.err.println("Interrupted Exception in creating Send sockets");
            }
        }
    }
    
    VentilatorGUI vgui;
    InetAddress iaRemote;
    final static int IMAXPACKETLENGTH = 2048;
    final static int IRECEIVETIMEOUT = 1000;
            
    class SessionReceiver implements Runnable {
        DatagramSocket dsSessionReceiver;
        
        @Override
        public void run() {
            DatagramPacket dpSend = new DatagramPacket(
                    new byte[VentiNetworkLink.IMAXPACKETLENGTH], 
                    VentiNetworkLink.IMAXPACKETLENGTH);
            DatagramPacket dpReceive = new DatagramPacket(
                    new byte[VentiNetworkLink.IMAXPACKETLENGTH], 
                    VentiNetworkLink.IMAXPACKETLENGTH);

            while(true) {
                if(dsSessionReceiver == null) {
                    try {
                        dsSessionReceiver = new DatagramSocket(VentiNetworkLink.ISESSIONPORT);
                    }
                    catch(SocketException se) {
                        try {
                            Thread.sleep(1000);
                        }
                        catch(InterruptedException tie) {
                            System.err.println("Session Manager Interrupted");
                        }
                    }
                    try {
                        dsSessionReceiver.setSoTimeout(VentiNetworkLink.IRECEIVETIMEOUT);
                    }
                    catch(SocketException se) {
                        System.err.println("Socket Exception in setting session timeout");
                        dsSessionReceiver.close();
                    }
                }
                else {
                    boolean boReceived = true;
                    try {
                        dsSessionReceiver.receive(dpReceive);
                    }
                    catch(SocketTimeoutException ste) {
                        boReceived = false;
                    }
                    catch(IOException ioe) {
                        dsSessionReceiver.close();
                        System.err.println("IOException in Session Manager");
                    }
                    if(boReceived) {
                        InetAddress ia = dpReceive.getAddress();
                        System.err.println(ia);
                        int i = dpReceive.getPort();
                        System.err.println(i);
                        String s = new String(dpReceive.getData(), 0, dpReceive.getLength());
                        System.err.println(s);
                        try {
                            if(!ia.equals(iaLAN) &&
                                    !ia.isLoopbackAddress() &&
                                    dsSessionSend != null &&
                                    iaRemote == null) {
                                if(!boVentiMachine && s.equals(SVENTIMACHINE)) {
                                    iaRemote = ia;
                                    dpSend.setAddress(ia);
                                    dpSend.setPort(VentiNetworkLink.ISESSIONPORT);
                                    dpSend.setData(SVENTIUSER.getBytes());
                                    dsSessionSend.send(dpSend);
                                    // start a GUI for the machine just detected
                                    vml = new VentilatorGUI(vulThis);
                                    System.err.println("new GUI: " + ia);
                                }
                                else if(boVentiMachine && s.equals(SVENTIUSER)) {
                                    iaRemote = ia;
                                }
                            }
                        }
                        catch(UnknownHostException uhe) {
                            System.err.println("UnknownHostException in Session Manager");
                        }
                        catch(IOException ioe) {
                            System.err.println("IOException in Session Manager reply");
                        }
                    }
                }
            }
        }
    }

    DatagramSocket dsDataReceiver;
    final static int IDATAPORT = 12022;

    class DataReceiver implements Runnable {
    
        @Override
        public void run() {
            DatagramPacket dpReceive = new DatagramPacket(
                    new byte[VentiNetworkLink.IMAXPACKETLENGTH], 
                    VentiNetworkLink.IMAXPACKETLENGTH);
            
            while(true) {
                if(dsDataReceiver == null) {
                    try {
                        dsDataReceiver = new DatagramSocket(VentiNetworkLink.IDATAPORT);
                        System.err.println("New Data Socket");
                    }
                    catch(SocketException se) {
                        try {
                            Thread.sleep(1000);
                        }
                        catch(InterruptedException tie) {
                            System.err.println("Data Receiver Interrupted");
                        }
                    }
                    try {
                        dsDataReceiver.setSoTimeout(VentiNetworkLink.IRECEIVETIMEOUT);
                    }
                    catch(SocketException se) {
                        System.err.println("Socket Exception in setting data timeout");
                        dsDataReceiver.close();
                    }
                }
                else {
                    boolean boReceived = true;
                    try {
                        dsDataReceiver.receive(dpReceive);
                    }
                    catch(SocketTimeoutException ste) {
                        boReceived = false;
                        // iaRemote = null;
                        // TODO notify user of loss of connection
                    }
                    catch(IOException ioe) {
                        dsDataReceiver.close();
                        System.err.println("IOException in Data Manager");
                    }
                    if(boReceived && iaRemote.equals(dpReceive.getAddress())) {
                        String s = new String(dpReceive.getData(), 0, dpReceive.getLength());
                        if(vul != null) {
                            // TODO pass user action to machine
                            passUserToMachine(s);
                        }
                        else if(vml != null && vml.isReady()) {
                            // TODO pass machine data to user
                            passMachineToUser(s);
                        }
                    }
                }
            }
        }
    }

    enum EUserToMachine {
        ESHUTDOWN,
        ERUNAUTO,
        ESTOPAUTO,
        EENABLELOGGER,
        EDISABLELOGGER,
        EOPENV1,
        ESHUTV1,
        EOPENV2,
        ESHUTV2,
        EOPENV3,
        ESHUTV3,
        EADDPEEP,
        EADDPIP,
        EADDTIDALVOLUME,
        EADDRPM,
        EMINUSPEEP,
        EMINUSPIP,
        EMINUSTIDALVOLUME,
        EMINUSRPM;
        
    }
    
    enum EMachineToUser {
        ENOTIFYPRESSURES,
        ENOTIFYMESSAGEBOX,
        ENOTIFYVALVESENABLED,
        ENOTIFYV1STATE,
        ENOTIFYV2STATE,
        ENOTIFYV3STATE,
        ENOTIFYPEEP,
        ENOTIFYPIP,
        ENOTIFYRPM,
        ENOTIFYTIDALVOLUME;
    }
    
    void passUserToMachine(String s) {
        if(vul != null) {
            String[] ss = s.split(",");
            try {
                EUserToMachine e = EUserToMachine.valueOf(ss[0]);
                switch(e) {
                    case ESHUTDOWN:
                        vul.shutdown();
                        break;
                    case ERUNAUTO:
                        vul.runAuto();
                        break;
                    case ESTOPAUTO:
                        vul.stopAuto();
                        break;
                    case EENABLELOGGER:
                        vul.enableLogger();
                        break;
                    case EDISABLELOGGER:
                        vul.disableLogger();
                        break;
                    case EOPENV1:
                        vul.openV1();
                        break;
                    case ESHUTV1:
                        vul.shutV1();
                        break;
                    case EOPENV2:
                        vul.openV2();
                        break;
                    case ESHUTV2:
                        vul.shutV2();
                        break;
                    case EOPENV3:
                        vul.openV3();
                        break;
                    case ESHUTV3:
                        vul.shutV3();
                        break;
                    case EADDPEEP:
                        vul.addPEEP();
                        break;
                    case EADDPIP:
                        vul.addPIP();
                        break;
                    case EADDTIDALVOLUME:
                        vul.addTidalVolume();
                        break;
                    case EADDRPM:
                        vul.addRPM();
                        break;
                    case EMINUSPEEP:
                        vul.minusPEEP();;
                        break;
                    case EMINUSPIP:
                        vul.minusPIP();;
                        break;
                    case EMINUSTIDALVOLUME:
                        vul.minusTidalVolume();
                        break;
                    case EMINUSRPM:
                        vul.minusRPM();
                        break;
                    default:
                        break;
                }
            }
            catch(IllegalArgumentException iae) {
            }
        }
    }
    
    void passMachineToUser(String s) {
        if(vml != null) {
            String[] ss = s.split(",");
            try {
                EMachineToUser e = EMachineToUser.valueOf(ss[0]);
                switch(e) {
                    case ENOTIFYPRESSURES:
                        if(ss.length >= 3) {
                            vml.notifyPressures(ss[1], ss[2]);
                        }
                        break;
                    case ENOTIFYMESSAGEBOX:
                        if(ss.length >= 2) {
                            vml.notifyMessageBox(ss[1]);
                        }
                        break;
                    case ENOTIFYVALVESENABLED:
                        if(ss.length >= 2) {
                            vml.notifyValvesEnabled(ss[1]);
                        }
                        break;
                    case ENOTIFYV1STATE:
                        if(ss.length >= 2) {
                            vml.notifyV1State(ss[1]);
                        }
                        break;
                    case ENOTIFYV2STATE:
                        if(ss.length >= 2) {
                            vml.notifyV2State(ss[1]);
                        }
                        break;
                    case ENOTIFYV3STATE:
                        if(ss.length >= 2) {
                            vml.notifyV3State(ss[1]);
                        }
                        break;
                    case ENOTIFYPEEP:
                        if(ss.length >= 2) {
                            vml.notifyPEEP(ss[1]);
                        }
                        break;
                    case ENOTIFYPIP:
                        if(ss.length >= 2) {
                            vml.notifyPIP(ss[1]);
                        }
                        break;
                    case ENOTIFYRPM:
                        if(ss.length >= 2) {
                            vml.notifyRPM(ss[1]);
                        }
                        break;
                    case ENOTIFYTIDALVOLUME:
                        if(ss.length >= 2) {
                        vml.notifyTidalVolume(ss[1]);
                        }
                        break;
                    default:
                        break;
                }
            }
            catch(IllegalArgumentException iae) {
            }
        }
        
    }
    
    // for interface VentiMachineListener
    @Override
    public void notifyPressures(String s1, String s2) {
        dataSend(EMachineToUser.ENOTIFYPRESSURES, s1, s2);
    }
    
    @Override
    public void notifyMessageBox(String s) {
        dataSend(EMachineToUser.ENOTIFYMESSAGEBOX, s);
    }
    
    @Override
    public void notifyValvesEnabled(String s) {
        dataSend(EMachineToUser.ENOTIFYVALVESENABLED, s);
    }
    
    @Override
    public void notifyV1State(String s) {
        dataSend(EMachineToUser.ENOTIFYV1STATE, s);
    }
    
    @Override
    public void notifyV2State(String s) {
        dataSend(EMachineToUser.ENOTIFYV2STATE, s);
    }
    
    @Override
    public void notifyV3State(String s) {
        dataSend(EMachineToUser.ENOTIFYV3STATE, s);
    }
    
    //Plus and Minus Button control
    @Override
    public void notifyPEEP(String s) {
        dataSend(EMachineToUser.ENOTIFYPEEP, s);
    }
    
    @Override
    public void notifyPIP(String s) {
        dataSend(EMachineToUser.ENOTIFYPIP, s);
    }

    @Override
    public void notifyRPM(String s) {
        dataSend(EMachineToUser.ENOTIFYRPM, s);
    }

    @Override
    public void notifyTidalVolume(String s) {
        dataSend(EMachineToUser.ENOTIFYTIDALVOLUME, s);
    }

    void dataSend(EMachineToUser e) {
        dataSend(e, null, null);
    }
    void dataSend(EMachineToUser e, String s) {
        dataSend(e, s, null);
    }
    void dataSend(EMachineToUser e, String s1, String s2) {
        dataSend(e.toString(), s1, s2);
    }
    
    void dataSend(EUserToMachine e) {
        dataSend(e, null, null);
    }
    void dataSend(EUserToMachine e, String s) {
        dataSend(e, s, null);
    }
    void dataSend(EUserToMachine e, String s1, String s2) {
        dataSend(e.toString(), s1, s2);
    }
    void dataSend(String s1, String s2, String s3) {
        if(dsDataSend != null && dpDataSend != null && iaRemote != null) {
            dpDataSend.setAddress(iaRemote);
            dpDataSend.setPort(IDATAPORT);
            byte[] bs;
            if(s3 != null) {
                bs = (new StringBuilder(s1))
                        .append(",").append(s2)
                        .append(",").append(s3).toString().getBytes();
            }
            else if(s2 != null) {
                bs = (new StringBuilder(s1))
                        .append(",").append(s2).toString().getBytes();
            }
            else {
                bs = s1.getBytes();
            }
            dpDataSend.setData(bs);
            try {
                dsDataSend.send(dpDataSend);
            }
            catch(IOException ioe) {
                System.err.println("IOException in Data Send");
            }
        }
    }
    // for interface VentiUserListener
    @Override
    public void shutdown() {
        dataSend(EUserToMachine.ESHUTDOWN);
    }
    
    @Override
    public void runAuto() {
        dataSend(EUserToMachine.ERUNAUTO);
    }
    
    @Override
    public void stopAuto() {
        dataSend(EUserToMachine.ESTOPAUTO);
    }
        
    @Override
    public void enableLogger() {
        dataSend(EUserToMachine.EENABLELOGGER);
    }
    
    @Override
    public void disableLogger() {
        dataSend(EUserToMachine.EDISABLELOGGER);
    }
    
    @Override
    public void openV1() {
        dataSend(EUserToMachine.EOPENV1);
    }
    
    @Override
    public void shutV1() {
        dataSend(EUserToMachine.ESHUTV1);
    }
    
    @Override
    public void openV2() {
        dataSend(EUserToMachine.EOPENV2);
    }
    
    @Override
    public void shutV2() {
        dataSend(EUserToMachine.ESHUTV2);
    }
    
    @Override
    public void openV3() {
        dataSend(EUserToMachine.EOPENV3);
    }
    
    @Override
    public void shutV3() {
        dataSend(EUserToMachine.ESHUTV3);
    }

    @Override
    public void addPEEP() {
        dataSend(EUserToMachine.EADDPEEP);
    }
    
    @Override
    public void addPIP() {
        dataSend(EUserToMachine.EADDPIP);
    }
    
    @Override
    public void addTidalVolume() {
        dataSend(EUserToMachine.EADDTIDALVOLUME);
    }
    
    @Override
    public void addRPM() {
        dataSend(EUserToMachine.EADDRPM);
    }
    
    @Override
    public void minusPEEP() {
        dataSend(EUserToMachine.EMINUSPEEP);
    }
    
    @Override
    public void minusPIP() {
        dataSend(EUserToMachine.EMINUSPIP);
    }
    
    @Override
    public void minusTidalVolume() {
        dataSend(EUserToMachine.EMINUSTIDALVOLUME);
    }
    
    @Override
    public void minusRPM() {
        dataSend(EUserToMachine.EMINUSRPM);
    }

}
