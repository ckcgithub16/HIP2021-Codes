/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hip;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 *
 * @author lee
 */
public class VentiLogger extends Thread {
    
    boolean boEnabled;
    boolean boContinue;
    final ConcurrentLinkedQueue<VentiLogRecord> clq;
    final static String SHEADER = "Time,V1,V2,V3,Pressure1,Pressure2\n";
    long lStartTime;
    //Adjust the scope of decimal pressures represented in the line below
    static final DecimalFormat DF = new DecimalFormat("#0.00");
    
    public static void main(String[] ss) {
        try {
            VentiLogger vl = new VentiLogger();
            vl.start();
            vl.setEnabled(true);
            Thread.sleep(100);
            vl.logData(System.currentTimeMillis(), 
                    new boolean[]{false, false, false}, 
                    new float[]{0.0f, 0.0f});
            Thread.sleep(100);
            vl.logData(System.currentTimeMillis(), 
                    new boolean[]{true, false, false}, 
                    new float[]{0.0f, 0.0f});
            vl.logData(System.currentTimeMillis(), 
                    new boolean[]{true, false, false}, 
                    new float[]{0.5f, 0.0f});
            Thread.sleep(100);
            vl.logData(System.currentTimeMillis(), 
                    new boolean[]{false, true, false}, 
                    new float[]{1.0f, 0.0f});
            vl.logData(System.currentTimeMillis(), 
                    new boolean[]{false, true, false}, 
                    new float[]{0.5f, 0.25f});
            vl.logData(System.currentTimeMillis(), 
                    new boolean[]{false, true, false}, 
                    new float[]{0.3f, 0.3f});
            vl.logData(System.currentTimeMillis(), 
                    new boolean[]{false, false, true}, 
                    new float[]{0.3f, 0.3f});
            vl.logData(System.currentTimeMillis(), 
                    new boolean[]{false, false, true}, 
                    new float[]{0.3f, 0.15f});
            Thread.sleep(100);
            vl.setEnabled(false);
            Thread.sleep(100);
            vl.setEnabled(true);
            Thread.sleep(100);
            for(int i = 0; i < 1000; i++) {
                vl.logData(System.currentTimeMillis(), 
                        new boolean[]{false, true, false}, 
                        new float[]{i * 0.1f, i * 0.15f});
                Thread.sleep(10);
            }
            vl.setEnabled(false);
            Thread.sleep(100);
            vl.shutdown();
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    VentiLogger() {
        boEnabled = false;
        boContinue = true;
        clq = new ConcurrentLinkedQueue<>();
    }
    
    void setEnabled(boolean bo) {
        boEnabled = bo;
        interrupt();
    }
    
    void shutdown() {
        boContinue = false;
        interrupt();
    }
    
    void logData(long lTime, boolean[] boDiscretes, float[] fAnalogs) {
        VentiLogRecord vlr = new VentiLogRecord(lTime, boDiscretes, fAnalogs);
        clq.offer(vlr);
        interrupt();
    }
    
    /* void logData(long lTime, float fPressure1, float fPressure2) {
        String s = String.format("%d,%.2f,%.2f\n", lTime - lStartTime, fPressure1, fPressure2);
        logString(s);
    } */
    
    @Override
    public void run() {
        BufferedWriter bw = null;

        this.setPriority(this.getPriority() -1);
        while(boContinue) {
            try {
                if(boEnabled && bw == null) {
                    SimpleDateFormat sdfLogName = new SimpleDateFormat("'VentiLog'-yyMMddHHmmss'.log'");
                    lStartTime = System.currentTimeMillis();
                    bw = new BufferedWriter(new FileWriter(sdfLogName.format(new Date(lStartTime))));
                    bw.write(SHEADER);
                }
                else if(!boEnabled && bw != null) {
                    clq.clear();
                    bw.close();
                    bw = null;
                }
                while(!clq.isEmpty()) {
                    VentiLogRecord vlr = clq.poll();
                    if(bw != null) {
                        bw.write(vlr.toCSV());
                    }
                }
            }
            catch(IOException ioe) {
                // TODO pass failure information to user, update GUI
                boEnabled = false;
                bw = null;
                clq.clear();
            }
            try {
                Thread.sleep(10000);
            }
            catch(InterruptedException ie) {
                
            }
        }
    }
    
    class VentiLogRecord {
        long lTime;
        boolean[] boDiscretes;
        float[] fAnalogs;
        
        VentiLogRecord(long lTime, boolean[] boDiscretes, float[] fAnalogs) {
            this.lTime = lTime;
            this.boDiscretes = boDiscretes;
            this.fAnalogs = fAnalogs;
        }
        
        String toCSV() {
            StringBuilder sb = new StringBuilder(Long.toString(lTime - lStartTime));
            for(boolean bo : boDiscretes) {
                sb.append(bo ? ",1" : ",0");
            }
            for(float f : fAnalogs) {
                sb.append(",").append(DF.format(f));
            }
            return sb.append("\n").toString();
        }
    }
    
}
